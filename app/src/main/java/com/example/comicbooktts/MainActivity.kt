package com.example.comicbooktts

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.comicbooktts.databinding.ActivityMainBinding
import com.example.comicbooktts.models.ComicBook
import com.example.comicbooktts.utils.PreferenceManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.system.measureTimeMillis

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var recentComicsAdapter: RecentComicsAdapter
    private lateinit var preferenceManager: PreferenceManager
    private val recentComics = mutableListOf<ComicBook>()
    
    // Error tracking and performance monitoring
    private var initializationTime: Long = 0L
    private var errorCount: Int = 0
    private val errorLog = mutableListOf<ErrorRecord>()
    
    companion object {
        private const val TAG = "MainActivity"
        private const val MAX_ERROR_LOG_SIZE = 50
    }
    
    /**
     * Data class for tracking errors
     */
    data class ErrorRecord(
        val timestamp: Long,
        val errorType: String,
        val message: String,
        val stackTrace: String?
    )

    private val openComicLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            Log.d(TAG, "File picker result: ${result.resultCode}")
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    Log.d(TAG, "Selected file URI: $uri")
                    openComic(uri)
                } ?: run {
                    Log.w(TAG, "No file selected")
                    showUserMessage("No file selected")
                }
            } else {
                Log.d(TAG, "File picker cancelled or failed")
            }
        } catch (e: Exception) {
            logError("FILE_PICKER_CALLBACK", "Error in file picker callback: ${e.message}", e)
            showUserMessage("Error processing file selection: ${e.message}")
        }
    }
    
    /**
     * Log error with tracking
     */
    private fun logError(errorType: String, message: String, exception: Exception? = null) {
        errorCount++
        val errorRecord = ErrorRecord(
            timestamp = System.currentTimeMillis(),
            errorType = errorType,
            message = message,
            stackTrace = exception?.stackTraceToString()
        )
        
        // Maintain error log size
        if (errorLog.size >= MAX_ERROR_LOG_SIZE) {
            errorLog.removeAt(0)
        }
        errorLog.add(errorRecord)
        
        Log.e(TAG, "[$errorType] $message", exception)
    }
    
    /**
     * Show user-friendly messages with error handling
     */
    private fun showUserMessage(message: String, isLong: Boolean = false) {
        try {
            val duration = if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            Toast.makeText(this, message, duration).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show toast message: $message", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate() called")
        super.onCreate(savedInstanceState)

        initializationTime = measureTimeMillis {
            try {
                Log.d(TAG, "Inflating layout...")
                binding = ActivityMainBinding.inflate(layoutInflater)
                setContentView(binding.root)
                Log.d(TAG, "ViewBinding initialized successfully")

                // Set up toolbar safely
                try {
                    Log.d(TAG, "Setting up toolbar...")
                    setSupportActionBar(binding.toolbar)
                    supportActionBar?.title = getString(R.string.app_name)
                    Log.d(TAG, "Toolbar setup complete")
                } catch (e: Exception) {
                    logError("TOOLBAR_SETUP", "Error setting up toolbar: ${e.message}", e)
                    // Continue without toolbar if it fails
                }

                Log.d(TAG, "Initializing PreferenceManager...")
                try {
                    preferenceManager = PreferenceManager(this)
                    Log.d(TAG, "PreferenceManager initialized")
                } catch (e: Exception) {
                    logError("PREFERENCE_MANAGER_INIT", "Failed to initialize PreferenceManager: ${e.message}", e)
                    throw e // This is critical, can't continue without preferences
                }

                Log.d(TAG, "Setting up recent comics list...")
                setupRecentComicsList()
                Log.d(TAG, "Recent comics list setup complete")

                Log.d(TAG, "Setting up click listeners...")
                setupClickListeners()
                Log.d(TAG, "Click listeners setup complete")

                // Handle intent if app was opened with a comic file
                intent?.data?.let { uri ->
                    Log.d(TAG, "Opening comic from intent: $uri")
                    try {
                        openComic(uri)
                    } catch (e: Exception) {
                        logError("INTENT_COMIC_OPEN", "Failed to open comic from intent: ${e.message}", e)
                        showUserMessage("Error opening comic file: ${e.message}", true)
                    }
                }

                Log.d(TAG, "MainActivity onCreate completed successfully in ${initializationTime}ms")
            } catch (e: Exception) {
                logError("ONCREATE_CRITICAL", "Critical error in onCreate: ${e.message}", e)
                
                // Try to show error to user
                showUserMessage("Error initializing app: ${e.message}", true)
                
                // If it's a critical error, we might need to finish the activity
                if (e is OutOfMemoryError || e is SecurityException) {
                    Log.e(TAG, "Critical system error, finishing activity")
                    finish()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadRecentComics()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return try {
            menuInflater.inflate(R.menu.main_menu, menu)
            true
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error creating options menu: ${e.message}")
            false
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupRecentComicsList() {
        try {
            recentComicsAdapter = RecentComicsAdapter(recentComics) { comic ->
                try {
                    val file = File(comic.filePath)
                    if (file.exists()) {
                        val uri = Uri.fromFile(file)
                        openComic(uri)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error opening comic: ${e.message}")
                    android.widget.Toast.makeText(this, "Error opening comic: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }

            binding.rvRecentComics.apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                adapter = recentComicsAdapter
            }

            loadRecentComics()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error setting up recent comics list: ${e.message}", e)
            // Hide the RecyclerView and show the no comics message
            try {
                binding.tvNoComics.visibility = View.VISIBLE
                binding.rvRecentComics.visibility = View.GONE
            } catch (viewError: Exception) {
                android.util.Log.e("MainActivity", "Error updating views: ${viewError.message}")
            }
        }
    }

    private fun loadRecentComics() {
        try {
            val savedComics = preferenceManager.getRecentComics()
            recentComics.clear()
            recentComics.addAll(savedComics)

            try {
                recentComicsAdapter.notifyDataSetChanged()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error updating adapter: ${e.message}")
            }

            if (recentComics.isEmpty()) {
                binding.tvNoComics.visibility = View.VISIBLE
                binding.rvRecentComics.visibility = View.GONE
            } else {
                binding.tvNoComics.visibility = View.GONE
                binding.rvRecentComics.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error loading recent comics: ${e.message}", e)
            // Show no comics message as fallback
            try {
                binding.tvNoComics.visibility = View.VISIBLE
                binding.rvRecentComics.visibility = View.GONE
            } catch (viewError: Exception) {
                android.util.Log.e("MainActivity", "Error updating views in loadRecentComics: ${viewError.message}")
            }
        }
    }

    private fun setupClickListeners() {
        try {
            binding.btnOpenComic.setOnClickListener {
                android.util.Log.d("MainActivity", "Open Comic button clicked")
                openFilePicker()
            }
            android.util.Log.d("MainActivity", "Click listeners setup complete")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error setting up click listeners: ${e.message}", e)
        }
    }

    private fun openFilePicker() {
        try {
            android.util.Log.d("MainActivity", "Opening file picker...")
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                // Add MIME types for comic book files
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                    "application/zip",
                    "application/x-zip-compressed",
                    "application/x-cbz",
                    "application/x-cbr",
                    "application/x-rar-compressed",
                    "application/vnd.comicbook+zip",
                    "application/vnd.comicbook-rar"
                ))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }

            openComicLauncher.launch(intent)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error opening file picker: ${e.message}", e)
            android.widget.Toast.makeText(this, "Error opening file picker: ${e.message}", android.widget.Toast.LENGTH_LONG).show()

            // Fallback: try a simpler file picker
            try {
                val fallbackIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                openComicLauncher.launch(Intent.createChooser(fallbackIntent, "Select Comic File"))
            } catch (fallbackError: Exception) {
                android.util.Log.e("MainActivity", "Fallback file picker also failed: ${fallbackError.message}")
                android.widget.Toast.makeText(this, "Unable to open file picker. Please check your file manager app.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openComic(uri: Uri) {
        try {
            Log.d(TAG, "Opening comic: $uri")
            
            // Validate URI
            if (uri.toString().isEmpty()) {
                throw IllegalArgumentException("Empty URI provided")
            }
            
            // Check if we can access the URI
            try {
                contentResolver.query(uri, null, null, null, null)?.use {
                    if (!it.moveToFirst()) {
                        throw IllegalStateException("Cannot access file at URI: $uri")
                    }
                }
            } catch (e: SecurityException) {
                logError("URI_ACCESS", "Security exception accessing URI: ${e.message}", e)
                throw IllegalStateException("Permission denied accessing file", e)
            } catch (e: Exception) {
                logError("URI_VALIDATION", "Error validating URI: ${e.message}", e)
                throw IllegalStateException("Cannot access file at URI: $uri", e)
            }
            
            // Get file information
            val fileName = try {
                getFileName(uri)
            } catch (e: Exception) {
                logError("FILENAME_EXTRACTION", "Error getting filename: ${e.message}", e)
                "Unknown Comic"
            }
            
            Log.d(TAG, "Comic filename: $fileName")
            
            // Save to recent comics
            try {
                val filePath = uri.toString()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val lastRead = dateFormat.format(Date())

                val comic = ComicBook(
                    title = fileName,
                    filePath = filePath,
                    lastReadDate = lastRead,
                    coverImagePath = null
                )

                preferenceManager.addRecentComic(comic)
                Log.d(TAG, "Added comic to recent list: $fileName")
            } catch (e: Exception) {
                logError("RECENT_COMICS_SAVE", "Error saving to recent comics: ${e.message}", e)
                // Continue even if we can't save to recent comics
            }

            // Open the reader activity
            try {
                val readerIntent = Intent(this, ReaderActivity::class.java).apply {
                    data = uri
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                
                // Verify the intent can be resolved
                if (readerIntent.resolveActivity(packageManager) == null) {
                    throw IllegalStateException("ReaderActivity not found or not properly configured")
                }
                
                startActivity(readerIntent)
                Log.d(TAG, "Successfully launched ReaderActivity for: $fileName")
            } catch (e: Exception) {
                logError("READER_LAUNCH", "Error launching ReaderActivity: ${e.message}", e)
                throw IllegalStateException("Cannot open comic reader", e)
            }
            
        } catch (e: IllegalArgumentException) {
            showUserMessage("Invalid comic file: ${e.message}")
        } catch (e: IllegalStateException) {
            showUserMessage("Cannot open comic: ${e.message}", true)
        } catch (e: Exception) {
            logError("OPEN_COMIC_GENERAL", "Unexpected error opening comic: ${e.message}", e)
            showUserMessage("Unexpected error opening comic: ${e.message}", true)
        }
    }

    private fun getFileName(uri: Uri): String {
        return try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && it.moveToFirst()) {
                    val fileName = it.getString(nameIndex)
                    if (!fileName.isNullOrBlank()) {
                        return fileName
                    }
                }
            }
            // Fallback to URI path segment
            uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: "Unknown Comic"
        } catch (e: Exception) {
            logError("GET_FILENAME", "Error extracting filename from URI: ${e.message}", e)
            "Unknown Comic"
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
        Log.i(TAG, "Resetting MainActivity metrics")
        errorLog.clear()
        errorCount = 0
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
            it.errorType in listOf("ONCREATE_CRITICAL", "PREFERENCE_MANAGER_INIT", "READER_LAUNCH")
        }
        
        return when {
            criticalErrors > 0 -> "CRITICAL"
            recentErrors == 0 -> "EXCELLENT"
            recentErrors <= 2 -> "GOOD"
            recentErrors <= 5 -> "FAIR"
            else -> "POOR"
        }
    }
}
