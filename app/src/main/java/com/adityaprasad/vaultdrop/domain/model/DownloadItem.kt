package com.adityaprasad.vaultdrop.domain.model

data class DownloadItem(
    val id: String,
    val url: String,
    val title: String,
    val platform: Platform,
    val status: DownloadStatus,
    val username: String? = null,
    val filePath: String? = null,
    val thumbnailPath: String? = null,
    val fileSize: Long = 0L,
    val durationMs: Long = 0L,
    val progressPercent: Int = 0,
    val speedBps: Long = 0L,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
) {
    val formattedSize: String
        get() {
            if (fileSize <= 0) return ""
            val kb = fileSize / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> "%.1f GB".format(gb)
                mb >= 1.0 -> "%.1f MB".format(mb)
                else -> "%.0f KB".format(kb)
            }
        }

    val formattedDuration: String
        get() {
            if (durationMs <= 0) return ""
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }

    val formattedSpeed: String
        get() {
            if (speedBps <= 0) return ""
            val kbps = speedBps / 1024.0
            val mbps = kbps / 1024.0
            return when {
                mbps >= 1.0 -> "%.1f MB".format(mbps)
                else -> "%.0f KB".format(kbps)
            }
        }

    val sourceDomain: String
        get() = when (platform) {
            Platform.INSTAGRAM -> "instagram.com"
            Platform.YOUTUBE -> "youtube.com"
            Platform.UNSUPPORTED -> "unknown"
        }
}
