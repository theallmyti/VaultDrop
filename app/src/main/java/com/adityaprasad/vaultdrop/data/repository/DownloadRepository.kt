package com.adityaprasad.vaultdrop.data.repository

import com.adityaprasad.vaultdrop.data.db.DownloadDao
import com.adityaprasad.vaultdrop.data.db.DownloadEntity
import com.adityaprasad.vaultdrop.domain.model.DownloadItem
import com.adityaprasad.vaultdrop.domain.model.DownloadStatus
import com.adityaprasad.vaultdrop.domain.model.Platform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val dao: DownloadDao
) {

    fun getAllDownloads(): Flow<List<DownloadItem>> =
        dao.getAllDownloads().map { list -> list.map { it.toDomainModel() } }

    suspend fun getAllDownloadsSync(): List<DownloadItem> =
        dao.getAllDownloadsSync().map { it.toDomainModel() }

    fun getActiveDownloads(): Flow<List<DownloadItem>> =
        dao.getActiveDownloads().map { list -> list.map { it.toDomainModel() } }

    fun getCompletedDownloads(): Flow<List<DownloadItem>> =
        dao.getCompletedDownloads().map { list -> list.map { it.toDomainModel() } }

    fun searchCompletedDownloads(query: String): Flow<List<DownloadItem>> =
        dao.searchCompletedDownloads(query).map { list -> list.map { it.toDomainModel() } }

    fun getCompletedUsernames(): Flow<List<String>> = dao.getCompletedUsernames()

    fun getActiveCount(): Flow<Int> = dao.getActiveCount()

    fun getCompletedCount(): Flow<Int> = dao.getCompletedCount()

    suspend fun getDownloadById(id: String): DownloadItem? =
        dao.getDownloadById(id)?.toDomainModel()

    suspend fun insertDownload(item: DownloadItem) {
        dao.insertDownload(item.toEntity())
    }

    suspend fun updateProgress(id: String, status: DownloadStatus, progress: Int, speed: Long) {
        dao.updateProgress(id, status.name, progress, speed)
    }

    suspend fun markCompleted(id: String, filePath: String, fileSize: Long, durationMs: Long) {
        dao.markCompleted(id, filePath, fileSize, durationMs, System.currentTimeMillis())
    }

    suspend fun markFailed(id: String, error: String) {
        dao.markFailed(id, error)
    }

    suspend fun deleteDownload(id: String) {
        dao.deleteById(id)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }

    suspend fun updateTitle(id: String, title: String) {
        dao.updateTitle(id, title)
    }

    suspend fun updateUsername(id: String, username: String) {
        val formatted = if (username.startsWith("@")) username.substring(1) else username
        dao.updateUsername(id, formatted)
    }

    suspend fun updateThumbnail(id: String, thumbnailPath: String) {
        dao.updateThumbnail(id, thumbnailPath)
    }

    private fun DownloadEntity.toDomainModel(): DownloadItem = DownloadItem(
        id = id,
        url = url,
        title = title,
        platform = try { Platform.valueOf(platform) } catch (_: Exception) { Platform.UNSUPPORTED },
        status = try { DownloadStatus.valueOf(status) } catch (_: Exception) { DownloadStatus.FAILED },
        username = username,
        filePath = filePath,
        thumbnailPath = thumbnailPath,
        fileSize = fileSize,
        durationMs = durationMs,
        progressPercent = progressPercent,
        speedBps = speedBps,
        errorMessage = errorMessage,
        createdAt = createdAt,
        completedAt = completedAt
    )

    private fun DownloadItem.toEntity(): DownloadEntity = DownloadEntity(
        id = id,
        url = url,
        title = title,
        platform = platform.name,
        status = status.name,
        username = username,
        filePath = filePath,
        thumbnailPath = thumbnailPath,
        fileSize = fileSize,
        durationMs = durationMs,
        progressPercent = progressPercent,
        speedBps = speedBps,
        errorMessage = errorMessage,
        createdAt = createdAt,
        completedAt = completedAt
    )
}
