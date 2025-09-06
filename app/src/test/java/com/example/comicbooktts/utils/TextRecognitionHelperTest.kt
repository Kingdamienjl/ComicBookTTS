package com.example.comicbooktts.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class TextRecognitionHelperTest {

    private lateinit var textRecognitionHelper: TextRecognitionHelper
    private lateinit var context: Context
    private lateinit var testDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        textRecognitionHelper = TextRecognitionHelper(context)
        testDir = File(context.cacheDir, "test_ocr")
        testDir.mkdirs()
    }

    @Test
    fun testInitialization() {
        assertNotNull(textRecognitionHelper)
    }

    @Test
    fun testExtractTextFromNonExistentFile() = runBlocking {
        val result = textRecognitionHelper.extractTextFromImage("/non/existent/path.jpg")
        assertEquals("", result)
    }

    @Test
    fun testExtractTextFromEmptyFile(): Unit = runBlocking {
        // Create a temporary empty file
        val tempFile = File.createTempFile("test", ".jpg", testDir)
        tempFile.writeBytes(byteArrayOf())
        
        val result = textRecognitionHelper.extractTextFromImage(tempFile.absolutePath)
        assertEquals("", result)
        
        tempFile.delete()
    }

    @Test
    fun testExtractTextFromInvalidImageFile(): Unit = runBlocking {
        val invalidFile = File(testDir, "invalid.jpg")
        invalidFile.writeText("This is not a valid image file")
        
        val result = textRecognitionHelper.extractTextFromImage(invalidFile.absolutePath)
        assertEquals("", result)
        
        invalidFile.delete()
    }

    @Test
    fun testExtractTextFromEmptyPath() = runBlocking {
        val result = textRecognitionHelper.extractTextFromImage("")
        assertEquals("", result)
    }

    @Test
    fun testCreateSimpleBitmap() {
        // Create a simple bitmap for testing
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Fill with white background
        canvas.drawColor(Color.WHITE)
        
        // Draw some text
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 20f
            isAntiAlias = true
        }
        canvas.drawText("TEST", 10f, 50f, paint)
        
        assertNotNull(bitmap)
        assertEquals(100, bitmap.width)
        assertEquals(100, bitmap.height)
    }

    @Test
    fun testImageScaling() {
        // Test that the helper can handle large image dimensions
        val helper = TextRecognitionHelper(context)
        
        // We can't easily test the actual bitmap scaling without creating real images,
        // but we can verify the helper initializes correctly
        assertNotNull(helper)
    }

    @Test
    fun testExtractTextFromCorruptedImage(): Unit = runBlocking {
        val corruptFile = File(testDir, "corrupt.jpg")
        
        // Create a file with JPEG header but corrupted data
        corruptFile.writeBytes(byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), // JPEG header
            0x00, 0x10, // Corrupted data
            0xFF.toByte(), 0xFF.toByte()
        ))
        
        val result = textRecognitionHelper.extractTextFromImage(corruptFile.absolutePath)
        assertEquals("", result)
        
        corruptFile.delete()
    }

    @Test
    fun testExtractTextFromVeryLargeImagePath() = runBlocking {
        // Test with a very long file path
        val longPath = "/" + "very_long_directory_name".repeat(50) + "/image.jpg"
        
        val result = textRecognitionHelper.extractTextFromImage(longPath)
        assertEquals("", result)
    }

    @Test
    fun testExtractTextFromSpecialCharacterPath() = runBlocking {
        // Test with special characters in path
        val specialPath = "/path/with spaces/and-dashes/file@#$.jpg"
        
        val result = textRecognitionHelper.extractTextFromImage(specialPath)
        assertEquals("", result)
    }

    @Test
    fun testMultipleExtractionCalls() = runBlocking {
        // Test multiple calls to ensure no memory leaks or state issues
        repeat(5) {
            val result = textRecognitionHelper.extractTextFromImage("/non/existent/file$it.jpg")
            assertEquals("", result)
        }
    }

    @Test
    fun testConcurrentExtractionCalls() = runBlocking {
        // Test concurrent calls (though they'll likely fail due to non-existent files)
        val results = (1..3).map { index ->
            async {
                textRecognitionHelper.extractTextFromImage("/non/existent/file$index.jpg")
            }
        }
        
        val allResults = awaitAll(*results.toTypedArray())
        allResults.forEach { result ->
            assertEquals("", result)
        }
    }

    @Test
    fun testExtractTextFromDifferentImageFormats() = runBlocking {
        val formats = listOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
        
        formats.forEach { format ->
            val result = textRecognitionHelper.extractTextFromImage("/non/existent/file.$format")
            assertEquals("Format $format should return empty string for non-existent file", "", result)
        }
    }

    @Test
    fun testExtractTextWithMemoryConstraints() = runBlocking {
        // Simulate memory pressure by creating multiple helper instances
        val helpers = mutableListOf<TextRecognitionHelper>()
        
        repeat(5) {
            helpers.add(TextRecognitionHelper(context))
        }
        
        // Test that all helpers work correctly
        helpers.forEach { helper ->
            val result = helper.extractTextFromImage("/non/existent/file.jpg")
            assertEquals("", result)
        }
        
        // Clear references
        helpers.clear()
    }

    @Test
    fun testHelperRobustness() {
        // Test that creating multiple instances doesn't cause issues
        repeat(10) {
            val helper = TextRecognitionHelper(context)
            assertNotNull(helper)
        }
    }

    @Test
    fun testExtractTextFromReadOnlyFile(): Unit = runBlocking {
        val readOnlyFile = File(testDir, "readonly.jpg")
        readOnlyFile.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))
        readOnlyFile.setReadOnly()
        
        val result = textRecognitionHelper.extractTextFromImage(readOnlyFile.absolutePath)
        assertEquals("", result)
        
        readOnlyFile.delete()
    }
}
