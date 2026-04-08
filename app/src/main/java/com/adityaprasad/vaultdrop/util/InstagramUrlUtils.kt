package com.adityaprasad.vaultdrop.util

object InstagramUrlUtils {

    private val canonicalPattern = Regex("/((?:p|reel|reels|tv))/([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE)

    fun normalize(url: String): String {
        val uri = runCatching { android.net.Uri.parse(url) }.getOrNull() ?: return url
        val host = uri.host.orEmpty().lowercase()
        if (!host.contains("instagram.com")) return url

        val match = canonicalPattern.find(uri.path.orEmpty()) ?: return stripIgshOnly(uri)
        val type = match.groupValues[1].lowercase()
        val shortcode = match.groupValues[2]

        // Normalize reel(s) to /reel/ and strip share-tracking params.
        val normalizedType = if (type == "reels") "reel" else type
        return "https://www.instagram.com/$normalizedType/$shortcode/"
    }

    private fun stripIgshOnly(uri: android.net.Uri): String {
        if (!uri.isHierarchical) return uri.toString()

        val builder = uri.buildUpon().clearQuery()
        val keptParams = uri.queryParameterNames
            .filter { it.lowercase() != "igsh" }

        for (name in keptParams) {
            uri.getQueryParameters(name).forEach { value ->
                builder.appendQueryParameter(name, value)
            }
        }

        val rebuilt = builder.build().toString()
        return rebuilt.removeSuffix("?")
    }
}