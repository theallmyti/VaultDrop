package com.adityaprasad.vaultdrop.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adityaprasad.vaultdrop.domain.model.DownloadItem
import com.adityaprasad.vaultdrop.domain.model.DownloadStatus
import com.adityaprasad.vaultdrop.domain.model.Platform
import com.adityaprasad.vaultdrop.ui.theme.*

@Composable
fun DownloadCard(
    item: DownloadItem,
    onClick: () -> Unit,
    onRetry: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(BgSurface)
            .border(1.dp, BorderSubtle, CardShape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        ThumbnailView(
            thumbnailPath = item.thumbnailPath,
            platform = item.platform,
            modifier = Modifier.size(56.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Title row with delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
            Text(
                text = item.title,
                fontFamily = DmSans,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Remove",
                        tint = TextTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            } // end title Row

            // Meta row
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildString {
                        append(item.sourceDomain)
                        if (!item.username.isNullOrEmpty()) {
                            append(" · @")
                            append(item.username)
                        }
                        if (item.formattedSize.isNotEmpty()) {
                            append(" · ")
                            append(item.formattedSize)
                        }
                        if (item.formattedDuration.isNotEmpty()) {
                            append(" · ")
                            append(item.formattedDuration)
                        }
                    },
                    fontFamily = DmSans,
                    fontWeight = FontWeight.Light,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            // Status-specific UI
            when (item.status) {
                DownloadStatus.QUEUED -> {
                    StatusBadge(
                        text = "Queued",
                        bgColor = BadgeBg,
                        textColor = TextSecondary
                    )
                }

                DownloadStatus.ACTIVE -> {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${item.progressPercent}%",
                                fontFamily = DmSans,
                                fontWeight = FontWeight.Light,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            if (item.formattedSpeed.isNotEmpty()) {
                                Text(
                                    text = "↓ ${item.formattedSpeed}/s",
                                    fontFamily = JetBrainsMono,
                                    fontWeight = FontWeight.Light,
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        ShimmerProgressBar(
                            progress = item.progressPercent / 100f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                        )
                    }
                }

                DownloadStatus.DONE -> {
                    StatusBadge(
                        text = "✓ Saved",
                        bgColor = Color.Transparent,
                        textColor = StatusSuccess
                    )
                }

                DownloadStatus.FAILED -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (item.errorMessage != null) {
                            Text(
                                text = item.errorMessage,
                                fontFamily = DmSans,
                                fontWeight = FontWeight.Light,
                                fontSize = 11.sp,
                                color = StatusError,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (onRetry != null) {
                            Box(
                                modifier = Modifier
                                    .clip(PillShape)
                                    .background(StatusError.copy(alpha = 0.15f))
                                    .clickable(onClick = onRetry)
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Retry",
                                    fontFamily = DmSans,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = StatusError
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(
    text: String,
    bgColor: Color,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            fontFamily = DmSans,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            color = textColor
        )
    }
}

@Composable
fun ShimmerProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -200f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(ProgressBg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            AccentPrimary,
                            AccentPrimary.copy(alpha = 0.6f),
                            AccentPrimary,
                        ),
                        start = Offset(shimmerOffset, 0f),
                        end = Offset(shimmerOffset + 200f, 0f)
                    )
                )
        )
    }
}
