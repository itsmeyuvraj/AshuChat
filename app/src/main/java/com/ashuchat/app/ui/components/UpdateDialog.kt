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
import com.ashuchat.app.ui.theme.NeoAccentYellow
import com.ashuchat.app.ui.theme.NeoBackground
import com.ashuchat.app.ui.theme.NeoCardBorder
import com.ashuchat.app.ui.theme.NeoCardYellow
import com.ashuchat.app.ui.theme.NeoSurface
import com.ashuchat.app.ui.theme.NeoSurfaceElevated
import com.ashuchat.app.ui.theme.NeoTextMuted
import com.ashuchat.app.ui.theme.NeoTextPrimary
import com.ashuchat.app.ui.theme.NeoTextSecondary
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
        containerColor = NeoSurface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.border(2.5.dp, NeoCardBorder, RoundedCornerShape(20.dp)),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(NeoCardYellow)
                        .border(2.dp, NeoCardBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = NeoAccentYellow,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "UPDATE AVAILABLE",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = NeoTextPrimary
                    )
                    Text(
                        text = "v${updateInfo.latestVersionName}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeoTextSecondary
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
                        color = NeoTextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(NeoSurfaceElevated)
                            .border(1.5.dp, NeoCardBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = updateInfo.releaseNotes,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = NeoTextPrimary,
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
                        color = NeoTextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { progressVal },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, NeoCardBorder, RoundedCornerShape(4.dp)),
                        color = NeoAccentYellow,
                        trackColor = NeoSurfaceElevated
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Installer will open automatically once finished.",
                        fontSize = 11.sp,
                        color = NeoTextMuted
                    )
                } else {
                    Text(
                        text = "Would you like to download and install this update now?",
                        fontSize = 13.sp,
                        color = NeoTextSecondary
                    )
                }
            }
        },
        confirmButton = {
            if (!isDownloading) {
                NeoBrutalButton(
                    text = "UPDATE NOW",
                    backgroundColor = NeoAccentYellow,
                    textColor = NeoBackground,
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
                        color = NeoTextSecondary
                    )
                }
            }
        }
    )
}
