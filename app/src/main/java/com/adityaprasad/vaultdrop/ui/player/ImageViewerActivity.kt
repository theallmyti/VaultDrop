package com.adityaprasad.vaultdrop.ui.player

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.adityaprasad.vaultdrop.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

import javax.inject.Inject
import com.adityaprasad.vaultdrop.data.downloader.MediaStoreHelper
import androidx.compose.material.icons.filled.Download

@AndroidEntryPoint
class ImageViewerActivity : ComponentActivity() {

    @Inject
    lateinit var mediaStoreHelper: MediaStoreHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: run {
            finish()
            return
        }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Image"

        setContent {
            VaultDropTheme {
                ImageViewerScreen(
                    filePath = filePath,
                    title = title,
                    onBack = { finish() },
                    onSave = {
                        val file = java.io.File(filePath.replace("content://", "")) // simplified, works for local files
                        if (!file.exists()) {
                            Toast.makeText(this@ImageViewerActivity, "Cannot save content URI directly", Toast.LENGTH_SHORT).show()
                            return@ImageViewerScreen
                        }
                        val mimeType = mediaStoreHelper.getMimeType(file)
                        val uri = mediaStoreHelper.copyToMediaStore(file, "Saved", mimeType)
                        if (uri != null) {
                            Toast.makeText(this@ImageViewerActivity, "Saved to Gallery", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@ImageViewerActivity, "Failed to save", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_TITLE = "extra_title"
    }
}

@Composable
fun ImageViewerScreen(
    filePath: String,
    title: String,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    var showControls by remember { mutableStateOf(true) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { showControls = !showControls }
    ) {
        // Support both content:// URIs (MediaStore) and regular file paths
        val imageModel: Any = if (filePath.startsWith("content://")) {
            android.net.Uri.parse(filePath)
        } else {
            File(filePath)
        }

        AsyncImage(
            model = imageModel,
            contentDescription = title,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
        )

        // Top bar overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ControlOverlay)
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                    IconButton(onClick = onSave) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = "Save to Gallery",
                            tint = TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Double-tap to reset zoom hint
        if (scale != 1f) {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Text(
                    text = "Pinch to zoom · ${"%.0f".format(scale * 100)}%",
                    fontFamily = DmSans,
                    fontWeight = FontWeight.Light,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
            }
        }
    }
}
