package com.example.comicreader.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.mockito.Mockito.*

@RunWith(AndroidJUnit4::class)
class ElevenLabsTTSTest {

    private lateinit var tts: ElevenLabsTTS
    private lateinit var context: Context
    private lateinit var mockCallback: ElevenLabsTTS.TTSCallback

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        tts = ElevenLabsTTS(context)
        mockCallback = mock(ElevenLabsTTS.TTSCallback::class.java)
    }

    @Test
    fun testSpeakWithEmptyText() {
        tts.speak("", mockCallback)
        verify(mockCallback).onError("No text to speak")
    }

    @Test
    fun testSpeakWithBlankText() {
        tts.speak("   ", mockCallback)
        verify(mockCallback).onError("No text to speak")
    }

    @Test
    fun testSpeakWithoutApiKey() {
        // Since we removed the hardcoded API key, this should fail
        tts.speak("Test text", mockCallback)
        verify(mockCallback).onError(contains("API key not configured"))
    }

    @Test
    fun testIsPlayingInitialState() {
        assertFalse(tts.isPlaying())
    }

    @Test
    fun testStopWhenNotPlaying() {
        // Should not throw exception
        tts.stop()
        assertFalse(tts.isPlaying())
    }

    @Test
    fun testCleanup() {
        // Should not throw exception
        tts.cleanup()
        assertFalse(tts.isPlaying())
    }
}