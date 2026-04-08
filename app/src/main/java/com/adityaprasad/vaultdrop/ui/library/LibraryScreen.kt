package com.adityaprasad.vaultdrop.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import androidx.compose.ui.platform.LocalContext
import com.adityaprasad.vaultdrop.domain.model.DownloadItem
import com.adityaprasad.vaultdrop.domain.model.Platform
import com.adityaprasad.vaultdrop.ui.components.EmptyState
import com.adityaprasad.vaultdrop.ui.components.ShimmerPlaceholder
import com.adityaprasad.vaultdrop.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onVideoClick: (DownloadItem) -> Unit,
    onShareClick: (DownloadItem) -> Unit,
    onDeleteClick: (DownloadItem) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val currentFilter by viewModel.filter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val usernames by viewModel.usernames.collectAsStateWithLifecycle()
    val selectedUsername by viewModel.selectedUsername.collectAsStateWithLifecycle()
    var showInitialSkeleton by rememberSaveable { mutableStateOf(true) }
    
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf(setOf<String>()) }
    
    var itemToDelete by remember { mutableStateOf<DownloadItem?>(null) }
    var showMultipleDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(260)
        showInitialSkeleton = false
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
            if (isSelectionMode) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = { 
                            isSelectionMode = false
                            selectedItems = emptySet()
                        },
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(imageVector = Icons.Outlined.Close, contentDescription = "Cancel", tint = TextPrimary)
                    }
                    Text(
                        text = "${selectedItems.size} Selected",
                        fontFamily = DmSans,
                        fontWeight = FontWeight.Medium,
                        fontSize = 20.sp,
                        color = TextPrimary
                    )
                    IconButton(
                        onClick = {
                            showMultipleDeleteDialog = true
                        },
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 48.dp)
                    ) {
                        Icon(imageVector = Icons.Outlined.Delete, contentDescription = "Delete", tint = StatusError)
                    }
                }
            } else {
                Text(
                    text = "Library",
                    fontFamily = DmSerifDisplay,
                    fontWeight = FontWeight.Normal,
                    fontSize = 28.sp,
                    color = TextPrimary
                )
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
                                    text = "Search library...",
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

            // Username filter chips
            if (usernames.isNotEmpty() && searchQuery.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterPill(
                        text = "All Users",
                        isActive = selectedUsername == null,
                        onClick = { viewModel.setSelectedUsername(null) }
                    )
                    usernames.forEach { username ->
                        FilterPill(
                            text = username,
                            isActive = selectedUsername == username,
                            onClick = {
                                viewModel.setSelectedUsername(
                                    if (selectedUsername == username) null else username
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Filter pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterPill(
                    text = "Most Recent",
                    isActive = currentFilter == LibraryFilter.MOST_RECENT,
                    onClick = { viewModel.setFilter(LibraryFilter.MOST_RECENT) }
                )
                FilterPill(
                    text = "By Source",
                    isActive = currentFilter == LibraryFilter.BY_SOURCE,
                    onClick = { viewModel.setFilter(LibraryFilter.BY_SOURCE) }
                )
            }
        }

        if (showInitialSkeleton && videos.isEmpty() && searchQuery.isEmpty()) {
            LibrarySkeletonGrid()
        } else if (videos.isEmpty()) {
            EmptyState()
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = videos,
                    key = { _, item -> item.id }
                ) { _, item ->
                    VideoGridItem(
                        item = item,
                        isSelectionMode = isSelectionMode,
                        isSelected = selectedItems.contains(item.id),
                        onClick = { 
                            if (isSelectionMode) {
                                if (selectedItems.contains(item.id)) selectedItems -= item.id
                                else selectedItems += item.id
                            } else {
                                onVideoClick(item)
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedItems = setOf(item.id)
                            }
                        },
                        onShareClick = { onShareClick(item) },
                        onDeleteClick = { itemToDelete = item }
                    )
                }

                // Bottom spacing for nav bar
                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }

        // Single delete confirmation
        if (itemToDelete != null) {
            AlertDialog(
                onDismissRequest = { itemToDelete = null },
                title = {
                    Text(
                        text = "Delete Media",
                        fontFamily = DmSans,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to delete this from the app and your device?",
                        fontFamily = DmSans,
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        itemToDelete?.let {
                            viewModel.deleteVideo(it.id)
                        }
                        itemToDelete = null
                    }) {
                        Text("Delete", color = StatusError, fontFamily = DmSans, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { itemToDelete = null }) {
                        Text("Cancel", color = TextPrimary, fontFamily = DmSans)
                    }
                },
                containerColor = BgElevated
            )
        }

        // Multiple delete confirmation
        if (showMultipleDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showMultipleDeleteDialog = false },
                title = {
                    Text(
                        text = "Delete ${selectedItems.size} Items",
                        fontFamily = DmSans,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to delete these items from the app and your device?",
                        fontFamily = DmSans,
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        selectedItems.forEach { viewModel.deleteVideo(it) }
                        isSelectionMode = false
                        selectedItems = emptySet()
                        showMultipleDeleteDialog = false
                    }) {
                        Text("Delete All", color = StatusError, fontFamily = DmSans, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showMultipleDeleteDialog = false }) {
                        Text("Cancel", color = TextPrimary, fontFamily = DmSans)
                    }
                },
                containerColor = BgElevated
            )
        }
    }
}

