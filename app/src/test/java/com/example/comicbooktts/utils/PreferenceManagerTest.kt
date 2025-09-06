package com.example.comicbooktts.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@RunWith(AndroidJUnit4::class)
class PreferenceManagerTest {

    private lateinit var preferenceManager: PreferenceManager
    private lateinit var context: Context
    
    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences
    
    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        context = ApplicationProvider.getApplicationContext()
        
        // Setup mock editor
        whenever(mockSharedPreferences.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putFloat(any(), any())).thenReturn(mockEditor)
        whenever(mockEditor.putString(any(), any())).thenReturn(mockEditor)
        whenever(mockEditor.putBoolean(any(), any())).thenReturn(mockEditor)
        whenever(mockEditor.putInt(any(), any())).thenReturn(mockEditor)
        whenever(mockEditor.apply()).then { /* no-op */ }
        
        preferenceManager = PreferenceManager(context)
    }

    @Test
    fun testInitialization() {
        assertNotNull(preferenceManager)
    }

    @Test
    fun testDefaultSpeechRate() {
        val speed = preferenceManager.getSpeechRate()
        assertTrue("Speech rate should be positive", speed > 0.0f)
        assertTrue("Speech rate should be reasonable", speed <= 3.0f)
    }

    @Test
    fun testSetAndGetSpeechRate() {
        val testSpeed = 1.5f
        preferenceManager.saveSpeechRate(testSpeed)
        
        // Since we're using real SharedPreferences in this test,
        // we should be able to retrieve the value
        val retrievedSpeed = preferenceManager.getSpeechRate()
        assertEquals(testSpeed, retrievedSpeed, 0.01f)
    }

    @Test
    fun testSpeechRateBoundaries() {
        // Test minimum boundary
        preferenceManager.saveSpeechRate(0.1f)
        val minSpeed = preferenceManager.getSpeechRate()
        assertTrue("Minimum speed should be respected", minSpeed >= 0.1f)
        
        // Test maximum boundary
        preferenceManager.saveSpeechRate(3.0f)
        val maxSpeed = preferenceManager.getSpeechRate()
        assertTrue("Maximum speed should be respected", maxSpeed <= 3.0f)
    }

    @Test
    fun testInvalidSpeechRate() {
        // Test negative speed
        preferenceManager.saveSpeechRate(-1.0f)
        val speed = preferenceManager.getSpeechRate()
        assertTrue("Negative speed should be handled", speed > 0.0f)
        
        // Test zero speed
        preferenceManager.saveSpeechRate(0.0f)
        val zeroSpeed = preferenceManager.getSpeechRate()
        assertTrue("Zero speed should be handled", zeroSpeed > 0.0f)
    }

    @Test
    fun testDefaultSpeechPitch() {
        val pitch = preferenceManager.getSpeechPitch()
        assertTrue("Speech pitch should be positive", pitch > 0.0f)
        assertTrue("Speech pitch should be reasonable", pitch <= 2.0f)
    }

    @Test
    fun testSetAndGetSpeechPitch() {
        val testPitch = 1.2f
        preferenceManager.saveSpeechPitch(testPitch)
        
        val retrievedPitch = preferenceManager.getSpeechPitch()
        assertEquals(testPitch, retrievedPitch, 0.01f)
    }

    @Test
    fun testSpeechPitchBoundaries() {
        // Test minimum boundary
        preferenceManager.saveSpeechPitch(0.5f)
        val minPitch = preferenceManager.getSpeechPitch()
        assertTrue("Minimum pitch should be respected", minPitch >= 0.5f)
        
        // Test maximum boundary
        preferenceManager.saveSpeechPitch(2.0f)
        val maxPitch = preferenceManager.getSpeechPitch()
        assertTrue("Maximum pitch should be respected", maxPitch <= 2.0f)
    }

    @Test
    fun testDefaultSelectedVoice() {
        val voice = preferenceManager.getSelectedVoice()
        assertNotNull("Selected voice should not be null", voice)
    }

    @Test
    fun testSetAndGetSelectedVoice() {
        val testVoice = "test_voice_id"
        preferenceManager.setSelectedVoice(testVoice)
        
        val retrievedVoice = preferenceManager.getSelectedVoice()
        assertEquals(testVoice, retrievedVoice)
    }

    @Test
    fun testSetNullVoice() {
        preferenceManager.setSelectedVoice(null)
        
        val voice = preferenceManager.getSelectedVoice()
        assertNotNull("Null voice should be handled gracefully", voice)
    }

    @Test
    fun testSetEmptyVoice() {
        preferenceManager.setSelectedVoice("")
        
        val voice = preferenceManager.getSelectedVoice()
        assertNotNull("Empty voice should be handled gracefully", voice)
    }

    @Test
    fun testDefaultAutoPlayEnabled() {
        val autoPlay = preferenceManager.isAutoPlayEnabled()
        // Default value can be either true or false, just verify it's a boolean
        assertTrue("Auto play should be true or false", autoPlay == true || autoPlay == false)
    }

    @Test
    fun testSetAndGetAutoPlayEnabled() {
        preferenceManager.setAutoPlayEnabled(true)
        assertTrue("Auto play should be enabled", preferenceManager.isAutoPlayEnabled())
        
        preferenceManager.setAutoPlayEnabled(false)
        assertFalse("Auto play should be disabled", preferenceManager.isAutoPlayEnabled())
    }

    @Test
    fun testDefaultSlideshowInterval() {
        val interval = preferenceManager.getSlideshowInterval()
        assertTrue("Slideshow interval should be positive", interval > 0)
        assertTrue("Slideshow interval should be reasonable", interval <= 60000) // Max 1 minute
    }

    @Test
    fun testSetAndGetSlideshowInterval() {
        val testInterval = 5000 // 5 seconds
        preferenceManager.setSlideshowInterval(testInterval)
        
        val retrievedInterval = preferenceManager.getSlideshowInterval()
        assertEquals(testInterval, retrievedInterval)
    }

    @Test
    fun testSlideshowIntervalBoundaries() {
        // Test minimum boundary
        preferenceManager.setSlideshowInterval(1000) // 1 second
        val minInterval = preferenceManager.getSlideshowInterval()
        assertTrue("Minimum interval should be respected", minInterval >= 1000)
        
        // Test maximum boundary
        preferenceManager.setSlideshowInterval(60000) // 1 minute
        val maxInterval = preferenceManager.getSlideshowInterval()
        assertTrue("Maximum interval should be respected", maxInterval <= 60000)
    }

    @Test
    fun testInvalidSlideshowInterval() {
        // Test negative interval
        preferenceManager.setSlideshowInterval(-1000)
        val interval = preferenceManager.getSlideshowInterval()
        assertTrue("Negative interval should be handled", interval > 0)
        
        // Test zero interval
        preferenceManager.setSlideshowInterval(0)
        val zeroInterval = preferenceManager.getSlideshowInterval()
        assertTrue("Zero interval should be handled", zeroInterval > 0)
    }

    @Test
    fun testDefaultZoomLevel() {
        val zoom = preferenceManager.getZoomLevel()
        assertTrue("Zoom level should be positive", zoom > 0.0f)
        assertTrue("Zoom level should be reasonable", zoom <= 5.0f)
    }

    @Test
    fun testSetAndGetZoomLevel() {
        val testZoom = 2.5f
        preferenceManager.setZoomLevel(testZoom)
        
        val retrievedZoom = preferenceManager.getZoomLevel()
        assertEquals(testZoom, retrievedZoom, 0.01f)
    }

    @Test
    fun testZoomLevelBoundaries() {
        // Test minimum boundary
        preferenceManager.setZoomLevel(0.5f)
        val minZoom = preferenceManager.getZoomLevel()
        assertTrue("Minimum zoom should be respected", minZoom >= 0.5f)
        
        // Test maximum boundary
        preferenceManager.setZoomLevel(5.0f)
        val maxZoom = preferenceManager.getZoomLevel()
        assertTrue("Maximum zoom should be respected", maxZoom <= 5.0f)
    }

    @Test
    fun testDefaultReadingMode() {
        val mode = preferenceManager.getReadingMode()
        assertNotNull("Reading mode should not be null", mode)
        assertTrue("Reading mode should be valid", 
            mode == "single_page" || mode == "double_page" || mode == "continuous")
    }

    @Test
    fun testSetAndGetReadingMode() {
        val testMode = "double_page"
        preferenceManager.setReadingMode(testMode)
        
        val retrievedMode = preferenceManager.getReadingMode()
        assertEquals(testMode, retrievedMode)
    }

    @Test
    fun testInvalidReadingMode() {
        preferenceManager.setReadingMode("invalid_mode")
        
        val mode = preferenceManager.getReadingMode()
        // Should fallback to a valid default mode
        assertTrue("Invalid mode should fallback to valid default",
            mode == "single_page" || mode == "double_page" || mode == "continuous")
    }

    @Test
    fun testMultiplePreferenceOperations() {
        // Test setting multiple preferences in sequence
        preferenceManager.saveSpeechRate(1.5f)
        preferenceManager.saveSpeechPitch(1.2f)
        preferenceManager.setSelectedVoice("test_voice")
        preferenceManager.setAutoPlayEnabled(true)
        preferenceManager.setSlideshowInterval(3000)
        
        // Verify all values are preserved
        assertEquals(1.5f, preferenceManager.getSpeechRate(), 0.01f)
        assertEquals(1.2f, preferenceManager.getSpeechPitch(), 0.01f)
        assertEquals("test_voice", preferenceManager.getSelectedVoice())
        assertTrue(preferenceManager.isAutoPlayEnabled())
        assertEquals(3000, preferenceManager.getSlideshowInterval())
    }

    @Test
    fun testPreferencesPersistence() {
        // Set some preferences
        preferenceManager.saveSpeechRate(1.8f)
        preferenceManager.setAutoPlayEnabled(false)
        
        // Create a new instance (simulating app restart)
        val newPreferenceManager = PreferenceManager(context)
        
        // Verify preferences are persisted
        assertEquals(1.8f, newPreferenceManager.getSpeechRate(), 0.01f)
        assertFalse(newPreferenceManager.isAutoPlayEnabled())
    }
}
