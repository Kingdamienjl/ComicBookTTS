package com.example.comicbooktts

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.example.comicbooktts.databinding.ActivitySplashBinding

/**
 * Splash screen with immersive comic-book animations.
 * Rebuilt 2026-10-11 from IMPLEMENTATION_SUMMARY_2026-01.md spec:
 * - Logo burst: scale 0→1.5→1 + 360° rotation (1000ms)
 * - Logo: scale 0→1.3→1 + -180°→0° rotation, bounce (1200ms)
 * - Title bubble: scale 0→1.2→1, bounce (800ms, delay 800ms)
 * - Subtitle bubble: scale 0→1.1→1, bounce (800ms, delay 1200ms)
 * - Loading: pulsing alpha + scale (infinite)
 * - 5 comic effects: explosive staggered entrances
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val splashDuration = 4500L // 4.5s for full animation sequence

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            Log.d("SplashActivity", "Creating splash screen...")
            binding = ActivitySplashBinding.inflate(layoutInflater)
            setContentView(binding.root)

            hideSystemUI()
            startSplashAnimations()

            Handler(Looper.getMainLooper()).postDelayed({
                navigateToMainActivity()
            }, splashDuration)

        } catch (e: Exception) {
            Log.e("SplashActivity", "Error in splash screen: ${e.message}", e)
            navigateToMainActivity()
        }
    }

    private fun hideSystemUI() {
        try {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        } catch (e: Exception) {
            Log.w("SplashActivity", "Could not hide system UI: ${e.message}")
        }
    }

    private fun startSplashAnimations() {
        try {
            // Logo burst: scale 0→1.5→1 + 360° rotation (1000ms)
            val burstScaleX = ObjectAnimator.ofFloat(binding.ivLogoBurst, "scaleX", 0f, 1.5f, 1f)
            val burstScaleY = ObjectAnimator.ofFloat(binding.ivLogoBurst, "scaleY", 0f, 1.5f, 1f)
            val burstAlpha = ObjectAnimator.ofFloat(binding.ivLogoBurst, "alpha", 0f, 1f)
            val burstRotation = ObjectAnimator.ofFloat(binding.ivLogoBurst, "rotation", 0f, 360f)
            AnimatorSet().apply {
                playTogether(burstScaleX, burstScaleY, burstAlpha, burstRotation)
                duration = 1000
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }

            // Logo: scale 0→1.3→1 + -180°→0° rotation with bounce (1200ms)
            val logoScaleX = ObjectAnimator.ofFloat(binding.ivLogo, "scaleX", 0f, 1.3f, 1f)
            val logoScaleY = ObjectAnimator.ofFloat(binding.ivLogo, "scaleY", 0f, 1.3f, 1f)
            val logoAlpha = ObjectAnimator.ofFloat(binding.ivLogo, "alpha", 0f, 1f)
            val logoRotation = ObjectAnimator.ofFloat(binding.ivLogo, "rotation", -180f, 0f)
            AnimatorSet().apply {
                playTogether(logoScaleX, logoScaleY, logoAlpha, logoRotation)
                duration = 1200
                interpolator = BounceInterpolator()
                start()
            }

            // Title bubble: scale 0→1.2→1 with bounce (800ms, delay 800ms)
            val titleScaleX = ObjectAnimator.ofFloat(binding.titleBubbleContainer, "scaleX", 0f, 1.2f, 1f)
            val titleScaleY = ObjectAnimator.ofFloat(binding.titleBubbleContainer, "scaleY", 0f, 1.2f, 1f)
            val titleAlpha = ObjectAnimator.ofFloat(binding.titleBubbleContainer, "alpha", 0f, 1f)
            AnimatorSet().apply {
                playTogether(titleScaleX, titleScaleY, titleAlpha)
                duration = 800
                startDelay = 800
                interpolator = BounceInterpolator()
                start()
            }

            // Subtitle bubble: scale 0→1.1→1 with bounce (800ms, delay 1200ms)
            val subScaleX = ObjectAnimator.ofFloat(binding.subtitleBubbleContainer, "scaleX", 0f, 1.1f, 1f)
            val subScaleY = ObjectAnimator.ofFloat(binding.subtitleBubbleContainer, "scaleY", 0f, 1.1f, 1f)
            val subAlpha = ObjectAnimator.ofFloat(binding.subtitleBubbleContainer, "alpha", 0f, 1f)
            AnimatorSet().apply {
                playTogether(subScaleX, subScaleY, subAlpha)
                duration = 800
                startDelay = 1200
                interpolator = BounceInterpolator()
                start()
            }

            // Loading text: fade in then pulse
            val loadingAlpha = ObjectAnimator.ofFloat(binding.tvLoading, "alpha", 0f, 1f).apply {
                duration = 500
                startDelay = 1500
                start()
            }
            loadingAlpha.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    startLoadingPulse()
                }
            })

            // Comic effects: explosive staggered entrances
            startComicEffectsAnimation()

        } catch (e: Exception) {
            Log.e("SplashActivity", "Error starting animations: ${e.message}")
        }
    }

    private fun startComicEffectsAnimation() {
        try {
            val effects = listOf(
                Triple(binding.tvEffectPow, 1400L, 0.7f),
                Triple(binding.tvEffectBoom, 1800L, 0.7f),
                Triple(binding.tvEffectZap, 2200L, 0.7f),
                Triple(binding.tvEffectWham, 2600L, 0.7f),
                Triple(binding.tvEffectKapow, 3000L, 0.8f)
            )
            for ((view, delay, endAlpha) in effects) {
                val sx = ObjectAnimator.ofFloat(view, "scaleX", 0.2f, 1.4f, 1f)
                val sy = ObjectAnimator.ofFloat(view, "scaleY", 0.2f, 1.4f, 1f)
                val alpha = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f, endAlpha)
                AnimatorSet().apply {
                    playTogether(sx, sy, alpha)
                    duration = 600
                    startDelay = delay
                    interpolator = BounceInterpolator()
                    start()
                }
            }
        } catch (e: Exception) {
            Log.e("SplashActivity", "Error animating comic effects: ${e.message}")
        }
    }

    private fun startLoadingPulse() {
        try {
            val pulseAlpha = ObjectAnimator.ofFloat(binding.tvLoading, "alpha", 0.3f, 1f).apply {
                duration = 800
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
            }
            val pulseScale = ObjectAnimator.ofFloat(binding.tvLoading, "scaleX", 0.95f, 1.05f).apply {
                duration = 800
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
            }
            val pulseScaleY = ObjectAnimator.ofFloat(binding.tvLoading, "scaleY", 0.95f, 1.05f).apply {
                duration = 800
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
            }
            AnimatorSet().apply {
                playTogether(pulseAlpha, pulseScale, pulseScaleY)
                start()
            }
        } catch (e: Exception) {
            Log.e("SplashActivity", "Error starting pulse animation: ${e.message}")
        }
    }

    private fun navigateToMainActivity() {
        try {
            Log.d("SplashActivity", "Navigating to MainActivity...")
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        } catch (e: Exception) {
            Log.e("SplashActivity", "Error navigating to MainActivity: ${e.message}")
            finish()
        }
    }

    override fun onBackPressed() {
        // Disable back button during splash
    }
}
