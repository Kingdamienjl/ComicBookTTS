package com.example.comicreader

import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.io.File
import java.io.IOException

class ReaderActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Your OCR pipeline goes here
        val comicText = "This is a test from ElevenLabs"
        sendTextToElevenLabs(comicText)
    }

    private fun sendTextToElevenLabs(text: String) {
        val client = OkHttpClient()
        val apiKey = "6e3e5e48a41757df7c198978cab5e887"
        val voiceId = "IRHApOXLvnW57QJPQH2P"
        val url = "https://api.elevenlabs.io/v1/text-to-speech/$voiceId/stream"

        val json = """
            {
                "text": "$text",
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

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TTS", "Error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.let { body ->
                    val file = File.createTempFile("tts", ".mp3", cacheDir)
                    file.outputStream().use { body.byteStream().copyTo(it) }
                    MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        prepare()
                        start()
                    }
                }
            }
        })
    }
}
