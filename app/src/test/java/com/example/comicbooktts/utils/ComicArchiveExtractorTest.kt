package com.example.comicbooktts.utils

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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class ComicArchiveExtractorTest {

    private lateinit var extractor: ComicArchiveExtractor
    private lateinit var context: Context
    private lateinit var testDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        extractor = ComicArchiveExtractor(context)
        testDir = File(context.cacheDir, "test_comics")
        testDir.mkdirs()
    }

    @Test
    fun testExtractFromNonExistentFile() = runBlocking {
        val result = extractor.extractComic(android.net.Uri.parse("file:///non/existent/file.cbz"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun testExtractFromEmptyFile() = runBlocking {
        val emptyFile = File(testDir, "empty.cbz")
        emptyFile.createNewFile()
        
        val result = extractor.extractComic(android.net.Uri.fromFile(emptyFile))
        assertTrue(result.isEmpty())
        
        emptyFile.delete()
    }

    @Test
    fun testExtractFromInvalidZipFile() = runBlocking {
        val invalidFile = File(testDir, "invalid.cbz")
        invalidFile.writeText("This is not a valid zip file")
        
        val result = extractor.extractComic(android.net.Uri.fromFile(invalidFile))
        assertTrue(result.isEmpty())
        
        invalidFile.delete()
    }

    @Test
    fun testCreateValidCbzFile() {
        val cbzFile = File(testDir, "test.cbz")
        
        // Create a valid CBZ file with test images
        ZipOutputStream(FileOutputStream(cbzFile)).use { zip ->
            // Add test image entries
            val imageNames = listOf("page001.jpg", "page002.png", "page003.gif")
            
            imageNames.forEach { imageName ->
                val entry = ZipEntry(imageName)
                zip.putNextEntry(entry)
                // Write minimal image data (just headers)
                when {
                    imageName.endsWith(".jpg") -> {
                        // JPEG header
                        zip.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))
                    }
                    imageName.endsWith(".png") -> {
                        // PNG header
                        zip.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
                    }
                    imageName.endsWith(".gif") -> {
                        // GIF header
                        zip.write("GIF89a".toByteArray())
                    }
                }
                zip.closeEntry()
            }
        }
        
        assertTrue(cbzFile.exists())
        assertTrue(cbzFile.length() > 0)
    }

    @Test
    fun testExtractFromValidCbzFile() = runBlocking {
        val cbzFile = File(testDir, "valid_test.cbz")
        
        // Create a valid CBZ file
        ZipOutputStream(FileOutputStream(cbzFile)).use { zip ->
            val imageNames = listOf("page001.jpg", "page002.png", "cover.jpg")
            
            imageNames.forEach { imageName ->
                val entry = ZipEntry(imageName)
                zip.putNextEntry(entry)
                // Write minimal valid image data
                zip.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))
                zip.closeEntry()
            }
        }
        
        val result = extractor.extractComic(android.net.Uri.fromFile(cbzFile))
        
        // Should extract the pages successfully
        assertTrue(result.isNotEmpty())
        assertEquals(3, result.size)
        
        // Verify all extracted files exist
        result.forEach { pagePath ->
            assertTrue(File(pagePath).exists())
        }
        
        cbzFile.delete()
    }

    @Test
    fun testExtractWithMixedFileTypes() = runBlocking {
        val cbzFile = File(testDir, "mixed_test.cbz")
        
        ZipOutputStream(FileOutputStream(cbzFile)).use { zip ->
            // Add image files
            listOf("image1.jpg", "image2.png").forEach { imageName ->
                val entry = ZipEntry(imageName)
                zip.putNextEntry(entry)
                zip.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))
                zip.closeEntry()
            }
            
            // Add non-image files (should be ignored)
            listOf("readme.txt", "metadata.xml").forEach { fileName ->
                val entry = ZipEntry(fileName)
                zip.putNextEntry(entry)
                zip.write("test content".toByteArray())
                zip.closeEntry()
            }
        }
        
        val result = extractor.extractComic(android.net.Uri.fromFile(cbzFile))
        
        // Should only extract image files
        assertEquals(2, result.size)
        
        cbzFile.delete()
    }

    @Test
    fun testExtractWithNestedDirectories() = runBlocking {
        val cbzFile = File(testDir, "nested_test.cbz")
        
        ZipOutputStream(FileOutputStream(cbzFile)).use { zip ->
            // Add images in nested directories
            val entries = listOf(
                "chapter1/page001.jpg",
                "chapter1/page002.jpg",
                "chapter2/page001.jpg",
                "cover.jpg"
            )
            
            entries.forEach { entryPath ->
                val entry = ZipEntry(entryPath)
                zip.putNextEntry(entry)
                zip.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))
                zip.closeEntry()
            }
        }
        
        val result = extractor.extractComic(android.net.Uri.fromFile(cbzFile))
        
        // Should extract all image files regardless of directory structure
        assertEquals(4, result.size)
        
        cbzFile.delete()
    }

    @Test
    fun testExtractWithUnsupportedImageFormats() = runBlocking {
        val cbzFile = File(testDir, "unsupported_test.cbz")
        
        ZipOutputStream(FileOutputStream(cbzFile)).use { zip ->
            // Add supported formats
            listOf("page1.jpg", "page2.png", "page3.gif").forEach { imageName ->
                val entry = ZipEntry(imageName)
                zip.putNextEntry(entry)
                zip.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))
                zip.closeEntry()
            }
            
            // Add unsupported formats
            listOf("page4.bmp", "page5.tiff", "page6.webp").forEach { imageName ->
                val entry = ZipEntry(imageName)
                zip.putNextEntry(entry)
                zip.write("fake image data".toByteArray())
                zip.closeEntry()
            }
        }
        
        val result = extractor.extractComic(android.net.Uri.fromFile(cbzFile))
        
        // Should handle both supported and unsupported formats gracefully
        assertTrue(result.isNotEmpty())
        
        cbzFile.delete()
    }

    @Test
    fun testExtractFromCorruptedArchive() = runBlocking {
        val corruptFile = File(testDir, "corrupt.cbz")
        
        // Create a file that starts like a zip but is corrupted
        corruptFile.writeBytes(byteArrayOf(
            0x50, 0x4B, 0x03, 0x04, // ZIP signature
            0x00, 0x00, 0x00, 0x00, // Corrupted data
            0xFF.toByte(), 0xFF.toByte()
        ))
        
        val result = extractor.extractComic(android.net.Uri.fromFile(corruptFile))
        assertTrue(result.isEmpty())
        
        corruptFile.delete()
    }

    @Test
    fun testExtractWithEmptyArchive() = runBlocking {
        val emptyZipFile = File(testDir, "empty.cbz")
        
        // Create an empty but valid ZIP file
        ZipOutputStream(FileOutputStream(emptyZipFile)).use { zip ->
            // Don't add any entries
        }
        
        val result = extractor.extractComic(android.net.Uri.fromFile(emptyZipFile))
        assertTrue(result.isEmpty())
        
        emptyZipFile.delete()
    }

    @Test
    fun testExtractorInitialization() {
        // Test that the extractor is properly initialized
        assertNotNull(extractor)
        
        // Verify extractor can handle basic operations
        val result = extractor.extractComic(android.net.Uri.parse("file:///invalid/path.cbz"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun testExtractLargeArchive() = runBlocking {
        val largeFile = File(testDir, "large.cbz")
        
        ZipOutputStream(FileOutputStream(largeFile)).use { zip ->
            // Create many entries to simulate a large comic
            repeat(50) { i ->
                val entry = ZipEntry("page${String.format("%03d", i + 1)}.jpg")
                zip.putNextEntry(entry)
                // Write minimal image data
                zip.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))
                zip.closeEntry()
            }
        }
        
        val result = extractor.extractComic(android.net.Uri.fromFile(largeFile))
        
        // Should handle large archives
        assertEquals(50, result.size)
        
        largeFile.delete()
    }
}
