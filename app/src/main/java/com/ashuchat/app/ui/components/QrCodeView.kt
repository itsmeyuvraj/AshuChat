package com.ashuchat.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.ashuchat.app.ui.theme.RosePrimary
import com.ashuchat.app.utils.QrUtils

@Composable
fun QrCodeView(
    content: String,
    modifier: Modifier = Modifier,
    sizeDp: Int = 220
) {
    val bitmap = remember(content) {
        if (content.isNotBlank()) {
            QrUtils.generateQrBitmap(content, 512)
        } else {
            null
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .border(3.dp, RosePrimary.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Room QR Code",
                modifier = Modifier.size((sizeDp - 32).dp)
            )
        } else {
            CircularProgressIndicator(
                color = RosePrimary,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
