package com.adityaprasad.vaultdrop.util

import android.content.Context

object InstagramSessionManager {
    private const val PREFS = "vaultdrop_prefs"
    private const val KEY_COOKIE_HEADER = "instagram_cookie_header"
    private const val KEY_UPDATED_AT = "instagram_session_updated_at"

    fun getCookieHeader(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_COOKIE_HEADER, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun hasSession(context: Context): Boolean {
        return !getCookieHeader(context).isNullOrBlank()
    }

    fun saveCookieHeader(context: Context, cookieHeader: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_COOKIE_HEADER, cookieHeader)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun clearSession(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_COOKIE_HEADER)
            .remove(KEY_UPDATED_AT)
            .apply()
    }
}