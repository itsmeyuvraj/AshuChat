package com.ashuchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashuchat.app.data.repository.PreferencesRepository
import com.ashuchat.app.ui.components.QrCodeView
import com.ashuchat.app.ui.theme.BrandAccent
import com.ashuchat.app.ui.theme.BrandDark
import com.ashuchat.app.ui.theme.BrandLight
import com.ashuchat.app.ui.theme.BrandPrimary
import com.ashuchat.app.ui.theme.MidnightBg
import com.ashuchat.app.ui.theme.SurfaceBorder
import com.ashuchat.app.ui.theme.SurfaceCard
import com.ashuchat.app.ui.theme.SurfaceElevated
import com.ashuchat.app.ui.theme.TextMuted
import com.ashuchat.app.ui.theme.TextPrimary
import com.ashuchat.app.ui.theme.TextSecondary
import kotlin.random.Random

@Composable
fun PairingScreen(
    prefs: PreferencesRepository,
    onNavigateToChat: () -> Unit,
    onNavigateToScanner: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    var myName by remember { mutableStateOf(prefs.myName) }
    var roomId by remember {
        mutableStateOf(
            if (prefs.roomId.isNotBlank()) prefs.roomId else generateRandomRoomId()
        )
    }

    var showQrCode by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBg)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(BrandPrimary.copy(alpha = 0.15f))
                        .border(1.dp, BrandPrimary.copy(alpha = 0.35f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Logo",
                        tint = BrandLight,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "AshuChat",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Encrypted Peer-to-Peer",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = BrandAccent
                    )
                }
            }

            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(SurfaceElevated)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Name setup card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceElevated)
                .border(1.dp, SurfaceBorder, RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Text(
                text = "Profile Identity",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = BrandLight
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Your display name for direct peer sessions",
                fontSize = 12.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = myName,
                onValueChange = {
                    myName = it
                    prefs.myName = it
                },
                label = { Text("Your Name / Nickname", fontSize = 12.sp) },
                singleLine = true,
                colors = outlinedColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Pairing Room Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceElevated)
                .border(1.dp, SurfaceBorder, RoundedCornerShape(20.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Session Pairing",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Scan this QR code from your peer device or enter matching Room ID",
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Room ID field with refresh button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = roomId,
                    onValueChange = {
                        roomId = it.uppercase()
                        prefs.roomId = it.uppercase()
                    },
                    label = { Text("Room Code") },
                    singleLine = true,
                    colors = outlinedColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        val newId = generateRandomRoomId()
                        roomId = newId
                        prefs.roomId = newId
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceCard)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "New Code",
                        tint = BrandAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // QR Code view
            if (showQrCode && roomId.isNotBlank()) {
                QrCodeView(
                    content = roomId,
                    sizeDp = 200
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Room: $roomId",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandLight
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scan QR Button
            Button(
                onClick = onNavigateToScanner,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(1.dp, BrandAccent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scan QR",
                    tint = BrandAccent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Scan Peer QR Code",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Enter Chat Action Button
        Button(
            onClick = {
                prefs.myName = myName.trim().ifEmpty { "You" }
                prefs.roomId = roomId.trim()
                onNavigateToChat()
            },
            enabled = roomId.isNotBlank(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                disabledContainerColor = SurfaceCard
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(
                    Brush.horizontalGradient(listOf(BrandPrimary, BrandDark)),
                    RoundedCornerShape(16.dp)
                )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Start Secure Session",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "🔒 Direct WebRTC P2P • Zero Central Data Storage • End-to-End Encrypted",
            fontSize = 11.sp,
            color = TextMuted,
            textAlign = TextAlign.Center
        )
    }
}

private val RoseLightColor = BrandLight

@Composable
private fun outlinedColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = BrandPrimary,
    unfocusedBorderColor = SurfaceBorder,
    focusedLabelColor = BrandPrimary,
    unfocusedLabelColor = TextMuted,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = BrandPrimary
)

private fun generateRandomRoomId(): String {
    val num = Random.nextInt(100, 999)
    return "P2P-$num"
}
