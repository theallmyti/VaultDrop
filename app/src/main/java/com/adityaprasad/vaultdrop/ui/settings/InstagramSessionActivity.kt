package com.adityaprasad.vaultdrop.ui.settings

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.adityaprasad.vaultdrop.ui.theme.AccentPrimary
import com.adityaprasad.vaultdrop.ui.theme.ControlOverlay
import com.adityaprasad.vaultdrop.ui.theme.DmSans
import com.adityaprasad.vaultdrop.ui.theme.PureBlack
import com.adityaprasad.vaultdrop.ui.theme.TextPrimary
import com.adityaprasad.vaultdrop.ui.theme.VaultDropTheme
import com.adityaprasad.vaultdrop.util.InstagramSessionManager

class InstagramSessionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VaultDropTheme {
                InstagramSessionScreen(
                    onBack = { finish() },
                    onSessionSaved = {
                        Toast.makeText(this, "Instagram session saved", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun InstagramSessionScreen(
    onBack: () -> Unit,
    onSessionSaved: () -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    var sessionSaved by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var blankRecoveryAttempted by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        AndroidView(
            factory = { context ->
                // Configure CookieManager BEFORE creating WebView
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)

                WebView(context).apply {
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.loadsImagesAutomatically = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            loading = newProgress < 100
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            return false
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            loading = true
                            loadError = null
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            loading = false

                            val pageLooksBlank = (view?.contentHeight ?: 0) <= 1
                            if (pageLooksBlank && !blankRecoveryAttempted) {
                                blankRecoveryAttempted = true
                                loading = true
                                view?.postDelayed({
                                    view.loadUrl("https://www.instagram.com/accounts/login/?force_classic_login=1")
                                }, 250)
                                return
                            }
                            
                            // Try to extract session from multiple possible cookie sources
                            val cookieRaw = CookieManager.getInstance().getCookie("https://www.instagram.com/")
                            val cookieWww = CookieManager.getInstance().getCookie("https://www.instagram.com")
                            val combinedCookies = listOfNotNull(cookieRaw, cookieWww).joinToString("; ")
                            
                            val hasSession = combinedCookies.contains("sessionid=")
                            
                            if (hasSession && !sessionSaved) {
                                sessionSaved = true
                                // Save the most complete cookie string available
                                val cookieToSave = if (cookieRaw?.isNotEmpty() == true) cookieRaw else combinedCookies
                                InstagramSessionManager.saveCookieHeader(context, cookieToSave)
                                onSessionSaved()
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                loading = false
                                loadError = error?.description?.toString() ?: "Failed to load Instagram"
                            }
                        }
                    }

                    loadUrl("https://www.instagram.com/accounts/login/")
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = AccentPrimary
            )
        }

        if (!loading && loadError != null) {
            Text(
                text = "Could not load Instagram login. Check network and try again.",
                fontFamily = DmSans,
                fontWeight = FontWeight.Light,
                fontSize = 12.sp,
                color = TextPrimary.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 20.dp)
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
                    text = "Instagram Session",
                    fontFamily = DmSans,
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Login once. Session will be saved automatically.",
                fontFamily = DmSans,
                fontWeight = FontWeight.Light,
                fontSize = 12.sp,
                color = TextPrimary.copy(alpha = 0.8f),
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}
