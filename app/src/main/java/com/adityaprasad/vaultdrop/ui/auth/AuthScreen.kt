package com.adityaprasad.vaultdrop.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.adityaprasad.vaultdrop.ui.components.AppErrorDialog
import com.adityaprasad.vaultdrop.ui.theme.*
import kotlinx.coroutines.delay

private enum class AuthIntent {
    SIGN_IN,
    SIGN_UP,
    RESET,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val authState by viewModel.authState.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var intent by remember { mutableStateOf(AuthIntent.SIGN_IN) }
    var isSignUpPage by remember { mutableStateOf(false) }
    var showPasswordVisibility by remember { mutableStateOf(false) }
    var showNewPasswordVisibility by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var hasNoAccountOption by remember { mutableStateOf(false) }
    var isOtpVerificationPage by remember { mutableStateOf(false) }
    var resendCooldownSeconds by remember { mutableStateOf(0) }

    val otpType = (authState as? AuthState.OtpSent)?.type

    LaunchedEffect(authState) {
        val currentState = authState

        if (currentState is AuthState.Success) {
            onSuccess()
        }

        if (currentState is AuthState.Error) {
            val authErrorMessage = currentState.message
            hasNoAccountOption = intent == AuthIntent.SIGN_IN && authErrorMessage.contains("No account found on this email", ignoreCase = true)
            errorMessage = currentState.message
            showErrorDialog = true
        }

        if (currentState is AuthState.OtpSent && currentState.type == "verify" && isSignUpPage) {
            isOtpVerificationPage = true
            if (resendCooldownSeconds <= 0) {
                resendCooldownSeconds = 60
            }
        }
    }

    LaunchedEffect(isOtpVerificationPage, resendCooldownSeconds) {
        if (isOtpVerificationPage && resendCooldownSeconds > 0) {
            delay(1000)
            resendCooldownSeconds -= 1
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Cloud Backup",
            fontFamily = DmSerifDisplay,
            fontSize = 32.sp,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Sign in to securely sync and backup your bookmarks across devices.",
            fontFamily = DmSans,
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(40.dp))

        if (isOtpVerificationPage) {
            // OTP Verification Page
            OtpVerificationPage(
                email = email,
                otp = otp,
                onOtpChange = { otp = it },
                onVerifyClick = { viewModel.verifyOtp(otp) },
                resendCooldownSeconds = resendCooldownSeconds,
                onResendClick = {
                    if (resendCooldownSeconds == 0) {
                        viewModel.requestOtp(email)
                        resendCooldownSeconds = 60
                    }
                },
                onEditClick = {
                    isOtpVerificationPage = false
                    otp = ""
                    resendCooldownSeconds = 0
                    viewModel.resetState()
                }
            )
        } else {
            when (val state = authState) {
                is AuthState.Loading -> {
                    CircularProgressIndicator(color = AccentPrimary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Please wait...", fontFamily = DmSans, color = TextSecondary)
                }
                else -> {
                    AuthInputField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Email Address",
                        keyboardType = KeyboardType.Email
                    )

                Spacer(modifier = Modifier.height(8.dp))
                AuthInputField(
                    value = password,
                    onValueChange = { password = it },
                    label = if (isSignUpPage) "Create Password" else "Password",
                    keyboardType = KeyboardType.Password,
                    isPassword = true,
                    passwordVisible = showPasswordVisibility,
                    onPasswordVisibilityToggle = { showPasswordVisibility = !showPasswordVisibility }
                )

                Spacer(modifier = Modifier.height(14.dp))
                if (isSignUpPage) {
                    Button(
                        onClick = {
                            intent = AuthIntent.SIGN_UP
                            viewModel.registerWithPassword(email, password)
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Create Account", fontFamily = DmSans, fontWeight = FontWeight.Bold, color = BgPrimary)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Already have an account? ",
                            fontFamily = DmSans,
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "Sign in",
                            modifier = Modifier.clickable {
                                isSignUpPage = false
                                intent = AuthIntent.SIGN_IN
                                otp = ""
                                newPassword = ""
                                viewModel.resetState()
                            },
                            fontFamily = DmSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = AccentPrimary
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            intent = AuthIntent.SIGN_IN
                            viewModel.loginWithPassword(email, password)
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Sign In", fontFamily = DmSans, fontWeight = FontWeight.Bold, color = BgPrimary)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Forgot password?",
                        modifier = Modifier.clickable {
                            intent = AuthIntent.RESET
                            viewModel.requestPasswordResetOtp(email)
                        },
                        fontFamily = DmSans,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = AccentPrimary
                    )

                    if (otpType == "reset") {
                        Spacer(modifier = Modifier.height(18.dp))
                        AuthInputField(
                            value = otp,
                            onValueChange = { otp = it },
                            label = "Reset OTP",
                            keyboardType = KeyboardType.Number
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        AuthInputField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = "New Password",
                            keyboardType = KeyboardType.Password,
                            isPassword = true,
                            passwordVisible = showNewPasswordVisibility,
                            onPasswordVisibilityToggle = { showNewPasswordVisibility = !showNewPasswordVisibility }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                intent = AuthIntent.RESET
                                viewModel.resetPasswordWithOtp(otp, newPassword)
                            },
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Reset Password", fontFamily = DmSans, fontWeight = FontWeight.Bold, color = BgPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Don't have an account? ",
                            fontFamily = DmSans,
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "Sign up",
                            modifier = Modifier.clickable {
                                isSignUpPage = true
                                intent = AuthIntent.SIGN_UP
                                otp = ""
                                newPassword = ""
                                viewModel.resetState()
                            },
                            fontFamily = DmSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = AccentPrimary
                        )
                    }
                }
            }
        }
        }

        AppErrorDialog(
            visible = showErrorDialog,
            message = errorMessage,
            onDismiss = {
                showErrorDialog = false
                viewModel.resetState()
            },
            onSecondaryAction = if (hasNoAccountOption) ({
                isSignUpPage = true
                intent = AuthIntent.SIGN_UP
                hasNoAccountOption = false
                viewModel.resetState()
            }) else null,
            secondaryActionLabel = if (hasNoAccountOption) "Sign up" else null
        )
    }
}

