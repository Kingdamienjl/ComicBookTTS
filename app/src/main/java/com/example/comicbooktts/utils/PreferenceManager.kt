package com.example.comicbooktts.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.comicbooktts.models.ComicBook
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PreferenceManager(context: Context) {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREF_NAME, Context.MODE_PRIVATE
    )
    private val gson = Gson()



    // TTS Settings
    fun saveSpeechRate(rate: Float) {
        sharedPreferences.edit().putFloat(KEY_SPEECH_RATE, rate).apply()
    }

    fun getSpeechRate(): Float {
        return sharedPreferences.getFloat(KEY_SPEECH_RATE, 0.9f) // Default slightly slower for comics
    }

    fun saveSpeechPitch(pitch: Float) {
        sharedPreferences.edit().putFloat(KEY_SPEECH_PITCH, pitch).apply()
    }

    fun getSpeechPitch(): Float {
        return sharedPreferences.getFloat(KEY_SPEECH_PITCH, 1.0f) // Default normal pitch
    }

    fun saveOcrSensitivity(sensitivity: Int) {
        sharedPreferences.edit().putInt(KEY_OCR_SENSITIVITY, sensitivity).apply()
    }

    fun getOcrSensitivity(): Int {
        return sharedPreferences.getInt(KEY_OCR_SENSITIVITY, 5) // Default medium sensitivity
    }

    // Auto-play Settings
    fun setAutoPlayEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_AUTO_PLAY_ENABLED, enabled).apply()
    }

    fun isAutoPlayEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_AUTO_PLAY_ENABLED, false) // Default disabled
    }

    // Slideshow Settings
    fun setSlideshowInterval(interval: Int) {
        sharedPreferences.edit().putInt(KEY_SLIDESHOW_INTERVAL, interval).apply()
    }

    fun getSlideshowInterval(): Int {
        return sharedPreferences.getInt(KEY_SLIDESHOW_INTERVAL, 3000) // Default 3 seconds
    }

    // Voice Settings
    fun setSelectedVoice(voiceId: String?) {
        sharedPreferences.edit().putString(KEY_SELECTED_VOICE, voiceId).apply()
    }

    fun getSelectedVoice(): String? {
        return sharedPreferences.getString(KEY_SELECTED_VOICE, null)
    }

    // Zoom Settings
    fun setZoomLevel(zoom: Float) {
        sharedPreferences.edit().putFloat(KEY_ZOOM_LEVEL, zoom).apply()
    }

    fun getZoomLevel(): Float {
        return sharedPreferences.getFloat(KEY_ZOOM_LEVEL, 1.0f) // Default normal zoom
    }

    // Reading Mode Settings
    fun setReadingMode(mode: String) {
        sharedPreferences.edit().putString(KEY_READING_MODE, mode).apply()
    }

    fun getReadingMode(): String {
        return sharedPreferences.getString(KEY_READING_MODE, "single_page") ?: "single_page"
    }

    fun addRecentComic(comic: ComicBook) {
        val comics = getRecentComics().toMutableList()
        
        // Remove if already exists
        comics.removeIf { it.filePath == comic.filePath }
        
        // Add to the beginning of the list
        comics.add(0, comic)
        
        // Keep only the most recent MAX_RECENT_COMICS
        val trimmedList = if (comics.size > MAX_RECENT_COMICS) {
            comics.subList(0, MAX_RECENT_COMICS)
        } else {
            comics
        }
        
        val json = gson.toJson(trimmedList)
        sharedPreferences.edit().putString(KEY_RECENT_COMICS, json).apply()
    }

    fun getRecentComics(): List<ComicBook> {
        val json = sharedPreferences.getString(KEY_RECENT_COMICS, null) ?: return emptyList()
        val type = object : TypeToken<List<ComicBook>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun updateComicProgress(filePath: String, currentPage: Int) {
        val comics = getRecentComics().toMutableList()
        val index = comics.indexOfFirst { it.filePath == filePath }
        
        if (index != -1) {
            val updatedComic = comics[index].copy(currentPage = currentPage)
            comics[index] = updatedComic
            val json = gson.toJson(comics)
            sharedPreferences.edit().putString(KEY_RECENT_COMICS, json).apply()
        }
    }

    companion object {
        private const val PREF_NAME = "ComicReaderPrefs"
        private const val KEY_RECENT_COMICS = "recent_comics"
        private const val KEY_SPEECH_RATE = "speech_rate"
        private const val KEY_SPEECH_PITCH = "speech_pitch"
        private const val KEY_OCR_SENSITIVITY = "ocr_sensitivity"
        private const val KEY_AUTO_PLAY_ENABLED = "auto_play_enabled"
        private const val KEY_SLIDESHOW_INTERVAL = "slideshow_interval"
        private const val KEY_SELECTED_VOICE = "selected_voice"
        private const val KEY_ZOOM_LEVEL = "zoom_level"
        private const val KEY_READING_MODE = "reading_mode"
        private const val MAX_RECENT_COMICS = 10
    }
}