@Composable
private fun LibrarySkeletonGrid() {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
        modifier = Modifier.fillMaxSize()
    ) {
        repeat(6) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .background(BgSurface)
                        .padding(8.dp)
                ) {
                    ShimmerPlaceholder(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(9f / 16f)
                            .clip(ChipShape)
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(100.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoGridItem(
    item: DownloadItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onShareClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .clip(ChipShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (isSelectionMode) onLongClick()
                    else {
                        // Enter selection mode on long press, or could show menu
                        // we'll just trigger onLongClick to enter selection mode
                        onLongClick() 
                    }
                }
            )
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) AccentPrimary else androidx.compose.ui.graphics.Color.Transparent,
                shape = ChipShape
            )
    ) {
        // Thumbnail
        if (item.thumbnailPath != null || item.filePath != null) {
            // Support both content:// URIs (MediaStore) and regular file paths
            val thumbnailModel: Any = run {
                val path = item.thumbnailPath ?: item.filePath ?: return@run ""
                if (path.startsWith("content://")) {
                    android.net.Uri.parse(path)
                } else {
                    path
                }
            }
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(thumbnailModel)
                    .crossfade(true)
                    .size(Size(800, 800))
                    .build(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
            )
        } else {
            ShimmerPlaceholder(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
            )
        }

        // Duration badge (bottom-right)
        if (item.formattedDuration.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(com.adityaprasad.vaultdrop.ui.theme.PillShape)
                    .background(com.adityaprasad.vaultdrop.ui.theme.PureBlack.copy(alpha = 0.75f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = item.formattedDuration,
                    fontFamily = com.adityaprasad.vaultdrop.ui.theme.DmSans,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                    fontSize = 10.sp,
                    color = com.adityaprasad.vaultdrop.ui.theme.TextPrimary
                )
            }
        }

        // Username badge (bottom-left)
        if (!item.username.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(com.adityaprasad.vaultdrop.ui.theme.PillShape)
                    .background(com.adityaprasad.vaultdrop.ui.theme.PureBlack.copy(alpha = 0.75f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "@${item.username}",
                    fontFamily = com.adityaprasad.vaultdrop.ui.theme.DmSans,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                    fontSize = 10.sp,
                    color = com.adityaprasad.vaultdrop.ui.theme.TextPrimary
                )
            }
        }

        // Platform badge (top-left)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .clip(com.adityaprasad.vaultdrop.ui.theme.PillShape)
                .background(PureBlack.copy(alpha = 0.6f))
                .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
            Text(
                text = when (item.platform) {
                    Platform.INSTAGRAM -> "IG"
                    Platform.YOUTUBE -> "YT"
                    Platform.UNSUPPORTED -> "?"
                },
                fontFamily = DmSans,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                color = TextPrimary
            )
        }

        // Username badge (bottom-left)
        if (!item.username.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(PillShape)
                    .background(PureBlack.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "@${item.username}",
                    fontFamily = DmSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    color = TextPrimary
                )
            }
        }

        // Selection Checkbox
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (isSelected) AccentPrimary else PureBlack.copy(alpha = 0.5f))
                    .border(
                        1.dp,
                        if (isSelected) AccentPrimary else TextSecondary,
                        androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = BgPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Context menu
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(BgElevated)
        ) {
            DropdownMenuItem(
                text = { Text("Share", fontFamily = DmSans, color = TextPrimary) },
                onClick = {
                    showMenu = false
                    onShareClick()
                }
            )
            DropdownMenuItem(
                text = { Text("Delete", fontFamily = DmSans, color = StatusError) },
                onClick = {
                    showMenu = false
                    onDeleteClick()
                }
            )
        }
    }
}

@Composable
private fun FilterPill(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(if (isActive) AccentPrimary else BgSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            fontFamily = DmSans,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            color = if (isActive) BgPrimary else TextSecondary
        )
    }
}
