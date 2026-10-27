package com.ashuchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashuchat.app.ui.components.NeoBrutalButton
import com.ashuchat.app.ui.components.NeoBrutalCard
import com.ashuchat.app.ui.components.NeoBrutalIconButton
import com.ashuchat.app.ui.components.NeoTopAppBar
import com.ashuchat.app.ui.theme.NeoBackground
import com.ashuchat.app.ui.theme.NeoBlack
import com.ashuchat.app.ui.theme.NeoBtnBlue
import com.ashuchat.app.ui.theme.NeoBtnMint
import com.ashuchat.app.ui.theme.NeoBtnPeach
import com.ashuchat.app.ui.theme.NeoCardLavender
import com.ashuchat.app.ui.theme.NeoCardMint
import com.ashuchat.app.ui.theme.NeoSurface
import com.ashuchat.app.ui.theme.NeoTextDark
import com.ashuchat.app.ui.theme.NeoTextMuted
import com.ashuchat.app.ui.theme.OfflineRed
import com.ashuchat.app.ui.theme.OnlineGreen
import com.ashuchat.app.ui.viewmodel.ChatViewModel
import com.ashuchat.app.webrtc.SignalingClient

private const val DEFAULT_SIGNALING_URL =
    "https://ashuchat-51b3e-default-rtdb.asia-southeast1.firebasedatabase.app"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayScreenPaper(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit
) {
    val prefs = viewModel.prefs
    var signalingUrl by remember { mutableStateOf(prefs.signalingUrl) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    Scaffold(
        containerColor = NeoBackground,
        topBar = {
            NeoTopAppBar(
                title = "Relay & Signaling",
                subtitle = "WebRTC Handshake Settings",
                onNavigateBack = onNavigateBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(NeoBackground)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Header Info Card (Pastel Mint NeoBrutalCard)
            NeoBrutalCard(
                backgroundColor = NeoCardMint,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🌐", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Signaling Server URL",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = NeoTextDark
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Used exclusively for exchanging WebRTC connection handshakes (SDP & ICE). Messages are never relayed through this server once connected.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = NeoTextDark.copy(alpha = 0.85f),
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = signalingUrl,
                        onValueChange = {
                            signalingUrl = it
                            prefs.signalingUrl = it
                            testResult = null
                        },
                        textStyle = TextStyle(
                            color = NeoBlack,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        label = {
                            Text(
                                "Server URL",
                                fontWeight = FontWeight.Bold,
                                color = NeoBlack
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeoBlack,
                            unfocusedBorderColor = NeoBlack,
                            focusedContainerColor = NeoSurface,
                            unfocusedContainerColor = NeoSurface,
                            focusedTextColor = NeoBlack,
                            unfocusedTextColor = NeoBlack,
                            focusedLabelColor = NeoBlack,
                            unfocusedLabelColor = NeoBlack.copy(alpha = 0.65f),
                            cursorColor = NeoBlack
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, NeoBlack, RoundedCornerShape(12.dp))
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        NeoBrutalButton(
                            onClick = {
                                isTestingConnection = true
                                testResult = null
                                SignalingClient.testConnection(signalingUrl) { success, msg ->
                                    isTestingConnection = false
                                    testResult = Pair(success, msg)
                                }
                            },
                            enabled = !isTestingConnection,
                            backgroundColor = NeoBtnBlue,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isTestingConnection) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = NeoBlack,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Testing...", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NeoBlack)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.NetworkCheck,
                                    contentDescription = null,
                                    tint = NeoBlack,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test Server", fontSize = 13.sp, fontWeight = FontWeight.Black, color = NeoBlack)
                            }
                        }

                        NeoBrutalIconButton(
                            onClick = {
                                signalingUrl = DEFAULT_SIGNALING_URL
                                prefs.signalingUrl = signalingUrl
                                testResult = null
                            },
                            backgroundColor = NeoBtnPeach,
                            size = 48.dp
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset",
                                tint = NeoBlack,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Test Connection Result feedback
                    testResult?.let { (success, msg) ->
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(NeoSurface)
                                .border(2.dp, if (success) OnlineGreen else OfflineRed, RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (success) OnlineGreen else OfflineRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = msg,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (success) OnlineGreen else OfflineRed
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Explanation / How it works Card (Pastel Lavender NeoBrutalCard)
            NeoBrutalCard(
                backgroundColor = NeoCardLavender,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🛡️", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "How WebRTC P2P Works",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = NeoTextDark
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "1. Handshake Phase: Both phones send encrypted connection coordinates (SDP offer/answer and ICE candidate addresses) via the signaling URL.\n\n" +
                                "2. Direct P2P Channel: Once the coordinates are exchanged, phones establish a direct, encrypted DTLS-SRTP channel to each other.\n\n" +
                                "3. Zero Server Storage: After the initial handshake, communication does not touch any server. Chats and files pass directly device-to-device.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = NeoTextDark.copy(alpha = 0.9f),
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
