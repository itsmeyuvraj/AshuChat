package com.ashuchat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashuchat.app.ui.theme.NeoCardBorder
import com.ashuchat.app.ui.theme.NeoSurfaceElevated
import com.ashuchat.app.ui.theme.NeoTextPrimary
import com.ashuchat.app.webrtc.P2PConnectionState

@Composable
fun ConnectionBadge(
    state: P2PConnectionState,
    modifier: Modifier = Modifier
) {
    val isOnline = state == P2PConnectionState.CONNECTED_P2P
    val dotColor = if (isOnline) Color(0xFF22C55E) else Color(0xFFEF4444)
    val label = if (isOnline) "ONLINE" else "OFFLINE"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(NeoSurfaceElevated)
            .border(1.5.dp, NeoCardBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
            color = NeoTextPrimary
        )
    }
}
