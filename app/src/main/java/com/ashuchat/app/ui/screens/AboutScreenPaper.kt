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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashuchat.app.ui.components.NeoBrutalButton
import com.ashuchat.app.ui.components.NeoBrutalCard
import com.ashuchat.app.ui.components.NeoTopAppBar
import com.ashuchat.app.ui.components.UpdateDialog
import com.ashuchat.app.ui.theme.NeoBackground
import com.ashuchat.app.ui.theme.NeoBlack
import com.ashuchat.app.ui.theme.NeoBtnBlue
import com.ashuchat.app.ui.theme.NeoBtnMint
import com.ashuchat.app.ui.theme.NeoCardLavender
import com.ashuchat.app.ui.theme.NeoCardPeach
import com.ashuchat.app.ui.theme.NeoCardPink
import com.ashuchat.app.ui.theme.NeoCardYellow
import com.ashuchat.app.ui.theme.NeoTextDark
import com.ashuchat.app.ui.theme.NeoTextMuted
import com.ashuchat.app.ui.theme.OnlineGreen
import com.ashuchat.app.ui.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreenPaper(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit
) {
    val updateInfo by viewModel.updateInfo.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val isDownloadingUpdate by viewModel.isDownloadingUpdate.collectAsState()
    val updateCheckMessage by viewModel.updateCheckMessage.collectAsState()

    Scaffold(
        containerColor = NeoBackground,
        topBar = {
            NeoTopAppBar(
                title = "About AshuChat",
                subtitle = "App Info & Updates",
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

            // App Icon / Logo
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(86.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(NeoCardYellow)
                    .border(2.5.dp, NeoBlack, RoundedCornerShape(22.dp))
            ) {
                Text(
                    text = "🎨",
                    fontSize = 42.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "AshuChat",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = NeoBlack
            )

            Text(
                text = "PRIVATE DIRECT PEER-TO-PEER ENCRYPTED CHAT",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
                color = NeoTextMuted
            )

            Spacer(modifier = Modifier.height(24.dp))

            // App Version & In-App Updater Card (Pastel Lavender NeoBrutalCard)
            NeoBrutalCard(
                backgroundColor = NeoCardLavender,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Installed Version",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeoTextMuted
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "v${viewModel.updateManager.currentVersionName} (Build ${viewModel.updateManager.currentVersionCode})",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = NeoBlack
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        NeoBrutalButton(
                            onClick = { viewModel.checkForUpdates(silent = false) },
                            enabled = !isCheckingUpdate && !isDownloadingUpdate,
                            backgroundColor = NeoBtnMint,
                            modifier = Modifier.width(140.dp)
                        ) {
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = NeoBlack,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = NeoBlack,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Check",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = NeoBlack
                                )
                            }
                        }
                    }

                    if (updateCheckMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White)
                                .border(1.5.dp, NeoBlack, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = OnlineGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = updateCheckMessage ?: "",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeoBlack
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Dedication Card (Pastel Pink NeoBrutalCard)
            NeoBrutalCard(
                backgroundColor = NeoCardPink,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "💖", fontSize = 28.sp)

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Developed by Yuvraj Poudyal for Ashu",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = NeoBlack,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Built with infinite love for private, unbreakable moments.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = NeoBlack.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Technology & Security Details Card (Pastel Peach NeoBrutalCard)
            NeoBrutalCard(
                backgroundColor = NeoCardPeach,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "⚡", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Specifications & Security",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = NeoBlack
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "• Architecture: Peer-to-Peer WebRTC DataChannel\n" +
                                "• Encryption: DTLS-SRTP 256-bit AES\n" +
                                "• Media Transfer: Chunked binary streaming\n" +
                                "• Local Storage: SQLite Room Database (Local Only)\n" +
                                "• Auto-Updates: GitHub Releases CI/CD Integration",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeoBlack.copy(alpha = 0.9f),
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Update Dialog if update is available
    if (updateInfo != null && updateInfo!!.isUpdateAvailable) {
        UpdateDialog(
            updateInfo = updateInfo!!,
            downloadProgress = downloadProgress,
            isDownloading = isDownloadingUpdate,
            onConfirmUpdate = {
                viewModel.startAppUpdate(updateInfo!!.downloadUrl)
            },
            onDismiss = {
                viewModel.dismissUpdateDialog()
            }
        )
    }
}
