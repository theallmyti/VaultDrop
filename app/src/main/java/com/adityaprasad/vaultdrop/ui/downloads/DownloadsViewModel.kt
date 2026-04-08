package com.adityaprasad.vaultdrop.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adityaprasad.vaultdrop.domain.model.DownloadItem
import com.adityaprasad.vaultdrop.domain.usecase.DeleteDownloadUseCase
import com.adityaprasad.vaultdrop.domain.usecase.GetDownloadsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    getDownloadsUseCase: GetDownloadsUseCase,
    private val deleteDownloadUseCase: DeleteDownloadUseCase,
) : ViewModel() {

    val activeDownloads: StateFlow<List<DownloadItem>> = getDownloadsUseCase
        .activeDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedDownloads: StateFlow<List<DownloadItem>> = getDownloadsUseCase
        .completedDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeCount: StateFlow<Int> = getDownloadsUseCase
        .activeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val completedCount: StateFlow<Int> = getDownloadsUseCase
        .completedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun deleteDownload(id: String) {
        viewModelScope.launch {
            deleteDownloadUseCase(id)
        }
    }
}
