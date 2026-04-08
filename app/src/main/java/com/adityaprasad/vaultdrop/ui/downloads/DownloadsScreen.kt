package com.adityaprasad.vaultdrop.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adityaprasad.vaultdrop.domain.model.DownloadItem
import com.adityaprasad.vaultdrop.ui.components.DownloadCard
import com.adityaprasad.vaultdrop.ui.components.EmptyState
import com.adityaprasad.vaultdrop.ui.components.ShimmerPlaceholder
import com.adityaprasad.vaultdrop.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun DownloadsScreen(
    onVideoClick: (DownloadItem) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val completedDownloads by viewModel.completedDownloads.collectAsStateWithLifecycle()
    val activeCount by viewModel.activeCount.collectAsStateWithLifecycle()
    val completedCount by viewModel.completedCount.collectAsStateWithLifecycle()
    var showInitialSkeleton by rememberSaveable { mutableStateOf(true) }

    var itemToDelete by remember { mutableStateOf<String?>(null) }

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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = "Downloads",
                    fontFamily = DmSerifDisplay,
                    fontWeight = FontWeight.Normal,
                    fontSize = 28.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$activeCount active · $completedCount completed",
                    fontFamily = DmSans,
                    fontWeight = FontWeight.Light,
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
        }

        if (showInitialSkeleton && activeDownloads.isEmpty() && completedDownloads.isEmpty()) {
            DownloadsSkeletonList()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // -- In Progress Section --
                item {
                    SectionLabel(text = "IN PROGRESS")
                }

                if (activeDownloads.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ChipShape)
                                .background(BgSurface)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "Nothing downloading right now",
                                fontFamily = DmSans,
                                fontWeight = FontWeight.Light,
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        items = activeDownloads,
                        key = { _, item -> item.id }
                    ) { _, item ->
                        DownloadCard(
                            item = item,
                            onClick = { onVideoClick(item) },
                            onDelete = { itemToDelete = item.id },
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionLabel(text = "COMPLETED")
                }

                if (completedDownloads.isEmpty()) {
                    item {
                        EmptyState()
                    }
                } else {
                    itemsIndexed(
                        items = completedDownloads,
                        key = { _, item -> item.id }
                    ) { _, item ->
                        DownloadCard(
                            item = item,
                            onClick = { onVideoClick(item) },
                            onDelete = { itemToDelete = item.id },
                        )
                    }
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
                        text = "Delete Download",
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
                            viewModel.deleteDownload(it)
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
    }
}

@Composable
private fun DownloadsSkeletonList() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { SectionLabel(text = "IN PROGRESS") }

        repeat(2) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .background(BgSurface)
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShimmerPlaceholder(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(14.dp)
                                .clip(PillShape)
                        )
                        ShimmerPlaceholder(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .clip(ChipShape)
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            SectionLabel(text = "COMPLETED")
        }

        repeat(3) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .background(BgSurface)
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShimmerPlaceholder(
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .height(14.dp)
                                .clip(PillShape)
                        )
                        ShimmerPlaceholder(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .height(12.dp)
                                .clip(PillShape)
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(100.dp)) }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = DmSans,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        color = TextInactive,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
    )
}
