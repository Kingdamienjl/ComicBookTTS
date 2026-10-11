package com.example.comicbooktts.utils

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device TTS engine using Sherpa-ONNX. Fully offline, no server, no API keys.
 *
 * Uses a VITS narrator voice model stored in app files. First launch downloads
 * the model (~50-150MB); all synthesis after that is local.
 *
 * Replaces the ElevenLabs dependency for the default narrator path.
 * Added 2026-10-11 (on-device TTS rebuild).
 */
class OnDeviceTTS(private val context: Context) {

    companion object {
        private const val TAG = "OnDeviceTTS"

        /** Default narrator voice model (Piper VITS, English). */
        const val DEFAULT_VOICE_ID = "en_US-narrator-medium"

        /** Where voice models live on device. */
        private const val VOICES_DIR = "tts_voices"

        // Model file names inside a voice dir (Piper layout)
        private const val MODEL_FILE = "model.onnx"
        private const val TOKENS_FILE = "tokens.txt"
        private const val ESPEAK_DATA_DIR = "espeak-ng-data"
    }

    private val prefs = PreferenceManager(context)
    private var tts: OfflineTts? = null
    private var currentVoiceId: String = DEFAULT_VOICE_ID
    private var isInitialized = false

    /**
     * Initialize the engine. Downloads the voice model on first run.
     * @return true if ready to synthesize.
     */
    suspend fun initialize(voiceId: String = DEFAULT_VOICE_ID): Boolean =
        withContext(Dispatchers.IO) {
            try {
                currentVoiceId = voiceId
                val voiceDir = File(context.filesDir, "$VOICES_DIR/$voiceId")

                if (!isVoiceReady(voiceDir)) {
                    Log.i(TAG, "Voice model missing, downloading: $voiceId")
                    if (!downloadVoice(voiceId, voiceDir)) {
                        Log.e(TAG, "Voice download failed")
                        return@withContext false
                    }
                }

                val modelConfig = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = File(voiceDir, MODEL_FILE).absolutePath,
                        tokens = File(voiceDir, TOKENS_FILE).absolutePath,
                        dataDir = File(voiceDir, ESPEAK_DATA_DIR).absolutePath
                    ),
                    numThreads = Runtime.getRuntime().availableProcessors().coerceAtMost(4),
                    debug = false
                )
                val config = OfflineTtsConfig(model = modelConfig)
                tts = OfflineTts(config)
                isInitialized = true
                Log.i(TAG, "On-device TTS ready (voice=$voiceId)")
                true
            } catch (e: Exception) {
                Log.e(TAG, "TTS init failed: ${e.message}", e)
                false
            }
        }

    /**
     * Synthesize text to a WAV file. Returns the file, or null on failure.
     */
    suspend fun synthesizeToFile(text: String, outFile: File): Boolean =
        withContext(Dispatchers.Default) {
            val engine = tts
            if (!isInitialized || engine == null) {
                Log.w(TAG, "synthesize called before init")
                return@withContext false
            }
            try {
                val audio = engine.generate(
                    text = text,
                    sid = 0,
                    speed = prefs.getSpeechRate()
                )
                if (audio.samples.isEmpty()) return@withContext false
                writeWav(outFile, audio.samples, audio.sampleRate)
                true
            } catch (e: Exception) {
                Log.e(TAG, "synthesize failed: ${e.message}", e)
                false
            }
        }

    fun isReady(): Boolean = isInitialized

    fun getVoiceId(): String = currentVoiceId

    fun release() {
        try {
            tts?.release()
        } catch (e: Exception) {
            Log.w(TAG, "release failed: ${e.message}")
        }
        tts = null
        isInitialized = false
    }

    private fun isVoiceReady(voiceDir: File): Boolean {
        return File(voiceDir, MODEL_FILE).exists() &&
            File(voiceDir, TOKENS_FILE).exists()
    }

    /**
     * Download a Piper voice model. Override the URL map to add voices.
     * Default: fetch from the project's voice CDN.
     */
    private fun downloadVoice(voiceId: String, destDir: File): Boolean {
        // TODO: point at real voice CDN. For now, expect manual placement
        // via Settings → Download narrator voice, or bundled asset.
        Log.w(TAG, "downloadVoice not yet wired to CDN for $voiceId")
        return false
    }

    /** Write 16-bit PCM mono WAV. */
    private fun writeWav(file: File, samples: FloatArray, sampleRate: Int) {
        val pcm = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val v = (samples[i].coerceIn(-1f, 1f) * 32767).toInt()
            pcm[i * 2] = (v and 0xFF).toByte()
            pcm[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        file.outputStream().use { out ->
            // RIFF header
            out.write("RIFF".toByteArray())
            writeIntLE(out, 36 + pcm.size)
            out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray())
            writeIntLE(out, 16)
            writeShortLE(out, 1) // PCM
            writeShortLE(out, 1) // mono
            writeIntLE(out, sampleRate)
            writeIntLE(out, sampleRate * 2)
            writeShortLE(out, 2)
            writeShortLE(out, 16)
            out.write("data".toByteArray())
            writeIntLE(out, pcm.size)
            out.write(pcm)
        }
    }

    private fun writeIntLE(out: java.io.OutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
        out.write((v shr 16) and 0xFF)
        out.write((v shr 24) and 0xFF)
    }

    private fun writeShortLE(out: java.io.OutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }
}
