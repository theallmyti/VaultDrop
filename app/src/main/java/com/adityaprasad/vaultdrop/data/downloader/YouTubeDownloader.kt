package com.adityaprasad.vaultdrop.data.downloader

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class VideoFormat(
    val label: String,
    val formatId: String,
    val height: Int
)

@Singleton
class YouTubeDownloader @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    suspend fun fetchAvailableFormats(url: String): List<VideoFormat> = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(url)
            val info = YoutubeDL.getInstance().getInfo(request)
            
            val formats = info.formats
            if (formats.isNullOrEmpty()) {
                return@withContext emptyList()
            }

            // Deduplicate by height, keeping the highest bitrate
            @Suppress("UNCHECKED_CAST")
            val rawFormats = formats as? List<com.yausername.youtubedl_android.mapper.VideoFormat> ?: emptyList()
            val bestFormats = rawFormats
                .asSequence()
                .filter { it.height > 0 } // video formats only
                .sortedByDescending { it.tbr.toDouble() }
                .distinctBy { it.height }
                .sortedByDescending { it.height }
                .map { format ->
                    val label = if (format.height >= 2160) "4K (2160p)" else "${format.height}p"
                    VideoFormat(label, format.formatId ?: "", format.height)
                }
                .toList()
            return@withContext bestFormats
        } catch (e: Exception) {
            val message = e.message ?: "Unknown error"
            if (message.contains("Private video", ignoreCase = true) || message.contains("Sign in", ignoreCase = true)) {
                throw Exception("This video is unavailable or restricted.")
            } else if (e is YoutubeDLException) {
                // If there's a networking issue, check message
                if (message.contains("Unable to download webpage", ignoreCase = true)) {
                    throw Exception("No internet connection. Please check your network.")
                }
            }
            throw Exception("Failed to fetch formats: $message", e)
        }
    }

    @Suppress("SpellCheckingInspection")
    suspend fun downloadVideo(
        url: String,
        formatId: String,
        outputDir: String,
        progressCallback: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val request = YoutubeDLRequest(url)
        request.addOption("-f", "$formatId+bestaudio/best")
        request.addOption("--merge-output-format", "mp4")
        
        // Output template (make sure directory exists)
        val dir = File(outputDir)
        if (!dir.exists()) dir.mkdirs()
        
        // We use a fixed string template for yt-dlp to write. We add a random suffix to avoid naming conflicts on same title.
        val outputTemplate = "$outputDir/%(title)s_%(id)s.%(ext)s"
        request.addOption("-o", outputTemplate)
        
        // We need a predictable way to find the file, or we can just parse the console output.
        // Even better, `--print after_move:filepath` can print the final output filename, but we can also just use `--exec`, maybe.
        // Actually, we can just execute and then list files in outputDir and find newest, but that's slightly flaky.
        // A standard approach is running it and parsing the actual output if we can't get the file path.
        // Wait, for vaultdrop, we update the title in the DB, so we can just use the output template and pass it to MediaScannerConnection.

        try {
            var finalFilePath: String? = null
            // Workaround to get actual filepath since ytDlp returns the terminal output
            // We can add --print filename
            
            YoutubeDL.getInstance().execute(request, null) { progress, _, line ->
                progressCallback(progress)
                // If line contains 'Destination:' it helps us find the file
                // if line contains 'merging formats into' it gives the final file
                if (line.contains("Destination: ")) {
                    val fp = line.substringAfter("Destination: ").trim()
                    if (fp.endsWith(".mp4")) finalFilePath = fp
                }
                if (line.contains("Merging formats into \"")) {
                    finalFilePath = line.substringAfter("Merging formats into \"").substringBeforeLast("\"")
                }
            }
            
            if (finalFilePath == null) {
                // If we couldn't parse the path from logs, find the newest file in the dir
                val newest = dir.listFiles()?.filter { it.extension == "mp4" }?.maxByOrNull { it.lastModified() }
                finalFilePath = newest?.absolutePath ?: throw Exception("Could not locate downloaded file.")
            }

            val finalFile = File(finalFilePath)
            if (!finalFile.exists()) {
                throw Exception("Could not save file. Check available storage.")
            }

            // Scan the output file into MediaStore
            MediaScannerConnection.scanFile(
                context,
                arrayOf(finalFile.absolutePath),
                arrayOf("video/mp4")
            ) { path: String, uri: Uri? ->
                android.util.Log.d("YouTubeDownloader", "Scanned $path:")
                android.util.Log.d("YouTubeDownloader", "-> uri=$uri")
            }

            return@withContext finalFile
            
        } catch (e: Exception) {
            val message = e.message ?: "Unknown error"
            if (message.contains("Private video", ignoreCase = true)) {
                throw Exception("This video is unavailable or restricted.")
            } else if (message.contains("Unable to download webpage", ignoreCase = true)) {
                throw Exception("No internet connection. Please check your network.")
            } else if (message.contains("ffprobe") || message.contains("ffmpeg")) {
                throw Exception("Failed to process video. The file may be incomplete. ($message)", e)
            } else if (e is YoutubeDLException) {
                throw Exception("Download error: $message", e)
            }
            throw Exception("Failed to download video: $message", e)
        }
    }
}
