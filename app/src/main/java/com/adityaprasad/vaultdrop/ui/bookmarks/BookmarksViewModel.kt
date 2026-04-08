package com.adityaprasad.vaultdrop.ui.bookmarks

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.adityaprasad.vaultdrop.data.downloader.InstagramDownloader
import androidx.lifecycle.ViewModel
import com.adityaprasad.vaultdrop.data.repository.BookmarkRepository
import com.adityaprasad.vaultdrop.domain.model.BookmarkItem
import com.adityaprasad.vaultdrop.domain.model.Platform
import com.adityaprasad.vaultdrop.util.InstagramUrlUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class BookmarkFilter { ALL, BY_ACCOUNT }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val repository: BookmarkRepository,
    private val instagramDownloader: InstagramDownloader
) : ViewModel() {

    private val backfillAttemptedIds = mutableSetOf<String>()
    private val normalizationAttemptedIds = mutableSetOf<String>()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filter = MutableStateFlow(BookmarkFilter.ALL)
    val filter: StateFlow<BookmarkFilter> = _filter.asStateFlow()

    private val _selectedUsername = MutableStateFlow<String?>(null)
    val selectedUsername: StateFlow<String?> = _selectedUsername.asStateFlow()

    val bookmarks: StateFlow<List<BookmarkItem>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                repository.getAllBookmarks()
            } else {
                repository.searchBookmarks(query)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val usernames: StateFlow<List<String>> = repository.getAllUsernames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarkCount: StateFlow<Int> = repository.getBookmarkCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        normalizeInstagramBookmarkUrls()
        backfillMissingThumbnails()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: BookmarkFilter) {
        _filter.value = filter
        if (filter == BookmarkFilter.ALL) {
            _selectedUsername.value = null
        }
    }

    fun selectUsername(username: String?) {
        _selectedUsername.value = username
        if (username != null) {
            _filter.value = BookmarkFilter.BY_ACCOUNT
        }
    }

    fun deleteBookmark(id: String) {
        viewModelScope.launch {
            repository.deleteBookmark(id)
        }
    }

    fun refreshThumbnail(item: BookmarkItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val resolved = resolveThumbnailUrl(item)
            if (!resolved.isNullOrBlank()) {
                repository.updateThumbnailUrl(item.id, resolved)
            }
        }
    }

    private fun backfillMissingThumbnails() {
        viewModelScope.launch {
            val items = repository.getAllBookmarks().first()
            items
                .asSequence()
                .filter { it.thumbnailUrl.isNullOrBlank() || isInvalidStoredThumbnail(it) }
                .filter { backfillAttemptedIds.add(it.id) }
                .forEach { item ->
                    launch(Dispatchers.IO) {
                        val resolved = resolveThumbnailUrl(item)
                        if (!resolved.isNullOrBlank()) {
                            repository.updateThumbnailUrl(item.id, resolved)
                        }
                    }
                }
        }
    }

    private fun isInvalidStoredThumbnail(item: BookmarkItem): Boolean {
        val url = item.thumbnailUrl.orEmpty().lowercase()
        if (url.isBlank()) return true

        if (item.platform == Platform.INSTAGRAM) {
            if (url.contains("instagram.com") && !url.contains("scontent") && !url.contains("fbcdn")) {
                return true
            }
            if (url.contains("/static/") || url.contains("logo") || url.contains("app-icon")) {
                return true
            }
        }

        return false
    }

    private fun normalizeInstagramBookmarkUrls() {
        viewModelScope.launch {
            val items = repository.getAllBookmarks().first()
            items
                .asSequence()
                .filter { it.platform == Platform.INSTAGRAM }
                .filter { normalizationAttemptedIds.add(it.id) }
                .forEach { item ->
                    launch(Dispatchers.IO) {
                        val normalized = InstagramUrlUtils.normalize(item.url)
                        if (normalized != item.url) {
                            repository.updateUrl(item.id, normalized)
                        }
                    }
                }
        }
    }

    private suspend fun resolveThumbnailUrl(item: BookmarkItem): String? {
        val extracted = when (item.platform) {
            Platform.INSTAGRAM -> runCatching {
                instagramDownloader.getThumbnailUrl(item.url)
            }.getOrNull()
            Platform.YOUTUBE -> buildYouTubeThumbnailUrl(item.url)
            Platform.UNSUPPORTED -> null
        }

        return extracted?.takeIf { it.isNotBlank() } ?: buildFaviconUrl(item.url)
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
}
