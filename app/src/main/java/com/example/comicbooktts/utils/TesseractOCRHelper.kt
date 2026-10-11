package com.example.comicbooktts.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream

/**
 * Secondary OCR engine powered by Tesseract (tess-two).
 *
 * Used by [DualEngineOCR] as a slower-but-more-accurate validator when the
 * primary ML Kit engine reports low confidence. Runs the LSTM neural-net
 * engine with automatic page segmentation and orientation detection.
 *
 * Training data (`eng.traineddata`) is copied from `assets/tessdata/` into
 * the app's private files dir on first use. If tess-two is missing from the
 * classpath or the training data is unavailable, every method degrades
 * gracefully instead of crashing.
 */
class TesseractOCRHelper {

    private var tessBaseApi: TessBaseAPI? = null
    private var isInitialized = false

    /**
     * Word-level OCR result with a confidence score.
     *
     * @param text Recognized word text.
     * @param confidence Word confidence in the range 0..1.
     */
    data class WordResult(
        val text: String,
        val confidence: Float
    )

    companion object {
        private const val TAG = "TesseractOCRHelper"
        private const val LANGUAGE = "eng"
        private const val TESSDATA_DIR = "tessdata"
        private const val TRAINED_DATA_FILE = "eng.traineddata"
        private const val ASSET_TESSDATA_PATH = "tessdata/eng.traineddata"
        private const val OCR_TIMEOUT_MS = 10_000L

        /** Characters Tesseract is allowed to emit (alphanumeric + common punctuation). */
        private const val CHAR_WHITELIST =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 .,!?;:'\"-()"
    }

    /**
     * Initializes the Tesseract engine.
     *
     * Copies training data from assets if needed, then creates the native
     * engine in LSTM-only mode with auto page segmentation (PSM_AUTO_OSD).
     *
     * @param context Application context used for asset/file access.
     * @return True if the engine is ready, false otherwise.
     */
    suspend fun initialize(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true

        try {
            if (!ensureTrainingData(context)) {
                Log.w(TAG, "Training data unavailable; Tesseract disabled")
                return@withContext false
            }

            val api = TessBaseAPI()
            val dataPath = context.filesDir.absolutePath
            if (!api.init(dataPath, LANGUAGE, TessBaseAPI.OEM_LSTM_ONLY)) {
                Log.e(TAG, "TessBaseAPI.init() failed for path=$dataPath")
                return@withContext false
            }

            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO_OSD
            api.setVariable("tessedit_char_whitelist", CHAR_WHITELIST)

            tessBaseApi = api
            isInitialized = true
            Log.i(TAG, "Tesseract initialized (OEM_LSTM_ONLY, PSM_AUTO_OSD)")
            true
        } catch (t: Throwable) {
            // Catches NoClassDefFoundError / UnsatisfiedLinkError when
            // tess-two is absent or its native libs failed to load.
            Log.w(TAG, "Tesseract not available: ${t.message}")
            tessBaseApi = null
            isInitialized = false
            false
        }
    }

