package com.adityaprasad.vaultdrop.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adityaprasad.vaultdrop.data.repository.DownloadRepository
import com.adityaprasad.vaultdrop.domain.model.DownloadItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryFilter {
    MOST_RECENT,
    BY_SOURCE
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: DownloadRepository,
    private val deleteDownloadUseCase: com.adityaprasad.vaultdrop.domain.usecase.DeleteDownloadUseCase
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedUsername = MutableStateFlow<String?>(null)
    val selectedUsername: StateFlow<String?> = _selectedUsername

    private val _filter = MutableStateFlow(LibraryFilter.MOST_RECENT)
    val filter: StateFlow<LibraryFilter> = _filter

    val usernames: StateFlow<List<String>> = repository.getCompletedUsernames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val videos: StateFlow<List<DownloadItem>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) repository.getCompletedDownloads()
            else repository.searchCompletedDownloads(query)
        }
        .combine(_selectedUsername) { items, username ->
            if (username == null) items else items.filter { it.username == username }
        }
        .combine(_filter) { items, filter ->
            when (filter) {
                LibraryFilter.MOST_RECENT -> items.sortedByDescending { it.completedAt }
                LibraryFilter.BY_SOURCE -> items.sortedBy { it.platform.name }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedUsername(username: String?) {
        _selectedUsername.value = username
    }

    fun setFilter(filter: LibraryFilter) {
        _filter.value = filter
    }

    fun deleteVideo(id: String) {
        viewModelScope.launch {
            deleteDownloadUseCase(id)
        }
    }
}
