package com.example.comicreader

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.chrisbanes.photoview.PhotoView

class ComicPageAdapter(private val images: List<Uri>) :
    RecyclerView.Adapter<ComicPageAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: PhotoView = view.findViewById(R.id.imageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.page_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.imageView.setImageURI(images[position])
        
        // Enable zooming and panning
        holder.imageView.maximumScale = 5.0f
        holder.imageView.mediumScale = 2.5f
    }

    override fun getItemCount() = images.size
}