    /**
     * Extracts text from a bitmap with a 10-second timeout guard.
     *
     * @param bitmap Source image (any config; converted internally if needed).
     * @return Pair of (recognized text, mean confidence 0..1). Empty text on
     *         failure or timeout.
     */
    suspend fun extractText(bitmap: Bitmap): Pair<String, Float> {
        val api = tessBaseApi
        if (!isInitialized || api == null) {
            Log.w(TAG, "extractText called before successful initialize()")
            return "" to 0f
        }

        return try {
            val result = withTimeoutOrNull(OCR_TIMEOUT_MS) {
                withContext(Dispatchers.Default) {
                    synchronized(api) {
                        api.setImage(bitmap)
                        val text = api.utF8Text.orEmpty()
                        val conf = (api.meanConfidence() / 100f).coerceIn(0f, 1f)
                        api.clear()
                        text to conf
                    }
                }
            }

            if (result == null) {
                Log.w(TAG, "Tesseract OCR timed out after ${OCR_TIMEOUT_MS}ms")
                "" to 0f
            } else {
                Log.d(TAG, "Tesseract done: ${result.first.length} chars, conf=${result.second}")
                result
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Tesseract extractText failed", t)
            "" to 0f
        }
    }

    /**
     * Extracts word-level results with per-word confidence scores.
     *
     * @param bitmap Source image.
     * @return List of recognized words; empty on failure, timeout, or if the
     *         engine is not initialized.
     */
    suspend fun extractTextWithConfidence(bitmap: Bitmap): List<WordResult> {
        val api = tessBaseApi
        if (!isInitialized || api == null) {
            Log.w(TAG, "extractTextWithConfidence called before successful initialize()")
            return emptyList()
        }

        return try {
            val result = withTimeoutOrNull(OCR_TIMEOUT_MS) {
                withContext(Dispatchers.Default) {
                    synchronized(api) {
                        api.setImage(bitmap)
                        val words = collectWordResults(api)
                        api.clear()
                        words
                    }
                }
            }

            if (result == null) {
                Log.w(TAG, "Tesseract word OCR timed out after ${OCR_TIMEOUT_MS}ms")
                emptyList()
            } else {
                Log.d(TAG, "Tesseract word OCR: ${result.size} words")
                result
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Tesseract extractTextWithConfidence failed", t)
            emptyList()
        }
    }

    /**
     * Ensures `eng.traineddata` exists under `<filesDir>/tessdata/`.
     *
     * Copies it from `assets/tessdata/eng.traineddata` on first run.
     *
     * @param context Application context.
     * @return True if the training data file exists (or was copied).
     */
    suspend fun ensureTrainingData(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val tessDir = File(context.filesDir, TESSDATA_DIR)
            if (!tessDir.exists() && !tessDir.mkdirs()) {
                Log.e(TAG, "Could not create tessdata dir: ${tessDir.absolutePath}")
                return@withContext false
            }

            val trainedData = File(tessDir, TRAINED_DATA_FILE)
            if (trainedData.exists() && trainedData.length() > 0) {
                Log.d(TAG, "Training data already present (${trainedData.length()} bytes)")
                return@withContext true
            }

            Log.i(TAG, "Copying eng.traineddata from assets...")
            context.assets.open(ASSET_TESSDATA_PATH).use { input ->
                FileOutputStream(trainedData).use { output ->
                    input.copyTo(output)
                }
            }

            val ok = trainedData.exists() && trainedData.length() > 0
            Log.i(TAG, if (ok) "Training data installed" else "Training data copy failed")
            ok
        } catch (e: Exception) {
            Log.w(TAG, "Training data not bundled in assets: ${e.message}")
            false
        }
    }

    /**
     * Releases the native Tesseract engine.
     */
    fun cleanup() {
        try {
            tessBaseApi?.end()
        } catch (t: Throwable) {
            Log.w(TAG, "Error during Tesseract cleanup: ${t.message}")
        } finally {
            tessBaseApi = null
            isInitialized = false
            Log.d(TAG, "Tesseract cleaned up")
        }
    }

    /**
     * Walks the result iterator at word level collecting text + confidence.
     * Must be called with the engine lock held and an image already set.
     */
    private fun collectWordResults(api: TessBaseAPI): List<WordResult> {
        val results = mutableListOf<WordResult>()
        val iterator = api.resultIterator ?: return emptyList()
        try {
            iterator.begin()
            do {
                val word = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                    ?.trim().orEmpty()
                val conf = (iterator.confidence(TessBaseAPI.PageIteratorLevel.RIL_WORD) / 100f)
                    .coerceIn(0f, 1f)
                if (word.isNotEmpty()) {
                    results.add(WordResult(word, conf))
                }
            } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
        } catch (t: Throwable) {
            Log.w(TAG, "Word iteration failed: ${t.message}")
        } finally {
            runCatching { iterator.delete() }
        }
        return results
    }
}
