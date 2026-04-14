package com.adityaprasad.vaultdrop.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val url: String,
    val username: String,        // Instagram username extracted from URL
    val comment: String,         // User's personal note
    val platform: String,        // "INSTAGRAM" | "YOUTUBE"
    val thumbnailUrl: String?,   // Optional thumbnail URL
    val createdAt: Long,
    val tagsCsv: String = ""     // Comma-separated tags
)
