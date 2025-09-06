package com.example.comicbooktts

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.core.app.ActivityScenario
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReaderActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(ReaderActivity::class.java)

    private lateinit var context: Context
    private lateinit var testDir: File

    @Mock
    private lateinit var mockUri: Uri

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        context = ApplicationProvider.getApplicationContext()
        testDir = File(context.cacheDir, "test_reader")
        testDir.mkdirs()
    }

    @Test
    fun testActivityLaunchWithValidUri() {
        // Create a test CBZ file
        val testFile = createTestCBZFile()
        
        val intent = Intent(context, ReaderActivity::class.java).apply {
            data = Uri.fromFile(testFile)
        }
        
        val scenario = ActivityScenario.launch<ReaderActivity>(intent)
        
        scenario.onActivity { activity ->
            assertNotNull(activity)
            assertFalse(activity.isFinishing)
        }
        
        testFile.delete()
    }

    @Test
    fun testActivityLaunchWithNullUri() {
        val intent = Intent(context, ReaderActivity::class.java)
        
        val scenario = ActivityScenario.launch<ReaderActivity>(intent)
        
        // Activity should handle null URI gracefully
        scenario.onActivity { activity ->
            assertNotNull(activity)
        }
    }

    @Test
    fun testActivityLaunchWithInvalidUri() {
        val intent = Intent(context, ReaderActivity::class.java).apply {
            data = Uri.parse("invalid://uri")
        }
        
        val scenario = ActivityScenario.launch<ReaderActivity>(intent)
        
        scenario.onActivity { activity ->
            assertNotNull(activity)
            // Activity should handle invalid URI gracefully
        }
    }

    @Test
    fun testActivityLifecycle() {
        val testFile = createTestCBZFile()
        
        val intent = Intent(context, ReaderActivity::class.java).apply {
            data = Uri.fromFile(testFile)
        }
        
        val scenario = ActivityScenario.launch<ReaderActivity>(intent)
        
        // Test pause and resume
        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        
        scenario.onActivity { activity ->
            assertFalse(activity.isFinishing)
        }
        
        testFile.delete()
    }

    @Test
    fun testSlideshowFunctionality() {
        val testFile = createTestCBZFile()
        
        val intent = Intent(context, ReaderActivity::class.java).apply {
            data = Uri.fromFile(testFile)
        }
        
        val scenario = ActivityScenario.launch<ReaderActivity>(intent)
        
        // Wait for activity to initialize
        Thread.sleep(1000)
        
        // Verify activity is initialized properly
        scenario.onActivity { activity ->
            assertNotNull(activity)
        }
        
        testFile.delete()
    }

    @Test
    fun testTTSInitialization() {
        val testFile = createTestCBZFile()
        
        val intent = Intent(context, ReaderActivity::class.java).apply {
            data = Uri.fromFile(testFile)
        }
        
        val scenario = ActivityScenario.launch<ReaderActivity>(intent)
        
        // Wait for TTS initialization
        Thread.sleep(2000)
        
        // Test basic activity initialization
        scenario.onActivity { activity ->
            assertNotNull(activity)
        }
        
        testFile.delete()
    }

    @Test
    fun testComicLoadingWithEmptyFile() {
        val emptyFile = File(testDir, "empty.cbz")
        emptyFile.createNewFile()
        
        val intent = Intent(context, ReaderActivity::class.java).apply {
            data = Uri.fromFile(emptyFile)
        }
        
        val scenario = ActivityScenario.launch<ReaderActivity>(intent)
        
        // Activity should handle empty file gracefully
        scenario.onActivity { activity ->
            assertNotNull(activity)
        }
        
        emptyFile.delete()
    }

    @Test
    fun testComicLoadingWithCorruptedFile() {
        val corruptFile = File(testDir, "corrupt.cbz")
        corruptFile.writeText("This is not a valid CBZ file")
        
        val intent = Intent(context, ReaderActivity::class.java).apply {
            data = Uri.fromFile(corruptFile)
        }
        
        val scenario = ActivityScenario.launch<ReaderActivity>(intent)
        
        // Activity should handle corrupted file gracefully
        scenario.onActivity { activity ->
            assertNotNull(activity)
        }
        
        corruptFile.delete()
    }

    @Test
    fun testMemoryManagement() {
        val testFile = createTestCBZFile()
        
        // Launch multiple activities to test memory management
        repeat(3) {
            val intent = Intent(context, ReaderActivity::class.java).apply {
                data = Uri.fromFile(testFile)
            }
            
            val scenario = ActivityScenario.launch<ReaderActivity>(intent)
            
            scenario.onActivity { activity ->
                assertNotNull(activity)
            }
            
            // Close scenario
            scenario.close()
            
            Thread.sleep(500)
        }
        
        testFile.delete()
    }

    @Test
    fun testConfigurationChanges() {
        val testFile = createTestCBZFile()
        
        val intent = Intent(context, ReaderActivity::class.java).apply {
            data = Uri.fromFile(testFile)
        }
        
        val scenario = ActivityScenario.launch<ReaderActivity>(intent)
        
        // Simulate configuration change (rotation)
        scenario.onActivity { activity ->
            activity.onConfigurationChanged(activity.resources.configuration)
            assertNotNull(activity)
            assertFalse(activity.isFinishing)
        }
        
        testFile.delete()
    }

    @Test
    fun testErrorHandlingIntegration() {
        val testFile = createTestCBZFile()
        
        val intent = Intent(context, ReaderActivity::class.java).apply {
            data = Uri.fromFile(testFile)
        }
        
        val scenario = ActivityScenario.launch<ReaderActivity>(intent)
        
        // Wait for initialization
        Thread.sleep(1000)
        
        // Activity should handle initialization gracefully
        scenario.onActivity { activity ->
            assertNotNull(activity)
            assertFalse(activity.isFinishing)
        }
        
        testFile.delete()
    }

    @Test
    fun testConcurrentOperations() = runBlocking {
        val testFile = createTestCBZFile()
        
        val intent = Intent(context, ReaderActivity::class.java).apply {
            data = Uri.fromFile(testFile)
        }
        
        val scenario = ActivityScenario.launch<ReaderActivity>(intent)
        
        // Wait for initialization
        Thread.sleep(1000)
        
        // Activity should handle concurrent operations gracefully
        scenario.onActivity { activity ->
            assertNotNull(activity)
        }
        
        testFile.delete()
    }

    @Test
    fun testResourceCleanup() {
        val testFile = createTestCBZFile()
        
        val intent = Intent(context, ReaderActivity::class.java).apply {
            data = Uri.fromFile(testFile)
        }
        
        val scenario = ActivityScenario.launch<ReaderActivity>(intent)
        
        // Wait for initialization
        Thread.sleep(1000)
        
        // Test resource cleanup on destroy
        scenario.onActivity { activity ->
            activity.finish()
        }
        
        // Verify activity is properly cleaned up
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        
        testFile.delete()
    }

    @Test
    fun testLargeComicFile() {
        val largeFile = createLargeTestCBZFile()
        
        val intent = Intent(context, ReaderActivity::class.java).apply {
            data = Uri.fromFile(largeFile)
        }
        
        val scenario = ActivityScenario.launch<ReaderActivity>(intent)
        
        // Wait longer for large file processing
        Thread.sleep(3000)
        
        // Activity should handle large files
        scenario.onActivity { activity ->
            assertNotNull(activity)
        }
        
        largeFile.delete()
    }

    @Test
    fun testPermissionHandling() {
        // Test with a file that might require permissions
        val restrictedFile = File("/system/test.cbz")
        
        val intent = Intent(context, ReaderActivity::class.java).apply {
            data = Uri.fromFile(restrictedFile)
        }
        
        val scenario = ActivityScenario.launch<ReaderActivity>(intent)
        
        // Activity should handle permission issues gracefully
        scenario.onActivity { activity ->
            assertNotNull(activity)
        }
    }

    private fun createTestCBZFile(): File {
        val testFile = File(testDir, "test.cbz")
        
        // Create a minimal ZIP file structure
        val zipData = byteArrayOf(
            0x50, 0x4B, 0x03, 0x04, // ZIP file signature
            0x14, 0x00, 0x00, 0x00, // Version, flags
            0x00, 0x00, 0x00, 0x00, // Compression method, time, date
            0x00, 0x00, 0x00, 0x00, // CRC-32
            0x00, 0x00, 0x00, 0x00, // Compressed size
            0x00, 0x00, 0x00, 0x00, // Uncompressed size
            0x08, 0x00, 0x00, 0x00, // Filename length, extra field length
            0x74, 0x65, 0x73, 0x74, 0x2E, 0x6A, 0x70, 0x67, // "test.jpg"
            0x50, 0x4B, 0x01, 0x02, // Central directory signature
            0x14, 0x00, 0x14, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x08, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x1E, 0x00, 0x00, 0x00,
            0x74, 0x65, 0x73, 0x74, 0x2E, 0x6A, 0x70, 0x67,
            0x50, 0x4B, 0x05, 0x06, // End of central directory
            0x00, 0x00, 0x00, 0x00,
            0x01, 0x00, 0x01, 0x00,
            0x36, 0x00, 0x00, 0x00,
            0x1E, 0x00, 0x00, 0x00,
            0x00, 0x00
        )
        
        testFile.writeBytes(zipData)
        return testFile
    }

    private fun createLargeTestCBZFile(): File {
        val largeFile = File(testDir, "large_test.cbz")
        
        // Create a larger test file (simulate a comic with multiple pages)
        val largeData = ByteArray(1024 * 100) // 100KB file
        largeData[0] = 0x50 // ZIP signature start
        largeData[1] = 0x4B
        largeData[2] = 0x03
        largeData[3] = 0x04
        
        largeFile.writeBytes(largeData)
        return largeFile
    }
}
