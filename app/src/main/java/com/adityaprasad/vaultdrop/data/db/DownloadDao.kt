package com.adityaprasad.vaultdrop.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    suspend fun getAllDownloadsSync(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY createdAt DESC")
    fun getDownloadsByStatus(status: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED', 'ACTIVE', 'FAILED') ORDER BY createdAt ASC")
    fun getActiveDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'DONE' ORDER BY completedAt DESC")
    fun getCompletedDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'DONE' AND (title LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%') ORDER BY completedAt DESC")
    fun searchCompletedDownloads(query: String): Flow<List<DownloadEntity>>

    @Query("SELECT DISTINCT username FROM downloads WHERE status = 'DONE' AND username IS NOT NULL AND username != '' ORDER BY username ASC")
    fun getCompletedUsernames(): Flow<List<String>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadById(id: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity)

    @Update
    suspend fun updateDownload(download: DownloadEntity)

    @Delete
    suspend fun deleteDownload(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM downloads WHERE status IN ('QUEUED', 'ACTIVE', 'FAILED')")
    fun getActiveCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'DONE'")
    fun getCompletedCount(): Flow<Int>

    @Query("UPDATE downloads SET status = :status, progressPercent = :progress, speedBps = :speed WHERE id = :id")
    suspend fun updateProgress(id: String, status: String, progress: Int, speed: Long)

    @Query("UPDATE downloads SET status = 'DONE', progressPercent = 100, filePath = :filePath, fileSize = :fileSize, durationMs = :durationMs, completedAt = :completedAt WHERE id = :id")
    suspend fun markCompleted(id: String, filePath: String, fileSize: Long, durationMs: Long, completedAt: Long)

    @Query("UPDATE downloads SET status = 'FAILED', errorMessage = :error WHERE id = :id")
    suspend fun markFailed(id: String, error: String)

    @Query("UPDATE downloads SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: String, title: String)

    @Query("UPDATE downloads SET username = :username WHERE id = :id")
    suspend fun updateUsername(id: String, username: String)

    @Query("UPDATE downloads SET thumbnailPath = :thumbnailPath WHERE id = :id")
    suspend fun updateThumbnail(id: String, thumbnailPath: String)
}
