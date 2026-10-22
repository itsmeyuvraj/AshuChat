package com.ashuchat.app.ui.components

import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ashuchat.app.data.model.ChatMessage
import com.ashuchat.app.data.model.MessageStatus
import com.ashuchat.app.data.model.MessageType
import com.ashuchat.app.ui.theme.NeoAccentBlue
import com.ashuchat.app.ui.theme.NeoAccentYellow
import com.ashuchat.app.ui.theme.NeoBtnBlue
import com.ashuchat.app.ui.theme.NeoBtnMint
import com.ashuchat.app.ui.theme.NeoCardBorder
import com.ashuchat.app.ui.theme.NeoCardMint
import com.ashuchat.app.ui.theme.NeoCardPeach
import com.ashuchat.app.ui.theme.NeoCardShadow
import com.ashuchat.app.ui.theme.NeoCardYellow
import com.ashuchat.app.ui.theme.NeoSurfaceElevated
import com.ashuchat.app.ui.theme.NeoTextMuted
import com.ashuchat.app.ui.theme.NeoTextPrimary
import com.ashuchat.app.ui.theme.NeoTextSecondary
import com.ashuchat.app.utils.AudioPlayerHelper
import com.ashuchat.app.utils.FileUtils
import com.ashuchat.app.utils.HapticHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatBubble(
    message: ChatMessage,
    onImageClick: (filePath: String, fileName: String?) -> Unit,
    onRetryClick: ((ChatMessage) -> Unit)? = null,
    onReplyClick: ((ChatMessage) -> Unit)? = null,
    onReactionClick: ((messageId: String, emoji: String) -> Unit)? = null,
    audioPlayerHelper: AudioPlayerHelper? = null,
    modifier: Modifier = Modifier
) {
    val isMe = message.isFromMe
    val alignment = if (isMe) Alignment.End else Alignment.Start
    var showReactionPicker by remember { mutableStateOf(false) }

    val bubbleShape = if (isMe) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
    }

    val bubbleColor = if (isMe) NeoCardMint else NeoSurfaceElevated
    val bubbleBorderColor = if (isMe) Color(0xFF28483B) else NeoCardBorder

    Column(
        horizontalAlignment = alignment,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        if (showReactionPicker) {
            ReactionPickerBar(
                onEmojiSelected = { emoji ->
                    onReactionClick?.invoke(message.id, emoji)
                    showReactionPicker = false
                },
                onReply = {
                    onReplyClick?.invoke(message)
                    showReactionPicker = false
                },
                onDismiss = { showReactionPicker = false }
            )
        }

        // Neo-Brutalist bubble with offset hard shadow
        Box(
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            // Shadow layer
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = if (isMe) 2.5.dp else (-2.5).dp, y = 2.5.dp)
                    .clip(bubbleShape)
                    .background(NeoCardShadow)
            )

            // Foreground bubble
            Box(
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(bubbleColor)
                    .border(2.dp, bubbleBorderColor, bubbleShape)
                    .pointerInput(message.id) {
                        detectTapGestures(
                            onLongPress = {
                                showReactionPicker = !showReactionPicker
                            }
                        )
                    }
                    .padding(if (message.type == MessageType.PING || message.type == MessageType.LOVE_PING) 12.dp else 11.dp)
            ) {
                Column {
                    // Reply quote header
                    if (!message.replyToText.isNullOrBlank()) {
                        ReplyPreviewBox(
                            replySender = message.replyToSender,
                            replyText = message.replyToText,
                            isMe = isMe
                        )
                    }

                    when (message.type) {
                        MessageType.TEXT -> {
                            Text(
                                text = message.text,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = NeoTextPrimary,
                                lineHeight = 21.sp
                            )
                        }

                        MessageType.IMAGE -> {
                            ImageBubbleContent(
                                message = message,
                                onImageClick = onImageClick
                            )
                        }

                        MessageType.FILE -> {
                            FileBubbleContent(message = message)
                        }

                        MessageType.PING, MessageType.LOVE_PING -> {
                            PingBubbleContent(message = message, isMe = isMe)
                        }

                        MessageType.AUDIO -> {
                            AudioBubbleContent(
                                message = message,
                                audioPlayerHelper = audioPlayerHelper,
                                isMe = isMe
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Timestamp, Disappearing Indicator, and Status
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (message.expiresAt != null || message.disappearingDuration != null) {
                            val isCountingDown = message.expiresAt != null
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = if (isCountingDown) "Disappearing timer active" else "Disappearing after view",
                                tint = NeoTextSecondary,
                                modifier = Modifier
                                    .size(12.dp)
                                    .padding(end = 2.dp)
                            )
                        }

                        Text(
                            text = formatTime(message.timestamp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeoTextMuted
                        )

                        if (isMe) {
                            Spacer(modifier = Modifier.width(4.dp))
                            StatusIndicator(
                                status = message.status,
                                onRetryClick = { onRetryClick?.invoke(message) }
                            )
                        }
                    }
                }
            }
        }

        // Reaction Badge pinned to bottom of bubble
        if (!message.reaction.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .offset(y = (-8).dp, x = if (isMe) (-6).dp else 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NeoSurfaceElevated)
                    .border(1.5.dp, NeoCardBorder, RoundedCornerShape(12.dp))
                    .clickable { showReactionPicker = !showReactionPicker }
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = message.reaction,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun ReactionPickerBar(
    onEmojiSelected: (String) -> Unit,
    onReply: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val emojis = listOf("❤️", "😂", "😮", "😢", "👍", "⚡")

    Box(
        modifier = Modifier
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(NeoSurfaceElevated)
            .border(2.dp, NeoCardBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            emojis.forEach { emoji ->
                Text(
                    text = emoji,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            HapticHelper.playClick(context)
                            onEmojiSelected(emoji)
                        }
                        .padding(4.dp)
                )
            }

            Box(
                modifier = Modifier
                    .width(1.5.dp)
                    .height(20.dp)
                    .background(NeoCardBorder)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        HapticHelper.playClick(context)
                        onReply()
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = "Reply",
                    tint = NeoTextPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Reply",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = NeoTextPrimary
                )
            }

            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = NeoTextSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .clickable {
                        HapticHelper.playClick(context)
                        onDismiss()
                    }
            )
        }
    }
}

