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
import com.ashuchat.app.ui.theme.NeoBackground
import com.ashuchat.app.ui.theme.NeoCardBorder
import com.ashuchat.app.ui.theme.NeoCardMint
import com.ashuchat.app.ui.theme.NeoCardPeach
import com.ashuchat.app.ui.theme.NeoCardShadow
import com.ashuchat.app.ui.theme.NeoCardSky
import com.ashuchat.app.ui.theme.NeoCardYellow
import com.ashuchat.app.ui.theme.NeoSurfaceElevated
import com.ashuchat.app.ui.theme.NeoTextPrimary
import com.ashuchat.app.utils.HapticHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentPickerSheet(
    onDismiss: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    onTakePhoto: () -> Unit,
    onSendPing: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NeoBackground,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            NeoPillBadge(
                text = "SHARE CONTENT",
                backgroundColor = NeoSurfaceElevated,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                PickerOptionItem(
                    icon = Icons.Default.Image,
                    label = "Photos",
                    color = NeoCardSky,
                    onClick = {
                        onDismiss()
                        onPickImage()
                    }
                )
                PickerOptionItem(
                    icon = Icons.Default.CameraAlt,
                    label = "Camera",
                    color = NeoCardMint,
                    onClick = {
                        onDismiss()
                        onTakePhoto()
                    }
                )
                PickerOptionItem(
                    icon = Icons.Default.Description,
                    label = "File",
                    color = NeoCardPeach,
                    onClick = {
                        onDismiss()
                        onPickFile()
                    }
                )
                PickerOptionItem(
                    icon = Icons.Default.NotificationsActive,
                    label = "Ping ⚡",
                    color = NeoCardYellow,
                    onClick = {
                        onDismiss()
                        onSendPing()
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PickerOptionItem(
    icon: ImageVector,
    label: String,
    color: Color,
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
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    HapticHelper.playClick(context)
                    onClick()
                }
            )
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier.size(58.dp)
        ) {
            // Shadow
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 3.dp, y = 3.dp)
                    .clip(CircleShape)
                    .background(NeoCardShadow)
            )

            // Front button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = translationOffset, y = translationOffset)
                    .clip(CircleShape)
                    .background(color)
                    .border(2.dp, NeoCardBorder, CircleShape)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = NeoTextPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = NeoTextPrimary
        )
    }
}
