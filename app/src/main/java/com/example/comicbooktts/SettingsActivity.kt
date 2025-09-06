package com.example.comicbooktts

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.comicbooktts.databinding.ActivitySettingsBinding

import com.example.comicbooktts.utils.HybridTTS
import com.example.comicbooktts.utils.PreferenceManager
import kotlinx.coroutines.launch
import java.util.*

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var preferenceManager: PreferenceManager

    private lateinit var hybridTTS: HybridTTS

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("SettingsActivity", "onCreate() called")
        super.onCreate(savedInstanceState)

        try {
            Log.d("SettingsActivity", "Inflating layout...")
            binding = ActivitySettingsBinding.inflate(layoutInflater)
            setContentView(binding.root)
            Log.d("SettingsActivity", "Layout inflated successfully")

            // Setup toolbar safely
            try {
                setSupportActionBar(binding.toolbar)
                supportActionBar?.setDisplayHomeAsUpEnabled(true)
                supportActionBar?.title = "Settings"
                Log.d("SettingsActivity", "Toolbar setup complete")
            } catch (e: Exception) {
                Log.e("SettingsActivity", "Error setting up toolbar: ${e.message}")
            }

            Log.d("SettingsActivity", "Initializing components...")
            preferenceManager = PreferenceManager(this)

            hybridTTS = HybridTTS(this)
            Log.d("SettingsActivity", "Components initialized")

                    Log.d("SettingsActivity", "Setting up controls...")
        setupTTSControls()
        updateConnectionStatus()
        updateUsageStats()
            Log.d("SettingsActivity", "SettingsActivity onCreate completed successfully")

        } catch (e: Exception) {
            Log.e("SettingsActivity", "Critical error in onCreate: ${e.message}", e)
            Toast.makeText(this, "Error loading settings: ${e.message}", Toast.LENGTH_LONG).show()
            // Don't finish() - let the user see the error and try to continue
        }
    }

    private fun setupTTSControls() {
        try {
            Log.d("SettingsActivity", "Setting up TTS controls...")

            // Setup reading speed control
            binding.seekBarReadingSpeed?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    try {
                        val speed = 0.5f + (progress / 25.0f) * 1.5f // 0.5x to 2.0x
                        binding.tvReadingSpeedValue?.text = String.format("%.1fx", speed)
                        if (fromUser) {
                            hybridTTS.setSpeechRate(speed)
                            preferenceManager.saveSpeechRate(speed)
                        }
                    } catch (e: Exception) {
                        Log.e("SettingsActivity", "Error in speed change: ${e.message}")
                    }
                }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

            // Setup OCR sensitivity control
            binding.seekBarOcrSensitivity?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    try {
                        val sensitivity = when (progress) {
                            in 0..2 -> "Low"
                            in 3..4 -> "Medium-Low"
                            in 5..6 -> "Medium"
                            in 7..8 -> "Medium-High"
                            else -> "High"
                        }
                        binding.tvOcrSensitivityValue?.text = sensitivity
                        if (fromUser) {
                            preferenceManager.saveOcrSensitivity(progress)
                        }
                    } catch (e: Exception) {
                        Log.e("SettingsActivity", "Error in OCR sensitivity change: ${e.message}")
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            // Load saved settings
            try {
                val savedSpeed = preferenceManager.getSpeechRate()
                val speedProgress = ((savedSpeed - 0.5f) / 1.5f * 25).toInt()
                binding.seekBarReadingSpeed?.progress = speedProgress
                binding.tvReadingSpeedValue?.text = String.format("%.1fx", savedSpeed)
            } catch (e: Exception) {
                Log.e("SettingsActivity", "Error loading speed settings: ${e.message}")
            }

            try {
                val savedSensitivity = preferenceManager.getOcrSensitivity()
                binding.seekBarOcrSensitivity?.progress = savedSensitivity
                val sensitivityText = when (savedSensitivity) {
                    in 0..2 -> "Low"
                    in 3..4 -> "Medium-Low"
                    in 5..6 -> "Medium"
                    in 7..8 -> "Medium-High"
                    else -> "High"
                }
                binding.tvOcrSensitivityValue?.text = sensitivityText
            } catch (e: Exception) {
                Log.e("SettingsActivity", "Error loading OCR settings: ${e.message}")
            }

            Log.d("SettingsActivity", "TTS controls setup complete")
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Error setting up TTS controls: ${e.message}", e)
        }
    }





    private fun updateConnectionStatus() {
        val status = if (hybridTTS.isTTSAvailable()) {
            val engine = hybridTTS.getCurrentEngine()
            "$engine: Ready"
        } else {
            "Android TTS: Initializing..."
        }
        binding.tvConnectionStatus.text = "Connection Status: $status"
    }

    private fun updateUsageStats() {
        val stats = hybridTTS.getUsageStats()
        val statsText = "Usage: ${stats.nativeRequests} native requests, ${stats.totalCharacters} characters"
        binding.tvUsageStats.text = statsText
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::hybridTTS.isInitialized) {
            hybridTTS.cleanup()
        }
    }
}
