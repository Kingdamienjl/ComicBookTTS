package com.example.comicbooktts.utils

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf

/**
 * Background pre-generation of TTS audio for upcoming pages.
 *
 * Enqueued when the reader opens a page: generates audio for the next N pages
 * so playback is instant and offline. Runs on-device via [OnDeviceTTS].
 *
 * Added 2026-10-11 (on-device TTS rebuild).
 */
class TtsPreGenWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "TtsPreGenWorker"
        const val KEY_TEXTS = "texts"
        const val KEY_VOICE_ID = "voice_id"

        fun enqueue(context: Context, texts: List<String>, voiceId: String) {
            if (texts.isEmpty()) return
            val data = workDataOf(
                KEY_TEXTS to texts.toTypedArray(),
                KEY_VOICE_ID to voiceId
            )
            val req = OneTimeWorkRequestBuilder<TtsPreGenWorker>()
                .setInputData(data)
                .addTag("tts-pregen")
                .build()
            WorkManager.getInstance(context).enqueue(req)
            Log.i(TAG, "enqueued pre-gen for ${texts.size} blocks")
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag("tts-pregen")
        }
    }

    override suspend fun doWork(): Result {
        val texts = inputData.getStringArray(KEY_TEXTS)?.toList() ?: return Result.failure()
        val voiceId = inputData.getString(KEY_VOICE_ID) ?: OnDeviceTTS.DEFAULT_VOICE_ID

        val tts = OnDeviceTTS(applicationContext)
        val cache = TtsCache(applicationContext)
        try {
            if (!tts.initialize(voiceId)) {
                Log.w(TAG, "TTS not ready, retrying later")
                return Result.retry()
            }
            var generated = 0
            for (text in texts) {
                if (isStopped) break
                val clean = text.trim()
                if (clean.length < 3) continue
                // Skip if already cached
                if (cache.get(clean, voiceId) != null) continue
                val outFile = cache.fileFor(clean, voiceId)
                if (tts.synthesizeToFile(clean, outFile)) generated++
            }
            Log.i(TAG, "pre-gen done: $generated new / ${texts.size} total")
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "pre-gen failed: ${e.message}", e)
            return Result.retry()
        } finally {
            tts.release()
        }
    }
}
