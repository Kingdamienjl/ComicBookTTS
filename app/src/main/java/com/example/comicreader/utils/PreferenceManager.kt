package com.example.comicreader.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.comicreader.models.ComicBook
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PreferenceManager(context: Context) {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREF_NAME, Context.MODE_PRIVATE
    )
    private val gson = Gson()

    fun saveApiKey(apiKey: String) {
        sharedPreferences.edit().putString(KEY_API_KEY, apiKey).apply()
    }

    fun getApiKey(): String {
        // First check SharedPreferences for user-set API key
        val savedKey = sharedPreferences.getString(KEY_API_KEY, null)
        if (!savedKey.isNullOrBlank()) {
            return savedKey
        }
        
        // Return empty string if no API key is set - user must configure it
        return ""
    }
    
    fun hasApiKey(): Boolean {
        val apiKey = getApiKey()
        return apiKey.isNotBlank()
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
        private const val KEY_API_KEY = "elevenlabs_api_key"
        private const val KEY_RECENT_COMICS = "recent_comics"
        private const val MAX_RECENT_COMICS = 10
    }
}