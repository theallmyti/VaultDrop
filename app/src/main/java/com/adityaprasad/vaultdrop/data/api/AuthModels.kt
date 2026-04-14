package com.adityaprasad.vaultdrop.data.api

data class RequestOtpRequest(val email: String, val type: String = "verify")
data class RequestOtpResponse(val success: Boolean, val message: String? = null)

data class VerifyOtpRequest(val email: String, val otp: String, val type: String = "verify")
data class VerifyOtpResponse(val success: Boolean, val token: String? = null, val error: String? = null)

data class RegisterPasswordRequest(val email: String, val password: String)
data class RegisterPasswordResponse(val success: Boolean, val message: String? = null, val error: String? = null)

data class LoginPasswordRequest(val email: String, val password: String)
data class LoginPasswordResponse(val success: Boolean, val token: String? = null, val error: String? = null)

data class ResetPasswordRequest(val email: String, val otp: String, val newPassword: String)
data class ResetPasswordResponse(val success: Boolean, val token: String? = null, val error: String? = null)

data class SyncBookmarksRequest(val token: String, val bookmarks: List<SyncBookmarkItem>)
data class SyncBookmarksResponse(val success: Boolean, val syncedCount: Int? = null, val error: String? = null)

data class SyncBookmarkItem(
    val bookmarkId: String,
    val url: String,
    val username: String,
    val comment: String,
    val platform: String,
    val thumbnailUrl: String?,
    val createdAt: Long,
    val tags: List<String>
)

data class GetBookmarksRequest(val token: String)
data class GetBookmarksResponse(val bookmarks: List<SyncBookmarkItem>? = null, val error: String? = null)

data class DeleteBookmarkRequest(val token: String, val bookmarkId: String, val url: String? = null)
data class DeleteBookmarkResponse(val success: Boolean, val deletedCount: Int? = null, val error: String? = null)

data class DeleteAllBookmarksRequest(val token: String)
data class DeleteAllBookmarksResponse(val success: Boolean, val deletedCount: Int? = null, val error: String? = null)
