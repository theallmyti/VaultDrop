package com.adityaprasad.vaultdrop.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE username = :username ORDER BY createdAt DESC")
    fun getBookmarksByUsername(username: String): Flow<List<BookmarkEntity>>

    @Query("""
        SELECT * FROM bookmarks 
        WHERE comment LIKE '%' || :query || '%' 
           OR username LIKE '%' || :query || '%'
           OR url LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
    """)
    fun searchBookmarks(query: String): Flow<List<BookmarkEntity>>

    @Query("SELECT DISTINCT username FROM bookmarks WHERE username != '' ORDER BY username ASC")
    fun getAllUsernames(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM bookmarks")
    fun getBookmarkCount(): Flow<Int>

    @Query("SELECT * FROM bookmarks WHERE id = :id")
    suspend fun getBookmarkById(id: String): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM bookmarks")
    suspend fun deleteAll()

    @Query("UPDATE bookmarks SET comment = :comment WHERE id = :id")
    suspend fun updateComment(id: String, comment: String)

    @Query("UPDATE bookmarks SET thumbnailUrl = :thumbnailUrl WHERE id = :id")
    suspend fun updateThumbnailUrl(id: String, thumbnailUrl: String)

    @Query("UPDATE bookmarks SET url = :url WHERE id = :id")
    suspend fun updateUrl(id: String, url: String)
}
