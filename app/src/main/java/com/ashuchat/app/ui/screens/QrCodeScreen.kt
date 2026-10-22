package com.ashuchat.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashuchat.app.ui.components.NeoBrutalButton
import com.ashuchat.app.ui.components.NeoBrutalCard
import com.ashuchat.app.ui.components.NeoBrutalIconButton
import com.ashuchat.app.ui.components.NeoPillBadge
import com.ashuchat.app.ui.components.NeoTopAppBar
import com.ashuchat.app.ui.components.QrCodeView
import com.ashuchat.app.ui.theme.NeoAccentYellow
import com.ashuchat.app.ui.theme.NeoBackground
import com.ashuchat.app.ui.theme.NeoBtnMint
import com.ashuchat.app.ui.theme.NeoCardBorder
import com.ashuchat.app.ui.theme.NeoCardYellow
import com.ashuchat.app.ui.theme.NeoSurfaceElevated
import com.ashuchat.app.ui.theme.NeoTextMuted
import com.ashuchat.app.ui.theme.NeoTextPrimary
import com.ashuchat.app.ui.theme.NeoTextSecondary
import com.ashuchat.app.utils.HapticHelper

@Composable
fun QrCodeScreen(
    roomId: String,
    onNavigateBack: () -> Unit,
    onNavigateToChat: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        containerColor = NeoBackground,
        topBar = {
            NeoTopAppBar(
                title = "YOUR CHAT CODE",
                onNavigateBack = onNavigateBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Main NeoBrutal QR Card
            NeoBrutalCard(
                backgroundColor = NeoCardYellow,
                cornerRadius = 24.dp,
                borderWidth = 2.5.dp,
                shadowOffsetX = 5.dp,
                shadowOffsetY = 5.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    NeoPillBadge(
                        text = "SCAN TO CONNECT",
                        backgroundColor = NeoSurfaceElevated
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Point camera at this screen to instantly connect",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = NeoTextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // QR code framed in high-contrast scanning container (white background for scanner optics)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White)
                            .border(2.5.dp, NeoCardBorder, RoundedCornerShape(20.dp))
                            .padding(14.dp)
                    ) {
                        QrCodeView(
                            content = roomId,
                            sizeDp = 210
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Room Code Box with Copy Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(NeoSurfaceElevated)
                            .border(2.dp, NeoCardBorder, RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "ROOM CODE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = NeoTextMuted
                            )
                            Text(
                                text = roomId,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                color = NeoTextPrimary
                            )
                        }

                        NeoBrutalIconButton(
                            icon = Icons.Default.ContentCopy,
                            contentDescription = "Copy Code",
                            backgroundColor = NeoAccentYellow,
                            tint = NeoBackground,
                            size = 40.dp,
                            onClick = {
                                HapticHelper.playClick(context)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Chat Code", roomId)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Code copied to clipboard!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Open Chat Button
            NeoBrutalButton(
                text = "OPEN CHAT",
                icon = Icons.AutoMirrored.Filled.Chat,
                backgroundColor = NeoAccentYellow,
                textColor = NeoBackground,
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToChat
            )

            Spacer(modifier = Modifier.height(14.dp))

            NeoPillBadge(
                text = "⚡ P2P ENCRYPTED • NO PHONE NUMBER NEEDED",
                backgroundColor = NeoSurfaceElevated
            )
        }
    }
}
