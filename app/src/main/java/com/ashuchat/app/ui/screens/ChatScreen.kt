package com.ashuchat.app.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import com.ashuchat.app.ui.viewmodel.CallState
import java.util.Locale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.ashuchat.app.ui.components.AttachmentPickerSheet
import com.ashuchat.app.ui.components.ChatBubble
import com.ashuchat.app.ui.components.ConnectionBadge
import com.ashuchat.app.ui.components.MediaViewerDialog
import com.ashuchat.app.ui.theme.BrandAccent
import com.ashuchat.app.ui.theme.BrandDark
import com.ashuchat.app.ui.theme.BrandLight
import com.ashuchat.app.ui.theme.BrandPrimary
import com.ashuchat.app.ui.theme.MidnightBg
import com.ashuchat.app.ui.theme.RoseAccent
import com.ashuchat.app.ui.theme.RoseDark
import com.ashuchat.app.ui.theme.RosePrimary
import com.ashuchat.app.ui.theme.SurfaceBorder
import com.ashuchat.app.ui.theme.SurfaceCard
import com.ashuchat.app.ui.theme.SurfaceElevated
import com.ashuchat.app.ui.theme.TextMuted
import com.ashuchat.app.ui.theme.TextPrimary
import com.ashuchat.app.ui.theme.TextSecondary
import com.ashuchat.app.ui.viewmodel.ChatViewModel
import com.ashuchat.app.webrtc.P2PConnectionState
import kotlinx.coroutines.delay
import java.io.File
import kotlin.random.Random

