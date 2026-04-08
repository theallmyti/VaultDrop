package com.adityaprasad.vaultdrop.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adityaprasad.vaultdrop.ui.theme.*

data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val body: String,
)

val onboardingPages = listOf(
    OnboardingPage(
        icon = Icons.Outlined.Share,
        title = "Just share the link",
        body = "Open any Instagram Reel or YouTube video and tap Share — choose VaultDrop from the list"
    ),
    OnboardingPage(
        icon = Icons.Outlined.Download,
        title = "Track everything",
        body = "See live download progress, speed, and file size — all in one place"
    ),
    OnboardingPage(
        icon = Icons.Outlined.PlayArrow,
        title = "Your personal vault",
        body = "All your saved videos in a clean library, ready to play offline anytime"
    ),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val item = onboardingPages[page]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = AccentPrimary,
                    modifier = Modifier.size(64.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = item.title,
                    fontFamily = DmSerifDisplay,
                    fontWeight = FontWeight.Normal,
                    fontSize = 24.sp,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = item.body,
                    fontFamily = DmSans,
                    fontWeight = FontWeight.Light,
                    fontSize = 15.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Dot indicators
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(onboardingPages.size) { index ->
                Box(
                    modifier = Modifier
                        .size(
                            width = if (pagerState.currentPage == index) 24.dp else 8.dp,
                            height = 8.dp
                        )
                        .clip(PillShape)
                        .background(
                            if (pagerState.currentPage == index) AccentPrimary
                            else BorderSubtle
                        )
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // CTA Button (last step only)
        AnimatedVisibility(
            visible = pagerState.currentPage == onboardingPages.size - 1,
            enter = fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 24.dp)
                    .clip(PillShape)
                    .background(AccentPrimary)
                    .clickable(onClick = onComplete)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Let's Go",
                    fontFamily = DmSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = BgPrimary
                )
            }
        }

        if (pagerState.currentPage != onboardingPages.size - 1) {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
