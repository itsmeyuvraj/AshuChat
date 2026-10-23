package com.ashuchat.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.VideoTrack
import java.util.Locale

@Composable
fun VoiceCallOverlay(
    callState: CallState,
    partnerName: String,
    callDurationSeconds: Int,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    isVideoCall: Boolean = false,
    localVideoTrack: VideoTrack? = null,
    remoteVideoTrack: VideoTrack? = null,
    eglBaseContext: EglBase.Context? = null,
    isLocalVideoEnabled: Boolean = true,
    isFrontCamera: Boolean = true,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onEnd: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onSwitchCamera: () -> Unit = {},
    onToggleVideo: () -> Unit = {}
) {
    val isPaper = com.ashuchat.app.ui.theme.LocalAppTheme.current == com.ashuchat.app.ui.theme.AppTheme.PAPER
    val isEffectivelyVideo = isVideoCall || remoteVideoTrack != null || localVideoTrack != null


    if (isEffectivelyVideo && callState == CallState.CONNECTED) {
        // Full Video Call Connected Experience
        VideoCallConnectedView(
            partnerName = partnerName,
            callDurationSeconds = callDurationSeconds,
            isMuted = isMuted,
            isSpeakerOn = isSpeakerOn,
            isLocalVideoEnabled = isLocalVideoEnabled,
            isFrontCamera = isFrontCamera,
            localVideoTrack = localVideoTrack,
            remoteVideoTrack = remoteVideoTrack,
            eglBaseContext = eglBaseContext,
            isPaper = isPaper,
            onEnd = onEnd,
            onToggleMute = onToggleMute,
            onToggleSpeaker = onToggleSpeaker,
            onSwitchCamera = onSwitchCamera,
            onToggleVideo = onToggleVideo
        )
    } else {
        // Voice Call or Video Ringing (Incoming / Outgoing)
        StandardCallView(
            callState = callState,
            partnerName = partnerName,
            callDurationSeconds = callDurationSeconds,
            isMuted = isMuted,
            isSpeakerOn = isSpeakerOn,
            isVideoCall = isVideoCall,
            localVideoTrack = localVideoTrack,
            eglBaseContext = eglBaseContext,
            isFrontCamera = isFrontCamera,
            isPaper = isPaper,
            onAccept = onAccept,
            onReject = onReject,
            onEnd = onEnd,
            onToggleMute = onToggleMute,
            onToggleSpeaker = onToggleSpeaker
        )
    }
}

