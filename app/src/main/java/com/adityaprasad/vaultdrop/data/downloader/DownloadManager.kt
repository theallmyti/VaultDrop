package com.adityaprasad.vaultdrop.data.downloader

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import com.adityaprasad.vaultdrop.data.repository.DownloadRepository
import com.adityaprasad.vaultdrop.domain.model.DownloadItem
import com.adityaprasad.vaultdrop.domain.model.DownloadStatus
import com.adityaprasad.vaultdrop.domain.model.Platform
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val instagramDownloader: InstagramDownloader,
    private val youtubeDownloader: YouTubeDownloader,
    private val repository: DownloadRepository,
    private val mediaStoreHelper: MediaStoreHelper,
) {
    companion object {
        private const val TAG = "DownloadManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val semaphore = Semaphore(3) // max 3 concurrent downloads

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    private val thumbnailDir: File
        get() {
            val dir = File(context.cacheDir, "thumbnails")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    fun enqueueDownload(item: DownloadItem, quality: String = "720p") {
        scope.launch {
            semaphore.acquire()
            _isProcessing.value = true
            try {
                processDownload(item, quality)
            } finally {
                semaphore.release()
                _isProcessing.value = false
            }
        }
    }

    private suspend fun processDownload(item: DownloadItem, quality: String) {
        val result = when (item.platform) {
            Platform.INSTAGRAM -> instagramDownloader.download(
                downloadId = item.id,
                url = item.url,
                onProgress = { progress, speed ->
                    scope.launch {
                        repository.updateProgress(item.id, DownloadStatus.ACTIVE, progress, speed)
                    }
                }
            )

            Platform.YOUTUBE -> {
                val outputDir = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES), "VaultDrop").absolutePath
                try {
                    val file = youtubeDownloader.downloadVideo(
                        url = item.url,
                        formatId = quality, // actually contains the formatId now from the UI picker
                        outputDir = outputDir,
                        progressCallback = { progress ->
                            scope.launch {
                                // Provide an estimated speed parameter to match existing signature, passing 0L for now.
                                repository.updateProgress(item.id, DownloadStatus.ACTIVE, progress.toInt(), 0L)
                            }
                        }
                    )
                    Result.success(file)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

            Platform.UNSUPPORTED -> Result.failure(Exception("Unsupported platform"))
        }

        result.fold(
            onSuccess = { file ->
                val durationMs = getVideoDuration(file)
                val mimeType = mediaStoreHelper.getMimeType(file)
                val isImage = mimeType.startsWith("image/")

                // Generate thumbnail before we potentially delete the private file
                val thumbnailPath = if (isImage) {
                    // For images, use the file itself as thumbnail
                    file.absolutePath
                } else {
                    // For videos, extract a frame
                    generateVideoThumbnail(file, item.id)
                }

                // Do not copy to MediaStore automatically (App keeps media private)
                val finalPath = file.absolutePath

                repository.markCompleted(
                    id = item.id,
                    filePath = finalPath,
                    fileSize = file.length(),
                    durationMs = durationMs
                )

                // Update thumbnail in DB
                if (thumbnailPath != null) {
                    repository.updateThumbnail(item.id, thumbnailPath)
                }

            },
            onFailure = { error ->
                repository.markFailed(
                    id = item.id,
                    error = error.message ?: "Download failed"
                )
            }
        )
    }

    /**
     * Extracts a frame from a video file and saves it as a JPEG thumbnail.
     */
    private fun generateVideoThumbnail(videoFile: File, downloadId: String): String? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoFile.absolutePath)

            // Get frame at 1 second (or first frame if video is shorter)
            val bitmap = retriever.getFrameAtTime(
                1_000_000L, // 1 second in microseconds
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
            retriever.release()

            if (bitmap != null) {
                val thumbFile = File(thumbnailDir, "thumb_${downloadId}.jpg")
                FileOutputStream(thumbFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos)
                }
                bitmap.recycle()
                Log.d(TAG, "Thumbnail generated: ${thumbFile.absolutePath}")
                thumbFile.absolutePath
            } else {
                Log.w(TAG, "Could not extract frame from video")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Thumbnail generation failed: ${e.message}")
            null
        }
    }

    private fun getVideoDuration(file: File): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            duration?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}
