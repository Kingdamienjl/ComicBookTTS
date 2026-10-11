package com.example.comicbooktts

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.comicbooktts.databinding.ActivityReaderBinding
import com.example.comicbooktts.utils.ComicArchiveExtractor
import com.example.comicbooktts.utils.HybridTTS
import com.example.comicbooktts.utils.NarratorReader
import com.example.comicbooktts.utils.PreferenceManager
import com.example.comicbooktts.utils.TextRecognitionHelper
import kotlinx.coroutines.*
import kotlin.system.measureTimeMillis

class ReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReaderBinding
    private lateinit var comicPageAdapter: ComicPageAdapter
    private lateinit var comicExtractor: ComicArchiveExtractor
    private lateinit var textRecognitionHelper: TextRecognitionHelper
    private lateinit var tts: HybridTTS
    private lateinit var narratorReader: NarratorReader
    private lateinit var preferenceManager: PreferenceManager
    
    private var comicPages = listOf<String>()
    private var currentPage = 0
    private var autoPlayEnabled = false
    private var isProcessing = false

    // Slideshow functionality
    private var slideshowJob: Job? = null
    private var isSlideShowActive = false
    private var slideshowSpeed = 5000L // 5 seconds per page
    private var autoScrollEnabled = true
    
    // Error tracking and performance monitoring
    private var initializationTime = 0L
    private var errorCount = 0
    private val errorLog = mutableListOf<ErrorRecord>()
    private var pageProcessingTimes = mutableMapOf<Int, Long>()
    private var ttsOperationCount = 0
    private var successfulTtsOperations = 0

    companion object {
        private const val TAG = "ReaderActivity"
        private const val MAX_ERROR_LOG_SIZE = 100
    }
    
    /**
     * Data class to track error details
     */
    data class ErrorRecord(
        val timestamp: Long,
        val errorType: String,
        val message: String,
        val exception: String?,
        val pageNumber: Int? = null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        initializationTime = measureTimeMillis {
            try {
                binding = ActivityReaderBinding.inflate(layoutInflater)
                setContentView(binding.root)
                
                Log.d(TAG, "ReaderActivity onCreate started")
                
                // Initialize utilities with error handling
                try {
                    comicExtractor = ComicArchiveExtractor(this)
                    textRecognitionHelper = TextRecognitionHelper(this)
                    tts = HybridTTS(this)
                    narratorReader = NarratorReader(this)
                    preferenceManager = PreferenceManager(this)
                    Log.d(TAG, "All utilities initialized successfully")

                    // Warm up on-device narrator in background (non-blocking)
                    lifecycleScope.launch(Dispatchers.IO) {
                        val ready = narratorReader.warmUp()
                        Log.i(TAG, "Narrator warm-up: ready=$ready")
                    }
                } catch (e: Exception) {
                    logError("UTILITY_INIT", "Failed to initialize utilities: ${e.message}", e)
                    showUserMessage("Error initializing app components", isError = true)
                    finish()
                    return
                }
                
                // Setup UI components
                try {
                    setupViewPager()
                    setupButtons()
                    Log.d(TAG, "UI components setup completed")
                } catch (e: Exception) {
                    logError("UI_SETUP", "Failed to setup UI components: ${e.message}", e)
                    showUserMessage("Error setting up interface", isError = true)
                    finish()
                    return
                }
                
                // Initialize TTS for immediate use
                try {
                    initializeTTS()
                    testTTSFunctionality()
                    Log.d(TAG, "TTS initialization completed")
                } catch (e: Exception) {
                    logError("TTS_INIT", "Failed to initialize TTS: ${e.message}", e)
                    showUserMessage("Text-to-speech initialization failed", isError = true)
                    // Don't finish - app can still work without TTS
                }
                
                // Load comic from intent if available
                intent?.data?.let { uri ->
                    try {
                        loadComic(uri)
                        Log.d(TAG, "Comic loading initiated for URI: $uri")
                    } catch (e: Exception) {
                        logError("COMIC_LOAD_INIT", "Failed to initiate comic loading: ${e.message}", e)
                        showUserMessage("Error loading comic file", isError = true)
                    }
                }
                
            } catch (e: OutOfMemoryError) {
                logError("ONCREATE_CRITICAL", "Out of memory during initialization", e)
                showUserMessage("Insufficient memory to run application", isError = true)
                finish()
            } catch (e: SecurityException) {
                logError("ONCREATE_CRITICAL", "Security exception during initialization: ${e.message}", e)
                showUserMessage("Permission denied", isError = true)
                finish()
            } catch (e: Exception) {
                logError("ONCREATE_GENERAL", "Unexpected error during initialization: ${e.message}", e)
                showUserMessage("Application initialization failed", isError = true)
                finish()
            }
        }
        
        Log.d(TAG, "ReaderActivity initialization completed in ${initializationTime}ms")
    }

    override fun onDestroy() {
        try {
            Log.d(TAG, "Destroying ReaderActivity")
            
            // Stop slideshow and cleanup
            try {
                stopSlideshow()
                Log.d(TAG, "Slideshow stopped successfully")
            } catch (e: Exception) {
                logError("ONDESTROY_SLIDESHOW", "Error stopping slideshow during destroy: ${e.message}", e)
            }

            // Cleanup TTS
            try {
                if (::tts.isInitialized) {
                    tts.cleanup()
                    Log.d(TAG, "TTS cleanup completed")
                }
                if (::narratorReader.isInitialized) {
                    narratorReader.release()
                    Log.d(TAG, "Narrator cleanup completed")
                } else {
                    Log.w(TAG, "TTS not initialized, skipping cleanup")
                }
            } catch (e: Exception) {
                logError("ONDESTROY_TTS", "Error cleaning up TTS during destroy: ${e.message}", e)
            }
            
            // Cancel any running coroutines
            try {
                slideshowJob?.cancel()
                Log.d(TAG, "Slideshow job cancelled")
            } catch (e: Exception) {
                logError("ONDESTROY_COROUTINE", "Error cancelling slideshow job: ${e.message}", e)
            }
            
            Log.d(TAG, "ReaderActivity destroyed successfully")
            
        } catch (e: Exception) {
            logError("ONDESTROY", "Critical error during activity destruction: ${e.message}", e)
        } finally {
            super.onDestroy()
        }
    }

    override fun onPause() {
        try {
            Log.d(TAG, "Pausing ReaderActivity")
            
            // Pause slideshow when activity is paused
            try {
                if (isSlideShowActive) {
                    stopSlideshow()
                    Log.d(TAG, "Slideshow stopped due to activity pause")
                } else {
                    Log.d(TAG, "No active slideshow to stop")
                }
            } catch (e: Exception) {
                logError("ONPAUSE_SLIDESHOW", "Error stopping slideshow during pause: ${e.message}", e)
            }
            
            // Stop any ongoing TTS
            try {
                if (::tts.isInitialized && tts.isTTSAvailable()) {
                    tts.stop()
                    Log.d(TAG, "TTS stopped due to activity pause")
                }
            } catch (e: Exception) {
                logError("ONPAUSE_TTS", "Error stopping TTS during pause: ${e.message}", e)
            }
            
            Log.d(TAG, "ReaderActivity paused successfully")
            
        } catch (e: Exception) {
            logError("ONPAUSE", "Critical error during activity pause: ${e.message}", e)
        } finally {
            super.onPause()
        }
    }

    private fun initializeTTS() {
        try {
            Log.d(TAG, "Initializing TTS...")

            // Validate TTS object
            if (!::tts.isInitialized) {
                logError("TTS_INIT", "TTS object not initialized", null)
                throw IllegalStateException("TTS object not initialized")
            }

            // TTS initialization is handled automatically in HybridTTS
            Log.d(TAG, "TTS automatic initialization started")

            // Test TTS after a short delay to ensure initialization
            try {
                binding.root.postDelayed({
                    try {
                        if (tts.isTTSAvailable()) {
                            Log.d(TAG, "TTS initialized successfully")
                            showUserMessage("Text-to-speech ready", isError = false)
                        } else {
                            Log.w(TAG, "TTS initialization may still be in progress")
                            showUserMessage("Text-to-speech initializing...", isError = false)
                        }
                    } catch (e: Exception) {
                        logError("TTS_INIT_CHECK", "Error checking TTS availability: ${e.message}", e)
                        showUserMessage("Text-to-speech check failed", isError = true)
                    }
                }, 1000)
                Log.d(TAG, "TTS initialization check scheduled")
            } catch (e: Exception) {
                logError("TTS_INIT_DELAY", "Error scheduling TTS initialization check: ${e.message}", e)
                throw e
            }
            
        } catch (e: Exception) {
            logError("TTS_INIT", "Critical error initializing TTS: ${e.message}", e)
            showUserMessage("Text-to-speech initialization failed", isError = true)
            throw e
        }
    }

    private fun testTTSFunctionality() {
        try {
            Log.d(TAG, "Testing TTS functionality...")
            
            // Validate TTS object
            if (!::tts.isInitialized) {
                logError("TTS_TEST", "TTS object not initialized for testing", null)
                showUserMessage("Text-to-speech not initialized", isError = true)
                return
            }

            // Check TTS availability
            try {
                if (!tts.isTTSAvailable()) {
                    Log.w(TAG, "TTS not available for testing")
                    showUserMessage("TTS not available - please wait for initialization...", isError = false)
                    return
                }
            } catch (e: Exception) {
                logError("TTS_TEST_AVAILABILITY", "Error checking TTS availability: ${e.message}", e)
                showUserMessage("Error checking text-to-speech availability", isError = true)
                return
            }

            // Perform TTS test
            try {
                val testMessage = "TTS is working correctly. This is a test message for the comic book reader."
                
                tts.speak(testMessage, object : HybridTTS.TTSCallback {
                    override fun onStart() {
                        try {
                            Log.d(TAG, "TTS test started successfully")
                            showUserMessage("TTS Test Started", isError = false)
                            ttsOperationCount++
                        } catch (e: Exception) {
                            logError("TTS_TEST_START", "Error in TTS test start callback: ${e.message}", e)
                        }
                    }

                    override fun onComplete() {
                        try {
                            Log.d(TAG, "TTS test completed successfully")
                            showUserMessage("TTS Test Completed Successfully", isError = false)
                            successfulTtsOperations++
                        } catch (e: Exception) {
                            logError("TTS_TEST_COMPLETE", "Error in TTS test complete callback: ${e.message}", e)
                        }
                    }

                    override fun onError(message: String) {
                        try {
                            Log.e(TAG, "TTS test failed: $message")
                            logError("TTS_TEST_FAILED", "TTS test failed: $message", null)
                            showUserMessage("TTS Test Failed: $message", isError = true)
                        } catch (e: Exception) {
                            logError("TTS_TEST_ERROR", "Error in TTS test error callback: ${e.message}", e)
                        }
                    }

                    override fun onEngineChanged(engine: String) {
                        try {
                            Log.d(TAG, "TTS Engine changed to: $engine")
                            showUserMessage("TTS Engine: $engine", isError = false)
                        } catch (e: Exception) {
                            logError("TTS_TEST_ENGINE", "Error in TTS engine change callback: ${e.message}", e)
                        }
                    }
                })
                
                Log.d(TAG, "TTS test initiated successfully")
                
            } catch (e: Exception) {
                logError("TTS_TEST_SPEAK", "Error initiating TTS test speech: ${e.message}", e)
                showUserMessage("Error starting TTS test", isError = true)
            }
            
        } catch (e: Exception) {
            logError("TTS_TEST", "Critical error testing TTS functionality: ${e.message}", e)
            showUserMessage("TTS test failed", isError = true)
        }
    }

    private fun setupViewPager() {
        try {
            Log.d(TAG, "Setting up ViewPager")
            
            // Initialize adapter
            try {
                comicPageAdapter = ComicPageAdapter(emptyList())
                binding.viewPager.adapter = comicPageAdapter
                Log.d(TAG, "ViewPager adapter initialized successfully")
            } catch (e: Exception) {
                logError("VIEWPAGER_SETUP", "Error initializing ViewPager adapter: ${e.message}", e)
                throw e
            }
            
            // Register page change callback
            try {
                binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        try {
                            Log.d(TAG, "Page selected: $position")
                            
                            // Validate position
                            if (position < 0 || position >= comicPages.size) {
                                logError("VIEWPAGER_PAGE", "Invalid page position: $position (total pages: ${comicPages.size})", null, position)
                                return
                            }
                            
                            currentPage = position

                            // Pre-generate narrator audio for upcoming pages (background)
                            preGenerateUpcomingPages(position)
                            
                            // If auto-play is enabled, read the new page
                            if (autoPlayEnabled && !isProcessing) {
                                try {
                                    readCurrentPage()
                                } catch (e: Exception) {
                                    logError("VIEWPAGER_AUTOPLAY", "Error auto-playing page $position: ${e.message}", e, position)
                                }
                            }
                        } catch (e: Exception) {
                            logError("VIEWPAGER_CALLBACK", "Error in page selection callback for position $position: ${e.message}", e, position)
                        }
                    }
                })
                Log.d(TAG, "ViewPager page change callback registered successfully")
            } catch (e: Exception) {
                logError("VIEWPAGER_CALLBACK", "Error registering page change callback: ${e.message}", e)
                throw e
            }
            
        } catch (e: Exception) {
            logError("VIEWPAGER_SETUP", "Critical error setting up ViewPager: ${e.message}", e)
            showUserMessage("Error setting up comic viewer", isError = true)
            throw e
        }
    }

    private fun setupButtons() {
        try {
            Log.d(TAG, "Setting up buttons and controls")
            
            // Setup Play TTS button
            try {
                binding.btnPlayTts?.setOnClickListener {
                    try {
                        if (!isProcessing) {
                            Log.d(TAG, "Play TTS button clicked")
                            readCurrentPage()
                        } else {
                            Log.d(TAG, "Play TTS button clicked but already processing")
                            showUserMessage("Already processing current page", isError = false)
                        }
                    } catch (e: Exception) {
                        logError("BUTTON_PLAY", "Error handling play TTS button click: ${e.message}", e)
                        showUserMessage("Error starting text-to-speech", isError = true)
                    }
                }
                Log.d(TAG, "Play TTS button listener set")
            } catch (e: Exception) {
                logError("BUTTON_SETUP", "Error setting up play TTS button: ${e.message}", e)
            }

            // Setup Stop TTS button
            try {
                binding.btnStopTts?.setOnClickListener {
                    try {
                        Log.d(TAG, "Stop TTS button clicked")
                        tts.stop()
                        showUserMessage("Text-to-speech stopped", isError = false)
                    } catch (e: Exception) {
                        logError("BUTTON_STOP", "Error handling stop TTS button click: ${e.message}", e)
                        showUserMessage("Error stopping text-to-speech", isError = true)
                    }
                }
                Log.d(TAG, "Stop TTS button listener set")
            } catch (e: Exception) {
                logError("BUTTON_SETUP", "Error setting up stop TTS button: ${e.message}", e)
            }

            // Add TTS test functionality - long press play button
            try {
                binding.btnPlayTts?.setOnLongClickListener {
                    try {
                        Log.d(TAG, "Play TTS button long-pressed - testing TTS")
                        testTTSFunctionality()
                        true
                    } catch (e: Exception) {
                        logError("BUTTON_TEST", "Error handling TTS test long press: ${e.message}", e)
                        showUserMessage("Error testing text-to-speech", isError = true)
                        false
                    }
                }
                Log.d(TAG, "Play TTS long press listener set")
            } catch (e: Exception) {
                logError("BUTTON_SETUP", "Error setting up TTS test long press: ${e.message}", e)
            }

            // Setup Auto-play switch
            try {
                binding.switchAutoPlay.setOnCheckedChangeListener { _, isChecked ->
                    try {
                        Log.d(TAG, "Auto-play switch changed to: $isChecked")
                        autoPlayEnabled = isChecked
                        
                        if (isChecked && !isProcessing) {
                            try {
                                readCurrentPage()
                            } catch (e: Exception) {
                                logError("AUTOPLAY_START", "Error starting auto-play: ${e.message}", e)
                                showUserMessage("Error starting auto-play", isError = true)
                            }
                        }
                    } catch (e: Exception) {
                        logError("AUTOPLAY_TOGGLE", "Error handling auto-play toggle: ${e.message}", e)
                        showUserMessage("Error toggling auto-play", isError = true)
                    }
                }
                Log.d(TAG, "Auto-play switch listener set")
            } catch (e: Exception) {
                logError("BUTTON_SETUP", "Error setting up auto-play switch: ${e.message}", e)
            }

            // Add slideshow functionality - double tap to start/stop slideshow
            try {
                var lastTapTime = 0L
                binding.viewPager.setOnClickListener {
                    try {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastTapTime < 300) { // Double tap detected
                            Log.d(TAG, "Double tap detected - toggling slideshow")
                            toggleSlideshow()
                        }
                        lastTapTime = currentTime
                    } catch (e: Exception) {
                        logError("SLIDESHOW_DOUBLETAP", "Error handling double tap for slideshow: ${e.message}", e)
                        showUserMessage("Error controlling slideshow", isError = true)
                    }
                }
                Log.d(TAG, "ViewPager double-tap listener set")
            } catch (e: Exception) {
                logError("BUTTON_SETUP", "Error setting up slideshow double-tap: ${e.message}", e)
            }
            
            Log.d(TAG, "Button setup completed successfully")
            
        } catch (e: Exception) {
            logError("BUTTON_SETUP", "Critical error setting up buttons: ${e.message}", e)
            showUserMessage("Error setting up controls", isError = true)
            throw e
        }
    }

    /**
     * Toggle slideshow mode with auto-scroll and zoom
     */
    private fun toggleSlideshow() {
        try {
            if (isSlideShowActive) {
                stopSlideshow()
            } else {
                startSlideshow()
            }
        } catch (e: Exception) {
            logError("SLIDESHOW_TOGGLE", "Error toggling slideshow: ${e.message}", e)
            showUserMessage("Error controlling slideshow", isError = true)
        }
    }

    /**
     * Start slideshow with left-to-right scrolling and auto-zoom
     */
    private fun startSlideshow() {
        try {
            // Validation checks
            if (comicPages.isEmpty()) {
                logError("SLIDESHOW_START", "Cannot start slideshow: no comic loaded", null)
                showUserMessage("No comic loaded", isError = true)
                return
            }
            
            if (isSlideShowActive) {
                Log.w(TAG, "Slideshow already active, ignoring start request")
                return
            }
            
            if (currentPage >= comicPages.size - 1) {
                logError("SLIDESHOW_START", "Cannot start slideshow: already at last page ($currentPage)", null)
                showUserMessage("Already at the last page", isError = true)
                return
            }

            isSlideShowActive = true
            showUserMessage("Slideshow started - Double tap to stop", isError = false)
            Log.d(TAG, "Starting slideshow with auto-scroll and zoom from page $currentPage")

            slideshowJob = lifecycleScope.launch {
                try {
                    while (isSlideShowActive && currentPage < comicPages.size - 1) {
                        // Perform auto-scroll and zoom on current page
                        if (autoScrollEnabled) {
                            try {
                                performAutoScrollAndZoom()
                            } catch (e: Exception) {
                                logError("SLIDESHOW_SCROLL", "Error during auto-scroll on page $currentPage: ${e.message}", e, currentPage)
                                // Continue slideshow even if scroll fails
                            }
                        }

                        // Wait for the slideshow duration
                        try {
                            delay(slideshowSpeed)
                        } catch (e: CancellationException) {
                            Log.d(TAG, "Slideshow cancelled during delay")
                            break
                        }

                        if (isSlideShowActive && currentPage < comicPages.size - 1) {
                            // Move to next page (left-to-right reading)
                            try {
                                withContext(Dispatchers.Main) {
                                    currentPage++
                                    binding.viewPager.setCurrentItem(currentPage, true)
                                    Log.d(TAG, "Slideshow advanced to page $currentPage")
                                }
                            } catch (e: Exception) {
                                logError("SLIDESHOW_ADVANCE", "Error advancing to page $currentPage: ${e.message}", e, currentPage)
                                // Stop slideshow on navigation error
                                break
                            }
                        }
                    }

                    // Slideshow completed or stopped
                    withContext(Dispatchers.Main) {
                        if (isSlideShowActive) {
                            isSlideShowActive = false
                            showUserMessage("Slideshow completed", isError = false)
                            Log.i(TAG, "Slideshow completed successfully")
                        }
                    }

                } catch (e: CancellationException) {
                    Log.d(TAG, "Slideshow cancelled")
                    withContext(Dispatchers.Main) {
                        isSlideShowActive = false
                    }
                } catch (e: Exception) {
                    logError("SLIDESHOW_ERROR", "Slideshow error: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        isSlideShowActive = false
                        showUserMessage("Slideshow error: ${e.message}", isError = true)
                    }
                }
            }
            
        } catch (e: Exception) {
            logError("SLIDESHOW_START", "Error starting slideshow: ${e.message}", e)
            showUserMessage("Error starting slideshow", isError = true)
            isSlideShowActive = false
        }
    }

    /**
     * Stop the slideshow
     */
    private fun stopSlideshow() {
        try {
            if (!isSlideShowActive && slideshowJob == null) {
                Log.d(TAG, "Slideshow already stopped")
                return
            }
            
            isSlideShowActive = false
            
            // Cancel slideshow job
            try {
                slideshowJob?.cancel()
                slideshowJob = null
                Log.d(TAG, "Slideshow job cancelled successfully")
            } catch (e: Exception) {
                logError("SLIDESHOW_STOP", "Error cancelling slideshow job: ${e.message}", e)
            }

            // Reset any zoom/scroll effects
            try {
                resetPageView()
            } catch (e: Exception) {
                logError("SLIDESHOW_STOP", "Error resetting page view: ${e.message}", e)
            }

            showUserMessage("Slideshow stopped", isError = false)
            Log.d(TAG, "Slideshow stopped successfully")
            
        } catch (e: Exception) {
            logError("SLIDESHOW_STOP", "Error stopping slideshow: ${e.message}", e)
            showUserMessage("Error stopping slideshow", isError = true)
            
            // Force stop slideshow state
            isSlideShowActive = false
            slideshowJob = null
        }
    }

    /**
     * Perform auto-scroll and zoom animation on current page
     */
    private suspend fun performAutoScrollAndZoom() {
        try {
            Log.d(TAG, "Starting auto-scroll and zoom animation sequence")
            
            // Validate binding
            if (!::binding.isInitialized) {
                logError("AUTO_SCROLL_ZOOM", "Binding not initialized for auto-scroll and zoom", null)
                return
            }
            
            withContext(Dispatchers.Main) {
                try {
                    val viewPager = binding.viewPager
                    
                    // Validate slideshow is still active
                    if (!isSlideShowActive) {
                        Log.d(TAG, "Slideshow no longer active, cancelling auto-scroll and zoom")
                        return@withContext
                    }

                    // Phase 1: Zoom in slightly and pan left
                    try {
                        Log.d(TAG, "Auto-scroll phase 1: Zoom in and pan left")
                        viewPager.animate()
                            .scaleX(1.3f)
                            .scaleY(1.3f)
                            .translationX(-100f)
                            .setDuration(1000)
                            .withStartAction {
                                Log.d(TAG, "Phase 1 animation started")
                            }
                            .start()
                    } catch (e: Exception) {
                        logError("AUTO_SCROLL_PHASE1", "Error in phase 1 animation: ${e.message}", e)
                        throw e
                    }

                    delay(1200)
                    
                    // Check if slideshow is still active before continuing
                    if (!isSlideShowActive) {
                        Log.d(TAG, "Slideshow cancelled during phase 1")
                        return@withContext
                    }

                    // Phase 2: Pan to center and zoom in more
                    try {
                        Log.d(TAG, "Auto-scroll phase 2: Pan to center and zoom in more")
                        viewPager.animate()
                            .scaleX(1.5f)
                            .scaleY(1.5f)
                            .translationX(0f)
                            .setDuration(800)
                            .start()
                    } catch (e: Exception) {
                        logError("AUTO_SCROLL_PHASE2", "Error in phase 2 animation: ${e.message}", e)
                        throw e
                    }

                    delay(1000)
                    
                    // Check if slideshow is still active before continuing
                    if (!isSlideShowActive) {
                        Log.d(TAG, "Slideshow cancelled during phase 2")
                        return@withContext
                    }

                    // Phase 3: Pan right and zoom out slightly
                    try {
                        Log.d(TAG, "Auto-scroll phase 3: Pan right and zoom out slightly")
                        viewPager.animate()
                            .scaleX(1.2f)
                            .scaleY(1.2f)
                            .translationX(100f)
                            .setDuration(800)
                            .start()
                    } catch (e: Exception) {
                        logError("AUTO_SCROLL_PHASE3", "Error in phase 3 animation: ${e.message}", e)
                        throw e
                    }

                    delay(1000)
                    
                    // Check if slideshow is still active before continuing
                    if (!isSlideShowActive) {
                        Log.d(TAG, "Slideshow cancelled during phase 3")
                        return@withContext
                    }

                    // Phase 4: Return to normal view
                    try {
                        Log.d(TAG, "Auto-scroll phase 4: Return to normal view")
                        viewPager.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .translationX(0f)
                            .setDuration(600)
                            .withEndAction {
                                Log.d(TAG, "Auto-scroll and zoom sequence completed")
                            }
                            .start()
                    } catch (e: Exception) {
                        logError("AUTO_SCROLL_PHASE4", "Error in phase 4 animation: ${e.message}", e)
                        throw e
                    }

                    delay(800)
                    
                    Log.d(TAG, "Auto-scroll and zoom animation sequence finished successfully")

                } catch (e: CancellationException) {
                    Log.d(TAG, "Auto-scroll and zoom cancelled")
                    // Reset view to normal state on cancellation
                    try {
                        binding.viewPager.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .translationX(0f)
                            .setDuration(300)
                            .start()
                    } catch (resetE: Exception) {
                        logError("AUTO_SCROLL_RESET", "Error resetting view after cancellation: ${resetE.message}", resetE)
                    }
                    throw e
                } catch (e: Exception) {
                    logError("AUTO_SCROLL_MAIN", "Error in main context auto-scroll and zoom: ${e.message}", e)
                    
                    // Attempt to reset view to normal state
                    try {
                        binding.viewPager.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .translationX(0f)
                            .setDuration(300)
                            .start()
                    } catch (resetE: Exception) {
                        logError("AUTO_SCROLL_ERROR_RESET", "Error resetting view after error: ${resetE.message}", resetE)
                    }
                    
                    throw e
                }
            }
            
        } catch (e: CancellationException) {
            Log.d(TAG, "Auto-scroll and zoom coroutine cancelled")
            throw e
        } catch (e: Exception) {
            logError("AUTO_SCROLL_ZOOM", "Critical error in auto-scroll and zoom: ${e.message}", e)
            throw e
        }
    }

    /**
     * Reset page view to normal state
     */
    private fun resetPageView() {
        try {
            Log.d(TAG, "Resetting page view to default state")
            
            // Validate binding
            if (!::binding.isInitialized) {
                logError("RESET_VIEW", "Binding not initialized for page view reset", null)
                return
            }
            
            // Reset view transformations
            try {
                binding.viewPager.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .translationX(0f)
                    .translationY(0f)
                    .setDuration(300)
                    .withEndAction {
                        Log.d(TAG, "Page view reset animation completed")
                    }
                    .start()
                    
                Log.d(TAG, "Page view reset animation started")
                
            } catch (e: Exception) {
                logError("RESET_VIEW_ANIMATE", "Error animating page view reset: ${e.message}", e)
                
                // Fallback: Set values directly without animation
                try {
                    binding.viewPager.scaleX = 1.0f
                    binding.viewPager.scaleY = 1.0f
                    binding.viewPager.translationX = 0f
                    binding.viewPager.translationY = 0f
                    Log.d(TAG, "Page view reset completed without animation")
                } catch (fallbackE: Exception) {
                    logError("RESET_VIEW_FALLBACK", "Error in fallback page view reset: ${fallbackE.message}", fallbackE)
                }
            }
            
        } catch (e: Exception) {
            logError("RESET_VIEW", "Critical error resetting page view: ${e.message}", e)
        }
    }

    private fun loadComic(uri: Uri) {
        Log.d(TAG, "Loading comic from URI: $uri")
        showLoading(true)
        
        lifecycleScope.launch(Dispatchers.IO) {
            val loadTime = measureTimeMillis {
                try {
                    // Validate URI
                    if (uri.scheme == null) {
                        logError("COMIC_LOAD", "Invalid URI scheme: $uri", null)
                        withContext(Dispatchers.Main) {
                            showUserMessage("Invalid comic file location", isError = true)
                            finish()
                        }
                        return@launch
                    }
                    
                    // Check file access
                    try {
                        contentResolver.openInputStream(uri)?.use { 
                            // Just checking if we can access the file
                        }
                    } catch (e: SecurityException) {
                        logError("COMIC_LOAD", "No permission to access file: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            showUserMessage("Permission denied to access comic file", isError = true)
                            finish()
                        }
                        return@launch
                    } catch (e: Exception) {
                        logError("COMIC_LOAD", "Cannot access file: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            showUserMessage("Cannot access comic file", isError = true)
                            finish()
                        }
                        return@launch
                    }
                    
                    // Extract comic pages
                    val extractedPages = comicExtractor.extractComic(uri)
                    
                    withContext(Dispatchers.Main) {
                        if (extractedPages.isEmpty()) {
                            logError("COMIC_LOAD", "No pages extracted from comic file", null)
                            showUserMessage("No readable pages found in comic file", isError = true)
                            finish()
                            return@withContext
                        }
                        
                        Log.d(TAG, "Successfully extracted ${extractedPages.size} pages")
                        comicPages = extractedPages
                        
                        try {
                            updateComicPages()
                            showLoading(false)
                            showUserMessage("Comic loaded successfully (${extractedPages.size} pages)", isError = false)
                        } catch (e: Exception) {
                            logError("COMIC_LOAD", "Error updating comic pages: ${e.message}", e)
                            showUserMessage("Error displaying comic pages", isError = true)
                            finish()
                        }
                    }
                    
                } catch (e: OutOfMemoryError) {
                    logError("COMIC_LOAD", "Out of memory while loading comic", e)
                    withContext(Dispatchers.Main) {
                        showUserMessage("Comic file too large for available memory", isError = true)
                        finish()
                    }
                } catch (e: Exception) {
                    logError("COMIC_LOAD", "Unexpected error loading comic: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        showUserMessage("Failed to load comic: ${e.message}", isError = true)
                        finish()
                    }
                }
            }
            
            Log.d(TAG, "Comic loading completed in ${loadTime}ms")
        }
    }

    private fun updateComicPages() {
        try {
            Log.d(TAG, "Updating comic pages display")
            
            // Validate comic pages
            if (comicPages.isEmpty()) {
                logError("UPDATE_PAGES", "No comic pages available to update", null)
                showUserMessage("No comic pages to display", isError = true)
                return
            }
            
            // Validate binding
            if (!::binding.isInitialized) {
                logError("UPDATE_PAGES", "Binding not initialized for page update", null)
                throw IllegalStateException("Binding not initialized")
            }
            
            // Convert paths to URIs with validation
            val imageResources = try {
                comicPages.mapIndexed { index, path ->
                    try {
                        val file = java.io.File(path)
                        if (!file.exists()) {
                            logError("UPDATE_PAGES", "Comic page file does not exist: $path", null, index)
                            throw IllegalArgumentException("Page file not found: $path")
                        }
                        
                        if (!file.canRead()) {
                            logError("UPDATE_PAGES", "Cannot read comic page file: $path", null, index)
                            throw IllegalArgumentException("Cannot read page file: $path")
                        }
                        
                        Uri.fromFile(file)
                    } catch (e: Exception) {
                        logError("UPDATE_PAGES_URI", "Error creating URI for page $index ($path): ${e.message}", e, index)
                        throw e
                    }
                }
            } catch (e: Exception) {
                logError("UPDATE_PAGES_MAPPING", "Error mapping comic pages to URIs: ${e.message}", e)
                throw e
            }
            
            // Create and set adapter
            try {
                comicPageAdapter = ComicPageAdapter(imageResources)
                binding.viewPager.adapter = comicPageAdapter
                
                Log.d(TAG, "Comic pages updated successfully: ${imageResources.size} pages")
                
            } catch (e: Exception) {
                logError("UPDATE_PAGES_ADAPTER", "Error setting comic page adapter: ${e.message}", e)
                throw e
            }
            
        } catch (e: Exception) {
            logError("UPDATE_PAGES", "Critical error updating comic pages: ${e.message}", e)
            showUserMessage("Error displaying comic pages", isError = true)
            throw e
        }
    }

    /**
     * Background pre-generation: OCR the next 3 pages and enqueue TTS synthesis
     * so narration is cached before the user gets there. Fully offline.
     */
    private fun preGenerateUpcomingPages(fromPosition: Int) {
        try {
            lifecycleScope.launch(Dispatchers.IO) {
                val upcoming = (fromPosition + 1..fromPosition + 3)
                    .filter { it in comicPages.indices }
                    .mapNotNull { idx ->
                        try {
                            val path = comicPages[idx]
                            if (path.isBlank() || !java.io.File(path).exists()) null
                            else textRecognitionHelper.extractTextFromImage(path)
                                .takeIf { it.isNotBlank() }
                        } catch (e: Exception) {
                            Log.w(TAG, "pre-gen OCR failed for page $idx: ${e.message}")
                            null
                        }
                    }
                if (upcoming.isNotEmpty() && ::narratorReader.isInitialized) {
                    narratorReader.preGenerateUpcoming(upcoming)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "preGenerateUpcomingPages failed: ${e.message}")
        }
    }

    private fun readCurrentPage() {
        // Validation checks
        if (currentPage >= comicPages.size) {
            logError("PAGE_READ", "Invalid page number: $currentPage >= ${comicPages.size}", null, currentPage)
            showUserMessage("Invalid page number", isError = true)
            return
        }
        
        if (isProcessing) {
            Log.d(TAG, "Already processing page $currentPage, ignoring request")
            return
        }

        Log.d(TAG, "readCurrentPage() called for page $currentPage")

        // Ensure TTS is ready before proceeding
        if (!tts.isTTSAvailable()) {
            logError("PAGE_READ", "TTS not available for page $currentPage", null, currentPage)
            showUserMessage("Text-to-speech not ready, please wait", isError = true)
            return
        }

        isProcessing = true
        showLoading(true)
        ttsOperationCount++
        
        lifecycleScope.launch(Dispatchers.IO) {
            val processingTime = measureTimeMillis {
                try {
                    // Validate page path
                    val pagePath = comicPages[currentPage]
                    if (pagePath.isBlank()) {
                        logError("PAGE_READ", "Empty page path for page $currentPage", null, currentPage)
                        withContext(Dispatchers.Main) {
                            showUserMessage("Invalid page file", isError = true)
                            isProcessing = false
                            showLoading(false)
                        }
                        return@launch
                    }
                    
                    // Check if page file exists
                    val pageFile = java.io.File(pagePath)
                    if (!pageFile.exists()) {
                        logError("PAGE_READ", "Page file does not exist: $pagePath", null, currentPage)
                        withContext(Dispatchers.Main) {
                            showUserMessage("Page file not found", isError = true)
                            isProcessing = false
                            showLoading(false)
                        }
                        return@launch
                    }
                    
                    Log.d(TAG, "Extracting text from page $currentPage: $pagePath")
                    val extractedText = textRecognitionHelper.extractTextFromImage(pagePath)
                    
                    withContext(Dispatchers.Main) {
                        Log.d(TAG, "Extracted text length: ${extractedText.length}")

                        if (extractedText.isBlank()) {
                            Log.i(TAG, "No text found on page $currentPage")
                            showUserMessage("No text found on this page", isError = false)
                            isProcessing = false
                            showLoading(false)

                            // If auto-play is enabled, move to next page
                            if (autoPlayEnabled && currentPage < comicPages.size - 1) {
                                try {
                                    binding.viewPager.currentItem = currentPage + 1
                                } catch (e: Exception) {
                                    logError("PAGE_READ", "Error moving to next page: ${e.message}", e, currentPage)
                                }
                            }
                            return@withContext
                        }

                        Log.d(TAG, "Starting narrator for extracted text (${extractedText.length} chars)...")

                        // Narrator: cache → on-device Sherpa-ONNX → HybridTTS fallback
                        try {
                            lifecycleScope.launch(Dispatchers.IO) {
                                narratorReader.narrate(extractedText, object : NarratorReader.PlaybackCallback {
                                    override fun onStart() {
                                        Log.d(TAG, "Narrator started for page $currentPage")
                                        showLoading(false)
                                    }

                                    override fun onComplete() {
                                        Log.d(TAG, "Narrator completed for page $currentPage")
                                        successfulTtsOperations++
                                        isProcessing = false

                                        // If auto-play is enabled, move to next page
                                        if (autoPlayEnabled && currentPage < comicPages.size - 1) {
                                            try {
                                                binding.viewPager.currentItem = currentPage + 1
                                            } catch (e: Exception) {
                                                logError("PAGE_READ", "Error auto-advancing page: ${e.message}", e, currentPage)
                                            }
                                        }
                                    }

                                    override fun onError(message: String) {
                                        logError("TTS_ERROR", "Narrator error on page $currentPage: $message", null, currentPage)
                                        showUserMessage("Text-to-speech error: $message", isError = true)
                                        isProcessing = false
                                        showLoading(false)
                                    }
                                })
                            }
                        } catch (e: Exception) {
                            logError("TTS_INIT", "Error starting narrator for page $currentPage: ${e.message}", e, currentPage)
                            showUserMessage("Error starting text-to-speech", isError = true)
                            isProcessing = false
                            showLoading(false)
                        }
                    }
                    
                } catch (e: OutOfMemoryError) {
                    logError("PAGE_READ", "Out of memory processing page $currentPage", e, currentPage)
                    withContext(Dispatchers.Main) {
                        showUserMessage("Insufficient memory to process page", isError = true)
                        isProcessing = false
                        showLoading(false)
                    }
                } catch (e: SecurityException) {
                    logError("PAGE_READ", "Security exception processing page $currentPage: ${e.message}", e, currentPage)
                    withContext(Dispatchers.Main) {
                        showUserMessage("Permission denied accessing page", isError = true)
                        isProcessing = false
                        showLoading(false)
                    }
                } catch (e: Exception) {
                    logError("PAGE_READ", "Unexpected error processing page $currentPage: ${e.message}", e, currentPage)
                    withContext(Dispatchers.Main) {
                        showUserMessage("Error processing page: ${e.message}", isError = true)
                        isProcessing = false
                        showLoading(false)
                    }
                }
            }
            
            // Record processing time
            pageProcessingTimes[currentPage] = processingTime
            Log.d(TAG, "Page $currentPage processing completed in ${processingTime}ms")
        }
    }

    private fun showLoading(show: Boolean) {
        try {
            Log.d(TAG, "Setting loading visibility: $show")
            
            // Validate binding
            if (!::binding.isInitialized) {
                logError("SHOW_LOADING", "Binding not initialized for loading display", null)
                return
            }
            
            // Validate progress bar exists
            try {
                binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
                Log.d(TAG, "Loading visibility set successfully: ${if (show) "VISIBLE" else "GONE"}")
            } catch (e: Exception) {
                logError("SHOW_LOADING_UI", "Error setting progress bar visibility: ${e.message}", e)
                // Don't throw here as this is a UI convenience method
            }
            
        } catch (e: Exception) {
            logError("SHOW_LOADING", "Critical error in showLoading: ${e.message}", e)
            // Don't throw here as this is a UI convenience method
        }
    }
    
    /**
     * Log error with detailed tracking
     */
    private fun logError(errorType: String, message: String, exception: Throwable?, pageNumber: Int? = null) {
        errorCount++
        
        val errorRecord = ErrorRecord(
            timestamp = System.currentTimeMillis(),
            errorType = errorType,
            message = message,
            exception = exception?.toString(),
            pageNumber = pageNumber
        )
        
        // Add to error log with size limit
        errorLog.add(errorRecord)
        if (errorLog.size > MAX_ERROR_LOG_SIZE) {
            errorLog.removeAt(0)
        }
        
        // Log to Android Log
        if (exception != null) {
            Log.e(TAG, "[$errorType] $message", exception)
        } else {
            Log.e(TAG, "[$errorType] $message")
        }
    }
    
    /**
     * Show user-friendly message
     */
    private fun showUserMessage(message: String, isError: Boolean) {
        val duration = if (isError) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        Toast.makeText(this, message, duration).show()
        
        if (isError) {
            Log.w(TAG, "User error message: $message")
        } else {
            Log.i(TAG, "User info message: $message")
        }
    }
    
    /**
     * Get comprehensive performance metrics
     */
    fun getPerformanceMetrics(): Map<String, Any> {
        return mapOf(
            "initializationTime" to initializationTime,
            "errorCount" to errorCount,
            "totalErrors" to errorLog.size,
            "pageProcessingTimes" to pageProcessingTimes.toMap(),
            "averagePageProcessingTime" to if (pageProcessingTimes.isNotEmpty()) {
                pageProcessingTimes.values.average()
            } else 0.0,
            "ttsOperationCount" to ttsOperationCount,
            "successfulTtsOperations" to successfulTtsOperations,
            "ttsSuccessRate" to if (ttsOperationCount > 0) {
                (successfulTtsOperations.toDouble() / ttsOperationCount) * 100
            } else 0.0,
            "recentErrors" to errorLog.filter { 
                System.currentTimeMillis() - it.timestamp < 24 * 60 * 60 * 1000 
            }.size,
            "errorsByType" to errorLog.groupBy { it.errorType }.mapValues { it.value.size },
            "lastErrorTimestamp" to (errorLog.lastOrNull()?.timestamp ?: 0L),
            "memoryUsage" to Runtime.getRuntime().let {
                mapOf(
                    "totalMemory" to it.totalMemory(),
                    "freeMemory" to it.freeMemory(),
                    "usedMemory" to (it.totalMemory() - it.freeMemory()),
                    "maxMemory" to it.maxMemory()
                )
            }
        )
    }
    
    /**
     * Get detailed error log for debugging
     */
    fun getErrorLog(): List<ErrorRecord> {
        return errorLog.toList()
    }
    
    /**
     * Clear error log and reset counters
     */
    fun resetMetrics() {
        Log.i(TAG, "Resetting ReaderActivity metrics")
        errorLog.clear()
        errorCount = 0
        pageProcessingTimes.clear()
        ttsOperationCount = 0
        successfulTtsOperations = 0
        initializationTime = 0L
    }
    
    /**
     * Get health status based on recent performance
     */
    fun getHealthStatus(): String {
        val recentErrors = errorLog.filter { 
            System.currentTimeMillis() - it.timestamp < 60 * 60 * 1000 // Last hour
        }.size
        
        val criticalErrors = errorLog.count { 
            it.errorType in listOf("ONCREATE_CRITICAL", "COMIC_LOAD", "TTS_CRITICAL")
        }
        
        val ttsSuccessRate = if (ttsOperationCount > 0) {
            (successfulTtsOperations.toDouble() / ttsOperationCount) * 100
        } else 100.0
        
        return when {
            criticalErrors > 0 -> "CRITICAL"
            recentErrors == 0 && ttsSuccessRate >= 90 -> "EXCELLENT"
            recentErrors <= 2 && ttsSuccessRate >= 80 -> "GOOD"
            recentErrors <= 5 && ttsSuccessRate >= 60 -> "FAIR"
            else -> "POOR"
        }
    }
}