@Composable
private fun AuthInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onPasswordVisibilityToggle: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontFamily = DmSans) },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = onPasswordVisibilityToggle) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        tint = TextSecondary
                    )
                }
            }
        } else null,
        modifier = Modifier.fillMaxWidth(0.8f),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentPrimary,
            unfocusedBorderColor = TextTertiary,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedLabelColor = TextSecondary,
            unfocusedLabelColor = TextTertiary,
        )
    )
}

@Composable
private fun OtpVerificationPage(
    email: String,
    otp: String,
    onOtpChange: (String) -> Unit,
    onVerifyClick: () -> Unit,
    resendCooldownSeconds: Int,
    onResendClick: () -> Unit,
    onEditClick: () -> Unit,
) {
    Spacer(modifier = Modifier.height(32.dp))

    // Email Display Section
    Row(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .background(BgSurface, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = email,
            fontFamily = DmSans,
            fontSize = 14.sp,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "Edit",
            fontFamily = DmSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = AccentPrimary,
            modifier = Modifier.clickable { onEditClick() }
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = "Enter OTP",
        fontFamily = DmSans,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = TextSecondary
    )

    Spacer(modifier = Modifier.height(12.dp))

    AuthInputField(
        value = otp,
        onValueChange = onOtpChange,
        label = "Verification OTP",
        keyboardType = KeyboardType.Number
    )

    Spacer(modifier = Modifier.height(14.dp))

    Button(
        onClick = onVerifyClick,
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .height(50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text("Complete Sign Up", fontFamily = DmSans, fontWeight = FontWeight.Bold, color = BgPrimary)
    }

    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = if (resendCooldownSeconds > 0) {
            "Resend OTP in ${resendCooldownSeconds}s"
        } else {
            "Resend OTP"
        },
        modifier = if (resendCooldownSeconds == 0) Modifier.clickable { onResendClick() } else Modifier,
        fontFamily = DmSans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        color = if (resendCooldownSeconds == 0) AccentPrimary else TextSecondary
    )
}