@Composable
private fun ReplyPreviewBox(
    replySender: String?,
    replyText: String,
    isMe: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(NeoSurfaceElevated.copy(alpha = 0.6f))
            .border(1.dp, NeoCardBorder, RoundedCornerShape(10.dp))
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(36.dp)
                .background(NeoAccentYellow)
        )
        Column(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = replySender ?: "Peer",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = NeoAccentYellow
            )
            Text(
                text = replyText,
                fontSize = 12.sp,
                color = NeoTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AudioBubbleContent(
    message: ChatMessage,
    audioPlayerHelper: AudioPlayerHelper?,
    isMe: Boolean
) {
    val context = LocalContext.current
    val currentPlayingId by audioPlayerHelper?.currentPlayingId?.collectAsState() ?: remember { mutableStateOf(null) }
    val isPlaying by audioPlayerHelper?.isPlaying?.collectAsState() ?: remember { mutableStateOf(false) }
    val progress by audioPlayerHelper?.progress?.collectAsState() ?: remember { mutableStateOf(0f) }
    val currentPositionMs by audioPlayerHelper?.currentPositionMs?.collectAsState() ?: remember { mutableStateOf(0L) }
    val totalDurationMs by audioPlayerHelper?.totalDurationMs?.collectAsState() ?: remember { mutableStateOf(0L) }
    val speed by audioPlayerHelper?.speed?.collectAsState() ?: remember { mutableStateOf(1.0f) }

    val isThisPlaying = (currentPlayingId == message.id && isPlaying)
    val thisProgress = if (currentPlayingId == message.id) progress else 0f

    val durationText = remember(currentPlayingId, isThisPlaying, currentPositionMs, totalDurationMs, message.attachmentSize) {
        if (currentPlayingId == message.id && totalDurationMs > 0) {
            val curSec = currentPositionMs / 1000
            val totSec = totalDurationMs / 1000
            String.format(Locale.getDefault(), "%d:%02d / %d:%02d", curSec / 60, curSec % 60, totSec / 60, totSec % 60)
        } else {
            val estimatedSec = (message.attachmentSize / 12000L).coerceIn(1L, 300L)
            String.format(Locale.getDefault(), "0:%02d", estimatedSec)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NeoSurfaceElevated)
            .border(1.5.dp, NeoCardBorder, RoundedCornerShape(14.dp))
            .padding(8.dp)
    ) {
        IconButton(
            onClick = {
                HapticHelper.playClick(context)
                val path = message.attachmentPath
                if (!path.isNullOrBlank()) {
                    audioPlayerHelper?.play(message.id, path)
                }
            },
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(NeoAccentYellow)
                .border(1.dp, NeoCardBorder, CircleShape)
        ) {
            Icon(
                imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isThisPlaying) "Pause" else "Play",
                tint = Color.Black,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { thisProgress },
                color = NeoAccentYellow,
                trackColor = NeoCardBorder,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = durationText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeoTextSecondary
                )

                if (currentPlayingId == message.id) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(NeoBtnMint)
                            .border(1.dp, NeoCardBorder, RoundedCornerShape(6.dp))
                            .clickable {
                                HapticHelper.playClick(context)
                                audioPlayerHelper?.toggleSpeed()
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${speed}x",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF34D399)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageBubbleContent(
    message: ChatMessage,
    onImageClick: (String, String?) -> Unit
) {
    val path = message.attachmentPath
    val isTransferring = message.status == MessageStatus.SENDING && message.transferProgress < 1.0f

    val fileExists = remember(path) {
        if (path.isNullOrBlank()) false
        else when {
            path.startsWith("content://") -> true
            path.startsWith("file://") -> File(path.removePrefix("file://")).exists()
            path.startsWith("/") -> File(path).exists()
            else -> File(path).exists()
        }
    }

    Column {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(NeoSurfaceElevated)
                .border(1.5.dp, NeoCardBorder, RoundedCornerShape(12.dp))
                .clickable(enabled = path != null && fileExists && !isTransferring) {
                    path?.let { onImageClick(it, message.attachmentName) }
                }
        ) {
            if (path != null && fileExists) {
                val context = LocalContext.current
                val imageModel = remember(path) {
                    when {
                        path.startsWith("content://") || path.startsWith("file://") -> Uri.parse(path)
                        path.startsWith("/") -> Uri.fromFile(File(path))
                        else -> File(path)
                    }
                }
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageModel)
                        .crossfade(true)
                        .build(),
                    contentDescription = message.attachmentName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } else if (!isTransferring) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = "Photo",
                        tint = NeoTextSecondary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message.attachmentName ?: "Photo",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeoTextSecondary
                    )
                }
            }

            if (isTransferring) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    CircularProgressIndicator(
                        progress = { message.transferProgress },
                        color = NeoAccentYellow,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
        }

        if (message.text.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = NeoTextPrimary
            )
        }
    }
}

