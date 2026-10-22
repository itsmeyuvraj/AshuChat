package com.ashuchat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashuchat.app.ui.theme.NeoAccentYellow
import com.ashuchat.app.ui.theme.NeoBackground
import com.ashuchat.app.ui.theme.NeoCardBorder
import com.ashuchat.app.ui.theme.NeoCardYellow
import com.ashuchat.app.ui.theme.NeoTextPrimary
import com.ashuchat.app.ui.theme.NeoTextSecondary

@Composable
fun BiometricLockOverlay(
    onUnlockClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(NeoBackground)
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(NeoCardYellow)
                    .border(3.dp, NeoCardBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "App Locked",
                    tint = NeoAccentYellow,
                    modifier = Modifier.size(50.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "ASHUCHAT LOCKED",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = NeoTextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Biometric authentication is required to access your private conversations",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = NeoTextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(36.dp))

            NeoBrutalButton(
                text = "UNLOCK WITH BIOMETRICS",
                icon = Icons.Default.Fingerprint,
                backgroundColor = NeoAccentYellow,
                textColor = NeoBackground,
                onClick = onUnlockClick
            )
        }
    }
}
