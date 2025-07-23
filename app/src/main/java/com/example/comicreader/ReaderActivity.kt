package com.example.comicreader

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.comicreader.databinding.ActivityReaderBinding
import com.example.comicreader.utils.ComicArchiveExtractor
import com.example.comicreader.utils.ElevenLabsTTS
import com.example.comicreader.utils.PreferenceManager
import com.example.comicreader.utils.TextRecognitionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReaderBinding
    private lateinit var comicPageAdapter: ComicPageAdapter
    private lateinit var comicExtractor: ComicArchiveExtractor
    private lateinit var textRecognitionHelper: TextRecognitionHelper
    private lateinit var tts: ElevenLabsTTS
    private lateinit var preferenceManager: PreferenceManager
    
    private var comicPages = listOf<String>()
    private var currentPage = 0
    private var autoPlayEnabled = false
    private var isProcessing = false

    companion object {
        private const val TAG = "ReaderActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize utilities
        comicExtractor = ComicArchiveExtractor(this)
        textRecognitionHelper = TextRecognitionHelper(this)
        tts = ElevenLabsTTS(this)
        preferenceManager = PreferenceManager(this)

        setupViewPager()
        setupButtons()
        
        // Test the ElevenLabs API call
        testElevenLabsApi()

        // Load comic from intent if available, otherwise just run the API test
        intent?.data?.let { uri ->
            loadComic(uri)
        } // Don't finish if no URI - we're just testing the API
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.cleanup()
    }

    private fun setupViewPager() {
        comicPageAdapter = ComicPageAdapter(emptyList())
        binding.viewPager.adapter = comicPageAdapter
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                
                // If auto-play is enabled, read the new page
                if (autoPlayEnabled && !isProcessing) {
                    readCurrentPage()
                }
            }
        })
    }

    private fun setupButtons() {
        binding.btnPlayTts.setOnClickListener {
            if (!isProcessing) {
                readCurrentPage()
            }
        }

        binding.btnStopTts.setOnClickListener {
            tts.stop()
        }

        binding.switchAutoPlay.setOnCheckedChangeListener { _, isChecked ->
            autoPlayEnabled = isChecked
            if (isChecked && !isProcessing) {
                readCurrentPage()
            }
        }
    }

    private fun loadComic(uri: Uri) {
        showLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val extractedPages = comicExtractor.extractComic(uri)
                withContext(Dispatchers.Main) {
                    if (extractedPages.isEmpty()) {
                        Toast.makeText(this@ReaderActivity, "Failed to extract comic pages", Toast.LENGTH_SHORT).show()
                        finish()
                        return@withContext
                    }
                    
                    comicPages = extractedPages
                    updateComicPages()
                    showLoading(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading comic: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ReaderActivity, "Error loading comic: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun updateComicPages() {
        val imageResources = comicPages.map { path ->
            Uri.fromFile(java.io.File(path))
        }
        comicPageAdapter = ComicPageAdapter(imageResources)
        binding.viewPager.adapter = comicPageAdapter
    }

    private fun readCurrentPage() {
        if (currentPage >= comicPages.size || isProcessing) return
        
        isProcessing = true
        showLoading(true)
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pagePath = comicPages[currentPage]
                val extractedText = textRecognitionHelper.extractTextFromImage(pagePath)
                
                withContext(Dispatchers.Main) {
                    if (extractedText.isBlank()) {
                        Toast.makeText(this@ReaderActivity, "No text found on this page", Toast.LENGTH_SHORT).show()
                        isProcessing = false
                        showLoading(false)
                        
                        // If auto-play is enabled, move to next page
                        if (autoPlayEnabled && currentPage < comicPages.size - 1) {
                            binding.viewPager.currentItem = currentPage + 1
                        }
                        return@withContext
                    }
                    
                    // Send text to TTS
                    tts.speak(extractedText, object : ElevenLabsTTS.TTSCallback {
                        override fun onStart() {
                            showLoading(false)
                        }
                        
                        override fun onComplete() {
                            isProcessing = false
                            
                            // If auto-play is enabled, move to next page
                            if (autoPlayEnabled && currentPage < comicPages.size - 1) {
                                binding.viewPager.currentItem = currentPage + 1
                            }
                        }
                        
                        override fun onError(message: String) {
                            Toast.makeText(this@ReaderActivity, message, Toast.LENGTH_SHORT).show()
                            isProcessing = false
                            showLoading(false)
                        }
                    })
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing page: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ReaderActivity, "Error processing page: ${e.message}", Toast.LENGTH_SHORT).show()
                    isProcessing = false
                    showLoading(false)
                }
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }
    
    /**
     * Test function to verify ElevenLabs API key works
     */
    private fun testElevenLabsApi() {
        Toast.makeText(this, "Testing ElevenLabs API...", Toast.LENGTH_SHORT).show()
        showLoading(true)
        
        // Simple test text
        val testText = "This is a test of the ElevenLabs API integration. If you hear this, the API key is working correctly."
        
        tts.speak(testText, object : ElevenLabsTTS.TTSCallback {
            override fun onStart() {
                Log.d(TAG, "API test started")
            }
            
            override fun onComplete() {
                Log.d(TAG, "API test completed successfully")
                showLoading(false)
                Toast.makeText(this@ReaderActivity, "API test successful!", Toast.LENGTH_SHORT).show()
            }
            
            override fun onError(message: String) {
                Log.e(TAG, "API test failed: $message")
                showLoading(false)
                Toast.makeText(this@ReaderActivity, "API test failed: $message", Toast.LENGTH_LONG).show()
            }
        })
    }
}
