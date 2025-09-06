package com.example.comicbooktts

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.hamcrest.Matchers.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@LargeTest
class ReaderActivityInstrumentationTest {

    private lateinit var context: Context
    private lateinit var testDir: File
    private lateinit var uiDevice: UiDevice

    @get:Rule
    val activityRule = ActivityScenarioRule(
        Intent(ApplicationProvider.getApplicationContext(), ReaderActivity::class.java)
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        testDir = File(context.cacheDir, "instrumentation_test")
        testDir.mkdirs()
        uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun testActivityLaunch() {
        // Test that the activity launches successfully
        onView(withId(android.R.id.content))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testViewPagerExists() {
        // Test that the main ViewPager is present
        onView(withId(R.id.viewPager))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testNavigationButtons() {
        // Test navigation button visibility
        onView(withId(R.id.btnPrevious))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.btnNext))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testSlideshowButton() {
        // Test slideshow button functionality
        onView(withId(R.id.btnSlideshow))
            .check(matches(isDisplayed()))
            .perform(click())
        
        // Wait for slideshow to start
        Thread.sleep(1000)
        
        // Click again to stop
        onView(withId(R.id.btnSlideshow))
            .perform(click())
    }

    @Test
    fun testTTSButton() {
        // Test TTS button functionality
        onView(withId(R.id.btnTTS))
            .check(matches(isDisplayed()))
            .perform(click())
        
        // Wait for TTS initialization
        Thread.sleep(2000)
    }

    @Test
    fun testSettingsButton() {
        // Test settings button
        onView(withId(R.id.btnSettings))
            .check(matches(isDisplayed()))
            .perform(click())
        
        // Wait for settings to open
        Thread.sleep(500)
    }

    @Test
    fun testProgressBar() {
        // Test that progress bar exists
        onView(withId(R.id.progressBar))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testPageNavigation() {
        // Test page navigation with buttons
        onView(withId(R.id.btnNext))
            .perform(click())
        
        Thread.sleep(500)
        
        onView(withId(R.id.btnPrevious))
            .perform(click())
        
        Thread.sleep(500)
    }

    @Test
    fun testSwipeGestures() {
        // Test swipe gestures on ViewPager
        onView(withId(R.id.viewPager))
            .perform(swipeLeft())
        
        Thread.sleep(500)
        
        onView(withId(R.id.viewPager))
            .perform(swipeRight())
        
        Thread.sleep(500)
    }

    @Test
    fun testZoomGestures() {
        // Test zoom functionality (if implemented)
        onView(withId(R.id.viewPager))
            .perform(click())
            .perform(longClick())
        
        Thread.sleep(500)
    }

    @Test
    fun testRotationHandling() {
        // Test device rotation
        uiDevice.setOrientationLandscape()
        Thread.sleep(1000)
        
        // Verify UI is still functional
        onView(withId(R.id.viewPager))
            .check(matches(isDisplayed()))
        
        uiDevice.setOrientationPortrait()
        Thread.sleep(1000)
        
        onView(withId(R.id.viewPager))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testMemoryPressure() {
        // Simulate memory pressure by rotating multiple times
        repeat(5) {
            uiDevice.setOrientationLandscape()
            Thread.sleep(500)
            uiDevice.setOrientationPortrait()
            Thread.sleep(500)
        }
        
        // Verify activity is still responsive
        onView(withId(R.id.viewPager))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testBackgroundAndForeground() {
        // Test app going to background and returning
        uiDevice.pressHome()
        Thread.sleep(1000)
        
        // Return to app
        uiDevice.pressRecentApps()
        Thread.sleep(500)
        uiDevice.click(uiDevice.displayWidth / 2, uiDevice.displayHeight / 2)
        Thread.sleep(1000)
        
        // Verify activity is still functional
        onView(withId(R.id.viewPager))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testMultipleButtonClicks() {
        // Test rapid button clicking
        repeat(5) {
            onView(withId(R.id.btnNext))
                .perform(click())
            Thread.sleep(100)
        }
        
        repeat(5) {
            onView(withId(R.id.btnPrevious))
                .perform(click())
            Thread.sleep(100)
        }
        
        // Verify UI is still responsive
        onView(withId(R.id.viewPager))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testSlideshowInterruption() {
        // Start slideshow
        onView(withId(R.id.btnSlideshow))
            .perform(click())
        
        Thread.sleep(1000)
        
        // Interrupt with manual navigation
        onView(withId(R.id.btnNext))
            .perform(click())
        
        Thread.sleep(500)
        
        // Stop slideshow
        onView(withId(R.id.btnSlideshow))
            .perform(click())
    }

    @Test
    fun testTTSInterruption() {
        // Start TTS
        onView(withId(R.id.btnTTS))
            .perform(click())
        
        Thread.sleep(2000)
        
        // Interrupt with page navigation
        onView(withId(R.id.btnNext))
            .perform(click())
        
        Thread.sleep(500)
        
        // Stop TTS
        onView(withId(R.id.btnTTS))
            .perform(click())
    }

    @Test
    fun testConcurrentFeatures() {
        // Test using multiple features simultaneously
        onView(withId(R.id.btnSlideshow))
            .perform(click())
        
        Thread.sleep(500)
        
        onView(withId(R.id.btnTTS))
            .perform(click())
        
        Thread.sleep(2000)
        
        // Stop both features
        onView(withId(R.id.btnSlideshow))
            .perform(click())
        
        onView(withId(R.id.btnTTS))
            .perform(click())
    }

    @Test
    fun testAccessibility() {
        // Test accessibility features
        onView(withId(R.id.btnNext))
            .check(matches(hasContentDescription()))
        
        onView(withId(R.id.btnPrevious))
            .check(matches(hasContentDescription()))
        
        onView(withId(R.id.btnSlideshow))
            .check(matches(hasContentDescription()))
        
        onView(withId(R.id.btnTTS))
            .check(matches(hasContentDescription()))
    }

    @Test
    fun testErrorRecovery() {
        // Test error recovery by performing invalid operations
        
        // Try to navigate beyond bounds
        repeat(20) {
            onView(withId(R.id.btnNext))
                .perform(click())
            Thread.sleep(50)
        }
        
        repeat(20) {
            onView(withId(R.id.btnPrevious))
                .perform(click())
            Thread.sleep(50)
        }
        
        // Verify UI is still responsive
        onView(withId(R.id.viewPager))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testLongRunningOperations() {
        // Test long-running operations don't block UI
        onView(withId(R.id.btnTTS))
            .perform(click())
        
        // While TTS is initializing, test other UI elements
        onView(withId(R.id.btnNext))
            .perform(click())
        
        onView(withId(R.id.btnSlideshow))
            .perform(click())
        
        Thread.sleep(3000)
        
        // Stop all operations
        onView(withId(R.id.btnSlideshow))
            .perform(click())
        
        onView(withId(R.id.btnTTS))
            .perform(click())
    }

    @Test
    fun testUIConsistency() {
        // Test UI consistency across different states
        
        // Initial state
        onView(withId(R.id.viewPager))
            .check(matches(isDisplayed()))
        
        // After navigation
        onView(withId(R.id.btnNext))
            .perform(click())
        
        onView(withId(R.id.viewPager))
            .check(matches(isDisplayed()))
        
        // After slideshow
        onView(withId(R.id.btnSlideshow))
            .perform(click())
        
        Thread.sleep(1000)
        
        onView(withId(R.id.viewPager))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.btnSlideshow))
            .perform(click())
    }

    @Test
    fun testPerformanceUnderLoad() {
        // Test performance under UI load
        val startTime = System.currentTimeMillis()
        
        repeat(50) {
            onView(withId(R.id.btnNext))
                .perform(click())
            
            if (it % 10 == 0) {
                onView(withId(R.id.btnSlideshow))
                    .perform(click())
                Thread.sleep(100)
                onView(withId(R.id.btnSlideshow))
                    .perform(click())
            }
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        // Verify operations completed in reasonable time (less than 30 seconds)
        assert(duration < 30000) { "UI operations took too long: ${duration}ms" }
        
        // Verify UI is still responsive
        onView(withId(R.id.viewPager))
            .check(matches(isDisplayed()))
    }
}
