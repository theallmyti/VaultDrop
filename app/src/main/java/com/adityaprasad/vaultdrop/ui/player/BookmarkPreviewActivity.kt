package com.adityaprasad.vaultdrop.ui.player

import android.annotation.SuppressLint
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.adityaprasad.vaultdrop.data.downloader.InstagramDownloader
import com.adityaprasad.vaultdrop.ui.theme.AccentPrimary
import com.adityaprasad.vaultdrop.ui.theme.ControlOverlay
import com.adityaprasad.vaultdrop.ui.theme.DmSans
import com.adityaprasad.vaultdrop.ui.theme.PureBlack
import com.adityaprasad.vaultdrop.ui.theme.TextPrimary
import com.adityaprasad.vaultdrop.ui.theme.VaultDropTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BookmarkPreviewActivity : ComponentActivity() {

    @Inject
    lateinit var instagramDownloader: InstagramDownloader

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
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
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
    onBack: () -> Unit
) {
    var mode by remember { mutableStateOf(PreviewMode.LOADING) }
    var resolvedUrl by remember { mutableStateOf(originalUrl) }
    var errorMessage by remember { mutableStateOf("Could not load preview") }
    var webLoading by remember { mutableStateOf(true) }
    var mediaBuffering by remember { mutableStateOf(true) }

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
                    onBufferingChanged = { mediaBuffering = it },
                    onError = {
                        errorMessage = "Video preview failed for this link"
                        mode = PreviewMode.ERROR
                    }
                )
            }
            PreviewMode.IMAGE -> {
                AsyncImage(
                    model = resolvedUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
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
    }
}

@Composable
private fun DirectVideoPreview(
    mediaUrl: String,
    onBufferingChanged: (Boolean) -> Unit,
    onError: () -> Unit
) {
    val context = LocalContext.current

    val exoPlayer = remember(mediaUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(mediaUrl)))
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ALL
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                onBufferingChanged(state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) {
                    onBufferingChanged(false)
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                onError()
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
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
