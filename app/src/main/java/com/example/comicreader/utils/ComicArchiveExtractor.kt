package com.example.comicreader.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.github.junrar.Archive
import com.github.junrar.exception.RarException
import net.lingala.zip4j.ZipFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

class ComicArchiveExtractor(private val context: Context) {

    companion object {
        private const val TAG = "ComicArchiveExtractor"
        private val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp")
    }

    /**
     * Extract comic archive (CBZ or CBR) to a temporary directory
     * @param uri URI of the comic file
     * @return List of image file paths sorted by name
     */
    fun extractComic(uri: Uri): List<String> {
        val fileName = getFileName(uri)
        val extension = fileName.substringAfterLast('.', "").lowercase()
        
        return when (extension) {
            "cbz", "zip" -> extractZip(uri, fileName)
            "cbr", "rar" -> extractRar(uri, fileName)
            else -> emptyList()
        }
    }

    private fun extractZip(uri: Uri, fileName: String): List<String> {
        val tempDir = createTempDirectory(fileName)
        val tempFile = File(tempDir, fileName)
        
        try {
            // Copy the URI content to a temporary file
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            // Extract the ZIP file
            val zipFile = ZipFile(tempFile)
            zipFile.extractAll(tempDir.absolutePath)
            
            // Delete the temporary ZIP file
            tempFile.delete()
            
            // Return sorted list of image files
            return getImageFiles(tempDir)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting ZIP: ${e.message}")
            return emptyList()
        }
    }

    private fun extractRar(uri: Uri, fileName: String): List<String> {
        val tempDir = createTempDirectory(fileName)
        val tempFile = File(tempDir, fileName)
        
        try {
            // Copy the URI content to a temporary file
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            // Extract the RAR file
            val archive = Archive(tempFile)
            val fileHeaders = archive.fileHeaders
            
            for (header in fileHeaders) {
                if (header.isDirectory) continue
                
                val entryName = header.fileName
                if (!isImageFile(entryName)) continue
                
                val outFile = File(tempDir, entryName.substringAfterLast('/'))
                FileOutputStream(outFile).use { output ->
                    archive.extractFile(header, output)
                }
            }
            
            archive.close()
            
            // Delete the temporary RAR file
            tempFile.delete()
            
            // Return sorted list of image files
            return getImageFiles(tempDir)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting RAR: ${e.message}")
            return emptyList()
        }
    }

    private fun createTempDirectory(fileName: String): File {
        val cacheDir = context.cacheDir
        val dirName = fileName.substringBeforeLast('.')
        val tempDir = File(cacheDir, dirName)
        
        // Clear previous extraction if exists
        if (tempDir.exists()) {
            tempDir.deleteRecursively()
        }
        
        tempDir.mkdirs()
        return tempDir
    }

    private fun getImageFiles(directory: File): List<String> {
        return directory.listFiles()
            ?.filter { isImageFile(it.name) }
            ?.sortedBy { it.nameWithoutExtension.padStart(10, '0') }
            ?.map { it.absolutePath }
            ?: emptyList()
    }

    private fun isImageFile(fileName: String): Boolean {
        val lowerCaseName = fileName.lowercase()
        return IMAGE_EXTENSIONS.any { lowerCaseName.endsWith(it) }
    }

    private fun getFileName(uri: Uri): String {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            it.getString(nameIndex)
        } ?: uri.lastPathSegment ?: "comic.cbz"
    }
}