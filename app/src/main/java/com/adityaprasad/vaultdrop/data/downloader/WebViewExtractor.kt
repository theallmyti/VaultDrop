@file:Suppress("SpellCheckingInspection", "GrazieInspection")

package com.adityaprasad.vaultdrop.data.downloader

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import com.adityaprasad.vaultdrop.util.InstagramSessionManager

/**
 * Extracts Instagram video download URLs using multiple strategies:
 *
 *  1. Instagram GraphQL API — direct HTTP query (fast, no WebView needed)
 *  2. Embed page JSON parsing — scrape video URL from embed HTML
 *  3. Background WebView — load the actual Instagram page and intercept
 *     CDN requests for video files (most reliable fallback)
 */
@Singleton
class WebViewExtractor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient
) {

    private fun addSessionCookie(builder: Request.Builder): Request.Builder {
        InstagramSessionManager.getCookieHeader(context)?.let { cookie ->
            builder.header("Cookie", cookie)
        }
        return builder
    }

    companion object {
        private const val TAG = "WebViewExtractor"
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private const val MOBILE_UA =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"

        // Same app ID that yt-dlp uses — this is the public web app ID
        private const val IG_APP_ID = "936619743392459"

        // GraphQL doc_id for shortcode_media query (from yt-dlp source)
        private const val GRAPHQL_DOC_ID = "8845758582119845"

        // Timeout for WebView extraction (ms)
        private const val WEBVIEW_TIMEOUT_MS = 15_000L
    }

    data class ExtractionResult(val videoUrl: String, val username: String?)

    /**
     * Attempts to extract a direct video download URL from an Instagram post.
     * Tries multiple strategies in order until one succeeds.
     */
    suspend fun extractVideoUrl(instagramUrl: String): ExtractionResult? = withContext(Dispatchers.IO) {
        Log.d(TAG, "Extracting video URL for: $instagramUrl")

        val shortcode = extractShortcode(instagramUrl)
        if (shortcode == null) {
            Log.e(TAG, "Could not extract shortcode from URL: $instagramUrl")
            return@withContext null
        }
        Log.d(TAG, "Shortcode: $shortcode")

        // Strategy 1: Instagram GraphQL API (yt-dlp method)
        tryGraphQLQuery(shortcode)?.let {
            Log.d(TAG, "Strategy 1 (GraphQL) succeeded: ${it.videoUrl.take(80)}...")
            return@withContext it
        }

        // Strategy 2: Embed page JSON + regex scraping
        tryEmbedPage(shortcode)?.let {
            Log.d(TAG, "Strategy 2 (Embed) succeeded: ${it.videoUrl.take(80)}...")
            return@withContext it
        }

        // Strategy 3: Background WebView — intercept CDN video requests
        tryWebViewExtraction(instagramUrl)?.let {
            Log.d(TAG, "Strategy 3 (WebView) succeeded: ${it.videoUrl.take(80)}...")
            return@withContext it
        }

        Log.d(TAG, "All extraction strategies failed")
        null
    }

    // ──────────────────────────────────────────────
    // Strategy 1: GraphQL API
    // ──────────────────────────────────────────────

    private fun tryGraphQLQuery(shortcode: String): ExtractionResult? {
        return try {
            val pk = shortcodeToPk(shortcode)

            // Step 1: Visit Instagram main page to get cookies (csrftoken, ig_did, etc.)
            val mainPageRequest = addSessionCookie(Request.Builder())
                .url("https://www.instagram.com/")
                .header("User-Agent", DESKTOP_UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            val mainResp = httpClient.newCall(mainPageRequest).execute()
            mainResp.body?.string() // consume body to trigger cookie saving
            Log.d(TAG, "Main page status: ${mainResp.code}")

            // Extract CSRF from cookies
            var csrfToken = extractCsrfFromCookies()

            // Step 2: Hit the ruling endpoint to set up API session
            val setupRequest = addSessionCookie(Request.Builder())
                .url("https://i.instagram.com/api/v1/web/get_ruling_for_content/?content_type=MEDIA&target_id=$pk")
                .header("User-Agent", DESKTOP_UA)
                .header("X-IG-App-ID", IG_APP_ID)
                .header("X-ASBD-ID", "198387")
                .header("X-IG-WWW-Claim", "0")
                .header("Origin", "https://www.instagram.com")
                .header("Accept", "*/*")
                .build()
            val setupResp = httpClient.newCall(setupRequest).execute()
            val setupBody = setupResp.body?.string() ?: ""
            Log.d(TAG, "Ruling response: ${setupResp.code} - ${setupBody.take(200)}")

            // Re-extract CSRF after ruling (might have been updated)
            val updatedCsrf = extractCsrfFromCookies()
            if (updatedCsrf.isNotEmpty()) csrfToken = updatedCsrf

            if (csrfToken.isEmpty()) {
                Log.d(TAG, "No CSRF token obtained, trying GraphQL without it")
            }

            // Step 3: GraphQL query
            val variables = JSONObject().apply {
                put("shortcode", shortcode)
                put("child_comment_count", 3)
                put("fetch_comment_count", 40)
                put("parent_comment_count", 24)
                put("has_threaded_comments", true)
            }

            val queryUrl = "https://www.instagram.com/graphql/query/?doc_id=$GRAPHQL_DOC_ID&variables=${
                java.net.URLEncoder.encode(variables.toString(), "UTF-8")
            }"

            val graphqlRequest = addSessionCookie(Request.Builder())
                .url(queryUrl)
                .header("User-Agent", DESKTOP_UA)
                .header("X-IG-App-ID", IG_APP_ID)
                .header("X-ASBD-ID", "198387")
                .header("X-IG-WWW-Claim", "0")
                .header("Origin", "https://www.instagram.com")
                .header("Accept", "*/*")
                .header("X-CSRFToken", csrfToken)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "https://www.instagram.com/p/$shortcode/")
                .build()

            val graphqlResp = httpClient.newCall(graphqlRequest).execute()
            val graphqlBody = graphqlResp.body?.string() ?: ""
            Log.d(TAG, "GraphQL response: HTTP ${graphqlResp.code}, length=${graphqlBody.length}")

            if (graphqlBody.isBlank() || graphqlResp.code != 200) {
                Log.d(TAG, "GraphQL returned ${graphqlResp.code}: ${graphqlBody.take(300)}")
                return null
            }

            val json = JSONObject(graphqlBody)

            // Try xdt_shortcode_media first (newer format)
            val media = json.optJSONObject("data")?.optJSONObject("xdt_shortcode_media")
                ?: json.optJSONObject("data")?.optJSONObject("shortcode_media")

            if (media == null) {
                Log.d(TAG, "No media object in GraphQL response")
                return null
            }

            val videoUrl = extractMediaUrlFromMedia(media) ?: return null
            val username = media.optJSONObject("owner")?.optString("username")
            ExtractionResult(videoUrl, username)
        } catch (e: Exception) {
            Log.e(TAG, "GraphQL extraction failed: ${e.message}", e)
            null
        }
    }

    /**
     * Extracts a media URL from Instagram's JSON media object.
     * Tries video first, then falls back to the highest-quality image.
     */
    private fun extractMediaUrlFromMedia(media: JSONObject): String? {
        // ── Try video first ──
        val isVideo = media.optBoolean("is_video", false)
        if (isVideo) {
            val videoUrl = media.optString("video_url")
            if (videoUrl.startsWith("http")) {
                return videoUrl.unescapeJson()
            }
        }

        // Video versions (v1 API format)
        val videoVersions = media.optJSONArray("video_versions")
        if (videoVersions != null && videoVersions.length() > 0) {
            val url = videoVersions.getJSONObject(0).optString("url")
            if (url.startsWith("http")) {
                return url.unescapeJson()
            }
        }

        // Carousel / sidecar — find first video, then first image
        val edges = media.optJSONObject("edge_sidecar_to_children")?.optJSONArray("edges")
        if (edges != null) {
            // First pass: look for videos
            for (i in 0 until edges.length()) {
                val node = edges.getJSONObject(i).optJSONObject("node") ?: continue
                if (node.optBoolean("is_video", false)) {
                    val url = node.optString("video_url")
                    if (url.startsWith("http")) {
                        return url.unescapeJson()
                    }
                }
            }
            // Second pass: look for images
            for (i in 0 until edges.length()) {
                val node = edges.getJSONObject(i).optJSONObject("node") ?: continue
                val imgUrl = extractImageUrl(node)
                if (imgUrl != null) return imgUrl
            }
        }

        // ── Fallback to image URL ──
        return extractImageUrl(media)
    }

    /**
     * Extracts the best-quality image URL from a media JSON object.
     * Prioritizes actual post content over generic images.
     */
    private fun extractImageUrl(media: JSONObject): String? {
        // GraphQL format: display_url (actual post image, highest quality)
        val displayUrl = media.optString("display_url")
        if (displayUrl.startsWith("http") && !isInstagramAppIcon(displayUrl)) {
            return displayUrl.unescapeJson()
        }

        // v1 API format: image_versions2.candidates[0].url (actual post image)
        val imageVersions = media.optJSONObject("image_versions2")
        if (imageVersions != null) {
            val candidates = imageVersions.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val url = candidates.getJSONObject(0).optString("url")
                if (url.startsWith("http") && !isInstagramAppIcon(url)) {
                    return url.unescapeJson()
                }
            }
        }

        // display_src (older GraphQL format, actual post image)
        val displaySrc = media.optString("display_src")
        if (displaySrc.startsWith("http") && !isInstagramAppIcon(displaySrc)) {
            return displaySrc.unescapeJson()
        }

        // thumbnail_src (lower quality fallback, but still actual post content)
        val thumbSrc = media.optString("thumbnail_src")
        if (thumbSrc.startsWith("http") && !isInstagramAppIcon(thumbSrc)) {
            return thumbSrc.unescapeJson()
        }

        // thumbnail_resources (often present for reels/video posts)
        val thumbnailResources = media.optJSONArray("thumbnail_resources")
        if (thumbnailResources != null && thumbnailResources.length() > 0) {
            for (i in thumbnailResources.length() - 1 downTo 0) {
                val resource = thumbnailResources.optJSONObject(i) ?: continue
                val url = resource.optString("src")
                if (url.startsWith("http") && !isInstagramAppIcon(url)) {
                    return url.unescapeJson()
                }
            }
        }

        return null
    }

    // ──────────────────────────────────────────────
    // Strategy 2: Embed page
    // ──────────────────────────────────────────────

    private fun tryEmbedPage(shortcode: String): ExtractionResult? {
        return try {
            // Try both /embed/ and /embed/captioned/
            for (suffix in listOf("embed/", "embed/captioned/")) {
                val embedUrl = "https://www.instagram.com/p/$shortcode/$suffix"
                val request = addSessionCookie(Request.Builder())
                    .url(embedUrl)
                    .header("User-Agent", DESKTOP_UA)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()

                val html = httpClient.newCall(request).execute().body?.string() ?: continue
                Log.d(TAG, "Embed ($suffix) page length: ${html.length}")

                // Try __additionalDataLoaded JSON
                val additionalDataPattern = Regex("""window\.__additionalDataLoaded\s*\(\s*[^,]+,\s*(\{.+?\})\s*\)""")
                val jsonMatch = additionalDataPattern.find(html)
                if (jsonMatch != null) {
                    try {
                        val json = JSONObject(jsonMatch.groupValues[1])

                        // items[0] path (v1 API format)
                        val items = json.optJSONArray("items")
                        if (items != null && items.length() > 0) {
                            val item = items.getJSONObject(0)
                            val videoUrl = extractMediaUrlFromMedia(item)
                            if (videoUrl != null) {
                                val username = item.optJSONObject("owner")?.optString("username")
                                return ExtractionResult(videoUrl, username)
                            }
                        }

                        // graphql.shortcode_media path
                        val media = json.optJSONObject("graphql")?.optJSONObject("shortcode_media")
                            ?: json.optJSONObject("shortcode_media")
                        if (media != null) {
                            val videoUrl = extractMediaUrlFromMedia(media)
                            if (videoUrl != null) {
                                val username = media.optJSONObject("owner")?.optString("username")
                                return ExtractionResult(videoUrl, username)
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Embed JSON parse error: ${e.message}")
                    }
                }

                // Regex patterns for direct URL extraction
                val patterns = listOf(
                    Regex(""""video_url"\s*:\s*"(https://[^"]+)""""),
                    Regex(""""contentUrl"\s*:\s*"(https://[^"]+)""""),
                    Regex(""""playbackUrl"\s*:\s*"(https://[^"]+)""""),
                    Regex("""video_url":"(https://[^"]+)""""),
                    Regex("""<meta\s+property="og:video"\s+content="(https://[^"]+)""""),
                    Regex("""<meta\s+property="og:video:secure_url"\s+content="(https://[^"]+)""""),
                    Regex("""<source\s+src="(https://[^"]+)"\s+type="video/mp4""""),
                )

                for (pattern in patterns) {
                    val match = pattern.find(html)
                    if (match != null) {
                        val url = match.groupValues[1].unescapeJson()
                        if (url.startsWith("http") && looksLikeCdnUrl(url)) {
                            // Try to find username as a fallback since json parsing failed
                            var username: String? = null
                            val userRegex = Regex(""""username"\s*:\s*"([^"]+)"""")
                            val userMatch = userRegex.find(html)
                            if (userMatch != null) username = userMatch.groupValues[1].unescapeJson()
                            
                            return ExtractionResult(url, username)
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.d(TAG, "Embed extraction failed: ${e.message}")
            null
        }
    }

    // ──────────────────────────────────────────────
    // Strategy 3: Background WebView
    // ──────────────────────────────────────────────

    /**
     * Loads the Instagram page in a hidden WebView and intercepts network
     * requests to find the CDN video URL. This works because Instagram's
     * web player has to request the actual .mp4 from its CDN, and we
     * catch that request via [WebViewClient.shouldInterceptRequest].
     */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun tryWebViewExtraction(instagramUrl: String): ExtractionResult? {
        return try {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    var resumed = false
                    val handler = Handler(Looper.getMainLooper())

                    val webView = WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.userAgentString = MOBILE_UA
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        // Block images to speed up loading
                        settings.blockNetworkImage = true
                    }

                    InstagramSessionManager.getCookieHeader(context)?.let { cookie ->
                        val cookieManager = android.webkit.CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setCookie("https://www.instagram.com/", cookie)
                        cookieManager.flush()
                    }

                    // Timeout runnable
                    val timeoutRunnable = Runnable {
                        if (!resumed) {
                            resumed = true
                            Log.d(TAG, "WebView timed out after ${WEBVIEW_TIMEOUT_MS}ms")
                            webView.stopLoading()
                            webView.destroy()
                            continuation.resume(null)
                        }
                    }
                    handler.postDelayed(timeoutRunnable, WEBVIEW_TIMEOUT_MS)

                    webView.webViewClient = object : WebViewClient() {

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val url = request?.url?.toString() ?: return null

                            // Look for Instagram video CDN URLs
                            if (looksLikeCdnUrl(url) && !resumed) {
                                Log.d(TAG, "WebView intercepted video URL: ${url.take(100)}...")
                                resumed = true
                                handler.removeCallbacks(timeoutRunnable)
                                handler.post {
                                    webView.stopLoading()
                                    webView.destroy()
                                }
                                continuation.resume(ExtractionResult(url, null))
                            }
                            return null
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            Log.d(TAG, "WebView page finished: $url")

                            // After page loads, try to play the video via JS
                            // to trigger the CDN request
                            view?.evaluateJavascript("""
                                (function() {
                                    // Try clicking play button
                                    var playBtn = document.querySelector('[aria-label="Play"]') 
                                        || document.querySelector('[aria-label="Control"]')
                                        || document.querySelector('button[type="button"]');
                                    if (playBtn) playBtn.click();
                                    
                                    // Try to find video element and get its src
                                    var videos = document.querySelectorAll('video');
                                    for (var i = 0; i < videos.length; i++) {
                                        var v = videos[i];
                                        if (v.src && v.src.startsWith('http')) return v.src;
                                        var source = v.querySelector('source');
                                        if (source && source.src && source.src.startsWith('http')) return source.src;
                                    }
                                    
                                    // Check og:video meta
                                    var meta = document.querySelector('meta[property="og:video"]');
                                    if (meta) return meta.getAttribute('content');
                                    
                                    return '';
                                })()
                            """.trimIndent()) { result ->
                                val videoUrl = result?.trim('"')?.trim() ?: ""
                                if (videoUrl.startsWith("http") && !resumed) {
                                    Log.d(TAG, "WebView JS found video: ${videoUrl.take(100)}...")
                                    resumed = true
                                    handler.removeCallbacks(timeoutRunnable)
                                    webView.stopLoading()
                                    webView.destroy()
                                    continuation.resume(ExtractionResult(videoUrl, null))
                                }
                            }
                        }
                    }

                    continuation.invokeOnCancellation {
                        handler.removeCallbacks(timeoutRunnable)
                        handler.post {
                            webView.stopLoading()
                            webView.destroy()
                        }
                    }

                    Log.d(TAG, "WebView loading: $instagramUrl")
                    webView.loadUrl(instagramUrl)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "WebView extraction failed: ${e.message}", e)
            null
        }
    }

    /**
     * Extracts a thumbnail image URL for an Instagram post.
     * Uses the same robust GraphQL extraction as video extraction.
     */
    suspend fun extractThumbnailUrl(instagramUrl: String): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val shortcode = extractShortcode(instagramUrl)
            if (shortcode == null) {
                Log.d(TAG, "Cannot extract shortcode for thumbnail")
                return@withContext null
            }
            val mediaType = detectMediaType(instagramUrl)
            val canonicalUrl = "https://www.instagram.com/$mediaType/$shortcode/"

            // Use timeout to prevent hanging (5 seconds max)
            kotlinx.coroutines.withTimeoutOrNull(5_000L) {
                var resolvedUrl: String? = null

                // Try GraphQL first (most reliable - this has the actual post image)
                tryGraphQLThumbnail(shortcode)?.let { 
                    if (!isInstagramAppIcon(it)) {
                        resolvedUrl = it
                    }
                }

                // Try embed page
                if (resolvedUrl == null) {
                    tryEmbedPageThumbnail(shortcode)?.let {
                        if (!isInstagramAppIcon(it)) {
                            resolvedUrl = it
                        }
                    }
                }

                // Try oEmbed endpoint (useful for shared links with igsh params)
                if (resolvedUrl == null) {
                    tryOEmbedThumbnail(canonicalUrl)?.let {
                        if (!isInstagramAppIcon(it)) {
                            resolvedUrl = it
                        }
                    }
                }

                // Final fallback: hidden WebView request interception for CDN image URLs.
                if (resolvedUrl == null) {
                    tryWebViewThumbnailExtraction(canonicalUrl)?.let {
                        if (!isInstagramAppIcon(it)) {
                            resolvedUrl = it
                        }
                    }
                }

                // Only fall back to og:image as last resort, but filter out app icons
                if (resolvedUrl == null) {
                    try {
                        val request = addSessionCookie(Request.Builder())
                            .url(canonicalUrl)
                            .header("User-Agent", DESKTOP_UA)
                            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                            .build()
                        val html = httpClient.newCall(request).execute().body?.string()
                        if (!html.isNullOrBlank()) {
                            val imagePattern = Regex("""og:image"\s+content="([^"]+)""", RegexOption.IGNORE_CASE)
                            val url = imagePattern.find(html)?.groupValues?.get(1)?.takeIf { it.startsWith("http") }
                            if (url != null && !isInstagramAppIcon(url)) {
                                resolvedUrl = url
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "og:image extraction failed: ${e.message}")
                    }
                }

                resolvedUrl
            } ?: run {
                Log.d(TAG, "Thumbnail extraction timed out after 5s")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Thumbnail extraction failed: ${e.message}")
            null
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun tryWebViewThumbnailExtraction(instagramUrl: String): String? {
        return try {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    var resumed = false
                    val handler = Handler(Looper.getMainLooper())

                    val webView = WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.userAgentString = MOBILE_UA
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.blockNetworkImage = false
                    }

                    InstagramSessionManager.getCookieHeader(context)?.let { cookie ->
                        val cookieManager = android.webkit.CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setCookie("https://www.instagram.com/", cookie)
                        cookieManager.flush()
                    }

                    val timeoutRunnable = Runnable {
                        if (!resumed) {
                            resumed = true
                            webView.stopLoading()
                            webView.destroy()
                            continuation.resume(null)
                        }
                    }
                    handler.postDelayed(timeoutRunnable, WEBVIEW_TIMEOUT_MS)

                    webView.webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val url = request?.url?.toString() ?: return null

                            if (!resumed && looksLikeImageCdnUrl(url) && !isInstagramAppIcon(url)) {
                                resumed = true
                                handler.removeCallbacks(timeoutRunnable)
                                handler.post {
                                    webView.stopLoading()
                                    webView.destroy()
                                }
                                continuation.resume(url)
                            }
                            return null
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.evaluateJavascript(
                                """
                                (function() {
                                  var og = document.querySelector('meta[property="og:image"]');
                                  if (og && og.content && og.content.startsWith('http')) return og.content;
                                  var tw = document.querySelector('meta[name="twitter:image"]');
                                  if (tw && tw.content && tw.content.startsWith('http')) return tw.content;
                                  var vid = document.querySelector('video');
                                  if (vid && vid.poster && vid.poster.startsWith('http')) return vid.poster;
                                  var imgs = document.querySelectorAll('img');
                                  for (var i = 0; i < imgs.length; i++) {
                                    var src = imgs[i].currentSrc || imgs[i].src;
                                    if (src && src.startsWith('http')) return src;
                                  }
                                  return '';
                                })();
                                """.trimIndent()
                            ) { jsResult ->
                                val candidate = jsResult?.trim('"')?.trim().orEmpty().unescapeJson()
                                if (!resumed && candidate.startsWith("http") && !isInstagramAppIcon(candidate)) {
                                    resumed = true
                                    handler.removeCallbacks(timeoutRunnable)
                                    webView.stopLoading()
                                    webView.destroy()
                                    continuation.resume(candidate)
                                }
                            }
                        }
                    }

                    continuation.invokeOnCancellation {
                        handler.removeCallbacks(timeoutRunnable)
                        handler.post {
                            webView.stopLoading()
                            webView.destroy()
                        }
                    }

                    webView.loadUrl(instagramUrl)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "WebView thumbnail extraction failed: ${e.message}")
            null
        }
    }

    private fun tryOEmbedThumbnail(canonicalUrl: String): String? {
        return try {
            val encoded = java.net.URLEncoder.encode(canonicalUrl, "UTF-8")
            val request = addSessionCookie(Request.Builder())
                .url("https://www.instagram.com/api/v1/oembed/?url=$encoded")
                .header("User-Agent", DESKTOP_UA)
                .header("Accept", "application/json")
                .build()
            val body = httpClient.newCall(request).execute().body?.string() ?: return null
            val json = JSONObject(body)
            json.optString("thumbnail_url")
                .takeIf { it.startsWith("http") }
                ?.unescapeJson()
        } catch (e: Exception) {
            Log.d(TAG, "oEmbed thumbnail extraction failed: ${e.message}")
            null
        }
    }

    /**
     * Checks if the URL points to Instagram's generic app icon or branding rather than actual post content.
     * Instagram post images typically come from scontent-*.instagram.com CDN with UUIDs/hashes.
     */
    private fun isInstagramAppIcon(url: String): Boolean {
        val lowerUrl = url.lowercase()
        
        // These are definitely app icons/branding
        if (lowerUrl.contains("/static/") ||
            lowerUrl.contains("/app-icon") ||
            lowerUrl.contains("instagram.com/logo") ||
            lowerUrl.contains("instagram.com/brand")) {
            return true
        }
        
        // Generic meta images or statics
        if (lowerUrl.contains("-cdn") && lowerUrl.contains("/images/") && !lowerUrl.contains("scontent")) {
            return true
        }
        
        // og:image pointing to Instagram's internal static assets
        if (lowerUrl.contains("instagram.com") && !lowerUrl.contains("scontent") && !lowerUrl.contains("fbcdn")) {
            return true
        }
        
        // Should allow actual post images from scontent and fbcdn CDNs
        if (lowerUrl.contains("scontent") || lowerUrl.contains("fbcdn") || lowerUrl.contains("cdninstagram")) {
            return false
        }
        
        return false
    }

    private fun tryGraphQLThumbnail(shortcode: String): String? {
        return try {
            val mainPageRequest = addSessionCookie(Request.Builder())
                .url("https://www.instagram.com/")
                .header("User-Agent", DESKTOP_UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            httpClient.newCall(mainPageRequest).execute().body?.string() // consume body for cookies
            
            val csrfToken = extractCsrfFromCookies()
            
            val variables = JSONObject().apply {
                put("shortcode", shortcode)
                put("child_comment_count", 3)
                put("fetch_comment_count", 40)
                put("parent_comment_count", 24)
                put("has_threaded_comments", true)
            }

            val queryUrl = "https://www.instagram.com/graphql/query/?doc_id=$GRAPHQL_DOC_ID&variables=${
                java.net.URLEncoder.encode(variables.toString(), "UTF-8")
            }"

            val graphqlRequest = addSessionCookie(Request.Builder())
                .url(queryUrl)
                .header("User-Agent", DESKTOP_UA)
                .header("X-IG-App-ID", IG_APP_ID)
                .header("X-ASBD-ID", "198387")
                .header("X-IG-WWW-Claim", "0")
                .header("Origin", "https://www.instagram.com")
                .header("Accept", "*/*")
                .header("X-CSRFToken", csrfToken)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "https://www.instagram.com/p/$shortcode/")
                .build()

            val graphqlResp = httpClient.newCall(graphqlRequest).execute()
            val graphqlBody = graphqlResp.body?.string() ?: return null

            if (graphqlBody.isBlank() || graphqlResp.code != 200) {
                return null
            }

            val json = JSONObject(graphqlBody)
            val media = json.optJSONObject("data")?.optJSONObject("xdt_shortcode_media")
                ?: json.optJSONObject("data")?.optJSONObject("shortcode_media")
                ?: return null

            // Extract image URL from media object
            extractImageUrl(media)
        } catch (e: Exception) {
            Log.d(TAG, "GraphQL thumbnail extraction failed: ${e.message}")
            null
        }
    }

    private fun tryEmbedPageThumbnail(shortcode: String): String? {
        return try {
            for (suffix in listOf("embed/", "embed/captioned/")) {
                val embedUrl = "https://www.instagram.com/p/$shortcode/$suffix"
                val request = addSessionCookie(Request.Builder())
                    .url(embedUrl)
                    .header("User-Agent", DESKTOP_UA)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .build()

                val html = httpClient.newCall(request).execute().body?.string() ?: continue

                // Try __additionalDataLoaded JSON
                val additionalDataPattern = Regex("""window\.__additionalDataLoaded\s*\(\s*[^,]+,\s*(\{.+?\})\s*\)""")
                val jsonMatch = additionalDataPattern.find(html)
                if (jsonMatch != null) {
                    try {
                        val json = JSONObject(jsonMatch.groupValues[1])

                        // Try items[0] first
                        val items = json.optJSONArray("items")
                        if (items != null && items.length() > 0) {
                            val imgUrl = extractImageUrl(items.getJSONObject(0))
                            if (imgUrl != null && !isInstagramAppIcon(imgUrl)) return imgUrl
                        }

                        // Try graphql.shortcode_media
                        val media = json.optJSONObject("graphql")?.optJSONObject("shortcode_media")
                            ?: json.optJSONObject("shortcode_media")
                        if (media != null) {
                            val imgUrl = extractImageUrl(media)
                            if (imgUrl != null && !isInstagramAppIcon(imgUrl)) return imgUrl
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Embed JSON parse error for thumbnail: ${e.message}")
                    }
                }

                // Regex fallback for og:image, but filter out app icons
                val imagePattern = Regex("""og:image"\s+content="([^"]+)""", RegexOption.IGNORE_CASE)
                val match = imagePattern.find(html)
                if (match != null) {
                    val url = match.groupValues[1]
                    if (url.startsWith("http") && !isInstagramAppIcon(url)) return url
                }
            }
            null
        } catch (e: Exception) {
            Log.d(TAG, "Embed page thumbnail extraction failed: ${e.message}")
            null
        }
    }

    // ──────────────────────────────────────────────
    // Utilities
    // ──────────────────────────────────────────────

    /**
     * Extract CSRF token from OkHttp's cookie jar
     */
    private fun extractCsrfFromCookies(): String {
        try {
            val url = okhttp3.HttpUrl.Builder()
                .scheme("https")
                .host("www.instagram.com")
                .build()
            val cookies = httpClient.cookieJar.loadForRequest(url)
            for (cookie in cookies) {
                if (cookie.name == "csrftoken") {
                    return cookie.value
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Cookie extraction error: ${e.message}")
        }
        return ""
    }

    private fun shortcodeToPk(shortcode: String): Long {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        var pk = 0L
        for (char in shortcode) {
            pk = pk * 64 + alphabet.indexOf(char)
        }
        return pk
    }

    private fun extractShortcode(url: String): String? {
        val pattern = Regex("/(?:p|reel|tv|reels)/([A-Za-z0-9_-]+)")
        return pattern.find(url)?.groupValues?.get(1)
    }

    private fun detectMediaType(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains("/reel/") || lower.contains("/reels/") -> "reel"
            lower.contains("/tv/") -> "tv"
            else -> "p"
        }
    }

    private fun looksLikeCdnUrl(url: String): Boolean {
        return url.contains("scontent") || url.contains("cdninstagram") || url.contains("fbcdn") ||
                url.contains(".mp4") || url.contains(".jpg") || url.contains(".png") || url.contains(".webp")
    }

    private fun looksLikeImageCdnUrl(url: String): Boolean {
        val lower = url.lowercase()
        val isCdn = lower.contains("scontent") || lower.contains("fbcdn") || lower.contains("cdninstagram")
        val isImage = lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png") || lower.contains(".webp")
        val isLikelyUiAsset = lower.contains("favicon") || lower.contains("sprite") || lower.contains("logo") || lower.contains("profile_pic")
        return isCdn && isImage && !isLikelyUiAsset
    }

    private fun String.unescapeJson(): String {
        return this
            .replace("\\u0026", "&")
            .replace("\\/", "/")
            .replace("\\\\", "\\")
    }
}
