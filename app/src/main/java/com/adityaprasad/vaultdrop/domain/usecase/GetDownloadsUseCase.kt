package com.adityaprasad.vaultdrop.domain.usecase

import com.adityaprasad.vaultdrop.data.repository.DownloadRepository
import com.adityaprasad.vaultdrop.domain.model.DownloadItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetDownloadsUseCase @Inject constructor(
    private val repository: DownloadRepository
) {
    fun allDownloads(): Flow<List<DownloadItem>> = repository.getAllDownloads()

    fun activeDownloads(): Flow<List<DownloadItem>> = repository.getActiveDownloads()

    fun completedDownloads(): Flow<List<DownloadItem>> = repository.getCompletedDownloads()

    fun activeCount(): Flow<Int> = repository.getActiveCount()

    fun completedCount(): Flow<Int> = repository.getCompletedCount()
}
