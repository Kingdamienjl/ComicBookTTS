package com.example.comicbooktts.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class TextRecognitionHelper(private val context: Context) {

    private val textRecognizer = try {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    } catch (e: IllegalStateException) {
        Log.w(TAG, "ML Kit TextRecognizer not available (likely in test environment): ${e.message}")
        null
    } catch (e: Exception) {
        Log.w(TAG, "ML Kit TextRecognizer initialization failed: ${e.message}")
        null
    }
    private val preferenceManager = PreferenceManager(context)
    
    // Performance and error tracking
    private var totalProcessingTime = 0L
    private var successfulExtractions = 0
    private var failedExtractions = 0
    private var totalRetryAttempts = 0
    private val errorLog = mutableListOf<ErrorRecord>()
    
    // Preprocessing cache for performance optimization
    private val preprocessingCache = mutableMapOf<String, Bitmap>()
    private val imageAnalysisCache = mutableMapOf<String, ImageCharacteristics>()
    
    // Image analysis data class
    data class ImageCharacteristics(
        val contrast: Float,
        val noiseLevel: Float,
        val edgeDensity: Float,
        val averageBrightness: Float,
        val needsEnhancement: Boolean
    )
    
    data class ErrorRecord(
        val timestamp: Long,
        val errorType: String,
        val errorMessage: String,
        val imagePath: String?,
        val attempt: Int
    )

    companion object {
        private const val TAG = "TextRecognitionHelper"
        private const val MIN_TEXT_CONFIDENCE = 0.5f // Lowered for better comic text detection
        private const val MIN_PANEL_WIDTH = 30 // Smaller panels for comic bubbles
        private const val MIN_PANEL_HEIGHT = 20
        private const val PANEL_MERGE_THRESHOLD = 15
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val CONFIDENCE_BOOST_THRESHOLD = 0.7f

        // Enhanced OCR constants for comics
        private const val HIGH_CONFIDENCE_THRESHOLD = 0.75f
        private const val MEDIUM_CONFIDENCE_THRESHOLD = 0.55f
        private const val LOW_CONFIDENCE_THRESHOLD = 0.35f
        private const val MIN_WORD_LENGTH = 1 // Allow single characters for sound effects
        
        // Comic-specific constants
        private const val MAX_IMAGE_DIMENSION = 2048
        private const val MIN_BUBBLE_SIZE = 25
        private const val TEXT_ENHANCEMENT_STRENGTH = 1.5f
        private const val CONTRAST_BOOST = 1.3f
        
        // Performance optimization constants
        private const val ENABLE_ADVANCED_PREPROCESSING = true
        private const val PREPROCESSING_CACHE_SIZE = 10
        private const val MIN_IMAGE_SIZE_FOR_ENHANCEMENT = 100
        private const val MAX_PROCESSING_TIME_MS = 5000L
        
        // Adaptive preprocessing thresholds
        private const val LOW_CONTRAST_THRESHOLD = 50
        private const val HIGH_NOISE_THRESHOLD = 0.3f
        private const val EDGE_DENSITY_THRESHOLD = 0.1f
    }

    /**
     * Enhanced data class representing a text panel with comic-specific properties
     */
    data class TextPanel(
        val text: String,
        val boundingBox: Rect,
        val confidence: Float,
        val isDialogue: Boolean = false,
        val isNarration: Boolean = false,
        val isSoundEffect: Boolean = false,
        val isThoughtBubble: Boolean = false,
        val isWhisper: Boolean = false,
        val wordCount: Int = text.split("\\s+".toRegex()).size,
        val qualityScore: Float = calculateQualityScore(text, confidence),
        val readingOrder: Int = 0
    ) {
        companion object {
            private fun calculateQualityScore(text: String, confidence: Float): Float {
                val lengthScore = minOf(text.length / 40.0f, 1.0f) // Optimized for comic text
                val wordScore = minOf(text.split("\\s+".toRegex()).size / 8.0f, 1.0f)
                val alphaScore = text.count { it.isLetterOrDigit() }.toFloat() / max(text.length, 1)
                val punctuationScore = text.count { it in "!?.,;:" }.toFloat() / max(text.length, 1)
                
                return (confidence * 0.4f + lengthScore * 0.25f + wordScore * 0.2f + 
                        alphaScore * 0.1f + punctuationScore * 0.05f).coerceIn(0f, 1f)
            }
        }
    }

    /**
     * Extract text from an image file using ML Kit OCR with enhanced reliability
     * @param imagePath Path to the image file
     * @return Extracted text ordered by reading sequence or empty string if no text found
     */
    suspend fun extractTextFromImage(imagePath: String): String = suspendCoroutine { continuation ->
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting text extraction for: $imagePath")
        
        try {
            // Validate input parameters
            if (imagePath.isBlank()) {
                logError("INVALID_INPUT", "Empty image path provided", imagePath, 0)
                failedExtractions++
                continuation.resume("")
                return@suspendCoroutine
            }
            
            val file = File(imagePath)
            if (!file.exists()) {
                logError("FILE_NOT_FOUND", "Image file does not exist: $imagePath", imagePath, 0)
                failedExtractions++
                continuation.resume("")
                return@suspendCoroutine
            }
            
            if (!file.canRead()) {
                logError("FILE_ACCESS_DENIED", "Cannot read image file: $imagePath", imagePath, 0)
                failedExtractions++
                continuation.resume("")
                return@suspendCoroutine
            }
            
            extractTextWithRetry(imagePath, 0) { result ->
                val processingTime = System.currentTimeMillis() - startTime
                totalProcessingTime += processingTime
                
                if (result.isNotEmpty()) {
                    successfulExtractions++
                    Log.i(TAG, "Text extraction successful in ${processingTime}ms. Text length: ${result.length}")
                } else {
                    failedExtractions++
                    Log.w(TAG, "Text extraction returned empty result in ${processingTime}ms")
                }
                
                continuation.resume(result)
            }
        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            totalProcessingTime += processingTime
            failedExtractions++
            logError("EXTRACTION_EXCEPTION", "Unexpected error during text extraction: ${e.message}", imagePath, 0)
            Log.e(TAG, "Critical error in extractTextFromImage", e)
            continuation.resume("")
        }
    }

    /**
     * Extract text with retry logic for improved reliability
     */
    private fun extractTextWithRetry(imagePath: String, attempt: Int, callback: (String) -> Unit) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Attempting text extraction (attempt ${attempt + 1}/$MAX_RETRY_ATTEMPTS) for: $imagePath")
        
        try {
            val file = File(imagePath)
            if (!file.exists()) {
                logError("FILE_NOT_FOUND", "File not found during retry", imagePath, attempt)
                callback("")
                return
            }

            // Load bitmap with optimized size for OCR
            val bitmap = loadOptimizedBitmap(file.absolutePath)
            if (bitmap == null) {
                logError("BITMAP_LOAD_FAILED", "Failed to load optimized bitmap", imagePath, attempt)
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    totalRetryAttempts++
                    Log.w(TAG, "Bitmap loading failed, retrying... (attempt ${attempt + 1})")
                    extractTextWithRetry(imagePath, attempt + 1, callback)
                } else {
                    callback("")
                }
                return
            }
            
            try {
                Log.d(TAG, "Bitmap loaded successfully: ${bitmap.width}x${bitmap.height}, config: ${bitmap.config}")
            } catch (e: NoSuchMethodError) {
                Log.d(TAG, "Bitmap loaded successfully: ${bitmap.width}x${bitmap.height}")
            }

            val image = InputImage.fromBitmap(bitmap, 0)
            
            // Handle test environment where textRecognizer might be null
            if (textRecognizer == null) {
                Log.w(TAG, "TextRecognizer not available, returning empty result")
                bitmap.recycle()
                callback("")
                return
            }
            
            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    try {
                        val processingTime = System.currentTimeMillis() - startTime
                        Log.d(TAG, "ML Kit processing completed in ${processingTime}ms")
                        
                        val panels = extractTextPanels(visionText, bitmap.width, bitmap.height)
                        Log.d(TAG, "Extracted ${panels.size} text panels")
                        
                        val filteredPanels = filterLowQualityPanels(panels)
                        Log.d(TAG, "Filtered to ${filteredPanels.size} high-quality panels")

                        // Check if we got good quality results or should retry
                        val averageConfidence = if (filteredPanels.isNotEmpty()) {
                            filteredPanels.map { it.confidence }.average().toFloat()
                        } else 0f

                        if (averageConfidence < LOW_CONFIDENCE_THRESHOLD && attempt < MAX_RETRY_ATTEMPTS) {
                            totalRetryAttempts++
                            Log.w(TAG, "Low confidence ($averageConfidence), retrying... (attempt ${attempt + 1})")
                            logError("LOW_CONFIDENCE", "Average confidence $averageConfidence below threshold", imagePath, attempt)
                            bitmap.recycle()
                            extractTextWithRetry(imagePath, attempt + 1, callback)
                            return@addOnSuccessListener
                        }

                        val orderedText = orderTextByReadingSequence(filteredPanels)
                        val processedText = preprocessTextForTTS(orderedText)
                        bitmap.recycle()

                        Log.i(TAG, "OCR completed successfully with ${filteredPanels.size} panels, avg confidence: $averageConfidence")
                        callback(processedText)
                    } catch (e: Exception) {
                        bitmap.recycle()
                        logError("PROCESSING_EXCEPTION", "Error during text processing: ${e.message}", imagePath, attempt)
                        Log.e(TAG, "Error processing OCR results", e)
                        
                        if (attempt < MAX_RETRY_ATTEMPTS) {
                            totalRetryAttempts++
                            extractTextWithRetry(imagePath, attempt + 1, callback)
                        } else {
                            callback("")
                        }
                    }
                }
                .addOnFailureListener { e ->
                    bitmap.recycle()
                    logError("ML_KIT_FAILURE", "ML Kit text recognition failed: ${e.message}", imagePath, attempt)
                    Log.e(TAG, "Text recognition failed (attempt ${attempt + 1}): ${e.message}", e)

                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        totalRetryAttempts++
                        extractTextWithRetry(imagePath, attempt + 1, callback)
                    } else {
                        callback("")
                    }
                }
        } catch (e: Exception) {
            logError("RETRY_EXCEPTION", "Unexpected error during retry: ${e.message}", imagePath, attempt)
            Log.e(TAG, "Critical error processing image (attempt ${attempt + 1}): ${e.message}", e)
            
            if (attempt < MAX_RETRY_ATTEMPTS) {
                totalRetryAttempts++
                extractTextWithRetry(imagePath, attempt + 1, callback)
            } else {
                callback("")
            }
        }
    }

    /**
     * Extract text from image with detailed panel information
     * @param imagePath Path to the image file
     * @return List of TextPanel objects with position and content information
     */
    suspend fun extractTextPanelsFromImage(imagePath: String): List<TextPanel> = suspendCoroutine { continuation ->
        try {
            val file = File(imagePath)
            if (!file.exists()) {
                continuation.resume(emptyList())
                return@suspendCoroutine
            }

            val bitmap = loadOptimizedBitmap(file.absolutePath)
            if (bitmap == null) {
                continuation.resume(emptyList())
                return@suspendCoroutine
            }

            val image = InputImage.fromBitmap(bitmap, 0)
            
            // Handle test environment where textRecognizer might be null
            if (textRecognizer == null) {
                Log.w(TAG, "TextRecognizer not available, returning empty panels")
                bitmap.recycle()
                continuation.resume(emptyList())
                return@suspendCoroutine
            }
            
            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val panels = extractTextPanels(visionText, bitmap.width, bitmap.height)
                    bitmap.recycle()
                    continuation.resume(panels)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Text recognition failed: ${e.message}")
                    bitmap.recycle()
                    continuation.resume(emptyList())
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image: ${e.message}")
            continuation.resume(emptyList())
        }
    }

    /**
     * Extract text panels from OCR result with position and classification
     */
    private fun extractTextPanels(visionText: Text, imageWidth: Int, imageHeight: Int): List<TextPanel> {
        val panels = mutableListOf<TextPanel>()

        for (block in visionText.textBlocks) {
            val blockText = StringBuilder()
            var blockBounds: Rect? = null
            var totalConfidence = 0f
            var lineCount = 0

            for (line in block.lines) {
                blockText.append(line.text).append(" ")

                // Merge bounding boxes
                line.boundingBox?.let { lineBounds ->
                    blockBounds = if (blockBounds == null) {
                        Rect(lineBounds)
                    } else {
                        Rect(
                            minOf(blockBounds!!.left, lineBounds.left),
                            minOf(blockBounds!!.top, lineBounds.top),
                            maxOf(blockBounds!!.right, lineBounds.right),
                            maxOf(blockBounds!!.bottom, lineBounds.bottom)
                        )
                    }
                }

                // Calculate average confidence
                for (element in line.elements) {
                    totalConfidence += element.confidence ?: 0.5f
                    lineCount++
                }
            }

            val text = blockText.toString().trim()
            if (text.isNotEmpty() && blockBounds != null) {
                val avgConfidence = if (lineCount > 0) totalConfidence / lineCount else 0.5f

                // Only include panels with sufficient confidence and size
                if (avgConfidence >= MIN_TEXT_CONFIDENCE &&
                    blockBounds!!.width() >= MIN_PANEL_WIDTH &&
                    blockBounds!!.height() >= MIN_PANEL_HEIGHT) {

                    val panel = TextPanel(
                        text = text,
                        boundingBox = blockBounds!!,
                        confidence = avgConfidence,
                        isDialogue = classifyAsDialogue(text),
                        isNarration = classifyAsNarration(text, blockBounds!!, imageWidth, imageHeight),
                        isSoundEffect = classifyAsSoundEffect(text)
                    )
                    panels.add(panel)
                }
            }
        }

        return mergeSimilarPanels(panels)
    }

    /**
     * Filter out low quality panels based on confidence and content analysis
     */
    private fun filterLowQualityPanels(panels: List<TextPanel>): List<TextPanel> {
        val ocrSensitivity = preferenceManager.getOcrSensitivity()
        val confidenceThreshold = when (ocrSensitivity) {
            in 0..2 -> HIGH_CONFIDENCE_THRESHOLD // Low sensitivity = high confidence required
            in 3..4 -> MEDIUM_CONFIDENCE_THRESHOLD
            in 5..6 -> MIN_TEXT_CONFIDENCE
            in 7..8 -> LOW_CONFIDENCE_THRESHOLD
            else -> LOW_CONFIDENCE_THRESHOLD * 0.8f // High sensitivity = very low threshold
        }

        return panels.filter { panel ->
            // Filter by confidence threshold
            if (panel.confidence < confidenceThreshold) return@filter false

            // Filter by quality score
            if (panel.qualityScore < 0.3f) return@filter false

            // Filter out very short text (likely OCR noise)
            if (panel.text.length < MIN_WORD_LENGTH) return@filter false

            // Filter out text that's mostly non-alphabetic (likely noise)
            val alphaRatio = panel.text.count { it.isLetter() }.toFloat() / panel.text.length
            if (alphaRatio < 0.3f && !panel.isSoundEffect) return@filter false

            // Filter out common OCR mistakes
            if (isLikelyOCRNoise(panel.text)) return@filter false

            true
        }.also { filtered ->
            Log.d(TAG, "Filtered ${panels.size - filtered.size} low quality panels (threshold: $confidenceThreshold)")
        }
    }

    /**
     * Check if text is likely OCR noise
     */
    private fun isLikelyOCRNoise(text: String): Boolean {
        val cleanText = text.trim()

        // Very short single characters (except common ones)
        if (cleanText.length == 1 && !cleanText.matches(Regex("[A-Za-z0-9!?.]"))) {
            return true
        }

        // Mostly punctuation or symbols
        val symbolRatio = cleanText.count { !it.isLetterOrDigit() && !it.isWhitespace() }.toFloat() / cleanText.length
        if (symbolRatio > 0.7f) return true

        // Common OCR misreads
        val commonNoise = listOf("l", "I", "|", ".", ",", "'", "\"", "-", "_", "~", "`")
        if (cleanText in commonNoise) return true

        return false
    }

    /**
     * Order text panels by comic reading sequence (top-to-bottom, left-to-right)
     */
    private fun orderTextByReadingSequence(panels: List<TextPanel>): String {
        if (panels.isEmpty()) return ""

        // Sort panels by reading order: top-to-bottom, then left-to-right
        val sortedPanels = panels.sortedWith { panel1, panel2 ->
            val verticalDiff = panel1.boundingBox.top - panel2.boundingBox.top

            // If panels are roughly on the same horizontal level (within 50px)
            if (abs(verticalDiff) < 50) {
                // Sort by left-to-right
                panel1.boundingBox.left - panel2.boundingBox.left
            } else {
                // Sort by top-to-bottom
                verticalDiff
            }
        }

        // Build text with appropriate separators
        val result = StringBuilder()
        var lastPanelType: String? = null

        for (panel in sortedPanels) {
            val currentType = when {
                panel.isSoundEffect -> "sound"
                panel.isNarration -> "narration"
                panel.isDialogue -> "dialogue"
                else -> "text"
            }

            // Add appropriate spacing based on panel type transitions
            if (result.isNotEmpty()) {
                when {
                    lastPanelType == "sound" && currentType != "sound" -> result.append("\n\n")
                    lastPanelType != "narration" && currentType == "narration" -> result.append("\n\n")
                    else -> result.append(" ")
                }
            }

            result.append(panel.text)
            lastPanelType = currentType
        }

        return result.toString().trim()
    }

    /**
     * Classify text as dialogue based on content patterns
     */
    private fun classifyAsDialogue(text: String): Boolean {
        val dialoguePatterns = listOf(
            "\".*\"",  // Quoted text
            "'.*'",    // Single quoted text
            ".*[!?]$", // Exclamations or questions
            ".*\\b(said|says|asked|replied|shouted|whispered)\\b.*"
        )

        return dialoguePatterns.any { pattern ->
            text.matches(Regex(pattern, RegexOption.IGNORE_CASE))
        }
    }

    /**
     * Classify text as narration based on position and content
     */
    private fun classifyAsNarration(text: String, bounds: Rect, imageWidth: Int, imageHeight: Int): Boolean {
        // Narration boxes are often rectangular and positioned at top/bottom
        val isTopOrBottom = bounds.top < imageHeight * 0.2 || bounds.bottom > imageHeight * 0.8
        val isWideBox = bounds.width() > imageWidth * 0.6

        val narrationPatterns = listOf(
            ".*\\b(meanwhile|later|earlier|suddenly|then|after|before)\\b.*",
            ".*\\b(narrator|caption|thought)\\b.*",
            "^[A-Z][^.!?]*[.!?]$" // Complete sentences starting with capital
        )

        val hasNarrationPattern = narrationPatterns.any { pattern ->
            text.matches(Regex(pattern, RegexOption.IGNORE_CASE))
        }

        return (isTopOrBottom && isWideBox) || hasNarrationPattern
    }

    /**
     * Classify text as sound effect based on content patterns
     */
    private fun classifyAsSoundEffect(text: String): Boolean {
        val soundEffectPatterns = listOf(
            "^[A-Z]{2,}[!]*$",  // All caps words like "BOOM!", "CRASH!"
            ".*[*]{2,}.*",       // Text surrounded by asterisks
            "^(BAM|POW|CRASH|BOOM|BANG|ZAP|WHOOSH|THUD|SLAM|WHAM|KAPOW|SMASH)!*$"
        )

        return soundEffectPatterns.any { pattern ->
            text.matches(Regex(pattern, RegexOption.IGNORE_CASE))
        } || text.length <= 8 && text.all { it.isUpperCase() || it in "!*-" }
    }

    /**
     * Merge panels that are very close to each other
     */
    private fun mergeSimilarPanels(panels: List<TextPanel>): List<TextPanel> {
        if (panels.size <= 1) return panels

        val merged = mutableListOf<TextPanel>()
        val processed = mutableSetOf<Int>()

        for (i in panels.indices) {
            if (i in processed) continue

            var currentPanel = panels[i]
            val toMerge = mutableListOf<TextPanel>()

            // Find nearby panels to merge
            for (j in i + 1 until panels.size) {
                if (j in processed) continue

                val otherPanel = panels[j]
                if (shouldMergePanels(currentPanel, otherPanel)) {
                    toMerge.add(otherPanel)
                    processed.add(j)
                }
            }

            // Merge panels if any were found
            if (toMerge.isNotEmpty()) {
                currentPanel = mergeTextPanels(currentPanel, toMerge)
            }

            merged.add(currentPanel)
            processed.add(i)
        }

        return merged
    }

    /**
     * Check if two panels should be merged based on proximity
     */
    private fun shouldMergePanels(panel1: TextPanel, panel2: TextPanel): Boolean {
        val bounds1 = panel1.boundingBox
        val bounds2 = panel2.boundingBox

        // Check if panels are close enough horizontally or vertically
        val horizontalDistance = minOf(
            abs(bounds1.right - bounds2.left),
            abs(bounds2.right - bounds1.left)
        )

        val verticalDistance = minOf(
            abs(bounds1.bottom - bounds2.top),
            abs(bounds2.bottom - bounds1.top)
        )

        return horizontalDistance <= PANEL_MERGE_THRESHOLD || verticalDistance <= PANEL_MERGE_THRESHOLD
    }

    /**
     * Merge multiple text panels into one
     */
    private fun mergeTextPanels(mainPanel: TextPanel, panelsToMerge: List<TextPanel>): TextPanel {
        val allPanels = listOf(mainPanel) + panelsToMerge

        // Merge bounding boxes
        var mergedBounds = Rect(mainPanel.boundingBox)
        for (panel in panelsToMerge) {
            mergedBounds.union(panel.boundingBox)
        }

        // Merge text content
        val mergedText = allPanels.joinToString(" ") { it.text }

        // Calculate average confidence
        val avgConfidence = allPanels.map { it.confidence }.average().toFloat()

        // Determine merged classification
        val hasDialogue = allPanels.any { it.isDialogue }
        val hasNarration = allPanels.any { it.isNarration }
        val hasSoundEffect = allPanels.any { it.isSoundEffect }

        return TextPanel(
            text = mergedText,
            boundingBox = mergedBounds,
            confidence = avgConfidence,
            isDialogue = hasDialogue,
            isNarration = hasNarration,
            isSoundEffect = hasSoundEffect
        )
    }

    /**
     * Preprocess text for better TTS pronunciation with enhanced comic-specific processing
     */
    private fun preprocessTextForTTS(text: String): String {
        if (text.isBlank()) return text

        var processedText = text

        // Fix common OCR mistakes first
        processedText = fixCommonOCRMistakes(processedText)

        // Handle common comic book onomatopoeia and sound effects
        val soundEffectReplacements = mapOf(
            "BOOM" to "boom",
            "CRASH" to "crash",
            "BANG" to "bang",
            "POW" to "pow",
            "ZAP" to "zap",
            "WHOOSH" to "whoosh",
            "THUD" to "thud",
            "SLAM" to "slam",
            "WHAM" to "wham",
            "KAPOW" to "ka-pow",
            "SMASH" to "smash",
            "CRACK" to "crack",
            "SPLASH" to "splash",
            "SWOOSH" to "swoosh",
            "THWACK" to "thwack",
            "BONK" to "bonk",
            "CLANG" to "clang",
            "SCREECH" to "screech"
        )

        // Replace sound effects with more pronounceable versions
        for ((original, replacement) in soundEffectReplacements) {
            processedText = processedText.replace(Regex("\\b$original!*\\b", RegexOption.IGNORE_CASE), replacement)
        }

        // Handle repeated characters (e.g., "Noooo" -> "No")
        processedText = processedText.replace(Regex("(.)\\1{3,}")) { matchResult ->
            matchResult.groupValues[1].repeat(2)
        }

        // Handle comic-specific text patterns
        processedText = processedText.replace(Regex("\\*([^*]+)\\*"), "$1") // Remove asterisks around actions
        processedText = processedText.replace(Regex("<([^>]+)>"), "$1") // Remove angle brackets
        processedText = processedText.replace(Regex("\\[([^\\]]+)\\]"), "$1") // Remove square brackets

        // Clean up excessive punctuation
        processedText = processedText.replace(Regex("[!]{2,}"), "!")
        processedText = processedText.replace(Regex("[?]{2,}"), "?")
        processedText = processedText.replace(Regex("[.]{3,}"), "...")

        // Add natural pauses for better speech flow
        processedText = processedText.replace("...", "... ")
        processedText = processedText.replace("—", " — ")
        processedText = processedText.replace(" - ", " — ") // Convert hyphens to em dashes

        // Handle dialogue attribution
        processedText = processedText.replace(Regex("\"([^\"]+)\",?\\s*said\\s+([^.!?]+)[.!?]"), "\"$1\"")
        processedText = processedText.replace(Regex("\"([^\"]+)\",?\\s*asked\\s+([^.!?]+)[.!?]"), "\"$1\"")

        // Clean up whitespace and normalize
        processedText = processedText.replace(Regex("\\s+"), " ").trim()

        return processedText
    }

    /**
     * Fix common OCR mistakes in comic text
     */
    private fun fixCommonOCRMistakes(text: String): String {
        var fixed = text

        // Common OCR character mistakes
        val ocrFixes = mapOf(
            "rn" to "m",  // Common OCR mistake
            "cl" to "d",  // Another common mistake
            "0" to "O",   // Zero to O in words
            "1" to "I",   // One to I in words
            "5" to "S",   // Five to S in words
            "|" to "I",   // Pipe to I
            "\\." to ".",  // Escaped periods
            "'" to "'",   // Smart quotes to regular quotes
            """ to "\"",  // Smart quotes to regular quotes
            """ to "\""   // Smart quotes to regular quotes
        )

        // Apply fixes only in word contexts to avoid breaking intentional numbers
        for ((mistake, correction) in ocrFixes) {
            if (mistake in listOf("0", "1", "5")) {
                // Only fix numbers when they're clearly in word context
                fixed = fixed.replace(Regex("\\b[A-Za-z]*$mistake[A-Za-z]+\\b")) { match ->
                    match.value.replace(mistake, correction)
                }
            } else {
                fixed = fixed.replace(mistake, correction)
            }
        }

        return fixed
    }

    /**
     * Load bitmap optimized for OCR processing
     */
    private fun loadOptimizedBitmap(imagePath: String): Bitmap? {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Loading optimized bitmap from: $imagePath")
        
        return try {
            // Validate file before processing
            val file = File(imagePath)
            if (!file.exists()) {
                logError("BITMAP_FILE_NOT_FOUND", "Bitmap file does not exist", imagePath, 0)
                return null
            }
            
            if (file.length() == 0L) {
                logError("BITMAP_EMPTY_FILE", "Bitmap file is empty", imagePath, 0)
                return null
            }
            
            // Handle bitmap operations that may not be available in test environment
            val bitmap = try {
                // First decode with inJustDecodeBounds=true to check dimensions
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(imagePath, options)
                
                // Validate image dimensions
                val outWidth = options.outWidth
                val outHeight = options.outHeight
                
                if (outWidth <= 0 || outHeight <= 0) {
                    logError("BITMAP_INVALID_DIMENSIONS", "Invalid image dimensions: ${outWidth}x${outHeight}", imagePath, 0)
                    return null
                }
                
                Log.d(TAG, "Original image dimensions: ${outWidth}x${outHeight}")

                // Calculate optimal size for OCR (balance between quality and performance)
                val maxDimension = MAX_IMAGE_DIMENSION
                var inSampleSize = 1

                if (outHeight > maxDimension || outWidth > maxDimension) {
                    val heightRatio = Math.round(outHeight.toFloat() / maxDimension.toFloat())
                    val widthRatio = Math.round(outWidth.toFloat() / maxDimension.toFloat())
                    inSampleSize = Math.max(heightRatio, widthRatio)
                    Log.d(TAG, "Downsampling image with inSampleSize: $inSampleSize")
                }

                // Decode bitmap with calculated inSampleSize
                options.apply {
                    inJustDecodeBounds = false
                    this.inSampleSize = inSampleSize
                    inPreferredConfig = Bitmap.Config.RGB_565 // Use less memory
                    inDither = false
                    inScaled = false
                }

                BitmapFactory.decodeFile(imagePath, options)
            } catch (e: NoSuchMethodError) {
                Log.w(TAG, "Bitmap operations not available in test environment: ${e.message}")
                return null
            }
            
            if (bitmap == null) {
                logError("BITMAP_DECODE_FAILED", "BitmapFactory.decodeFile returned null", imagePath, 0)
                return null
            }
            
            val loadTime = System.currentTimeMillis() - startTime
            try {
                Log.d(TAG, "Bitmap loaded successfully in ${loadTime}ms: ${bitmap.width}x${bitmap.height}, config: ${bitmap.config}")
            } catch (e: NoSuchMethodError) {
                Log.d(TAG, "Bitmap loaded successfully in ${loadTime}ms: ${bitmap.width}x${bitmap.height}")
            }

            // Apply contrast enhancement for better OCR accuracy
            bitmap?.let { enhanceContrastForOCR(it) } ?: bitmap

        } catch (e: OutOfMemoryError) {
            logError("BITMAP_OUT_OF_MEMORY", "Out of memory loading bitmap: ${e.message}", imagePath, 0)
            Log.e(TAG, "Out of memory loading bitmap", e)
            System.gc() // Suggest garbage collection
            null
        } catch (e: Exception) {
            logError("BITMAP_LOAD_EXCEPTION", "Error loading optimized bitmap: ${e.message}", imagePath, 0)
            Log.e(TAG, "Error loading optimized bitmap", e)
            null
        }
    }
    
    /**
     * Log error with detailed information for debugging and monitoring
     */
    private fun logError(errorType: String, message: String, imagePath: String?, attempt: Int) {
        val errorRecord = ErrorRecord(
            timestamp = System.currentTimeMillis(),
            errorType = errorType,
            errorMessage = message,
            imagePath = imagePath,
            attempt = attempt
        )
        
        errorLog.add(errorRecord)
        
        // Keep error log size manageable
        if (errorLog.size > 100) {
            errorLog.removeAt(0)
        }
        
        Log.w(TAG, "[$errorType] $message (attempt: $attempt, path: $imagePath)")
    }

    /**
     * Enhance bitmap contrast for better OCR accuracy with advanced preprocessing
     */
    private fun enhanceContrastForOCR(bitmap: Bitmap): Bitmap {
        return try {
            val startTime = System.currentTimeMillis()
            Log.d(TAG, "Starting advanced image preprocessing for OCR")
            
            // Create a mutable copy to work with
            val enhanced = try {
                bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
            } catch (e: NoSuchMethodError) {
                // Fallback for older Android versions
                bitmap.copy(Bitmap.Config.ARGB_8888, true)
            }

            // Apply multiple enhancement techniques for comic book text
            val processed = applyComicBookOptimizations(enhanced)
            
            val processingTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "Image preprocessing completed in ${processingTime}ms")
            
            processed
        } catch (e: Exception) {
            Log.w(TAG, "Could not enhance image, using original bitmap: ${e.message}")
            bitmap
        }
    }
    
    /**
     * Analyzes image characteristics to determine optimal preprocessing strategy
     */
    private fun analyzeImageCharacteristics(bitmap: Bitmap): ImageCharacteristics {
        try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            
            var totalBrightness = 0f
            var edgeCount = 0
            val brightnessValues = mutableListOf<Float>()
            
            // Analyze pixel characteristics
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = pixels[y * width + x]
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    val brightness = (0.299f * r + 0.587f * g + 0.114f * b)
                    
                    brightnessValues.add(brightness)
                    totalBrightness += brightness
                    
                    // Simple edge detection for edge density
                    if (x > 0 && y > 0) {
                        val prevPixel = pixels[(y-1) * width + (x-1)]
                        val prevR = (prevPixel shr 16) and 0xFF
                        val prevG = (prevPixel shr 8) and 0xFF
                        val prevB = prevPixel and 0xFF
                        val prevBrightness = (0.299f * prevR + 0.587f * prevG + 0.114f * prevB)
                        
                        if (kotlin.math.abs(brightness - prevBrightness) > 30) {
                            edgeCount++
                        }
                    }
                }
            }
            
            val avgBrightness = totalBrightness / pixels.size
            val edgeDensity = edgeCount.toFloat() / pixels.size
            
            // Calculate contrast (standard deviation of brightness)
            val variance = brightnessValues.map { (it - avgBrightness) * (it - avgBrightness) }.average()
            val contrast = kotlin.math.sqrt(variance).toFloat()
            
            // Estimate noise level (high frequency variations)
            val noiseLevel = edgeDensity * 0.5f + (contrast / 255f) * 0.5f
            
            val needsEnhancement = contrast < LOW_CONTRAST_THRESHOLD || 
                                 noiseLevel > HIGH_NOISE_THRESHOLD ||
                                 edgeDensity < EDGE_DENSITY_THRESHOLD
            
            return ImageCharacteristics(
                contrast = contrast,
                noiseLevel = noiseLevel,
                edgeDensity = edgeDensity,
                averageBrightness = avgBrightness,
                needsEnhancement = needsEnhancement
            )
        } catch (e: Exception) {
            Log.w(TAG, "Image analysis failed: ${e.message}")
            return ImageCharacteristics(50f, 0.2f, 0.1f, 128f, true)
        }
    }
    
    /**
     * Apply comic book specific image optimizations for better OCR with adaptive preprocessing
     */
    private fun applyComicBookOptimizations(bitmap: Bitmap): Bitmap {
        try {
            // Check if image is too small for enhancement
            if (bitmap.width < MIN_IMAGE_SIZE_FOR_ENHANCEMENT || 
                bitmap.height < MIN_IMAGE_SIZE_FOR_ENHANCEMENT) {
                Log.d(TAG, "Image too small for enhancement, skipping preprocessing")
                return bitmap
            }
            
            // Generate cache key
            val cacheKey = "${bitmap.width}x${bitmap.height}_${bitmap.hashCode()}"
            
            // Check preprocessing cache
            preprocessingCache[cacheKey]?.let { cachedBitmap ->
                Log.d(TAG, "Using cached preprocessed image")
                return cachedBitmap
            }
            
            val startTime = System.currentTimeMillis()
            
            // Analyze image characteristics
            val characteristics = imageAnalysisCache[cacheKey] ?: run {
                val analysis = analyzeImageCharacteristics(bitmap)
                imageAnalysisCache[cacheKey] = analysis
                analysis
            }
            
            Log.d(TAG, "Image analysis - Contrast: ${characteristics.contrast}, Noise: ${characteristics.noiseLevel}, Edge density: ${characteristics.edgeDensity}")
            
            // Skip enhancement if not needed
            if (!ENABLE_ADVANCED_PREPROCESSING || !characteristics.needsEnhancement) {
                Log.d(TAG, "Image doesn't need enhancement, using original")
                return bitmap
            }
            
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            
            var appliedOptimizations = mutableListOf<String>()
            
            // Step 1: Always convert to grayscale with optimized weights for text
            convertToOptimizedGrayscale(pixels)
            appliedOptimizations.add("grayscale")
            
            // Step 2: Apply histogram equalization for low contrast images
            if (characteristics.contrast < LOW_CONTRAST_THRESHOLD) {
                applyHistogramEqualization(pixels)
                appliedOptimizations.add("histogram_eq")
            }
            
            // Step 3: Reduce noise for noisy images while preserving text edges
            if (characteristics.noiseLevel > HIGH_NOISE_THRESHOLD) {
                applyNoiseReduction(pixels, width, height)
                appliedOptimizations.add("noise_reduction")
            }
            
            // Step 4: Apply adaptive thresholding for better text separation
            applyAdaptiveThresholding(pixels, width, height)
            appliedOptimizations.add("adaptive_threshold")
            
            // Step 5: Enhance text edges for low edge density images
            if (characteristics.edgeDensity < EDGE_DENSITY_THRESHOLD) {
                enhanceTextEdges(pixels, width, height)
                appliedOptimizations.add("edge_enhancement")
            }
            
            // Create enhanced bitmap
            val enhanced = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            enhanced.setPixels(pixels, 0, width, 0, 0, width, height)
            
            // Cache the result if processing was successful and within time limit
            val processingTime = System.currentTimeMillis() - startTime
            if (processingTime < MAX_PROCESSING_TIME_MS) {
                // Limit cache size to prevent memory issues
                if (preprocessingCache.size >= PREPROCESSING_CACHE_SIZE) {
                    val oldestKey = preprocessingCache.keys.first()
                    preprocessingCache.remove(oldestKey)
                    imageAnalysisCache.remove(oldestKey)
                }
                preprocessingCache[cacheKey] = enhanced
                Log.d(TAG, "Cached preprocessed image (processing time: ${processingTime}ms)")
            }
            
            Log.d(TAG, "Applied optimizations: ${appliedOptimizations.joinToString(", ")} (${processingTime}ms)")
            return enhanced
            
        } catch (e: Exception) {
            Log.w(TAG, "Error in comic book optimizations: ${e.message}")
            return bitmap
        }
    }
    
    /**
     * Convert to grayscale using optimized weights for text recognition
     */
    private fun convertToOptimizedGrayscale(pixels: IntArray) {
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            
            // Use optimized weights for text recognition (emphasize contrast)
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt().coerceIn(0, 255)
            pixels[i] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
        }
    }
    
    /**
     * Apply histogram equalization for improved contrast
     */
    private fun applyHistogramEqualization(pixels: IntArray) {
        // Calculate histogram
        val histogram = IntArray(256)
        for (pixel in pixels) {
            val gray = pixel and 0xFF
            histogram[gray]++
        }
        
        // Calculate cumulative distribution function
        val cdf = IntArray(256)
        cdf[0] = histogram[0]
        for (i in 1 until 256) {
            cdf[i] = cdf[i - 1] + histogram[i]
        }
        
        // Normalize CDF
        val totalPixels = pixels.size
        val cdfMin = cdf.first { it > 0 }
        
        for (i in pixels.indices) {
            val gray = pixels[i] and 0xFF
            val newGray = ((cdf[gray] - cdfMin) * 255 / (totalPixels - cdfMin)).coerceIn(0, 255)
            pixels[i] = (0xFF shl 24) or (newGray shl 16) or (newGray shl 8) or newGray
        }
    }
    
    /**
     * Apply noise reduction while preserving text edges
     */
    private fun applyNoiseReduction(pixels: IntArray, width: Int, height: Int) {
        val filtered = pixels.clone()
        
        // Apply median filter for noise reduction
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val neighbors = mutableListOf<Int>()
                
                // Collect 3x3 neighborhood
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val idx = (y + dy) * width + (x + dx)
                        neighbors.add(pixels[idx] and 0xFF)
                    }
                }
                
                // Use median value
                neighbors.sort()
                val median = neighbors[4] // Middle value of 9 elements
                val idx = y * width + x
                filtered[idx] = (0xFF shl 24) or (median shl 16) or (median shl 8) or median
            }
        }
        
        System.arraycopy(filtered, 0, pixels, 0, pixels.size)
    }
    
    /**
     * Apply adaptive thresholding for better text separation
     */
    private fun applyAdaptiveThresholding(pixels: IntArray, width: Int, height: Int) {
        val windowSize = 15 // Adaptive window size
        val c = 10 // Constant subtracted from mean
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val currentGray = pixels[idx] and 0xFF
                
                // Calculate local mean in window
                var sum = 0
                var count = 0
                
                val startY = maxOf(0, y - windowSize / 2)
                val endY = minOf(height - 1, y + windowSize / 2)
                val startX = maxOf(0, x - windowSize / 2)
                val endX = minOf(width - 1, x + windowSize / 2)
                
                for (wy in startY..endY) {
                    for (wx in startX..endX) {
                        val wIdx = wy * width + wx
                        sum += pixels[wIdx] and 0xFF
                        count++
                    }
                }
                
                val localMean = sum / count
                val threshold = localMean - c
                
                // Apply threshold
                val newValue = if (currentGray > threshold) 255 else 0
                pixels[idx] = (0xFF shl 24) or (newValue shl 16) or (newValue shl 8) or newValue
            }
        }
    }
    
    /**
     * Enhance text edges for better OCR recognition
     */
    private fun enhanceTextEdges(pixels: IntArray, width: Int, height: Int) {
        val enhanced = pixels.clone()
        
        // Sobel edge detection kernels
        val sobelX = arrayOf(
            intArrayOf(-1, 0, 1),
            intArrayOf(-2, 0, 2),
            intArrayOf(-1, 0, 1)
        )
        
        val sobelY = arrayOf(
            intArrayOf(-1, -2, -1),
            intArrayOf(0, 0, 0),
            intArrayOf(1, 2, 1)
        )
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var gx = 0
                var gy = 0
                
                // Apply Sobel kernels
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val idx = (y + ky) * width + (x + kx)
                        val gray = pixels[idx] and 0xFF
                        gx += gray * sobelX[ky + 1][kx + 1]
                        gy += gray * sobelY[ky + 1][kx + 1]
                    }
                }
                
                // Calculate gradient magnitude
                val magnitude = kotlin.math.sqrt((gx * gx + gy * gy).toDouble()).toInt()
                val edgeValue = magnitude.coerceIn(0, 255)
                
                // Enhance edges while preserving original structure
                val originalIdx = y * width + x
                val original = pixels[originalIdx] and 0xFF
                val enhanced_value = minOf(255, original + edgeValue / 4)
                
                enhanced[originalIdx] = (0xFF shl 24) or (enhanced_value shl 16) or (enhanced_value shl 8) or enhanced_value
            }
        }
        
        System.arraycopy(enhanced, 0, pixels, 0, pixels.size)
    }

    /**
     * Get comprehensive performance metrics and error statistics
     */
    fun getPerformanceMetrics(): Map<String, Any> {
        val totalExtractions = successfulExtractions + failedExtractions
        val successRate = if (totalExtractions > 0) {
            (successfulExtractions.toFloat() / totalExtractions) * 100
        } else 0f
        
        val averageProcessingTime = if (successfulExtractions > 0) {
            totalProcessingTime / successfulExtractions
        } else 0L
        
        // Group errors by type for analysis
        val errorsByType = errorLog.groupBy { it.errorType }
            .mapValues { it.value.size }
        
        // Recent errors (last 24 hours)
        val recentErrors = errorLog.filter { 
            System.currentTimeMillis() - it.timestamp < 24 * 60 * 60 * 1000 
        }
        
        return mapOf(
            "totalExtractions" to totalExtractions,
            "successfulExtractions" to successfulExtractions,
            "failedExtractions" to failedExtractions,
            "successRate" to successRate,
            "averageProcessingTime" to averageProcessingTime,
            "totalProcessingTime" to totalProcessingTime,
            "totalRetryAttempts" to totalRetryAttempts,
            "averageRetriesPerExtraction" to if (totalExtractions > 0) {
                totalRetryAttempts.toFloat() / totalExtractions
            } else 0f,
            "errorsByType" to errorsByType,
            "totalErrors" to errorLog.size,
            "recentErrors" to recentErrors.size,
            "lastErrorTimestamp" to (errorLog.lastOrNull()?.timestamp ?: 0L),
            "ocrConfidenceThreshold" to MIN_TEXT_CONFIDENCE,
            "minPanelWidth" to MIN_PANEL_WIDTH,
            "minPanelHeight" to MIN_PANEL_HEIGHT,
            "panelMergeThreshold" to PANEL_MERGE_THRESHOLD,
            "preprocessingCacheSize" to preprocessingCache.size,
            "imageAnalysisCacheSize" to imageAnalysisCache.size,
            "advancedPreprocessingEnabled" to ENABLE_ADVANCED_PREPROCESSING,
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
     * Clear preprocessing caches to free memory
     */
    fun clearPreprocessingCache() {
        preprocessingCache.clear()
        imageAnalysisCache.clear()
        Log.d(TAG, "Preprocessing caches cleared")
    }
    
    /**
     * Get cache statistics for monitoring
     */
    fun getCacheStatistics(): Map<String, Any> {
        return mapOf(
            "preprocessingCacheSize" to preprocessingCache.size,
            "preprocessingCacheLimit" to PREPROCESSING_CACHE_SIZE,
            "imageAnalysisCacheSize" to imageAnalysisCache.size,
            "cacheMemoryEstimate" to estimateCacheMemoryUsage(),
            "advancedPreprocessingEnabled" to ENABLE_ADVANCED_PREPROCESSING,
            "minImageSizeForEnhancement" to MIN_IMAGE_SIZE_FOR_ENHANCEMENT,
            "maxProcessingTimeMs" to MAX_PROCESSING_TIME_MS
        )
    }
    
    /**
     * Estimate memory usage of preprocessing caches
     */
    private fun estimateCacheMemoryUsage(): Long {
        var totalBytes = 0L
        preprocessingCache.values.forEach { bitmap ->
            totalBytes += bitmap.byteCount.toLong()
        }
        return totalBytes
    }
    
    /**
     * Clear error log and reset performance counters
     */
    fun resetMetrics() {
        Log.i(TAG, "Resetting performance metrics and error log")
        errorLog.clear()
        totalProcessingTime = 0L
        successfulExtractions = 0
        failedExtractions = 0
        totalRetryAttempts = 0
    }
    
    /**
     * Get health status based on recent performance
     */
    fun getHealthStatus(): String {
        val totalExtractions = successfulExtractions + failedExtractions
        if (totalExtractions == 0) return "UNKNOWN"
        
        val successRate = (successfulExtractions.toFloat() / totalExtractions) * 100
        val recentErrors = errorLog.filter { 
            System.currentTimeMillis() - it.timestamp < 60 * 60 * 1000 // Last hour
        }.size
        
        return when {
            successRate >= 95 && recentErrors <= 2 -> "EXCELLENT"
            successRate >= 85 && recentErrors <= 5 -> "GOOD"
            successRate >= 70 && recentErrors <= 10 -> "FAIR"
            successRate >= 50 && recentErrors <= 20 -> "POOR"
            else -> "CRITICAL"
        }
    }

    /**
     * Enhanced comic book text processing with spell correction and pronunciation fixes
     */
    private fun enhanceComicBookText(text: String): String {
        if (text.isBlank()) return text

        var enhanced = text

        // Fix common OCR errors in comic books
        enhanced = fixCommonOCRErrors(enhanced)

        // Enhance sound effects and onomatopoeia
        enhanced = enhanceSoundEffects(enhanced)

        // Fix character names and common comic terms
        enhanced = fixComicBookTerms(enhanced)

        // Improve punctuation for better TTS reading
        enhanced = improvePunctuationForTTS(enhanced)

        Log.d(TAG, "Text enhanced from ${text.length} to ${enhanced.length} characters")
        return enhanced
    }

    /**
     * Fix common OCR errors specific to comic books
     */
    private fun fixCommonOCRErrors(text: String): String {
        var fixed = text

        // Common character substitutions
        val corrections = mapOf(
            "0" to "O", // Zero to letter O in names
            "1" to "I", // One to letter I
            "5" to "S", // Five to letter S
            "8" to "B", // Eight to letter B
            "rn" to "m", // Common OCR error
            "vv" to "w", // Double v to w
            "VV" to "W", // Double V to W
            "l1" to "ll", // One to double l
            "Il" to "ll", // I-l to double l
            "|" to "I", // Pipe to I
            "!" to "I" // Exclamation to I in words
        )

        // Apply corrections in word context
        for ((error, correction) in corrections) {
            // Only replace if it makes sense in context
            fixed = fixed.replace(Regex("\\b\\w*$error\\w*\\b")) { match ->
                val word = match.value
                if (word.length > 1 && word.any { it.isLetter() }) {
                    word.replace(error, correction)
                } else {
                    word
                }
            }
        }

        return fixed
    }

    /**
     * Enhance sound effects and onomatopoeia for better TTS pronunciation
     */
    private fun enhanceSoundEffects(text: String): String {
        var enhanced = text

        // Common comic sound effects with pronunciation guides
        val soundEffects = mapOf(
            "BOOM" to "BOOM!",
            "POW" to "POW!",
            "BAM" to "BAM!",
            "ZAP" to "ZAP!",
            "CRASH" to "CRASH!",
            "BANG" to "BANG!",
            "WHOOSH" to "WHOOSH!",
            "THUD" to "THUD!",
            "SLAM" to "SLAM!",
            "CRACK" to "CRACK!"
        )

        for ((effect, enhanced_effect) in soundEffects) {
            enhanced = enhanced.replace(Regex("\\b$effect\\b", RegexOption.IGNORE_CASE), enhanced_effect)
        }

        return enhanced
    }

    /**
     * Fix common comic book character names and terms
     */
    private fun fixComicBookTerms(text: String): String {
        var fixed = text

        // Common comic book terms that might be misread
        val comicTerms = mapOf(
            "SUPERHERO" to "superhero",
            "VILLAIN" to "villain",
            "GOTHAM" to "Gotham",
            "METROPOLIS" to "Metropolis",
            "KRYPTON" to "Krypton"
        )

        for ((term, corrected) in comicTerms) {
            fixed = fixed.replace(Regex("\\b$term\\b", RegexOption.IGNORE_CASE), corrected)
        }

        return fixed
    }

    /**
     * Improve punctuation for better TTS reading flow
     */
    private fun improvePunctuationForTTS(text: String): String {
        var improved = text

        // Add pauses for better speech flow
        improved = improved.replace(Regex("([.!?])([A-Z])"), "$1 $2") // Space after sentence endings
        improved = improved.replace(Regex("([,;:])([A-Za-z])"), "$1 $2") // Space after commas, etc.

        // Fix ellipses for better pronunciation
        improved = improved.replace(Regex("\\.{2,}"), "...") // Normalize ellipses

        // Add emphasis markers for dramatic effect
        improved = improved.replace(Regex("([!]{2,})"), "!") // Normalize multiple exclamations
        improved = improved.replace(Regex("([?]{2,})"), "?") // Normalize multiple questions

        return improved
    }
}