import com.ashuchat.app.ui.theme.OfflineRed
import com.ashuchat.app.AshuChatApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToShowQr: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val partnerName by viewModel.partnerName.collectAsState()
    val activeRoomId by viewModel.activeRoomId.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val isPartnerTyping by viewModel.isPartnerTyping.collectAsState()
    val showPingEffect by viewModel.showPingEffect.collectAsState()
    val replyingToMessage by viewModel.replyingToMessage.collectAsState()
    val isRecordingVoice by viewModel.isRecordingVoice.collectAsState()
    val voiceRecordDurationMs by viewModel.voiceRecordDurationMs.collectAsState()
    val voiceAmplitude by viewModel.voiceAmplitude.collectAsState()
    val callState by viewModel.callState.collectAsState()
    val callDurationSeconds by viewModel.callDurationSeconds.collectAsState()
    val isCallMuted by viewModel.isCallMuted.collectAsState()
    val isCallSpeakerOn by viewModel.isCallSpeakerOn.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var activeViewerMedia by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var cameraPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var showRoomMenu by remember { mutableStateOf(false) }
    var showClearRoomDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    val recordAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) {
            viewModel.startVoiceRecording()
        }
    }

    // Activity Result Launchers
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.sendAttachment(it) }
    }

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.sendAttachment(it) }
    }

    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            cameraPhotoUri?.let { viewModel.sendAttachment(it) }
        }
    }

    // Track active chat room for heads-up notification filtering
    DisposableEffect(activeRoomId) {
        AshuChatApp.activeChatRoom = activeRoomId
        onDispose {
            if (AshuChatApp.activeChatRoom == activeRoomId) {
                AshuChatApp.activeChatRoom = null
            }
        }
    }

    // Connect WebRTC upon entering
    LaunchedEffect(Unit) {
        viewModel.startConnection()
    }

    // Auto-scroll to bottom on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Typing debounce
    LaunchedEffect(inputText) {
        if (inputText.isNotBlank()) {
            viewModel.setTyping(true)
            delay(2000)
            viewModel.setTyping(false)
        }
    }

    // Intercept system back gesture and back button
    BackHandler {
        when {
            activeViewerMedia != null -> {
                activeViewerMedia = null
            }
            showAttachmentSheet -> {
                showAttachmentSheet = false
            }
            else -> {
                viewModel.leaveActiveRoom()
                onNavigateBack()
            }
        }
    }

    Scaffold(
        containerColor = MidnightBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceElevated
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.leaveActiveRoom()
                        onNavigateBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Partner Avatar
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(RosePrimary, RoseDark))
                                )
                        ) {
                            Text(
                                text = partnerName.take(1).uppercase(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = partnerName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                if (activeRoomId.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "• $activeRoomId",
                                        fontSize = 12.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                            if (isPartnerTyping) {
                                Text(
                                    text = "typing...",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = BrandAccent
                                )
                            } else {
                                ConnectionBadge(state = connectionState)
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val hasAudioPermission = ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.RECORD_AUDIO
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (hasAudioPermission) {
                            viewModel.startCall()
                        } else {
                            recordAudioLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Voice Call",
                            tint = RoseAccent
                        )
                    }

                    if (onNavigateToShowQr != null && activeRoomId.isNotBlank()) {
                        IconButton(onClick = { onNavigateToShowQr(activeRoomId) }) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = "Show QR",
                                tint = TextSecondary
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { showRoomMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Room Options",
                                tint = TextSecondary
                            )
                        }

                        DropdownMenu(
                            expanded = showRoomMenu,
                            onDismissRequest = { showRoomMenu = false },
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceElevated)
                                .border(1.dp, SurfaceBorder, RoundedCornerShape(16.dp))
                        ) {
                            if (onNavigateToShowQr != null && activeRoomId.isNotBlank()) {
                                DropdownMenuItem(
                                    text = {
                                        Text("Room QR Code", color = TextPrimary, fontSize = 14.sp)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.QrCode,
                                            contentDescription = null,
                                            tint = BrandLight,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        showRoomMenu = false
                                        onNavigateToShowQr(activeRoomId)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text("Clear Chat", color = OfflineRed, fontSize = 14.sp)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = OfflineRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showRoomMenu = false
                                    showClearRoomDialog = true
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceElevated)
                    .imePadding()
            ) {
                // Reply Preview Bar
                if (replyingToMessage != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1F1B2B))
                            .border(1.dp, SurfaceBorder)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(32.dp)
                                .background(RoseAccent)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Replying to ${replyingToMessage?.senderName ?: "Peer"}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = RoseAccent
                            )
                            Text(
                                text = replyingToMessage?.text?.ifBlank { replyingToMessage?.attachmentName ?: "Attachment" } ?: "",
                                fontSize = 12.sp,
                                color = TextPrimary.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { viewModel.setReplyingTo(null) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel reply",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Message Input Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    if (isRecordingVoice) {
                        // Voice Note Recording Mode
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0xFF2A1C25))
                                .border(1.dp, RoseAccent.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            val infiniteTransition = rememberInfiniteTransition(label = "recDot")
                            val dotAlpha by infiniteTransition.animateFloat(
                                initialValue = 0.2f,
                                targetValue = 1.0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(500),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "dot"
                            )
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color.Red.copy(alpha = dotAlpha))
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            val sec = voiceRecordDurationMs / 1000
                            Text(
                                text = String.format(Locale.getDefault(), "%d:%02d", sec / 60, sec % 60),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val barCount = 12
                                    for (i in 0 until barCount) {
                                        val factor = ((voiceAmplitude * 1.5f + (i * 0.05f)) % 1f).coerceIn(0.2f, 1f)
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .height((factor * 16).dp)
                                                .clip(RoundedCornerShape(1.5.dp))
                                                .background(RoseAccent)
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { viewModel.cancelVoiceRecording() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Cancel recording",
                                    tint = OfflineRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(RosePrimary, RoseDark)))
                                .clickable {
                                    viewModel.stopAndSendVoiceRecording()
                                }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Voice Note",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        // Attachments button
                        IconButton(
                            onClick = { showAttachmentSheet = true },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(SurfaceCard)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Attach",
                                tint = RosePrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Text Input Field
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(24.dp))
                                .background(SurfaceCard)
                                .border(1.dp, SurfaceBorder, RoundedCornerShape(24.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            if (inputText.isEmpty()) {
                                Text(
                                    text = "Message $partnerName...",
                                    fontSize = 14.sp,
                                    color = TextMuted
                                )
                            }
                            BasicTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                textStyle = TextStyle(
                                    fontSize = 14.sp,
                                    color = TextPrimary
                                ),
                                cursorBrush = SolidColor(RosePrimary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        val isSendEnabled = inputText.trim().isNotEmpty()
                        if (isSendEnabled) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(RosePrimary, RoseDark)))
                                    .clickable {
                                        viewModel.sendTextMessage(inputText)
                                        inputText = ""
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceCard)
                                    .clickable {
                                        val hasAudioPermission = ContextCompat.checkSelfPermission(
                                            context,
                                            android.Manifest.permission.RECORD_AUDIO
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        if (hasAudioPermission) {
                                            viewModel.startVoiceRecording()
                                        } else {
                                            recordAudioLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Record Voice Note",
                                    tint = RosePrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = connectionState == P2PConnectionState.WAITING_FOR_PEER,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF151B26))
                            .border(1.dp, SurfaceBorder, RoundedCornerShape(0.dp))
                            .padding(vertical = 6.dp, horizontal = 16.dp)
                    ) {
                        Text(
                            text = "$partnerName is offline • Messages will deliver when they open the app",
                            fontSize = 11.sp,
                            color = BrandLight
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (messages.isEmpty()) {
                        // Empty professional placeholder
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(BrandPrimary.copy(alpha = 0.15f))
                                    .border(1.dp, BrandPrimary.copy(alpha = 0.35f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = BrandLight,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Ready to chat",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Send a message, photo, or file to $partnerName",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 8.dp)
                        ) {
                            items(messages, key = { it.id }) { message ->
                                ChatBubble(
                                    message = message,
                                    onImageClick = { filePath, fileName ->
                                        activeViewerMedia = Pair(filePath, fileName)
                                    },
                                    onRetryClick = {
                                        viewModel.retryMessage(it.id)
                                    },
                                    onReplyClick = {
                                        viewModel.setReplyingTo(it)
                                    },
                                    onReactionClick = { msgId, emoji ->
                                        viewModel.sendReaction(msgId, emoji)
                                    },
                                    audioPlayerHelper = viewModel.audioPlayerHelper
                                )
                            }
                        }
                    }
                }
            }

            // Ping / Nudge Alert Banner Overlay
            androidx.compose.animation.AnimatedVisibility(
                visible = showPingEffect,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
            ) {
                PingAlertBanner(partnerName = partnerName)
            }
        }
    }

    // Attachment Picker Bottom Sheet
    if (showAttachmentSheet) {
        AttachmentPickerSheet(
            onDismiss = { showAttachmentSheet = false },
            onPickImage = {
                pickImageLauncher.launch("image/*")
            },
            onPickFile = {
                pickFileLauncher.launch("*/*")
            },
            onTakePhoto = {
                val photoFile = File(context.cacheDir, "camera_photo_${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    photoFile
                )
                cameraPhotoUri = uri
                takePhotoLauncher.launch(uri)
            },
            onSendPing = {
                viewModel.sendPing()
            }
        )
    }

    if (showClearRoomDialog) {
        AlertDialog(
            onDismissRequest = { showClearRoomDialog = false },
            title = {
                Text(text = "Clear Room Chat?", color = TextPrimary)
            },
            text = {
                Text(
                    text = "This will permanently delete all messages and media records for this chat.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearRoomDialog = false
                        viewModel.clearChat()
                    }
                ) {
                    Text("Clear", color = OfflineRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearRoomDialog = false }) {
                    Text("Cancel", color = TextPrimary)
                }
            },
            containerColor = SurfaceElevated
        )
    }

    // Media Viewer Fullscreen Dialog
    activeViewerMedia?.let { (filePath, fileName) ->
        MediaViewerDialog(
            filePath = filePath,
            fileName = fileName,
            onDismiss = { activeViewerMedia = null }
        )
    }

    // Voice Call Overlay
    if (callState != CallState.IDLE) {
        VoiceCallOverlay(
            callState = callState,
            partnerName = partnerName,
            callDurationSeconds = callDurationSeconds,
            isMuted = isCallMuted,
            isSpeakerOn = isCallSpeakerOn,
            onAccept = { viewModel.acceptIncomingCall() },
            onReject = { viewModel.rejectIncomingCall() },
            onEnd = { viewModel.endCall() },
            onToggleMute = { viewModel.toggleCallMute() },
            onToggleSpeaker = { viewModel.toggleCallSpeaker() }
        )
    }
}

@Composable
private fun VoiceCallOverlay(
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
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Accept",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                            tint = Color.White,
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
                            imageVector = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = "Speaker",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PingAlertBanner(partnerName: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceCard)
                .border(1.dp, BrandAccent.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = BrandAccent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Ping received from $partnerName",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }
    }
}
