package com.adityaprasad.vaultdrop.data.downloader

import android.content.Context
import android.util.Log
import com.adityaprasad.vaultdrop.data.repository.DownloadRepository
import com.adityaprasad.vaultdrop.domain.model.DownloadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject
import com.adityaprasad.vaultdrop.util.InstagramUrlUtils
import com.adityaprasad.vaultdrop.util.InstagramSessionManager

@Singleton
class InstagramDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val repository: DownloadRepository,
    private val webViewExtractor: WebViewExtractor
) {

    companion object {
        private const val TAG = "InstagramDownloader"
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }

    private val downloadDir: File
        get() {
            val dir = File(context.getExternalFilesDir(null), "Downloads/Instagram")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    suspend fun download(
        downloadId: String,
        url: String,
        onProgress: (Int, Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            repository.updateProgress(downloadId, DownloadStatus.ACTIVE, 0, 0)

            // 1. First extract the video URL and potentially the username from the main extraction
            val extractionResult = extractVideoUrl(url)
            val videoUrl = extractionResult?.videoUrl
            val extractedUsername = extractionResult?.username
            
            if (videoUrl == null) {
                return@withContext Result.failure(
                    Exception("Could not extract video. The post may be private or temporarily unavailable.")
                )
            }

            Log.d(TAG, "Got video URL, starting download...")
            repository.updateProgress(downloadId, DownloadStatus.ACTIVE, 5, 0)

            // Try to extract title and username from the page metadata
            val (title, metadataUsername) = getMetadata(url)
            val shortcode = extractShortcode(url)
            val fallbackTitle = when {
                title != null -> title
                shortcode != null -> "Instagram · $shortcode"
                else -> "Instagram Post"
            }
            repository.updateTitle(downloadId, fallbackTitle)
            
            val finalUsername = extractedUsername ?: metadataUsername
            if (finalUsername != null) {
                repository.updateUsername(downloadId, finalUsername)
            }

            downloadFile(videoUrl, url, onProgress)
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            Result.failure(e)
        }
    }

    private suspend fun extractVideoUrl(pageUrl: String): WebViewExtractor.ExtractionResult? {
        Log.d(TAG, "Extracting video URL from: $pageUrl")

        // All strategies are handled by WebViewExtractor:
        //  1. Instagram GraphQL API (yt-dlp method with doc_id)
        //  2. Embed page __additionalDataLoaded JSON
        //  3. Embed page regex scraping
        try {
            val result = webViewExtractor.extractVideoUrl(pageUrl)
            if (result != null) {
                Log.d(TAG, "Video URL extracted successfully")
                return result
            }
        } catch (e: Exception) {
            Log.e(TAG, "Video extraction failed: ${e.message}")
        }

        Log.d(TAG, "All extraction strategies failed")
        return null
    }


    suspend fun getMetadata(pageUrl: String): Pair<String?, String?> = withContext(Dispatchers.IO) {
        return@withContext try {
            val builder = Request.Builder()
                .url(pageUrl)
                .header("User-Agent", DESKTOP_UA)
            InstagramSessionManager.getCookieHeader(context)?.let { cookie ->
                builder.header("Cookie", cookie)
            }
            val request = builder.build()
            val response = httpClient.newCall(request).execute()
            val html = response.body?.string() ?: return@withContext Pair(null, null)
            
            val titlePattern = Regex("""og:title"\s+content="([^"]+)"""")
            val title = titlePattern.find(html)?.groupValues?.get(1)
            
            var username: String? = null
            
            // 1. Try __additionalDataLoaded json
            val additionalDataPattern = Regex("""window\.__additionalDataLoaded\s*\(\s*[^,]+,\s*(\{.+?\})\s*\)""")
            val jsonMatch = additionalDataPattern.find(html)
            if (jsonMatch != null) {
                try {
                    val json = JSONObject(jsonMatch.groupValues[1])
                    val items = json.optJSONArray("items")
                    if (items != null && items.length() > 0) {
                        username = items.getJSONObject(0).optJSONObject("owner")?.optString("username")
                    }
                    if (username.isNullOrEmpty()) {
                        val media = json.optJSONObject("graphql")?.optJSONObject("shortcode_media") 
                                 ?: json.optJSONObject("shortcode_media")
                        username = media?.optJSONObject("owner")?.optString("username")
                    }
                } catch (e: Exception) {}
            }
            
            // 2. Try og:url
            if (username.isNullOrEmpty()) {
                val urlPattern = Regex("""og:url"\s+content="([^"]+)"""")
                val ogUrl = urlPattern.find(html)?.groupValues?.get(1)
                if (ogUrl != null) {
                    val userRegex = Regex("instagram\\.com/([^/]+)/(?:p|reel|tv|reels)/")
                    userRegex.find(ogUrl)?.groupValues?.get(1)?.let { u ->
                        if (u !in listOf("www", "m", "stories", "explore")) {
                            username = u
                        }
                    }
                }
            }
            
            if (username.isNullOrEmpty()) {
                username = fetchUsernameViaOEmbed(pageUrl)
            }

            val normalizedUsername = username
                ?.trim()
                ?.removePrefix("@")
                ?.takeIf { it.isNotBlank() }
                ?.let { "@$it" }

            Pair(title, normalizedUsername)
        } catch (_: Exception) {
            Pair(null, null)
        }
    }

    private fun fetchUsernameViaOEmbed(pageUrl: String): String? {
        return runCatching {
            val encoded = URLEncoder.encode(pageUrl, "UTF-8")
            val oEmbedUrl = "https://www.instagram.com/oembed/?url=$encoded"

            val request = Request.Builder()
                .url(oEmbedUrl)
                .header("User-Agent", DESKTOP_UA)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@runCatching null

            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)
            val authorUrl = json.optString("author_url")
            val profileMatch = Regex("instagram\\.com/([^/]+)/?").find(authorUrl)
            profileMatch?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    suspend fun getThumbnailUrl(pageUrl: String): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            // Use WebViewExtractor's robust multi-strategy thumbnail extraction
            val normalized = InstagramUrlUtils.normalize(pageUrl)
            webViewExtractor.extractThumbnailUrl(normalized)
                ?: webViewExtractor.extractThumbnailUrl(pageUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract thumbnail: ${e.message}")
            null
        }
    }

    suspend fun getDirectPreviewMediaUrl(pageUrl: String): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val normalized = InstagramUrlUtils.normalize(pageUrl)
            extractVideoUrl(normalized)?.videoUrl
                ?: webViewExtractor.extractThumbnailUrl(normalized)
                ?: webViewExtractor.extractThumbnailUrl(pageUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get preview media url: ${e.message}")
            null
        }
    }

    suspend fun resolveUsername(pageUrl: String): String? = withContext(Dispatchers.IO) {
        val normalized = InstagramUrlUtils.normalize(pageUrl)

        runCatching { extractVideoUrl(normalized)?.username }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { return@withContext formatUsername(it) }

        runCatching { getMetadata(normalized).second }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { return@withContext formatUsername(it) }

        val pathUsername = runCatching {
            val uri = android.net.Uri.parse(normalized)
            val host = uri.host.orEmpty().lowercase()
            if (host.contains("instagram.com")) {
                val firstPath = uri.pathSegments.firstOrNull()?.trim().orEmpty()
                firstPath.takeIf { it.isNotBlank() && it !in setOf("p", "reel", "tv", "reels", "stories", "explore", "share") }
            } else null
        }.getOrNull()

        pathUsername?.let { return@withContext formatUsername(it) }
        null
    }

    private fun extractShortcode(url: String): String? {
        val pattern = Regex("/(?:p|reel|tv|reels)/([A-Za-z0-9_-]+)")
        return pattern.find(url)?.groupValues?.get(1)
    }

    private fun formatUsername(raw: String): String {
        val cleaned = raw.trim().removePrefix("@").takeIf { it.isNotBlank() } ?: return raw
        return "@$cleaned"
    }

    private suspend fun downloadFile(
        videoUrl: String,
        originalUrl: String,
        onProgress: (Int, Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val builder = Request.Builder()
                .url(videoUrl)
                .header("User-Agent", DESKTOP_UA)
                .header("Referer", "https://www.instagram.com/")
            InstagramSessionManager.getCookieHeader(context)?.let { cookie ->
                builder.header("Cookie", cookie)
            }
            val request = builder.build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Download failed: HTTP ${response.code}"))
            }

            val body = response.body
                ?: return@withContext Result.failure(Exception("Empty response body"))
            val contentLength = body.contentLength()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val shortcode = extractShortcode(originalUrl) ?: "media"

            // Detect file type from Content-Type header or URL
            val contentType = response.header("Content-Type") ?: ""
            val isImage = contentType.startsWith("image/") ||
                    videoUrl.contains(".jpg") || videoUrl.contains(".jpeg") ||
                    videoUrl.contains(".png") || videoUrl.contains(".webp")
            val extension = when {
                contentType.contains("jpeg") || contentType.contains("jpg") -> ".jpg"
                contentType.contains("png") -> ".png"
                contentType.contains("webp") -> ".webp"
                contentType.contains("mp4") || contentType.contains("video") -> ".mp4"
                isImage -> ".jpg"
                else -> ".mp4"
            }
            val fileName = "IG_${shortcode}_${timestamp}${extension}"
            val outputFile = File(downloadDir, fileName)

            FileOutputStream(outputFile).use { fos ->
                val buffer = ByteArray(8192)
                var bytesRead: Long = 0
                var lastUpdateTime = System.currentTimeMillis()
                var lastBytesRead = 0L
                val inputStream = body.byteStream()

                while (true) {
                    val read = inputStream.read(buffer)
                    if (read == -1) break
                    fos.write(buffer, 0, read)
                    bytesRead += read

                    val now = System.currentTimeMillis()
                    val elapsed = now - lastUpdateTime
                    if (elapsed >= 500) {
                        val progress = if (contentLength > 0) {
                            ((bytesRead * 100) / contentLength).toInt().coerceIn(5, 99)
                        } else 50
                        val speed = ((bytesRead - lastBytesRead) * 1000) / elapsed
                        onProgress(progress, speed)
                        lastUpdateTime = now
                        lastBytesRead = bytesRead
                    }
                }
            }

            Log.d(TAG, "Download complete: ${outputFile.length()} bytes")
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
