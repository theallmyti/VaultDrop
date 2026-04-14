package com.adityaprasad.vaultdrop.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DownloadEntity::class, BookmarkEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun bookmarkDao(): BookmarkDao
}
