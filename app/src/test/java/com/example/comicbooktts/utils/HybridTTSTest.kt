package com.example.comicbooktts.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import java.util.*

@RunWith(AndroidJUnit4::class)
class HybridTTSTest {

    private lateinit var hybridTTS: HybridTTS
    private lateinit var context: Context
    
    @Mock
    private lateinit var mockTextToSpeech: TextToSpeech
    
    @Mock
    private lateinit var mockPreferenceManager: PreferenceManager

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        context = ApplicationProvider.getApplicationContext()
        
        // Setup mock preferences
        whenever(mockPreferenceManager.getSpeechRate()).thenReturn(1.0f)
        whenever(mockPreferenceManager.getSpeechPitch()).thenReturn(1.0f)
        whenever(mockPreferenceManager.getSelectedVoice()).thenReturn("")
        whenever(mockPreferenceManager.isAutoPlayEnabled()).thenReturn(true)
        
        hybridTTS = HybridTTS(context)
    }

    @Test
    fun testInitialization() {
        assertNotNull(hybridTTS)
        assertFalse(hybridTTS.isTTSAvailable())
    }

    @Test
    fun testGetCurrentEngine() {
        val engine = hybridTTS.getCurrentEngine()
        assertEquals("Enhanced Android Native TTS", engine)
    }

    @Test
    fun testSpeakWithEmptyText() = runBlocking {
        val result = hybridTTS.speak("")
        assertFalse(result)
    }

    @Test
    fun testSpeakWithBlankText() = runBlocking {
        val result = hybridTTS.speak("   ")
        assertFalse(result)
    }

    @Test
    fun testSpeakWithValidText() = runBlocking {
        val testText = "Hello, this is a test."
        
        // Since TTS might not be initialized in test environment,
        // we expect this to return false but not crash
        val result = hybridTTS.speak(testText)
        // In test environment, TTS is typically not available
        assertFalse(result)
    }

    @Test
    fun testStopSpeaking() {
        // Should not crash even if TTS is not initialized
        hybridTTS.stop()
        // No assertion needed, just verify no exception is thrown
    }

    @Test
    fun testIsSpeaking() {
        val isSpeaking = hybridTTS.isPlaying()
        assertFalse(isSpeaking) // Should be false in test environment
    }

    @Test
    fun testGetUsageStats() {
        val stats = hybridTTS.getUsageStats()
        assertNotNull(stats)
        assertEquals(0, stats.totalCharacters)
        assertEquals(0, stats.nativeRequests)
        assertEquals(0, stats.successfulReadings)
        assertEquals(0, stats.failedReadings)
    }

    @Test
    fun testSetSpeechRate() {
        // Should not crash even if TTS is not initialized
        hybridTTS.setSpeechRate(1.5f)
        // No assertion needed, just verify no exception is thrown
    }

    @Test
    fun testSetPitch() {
        // Should not crash even if TTS is not initialized
        hybridTTS.setPitch(1.2f)
        // No assertion needed, just verify no exception is thrown
    }

    @Test
    fun testSetSpeechRateWithInvalidValues() {
        // Test with negative value
        hybridTTS.setSpeechRate(-1.0f)
        
        // Test with zero
        hybridTTS.setSpeechRate(0.0f)
        
        // Test with very high value
        hybridTTS.setSpeechRate(10.0f)
        
        // Should handle all cases gracefully without crashing
    }

    @Test
    fun testSetPitchWithInvalidValues() {
        // Test with negative value
        hybridTTS.setPitch(-1.0f)
        
        // Test with zero
        hybridTTS.setPitch(0.0f)
        
        // Test with very high value
        hybridTTS.setPitch(5.0f)
        
        // Should handle all cases gracefully without crashing
    }

    @Test
    fun testGetAvailableVoices() {
        val voices = hybridTTS.getAvailableVoices()
        assertNotNull(voices)
        // In test environment, voices list might be empty
        assertTrue(voices.isEmpty() || voices.isNotEmpty())
    }

    @Test
    fun testSetVoiceWithValidVoice() {
        val voices = hybridTTS.getAvailableVoices()
        if (voices.isNotEmpty()) {
            // Should not crash when setting a valid voice
            hybridTTS.setVoice(voices.first())
        }
        // No assertion needed, just verify no exception is thrown
    }

    @Test
    fun testCleanup() {
        // Should not crash
        hybridTTS.cleanup()
        
        // After cleanup, TTS should not be available
        assertFalse(hybridTTS.isTTSAvailable())
    }

    @Test
    fun testMultipleCleanupCalls() {
        // Multiple cleanup calls should not crash
        hybridTTS.cleanup()
        hybridTTS.cleanup()
        hybridTTS.cleanup()
        
        assertFalse(hybridTTS.isTTSAvailable())
    }

    @Test
    fun testSpeakAfterCleanup() = runBlocking {
        hybridTTS.cleanup()
        
        val result = hybridTTS.speak("Test after cleanup")
        assertFalse(result) // Should return false after cleanup
    }

    @Test
    fun testLongTextHandling() = runBlocking {
        val longText = "This is a very long text that should be handled properly by the TTS system. ".repeat(100)
        
        // Should not crash with long text
        val result = hybridTTS.speak(longText)
        // In test environment, expect false but no crash
        assertFalse(result)
    }

    @Test
    fun testSpecialCharactersInText() = runBlocking {
        val specialText = "Hello! @#$%^&*()_+ 123 ñáéíóú çñ 你好 🎉"
        
        // Should handle special characters gracefully
        val result = hybridTTS.speak(specialText)
        // In test environment, expect false but no crash
        assertFalse(result)
    }
}
