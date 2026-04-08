package com.adityaprasad.vaultdrop.domain.usecase

import com.adityaprasad.vaultdrop.data.repository.DownloadRepository
import com.adityaprasad.vaultdrop.domain.model.DownloadItem
import com.adityaprasad.vaultdrop.domain.model.DownloadStatus
import com.adityaprasad.vaultdrop.domain.model.Platform
import java.util.UUID
import javax.inject.Inject

class StartDownloadUseCase @Inject constructor(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(
        url: String,
        quality: String = "720p"
    ): Result<DownloadItem> {
        val platform = Platform.detect(url)
        if (platform == Platform.UNSUPPORTED) {
            return Result.failure(IllegalArgumentException("Unsupported URL"))
        }

        val item = DownloadItem(
            id = UUID.randomUUID().toString(),
            url = url,
            title = "Fetching title...",
            platform = platform,
            status = DownloadStatus.QUEUED,
            createdAt = System.currentTimeMillis()
        )

        repository.insertDownload(item)
        return Result.success(item)
    }
}
