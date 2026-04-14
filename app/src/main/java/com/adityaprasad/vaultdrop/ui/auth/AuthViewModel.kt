package com.adityaprasad.vaultdrop.ui.auth

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adityaprasad.vaultdrop.data.api.ConvexApiService
import com.adityaprasad.vaultdrop.data.api.LoginPasswordRequest
import com.adityaprasad.vaultdrop.data.api.RequestOtpRequest
import com.adityaprasad.vaultdrop.data.api.RegisterPasswordRequest
import com.adityaprasad.vaultdrop.data.api.ResetPasswordRequest
import com.adityaprasad.vaultdrop.data.api.VerifyOtpRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.Response
import javax.inject.Inject

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class OtpSent(val type: String) : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    application: Application,
    private val api: ConvexApiService
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("vaultdrop_prefs", Context.MODE_PRIVATE)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var currentEmail: String = ""
    private var currentOtpType: String = "verify"

    private fun saveSession(token: String, email: String) {
        prefs.edit()
            .putString("auth_token", token)
            .putString("auth_email", email)
            .apply()
    }

    private fun parseApiError(response: Response<*>, fallback: String): String {
        val bodyText = runCatching { response.errorBody()?.string() }.getOrNull().orEmpty()
        if (bodyText.isBlank()) return fallback

        return runCatching {
            val json = JSONObject(bodyText)
            val error = json.optString("error").trim()
            val message = json.optString("message").trim()
            when {
                error.isNotBlank() -> error
                message.isNotBlank() -> message
                else -> fallback
            }
        }.getOrElse { fallback }
    }

    private fun formatOtpError(raw: String): String {
        val lower = raw.lowercase()
        return when {
            lower.contains("expired") -> "This OTP has expired. Tap Resend OTP to get a new code."
            lower.contains("invalid otp") || lower.contains("invalid or expired otp") -> "That OTP is incorrect. Please check the code and try again."
            lower.contains("6 digits") -> "OTP must be exactly 6 digits."
            else -> raw
        }
    }

    private fun requestOtpInternal(email: String, type: String) {
        currentEmail = email
        currentOtpType = type
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val response = api.requestOtp(RequestOtpRequest(email = email, type = type))
                if (response.isSuccessful && response.body()?.success == true) {
                    _authState.value = AuthState.OtpSent(type)
                } else {
                    _authState.value = AuthState.Error(
                        response.body()?.message ?: parseApiError(response, "Failed to send OTP")
                    )
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Network error")
            }
        }
    }

    fun requestOtp(email: String) {
        requestOtpInternal(email, "verify")
    }

    fun requestPasswordResetOtp(email: String) {
        requestOtpInternal(email, "reset")
    }

    fun registerWithPassword(email: String, password: String) {
        if (password.trim().length < 8) {
            _authState.value = AuthState.Error("Password must be at least 8 characters")
            return
        }

        currentEmail = email
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val response = api.registerPassword(RegisterPasswordRequest(email = email, password = password))
                if (response.isSuccessful && response.body()?.success == true) {
                    requestOtpInternal(email, "verify")
                } else {
                    _authState.value = AuthState.Error(
                        response.body()?.error
                            ?: response.body()?.message
                            ?: parseApiError(response, "Failed to create account")
                    )
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Network error")
            }
        }
    }

    fun loginWithPassword(email: String, password: String) {
        currentEmail = email
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val response = api.loginPassword(LoginPasswordRequest(email = email, password = password))
                if (response.isSuccessful && response.body()?.success == true) {
                    val token = response.body()?.token
                    if (token != null) {
                        saveSession(token, email)
                        _authState.value = AuthState.Success
                    } else {
                        _authState.value = AuthState.Error("Token missing in response")
                    }
                } else {
                    _authState.value = AuthState.Error(
                        response.body()?.error ?: parseApiError(response, "Invalid email or password")
                    )
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Network error")
            }
        }
    }

    fun verifyOtp(otp: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val response = api.verifyOtp(VerifyOtpRequest(email = currentEmail, otp = otp, type = currentOtpType))
                if (response.isSuccessful && response.body()?.success == true) {
                    val token = response.body()?.token
                    if (token != null) {
                        saveSession(token, currentEmail)
                        _authState.value = AuthState.Success
                    } else {
                        _authState.value = AuthState.Error("Token missing in response")
                    }
                } else {
                    _authState.value = AuthState.Error(
                        formatOtpError(response.body()?.error ?: parseApiError(response, "Invalid OTP"))
                    )
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(formatOtpError(e.message ?: "Network error"))
            }
        }
    }

    fun resetPasswordWithOtp(otp: String, newPassword: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val response = api.resetPassword(
                    ResetPasswordRequest(
                        email = currentEmail,
                        otp = otp,
                        newPassword = newPassword
                    )
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val token = response.body()?.token
                    if (token != null) {
                        saveSession(token, currentEmail)
                        _authState.value = AuthState.Success
                    } else {
                        _authState.value = AuthState.Error("Token missing in response")
                    }
                } else {
                    _authState.value = AuthState.Error(
                        formatOtpError(response.body()?.error ?: parseApiError(response, "Failed to reset password"))
                    )
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(formatOtpError(e.message ?: "Network error"))
            }
        }
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }
}
