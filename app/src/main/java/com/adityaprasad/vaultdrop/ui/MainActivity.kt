package com.adityaprasad.vaultdrop.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.adityaprasad.vaultdrop.data.repository.DownloadRepository
import com.adityaprasad.vaultdrop.data.repository.BookmarkRepository
import com.adityaprasad.vaultdrop.data.downloader.InstagramDownloader
import com.adityaprasad.vaultdrop.domain.model.DownloadItem
import com.adityaprasad.vaultdrop.domain.model.DownloadStatus
import com.adityaprasad.vaultdrop.ui.components.BottomNavBar
import com.adityaprasad.vaultdrop.ui.components.NavItem
import com.adityaprasad.vaultdrop.ui.downloads.DownloadsScreen
import com.adityaprasad.vaultdrop.ui.home.HomeScreen
import com.adityaprasad.vaultdrop.ui.library.LibraryScreen
import com.adityaprasad.vaultdrop.ui.bookmarks.BookmarksScreen
import com.adityaprasad.vaultdrop.ui.onboarding.OnboardingScreen
import com.adityaprasad.vaultdrop.ui.player.ImageViewerActivity
import com.adityaprasad.vaultdrop.ui.player.VideoPlayerActivity
import com.adityaprasad.vaultdrop.ui.settings.SettingsScreen
import com.adityaprasad.vaultdrop.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp

