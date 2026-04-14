package com.adityaprasad.vaultdrop.ui.player

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.adityaprasad.vaultdrop.data.downloader.MediaStoreHelper
import com.adityaprasad.vaultdrop.data.repository.DownloadRepository
import com.adityaprasad.vaultdrop.domain.model.DownloadItem
import com.adityaprasad.vaultdrop.domain.model.DownloadStatus
import com.adityaprasad.vaultdrop.domain.model.Platform
import com.adityaprasad.vaultdrop.data.downloader.InstagramDownloader
import com.adityaprasad.vaultdrop.util.InstagramSessionManager
import com.adityaprasad.vaultdrop.ui.theme.AccentPrimary
import com.adityaprasad.vaultdrop.ui.theme.ControlOverlay
import com.adityaprasad.vaultdrop.ui.theme.DmSans
import com.adityaprasad.vaultdrop.ui.theme.PureBlack
import com.adityaprasad.vaultdrop.ui.theme.TextPrimary
import com.adityaprasad.vaultdrop.ui.theme.TextSecondary
import com.adityaprasad.vaultdrop.ui.theme.VaultDropTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class BookmarkPreviewActivity : ComponentActivity() {

    @Inject
    lateinit var instagramDownloader: InstagramDownloader

    @Inject
    lateinit var mediaStoreHelper: MediaStoreHelper

    @Inject
    lateinit var downloadRepository: DownloadRepository

    @Inject
    lateinit var httpClient: OkHttpClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val url = intent.getStringExtra(EXTRA_URL) ?: run {
            finish()
            return
        }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Preview"

        setContent {
            VaultDropTheme {
                BookmarkPreviewScreen(
                    originalUrl = url,
                    title = title,
                    instagramDownloader = instagramDownloader,
                    onSaveInApp = { resolvedUrl ->
                        savePreviewToApp(resolvedUrl, title, url)
                    },
                    onSaveToDevice = { resolvedUrl ->
                        savePreviewToDevice(resolvedUrl, url)
                    },
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
    }

    private fun savePreviewToDevice(mediaUrl: String, originalUrl: String) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val request = Request.Builder()
                    .url(mediaUrl)
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://www.instagram.com/")
                    .apply {
                        InstagramSessionManager.getCookieHeader(this@BookmarkPreviewActivity)
                            ?.takeIf { mediaUrl.contains("instagram", ignoreCase = true) || mediaUrl.contains("fbcdn", ignoreCase = true) || mediaUrl.contains("cdninstagram", ignoreCase = true) }
                            ?.let { header("Cookie", it) }
                    }
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Failed to download media")
                }

                val body = response.body ?: throw IllegalStateException("Empty media response")
                val extension = when {
                    mediaUrl.contains(".png", ignoreCase = true) -> ".png"
                    mediaUrl.contains(".webp", ignoreCase = true) -> ".webp"
                    mediaUrl.contains(".jpg", ignoreCase = true) || mediaUrl.contains(".jpeg", ignoreCase = true) -> ".jpg"
                    mediaUrl.contains(".mp4", ignoreCase = true) -> ".mp4"
                    else -> if (response.header("Content-Type").orEmpty().startsWith("image/")) ".jpg" else ".mp4"
                }

                val tempFile = File(cacheDir, "bookmark_preview_${System.currentTimeMillis()}$extension")
                body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val mimeType = mediaStoreHelper.getMimeType(tempFile)
                val saved = mediaStoreHelper.copyToMediaStore(tempFile, "Saved", mimeType)
                tempFile.delete()

                launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        this@BookmarkPreviewActivity,
                        if (saved != null) "Saved to Gallery" else "Failed to save",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }.onFailure { error ->
                launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        this@BookmarkPreviewActivity,
                        error.message ?: "Failed to save",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun savePreviewToApp(mediaUrl: String, title: String, originalUrl: String) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val request = Request.Builder()
                    .url(mediaUrl)
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://www.instagram.com/")
                    .apply {
                        InstagramSessionManager.getCookieHeader(this@BookmarkPreviewActivity)
                            ?.takeIf { mediaUrl.contains("instagram", ignoreCase = true) || mediaUrl.contains("fbcdn", ignoreCase = true) || mediaUrl.contains("cdninstagram", ignoreCase = true) }
                            ?.let { header("Cookie", it) }
                    }
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Failed to download media")
                }

                val body = response.body ?: throw IllegalStateException("Empty media response")
                val extension = when {
                    mediaUrl.contains(".png", ignoreCase = true) -> ".png"
                    mediaUrl.contains(".webp", ignoreCase = true) -> ".webp"
                    mediaUrl.contains(".jpg", ignoreCase = true) || mediaUrl.contains(".jpeg", ignoreCase = true) -> ".jpg"
                    mediaUrl.contains(".mp4", ignoreCase = true) -> ".mp4"
                    else -> if (response.header("Content-Type").orEmpty().startsWith("image/")) ".jpg" else ".mp4"
                }

                val saveDir = File(getExternalFilesDir(null), "Library").apply { mkdirs() }
                val fileName = "PREVIEW_${System.currentTimeMillis()}$extension"
                val outputFile = File(saveDir, fileName)
                body.byteStream().use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val mimeType = mediaStoreHelper.getMimeType(outputFile)
                val isImage = mimeType.startsWith("image/")
                val thumbnailPath = if (isImage) {
                    outputFile.absolutePath
                } else {
                    generatePreviewVideoThumbnail(outputFile)
                }
                val durationMs = if (isImage) 0L else getMediaDurationMs(outputFile)

                val platform = Platform.detect(originalUrl)
                val downloadItem = DownloadItem(
                    id = UUID.randomUUID().toString(),
                    url = originalUrl,
                    title = title,
                    platform = platform,
                    status = DownloadStatus.DONE,
                    filePath = outputFile.absolutePath,
                    thumbnailPath = thumbnailPath,
                    fileSize = outputFile.length(),
                    durationMs = durationMs,
                    createdAt = System.currentTimeMillis(),
                    completedAt = System.currentTimeMillis()
                )

                downloadRepository.insertDownload(downloadItem)

                launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        this@BookmarkPreviewActivity,
                        "Saved in app",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }.onFailure { error ->
                launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        this@BookmarkPreviewActivity,
                        error.message ?: "Failed to save",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun generatePreviewVideoThumbnail(videoFile: File): String? {
        return try {
            val thumbsDir = File(filesDir, "preview_thumbnails").apply { mkdirs() }
            val thumbFile = File(thumbsDir, "thumb_${videoFile.nameWithoutExtension}.jpg")

            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoFile.absolutePath)
            val bitmap = retriever.getFrameAtTime(
                1_000_000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            )
            retriever.release()

            if (bitmap != null) {
                thumbFile.outputStream().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)
                }
                bitmap.recycle()
                thumbFile.absolutePath
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun getMediaDurationMs(file: File): Long {
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

private enum class PreviewMode {
    LOADING,
    VIDEO,
    IMAGE,
    WEB,
    ERROR
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BookmarkPreviewScreen(
    originalUrl: String,
    title: String,
    instagramDownloader: InstagramDownloader,
    onSaveInApp: (String) -> Unit,
    onSaveToDevice: (String) -> Unit,
    onBack: () -> Unit
) {
    var mode by remember { mutableStateOf(PreviewMode.LOADING) }
    var resolvedUrl by remember { mutableStateOf(originalUrl) }
    var errorMessage by remember { mutableStateOf("Could not load preview") }
    var webLoading by remember { mutableStateOf(true) }
    var mediaBuffering by remember { mutableStateOf(true) }
    var zoomScale by remember { mutableStateOf(1f) }
    var zoomOffsetX by remember { mutableStateOf(0f) }
    var zoomOffsetY by remember { mutableStateOf(0f) }
    var showSaveDialog by remember { mutableStateOf(false) }

    LaunchedEffect(mode) {
        if (mode == PreviewMode.IMAGE) {
            mediaBuffering = false
        }
    }

    LaunchedEffect(originalUrl) {
        val isInstagram = originalUrl.contains("instagram.com", ignoreCase = true)
        if (!isInstagram) {
            mode = PreviewMode.WEB
            resolvedUrl = originalUrl
            return@LaunchedEffect
        }

        val directMediaUrl = runCatching {
            instagramDownloader.getDirectPreviewMediaUrl(originalUrl)
        }.getOrNull()

        if (!directMediaUrl.isNullOrBlank()) {
            resolvedUrl = directMediaUrl
            mode = if (looksLikeVideoUrl(directMediaUrl)) PreviewMode.VIDEO else PreviewMode.IMAGE
        } else {
            errorMessage = "Could not fetch media from this Instagram link"
            mode = PreviewMode.ERROR
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        when (mode) {
            PreviewMode.LOADING -> Unit
            PreviewMode.VIDEO -> {
                DirectVideoPreview(
                    mediaUrl = resolvedUrl,
                    originalUrl = originalUrl,
                    onBufferingChanged = { mediaBuffering = it },
                    onError = {
                        // Reels can fail without proper auth headers; web fallback is safer for playback.
                        resolvedUrl = originalUrl
                        mode = PreviewMode.WEB
                    }
                )
            }
            PreviewMode.IMAGE -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                zoomScale = (zoomScale * zoom).coerceIn(1f, 5f)
                                if (zoomScale > 1f) {
                                    zoomOffsetX += pan.x
                                    zoomOffsetY += pan.y
                                } else {
                                    zoomOffsetX = 0f
                                    zoomOffsetY = 0f
                                }
                            }
                        }
                ) {
                    AsyncImage(
                        model = resolvedUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = zoomScale,
                                scaleY = zoomScale,
                                translationX = zoomOffsetX,
                                translationY = zoomOffsetY
                            )
                    )
                }
            }
            PreviewMode.WEB -> {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.userAgentString =
                                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    webLoading = newProgress < 100
                                }
                            }
                            webViewClient = WebViewClient()
                            loadUrl(resolvedUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { webView ->
                        if (webView.url != resolvedUrl) {
                            webView.loadUrl(resolvedUrl)
                        }
                    }
                )
            }
            PreviewMode.ERROR -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Preview unavailable",
                        fontFamily = DmSans,
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMessage,
                        fontFamily = DmSans,
                        fontWeight = FontWeight.Light,
                        fontSize = 13.sp,
                        color = com.adityaprasad.vaultdrop.ui.theme.TextSecondary
                    )
                }
            }
        }

        val showLoading = mode == PreviewMode.LOADING || (mode == PreviewMode.WEB && webLoading) || (mode == PreviewMode.VIDEO && mediaBuffering)
        if (showLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = AccentPrimary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(ControlOverlay)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontFamily = DmSans,
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(ControlOverlay)
                .size(54.dp)
        ) {
            IconButton(
                onClick = { showSaveDialog = true },
                modifier = Modifier.align(Alignment.Center)
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "Download",
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                title = {
                    Text(
                        text = "Save where?",
                        fontFamily = DmSans,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                },
                text = {
                    Text(
                        text = "Choose whether to keep it inside the app or save it to your device Gallery.",
                        fontFamily = DmSans,
                        fontWeight = FontWeight.Light,
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showSaveDialog = false
                        onSaveInApp(resolvedUrl)
                    }) {
                        Text("In app")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showSaveDialog = false
                        onSaveToDevice(resolvedUrl)
                    }) {
                        Text("Device")
                    }
                }
            )
        }
    }
}

