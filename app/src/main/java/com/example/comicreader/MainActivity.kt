package com.example.comicreader

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.comicreader.databinding.ActivityMainBinding
import com.example.comicreader.models.ComicBook
import com.example.comicreader.utils.PreferenceManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var recentComicsAdapter: RecentComicsAdapter
    private lateinit var preferenceManager: PreferenceManager
    private val recentComics = mutableListOf<ComicBook>()

    private val openComicLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                openComic(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        preferenceManager = PreferenceManager(this)
        setupRecentComicsList()
        setupClickListeners()

        // Handle intent if app was opened with a comic file
        intent?.data?.let { uri ->
            openComic(uri)
        }
    }

    override fun onResume() {
        super.onResume()
        loadRecentComics()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupRecentComicsList() {
        recentComicsAdapter = RecentComicsAdapter(recentComics) { comic ->
            val file = File(comic.filePath)
            if (file.exists()) {
                val uri = Uri.fromFile(file)
                openComic(uri)
            }
        }

        binding.rvRecentComics.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = recentComicsAdapter
        }

        loadRecentComics()
    }

    private fun loadRecentComics() {
        val savedComics = preferenceManager.getRecentComics()
        recentComics.clear()
        recentComics.addAll(savedComics)
        recentComicsAdapter.notifyDataSetChanged()

        if (recentComics.isEmpty()) {
            binding.tvNoComics.visibility = View.VISIBLE
            binding.rvRecentComics.visibility = View.GONE
        } else {
            binding.tvNoComics.visibility = View.GONE
            binding.rvRecentComics.visibility = View.VISIBLE
        }
    }

    private fun setupClickListeners() {
        binding.btnOpenComic.setOnClickListener {
            // Launch ReaderActivity directly for API testing
            val intent = Intent(this, ReaderActivity::class.java)
            startActivity(intent)
        }
    }

    private fun openComic(uri: Uri) {
        // Save to recent comics
        val fileName = getFileName(uri)
        val filePath = uri.toString()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val lastRead = dateFormat.format(Date())

        val comic = ComicBook(
            title = fileName,
            filePath = filePath,
            lastReadDate = lastRead,
            coverImagePath = null
        )

        preferenceManager.addRecentComic(comic)

        // Open the reader activity
        val intent = Intent(this, ReaderActivity::class.java).apply {
            data = uri
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        startActivity(intent)
    }

    private fun getFileName(uri: Uri): String {
        val cursor = contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            it.getString(nameIndex)
        } ?: uri.lastPathSegment ?: "Unknown Comic"
    }
}