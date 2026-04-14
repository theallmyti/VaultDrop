package com.adityaprasad.vaultdrop.ui.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adityaprasad.vaultdrop.data.repository.BookmarkRepository
import com.adityaprasad.vaultdrop.data.repository.DownloadRepository
import com.adityaprasad.vaultdrop.data.api.ConvexApiService
import com.adityaprasad.vaultdrop.data.api.SyncBookmarkItem
import com.adityaprasad.vaultdrop.data.api.SyncBookmarksRequest
import com.adityaprasad.vaultdrop.domain.model.BookmarkItem
import com.adityaprasad.vaultdrop.domain.model.DownloadItem
import com.adityaprasad.vaultdrop.domain.model.DownloadStatus
import com.adityaprasad.vaultdrop.domain.model.Platform
import com.adityaprasad.vaultdrop.service.DownloadService
import com.adityaprasad.vaultdrop.ui.theme.*
import com.adityaprasad.vaultdrop.data.downloader.YouTubeDownloader
import com.adityaprasad.vaultdrop.data.downloader.VideoFormat
import com.adityaprasad.vaultdrop.util.InstagramUrlUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    @Inject
    lateinit var repository: DownloadRepository

    @Inject
    lateinit var bookmarkRepository: BookmarkRepository
    
    @Inject
    lateinit var instagramDownloader: com.adityaprasad.vaultdrop.data.downloader.InstagramDownloader

    @Inject
    lateinit var youtubeDownloader: YouTubeDownloader

    @Inject
    lateinit var convexApi: ConvexApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedUrl = extractUrl(intent)
        val normalizedSharedUrl = sharedUrl?.let { InstagramUrlUtils.normalize(it) }
        val prefs = getSharedPreferences("vaultdrop_prefs", Context.MODE_PRIVATE)
        val availableTags = (prefs.getString("bookmark_tags", "")
            .orEmpty()
            .split("|")
            .map { it.trim() }
            .filter { it.isNotEmpty() } + listOf("IG", "YT"))
            .distinctBy { it.lowercase() }

        setContent {
            VaultDropTheme {
                ShareReceiverSheet(
                    url = normalizedSharedUrl,
                    youtubeDownloader = youtubeDownloader,
                    availableTags = availableTags,
                    onDownload = { url, quality ->
                        startDownload(url, quality)
                    },
                    onSaveLink = { url, comment ->
                        saveBookmark(url, comment)
                    },
                    onDismiss = { finish() },
                    showErrorToast = { msg ->
                        Toast.makeText(this@ShareReceiverActivity, msg, Toast.LENGTH_LONG).show()
                        finish()
                    }
                )
            }
        }
    }

    private fun extractUrl(intent: Intent?): String? {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
            val urlPattern = Regex("https?://\\S+")
            return urlPattern.find(text)?.value
        }
        return null
    }

    private fun startDownload(url: String, quality: String) {
        val normalizedUrl = InstagramUrlUtils.normalize(url)
        val platform = Platform.detect(normalizedUrl)
        val username = bookmarkRepository.extractUsername(normalizedUrl)
        val downloadId = UUID.randomUUID().toString()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            var finalUsername = username
            if (finalUsername.isEmpty()) {
                try {
                    val (_, fetchedUsername) = instagramDownloader.getMetadata(normalizedUrl)
                    if (fetchedUsername != null) finalUsername = fetchedUsername
                } catch (e: Exception) {
                    // fall back gracefully
                }
            }
            
            val item = DownloadItem(
                id = downloadId,
                url = normalizedUrl,
                username = finalUsername,
                title = "Fetching title...",
                platform = platform,
                status = DownloadStatus.QUEUED,
                createdAt = System.currentTimeMillis()
            )
            repository.insertDownload(item)
            DownloadService.start(this@ShareReceiverActivity, downloadId, normalizedUrl, quality)
        }

        Toast.makeText(this, "Added to queue ↓", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun saveBookmark(url: String, comment: String) {
        val normalizedUrl = InstagramUrlUtils.normalize(url)
        val platform = Platform.detect(normalizedUrl)
        val username = bookmarkRepository.extractUsername(normalizedUrl)
        val parsedTags = extractTagsFromComment(comment)
            .map { normalizeTag(it) }
            .filter { it.isNotBlank() }
            .distinct()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val initialThumbnail = when (platform) {
                Platform.YOUTUBE -> buildYouTubeThumbnailUrl(normalizedUrl)
                else -> null
            } ?: buildFaviconUrl(normalizedUrl)

            val localBookmark = BookmarkItem(
                id = bookmarkRepository.getBookmarkByUrl(normalizedUrl)?.id ?: UUID.randomUUID().toString(),
                url = normalizedUrl,
                username = username,
                comment = comment.trim(),
                platform = platform,
                thumbnailUrl = initialThumbnail,
                createdAt = System.currentTimeMillis(),
                tags = parsedTags
            )

            val saveResult = runCatching { bookmarkRepository.insertBookmark(localBookmark) }
            if (saveResult.isFailure) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ShareReceiverActivity,
                        saveResult.exceptionOrNull()?.message ?: "Failed to save link",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(this@ShareReceiverActivity, "Link saved 🔖", Toast.LENGTH_SHORT).show()
                finish()
            }

            var enrichedUsername = localBookmark.username
            var enrichedThumbnail = localBookmark.thumbnailUrl

            runCatching {
                if (enrichedUsername.isBlank() || enrichedUsername.equals("@instagram", ignoreCase = true)) {
                    val resolvedUsername = instagramDownloader.resolveUsername(normalizedUrl)
                    if (!resolvedUsername.isNullOrBlank()) {
                        enrichedUsername = resolvedUsername
                    }
                }

                when (platform) {
                    Platform.INSTAGRAM -> {
                        val (_, fetchedUsername) = instagramDownloader.getMetadata(normalizedUrl)
                        if ((enrichedUsername.isBlank() || enrichedUsername.equals("@instagram", ignoreCase = true)) && !fetchedUsername.isNullOrEmpty()) {
                            enrichedUsername = normalizeUsername(fetchedUsername)
                        }
                        val fetchedThumb = instagramDownloader.getThumbnailUrl(normalizedUrl)
                        if (!fetchedThumb.isNullOrBlank()) {
                            enrichedThumbnail = fetchedThumb
                        }
                    }
                    Platform.YOUTUBE -> {
                        val fetchedThumb = buildYouTubeThumbnailUrl(normalizedUrl)
                        if (!fetchedThumb.isNullOrBlank()) {
                            enrichedThumbnail = fetchedThumb
                        }
                    }
                    Platform.UNSUPPORTED -> Unit
                }
            }

            if (enrichedThumbnail.isNullOrBlank()) {
                enrichedThumbnail = buildFaviconUrl(normalizedUrl)
            }

            if (enrichedThumbnail != localBookmark.thumbnailUrl && !enrichedThumbnail.isNullOrBlank()) {
                runCatching { bookmarkRepository.updateThumbnailUrl(localBookmark.id, enrichedThumbnail) }
            }
            if (enrichedUsername != localBookmark.username) {
                runCatching { bookmarkRepository.updateUsername(localBookmark.id, enrichedUsername) }
            }

            val token = getSharedPreferences("vaultdrop_prefs", Context.MODE_PRIVATE)
                .getString("auth_token", null)
                .orEmpty()
                .trim()

            if (token.isNotBlank()) {
                runCatching {
                    convexApi.syncBookmarks(
                        SyncBookmarksRequest(
                            token = token,
                            bookmarks = listOf(
                                SyncBookmarkItem(
                                    bookmarkId = localBookmark.id,
                                    url = localBookmark.url,
                                    username = enrichedUsername,
                                    comment = localBookmark.comment,
                                    platform = localBookmark.platform.name,
                                    thumbnailUrl = enrichedThumbnail,
                                    createdAt = localBookmark.createdAt,
                                    tags = localBookmark.tags,
                                ),
                            ),
                        ),
                    )
                }
            }
        }
    }

    private fun buildYouTubeThumbnailUrl(url: String): String? {
        val videoId = extractYouTubeVideoId(url) ?: return null
        return "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
    }

    private fun extractYouTubeVideoId(url: String): String? {
        return runCatching {
            val uri = Uri.parse(url)
            val host = uri.host.orEmpty().lowercase()
            when {
                host.contains("youtu.be") -> uri.lastPathSegment
                host.contains("youtube.com") -> {
                    if (uri.path.orEmpty().startsWith("/shorts/")) {
                        uri.pathSegments.getOrNull(1)
                    } else {
                        uri.getQueryParameter("v")
                    }
                }
                else -> null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun buildFaviconUrl(url: String): String? {
        val host = runCatching { Uri.parse(url).host }.getOrNull() ?: return null
        return "https://www.google.com/s2/favicons?sz=128&domain=$host"
    }

    private fun normalizeUsername(raw: String): String {
        val cleaned = raw.trim().removePrefix("@")
        return if (cleaned.isBlank()) "" else "@$cleaned"
    }

    private fun extractTagsFromComment(comment: String): List<String> {
        return Regex("#([A-Za-z0-9_]+)")
            .findAll(comment)
            .map { it.groupValues[1] }
            .toList()
    }

    private fun normalizeTag(raw: String): String {
        return raw.trim().lowercase().removePrefix("#").replace(" ", "_")
    }
}

private enum class ShareMode { CHOOSE, LOADING_FORMATS, DOWNLOAD, SAVE_LINK }

@Composable
fun ShareReceiverSheet(
    url: String?,
    youtubeDownloader: YouTubeDownloader,
    availableTags: List<String>,
    onDownload: (String, String) -> Unit,
    onSaveLink: (String, String) -> Unit,
    onDismiss: () -> Unit,
    showErrorToast: (String) -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(ShareMode.CHOOSE) }
    var comment by remember { mutableStateOf("") }
    val defaultPlatformTags = remember(url) {
        val detected = url?.let { Platform.detect(it) } ?: Platform.UNSUPPORTED
        when (detected) {
            Platform.INSTAGRAM -> setOf("IG")
            Platform.YOUTUBE -> setOf("YT")
            Platform.UNSUPPORTED -> emptySet()
        }
    }
    var selectedTags by remember(defaultPlatformTags) { mutableStateOf(defaultPlatformTags) }
    var saveModeInitialized by remember { mutableStateOf(false) }
    
    var videoFormats by remember { mutableStateOf<List<VideoFormat>>(emptyList()) }
    var selectedFormatId by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        delay(50)
        visible = true
    }

    val platform = url?.let { Platform.detect(it) } ?: Platform.UNSUPPORTED
    val allQuickTags = remember(availableTags, defaultPlatformTags) {
        (availableTags + defaultPlatformTags.toList()).distinctBy { it.lowercase() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(300)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(BottomSheetShape)
                    .background(BgSurface)
                    .clickable(enabled = false, onClick = {})
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(PillShape)
                        .background(BadgeBg)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // App name
                Text(
                    text = "VaultDrop",
                    fontFamily = DmSerifDisplay,
                    fontWeight = FontWeight.Normal,
                    fontSize = 22.sp,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (url == null || platform == Platform.UNSUPPORTED) {
                    Text(
                        text = "This link isn't supported yet",
                        fontFamily = DmSans,
                        fontWeight = FontWeight.Light,
                        fontSize = 14.sp,
                        color = StatusError
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                } else {
                    // Detected URL
                    Text(
                        text = url,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Light,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Platform chip
                    Box(
                        modifier = Modifier
                            .clip(PillShape)
                            .background(BgElevated)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = when (platform) {
                                Platform.INSTAGRAM -> "Instagram"
                                Platform.YOUTUBE -> "YouTube"
                                else -> ""
                            },
                            fontFamily = DmSans,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    when (mode) {
                        ShareMode.CHOOSE -> {
                            // Two action cards side by side
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Download option
                                ActionCard(
                                    icon = Icons.Outlined.Download,
                                    title = "Download",
                                    subtitle = "Save to device",
                                    modifier = Modifier.weight(1f),
                                    onClick = { 
                                        if (platform == Platform.YOUTUBE) {
                                            mode = ShareMode.LOADING_FORMATS
                                            coroutineScope.launch {
                                                try {
                                                    val formats = youtubeDownloader.fetchAvailableFormats(url)
                                                    if (formats.isEmpty()) {
                                                        showErrorToast("No downloadable formats found for this video.")
                                                    } else {
                                                        videoFormats = formats
                                                        selectedFormatId = formats.first().formatId
                                                        mode = ShareMode.DOWNLOAD
                                                    }
                                                } catch (e: Exception) {
                                                    showErrorToast(e.message ?: "Failed to fetch formats.")
                                                }
                                            }
                                        } else {
                                            onDownload(url, "720p")
                                        }
                                    }
                                )

                                // Save Link option
                                ActionCard(
                                    icon = Icons.Outlined.Bookmark,
                                    title = "Save Link",
                                    subtitle = "Bookmark it",
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        mode = ShareMode.SAVE_LINK
                                        if (!saveModeInitialized) {
                                            selectedTags = defaultPlatformTags
                                            comment = mergeCommentAndTags(comment, selectedTags)
                                            saveModeInitialized = true
                                        }
                                    }
                                )
                            }
                        }

                        ShareMode.LOADING_FORMATS -> {
                            Spacer(modifier = Modifier.height(20.dp))
                            CircularProgressIndicator(color = AccentPrimary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Fetching video details...",
                                fontFamily = DmSans,
                                fontWeight = FontWeight.Normal,
                                fontSize = 14.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                        }

                        ShareMode.DOWNLOAD -> {
                            if (platform == Platform.YOUTUBE && videoFormats.isNotEmpty()) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(videoFormats) { format ->
                                        QualityPill(
                                            text = format.label,
                                            isSelected = selectedFormatId == format.formatId,
                                            onClick = { selectedFormatId = format.formatId }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                            }

                            // Download button
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(PillShape)
                                    .background(AccentPrimary)
                                    .clickable { onDownload(url, selectedFormatId) }
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Add to Queue",
                                    fontFamily = DmSans,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 16.sp,
                                    color = BgPrimary
                                )
                            }
                        }

                        ShareMode.SAVE_LINK -> {
                            // Comment input
                            Text(
                                text = "Add a note (optional)",
                                fontFamily = DmSans,
                                fontWeight = FontWeight.Normal,
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            if (allQuickTags.isNotEmpty()) {
                                Text(
                                    text = "Quick tags",
                                    fontFamily = DmSans,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(allQuickTags) { tag ->
                                        val isSelected = selectedTags.contains(tag)
                                        Box(
                                            modifier = Modifier
                                                .clip(PillShape)
                                                .background(if (isSelected) AccentPrimary else BgElevated)
                                                .clickable {
                                                    selectedTags = if (isSelected) {
                                                        comment = removeTagFromComment(comment, tag)
                                                        selectedTags - tag
                                                    } else {
                                                        comment = appendTagToComment(comment, tag)
                                                        selectedTags + tag
                                                    }
                                                }
                                                .padding(horizontal = 12.dp, vertical = 7.dp)
                                        ) {
                                            Text(
                                                text = "#$tag",
                                                fontFamily = DmSans,
                                                fontWeight = FontWeight.Normal,
                                                fontSize = 12.sp,
                                                color = if (isSelected) BgPrimary else TextSecondary
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(CardShape)
                                    .background(BgElevated)
                                    .padding(4.dp)
                            ) {
                                BasicTextField(
                                    value = comment,
                                    onValueChange = { comment = it },
                                    textStyle = TextStyle(
                                        fontFamily = DmSans,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 14.sp,
                                        color = TextPrimary
                                    ),
                                    cursorBrush = SolidColor(AccentPrimary),
                                    maxLines = 4,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    decorationBox = { innerTextField ->
                                        Box {
                                            if (comment.isEmpty()) {
                                                Text(
                                                    text = "e.g. \"Cool outfit inspo\" or \"Recipe to try\"",
                                                    fontFamily = DmSans,
                                                    fontWeight = FontWeight.Light,
                                                    fontSize = 14.sp,
                                                    color = TextTertiary
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Save button
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(PillShape)
                                    .background(AccentPrimary)
                                    .clickable { onSaveLink(url, mergeCommentAndTags(comment, selectedTags)) }
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Save Link",
                                    fontFamily = DmSans,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 16.sp,
                                    color = BgPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(CardShape)
            .background(BgElevated)
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp, horizontal = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = AccentPrimary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            fontFamily = DmSans,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            fontFamily = DmSans,
            fontWeight = FontWeight.Light,
            fontSize = 11.sp,
            color = TextSecondary
        )
    }
}

@Composable
private fun QualityPill(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(if (isSelected) AccentPrimary else BgElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            fontFamily = DmSans,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            color = if (isSelected) BgPrimary else TextSecondary
        )
    }
}

private fun appendTagToComment(comment: String, tag: String): String {
    val token = "#$tag"
    if (comment.contains(token)) return comment
    return if (comment.isBlank()) token else "$comment $token"
}

private fun removeTagFromComment(comment: String, tag: String): String {
    val token = "#$tag"
    return comment
        .replace(token, "")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun mergeCommentAndTags(comment: String, tags: Set<String>): String {
    var result = comment.trim()
    tags.forEach { tag ->
        val token = "#$tag"
        if (!result.contains(token)) {
            result = if (result.isBlank()) token else "$result $token"
        }
    }
    return result
}
