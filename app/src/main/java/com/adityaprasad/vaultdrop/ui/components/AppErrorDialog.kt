package com.adityaprasad.vaultdrop.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.adityaprasad.vaultdrop.ui.theme.BgElevated
import com.adityaprasad.vaultdrop.ui.theme.DmSans
import com.adityaprasad.vaultdrop.ui.theme.TextPrimary
import com.adityaprasad.vaultdrop.ui.theme.TextSecondary

@Composable
fun AppErrorDialog(
    visible: Boolean,
    message: String,
    onDismiss: () -> Unit,
    onSecondaryAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Something went wrong",
                fontFamily = DmSans,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
        },
        text = {
            Text(
                text = message,
                fontFamily = DmSans,
                fontWeight = FontWeight.Normal,
                color = TextSecondary,
            )
        },
        confirmButton = {
            if (onSecondaryAction != null && secondaryActionLabel != null) {
                TextButton(onClick = {
                    onSecondaryAction()
                    onDismiss()
                }) {
                    Text(
                        text = secondaryActionLabel,
                        fontFamily = DmSans,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "OK",
                        fontFamily = DmSans,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                }
            }
        },
        dismissButton = {
            if (onSecondaryAction != null && secondaryActionLabel != null) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "OK",
                        fontFamily = DmSans,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                    )
                }
            }
        },
        containerColor = BgElevated,
    )
}