@Composable
private fun VideoCallConnectedView(
    partnerName: String,
    callDurationSeconds: Int,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    isLocalVideoEnabled: Boolean,
    isFrontCamera: Boolean,
    localVideoTrack: VideoTrack?,
    remoteVideoTrack: VideoTrack?,
    eglBaseContext: EglBase.Context?,
    isPaper: Boolean,
    onEnd: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleVideo: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (remoteVideoTrack != null) Color.Transparent else Color(0xFF09080F))
    ) {
        // 1. Remote Video Stream (Fullscreen)
        if (remoteVideoTrack != null && eglBaseContext != null) {
            WebRtcVideoView(
                videoTrack = remoteVideoTrack,
                eglContext = eglBaseContext,
                modifier = Modifier.fillMaxSize(),
                isMirror = false,
                scalingType = RendererCommon.ScalingType.SCALE_ASPECT_FILL,
                isOverlay = false
            )
        } else {
            // Waiting for Remote Video Stream Placeholder
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(110.dp)
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
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Connected with $partnerName",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Waiting for video stream...",
                        fontSize = 14.sp,
                        color = RoseAccent
                    )
                }
            }
        }

        // Top Gradient Scrim for Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.8f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 2. Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 44.dp, start = 20.dp, end = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = partnerName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF69F0AE))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val minutes = callDurationSeconds / 60
                    val seconds = callDurationSeconds % 60
                    Text(
                        text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF69F0AE)
                    )
                }
            }

            // Direct P2P badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Encrypted",
                        tint = RoseAccent,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "P2P Video",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }

        // 3. Floating Picture-in-Picture Local Camera Preview
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 110.dp, end = 16.dp)
                .size(width = 110.dp, height = 160.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(18.dp))
                .background(Color(0xFF1E1C2A))
        ) {
            if (isLocalVideoEnabled && localVideoTrack != null && eglBaseContext != null) {
                WebRtcVideoView(
                    videoTrack = localVideoTrack,
                    eglContext = eglBaseContext,
                    isMirror = isFrontCamera,
                    scalingType = RendererCommon.ScalingType.SCALE_ASPECT_FILL,
                    isOverlay = true,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.VideocamOff,
                            contentDescription = "Camera off",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (localVideoTrack == null) "No Camera" else "Off",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // Bottom Gradient Scrim for Controls
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(160.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        // 4. In-Call Floating Control Dock
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp, start = 16.dp, end = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = if (isPaper) Color.White else Color(0xCC1A162B),
                border = androidx.compose.foundation.BorderStroke(
                    width = if (isPaper) 2.dp else 1.dp,
                    color = if (isPaper) Color.Black else Color.White.copy(alpha = 0.2f)
                ),
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Flip Camera
                    IconButton(
                        onClick = onSwitchCamera,
                        enabled = localVideoTrack != null,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                if (localVideoTrack == null) Color.White.copy(alpha = 0.05f)
                                else if (isPaper) com.ashuchat.app.ui.theme.NeoCardYellow
                                else Color.White.copy(alpha = 0.15f)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Switch Camera",
                            tint = if (localVideoTrack == null) Color.White.copy(alpha = 0.3f)
                            else if (isPaper) Color.Black
                            else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Toggle Local Video
                    IconButton(
                        onClick = onToggleVideo,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                if (!isLocalVideoEnabled) Color(0xFFFF5252).copy(alpha = 0.3f)
                                else if (isPaper) com.ashuchat.app.ui.theme.NeoBtnMint
                                else Color.White.copy(alpha = 0.15f)
                            )
                    ) {
                        Icon(
                            imageVector = if (isLocalVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = "Toggle Video",
                            tint = if (!isLocalVideoEnabled) Color(0xFFFF5252) else if (isPaper) Color.Black else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Toggle Mute
                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                if (isMuted) Color(0xFFFF5252).copy(alpha = 0.3f)
                                else if (isPaper) Color.White
                                else Color.White.copy(alpha = 0.15f)
                            )
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Mute",
                            tint = if (isMuted) Color(0xFFFF5252) else if (isPaper) Color.Black else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Toggle Speaker
                    IconButton(
                        onClick = onToggleSpeaker,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSpeakerOn && !isPaper) Color(0xFF69F0AE).copy(alpha = 0.25f)
                                else if (isSpeakerOn && isPaper) com.ashuchat.app.ui.theme.NeoBtnMint
                                else Color.White.copy(alpha = 0.15f)
                            )
                    ) {
                        Icon(
                            imageVector = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                            contentDescription = "Speaker",
                            tint = if (isSpeakerOn && !isPaper) Color(0xFF69F0AE) else if (isPaper) Color.Black else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // End Call
                    IconButton(
                        onClick = onEnd,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE53935))
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End Video Call",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StandardCallView(
    callState: CallState,
    partnerName: String,
    callDurationSeconds: Int,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    isVideoCall: Boolean,
    localVideoTrack: VideoTrack?,
    eglBaseContext: EglBase.Context?,
    isFrontCamera: Boolean,
    isPaper: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onEnd: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit
) {
    val hasOutgoingVideoPreview = isVideoCall && callState == CallState.OUTGOING && localVideoTrack != null && eglBaseContext != null


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (hasOutgoingVideoPreview) Color.Transparent else Color(0xE60D0B14))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        // If outgoing video call, show live camera preview in background
        if (hasOutgoingVideoPreview) {
            WebRtcVideoView(
                videoTrack = localVideoTrack,
                eglContext = eglBaseContext,
                isMirror = isFrontCamera,
                scalingType = RendererCommon.ScalingType.SCALE_ASPECT_FILL,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
            )
        }

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
                if (isVideoCall) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Video Call",
                        tint = Color.White,
                        modifier = Modifier.size(52.dp)
                    )
                } else {
                    Text(
                        text = partnerName.take(1).uppercase(),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
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
                        text = if (isVideoCall) "Outgoing Video Call..." else "Calling...",
                        fontSize = 15.sp,
                        color = RoseAccent
                    )
                }
                CallState.INCOMING -> {
                    Text(
                        text = if (isVideoCall) "Incoming Video Call..." else "Incoming Voice Call...",
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
                    if (isPaper) {
                        com.ashuchat.app.ui.components.NeoBrutalIconButton(
                            icon = Icons.Default.CallEnd,
                            contentDescription = "Decline",
                            backgroundColor = Color(0xFFFF6B6B),
                            size = 64.dp,
                            onClick = onReject
                        )

                        com.ashuchat.app.ui.components.NeoBrutalIconButton(
                            icon = if (isVideoCall) Icons.Default.Videocam else Icons.Default.Phone,
                            contentDescription = "Accept",
                            backgroundColor = com.ashuchat.app.ui.theme.NeoBtnMint,
                            size = 64.dp,
                            onClick = onAccept
                        )
                    } else {
                        IconButton(
                            onClick = onReject,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE53935))
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "Decline",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(
                            onClick = onAccept,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF43A047))
                        ) {
                            Icon(
                                imageVector = if (isVideoCall) Icons.Default.Videocam else Icons.Default.Phone,
                                contentDescription = "Accept",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isPaper) {
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
                    } else {
                        IconButton(
                            onClick = onToggleMute,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(if (isMuted) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Mute",
                                tint = if (isMuted) Color(0xFFFF5252) else Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        IconButton(
                            onClick = onEnd,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE53935))
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "End Call",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(
                            onClick = onToggleSpeaker,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(if (isSpeakerOn) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                                contentDescription = "Speaker",
                                tint = if (isSpeakerOn) Color(0xFF69F0AE) else Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
