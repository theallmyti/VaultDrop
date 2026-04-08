package com.adityaprasad.vaultdrop.domain.model

enum class Platform {
    INSTAGRAM,
    YOUTUBE,
    UNSUPPORTED;

    companion object {
        fun detect(url: String): Platform {
            return when {
                url.contains("instagram.com") -> INSTAGRAM
                url.contains("youtu.be") || url.contains("youtube.com") -> YOUTUBE
                else -> UNSUPPORTED
            }
        }
    }
}
