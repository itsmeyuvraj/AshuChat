package com.ashuchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashuchat.app.ui.components.NeoBrutalButton
import com.ashuchat.app.ui.components.NeoBrutalCard
import com.ashuchat.app.ui.components.NeoTopAppBar
import com.ashuchat.app.ui.theme.NeoAccentYellow
import com.ashuchat.app.ui.theme.NeoBackground
import com.ashuchat.app.ui.theme.NeoBtnMint
import com.ashuchat.app.ui.theme.NeoBtnPeach
import com.ashuchat.app.ui.theme.NeoCardBorder
import com.ashuchat.app.ui.theme.NeoCardMint
import com.ashuchat.app.ui.theme.NeoCardPeach
import com.ashuchat.app.ui.theme.NeoCardYellow
import com.ashuchat.app.ui.theme.NeoSurface
import com.ashuchat.app.ui.theme.NeoSurfaceElevated
import com.ashuchat.app.ui.theme.NeoTextMuted
import com.ashuchat.app.ui.theme.NeoTextPrimary
import com.ashuchat.app.ui.theme.NeoTextSecondary
import com.ashuchat.app.ui.theme.OfflineRed
import com.ashuchat.app.ui.theme.OnlineGreen
import com.ashuchat.app.ui.viewmodel.ChatViewModel
import com.ashuchat.app.utils.BatteryOptimizationHelper
import com.ashuchat.app.utils.HapticHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSettingsScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = NeoBackground,
        topBar = {
            NeoTopAppBar(
                title = "Chat Settings",
                subtitle = "Security & Storage Options",
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
            // Privacy & Security Card (Dark Tinted NeoBrutalCard)
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
                        Text(text = "🔒", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Privacy & App Protection",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = NeoTextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 1. Biometric Lock Switch
                    var biometricEnabled by remember { mutableStateOf(viewModel.prefs.isBiometricEnabled) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Biometric App Lock",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeoTextPrimary
                            )
                            Text(
                                text = "Require fingerprint or face unlock on open",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = NeoTextMuted
                            )
                        }
                        Switch(
                            checked = biometricEnabled,
                            onCheckedChange = { checked ->
                                HapticHelper.playClick(context, view)
                                biometricEnabled = checked
                                viewModel.prefs.isBiometricEnabled = checked
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = NeoAccentYellow,
                                uncheckedThumbColor = NeoTextSecondary,
                                uncheckedTrackColor = NeoSurfaceElevated
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. Anti-Screenshot / FLAG_SECURE Switch
                    var flagSecureEnabled by remember { mutableStateOf(viewModel.prefs.isFlagSecureEnabled) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Anti-Screenshot Protection",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeoTextPrimary
                            )
                            Text(
                                text = "Block screenshots & hide preview in recent apps",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = NeoTextMuted
                            )
                        }
                        Switch(
                            checked = flagSecureEnabled,
                            onCheckedChange = { checked ->
                                HapticHelper.playClick(context, view)
                                flagSecureEnabled = checked
                                viewModel.prefs.isFlagSecureEnabled = checked
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = NeoAccentYellow,
                                uncheckedThumbColor = NeoTextSecondary,
                                uncheckedTrackColor = NeoSurfaceElevated
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. Disappearing Messages Selector
                    var disappearingDuration by remember { mutableStateOf(viewModel.prefs.disappearingDurationSeconds) }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Disappearing Messages",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeoTextPrimary
                        )
                        Text(
                            text = "Messages disappear after timer, only once viewed by partner",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = NeoTextMuted
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        val options = listOf(
                            0L to "Off",
                            10L to "View Once",
                            30L to "30 sec",
                            300L to "5 min",
                            3600L to "1 hour",
                            86400L to "24 hours"
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            options.forEach { (duration, label) ->
                                val isSelected = disappearingDuration == duration
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) NeoAccentYellow else NeoSurfaceElevated)
                                        .border(2.dp, if (isSelected) NeoAccentYellow else NeoCardBorder, RoundedCornerShape(10.dp))
                                        .clickable {
                                            HapticHelper.playClick(context, view)
                                            disappearingDuration = duration
                                            viewModel.prefs.disappearingDurationSeconds = duration
                                        }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (isSelected) NeoBackground else NeoTextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Background Operation & Battery Optimization Card (Dark Tinted NeoBrutalCard)
            var isBatteryIgnored by remember {
                mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))
            }

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
                        Text(text = "⚡", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "24/7 Background Sync & Calls",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = NeoTextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "AshuChat runs a persistent foreground sync service so you can receive incoming voice calls and encrypted messages even when the app is closed or the screen is locked.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = NeoTextSecondary,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(NeoSurfaceElevated)
                            .border(2.dp, NeoCardBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Icon(
                            imageVector = if (isBatteryIgnored) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = if (isBatteryIgnored) OnlineGreen else Color(0xFFD97706),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isBatteryIgnored) "Unrestricted Background Active" else "Battery Optimization Active",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeoTextPrimary
                            )
                            Text(
                                text = if (isBatteryIgnored)
                                    "Android will not terminate incoming call signaling when idle"
                                else
                                    "OEM power savers may delay or terminate calls when app is closed",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = NeoTextMuted
                            )
                        }
                    }

                    if (!isBatteryIgnored) {
                        Spacer(modifier = Modifier.height(14.dp))
                        NeoBrutalButton(
                            onClick = {
                                BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                                isBatteryIgnored = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
                            },
                            backgroundColor = NeoAccentYellow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Allow Unrestricted Background",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = NeoBackground
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Data Management Card (Dark Tinted NeoBrutalCard)
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
                        Text(text = "🗑️", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Local Storage & History",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = NeoTextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Messages, media, and room histories are stored securely in your phone's private Room database. You can wipe all local chat records at any time.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = NeoTextSecondary,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    NeoBrutalButton(
                        onClick = { showClearConfirmDialog = true },
                        backgroundColor = Color(0xFF7F1D1D),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Clear All Local Data",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(text = "Clear All Local Data?", color = NeoTextPrimary, fontWeight = FontWeight.Black)
            },
            text = {
                Text(
                    text = "This will permanently remove all chat rooms, message histories, and downloaded media files saved on this device.",
                    color = NeoTextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        HapticHelper.playHeavyClick(context, view)
                        showClearConfirmDialog = false
                        viewModel.clearAllData()
                    }
                ) {
                    Text("Clear All Data", color = OfflineRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel", color = NeoTextSecondary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = NeoSurface,
            modifier = Modifier.border(2.dp, NeoCardBorder, RoundedCornerShape(20.dp))
        )
    }
}
