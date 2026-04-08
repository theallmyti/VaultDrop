package com.adityaprasad.vaultdrop.data.downloader

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class that ensures downloaded media files are visible in the device Gallery.
 *
 * Strategy:
 * - Android 10+ (API 29+): Uses MediaStore API to insert files into the public media collection.
 * - Android 9 and below: Saves to public Downloads directory and triggers MediaScanner.
 */
@Singleton
class MediaStoreHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "MediaStoreHelper"
        private const val VAULTDROP_FOLDER = "VaultDrop"
    }

    /**
     * Copies a downloaded file to the public media store so it appears in the gallery.
     * Returns the public file path (or content URI string) on success.
     *
     * @param sourceFile The file in the app's private storage
     * @param subFolder  Sub-folder name (e.g., "Instagram", "YouTube")
     * @param mimeType   MIME type of the file (e.g., "video/mp4", "image/jpeg")
     * @return The public file path or content URI string, or null on failure
     */
    fun copyToMediaStore(sourceFile: File, subFolder: String, mimeType: String): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                copyViaMediaStoreApi(sourceFile, subFolder, mimeType)
            } else {
                copyToPublicDirectoryLegacy(sourceFile, subFolder, mimeType)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy to MediaStore: ${e.message}", e)
            // Fallback: just scan the private file so at least some gallery apps can find it
            scanFile(sourceFile, mimeType)
            null
        }
    }

    /**
     * Android 10+ (API 29+): Uses MediaStore API for scoped storage compliance.
     */
    private fun copyViaMediaStoreApi(sourceFile: File, subFolder: String, mimeType: String): String? {
        val isVideo = mimeType.startsWith("video/")
        val isImage = mimeType.startsWith("image/")

        val collection: Uri
        val relativePath: String

        when {
            isVideo -> {
                collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                relativePath = "${Environment.DIRECTORY_MOVIES}/$VAULTDROP_FOLDER/$subFolder"
            }
            isImage -> {
                collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                relativePath = "${Environment.DIRECTORY_PICTURES}/$VAULTDROP_FOLDER/$subFolder"
            }
            else -> {
                collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$VAULTDROP_FOLDER/$subFolder"
            }
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(collection, contentValues)
            ?: run {
                Log.e(TAG, "MediaStore insert returned null URI")
                return null
            }

        resolver.openOutputStream(uri)?.use { outputStream ->
            FileInputStream(sourceFile).use { inputStream ->
                inputStream.copyTo(outputStream, bufferSize = 8192)
            }
        } ?: run {
            Log.e(TAG, "Could not open output stream for MediaStore URI")
            resolver.delete(uri, null, null)
            return null
        }

        // Mark as not pending — now visible to gallery
        contentValues.clear()
        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, contentValues, null, null)

        Log.d(TAG, "File copied to MediaStore: $uri (${sourceFile.name})")
        return uri.toString()
    }

    /**
     * Android 9 and below: Copy to public directory and scan with MediaScanner.
     */
    @Suppress("DEPRECATION")
    private fun copyToPublicDirectoryLegacy(sourceFile: File, subFolder: String, mimeType: String): String? {
        val isVideo = mimeType.startsWith("video/")
        val isImage = mimeType.startsWith("image/")

        val baseDir = when {
            isVideo -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            isImage -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            else -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        }

        val destDir = File(baseDir, "$VAULTDROP_FOLDER/$subFolder")
        if (!destDir.exists()) destDir.mkdirs()

        val destFile = File(destDir, sourceFile.name)

        FileInputStream(sourceFile).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output, bufferSize = 8192)
            }
        }

        // Trigger MediaScanner to make the file visible
        scanFile(destFile, mimeType)

        Log.d(TAG, "File copied to public dir: ${destFile.absolutePath}")
        return destFile.absolutePath
    }

    /**
     * Triggers MediaScanner for a specific file so it shows up in gallery apps.
     */
    fun scanFile(file: File, mimeType: String? = null) {
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            if (mimeType != null) arrayOf(mimeType) else null
        ) { path, uri ->
            Log.d(TAG, "MediaScanner scanned: $path -> $uri")
        }
    }

    /**
     * Returns the MIME type based on file extension.
     */
    fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "application/octet-stream"
        }
    }
}
