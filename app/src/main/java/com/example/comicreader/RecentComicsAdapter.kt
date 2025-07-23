package com.example.comicreader

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.comicreader.databinding.ItemComicBinding
import com.example.comicreader.models.ComicBook
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class RecentComicsAdapter(
    private val comics: List<ComicBook>,
    private val onComicClicked: (ComicBook) -> Unit
) : RecyclerView.Adapter<RecentComicsAdapter.ComicViewHolder>() {

    inner class ComicViewHolder(private val binding: ItemComicBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(comic: ComicBook) {
            binding.tvComicTitle.text = comic.title
            binding.tvComicPath.text = comic.filePath
            binding.tvLastRead.text = "Last read: ${formatDate(comic.lastReadDate)}"
            
            // Load cover image if available
            comic.coverImagePath?.let { coverPath ->
                val coverFile = File(coverPath)
                if (coverFile.exists()) {
                    binding.ivComicCover.setImageURI(android.net.Uri.fromFile(coverFile))
                } else {
                    binding.ivComicCover.setImageResource(R.drawable.ic_comic_placeholder)
                }
            } ?: binding.ivComicCover.setImageResource(R.drawable.ic_comic_placeholder)
            
            binding.root.setOnClickListener {
                onComicClicked(comic)
            }
        }
        
        private fun formatDate(dateString: String): String {
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val date = inputFormat.parse(dateString) ?: return dateString
                
                val now = Calendar.getInstance()
                val comicDate = Calendar.getInstance().apply { time = date }
                
                return when {
                    isSameDay(now, comicDate) -> "Today"
                    isYesterday(now, comicDate) -> "Yesterday"
                    else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date)
                }
            } catch (e: Exception) {
                return dateString
            }
        }
        
        private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                   cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
        }
        
        private fun isYesterday(now: Calendar, date: Calendar): Boolean {
            val yesterday = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -1)
            }
            return isSameDay(yesterday, date)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ComicViewHolder {
        val binding = ItemComicBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ComicViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ComicViewHolder, position: Int) {
        holder.bind(comics[position])
    }

    override fun getItemCount() = comics.size
}