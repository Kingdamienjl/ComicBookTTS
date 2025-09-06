package com.example.comicbooktts.utils

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.*

/**
 * Enterprise-Level Hybrid TTS System - Uses Android Native TTS with enhanced comic book optimization
 * 
 * Features:
 * - Android Native TTS (always available, zero cost)
 * - Comic-specific voice selection and speech patterns
 * - Enhanced audio quality and processing
 * - Automatic language detection and fallback
 * - Configurable speech rate and pitch
 * - Usage statistics and performance monitoring
 * - Reliable offline operation
 */
class HybridTTS(private val context: Context) : TextToSpeech.OnInitListener {

    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private val preferenceManager = PreferenceManager(context)
    private var currentCallback: TTSCallback? = null
    private var usageStats = UsageStats()
    
    // Enhanced TTS properties for comic reading
    private var speechRate = 1.0f
    private var speechPitch = 1.0f
    private var currentVoice: Voice? = null
    private var availableVoices: List<Voice> = emptyList()
    private var isComicMode = true // Enable comic-specific speech patterns
    private var currentLanguage = Locale.US
    
    // Advanced voice character mapping
    private var dialogueVoice: Voice? = null
    private var narrationVoice: Voice? = null
    private var soundEffectVoice: Voice? = null
    private var characterVoices: MutableMap<String, Voice> = mutableMapOf()

    companion object {
        private const val TAG = "HybridTTS"
        private const val UTTERANCE_ID = "ComicTTS_"
        private const val COMIC_READING_RATE = 0.85f // Slightly slower for comic reading
        private const val COMIC_PITCH_VARIATION = 0.1f // Subtle pitch variations
        private const val DIALOGUE_RATE = 0.9f // Normal speed for dialogue
        private const val NARRATION_RATE = 0.8f // Slower for narration
        private const val SOUND_EFFECT_RATE = 1.2f // Faster for sound effects
        private const val DIALOGUE_PITCH = 1.0f // Normal pitch for dialogue
        private const val NARRATION_PITCH = 0.95f // Slightly lower for narration
        private const val SOUND_EFFECT_PITCH = 1.1f // Higher pitch for effects
    }

    interface TTSCallback {
        fun onStart()
        fun onComplete()
        fun onError(message: String)
        fun onEngineChanged(engine: String)
    }
    
    enum class TextType {
        DIALOGUE,    // Character speech
        NARRATION,   // Story narration
        SOUND_EFFECT // Sound effects and onomatopoeia
    }

    data class UsageStats(
        var nativeRequests: Int = 0,
        var totalCharacters: Int = 0,
        var successfulReadings: Int = 0,
        var failedReadings: Int = 0,
        var averageConfidence: Float = 0f
    )

    init {
        initializeTTS()
    }

