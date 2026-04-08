package com.adityaprasad.vaultdrop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adityaprasad.vaultdrop.ui.theme.AccentPrimary
import com.adityaprasad.vaultdrop.ui.theme.BgPrimary
import com.adityaprasad.vaultdrop.ui.theme.DmSans
import com.adityaprasad.vaultdrop.ui.theme.TextPrimary
import com.adityaprasad.vaultdrop.ui.theme.TextSecondary

@Composable
fun AppLockScreen(
    onUnlockRequested: () -> Unit
) {
    // Automatically request unlock when screen is shown
    LaunchedEffect(Unit) {
        onUnlockRequested()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "Locked",
                tint = AccentPrimary,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "VaultDrop is Locked",
                fontFamily = DmSans,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Unlock to view your private media",
                fontFamily = DmSans,
                fontWeight = FontWeight.Light,
                fontSize = 14.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onUnlockRequested,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
            ) {
                Text(
                    text = "Tap to Unlock",
                    fontFamily = DmSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = BgPrimary
                )
            }
        }
    }
}
