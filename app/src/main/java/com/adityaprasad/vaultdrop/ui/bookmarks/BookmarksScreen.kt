package com.adityaprasad.vaultdrop.ui.bookmarks

import android.content.Intent
import android.content.ClipboardManager
import android.content.ClipData
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adityaprasad.vaultdrop.domain.model.BookmarkItem
import com.adityaprasad.vaultdrop.domain.model.Platform
import com.adityaprasad.vaultdrop.ui.components.EmptyState
import com.adityaprasad.vaultdrop.ui.components.ShimmerPlaceholder
import com.adityaprasad.vaultdrop.ui.theme.*
import com.adityaprasad.vaultdrop.ui.player.BookmarkPreviewActivity
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookmarksScreen(
    viewModel: BookmarksViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val currentFilter by viewModel.filter.collectAsStateWithLifecycle()
    val usernames by viewModel.usernames.collectAsStateWithLifecycle()
    val selectedUsername by viewModel.selectedUsername.collectAsStateWithLifecycle()
    val bookmarkCount by viewModel.bookmarkCount.collectAsStateWithLifecycle()
    var showInitialSkeleton by rememberSaveable { mutableStateOf(true) }
    val clipboard = context.getSystemService(ClipboardManager::class.java)

    LaunchedEffect(Unit) {
        delay(260)
        showInitialSkeleton = false
    }

    // Filter by username if selected
    val displayedBookmarks = if (selectedUsername != null) {
        bookmarks.filter { it.username == selectedUsername }
    } else {
        bookmarks
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bookmarks",
                    fontFamily = DmSerifDisplay,
                    fontWeight = FontWeight.Normal,
                    fontSize = 28.sp,
                    color = TextPrimary
                )
                if (bookmarkCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(PillShape)
                            .background(BgElevated)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "$bookmarkCount saved",
                            fontFamily = DmSans,
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search bar
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
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(18.dp)
                )

                BasicTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = DmSans,
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp,
                        color = TextPrimary
                    ),
                    cursorBrush = SolidColor(AccentPrimary),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    decorationBox = { innerTextField ->
                        Box {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search by comment or username...",
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

                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.setSearchQuery("") },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Clear",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Username filter chips (horizontal scroll)
            if (usernames.isNotEmpty() && searchQuery.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        text = "All",
                        isActive = selectedUsername == null,
                        onClick = { viewModel.selectUsername(null) }
                    )
                    usernames.forEach { username ->
                        FilterChip(
                            text = username,
                            isActive = selectedUsername == username,
                            onClick = {
                                viewModel.selectUsername(
                                    if (selectedUsername == username) null else username
                                )
                            }
                        )
                    }
                }
            }
        }

        if (showInitialSkeleton && displayedBookmarks.isEmpty() && searchQuery.isEmpty()) {
            BookmarksSkeletonList()
        } else if (displayedBookmarks.isEmpty()) {
            if (searchQuery.isNotEmpty()) {
                // No search results
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "🔍",
                            fontSize = 40.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No results for \"$searchQuery\"",
                            fontFamily = DmSans,
                            fontWeight = FontWeight.Normal,
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = 100.dp)
                    ) {
                        Text(
                            text = "🔖",
                            fontSize = 48.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No saved links yet",
                            fontFamily = DmSans,
                            fontWeight = FontWeight.Normal,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Share a link and tap \"Save Link\"",
                            fontFamily = DmSans,
                            fontWeight = FontWeight.Light,
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp,
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = displayedBookmarks,
                    key = { it.id }
                ) { item ->
                    BookmarkGridItem(
                        item = item,
                        onOpen = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                            context.startActivity(intent)
                        },
                        onPreview = {
                            val intent = Intent(context, BookmarkPreviewActivity::class.java).apply {
                                putExtra(BookmarkPreviewActivity.EXTRA_URL, item.url)
                                putExtra(BookmarkPreviewActivity.EXTRA_TITLE, item.username.ifBlank { item.sourceDomain })
                            }
                            context.startActivity(intent)
                        },
                        onCopyLink = {
                            clipboard?.setPrimaryClip(ClipData.newPlainText("bookmark_link", item.url))
                        },
                        onRefreshThumbnail = { viewModel.refreshThumbnail(item) },
                        onDelete = { viewModel.deleteBookmark(item.id) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }
}

@Composable
private fun BookmarksSkeletonList() {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
        modifier = Modifier.fillMaxSize()
    ) {
        repeat(6) {
            item {
                ShimmerPlaceholder(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 16f)
                        .clip(ChipShape)
                )
            }
        }

        item { Spacer(modifier = Modifier.height(100.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkGridItem(
    item: BookmarkItem,
    onOpen: () -> Unit,
    onPreview: () -> Unit,
    onCopyLink: () -> Unit,
    onRefreshThumbnail: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var thumbnailLoadFailed by remember(item.id, item.thumbnailUrl) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .clip(ChipShape)
            .combinedClickable(
                onClick = onOpen,
                onLongClick = { showMenu = true }
            )
    ) {
        if (!item.thumbnailUrl.isNullOrEmpty() && !thumbnailLoadFailed) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.thumbnailUrl)
                    .size(coil.size.Size(800, 800))
                    .crossfade(200)
                    .build(),
                contentDescription = "Bookmark thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .then(if (showMenu) Modifier.blur(6.dp) else Modifier),
                onError = { thumbnailLoadFailed = true }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .background(BgSurface)
                    .then(if (showMenu) Modifier.blur(6.dp) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No Preview",
                    fontFamily = DmSans,
                    fontWeight = FontWeight.Light,
                    fontSize = 11.sp,
                    color = TextSecondary,
                )
            }
        }

        // Platform badge (top-left)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .clip(PillShape)
                .background(PureBlack.copy(alpha = 0.75f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = when (item.platform) {
                    Platform.INSTAGRAM -> "IG"
                    Platform.YOUTUBE -> "YT"
                    Platform.UNSUPPORTED -> "LINK"
                },
                fontFamily = DmSans,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                color = TextPrimary
            )
        }

        // Date badge (top-right)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .clip(PillShape)
                .background(PureBlack.copy(alpha = 0.65f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = item.formattedDate,
                fontFamily = DmSans,
                fontWeight = FontWeight.Light,
                fontSize = 9.sp,
                color = TextPrimary
            )
        }

        // Bottom caption badge: username first, fallback to domain
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .clip(PillShape)
                .background(PureBlack.copy(alpha = 0.75f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = when {
                    item.comment.isNotBlank() -> "${item.comment.take(28)}"
                    item.username.isNotBlank() -> item.username
                    else -> item.sourceDomain
                },
                fontFamily = DmSans,
                fontWeight = FontWeight.Normal,
                fontSize = 10.sp,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .clip(PillShape)
                .background(PureBlack.copy(alpha = 0.75f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = "Open",
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }

        if (showMenu) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(PureBlack.copy(alpha = 0.30f))
                    .clickable { showMenu = false }
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp, start = 10.dp, end = 10.dp)
                    .clip(CardShape)
                    .background(BgElevated.copy(alpha = 0.95f))
                    .border(1.dp, BorderSubtle, CardShape)
                    .padding(vertical = 6.dp)
            ) {
                ContextMenuAction(text = "Preview in app", onClick = {
                    showMenu = false
                    onPreview()
                })
                ContextMenuAction(text = "Open in browser", onClick = {
                    showMenu = false
                    onOpen()
                })
                ContextMenuAction(text = "Copy link", onClick = {
                    showMenu = false
                    onCopyLink()
                }, leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                })
                ContextMenuAction(text = "Refresh preview", onClick = {
                    showMenu = false
                    onRefreshThumbnail()
                })
                ContextMenuAction(text = "Delete", onClick = {
                    showMenu = false
                    onDelete()
                }, color = StatusError)
            }
        }
    }
}

@Composable
private fun ContextMenuAction(
    text: String,
    onClick: () -> Unit,
    color: Color = TextPrimary,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (leadingIcon != null) {
            leadingIcon()
        }
        Text(
            text = text,
            fontFamily = DmSans,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            color = color
        )
    }
}

@Composable
private fun FilterChip(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(if (isActive) AccentPrimary else BgSurface)
            .border(
                1.dp,
                if (isActive) AccentPrimary else BorderSubtle,
                PillShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontFamily = DmSans,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            color = if (isActive) BgPrimary else TextSecondary
        )
    }
}
