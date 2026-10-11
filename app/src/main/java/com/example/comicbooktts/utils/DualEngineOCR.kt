package com.example.comicbooktts.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * Which engine produced an [OCRResult].
 */
enum class OCREngine {
    /** No engine produced a result (error path). */
    NONE,

    /** ML Kit alone, high confidence on the first pass. */
    ML_KIT,

    /** Tesseract alone (ML Kit produced nothing usable). */
    TESSERACT,

    /** Both engines ran; ML Kit won the confidence vote. */
    ML_KIT_VOTED,

    /** Both engines ran; Tesseract won the confidence vote. */
    TESSERACT_VOTED
}

/**
 * A single OCR pass result with provenance metadata.
 *
 * @param text Recognized (and post-processed) text.
 * @param confidence Mean confidence in the range 0..1.
 * @param engine Which engine / voting outcome produced this result.
 * @param error Human-readable error when [engine] is [OCREngine.NONE].
 */
data class OCRResult(
    val text: String,
    val confidence: Float,
    val engine: OCREngine,
    val error: String? = null
)

/**
 * Dual-engine OCR orchestrator: ML Kit (primary, fast) + Tesseract (secondary,
 * accurate), with comic-specific preprocessing and confidence voting.
 *
 * Pipeline:
 * ```
 * Image → Preprocess(mode) → ML Kit → conf >= 0.85? → return
 *                                  ↓ no (or QUALITY mode)
 *                        Tesseract → vote → post-process → return
 * ```
 *
 * Preprocessing intensity follows the user's OCR sensitivity setting
 * ([PreferenceManager.getOcrSensitivity], 0-10): 0-2 → FAST, 3-7 → BALANCED,
 * 8-10 → QUALITY (both engines always run).
 *
 * Tesseract is optional: if its training data is missing the orchestrator
 * silently falls back to ML Kit-only results.
 */
class DualEngineOCR(private val context: Context) {

    private val mlKitHelper = TextRecognitionHelper(context)
    private val tesseractHelper = TesseractOCRHelper()
    private val preprocessor = ComicImagePreprocessor()
    private val preferenceManager = PreferenceManager(context)

    private var tesseractReady = false

    companion object {
        private const val TAG = "DualEngineOCR"

        /** ML Kit confidence at or above which Tesseract is skipped. */
        private const val HIGH_CONFIDENCE_CUTOFF = 0.85f

        /** Confidence gap below which the longer text wins the vote. */
        private const val VOTE_TIE_THRESHOLD = 0.1f

        /** Total wall-clock budget for one extraction. */
        private const val TOTAL_TIMEOUT_MS = 10_000L
    }

