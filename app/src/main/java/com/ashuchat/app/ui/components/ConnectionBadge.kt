package com.ashuchat.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashuchat.app.ui.theme.ConnectingYellow
import com.ashuchat.app.ui.theme.OfflineRed
import com.ashuchat.app.ui.theme.OnlineGreen
import com.ashuchat.app.webrtc.P2PConnectionState

@Composable
fun ConnectionBadge(
    state: P2PConnectionState,
    modifier: Modifier = Modifier
) {
    val (dotColor, label) = when (state) {
        P2PConnectionState.CONNECTED_P2P -> Pair(OnlineGreen, "Direct P2P Active")
        P2PConnectionState.CONNECTING_SIGNALING -> Pair(ConnectingYellow, "Connecting Relay...")
        P2PConnectionState.WAITING_FOR_PEER -> Pair(ConnectingYellow, "Peer Offline • Mailbox Ready")
        P2PConnectionState.NEGOTIATING_WEBRTC -> Pair(ConnectingYellow, "Handshaking P2P...")
        P2PConnectionState.FAILED -> Pair(OfflineRed, "Connection Failed")
        P2PConnectionState.DISCONNECTED -> Pair(OfflineRed, "Offline")
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val isPulsing = state != P2PConnectionState.CONNECTED_P2P && state != P2PConnectionState.DISCONNECTED

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF151B26).copy(alpha = 0.9f))
            .border(1.dp, dotColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .alpha(if (isPulsing) alphaAnim else 1f)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}
