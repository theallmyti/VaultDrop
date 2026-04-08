package com.adityaprasad.vaultdrop.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val platform: String,       // "INSTAGRAM" | "YOUTUBE"
    val status: String,         // "QUEUED" | "ACTIVE" | "DONE" | "FAILED"
    val username: String?,
    val filePath: String?,
    val thumbnailPath: String?,
    val fileSize: Long,
    val durationMs: Long,
    val progressPercent: Int,
    val speedBps: Long,
    val errorMessage: String?,
    val createdAt: Long,
    val completedAt: Long?
)
