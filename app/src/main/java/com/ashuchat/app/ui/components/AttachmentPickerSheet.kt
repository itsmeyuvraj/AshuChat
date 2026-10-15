package com.ashuchat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashuchat.app.ui.theme.RoseAccent
import com.ashuchat.app.ui.theme.RosePrimary
import com.ashuchat.app.ui.theme.SurfaceCard
import com.ashuchat.app.ui.theme.SurfaceElevated
import com.ashuchat.app.ui.theme.TextPrimary
import com.ashuchat.app.ui.theme.VioletAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentPickerSheet(
    onDismiss: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    onTakePhoto: () -> Unit,
    onSendLovePing: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceElevated,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Share Content",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                PickerOptionItem(
                    icon = Icons.Default.Image,
                    label = "Photos",
                    color = RosePrimary,
                    onClick = {
                        onDismiss()
                        onPickImage()
                    }
                )
                PickerOptionItem(
                    icon = Icons.Default.CameraAlt,
                    label = "Camera",
                    color = VioletAccent,
                    onClick = {
                        onDismiss()
                        onTakePhoto()
                    }
                )
                PickerOptionItem(
                    icon = Icons.Default.Description,
                    label = "File",
                    color = Color(0xFF00B0FF),
                    onClick = {
                        onDismiss()
                        onPickFile()
                    }
                )
                PickerOptionItem(
                    icon = Icons.Default.NotificationsActive,
                    label = "Ping",
                    color = RoseAccent,
                    onClick = {
                        onDismiss()
                        onSendLovePing()
                    }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
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
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
    }
}
