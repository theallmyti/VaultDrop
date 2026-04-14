package com.adityaprasad.vaultdrop.ui.bookmarks

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.adityaprasad.vaultdrop.data.api.ConvexApiService
import com.adityaprasad.vaultdrop.data.api.DeleteBookmarkRequest
import com.adityaprasad.vaultdrop.data.downloader.InstagramDownloader
import androidx.lifecycle.ViewModel
import com.adityaprasad.vaultdrop.data.repository.BookmarkRepository
import com.adityaprasad.vaultdrop.domain.model.BookmarkItem
import com.adityaprasad.vaultdrop.domain.model.Platform
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class BookmarkFilter { ALL, NONE, BY_TAG }

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val repository: BookmarkRepository,
    private val instagramDownloader: InstagramDownloader,
    private val convexApi: ConvexApiService,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val usernameBackfillAttemptedIds = mutableSetOf<String>()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filter = MutableStateFlow(BookmarkFilter.ALL)
    val filter: StateFlow<BookmarkFilter> = _filter.asStateFlow()

    private val _selectedUsername = MutableStateFlow<String?>(null)
    val selectedUsername: StateFlow<String?> = _selectedUsername.asStateFlow()

    private val _selectedTags = MutableStateFlow<Set<String>>(emptySet())
    val selectedTags: StateFlow<Set<String>> = _selectedTags.asStateFlow()

    private val debouncedSearchQuery = _searchQuery
        .debounce(250)
        .distinctUntilChanged()

    val bookmarks: StateFlow<List<BookmarkItem>> = debouncedSearchQuery
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
        backfillMissingInstagramUsernames()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: BookmarkFilter) {
        _filter.value = filter
    }

    fun selectUsername(username: String?) {
        _selectedUsername.value = username
        _selectedTags.value = emptySet()
    }

    fun selectTag(tag: String?) {
        _selectedUsername.value = null
        _selectedTags.value = when (tag) {
            null -> emptySet()
            else -> {
                val normalized = normalizeTag(tag)
                if (normalized.isBlank()) {
                    _selectedTags.value
                } else {
                    _selectedTags.value.toMutableSet().apply {
                        if (!add(normalized)) {
                            remove(normalized)
                        }
                    }
                }
            }
        }
    }

    fun clearFilters() {
        _selectedUsername.value = null
        _selectedTags.value = emptySet()
        _filter.value = BookmarkFilter.NONE
    }

    fun clearSelectedTags() {
        _selectedTags.value = emptySet()
    }

    fun deleteBookmark(id: String, removeFromCloud: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = bookmarks.value.firstOrNull { it.id == id }
            repository.deleteBookmark(id)

            if (removeFromCloud) {
                val prefs = appContext.getSharedPreferences("vaultdrop_prefs", Context.MODE_PRIVATE)
                val token = prefs.getString("auth_token", null).orEmpty().trim()
                if (token.isNotBlank()) {
                    runCatching {
                        convexApi.deleteBookmark(
                            DeleteBookmarkRequest(
                                token = token,
                                bookmarkId = id,
                                url = item?.url,
                            ),
                        )
                    }
                }
            }
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

    private fun backfillMissingInstagramUsernames() {
        viewModelScope.launch(Dispatchers.IO) {
            val items = repository.getAllBookmarks().first()
            items
                .asSequence()
                .filter { it.platform == Platform.INSTAGRAM }
                .filter { it.username.isBlank() || it.username.equals("@instagram", ignoreCase = true) }
                .filter { usernameBackfillAttemptedIds.add(it.id) }
                .take(6)
                .forEach { item ->
                    val fetched = runCatching { instagramDownloader.resolveUsername(item.url) }
                        .getOrNull()
                        .orEmpty()
                        .trim()
                    if (fetched.isNotBlank()) {
                        repository.updateUsername(item.id, fetched)
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

    private fun normalizeTag(raw: String): String {
        return raw.trim().lowercase().removePrefix("#").replace(" ", "_")
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