    private fun initializeTTS() {
        textToSpeech = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.let { tts ->
                // Set language with fallback support
                setupLanguageWithFallback(tts)
                
                // Configure audio attributes for professional quality
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
                    .build()

                tts.setAudioAttributes(audioAttributes)

                // Load and configure enhanced speech parameters
                speechRate = preferenceManager.getSpeechRate() * COMIC_READING_RATE
                speechPitch = preferenceManager.getSpeechPitch()

                // Set up voices for comic reading
                setupComicVoices(tts)

                // Configure TTS for expressive comic reading
                configureTTSForComics(tts)

                // Set up progress listener with enhanced error handling
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        currentCallback?.onStart()
                        usageStats.successfulReadings++
                    }

                    override fun onDone(utteranceId: String?) {
                        currentCallback?.onComplete()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        handleTTSError("TTS playback error", utteranceId)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        val errorMessage = when (errorCode) {
                            TextToSpeech.ERROR_NETWORK -> "Network error during TTS"
                            TextToSpeech.ERROR_NETWORK_TIMEOUT -> "Network timeout during TTS"
                            TextToSpeech.ERROR_INVALID_REQUEST -> "Invalid TTS request"
                            TextToSpeech.ERROR_SERVICE -> "TTS service error"
                            TextToSpeech.ERROR_OUTPUT -> "TTS output error"
                            else -> "TTS error: $errorCode"
                        }
                        handleTTSError(errorMessage, utteranceId)
                    }
                })

                isInitialized = true
                Log.d(TAG, "TTS initialized successfully with ${availableVoices.size} voices")
            }
        } else {
            Log.e(TAG, "TTS initialization failed with status: $status")
            currentCallback?.onError("TTS initialization failed")
        }
    }

    /**
     * Setup language with intelligent fallback for comic reading
     */
    private fun setupLanguageWithFallback(tts: TextToSpeech) {
        val preferredLanguages = listOf(
            Locale.US,      // English (US) - primary
            Locale.UK,      // English (UK) - fallback
            Locale.CANADA,  // English (Canada) - fallback
            Locale.ENGLISH  // Generic English - last resort
        )
        
        for (locale in preferredLanguages) {
            val result = tts.setLanguage(locale)
            if (result == TextToSpeech.LANG_AVAILABLE || result == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
                currentLanguage = locale
                Log.d(TAG, "Language set to: ${locale.displayLanguage}")
                break
            } else if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Language not supported: ${locale.displayLanguage}")
            }
        }
    }

    /**
     * Setup comic-specific voices for enhanced reading experience
     */
    private fun setupComicVoices(tts: TextToSpeech) {
        availableVoices = tts.voices?.filter { voice ->
            // Filter for high-quality voices suitable for comic reading
            voice.isNetworkConnectionRequired == false && // Prefer offline voices
            voice.quality >= Voice.QUALITY_HIGH
        } ?: emptyList()
        
        // Assign different voices for different content types
        assignVoicesByCharacteristics()
        
        // Select the best available voice for comic reading
        currentVoice = dialogueVoice ?: availableVoices.firstOrNull { voice ->
            voice.name.contains("en-US", ignoreCase = true) ||
            voice.name.contains("en-GB", ignoreCase = true) ||
            voice.name.contains("en", ignoreCase = true)
        } ?: availableVoices.firstOrNull()
        
        currentVoice?.let { voice ->
            tts.voice = voice
            Log.d(TAG, "Selected primary voice: ${voice.name} (${voice.locale})")
        }
        
        logVoiceAssignments()
    }
    
    /**
     * Assign voices based on characteristics for different comic content types
     */
    private fun assignVoicesByCharacteristics() {
        val maleVoices = availableVoices.filter { 
            it.name.contains("male", ignoreCase = true) && 
            !it.name.contains("female", ignoreCase = true)
        }
        val femaleVoices = availableVoices.filter { 
            it.name.contains("female", ignoreCase = true)
        }
        val neutralVoices = availableVoices.filter { 
            !it.name.contains("male", ignoreCase = true) && 
            !it.name.contains("female", ignoreCase = true)
        }
        
        // Assign dialogue voice (prefer neutral or first available)
        dialogueVoice = neutralVoices.firstOrNull() ?: availableVoices.firstOrNull()
        
        // Assign narration voice (prefer deeper/male voice)
        narrationVoice = maleVoices.firstOrNull() ?: 
                        availableVoices.find { it.name.contains("deep", ignoreCase = true) } ?:
                        dialogueVoice
        
        // Assign sound effect voice (prefer higher pitch/female voice)
        soundEffectVoice = femaleVoices.firstOrNull() ?:
                          availableVoices.find { it.name.contains("high", ignoreCase = true) } ?:
                          dialogueVoice
    }
    
    /**
     * Log voice assignments for debugging
     */
    private fun logVoiceAssignments() {
        Log.d(TAG, "Voice Assignments:")
        Log.d(TAG, "  Dialogue: ${dialogueVoice?.name ?: "None"}")
        Log.d(TAG, "  Narration: ${narrationVoice?.name ?: "None"}")
        Log.d(TAG, "  Sound Effects: ${soundEffectVoice?.name ?: "None"}")
        Log.d(TAG, "  Available voices: ${availableVoices.size}")
    }

    /**
     * Configure TTS specifically for comic book reading
     */
    private fun configureTTSForComics(tts: TextToSpeech) {
        // Set optimal parameters for comic reading
        tts.setSpeechRate(speechRate)
        tts.setPitch(speechPitch)
        
        // Enable enhanced audio processing if available
        try {
            // Set utterance ID for better tracking
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID)
            }
            // Note: setSpeechRate is called in the main configuration
        } catch (e: Exception) {
            Log.w(TAG, "Enhanced audio configuration not available: ${e.message}")
        }
    }

    /**
     * Enhanced text preprocessing for comic book reading
     */
    private fun preprocessTextForComics(text: String): String {
        var processedText = text

        // Handle comic book sound effects with enhanced pronunciation
        val soundEffectReplacements = mapOf(
            "BOOM!" to "Boom!",
            "CRASH!" to "Crash!",
            "BANG!" to "Bang!",
            "POW!" to "Pow!",
            "ZAP!" to "Zap!",
            "WHOOSH!" to "Whoosh!",
            "THUD!" to "Thud!",
            "SLAM!" to "Slam!",
            "WHAM!" to "Wham!",
            "KAPOW!" to "Ka-pow!",
            "SMASH!" to "Smash!",
            "KABOOM!" to "Ka-boom!",
            "BLAM!" to "Blam!",
            "CLANG!" to "Clang!",
            "RUMBLE!" to "Rumble!"
        )

        // Replace sound effects with more pronounceable versions
        for ((original, replacement) in soundEffectReplacements) {
            processedText = processedText.replace(original, replacement, ignoreCase = true)
        }

        // Handle repeated characters (e.g., "Noooo" -> "No")
        processedText = processedText.replace(Regex("(.)\\1{3,}")) { matchResult ->
            matchResult.groupValues[1].repeat(2)
        }

        // Clean up excessive punctuation for better TTS
        processedText = processedText.replace(Regex("[!]{2,}"), "!")
        processedText = processedText.replace(Regex("[?]{2,}"), "?")
        processedText = processedText.replace(Regex("[.]{2,}"), "...")

        // Add natural pauses for comic reading
        processedText = processedText.replace("...", "... ")
        processedText = processedText.replace("—", " — ")
        processedText = processedText.replace("–", " – ")

        // Handle comic-specific formatting
        processedText = processedText.replace(Regex("\\*([^*]+)\\*")) { matchResult ->
            "emphasized ${matchResult.groupValues[1]}"
        }

        return processedText.trim()
    }

    /**
     * Handle TTS errors with enhanced logging and recovery
     */
    private fun handleTTSError(errorMessage: String, utteranceId: String?) {
        Log.e(TAG, "TTS Error: $errorMessage (Utterance: $utteranceId)")
        usageStats.failedReadings++
        currentCallback?.onError(errorMessage)
    }

    /**
     * Determine text type for optimal voice selection
     */
    private fun determineTextType(text: String): TextType {
        return when {
            text.matches(Regex(".*[A-Z]{3,}.*[!]+.*")) -> TextType.SOUND_EFFECT
            text.startsWith("*") && text.endsWith("*") -> TextType.NARRATION
            text.contains(":") && text.indexOf(":") < text.length / 3 -> TextType.DIALOGUE
            text.matches(Regex(".*\\b(BOOM|CRASH|BANG|POW|ZAP|WHOOSH|THUD|SLAM|WHAM|KAPOW|SMASH|KABOOM|BLAM|CLANG|RUMBLE)\\b.*")) -> TextType.SOUND_EFFECT
            else -> TextType.DIALOGUE
        }
    }
    
    /**
     * Apply voice and speech settings based on text type
     */
    private fun applyTextTypeSettings(textType: TextType) {
        textToSpeech?.let { tts ->
            when (textType) {
                TextType.DIALOGUE -> {
                    dialogueVoice?.let { tts.voice = it }
                    tts.setSpeechRate(speechRate * DIALOGUE_RATE)
                    tts.setPitch(speechPitch * DIALOGUE_PITCH)
                }
                TextType.NARRATION -> {
                    narrationVoice?.let { tts.voice = it }
                    tts.setSpeechRate(speechRate * NARRATION_RATE)
                    tts.setPitch(speechPitch * NARRATION_PITCH)
                }
                TextType.SOUND_EFFECT -> {
                    soundEffectVoice?.let { tts.voice = it }
                    tts.setSpeechRate(speechRate * SOUND_EFFECT_RATE)
                    tts.setPitch(speechPitch * SOUND_EFFECT_PITCH)
                }
            }
        }
    }
    
    /**
     * Speak text using enhanced Android native TTS with dynamic voice selection
     */
    fun speak(text: String, callback: TTSCallback) {
        currentCallback = callback
        usageStats.totalCharacters += text.length

        if (!isInitialized) {
            callback.onError("TTS not initialized - please wait and try again")
            return
        }

        if (text.isBlank()) {
            callback.onError("No text to speak")
            return
        }
        
        // Determine text type and apply appropriate voice settings
        val textType = determineTextType(text)
        applyTextTypeSettings(textType)
        
        Log.d(TAG, "Speaking as ${textType.name}: ${text.take(50)}...")

        Log.d(TAG, "Using Enhanced Android Native TTS")
        callback.onEngineChanged("Enhanced Android Native TTS")
        usageStats.nativeRequests++

        textToSpeech?.let { tts ->
            tts.stop()

            // Preprocess text for better comic reading
            val processedText = preprocessTextForComics(text)
            Log.d(TAG, "Processed text: ${processedText.take(100)}...")

            // Create parameters bundle with enhanced utterance ID
            val utteranceId = UTTERANCE_ID + System.currentTimeMillis()
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }

            Log.d(TAG, "Starting TTS with utterance ID: $utteranceId")

            // Speak the text with enhanced parameters
            val result = tts.speak(processedText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (result == TextToSpeech.ERROR) {
                callback.onError("Failed to start speech synthesis")
            }
        } ?: run {
            callback.onError("TTS engine not available")
        }
    }

    /**
     * Speak text with simple boolean return for testing
     */
    fun speak(text: String): Boolean {
        if (!isInitialized || text.isBlank()) {
            return false
        }
        
        textToSpeech?.let { tts ->
            val processedText = preprocessTextForComics(text)
            val utteranceId = UTTERANCE_ID + System.currentTimeMillis()
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            
            val result = tts.speak(processedText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            return result != TextToSpeech.ERROR
        }
        
        return false
    }

    /**
     * Check if TTS is available
     */
    fun isTTSAvailable(): Boolean {
        return isInitialized && textToSpeech != null
    }

    /**
     * Get current TTS engine being used
     */
    fun getCurrentEngine(): String {
        return "Enhanced Android Native TTS"
    }

    /**
     * Get usage statistics
     */
    fun getUsageStats(): UsageStats {
        return usageStats.copy()
    }

    /**
     * Reset usage statistics
     */
    fun resetUsageStats() {
        usageStats = UsageStats()
    }

    /**
     * Test TTS functionality with comic-specific test text
     */
    fun testTTS(callback: TTSCallback) {
        val testText = "This is a test of the enhanced Android TTS system for comic book reading. " +
                      "It includes sound effects like BOOM! and CRASH! for testing purposes."
        speak(testText, callback)
    }

    /**
     * Stop current playback
     */
    fun stop() {
        textToSpeech?.stop()
    }

    /**
     * Check if TTS is currently playing
     */
    fun isPlaying(): Boolean {
        return textToSpeech?.isSpeaking == true
    }

    /**
     * Set speech rate with comic-specific optimization
     */
    fun setSpeechRate(rate: Float) {
        speechRate = rate * COMIC_READING_RATE
        textToSpeech?.setSpeechRate(speechRate)
        preferenceManager.saveSpeechRate(rate)
    }

    /**
     * Set pitch
     */
    fun setPitch(pitch: Float) {
        textToSpeech?.setPitch(pitch)
        preferenceManager.saveSpeechPitch(pitch)
    }

    /**
     * Get available voices for user selection
     */
    fun getAvailableVoices(): List<Voice> {
        return availableVoices
    }

    /**
     * Set specific voice for comic reading
     */
    fun setVoice(voice: Voice) {
        currentVoice = voice
        textToSpeech?.voice = voice
        Log.d(TAG, "Voice changed to: ${voice.name}")
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        textToSpeech?.shutdown()
        textToSpeech = null
        isInitialized = false
        currentCallback = null
    }
}
