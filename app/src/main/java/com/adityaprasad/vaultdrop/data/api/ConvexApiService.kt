package com.adityaprasad.vaultdrop.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ConvexApiService {

    @POST("auth/requestOTP")
    suspend fun requestOtp(@Body request: RequestOtpRequest): Response<RequestOtpResponse>

    @POST("auth/verifyOTP")
    suspend fun verifyOtp(@Body request: VerifyOtpRequest): Response<VerifyOtpResponse>

    @POST("auth/registerPassword")
    suspend fun registerPassword(@Body request: RegisterPasswordRequest): Response<RegisterPasswordResponse>

    @POST("auth/loginPassword")
    suspend fun loginPassword(@Body request: LoginPasswordRequest): Response<LoginPasswordResponse>

    @POST("auth/resetPassword")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): Response<ResetPasswordResponse>

    @POST("bookmarks/sync")
    suspend fun syncBookmarks(@Body request: SyncBookmarksRequest): Response<SyncBookmarksResponse>

    @POST("bookmarks/get")
    suspend fun getBookmarks(@Body request: GetBookmarksRequest): Response<GetBookmarksResponse>

    @POST("bookmarks/delete")
    suspend fun deleteBookmark(@Body request: DeleteBookmarkRequest): Response<DeleteBookmarkResponse>

    @POST("bookmarks/deleteAll")
    suspend fun deleteAllBookmarks(@Body request: DeleteAllBookmarksRequest): Response<DeleteAllBookmarksResponse>
}
