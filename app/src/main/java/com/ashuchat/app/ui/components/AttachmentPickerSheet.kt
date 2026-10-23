package com.ashuchat.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashuchat.app.ui.theme.AppTheme
import com.ashuchat.app.ui.theme.BrandLight
import com.ashuchat.app.ui.theme.LocalAppTheme
import com.ashuchat.app.ui.theme.NeoBackground
import com.ashuchat.app.ui.theme.NeoBlack
import com.ashuchat.app.ui.theme.NeoCardMint
import com.ashuchat.app.ui.theme.NeoCardPeach
import com.ashuchat.app.ui.theme.NeoCardSky
import com.ashuchat.app.ui.theme.NeoCardYellow
import com.ashuchat.app.ui.theme.SurfaceElevated
import com.ashuchat.app.ui.theme.TextPrimary
import com.ashuchat.app.utils.HapticHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentPickerSheet(
    onDismiss: () -> Unit,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickFile: () -> Unit,
    onSendPing: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isPaper = LocalAppTheme.current == AppTheme.PAPER

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (isPaper) NeoBackground else SurfaceElevated,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = if (isPaper) 12.dp else 16.dp)
        ) {
            if (isPaper) {
                NeoPillBadge(
                    text = "SHARE CONTENT",
                    backgroundColor = NeoCardYellow,
                    modifier = Modifier.padding(bottom = 20.dp)
                )
            } else {
                Text(
                    text = "Share Content",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 20.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                PickerOptionItem(
                    icon = Icons.Default.Image,
                    label = "Photos",
                    color = if (isPaper) NeoCardSky else BrandLight,
                    isPaper = isPaper,
                    onClick = {
                        onDismiss()
                        onPickImage()
                    }
                )
                PickerOptionItem(
                    icon = Icons.Default.CameraAlt,
                    label = "Camera",
                    color = if (isPaper) NeoCardMint else BrandLight,
                    isPaper = isPaper,
                    onClick = {
                        onDismiss()
                        onTakePhoto()
                    }
                )
                PickerOptionItem(
                    icon = Icons.Default.Description,
                    label = "File",
                    color = if (isPaper) NeoCardPeach else BrandLight,
                    isPaper = isPaper,
                    onClick = {
                        onDismiss()
                        onPickFile()
                    }
                )
                PickerOptionItem(
                    icon = Icons.Default.NotificationsActive,
                    label = if (isPaper) "Ping ⚡" else "Ping",
                    color = if (isPaper) NeoCardYellow else BrandLight,
                    isPaper = isPaper,
                    onClick = {
                        onDismiss()
                        onSendPing()
                    }
                )
            }

            Spacer(modifier = Modifier.height(if (isPaper) 32.dp else 28.dp))
        }
    }
}

@Composable
private fun PickerOptionItem(
    icon: ImageVector,
    label: String,
    color: Color,
    isPaper: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val translationOffset by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 0.dp,
        animationSpec = spring(stiffness = 500f),
        label = "sheetBtnPress"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = if (isPaper) interactionSource else remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (isPaper) HapticHelper.playClick(context)
                    onClick()
                }
            )
            .padding(if (isPaper) 6.dp else 8.dp)
    ) {
        if (isPaper) {
            Box(
                modifier = Modifier.size(58.dp)
            ) {
                // Shadow
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .offset(x = 3.dp, y = 3.dp)
                        .clip(CircleShape)
                        .background(NeoBlack)
                )

                // Front button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .matchParentSize()
                        .offset(x = translationOffset, y = translationOffset)
                        .clip(CircleShape)
                        .background(color)
                        .border(2.dp, NeoBlack, CircleShape)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = NeoBlack,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.18f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(if (isPaper) 10.dp else 8.dp))

        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isPaper) FontWeight.Black else FontWeight.Medium,
            color = if (isPaper) NeoBlack else TextPrimary
        )
    }
}
