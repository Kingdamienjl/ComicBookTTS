package com.example.comicreader.models

import java.io.Serializable

data class ComicBook(
    val title: String,
    val filePath: String,
    val lastReadDate: String,
    val coverImagePath: String?,
    val currentPage: Int = 0
) : Serializable