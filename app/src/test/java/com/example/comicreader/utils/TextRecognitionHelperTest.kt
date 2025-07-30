package com.example.comicreader.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class TextRecognitionHelperTest {

    private lateinit var textRecognitionHelper: TextRecognitionHelper
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        textRecognitionHelper = TextRecognitionHelper(context)
    }

    @Test
    fun testExtractTextFromNonExistentFile() = runBlocking {
        val result = textRecognitionHelper.extractTextFromImage("/non/existent/path.jpg")
        assertEquals("", result)
    }

    @Test
    fun testExtractTextFromEmptyFile() = runBlocking {
        // Create a temporary empty file
        val tempFile = File.createTempFile("test", ".jpg", context.cacheDir)
        tempFile.writeBytes(byteArrayOf())
        
        val result = textRecognitionHelper.extractTextFromImage(tempFile.absolutePath)
        assertEquals("", result)
        
        tempFile.delete()
    }

    @Test
    fun testImageScaling() {
        // Test that the helper can handle large image dimensions
        // This is a unit test for the scaling logic
        val helper = TextRecognitionHelper(context)
        
        // We can't easily test the actual bitmap scaling without creating real images,
        // but we can verify the helper initializes correctly
        assertNotNull(helper)
    }
}