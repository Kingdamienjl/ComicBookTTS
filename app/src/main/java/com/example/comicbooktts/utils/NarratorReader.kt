package com.example.comicbooktts.utils

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Narrator playback orchestrator: cache → on-device synthesis → HybridTTS fallback.
 *
 * - Checks [TtsCache] first; plays cached WAV via MediaPlayer (instant, offline).
 * - On cache miss, synthesizes via [OnDeviceTTS] (offline), caches, then plays.
 * - Falls back to [HybridTTS] (Android native) if on-device engine isn't ready.
 *
 * Added 2026-10-11 (on-device TTS wiring).
 */
class NarratorReader(private val context: Context) {

    companion object {
        private const val TAG = "NarratorReader"
    }

    interface PlaybackCallback {
        fun onStart()
        fun onComplete()
        fun onError(message: String)
    }

    private val cache = TtsCache(context)
    private val onDeviceTts = OnDeviceTTS(context)
    private val hybridTts: HybridTTS by lazy { HybridTTS(context) }
    private var mediaPlayer: MediaPlayer? = null
    private var ttsReady: Boolean? = null

    /** Pre-warm the on-device engine (call from Activity.onCreate). */
    suspend fun warmUp(): Boolean = withContext(Dispatchers.IO) {
        if (ttsReady == null) {
            ttsReady = onDeviceTts.initialize()
        }
        ttsReady == true
    }

    /**
     * Play narration for text. Uses cache → on-device → HybridTTS fallback chain.
     */
    suspend fun narrate(text: String, callback: PlaybackCallback) {
        val clean = text.trim()
        if (clean.length < 3) {
            callback.onError("No text to narrate")
            return
        }
        val voiceId = OnDeviceTTS.DEFAULT_VOICE_ID

        // 1. Cache hit → play immediately
        cache.get(clean, voiceId)?.let { cached ->
            Log.d(TAG, "cache hit, playing ${cached.name}")
            playFile(cached, callback)
            return
        }

        // 2. On-device synthesis
        if (ttsReady != false) {
            val ready = warmUp()
            if (ready) {
                val outFile = cache.fileFor(clean, voiceId)
                val ok = withContext(Dispatchers.Default) {
                    onDeviceTts.synthesizeToFile(clean, outFile)
                }
                if (ok) {
                    Log.d(TAG, "synthesized on-device, playing")
                    playFile(outFile, callback)
                    return
                }
                Log.w(TAG, "on-device synthesis failed, falling back")
            }
        }

        // 3. HybridTTS fallback (Android native)
        Log.i(TAG, "using HybridTTS fallback")
        withContext(Dispatchers.Main) {
            hybridTts.speak(clean, object : HybridTTS.TTSCallback {
                override fun onStart() = callback.onStart()
                override fun onComplete() = callback.onComplete()
                override fun onError(error: String) = callback.onError(error)
            })
        }
    }

    /** Enqueue background pre-generation for upcoming page texts. */
    fun preGenerateUpcoming(texts: List<String>) {
        if (texts.isEmpty()) return
        // Only pre-gen cache misses to avoid redundant work
        val misses = texts.map { it.trim() }
            .filter { it.length >= 3 }
            .filter { cache.get(it, OnDeviceTTS.DEFAULT_VOICE_ID) == null }
        if (misses.isNotEmpty()) {
            TtsPreGenWorker.enqueue(context, misses, OnDeviceTTS.DEFAULT_VOICE_ID)
        }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "stop failed: ${e.message}")
        }
        mediaPlayer = null
        hybridTts.stop()
    }

    fun release() {
        stop()
        onDeviceTts.release()
        hybridTts.shutdown()
    }

    private suspend fun playFile(file: File, callback: PlaybackCallback) {
        withContext(Dispatchers.Main) {
            try {
                stop() // stop any current playback
                callback.onStart()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    prepare()
                    setOnCompletionListener {
                        callback.onComplete()
                        release()
                        mediaPlayer = null
                    }
                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "MediaPlayer error $what/$extra")
                        callback.onError("Playback failed")
                        true
                    }
                    start()
                }
            } catch (e: Exception) {
                Log.e(TAG, "playFile failed: ${e.message}", e)
                callback.onError("Playback failed: ${e.message}")
            }
        }
    }
}
