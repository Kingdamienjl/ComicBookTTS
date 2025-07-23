package com.example.comicreader

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.comicreader.databinding.ActivitySettingsBinding
import com.example.comicreader.utils.ElevenLabsTTS
import com.example.comicreader.utils.PreferenceManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var tts: ElevenLabsTTS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        preferenceManager = PreferenceManager(this)
        tts = ElevenLabsTTS(this)

        // Load saved API key
        binding.etApiKey.setText(preferenceManager.getApiKey())

        binding.btnSave.setOnClickListener {
            val apiKey = binding.etApiKey.text.toString().trim()
            preferenceManager.saveApiKey(apiKey)
            Toast.makeText(this, "API key saved", Toast.LENGTH_SHORT).show()
            finish()
        }
        
        binding.btnTest.setOnClickListener {
            testApiKey()
        }
    }
    
    private fun testApiKey() {
        val apiKey = binding.etApiKey.text.toString().trim()
        
        if (apiKey.isBlank()) {
            Toast.makeText(this, "Please enter an API key first", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Save the key first
        preferenceManager.saveApiKey(apiKey)
        Toast.makeText(this, "Testing API key...", Toast.LENGTH_SHORT).show()
        
        // Test text
        val testText = "This is a test of the ElevenLabs API. If you hear this message, your API key is working correctly."
        
        tts.speak(testText, object : ElevenLabsTTS.TTSCallback {
            override fun onStart() {
                Log.d("SettingsActivity", "API test started")
            }
            
            override fun onComplete() {
                Log.d("SettingsActivity", "API test completed successfully")
                Toast.makeText(this@SettingsActivity, "API key is valid!", Toast.LENGTH_SHORT).show()
            }
            
            override fun onError(message: String) {
                Log.e("SettingsActivity", "API test failed: $message")
                Toast.makeText(this@SettingsActivity, "API key test failed: $message", Toast.LENGTH_LONG).show()
            }
        })
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::tts.isInitialized) {
            tts.cleanup()
        }
    }
}