package com.adityaprasad.vaultdrop.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.biometric.BiometricManager
import android.content.Intent
import android.provider.Settings
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adityaprasad.vaultdrop.ui.theme.*
import com.adityaprasad.vaultdrop.util.InstagramSessionManager

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onDeleteAll: () -> Unit,
    onDeleteAllBookmarks: () -> Unit,
    isRefreshingBookmarks: Boolean,
    onRefreshBookmarks: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDeleteBookmarksDialog by remember { mutableStateOf(false) }
    var selectedQuality by remember { mutableStateOf("720p") }
    var concurrentDownloads by remember { mutableStateOf(3) }
    var newTagText by remember { mutableStateOf("") }
    var editingTagIndex by remember { mutableStateOf(-1) }
    
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences("vaultdrop_prefs", Context.MODE_PRIVATE) }
    var appLockEnabled by remember { mutableStateOf(prefs.getBoolean("app_lock_enabled", false)) }
    var bookmarkTags by remember { mutableStateOf(loadBookmarkTagsFromPrefs(prefs)) }
    var hasInstagramSession by remember { mutableStateOf(InstagramSessionManager.hasSession(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasInstagramSession = InstagramSessionManager.hasSession(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
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
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Text(
                text = "Settings",
                fontFamily = DmSerifDisplay,
                fontWeight = FontWeight.Normal,
                fontSize = 24.sp,
                color = TextPrimary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // --- Downloads Section ---
            SectionHeader("Downloads")

            SettingsRow(
                title = "Default Quality",
                subtitle = selectedQuality
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("480p", "720p", "1080p").forEach { q ->
                        SegmentPill(
                            text = q,
                            isSelected = selectedQuality == q,
                            onClick = { selectedQuality = q }
                        )
                    }
                }
            }

            SettingsRow(
                title = "Concurrent Downloads",
                subtitle = "$concurrentDownloads"
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3).forEach { n ->
                        SegmentPill(
                            text = "$n",
                            isSelected = concurrentDownloads == n,
                            onClick = { concurrentDownloads = n }
                        )
                    }
                }
            }

            SettingsDivider()

            // --- Instagram Section ---
            SectionHeader("Instagram")

            SettingsRow(
                title = "Cookie / Session",
                subtitle = if (hasInstagramSession) {
                    "Connected: authenticated extraction enabled"
                } else {
                    "Required for Stories and private content"
                },
                onClick = {
                    context.startActivity(Intent(context, InstagramSessionActivity::class.java))
                }
            ) {
                if (hasInstagramSession) {
                    Text(
                        text = "Clear session",
                        modifier = Modifier
                            .clip(PillShape)
                            .background(BgElevated)
                            .clickable {
                                InstagramSessionManager.clearSession(context)
                                hasInstagramSession = false
                                Toast.makeText(context, "Instagram session cleared", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        fontFamily = DmSans,
                        fontWeight = FontWeight.Light,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            SettingsDivider()

            // --- Storage Section ---
            SectionHeader("Storage")

            SettingsRow(
                title = "Clear Cache",
                subtitle = "Free up temporary files",
                onClick = { /* TODO */ }
            )

            SettingsRow(
                title = "Delete All Downloads",
                subtitle = "Remove all downloaded videos",
                isDestructive = true,
                onClick = { showDeleteDialog = true }
            )

            SettingsDivider()

            // --- Bookmarks Section ---
            SectionHeader("Bookmarks")

            SettingsRow(
                title = "Quick Tags",
                subtitle = "Create tags for faster notes"
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CardShape)
                                .background(BgElevated)
                                .padding(4.dp)
                        ) {
                            BasicTextField(
                                value = newTagText,
                                onValueChange = { newTagText = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontFamily = DmSans,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 14.sp,
                                    color = TextPrimary
                                ),
                                cursorBrush = SolidColor(AccentPrimary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                decorationBox = { innerTextField ->
                                    Box {
                                        if (newTagText.isEmpty()) {
                                            Text(
                                                text = if (editingTagIndex >= 0) "edit tag" else "tag name",
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

                        Box(
                            modifier = Modifier
                                .clip(PillShape)
                                .background(AccentPrimary)
                                .clickable {
                                    val normalized = normalizeTag(newTagText)
                                    if (normalized.isNotEmpty()) {
                                        bookmarkTags = if (editingTagIndex in bookmarkTags.indices) {
                                            bookmarkTags.toMutableList().apply {
                                                this[editingTagIndex] = normalized
                                            }
                                        } else if (!bookmarkTags.contains(normalized)) {
                                            bookmarkTags + normalized
                                        } else {
                                            bookmarkTags
                                        }
                                        saveBookmarkTagsToPrefs(prefs, bookmarkTags)
                                        newTagText = ""
                                        editingTagIndex = -1
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (editingTagIndex >= 0) "Update" else "Add",
                                fontFamily = DmSans,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = BgPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (bookmarkTags.isEmpty()) {
                        Text(
                            text = "No tags yet",
                            fontFamily = DmSans,
                            fontWeight = FontWeight.Light,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(bookmarkTags) { tag ->
                                val index = bookmarkTags.indexOf(tag)
                                Box(
                                    modifier = Modifier
                                        .clip(PillShape)
                                        .background(BgElevated)
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "#$tag",
                                            fontFamily = DmSans,
                                            fontWeight = FontWeight.Normal,
                                            fontSize = 12.sp,
                                            color = TextPrimary,
                                            modifier = Modifier.clickable {
                                                newTagText = tag
                                                editingTagIndex = index
                                            }
                                        )
                                        Text(
                                            text = "✕",
                                            modifier = Modifier.clickable {
                                                bookmarkTags = bookmarkTags.filterNot { it == tag }
                                                saveBookmarkTagsToPrefs(prefs, bookmarkTags)
                                                if (editingTagIndex == index) {
                                                    newTagText = ""
                                                    editingTagIndex = -1
                                                }
                                            },
                                            fontFamily = DmSans,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.sp,
                                            color = TextTertiary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SettingsRow(
                title = "Repair Instagram Links & Thumbnails",
                subtitle = if (isRefreshingBookmarks) "Refreshing saved previews..." else "One tap: remove igsh and refresh previews",
                onClick = if (isRefreshingBookmarks) null else onRefreshBookmarks
            ) {
                if (isRefreshingBookmarks) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = AccentPrimary
                        )
                        Text(
                            text = "Working...",
                            fontFamily = DmSans,
                            fontWeight = FontWeight.Light,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            SettingsRow(
                title = "Delete All Bookmarks",
                subtitle = "Remove all saved bookmark links",
                isDestructive = true,
                onClick = { showDeleteBookmarksDialog = true }
            )

            SettingsDivider()

            // --- Security Section ---
            SectionHeader("Security")

            SettingsRow(
                title = "App Lock",
                subtitle = "Require biometric or PIN to open"
            ) {
                Switch(
                    checked = appLockEnabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            val biometricManager = BiometricManager.from(context)
                            val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                            when (biometricManager.canAuthenticate(authenticators)) {
                                BiometricManager.BIOMETRIC_SUCCESS -> {
                                    appLockEnabled = true
                                    prefs.edit().putBoolean("app_lock_enabled", true).apply()
                                }
                                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                                    Toast.makeText(context, "Please set up a screen lock (PIN/Pattern) or Biometric first.", Toast.LENGTH_LONG).show()
                                    val enrollIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                                            putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, authenticators)
                                        }
                                    } else {
                                        Intent(Settings.ACTION_SECURITY_SETTINGS)
                                    }
                                    context.startActivity(enrollIntent)
                                }
                                else -> {
                                    Toast.makeText(context, "App lock is not available on this device", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            appLockEnabled = false
                            prefs.edit().putBoolean("app_lock_enabled", false).apply()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BgPrimary,
                        checkedTrackColor = AccentPrimary,
                        uncheckedThumbColor = TextTertiary,
                        uncheckedTrackColor = BgSurface
                    )
                )
            }

            SettingsDivider()

            // --- About Section ---
            SectionHeader("About")

            SettingsRow(
                title = "App Version",
                subtitle = "1.0.0"
            )

            SettingsRow(
                title = "Credits / Licenses",
                subtitle = "Open source libraries used"
            )

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = "Delete all downloads?",
                    fontFamily = DmSans,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "This will permanently remove all downloaded videos. This action cannot be undone.",
                    fontFamily = DmSans,
                    fontWeight = FontWeight.Light,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteAll()
                    }
                ) {
                    Text("Delete", color = StatusError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BgElevated
        )
    }

    if (showDeleteBookmarksDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteBookmarksDialog = false },
            title = {
                Text(
                    text = "Delete all bookmarks?",
                    fontFamily = DmSans,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "This will permanently remove all saved bookmark links. This action cannot be undone.",
                    fontFamily = DmSans,
                    fontWeight = FontWeight.Light,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteBookmarksDialog = false
                        onDeleteAllBookmarks()
                    }
                ) {
                    Text("Delete", color = StatusError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteBookmarksDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BgElevated
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontFamily = DmSans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        color = AccentPrimary,
        modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    isDestructive: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = title,
            fontFamily = DmSans,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            color = if (isDestructive) StatusError else TextPrimary
        )
        Text(
            text = subtitle,
            fontFamily = DmSans,
            fontWeight = FontWeight.Light,
            fontSize = 12.sp,
            color = if (isDestructive) StatusError.copy(alpha = 0.6f) else TextSecondary
        )
        if (content != null) {
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        thickness = 1.dp,
        color = BorderSubtle
    )
}

@Composable
private fun SegmentPill(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(if (isSelected) AccentPrimary else BgSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
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

private fun loadBookmarkTagsFromPrefs(prefs: android.content.SharedPreferences): List<String> {
    val raw = prefs.getString("bookmark_tags", "").orEmpty()
    if (raw.isBlank()) return emptyList()
    return raw.split("|").map { it.trim() }.filter { it.isNotEmpty() }
}

private fun saveBookmarkTagsToPrefs(prefs: android.content.SharedPreferences, tags: List<String>) {
    prefs.edit().putString("bookmark_tags", tags.joinToString("|")).apply()
}

private fun normalizeTag(raw: String): String {
    return raw.trim().lowercase().removePrefix("#").replace(" ", "_")
}
