package com.adityaprasad.vaultdrop.domain.model

data class BookmarkItem(
    val id: String,
    val url: String,
    val username: String,
    val comment: String,
    val platform: Platform,
    val thumbnailUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val sourceDomain: String
        get() = when (platform) {
            Platform.INSTAGRAM -> "instagram.com"
            Platform.YOUTUBE -> "youtube.com"
            Platform.UNSUPPORTED -> "unknown"
        }

    val shortcode: String?
        get() {
            val pattern = Regex("/(?:p|reel|tv|reels)/([A-Za-z0-9_-]+)")
            return pattern.find(url)?.groupValues?.get(1)
        }

    val formattedDate: String
        get() {
            val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US)
            return sdf.format(java.util.Date(createdAt))
        }
}