@Composable
private fun DirectVideoPreview(
    mediaUrl: String,
    originalUrl: String,
    onBufferingChanged: (Boolean) -> Unit,
    onError: () -> Unit
) {
    val context = LocalContext.current
    val cookieHeader = InstagramSessionManager.getCookieHeader(context)
    val requestHeaders = remember(mediaUrl, cookieHeader) {
        buildMap<String, String> {
            put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            put("Referer", "https://www.instagram.com/")
            if (!cookieHeader.isNullOrBlank()) {
                if (mediaUrl.contains("instagram", ignoreCase = true) || mediaUrl.contains("fbcdn", ignoreCase = true) || mediaUrl.contains("cdninstagram", ignoreCase = true)) {
                    put("Cookie", cookieHeader)
                }
            }
        }
    }

    val exoPlayer = remember(mediaUrl, requestHeaders) {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(requestHeaders["User-Agent"] ?: "Mozilla/5.0")
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(requestHeaders)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(mediaUrl)))
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ALL
        }
    }

    var hasPlayableState by remember(mediaUrl) { mutableStateOf(false) }
    var fallbackTriggered by remember(mediaUrl) { mutableStateOf(false) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                onBufferingChanged(state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) {
                    hasPlayableState = true
                    onBufferingChanged(false)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    hasPlayableState = true
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                fallbackTriggered = true
                onError()
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(mediaUrl) {
        delay(6000)
        if (!hasPlayableState && !fallbackTriggered) {
            fallbackTriggered = true
            onError()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun looksLikeVideoUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains(".mp4") || lower.contains("video") || lower.contains("mime_type=video")
}
