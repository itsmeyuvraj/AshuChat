package com.ashuchat.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashuchat.app.data.model.CallState
import com.ashuchat.app.ui.theme.BrandLight
import com.ashuchat.app.ui.theme.RoseAccent
import com.ashuchat.app.ui.theme.RoseDark
import com.ashuchat.app.ui.theme.RosePrimary
import java.util.Locale

@Composable
fun VoiceCallOverlay(
    callState: CallState,
    partnerName: String,
    callDurationSeconds: Int,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onEnd: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE60D0B14))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "callPulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 0.95f,
                targetValue = 1.08f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse"
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(110.dp)
                    .scale(if (callState != CallState.CONNECTED) pulseScale else 1.0f)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(RosePrimary, RoseDark)))
                    .border(2.dp, RoseAccent.copy(alpha = 0.6f), CircleShape)
            ) {
                Text(
                    text = partnerName.take(1).uppercase(),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = partnerName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            when (callState) {
                CallState.OUTGOING -> {
                    Text(
                        text = "Calling...",
                        fontSize = 15.sp,
                        color = RoseAccent
                    )
                }
                CallState.INCOMING -> {
                    Text(
                        text = "Incoming Voice Call...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandLight
                    )
                }
                CallState.CONNECTED -> {
                    val minutes = callDurationSeconds / 60
                    val seconds = callDurationSeconds % 60
                    Text(
                        text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF69F0AE)
                    )
                }
                CallState.IDLE -> {}
            }

            Spacer(modifier = Modifier.height(48.dp))

            if (callState == CallState.INCOMING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    com.ashuchat.app.ui.components.NeoBrutalIconButton(
                        icon = Icons.Default.CallEnd,
                        contentDescription = "Decline",
                        backgroundColor = Color(0xFFFF6B6B),
                        size = 64.dp,
                        onClick = onReject
                    )

                    com.ashuchat.app.ui.components.NeoBrutalIconButton(
                        icon = Icons.Default.Phone,
                        contentDescription = "Accept",
                        backgroundColor = com.ashuchat.app.ui.theme.NeoBtnMint,
                        size = 64.dp,
                        onClick = onAccept
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    com.ashuchat.app.ui.components.NeoBrutalIconButton(
                        icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mute",
                        backgroundColor = if (isMuted) Color(0xFFFF6B6B) else Color.White,
                        size = 54.dp,
                        onClick = onToggleMute
                    )

                    com.ashuchat.app.ui.components.NeoBrutalIconButton(
                        icon = Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        backgroundColor = Color(0xFFFF6B6B),
                        size = 64.dp,
                        onClick = onEnd
                    )

                    com.ashuchat.app.ui.components.NeoBrutalIconButton(
                        icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                        contentDescription = "Speaker",
                        backgroundColor = if (isSpeakerOn) com.ashuchat.app.ui.theme.NeoBtnMint else Color.White,
                        size = 54.dp,
                        onClick = onToggleSpeaker
                    )
                }
            }
        }
    }
}
