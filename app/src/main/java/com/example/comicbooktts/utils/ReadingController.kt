package com.example.comicbooktts.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*

/**
 * Enhanced reading controller with intelligent timing and progression
 */
class ReadingController(private val context: Context) {
    
    private val preferenceManager = PreferenceManager(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var readingJob: Job? = null
    private var currentPageIndex = 0
    private var totalPages = 0
    private var isAutoReading = false
    private var isPaused = false
    
    companion object {
        private const val TAG = "ReadingController"
        private const val DEFAULT_PAGE_DELAY_MS = 3000L
        private const val DEFAULT_PANEL_DELAY_MS = 1500L
        private const val MIN_READING_SPEED = 0.5f
        private const val MAX_READING_SPEED = 3.0f
    }
    
    interface ReadingCallback {
        fun onPageChanged(pageIndex: Int)
        fun onReadingStarted()
        fun onReadingPaused()
        fun onReadingResumed()
        fun onReadingStopped()
        fun onReadingCompleted()
        fun onError(message: String)
    }
    
    /**
     * Start automatic reading with intelligent timing
     */
    fun startAutoReading(
        totalPages: Int,
        startPage: Int = 0,
        callback: ReadingCallback
    ) {
        if (isAutoReading) {
            Log.w(TAG, "Auto reading already in progress")
            return
        }
        
        this.totalPages = totalPages
        this.currentPageIndex = startPage
        this.isAutoReading = true
        this.isPaused = false
        
        callback.onReadingStarted()
        
        readingJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                while (currentPageIndex < totalPages && isAutoReading && !isPaused) {
                    callback.onPageChanged(currentPageIndex)
                    
                    // Calculate reading time based on content and user preferences
                    val readingTime = calculateReadingTime(currentPageIndex)
                    
                    delay(readingTime)
                    
                    if (isAutoReading && !isPaused) {
                        currentPageIndex++
                    }
                }
                
                if (currentPageIndex >= totalPages) {
                    callback.onReadingCompleted()
                } else {
                    callback.onReadingStopped()
                }
                
            } catch (e: CancellationException) {
                Log.d(TAG, "Reading cancelled")
                callback.onReadingStopped()
            } catch (e: Exception) {
                Log.e(TAG, "Error during auto reading: ${e.message}")
                callback.onError("Reading error: ${e.message}")
            } finally {
                isAutoReading = false
                isPaused = false
            }
        }
    }
    
    /**
     * Pause automatic reading
     */
    fun pauseReading(callback: ReadingCallback) {
        if (isAutoReading && !isPaused) {
            isPaused = true
            callback.onReadingPaused()
        }
    }
    
    /**
     * Resume automatic reading
     */
    fun resumeReading(callback: ReadingCallback) {
        if (isAutoReading && isPaused) {
            isPaused = false
            callback.onReadingResumed()
        }
    }
    
    /**
     * Stop automatic reading
     */
    fun stopReading(callback: ReadingCallback) {
        if (isAutoReading) {
            isAutoReading = false
            isPaused = false
            readingJob?.cancel()
            readingJob = null
            callback.onReadingStopped()
        }
    }
    
    /**
     * Skip to next page during auto reading
     */
    fun skipToNextPage() {
        if (isAutoReading && currentPageIndex < totalPages - 1) {
            currentPageIndex++
        }
    }
    
    /**
     * Go back to previous page during auto reading
     */
    fun skipToPreviousPage() {
        if (isAutoReading && currentPageIndex > 0) {
            currentPageIndex--
        }
    }
    
    /**
     * Calculate optimal reading time based on content analysis
     */
    private suspend fun calculateReadingTime(pageIndex: Int): Long {
        return withContext(Dispatchers.Default) {
            try {
                // Base reading time from user preferences
                val baseTime = getBaseReadingTime()
                val readingSpeed = getReadingSpeed()
                
                // Adjust time based on reading speed preference
                val adjustedTime = (baseTime / readingSpeed).toLong()
                
                // Ensure minimum and maximum bounds
                adjustedTime.coerceIn(1000L, 30000L)
                
            } catch (e: Exception) {
                Log.w(TAG, "Error calculating reading time, using default: ${e.message}")
                DEFAULT_PAGE_DELAY_MS
            }
        }
    }
    
    /**
     * Get base reading time from user preferences
     */
    private fun getBaseReadingTime(): Long {
        // This could be expanded to analyze actual page content
        // For now, use user preference or default
        return DEFAULT_PAGE_DELAY_MS
    }
    
    /**
     * Get reading speed multiplier from user preferences
     */
    private fun getReadingSpeed(): Float {
        // This would come from user settings
        // For now, return default speed
        return 1.0f
    }
    
    /**
     * Check if currently auto reading
     */
    fun isAutoReading(): Boolean = isAutoReading
    
    /**
     * Check if reading is paused
     */
    fun isPaused(): Boolean = isPaused
    
    /**
     * Get current page index
     */
    fun getCurrentPage(): Int = currentPageIndex
    
    /**
     * Get total pages
     */
    fun getTotalPages(): Int = totalPages
    
    /**
     * Get reading progress as percentage
     */
    fun getProgress(): Float {
        return if (totalPages > 0) {
            (currentPageIndex.toFloat() / totalPages.toFloat()) * 100f
        } else {
            0f
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        readingJob?.cancel()
        readingJob = null
        isAutoReading = false
        isPaused = false
    }
}
