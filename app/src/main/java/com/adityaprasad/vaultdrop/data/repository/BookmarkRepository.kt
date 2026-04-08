package com.adityaprasad.vaultdrop.data.repository

import com.adityaprasad.vaultdrop.data.db.BookmarkDao
import com.adityaprasad.vaultdrop.data.db.BookmarkEntity
import com.adityaprasad.vaultdrop.domain.model.BookmarkItem
import com.adityaprasad.vaultdrop.domain.model.Platform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepository @Inject constructor(
    private val dao: BookmarkDao
) {

    fun getAllBookmarks(): Flow<List<BookmarkItem>> =
        dao.getAllBookmarks().map { list -> list.map { it.toDomainModel() } }

    fun getBookmarksByUsername(username: String): Flow<List<BookmarkItem>> =
        dao.getBookmarksByUsername(username).map { list -> list.map { it.toDomainModel() } }

    fun searchBookmarks(query: String): Flow<List<BookmarkItem>> =
        dao.searchBookmarks(query).map { list -> list.map { it.toDomainModel() } }

    fun getAllUsernames(): Flow<List<String>> = dao.getAllUsernames()

    fun getBookmarkCount(): Flow<Int> = dao.getBookmarkCount()

    suspend fun insertBookmark(item: BookmarkItem) {
        dao.insertBookmark(item.toEntity())
    }

    suspend fun deleteBookmark(id: String) {
        dao.deleteById(id)
    }

    suspend fun deleteAllBookmarks() {
        dao.deleteAll()
    }

    suspend fun updateComment(id: String, comment: String) {
        dao.updateComment(id, comment)
    }

    suspend fun updateThumbnailUrl(id: String, thumbnailUrl: String) {
        dao.updateThumbnailUrl(id, thumbnailUrl)
    }

    suspend fun updateUrl(id: String, url: String) {
        dao.updateUrl(id, url)
    }

    /**
     * Extracts the Instagram username from a URL.
     * Handles formats like:
     *  - https://www.instagram.com/username/reel/ABC123/
     *  - https://www.instagram.com/p/ABC123/  (no username in URL)
     *  - https://www.instagram.com/username/p/ABC123/
     */
    fun extractUsername(url: String): String {
        // Pattern: instagram.com/username/reel/... or instagram.com/username/p/...
        val withUsername = Regex("instagram\\.com/([^/]+)/(?:p|reel|tv|reels)/")
        withUsername.find(url)?.groupValues?.get(1)?.let { username ->
            // Filter out common non-username segments
            if (username !in listOf("www", "m", "stories", "explore")) {
                return "@$username"
            }
        }
        // No username in URL
        return ""
    }

    private fun BookmarkEntity.toDomainModel(): BookmarkItem = BookmarkItem(
        id = id,
        url = url,
        username = username,
        comment = comment,
        platform = try { Platform.valueOf(platform) } catch (_: Exception) { Platform.UNSUPPORTED },
        thumbnailUrl = thumbnailUrl,
        createdAt = createdAt
    )

    private fun BookmarkItem.toEntity(): BookmarkEntity = BookmarkEntity(
        id = id,
        url = url,
        username = username,
        comment = comment,
        platform = platform.name,
        thumbnailUrl = thumbnailUrl,
        createdAt = createdAt
    )
}
