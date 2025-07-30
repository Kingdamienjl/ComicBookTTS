package com.example.comicreader.utils

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.io.File
import java.io.IOException

class ElevenLabsTTS(private val context: Context) {

    private val client = OkHttpClient()
    private var mediaPlayer: MediaPlayer? = null
    private var _isPlaying = false
    private var currentFile: File? = null
    private val preferenceManager = PreferenceManager(context)

    companion object {
        private const val TAG = "ElevenLabsTTS"
        private const val BASE_URL = "https://api.elevenlabs.io/v1/text-to-speech"
        private const val DEFAULT_VOICE_ID = "IRHApOXLvnW57QJPQH2P" // Default voice ID
    }

    interface TTSCallback {
        fun onStart()
        fun onComplete()
        fun onError(message: String)
    }

    /**
     * Speak the provided text using ElevenLabs API
     * @param text Text to speak
     * @param callback Callback for TTS events
     */
    fun speak(text: String, callback: TTSCallback) {
        if (text.isBlank()) {
            callback.onError("No text to speak")
            return
        }

        val apiKey = preferenceManager.getApiKey()
        if (apiKey.isBlank()) {
            callback.onError("ElevenLabs API key not configured. Please set your API key in Settings.\n\nTo get an API key:\n1. Visit https://elevenlabs.io\n2. Sign up for an account\n3. Go to Profile & API Key\n4. Copy your API key\n5. Paste it in the app Settings")
            return
        }

        // Stop any current playback
        stop()

        val voiceId = DEFAULT_VOICE_ID
        val url = "$BASE_URL/$voiceId/stream"

        // Escape quotes in text to prevent JSON parsing errors
        val escapedText = text.replace("\"", "\\\"")
        
        val json = """
            {
                "text": "$escapedText",
                "model_id": "eleven_monolingual_v1",
                "voice_settings": {
                    "stability": 0.5,
                    "similarity_boost": 0.75
                }
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(url)
            .addHeader("xi-api-key", apiKey)
            .post(RequestBody.create("application/json".toMediaType(), json))
            .build()

        callback.onStart()

        // Log the request details for debugging
        Log.d(TAG, "Making ElevenLabs API request to: $url")
        Log.d(TAG, "Using API key: ${apiKey.take(5)}...${apiKey.takeLast(5)}")
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "TTS API network error: ${e.message}")
                Log.e(TAG, "Error details: ${e.stackTraceToString()}")
                callback.onError("Network error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d(TAG, "Received API response with code: ${response.code}")
                
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    Log.e(TAG, "TTS API error response: $errorBody")
                    Log.e(TAG, "Response headers: ${response.headers}")
                    callback.onError("API error (${response.code}): $errorBody")
                    return
                }
                
                Log.d(TAG, "API request successful")

                response.body?.let { body ->
                    try {
                        // Create a temporary file for the audio
                        val file = File.createTempFile("tts", ".mp3", context.cacheDir)
                        file.outputStream().use { body.byteStream().copyTo(it) }
                        currentFile = file

                        // Play the audio
                        playAudio(file, callback)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing TTS response: ${e.message}")
                        callback.onError("Error processing audio: ${e.message}")
                    }
                }
            }
        })
    }

    /**
     * Play the audio file
     */
    private fun playAudio(file: File, callback: TTSCallback) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    _isPlaying = false
                    callback.onComplete()
                }
                setOnErrorListener { _, _, _ ->
                    _isPlaying = false
                    callback.onError("Error playing audio")
                    true
                }
                prepare()
                start()
                _isPlaying = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio: ${e.message}")
            callback.onError("Error playing audio: ${e.message}")
        }
    }

    /**
     * Stop current playback
     */
    fun stop() {
        mediaPlayer?.apply {
            if (_isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
        _isPlaying = false
    }

    /**
     * Check if TTS is currently playing
     */
    fun isPlaying(): Boolean {
        return _isPlaying
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        stop()
        currentFile?.delete()
        currentFile = null
    }
}