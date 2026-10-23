package com.ashuchat.app.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
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
import com.ashuchat.app.ui.theme.NeoCardMint
import com.ashuchat.app.ui.theme.NeoCardYellow
import com.ashuchat.app.ui.theme.NeoHeaderYellow
import com.ashuchat.app.ui.theme.NeoTextDark
import com.ashuchat.app.ui.theme.NeoTextMuted
import com.ashuchat.app.ui.viewmodel.ChatViewModel
import kotlin.random.Random

@Composable
fun PairingScreenPaper(
    viewModel: ChatViewModel,
    onNavigateToChat: () -> Unit,
    onNavigateToShowQr: (String) -> Unit,
    onNavigateToScanner: () -> Unit,
    onNavigateBack: (() -> Unit)? = null
) {
    val prefs = viewModel.prefs
    var myName by remember { mutableStateOf(prefs.myName) }
    var roomId by remember {
        mutableStateOf(generateRandomRoomId())
    }
    var partnerName by remember { mutableStateOf("") }

    Scaffold(
        containerColor = NeoBackground,
        topBar = {
            NeoTopAppBar(
                title = "New Chat Room",
                subtitle = "Join or create a direct room",
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
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Section 1: Your Name Card (Pastel Yellow NeoBrutalCard)
            NeoBrutalCard(
                backgroundColor = NeoCardYellow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "👤", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Your Display Handle",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = NeoTextDark
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "The alias visible to your peer in this direct room",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = NeoTextDark.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = myName,
                        onValueChange = {
                            myName = it
                            prefs.myName = it
                        },
                        textStyle = TextStyle(
                            color = NeoBlack,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        placeholder = {
                            Text(
                                "Enter your name",
                                color = NeoBlack.copy(alpha = 0.5f),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeoBlack,
                            unfocusedBorderColor = NeoBlack,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedTextColor = NeoBlack,
                            unfocusedTextColor = NeoBlack,
                            focusedPlaceholderColor = NeoBlack.copy(alpha = 0.5f),
                            unfocusedPlaceholderColor = NeoBlack.copy(alpha = 0.5f),
                            cursorColor = NeoBlack
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, NeoBlack, RoundedCornerShape(12.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 2: Room Code Card (Pastel Mint NeoBrutalCard)
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
                        Text(text = "🔑", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Room Secret Code",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = NeoTextDark
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Share this code or enter partner's code to connect directly",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = NeoTextDark.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Room ID Input with Refresh Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = roomId,
                            onValueChange = {
                                roomId = it.uppercase()
                            },
                            textStyle = TextStyle(
                                color = NeoBlack,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            ),
                            placeholder = {
                                Text(
                                    "e.g. CHAT-123",
                                    color = NeoBlack.copy(alpha = 0.5f),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeoBlack,
                                unfocusedBorderColor = NeoBlack,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedTextColor = NeoBlack,
                                unfocusedTextColor = NeoBlack,
                                focusedPlaceholderColor = NeoBlack.copy(alpha = 0.5f),
                                unfocusedPlaceholderColor = NeoBlack.copy(alpha = 0.5f),
                                cursorColor = NeoBlack
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .border(2.dp, NeoBlack, RoundedCornerShape(12.dp))
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        NeoBrutalIconButton(
                            onClick = {
                                roomId = generateRandomRoomId()
                            },
                            backgroundColor = NeoBtnBlue,
                            size = 52.dp
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Generate New Code",
                                tint = NeoBlack,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Peer Name (Optional)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeoTextDark
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = partnerName,
                        onValueChange = { partnerName = it },
                        textStyle = TextStyle(
                            color = NeoBlack,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        placeholder = {
                            Text(
                                "e.g. Ashu (can be changed later)",
                                color = NeoBlack.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeoBlack,
                            unfocusedBorderColor = NeoBlack,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedTextColor = NeoBlack,
                            unfocusedTextColor = NeoBlack,
                            focusedPlaceholderColor = NeoBlack.copy(alpha = 0.5f),
                            unfocusedPlaceholderColor = NeoBlack.copy(alpha = 0.5f),
                            cursorColor = NeoBlack
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, NeoBlack, RoundedCornerShape(12.dp))
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // QR action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        NeoBrutalButton(
                            onClick = {
                                val clean = roomId.trim().uppercase()
                                val cleanPartner = partnerName.trim().ifEmpty { "Peer" }
                                if (clean.isNotBlank()) {
                                    viewModel.createOrJoinRoom(clean, cleanPartner)
                                    onNavigateToShowQr(clean)
                                }
                            },
                            enabled = roomId.isNotBlank(),
                            backgroundColor = Color.White,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = null,
                                tint = NeoBlack,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Show QR",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeoBlack
                            )
                        }

                        NeoBrutalButton(
                            onClick = onNavigateToScanner,
                            backgroundColor = NeoBtnBlue,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = NeoBlack,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Scan QR",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeoBlack
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // Join & Open Chat Action Button
            NeoBrutalButton(
                onClick = {
                    val cleanName = myName.trim().ifEmpty { "You" }
                    val cleanRoom = roomId.trim().uppercase()
                    val cleanPartner = partnerName.trim().ifEmpty { "Peer" }
                    prefs.myName = cleanName
                    viewModel.createOrJoinRoom(cleanRoom, cleanPartner)
                    viewModel.openRoom(cleanRoom)
                    onNavigateToChat()
                },
                enabled = roomId.isNotBlank(),
                backgroundColor = NeoHeaderYellow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    tint = NeoBlack,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Join & Start Chat",
                    color = NeoBlack,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

private fun generateRandomRoomId(): String {
    val number = Random.nextInt(100, 999)
    return "CHAT-$number"
}
