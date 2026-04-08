package com.adityaprasad.vaultdrop.domain.usecase

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import com.adityaprasad.vaultdrop.data.repository.DownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class DeleteDownloadUseCase @Inject constructor(
    private val repository: DownloadRepository,
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(id: String) {
        val download = repository.getDownloadById(id)
        if (download != null) {
            deleteFile(download.filePath)
            deleteFile(download.thumbnailPath)
        }
        repository.deleteDownload(id)
    }

    suspend fun deleteAll() {
        try {
            val allDownloads = repository.getAllDownloadsSync()
            for (download in allDownloads) {
                deleteFile(download.filePath)
                deleteFile(download.thumbnailPath)
            }
        } catch (e: Exception) {
            Log.e("DeleteDownloadUseCase", "Failed to delete files from db list", e)
        }

        // Also clean dirs as fallback using Media Store Query & absolute paths
        try {
            cleanupPublicDirectories()
        } catch (e: Exception) {
            Log.e("DeleteDownloadUseCase", "Failed public directory cleanup", e)
        }

        // Clean cache directories
        val igDir = File(context.getExternalFilesDir(null), "Downloads/Instagram")
        if (igDir.exists()) igDir.deleteRecursively()
        
        val ytDir = File(context.getExternalFilesDir(null), "Downloads/YouTube")
        if (ytDir.exists()) ytDir.deleteRecursively()

        val thumbDir = File(context.cacheDir, "thumbnails")
        if (thumbDir.exists()) thumbDir.deleteRecursively()

        repository.deleteAll()
    }

    private fun deleteFile(path: String?) {
        if (path == null) return
        try {
            if (path.startsWith("content://")) {
                val uri = Uri.parse(path)
                // Just use contentResolver, which handles both file and DB perfectly on scoped storage
                // By not manually deleting the File, MediaStore deletion succeeds normally
                val deletedRows = context.contentResolver.delete(uri, null, null)
                Log.d("DeleteDownloadUseCase", "MediaStore delete returned $deletedRows rows")
            } else {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                    // Tell the gallery that the file was deleted so it removes phantom items from MediaStore
                    MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, _ -> }
                }
            }
        } catch (e: Exception) {
            Log.e("DeleteDownloadUseCase", "Failed to delete file: $path", e)
        }
    }

    private fun cleanupPublicDirectories() {
        val resolver = context.contentResolver
        val collections = listOf(
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else null
        ).filterNotNull()

        for (collection in collections) {
            try {
                val selection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    "${android.provider.MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
                } else {
                    "${android.provider.MediaStore.MediaColumns.DATA} LIKE ?"
                }
                val selectionArgs = arrayOf("%VaultDrop%")

                val cursor = resolver.query(collection, arrayOf(android.provider.MediaStore.MediaColumns._ID), selection, selectionArgs, null)
                cursor?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(c.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID))
                        val uri = android.content.ContentUris.withAppendedId(collection, id)
                        resolver.delete(uri, null, null)
                    }
                }
            } catch (e: Exception) {
                Log.e("DeleteDownloadUseCase", "Failed to clean collection $collection", e)
            }
        }
        
        // Android 9 and below: Also explicitly check the raw directories and delete them securely
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            val baseDirs = listOf(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES),
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES),
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            )
            for (baseDir in baseDirs) {
                val destDir = File(baseDir, "VaultDrop")
                if (destDir.exists()) destDir.deleteRecursively()
            }
        }
    }
}
