package com.example.comicbooktts

import android.app.Application
import android.util.Log

class ComicReaderApplication : Application() {
    
    companion object {
        private const val TAG = "ComicReaderApp"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        try {
            Log.d(TAG, "ComicReader Application starting...")
            
            // Initialize any global components here if needed
            // For now, just log that we're starting
            
            Log.d(TAG, "ComicReader Application initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing ComicReader Application: ${e.message}", e)
            // Don't crash the app, just log the error
        }
    }
}
