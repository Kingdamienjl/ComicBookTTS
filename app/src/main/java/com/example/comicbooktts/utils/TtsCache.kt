package com.example.comicbooktts.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.security.MessageDigest

/**
 * Disk cache for pre-generated TTS audio.
 *
 * Key = SHA-256(text + voiceId + speechRate), so changing the narrator voice
 * or rate naturally misses and regenerates — no manual invalidation needed.
 *
 * Added 2026-10-11 (on-device TTS rebuild).
 */
class TtsCache(context: Context) {

    companion object {
        private const val TAG = "TtsCache"
        private const val CACHE_DIR = "tts_cache"
        private const val MAX_FILES = 2000L
    }

    private val dir = File(context.filesDir, CACHE_DIR).apply { mkdirs() }
    private val prefs = PreferenceManager(context)

    /** File for this text+voice combo, or null if not cached. */
    fun get(text: String, voiceId: String): File? {
        val f = fileFor(text, voiceId)
        return if (f.exists() && f.length() > 0) f else null
    }

    /** Reserve the output file for a generation job. */
    fun fileFor(text: String, voiceId: String): File {
        return File(dir, "${keyFor(text, voiceId)}.wav")
    }

    fun put(text: String, voiceId: String, file: File): Boolean {
        return try {
            val dest = fileFor(text, voiceId)
            if (file.absolutePath != dest.absolutePath) file.copyTo(dest, overwrite = true)
            pruneIfNeeded()
            true
        } catch (e: Exception) {
            Log.w(TAG, "cache put failed: ${e.message}")
            false
        }
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    fun sizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    private fun keyFor(text: String, voiceId: String): String {
        val input = "$voiceId|${prefs.getSpeechRate()}|$text"
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    private fun pruneIfNeeded() {
        try {
            val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
            if (files.size > MAX_FILES) {
                files.take(files.size - MAX_FILES.toInt()).forEach { it.delete() }
                Log.i(TAG, "pruned TTS cache to $MAX_FILES files")
            }
        } catch (e: Exception) {
            Log.w(TAG, "prune failed: ${e.message}")
        }
    }
}
