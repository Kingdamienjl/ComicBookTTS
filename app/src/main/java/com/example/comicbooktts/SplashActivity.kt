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

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val splashDuration = 4000L // 4 seconds for better animation experience

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            Log.d("SplashActivity", "Creating splash screen...")
            binding = ActivitySplashBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            // Hide system UI for immersive experience
            hideSystemUI()
            
            // Start animations
            startSplashAnimations()
            
            // Navigate to MainActivity after delay
            Handler(Looper.getMainLooper()).postDelayed({
                navigateToMainActivity()
            }, splashDuration)
            
        } catch (e: Exception) {
            Log.e("SplashActivity", "Error in splash screen: ${e.message}", e)
            // If splash fails, go directly to main activity
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
            // Logo animation - dramatic entrance with rotation
            val logoScaleX = ObjectAnimator.ofFloat(binding.ivLogo, "scaleX", 0f, 1.3f, 1f)
            val logoScaleY = ObjectAnimator.ofFloat(binding.ivLogo, "scaleY", 0f, 1.3f, 1f)
            val logoAlpha = ObjectAnimator.ofFloat(binding.ivLogo, "alpha", 0f, 1f)
            val logoRotation = ObjectAnimator.ofFloat(binding.ivLogo, "rotation", -180f, 0f)

            val logoAnimatorSet = AnimatorSet().apply {
                playTogether(logoScaleX, logoScaleY, logoAlpha, logoRotation)
                duration = 1200
                interpolator = BounceInterpolator()
            }

            // Title animation - slide in from top
            val titleTranslation = ObjectAnimator.ofFloat(binding.tvAppTitle, "translationY", -200f, 0f)
            val titleAlpha = ObjectAnimator.ofFloat(binding.tvAppTitle, "alpha", 0f, 1f)
            
            val titleAnimatorSet = AnimatorSet().apply {
                playTogether(titleTranslation, titleAlpha)
                duration = 800
                startDelay = 500
                interpolator = AccelerateDecelerateInterpolator()
            }

            // Subtitle animation - slide in from bottom
            val subtitleTranslation = ObjectAnimator.ofFloat(binding.tvSubtitle, "translationY", 200f, 0f)
            val subtitleAlpha = ObjectAnimator.ofFloat(binding.tvSubtitle, "alpha", 0f, 1f)
            
            val subtitleAnimatorSet = AnimatorSet().apply {
                playTogether(subtitleTranslation, subtitleAlpha)
                duration = 800
                startDelay = 800
                interpolator = AccelerateDecelerateInterpolator()
            }

            // Loading animation - fade in
            val loadingAlpha = ObjectAnimator.ofFloat(binding.tvLoading, "alpha", 0f, 1f)
            loadingAlpha.apply {
                duration = 500
                startDelay = 1500
            }

            // Start all animations
            logoAnimatorSet.start()
            titleAnimatorSet.start()
            subtitleAnimatorSet.start()
            loadingAlpha.start()
            
            // Add pulsing effect to loading text
            startLoadingPulse()

            // Animate comic book effects
            startComicEffectsAnimation()

        } catch (e: Exception) {
            Log.e("SplashActivity", "Error starting animations: ${e.message}")
        }
    }

    private fun startComicEffectsAnimation() {
        try {
            // Animate POW! effect
            val powScale = ObjectAnimator.ofFloat(binding.tvEffect1, "scaleX", 0.5f, 1.2f, 1f)
            val powScaleY = ObjectAnimator.ofFloat(binding.tvEffect1, "scaleY", 0.5f, 1.2f, 1f)
            val powAlpha = ObjectAnimator.ofFloat(binding.tvEffect1, "alpha", 0f, 1f, 0.7f)

            val powAnimator = AnimatorSet().apply {
                playTogether(powScale, powScaleY, powAlpha)
                duration = 800
                startDelay = 1200
                interpolator = BounceInterpolator()
            }

            // Animate BOOM! effect
            val boomScale = ObjectAnimator.ofFloat(binding.tvEffect2, "scaleX", 0.3f, 1.3f, 1f)
            val boomScaleY = ObjectAnimator.ofFloat(binding.tvEffect2, "scaleY", 0.3f, 1.3f, 1f)
            val boomAlpha = ObjectAnimator.ofFloat(binding.tvEffect2, "alpha", 0f, 1f, 0.7f)

            val boomAnimator = AnimatorSet().apply {
                playTogether(boomScale, boomScaleY, boomAlpha)
                duration = 600
                startDelay = 1800
                interpolator = AccelerateDecelerateInterpolator()
            }

            // Animate ZAP! effect
            val zapScale = ObjectAnimator.ofFloat(binding.tvEffect3, "scaleX", 0.4f, 1.1f, 1f)
            val zapScaleY = ObjectAnimator.ofFloat(binding.tvEffect3, "scaleY", 0.4f, 1.1f, 1f)
            val zapAlpha = ObjectAnimator.ofFloat(binding.tvEffect3, "alpha", 0f, 1f, 0.6f)

            val zapAnimator = AnimatorSet().apply {
                playTogether(zapScale, zapScaleY, zapAlpha)
                duration = 700
                startDelay = 2400
                interpolator = BounceInterpolator()
            }

            powAnimator.start()
            boomAnimator.start()
            zapAnimator.start()

        } catch (e: Exception) {
            Log.e("SplashActivity", "Error animating comic effects: ${e.message}")
        }
    }

    private fun startLoadingPulse() {
        try {
            val pulseAnimation = ObjectAnimator.ofFloat(binding.tvLoading, "alpha", 0.3f, 1f)
            pulseAnimation.apply {
                duration = 800
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                startDelay = 2000
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
            
            // Add transition animation
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
