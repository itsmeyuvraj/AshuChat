package com.ashuchat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashuchat.app.ui.theme.BrandDark
import com.ashuchat.app.ui.theme.BrandLight
import com.ashuchat.app.ui.theme.BrandPrimary
import com.ashuchat.app.ui.theme.SurfaceBorder
import com.ashuchat.app.ui.theme.SurfaceCard
import com.ashuchat.app.ui.theme.SurfaceElevated
import com.ashuchat.app.ui.theme.TextMuted
import com.ashuchat.app.ui.theme.TextPrimary
import com.ashuchat.app.ui.theme.TextSecondary
import com.ashuchat.app.utils.AppUpdateInfo

@Composable
fun UpdateDialog(
    updateInfo: AppUpdateInfo,
    downloadProgress: Float?,
    isDownloading: Boolean,
    onConfirmUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) onDismiss()
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.border(2.5.dp, com.ashuchat.app.ui.theme.NeoBlack, RoundedCornerShape(20.dp)),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(com.ashuchat.app.ui.theme.NeoCardYellow)
                        .border(2.dp, com.ashuchat.app.ui.theme.NeoBlack, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = com.ashuchat.app.ui.theme.NeoBlack,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "UPDATE AVAILABLE",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = com.ashuchat.app.ui.theme.NeoBlack
                    )
                    Text(
                        text = "v${updateInfo.latestVersionName}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = com.ashuchat.app.ui.theme.NeoBlack.copy(alpha = 0.6f)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                if (updateInfo.releaseNotes.isNotBlank()) {
                    Text(
                        text = "WHAT'S NEW:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = com.ashuchat.app.ui.theme.NeoBlack
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(com.ashuchat.app.ui.theme.NeoBackground)
                            .border(1.5.dp, com.ashuchat.app.ui.theme.NeoBlack, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = updateInfo.releaseNotes,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = com.ashuchat.app.ui.theme.NeoBlack,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                if (isDownloading) {
                    val progressVal = downloadProgress ?: 0f
                    val percentage = (progressVal * 100).toInt()

                    Text(
                        text = "Downloading update: $percentage%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = com.ashuchat.app.ui.theme.NeoBlack
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { progressVal },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, com.ashuchat.app.ui.theme.NeoBlack, RoundedCornerShape(4.dp)),
                        color = com.ashuchat.app.ui.theme.NeoHeaderYellow,
                        trackColor = Color.LightGray.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Installer will open automatically once finished.",
                        fontSize = 11.sp,
                        color = com.ashuchat.app.ui.theme.NeoBlack.copy(alpha = 0.6f)
                    )
                } else {
                    Text(
                        text = "Would you like to download and install this update now?",
                        fontSize = 13.sp,
                        color = com.ashuchat.app.ui.theme.NeoBlack.copy(alpha = 0.8f)
                    )
                }
            }
        },
        confirmButton = {
            if (!isDownloading) {
                NeoBrutalButton(
                    text = "UPDATE NOW",
                    backgroundColor = com.ashuchat.app.ui.theme.NeoBtnMint,
                    onClick = onConfirmUpdate
                )
            }
        },
        dismissButton = {
            if (!isDownloading) {
                TextButton(onClick = onDismiss) {
                    Text(
                        "LATER",
                        fontWeight = FontWeight.Bold,
                        color = com.ashuchat.app.ui.theme.NeoBlack
                    )
                }
            }
        }
    )
}