@Composable
private fun FileBubbleContent(message: ChatMessage) {
    val isTransferring = message.status == MessageStatus.SENDING && message.transferProgress < 1.0f

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NeoSurfaceElevated)
                .border(1.5.dp, NeoCardBorder, RoundedCornerShape(12.dp))
                .padding(8.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(NeoBtnBlue)
                    .border(1.5.dp, NeoCardBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = "File",
                    tint = NeoAccentBlue,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message.attachmentName ?: "Attachment",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeoTextPrimary,
                    maxLines = 1
                )
                Text(
                    text = FileUtils.formatFileSize(message.attachmentSize),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = NeoTextSecondary
                )
            }
        }

        if (isTransferring) {
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { message.transferProgress },
                color = NeoAccentYellow,
                trackColor = NeoCardBorder,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
        }

        if (message.text.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = NeoTextPrimary
            )
        }
    }
}

@Composable
private fun PingBubbleContent(message: ChatMessage, isMe: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pingPulse")
    val scaleAnim by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pingScale"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NeoSurfaceElevated)
            .border(1.5.dp, NeoCardBorder, RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.NotificationsActive,
            contentDescription = "Ping",
            tint = NeoAccentYellow,
            modifier = Modifier
                .size(24.dp)
                .scale(scaleAnim)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = if (isMe) "YOU SENT A PING ⚡" else "${message.senderName?.uppercase()} SENT A PING ⚡",
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = NeoTextPrimary
        )
    }
}

@Composable
private fun StatusIndicator(
    status: MessageStatus,
    onRetryClick: (() -> Unit)? = null
) {
    when (status) {
        MessageStatus.SENDING -> {
            val infiniteTransition = rememberInfiniteTransition(label = "sendingPulse")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(650),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "sendingAlpha"
            )
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = "Sending / Queued",
                tint = NeoTextSecondary.copy(alpha = alpha),
                modifier = Modifier.size(12.dp)
            )
        }
        MessageStatus.SENT -> {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Sent",
                tint = NeoTextSecondary,
                modifier = Modifier.size(13.dp)
            )
        }
        MessageStatus.DELIVERED,
        MessageStatus.READ -> {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Delivered",
                tint = if (status == MessageStatus.READ) NeoAccentBlue else NeoTextSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
        MessageStatus.FAILED -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(enabled = onRetryClick != null) { onRetryClick?.invoke() }
                    .padding(horizontal = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "Failed. Tap to retry",
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "Retry",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD32F2F)
                )
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(millis))
}
