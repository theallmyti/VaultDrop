package com.adityaprasad.vaultdrop.ui.home

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Link
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adityaprasad.vaultdrop.domain.model.DownloadItem
import com.adityaprasad.vaultdrop.domain.model.DownloadStatus
import com.adityaprasad.vaultdrop.domain.model.Platform
import com.adityaprasad.vaultdrop.data.repository.DownloadRepository
import com.adityaprasad.vaultdrop.service.DownloadService
import com.adityaprasad.vaultdrop.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.util.UUID

@Composable
fun HomeScreen(
    repository: DownloadRepository
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    var urlText by remember { mutableStateOf("") }
    var selectedQuality by remember { mutableStateOf("720p") }
    var detectedPlatform by remember { mutableStateOf<Platform?>(null) }
    var showSuccess by remember { mutableStateOf(false) }

    // Detect platform as user types
    detectedPlatform = if (urlText.isNotBlank()) {
        val platform = Platform.detect(urlText)
        if (platform != Platform.UNSUPPORTED) platform else null
    } else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header
        Text(
            text = "VaultDrop",
            fontFamily = DmSerifDisplay,
            fontWeight = FontWeight.Normal,
            fontSize = 28.sp,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Paste a link to download",
            fontFamily = DmSans,
            fontWeight = FontWeight.Light,
            fontSize = 13.sp,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(32.dp))

        // URL Input Field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(BgSurface)
                .border(1.dp, BorderSubtle, CardShape)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Link,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(20.dp)
            )

            BasicTextField(
                value = urlText,
                onValueChange = {
                    urlText = it
                    showSuccess = false
                },
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Light,
                    fontSize = 13.sp,
                    color = TextPrimary
                ),
                cursorBrush = SolidColor(AccentPrimary),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                decorationBox = { innerTextField ->
                    Box {
                        if (urlText.isEmpty()) {
                            Text(
                                text = "https://www.instagram.com/reel/...",
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Light,
                                fontSize = 13.sp,
                                color = TextTertiary
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // Paste button
            IconButton(
                onClick = {
                    val clip = clipboardManager.getText()
                    if (clip != null) {
                        urlText = clip.text
                        showSuccess = false
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentPaste,
                    contentDescription = "Paste",
                    tint = AccentPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Platform detection chip
        AnimatedVisibility(
            visible = detectedPlatform != null,
            enter = fadeIn(tween(200)) + slideInVertically(
                initialOffsetY = { -8 },
                animationSpec = tween(200, easing = FastOutSlowInEasing)
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(BgElevated)
                        .border(1.dp, BorderSubtle, PillShape)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = when (detectedPlatform) {
                            Platform.INSTAGRAM -> "📸  Instagram detected"
                            Platform.YOUTUBE -> "▶️  YouTube detected"
                            else -> ""
                        },
                        fontFamily = DmSans,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                }
            }
        }

        // Quality selector (YouTube only)
        AnimatedVisibility(
            visible = detectedPlatform == Platform.YOUTUBE,
            enter = fadeIn(tween(200))
        ) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "QUALITY",
                    fontFamily = DmSans,
                    fontWeight = FontWeight.Normal,
                    fontSize = 10.sp,
                    color = TextInactive,
                    letterSpacing = 1.2.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("1080p", "720p", "480p").forEach { quality ->
                        Box(
                            modifier = Modifier
                                .clip(PillShape)
                                .background(if (selectedQuality == quality) AccentPrimary else BgSurface)
                                .border(
                                    1.dp,
                                    if (selectedQuality == quality) AccentPrimary else BorderSubtle,
                                    PillShape
                                )
                                .clickable { selectedQuality = quality }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = quality,
                                fontFamily = DmSans,
                                fontWeight = FontWeight.Normal,
                                fontSize = 13.sp,
                                color = if (selectedQuality == quality) BgPrimary else TextSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Download button
        val isValidUrl = detectedPlatform != null
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(PillShape)
                .background(if (isValidUrl) AccentPrimary else AccentPrimary.copy(alpha = 0.3f))
                .then(
                    if (isValidUrl) Modifier.clickable {
                        val platform = Platform.detect(urlText)
                        val downloadId = UUID.randomUUID().toString()
                        val item = DownloadItem(
                            id = downloadId,
                            url = urlText.trim(),
                            title = "Fetching title...",
                            platform = platform,
                            status = DownloadStatus.QUEUED,
                            createdAt = System.currentTimeMillis()
                        )
                        coroutineScope.launch { repository.insertDownload(item) }
                        DownloadService.start(context, downloadId, urlText.trim(), selectedQuality)
                        Toast.makeText(context, "Added to queue ↓", Toast.LENGTH_SHORT).show()
                        showSuccess = true
                        urlText = ""
                    } else Modifier
                )
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (showSuccess) "✓ Added!" else "Download",
                fontFamily = DmSans,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = if (isValidUrl) BgPrimary else BgPrimary.copy(alpha = 0.5f)
            )
        }

        if (!isValidUrl && urlText.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Paste a valid Instagram or YouTube link",
                fontFamily = DmSans,
                fontWeight = FontWeight.Light,
                fontSize = 12.sp,
                color = StatusError,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Help text at bottom
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Supported links",
                fontFamily = DmSans,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SupportedChip("Instagram Reels")
                SupportedChip("Instagram Posts")
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SupportedChip("YouTube Videos")
                SupportedChip("YouTube Shorts")
            }
        }
    }
}

@Composable
private fun SupportedChip(text: String) {
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(BgSurface)
            .border(1.dp, BorderSubtle, PillShape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontFamily = DmSans,
            fontWeight = FontWeight.Light,
            fontSize = 11.sp,
            color = TextSecondary
        )
    }
}