import com.adityaprasad.vaultdrop.ui.components.AppLockScreen
import com.adityaprasad.vaultdrop.util.InstagramUrlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var repository: DownloadRepository

    @Inject
    lateinit var deleteDownloadUseCase: com.adityaprasad.vaultdrop.domain.usecase.DeleteDownloadUseCase

    @Inject
    lateinit var bookmarkRepository: BookmarkRepository

    @Inject
    lateinit var instagramDownloader: InstagramDownloader

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    private var isAppLocked = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestRequiredPermissions()

        val prefs = getSharedPreferences("vaultdrop_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("app_lock_enabled", false)) {
            isAppLocked.value = true
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (getSharedPreferences("vaultdrop_prefs", Context.MODE_PRIVATE).getBoolean("app_lock_enabled", false)) {
                    isAppLocked.value = true
                }
            }
        })

        setContent {
            VaultDropTheme {
                if (isAppLocked.value) {
                    AppLockScreen(
                        onUnlockRequested = {
                            showBiometricPrompt { success ->
                                if (success) {
                                    isAppLocked.value = false
                                }
                            }
                        }
                    )
                } else {
                    MainApp(
                        repository = repository,
                        deleteDownloadUseCase = deleteDownloadUseCase,
                        bookmarkRepository = bookmarkRepository,
                        instagramDownloader = instagramDownloader
                    )
                }
            }
        }
    }

    private fun showBiometricPrompt(onResult: (Boolean) -> Unit) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val biometricManager = BiometricManager.from(this)

        if (biometricManager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            // Devices with no secure lock set up can't be locked, or lock was removed. Unlock automatically.
            android.widget.Toast.makeText(this, "No secure lock screen set up. App unlocked.", android.widget.Toast.LENGTH_LONG).show()
            getSharedPreferences("vaultdrop_prefs", Context.MODE_PRIVATE).edit().putBoolean("app_lock_enabled", false).apply()
            onResult(true)
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onResult(true)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // Toast error messages to the user if they're not just cancelling
                    if (errorCode != BiometricPrompt.ERROR_CANCELED && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                        android.widget.Toast.makeText(this@MainActivity, errString, android.widget.Toast.LENGTH_SHORT).show()
                    }
                    onResult(false)
                }
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onResult(false)
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock VaultDrop")
            .setSubtitle("Use your biometric credential or PIN")
            .setAllowedAuthenticators(authenticators)
            .build()
        
        biometricPrompt.authenticate(promptInfo)
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_MEDIA_VIDEO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_MEDIA_IMAGES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainApp(
    repository: DownloadRepository,
    deleteDownloadUseCase: com.adityaprasad.vaultdrop.domain.usecase.DeleteDownloadUseCase,
    bookmarkRepository: BookmarkRepository,
    instagramDownloader: InstagramDownloader
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Check onboarding
    val prefs = context.getSharedPreferences("vaultdrop_prefs", Context.MODE_PRIVATE)
    var hasCompletedOnboarding by rememberSaveable {
        mutableStateOf(prefs.getBoolean("onboarding_complete", false))
    }

    // Settings navigation
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var isRefreshingBookmarks by rememberSaveable { mutableStateOf(false) }

    if (!hasCompletedOnboarding) {
        OnboardingScreen(
            onComplete = {
                prefs.edit().putBoolean("onboarding_complete", true).apply()
                hasCompletedOnboarding = true
            }
        )
        return
    }

    if (showSettings) {
        BackHandler { showSettings = false }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgPrimary)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            SettingsScreen(
                onBack = { showSettings = false },
                onDeleteAll = { 
                    coroutineScope.launch {
                        deleteDownloadUseCase.deleteAll()
                    }
                },
                onDeleteAllBookmarks = {
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) {
                            bookmarkRepository.deleteAllBookmarks()
                        }
                        Toast.makeText(context, "All bookmarks deleted", Toast.LENGTH_SHORT).show()
                    }
                },
                isRefreshingBookmarks = isRefreshingBookmarks,
                onRefreshBookmarks = {
                    if (!isRefreshingBookmarks) {
                        isRefreshingBookmarks = true
                        coroutineScope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    val items = bookmarkRepository.getAllBookmarks().first()
                                    items.forEach { item ->
                                        var workingUrl = item.url
                                        if (item.platform == com.adityaprasad.vaultdrop.domain.model.Platform.INSTAGRAM) {
                                            val normalized = InstagramUrlUtils.normalize(item.url)
                                            if (normalized != item.url) {
                                                bookmarkRepository.updateUrl(item.id, normalized)
                                            }
                                            workingUrl = normalized
                                        }

                                        val refreshed = when (item.platform) {
                                            com.adityaprasad.vaultdrop.domain.model.Platform.INSTAGRAM -> instagramDownloader.getThumbnailUrl(workingUrl)
                                            com.adityaprasad.vaultdrop.domain.model.Platform.YOUTUBE -> {
                                                val uri = runCatching { android.net.Uri.parse(workingUrl) }.getOrNull()
                                                val host = uri?.host.orEmpty().lowercase()
                                                val videoId = when {
                                                    host.contains("youtu.be") -> uri?.lastPathSegment
                                                    host.contains("youtube.com") -> {
                                                        if (uri?.path.orEmpty().startsWith("/shorts/")) uri?.pathSegments?.getOrNull(1)
                                                        else uri?.getQueryParameter("v")
                                                    }
                                                    else -> null
                                                }
                                                videoId?.let { "https://img.youtube.com/vi/$it/maxresdefault.jpg" }
                                            }
                                            else -> null
                                        }

                                        if (!refreshed.isNullOrBlank()) {
                                            bookmarkRepository.updateThumbnailUrl(item.id, refreshed)
                                        } else {
                                            val host = runCatching { android.net.Uri.parse(workingUrl).host }.getOrNull()
                                            if (!host.isNullOrBlank()) {
                                                val fallback = "https://www.google.com/s2/favicons?sz=128&domain=$host"
                                                bookmarkRepository.updateThumbnailUrl(item.id, fallback)
                                            }
                                        }
                                    }
                                }
                                Toast.makeText(context, "Bookmarks repaired and refreshed", Toast.LENGTH_SHORT).show()
                            } finally {
                                isRefreshingBookmarks = false
                            }
                        }
                    }
                }
            )
        }
        return
    }

    // Main tabbed UI with swipe
    val navItems = remember {
        listOf(
            NavItem(
                label = "Home",
                iconOutlined = Icons.Outlined.Home,
                iconFilled = Icons.Filled.Home,
                route = "home"
            ),
            NavItem(
                label = "Downloads",
                iconOutlined = Icons.Outlined.Download,
                iconFilled = Icons.Filled.Download,
                route = "downloads"
            ),
            NavItem(
                label = "Library",
                iconOutlined = Icons.Outlined.FolderOpen,
                iconFilled = Icons.Filled.FolderOpen,
                route = "library"
            ),
            NavItem(
                label = "Vault",
                iconOutlined = Icons.Outlined.Bookmark,
                iconFilled = Icons.Filled.Bookmark,
                route = "vault"
            ),
        )
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { navItems.size }
    )

    // Sync pager with currently selected route
    var selectedIndex by rememberSaveable { mutableStateOf(0) }

    // When user swipes, update the selected tab
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            selectedIndex = page
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Swipeable pages
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 0
        ) { page ->
            when (page) {
                0 -> HomeScreen(repository = repository)
                1 -> com.adityaprasad.vaultdrop.ui.downloads.DownloadsScreen(
                    onVideoClick = { item -> openVideoPlayer(context, item) }
                )
                2 -> LibraryScreen(
                    onVideoClick = { item -> openVideoPlayer(context, item) },
                    onShareClick = { item -> shareVideo(context, item) },
                    onDeleteClick = { /* handled by viewmodel */ }
                )
                3 -> BookmarksScreen()
            }
        }

        // Floating bottom nav bar
        BottomNavBar(
            items = navItems,
            selectedRoute = navItems[selectedIndex].route,
            onItemSelected = { route ->
                val index = navItems.indexOfFirst { it.route == route }
                if (index >= 0 && index != selectedIndex) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        
        // Universal Settings Icon
        androidx.compose.material3.IconButton(
            onClick = { showSettings = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 18.dp, end = 20.dp)
        ) {
            androidx.compose.material3.Icon(
                imageVector = androidx.compose.material.icons.Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = com.adityaprasad.vaultdrop.ui.theme.TextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun openVideoPlayer(context: Context, item: DownloadItem) {
    if (item.status == DownloadStatus.DONE && item.filePath != null) {
        val filePath = item.filePath
        
        // Determine if media is an image. Works with both file paths and content:// URIs.
        val isImage = filePath.endsWith(".jpg", ignoreCase = true) ||
                filePath.endsWith(".jpeg", ignoreCase = true) ||
                filePath.endsWith(".png", ignoreCase = true) ||
                filePath.endsWith(".webp", ignoreCase = true) ||
                // For content:// URIs, check if it's in the Images collection or has no duration
                (filePath.startsWith("content://") && (
                    filePath.contains("images", ignoreCase = true) ||
                    item.durationMs == 0L
                ))

        if (isImage) {
            val intent = Intent(context, ImageViewerActivity::class.java).apply {
                putExtra(ImageViewerActivity.EXTRA_FILE_PATH, filePath)
                putExtra(ImageViewerActivity.EXTRA_TITLE, item.title)
            }
            context.startActivity(intent)
        } else {
            val intent = Intent(context, VideoPlayerActivity::class.java).apply {
                putExtra(VideoPlayerActivity.EXTRA_FILE_PATH, filePath)
                putExtra(VideoPlayerActivity.EXTRA_TITLE, item.title)
            }
            context.startActivity(intent)
        }
    }
}

private fun shareVideo(context: Context, item: DownloadItem) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, item.url)
    }
    context.startActivity(Intent.createChooser(intent, "Share"))
}
