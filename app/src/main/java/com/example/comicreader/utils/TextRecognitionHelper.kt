package com.example.comicreader.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

class TextRecognitionHelper(private val context: Context) {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    companion object {
        private const val TAG = "TextRecognitionHelper"
    }

    /**
     * Extract text from an image file using ML Kit OCR
     * @param imagePath Path to the image file
     * @return Extracted text or empty string if no text found
     */
    suspend fun extractTextFromImage(imagePath: String): String = suspendCoroutine { continuation ->
        try {
            val file = File(imagePath)
            if (!file.exists()) {
                continuation.resume("")
                return@suspendCoroutine
            }

            // Load bitmap with reduced size to avoid OutOfMemoryError
            val bitmap = loadScaledBitmap(file.absolutePath)
            if (bitmap == null) {
                continuation.resume("")
                return@suspendCoroutine
            }

            val image = InputImage.fromBitmap(bitmap, 0)
            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val extractedText = processVisionText(visionText)
                    bitmap.recycle()
                    continuation.resume(extractedText)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Text recognition failed: ${e.message}")
                    bitmap.recycle()
                    continuation.resume("")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image: ${e.message}")
            continuation.resume("")
        }
    }

    /**
     * Process the OCR result to extract meaningful text
     */
    private fun processVisionText(visionText: Text): String {
        val stringBuilder = StringBuilder()
        
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                stringBuilder.append(line.text).append("\n")
            }
            stringBuilder.append("\n")
        }
        
        return stringBuilder.toString().trim()
    }

    /**
     * Load a bitmap with reduced size to avoid OutOfMemoryError
     */
    private fun loadScaledBitmap(imagePath: String): Bitmap? {
        return try {
            // First decode with inJustDecodeBounds=true to check dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(imagePath, options)
            
            // Calculate inSampleSize
            val maxDimension = 2048 // Max dimension for ML Kit
            var inSampleSize = 1
            
            if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                val heightRatio = Math.round(options.outHeight.toFloat() / maxDimension.toFloat())
                val widthRatio = Math.round(options.outWidth.toFloat() / maxDimension.toFloat())
                inSampleSize = Math.max(heightRatio, widthRatio)
            }
            
            // Decode bitmap with inSampleSize set
            options.apply {
                inJustDecodeBounds = false
                this.inSampleSize = inSampleSize
            }
            
            BitmapFactory.decodeFile(imagePath, options)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap: ${e.message}")
            null
        }
    }
}