    /**
     * Initializes the secondary (Tesseract) engine.
     *
     * ML Kit needs no explicit init. Always returns true — a Tesseract
     * failure only disables the validation stage, never the whole pipeline.
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        tesseractReady = try {
            tesseractHelper.initialize(context)
        } catch (t: Throwable) {
            Log.w(TAG, "Tesseract init threw: ${t.message}")
            false
        }
        Log.i(TAG, "DualEngineOCR initialized (tesseractReady=$tesseractReady)")
        true
    }

    /**
     * Extracts text from a bitmap through the dual-engine pipeline.
     *
     * Runs on [Dispatchers.Default] with a 10-second total timeout. Never
     * throws: failures are reported via [OCRResult.error] with
     * [OCREngine.NONE].
     *
     * @param bitmap Source comic page/panel image.
     */
    suspend fun extractText(bitmap: Bitmap): OCRResult =
        withContext(Dispatchers.Default) {
            val result = withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
                runPipeline(bitmap)
            }
            result ?: OCRResult(
                text = "",
                confidence = 0f,
                engine = OCREngine.NONE,
                error = "OCR timed out after ${TOTAL_TIMEOUT_MS}ms"
            )
        }

    /**
     * Releases the secondary engine's native resources.
     */
    fun cleanup() {
        try {
            tesseractHelper.cleanup()
        } catch (t: Throwable) {
            Log.w(TAG, "Cleanup threw: ${t.message}")
        }
        tesseractReady = false
        Log.d(TAG, "DualEngineOCR cleaned up")
    }

    // ------------------------------------------------------------------
    // Pipeline
    // ------------------------------------------------------------------

    private suspend fun runPipeline(bitmap: Bitmap): OCRResult {
        val mode = modeForSensitivity(preferenceManager.getOcrSensitivity())
        Log.d(TAG, "extractText: mode=$mode, tesseractReady=$tesseractReady")

        val preprocessed = try {
            preprocessor.preprocess(bitmap, mode)
        } catch (t: Throwable) {
            Log.w(TAG, "Preprocessing failed, using raw bitmap: ${t.message}")
            bitmap
        }

        // Stage 1: ML Kit (primary).
        val mlResult = runMlKit(preprocessed)
        Log.d(TAG, "ML Kit: ${mlResult.first.length} chars, conf=${mlResult.second}")

        // Stage 2: Tesseract (secondary) — when ML Kit is unsure, or always
        // in QUALITY mode.
        val runTesseract = tesseractReady &&
            (mode == ComicImagePreprocessor.PreprocessMode.QUALITY ||
                mlResult.second < HIGH_CONFIDENCE_CUTOFF)

        val voted: OCRResult = if (runTesseract) {
            val tessResult = tesseractHelper.extractText(preprocessed)
            Log.d(TAG, "Tesseract: ${tessResult.first.length} chars, conf=${tessResult.second}")
            selectBestResult(mlResult, tessResult)
        } else {
            val engine = if (mlResult.first.isNotBlank()) OCREngine.ML_KIT else OCREngine.NONE
            OCRResult(mlResult.first, mlResult.second, engine)
        }

        // Recycle the preprocessed copy if we made one.
        if (preprocessed !== bitmap) {
            runCatching { preprocessed.recycle() }
        }

        if (voted.engine == OCREngine.NONE) {
            return voted.copy(error = voted.error ?: "Both engines returned no text")
        }

        // Stage 3: post-process common OCR errors.
        val fixed = postProcessText(voted.text)
        return voted.copy(text = fixed)
    }

    /**
     * Runs ML Kit on a bitmap via [TextRecognitionHelper], returning
     * (text, mean panel confidence).
     *
     * The existing helper works on file paths, so the bitmap is staged
     * through a temp PNG in the app cache dir.
     */
    private suspend fun runMlKit(bitmap: Bitmap): Pair<String, Float> {
        val tmp = File.createTempFile("dual_ocr_", ".png", context.cacheDir)
        try {
            FileOutputStream(tmp).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val panels = mlKitHelper.extractTextPanelsFromImage(tmp.absolutePath)
            if (panels.isEmpty()) return "" to 0f

            val text = panels.joinToString(" ") { it.text }.trim()
            if (text.isEmpty()) return "" to 0f

            val confidence = panels.map { it.confidence }.average().toFloat()
            return text to confidence.coerceIn(0f, 1f)
        } catch (t: Throwable) {
            Log.w(TAG, "ML Kit stage failed: ${t.message}")
            return "" to 0f
        } finally {
            runCatching { tmp.delete() }
        }
    }

    /**
     * Picks the winner between the two engines.
     *
     * If confidences are within [VOTE_TIE_THRESHOLD], the longer text wins
     * (engines usually agree; extra words are usually real). Otherwise the
     * higher confidence wins. The winning engine is tagged with its
     * `*_VOTED` variant.
     */
    private fun selectBestResult(
        mlKit: Pair<String, Float>,
        tesseract: Pair<String, Float>
    ): OCRResult {
        val (mlText, mlConf) = mlKit
        val (tessText, tessConf) = tesseract

        // Both empty: nothing to vote on.
        if (mlText.isBlank() && tessText.isBlank()) {
            return OCRResult("", 0f, OCREngine.NONE, "Both engines returned no text")
        }
        // Only one produced text: take it as-is.
        if (mlText.isBlank()) {
            return OCRResult(tessText, tessConf, OCREngine.TESSERACT)
        }
        if (tessText.isBlank()) {
            return OCRResult(mlText, mlConf, OCREngine.ML_KIT)
        }

        val mlWins: Boolean = if (abs(mlConf - tessConf) < VOTE_TIE_THRESHOLD) {
            Log.d(TAG, "Vote tie (Δ<0.1): longer text wins " +
                "(ml=${mlText.length}, tess=${tessText.length})")
            mlText.length >= tessText.length
        } else {
            mlConf > tessConf
        }

        return if (mlWins) {
            Log.d(TAG, "Vote winner: ML_KIT (conf=$mlConf vs $tessConf)")
            OCRResult(mlText, mlConf, OCREngine.ML_KIT_VOTED)
        } else {
            Log.d(TAG, "Vote winner: TESSERACT (conf=$tessConf vs $mlConf)")
            OCRResult(tessText, tessConf, OCREngine.TESSERACT_VOTED)
        }
    }

    /**
     * Maps the 0-10 OCR sensitivity slider to a preprocessing mode.
     */
    private fun modeForSensitivity(sensitivity: Int): ComicImagePreprocessor.PreprocessMode {
        return when (sensitivity.coerceIn(0, 10)) {
            in 0..2 -> ComicImagePreprocessor.PreprocessMode.FAST
            in 8..10 -> ComicImagePreprocessor.PreprocessMode.QUALITY
            else -> ComicImagePreprocessor.PreprocessMode.BALANCED
        }
    }

    /**
     * Fixes common OCR misreads in comic lettering.
     *
     * - `0` → `O` between letters ("W0W" → "WOW")
     * - `l` → `I` inside all-caps words ("HELlO" → "HELLO")
     * - `rn` → `m` (classic Tesseract/ML Kit confusion)
     * - `vv` → `w`, `VV` → `W`
     * - leading `1` → `I` before a letter ("1AM" → "IAM")
     * - collapses excessive whitespace
     */
    private fun postProcessText(text: String): String {
        if (text.isBlank()) return text
        var fixed = text

        // 0 -> O when surrounded by letters.
        fixed = fixed.replace(Regex("(?<=[A-Za-z])0(?=[A-Za-z])"), "O")

        // l -> I inside all-caps words.
        fixed = Regex("\\b[A-Zl]+\\b").replace(fixed) { match ->
            match.value.replace('l', 'I')
        }

        // rn -> m, vv -> w, VV -> W.
        fixed = fixed.replace("rn", "m")
        fixed = fixed.replace("vv", "w")
        fixed = fixed.replace("VV", "W")

        // Leading 1 -> I before a letter.
        fixed = fixed.replace(Regex("\\b1(?=[A-Za-z])"), "I")

        // Collapse whitespace.
        fixed = fixed.replace(Regex("\\s+"), " ").trim()

        if (fixed != text) {
            Log.d(TAG, "postProcessText: \"$text\" -> \"$fixed\"")
        }
        return fixed
    }
}
