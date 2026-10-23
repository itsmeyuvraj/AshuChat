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
import com.ashuchat.app.ui.theme.AppTheme
import com.ashuchat.app.ui.theme.BrandDark
import com.ashuchat.app.ui.theme.BrandLight
import com.ashuchat.app.ui.theme.BrandPrimary
import com.ashuchat.app.ui.theme.LocalAppTheme
import com.ashuchat.app.ui.theme.NeoBackground
import com.ashuchat.app.ui.theme.NeoBlack
import com.ashuchat.app.ui.theme.NeoBtnMint
import com.ashuchat.app.ui.theme.NeoCardYellow
import com.ashuchat.app.ui.theme.NeoHeaderYellow
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
    val isPaper = LocalAppTheme.current == AppTheme.PAPER

    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) onDismiss()
        },
        containerColor = if (isPaper) Color.White else SurfaceElevated,
        shape = RoundedCornerShape(20.dp),
        modifier = if (isPaper) {
            Modifier.border(2.5.dp, NeoBlack, RoundedCornerShape(20.dp))
        } else {
            Modifier.border(1.dp, SurfaceBorder, RoundedCornerShape(20.dp))
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(if (isPaper) 44.dp else 40.dp)
                        .clip(CircleShape)
                        .background(if (isPaper) NeoCardYellow else SurfaceCard)
                        .border(
                            if (isPaper) 2.dp else 1.dp,
                            if (isPaper) NeoBlack else SurfaceBorder,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = if (isPaper) NeoBlack else BrandLight,
                        modifier = Modifier.size(if (isPaper) 24.dp else 22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = if (isPaper) "UPDATE AVAILABLE" else "Update Available",
                        fontSize = if (isPaper) 16.sp else 18.sp,
                        fontWeight = if (isPaper) FontWeight.Black else FontWeight.Bold,
                        color = if (isPaper) NeoBlack else TextPrimary
                    )
                    Text(
                        text = if (isPaper) "v${updateInfo.latestVersionName}" else "Version ${updateInfo.latestVersionName}",
                        fontSize = 12.sp,
                        fontWeight = if (isPaper) FontWeight.Bold else FontWeight.Normal,
                        color = if (isPaper) NeoBlack.copy(alpha = 0.6f) else BrandLight
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (updateInfo.releaseNotes.isNotBlank()) {
                    Text(
                        text = if (isPaper) "WHAT'S NEW:" else "What's New:",
                        fontSize = if (isPaper) 12.sp else 13.sp,
                        fontWeight = if (isPaper) FontWeight.Black else FontWeight.SemiBold,
                        color = if (isPaper) NeoBlack else TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isPaper) NeoBackground else SurfaceCard)
                            .border(
                                if (isPaper) 1.5.dp else 1.dp,
                                if (isPaper) NeoBlack else SurfaceBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = updateInfo.releaseNotes,
                            fontSize = 12.sp,
                            fontWeight = if (isPaper) FontWeight.Medium else FontWeight.Normal,
                            color = if (isPaper) NeoBlack else TextSecondary,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                if (isDownloading && downloadProgress != null) {
                    val percentage = (downloadProgress * 100).toInt()
                    Text(
                        text = "Downloading update: $percentage%",
                        fontSize = 13.sp,
                        fontWeight = if (isPaper) FontWeight.Black else FontWeight.Medium,
                        color = if (isPaper) NeoBlack else TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .then(
                                if (isPaper) Modifier.border(1.dp, NeoBlack, RoundedCornerShape(4.dp))
                                else Modifier
                            ),
                        color = if (isPaper) NeoHeaderYellow else BrandLight,
                        trackColor = if (isPaper) Color.LightGray.copy(alpha = 0.5f) else SurfaceCard
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Installer will open automatically once finished.",
                        fontSize = 11.sp,
                        color = if (isPaper) NeoBlack.copy(alpha = 0.6f) else TextMuted
                    )
                } else {
                    Text(
                        text = "Would you like to download and install this update now?",
                        fontSize = 13.sp,
                        color = if (isPaper) NeoBlack.copy(alpha = 0.8f) else TextSecondary
                    )
                }
            }
        },
        confirmButton = {
            if (!isDownloading) {
                if (isPaper) {
                    NeoBrutalButton(
                        text = "UPDATE NOW",
                        backgroundColor = NeoBtnMint,
                        onClick = onConfirmUpdate
                    )
                } else {
                    Button(
                        onClick = onConfirmUpdate,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .background(
                                Brush.horizontalGradient(listOf(BrandPrimary, BrandDark)),
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        Text("Update Now", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                TextButton(onClick = onDismiss) {
                    Text(
                        if (isPaper) "LATER" else "Later",
                        fontWeight = if (isPaper) FontWeight.Bold else FontWeight.Normal,
                        color = if (isPaper) NeoBlack else TextSecondary
                    )
                }
            }
        }
    )
}
