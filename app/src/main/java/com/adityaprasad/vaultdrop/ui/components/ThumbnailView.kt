package com.adityaprasad.vaultdrop.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.adityaprasad.vaultdrop.domain.model.Platform
import com.adityaprasad.vaultdrop.ui.theme.*
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun ThumbnailView(
    thumbnailPath: String?,
    platform: Platform,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(ThumbnailShape),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnailPath != null) {
            AsyncImage(
                model = thumbnailPath,
                contentDescription = "Video thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            // Skeleton shimmer placeholder
            ShimmerPlaceholder(modifier = Modifier.matchParentSize())
        }

        // Platform badge (top-left)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .clip(ChipShape)
                .background(PureBlack.copy(alpha = 0.7f))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = when (platform) {
                    Platform.INSTAGRAM -> "IG"
                    Platform.YOUTUBE -> "YT"
                    Platform.UNSUPPORTED -> "?"
                },
                fontFamily = DmSans,
                fontWeight = FontWeight.Medium,
                fontSize = 9.sp,
                color = TextPrimary
            )
        }
    }
}

@Composable
fun ShimmerPlaceholder(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val offset by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "skeletonOffset"
    )

    Box(
        modifier = modifier
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        BgElevated,
                        BorderSubtle,
                        BgElevated,
                    ),
                    start = Offset(offset, 0f),
                    end = Offset(offset + 300f, 0f)
                )
            )
    )
}
