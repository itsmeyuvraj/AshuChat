package com.ashuchat.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
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
import com.ashuchat.app.data.model.CallState
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
fun ChatScreenPaper(
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

    val launchCameraPhoto: () -> Unit = {
        try {
            val photoFile = File(context.cacheDir, "camera_photo_${System.currentTimeMillis()}.jpg")
            photoFile.parentFile?.mkdirs()
            if (!photoFile.exists()) {
                photoFile.createNewFile()
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                photoFile
            )
            cameraPhotoUri = uri
            takePhotoLauncher.launch(uri)
        } catch (e: Exception) {
            Log.e("ChatScreen", "Error launching camera: ${e.message}", e)
            Toast.makeText(
                context,
                "Unable to open camera: ${e.localizedMessage ?: "Unknown error"}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCameraPhoto()
        } else {
            Toast.makeText(
                context,
                "Camera permission is required to capture photos",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Track active chat room for heads-up notification filtering & mark messages as viewed
    DisposableEffect(activeRoomId) {
        AshuChatApp.activeChatRoom = activeRoomId
        if (activeRoomId.isNotBlank()) {
            viewModel.markRoomMessagesAsRead(activeRoomId)
        }
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

    // Auto-scroll to bottom on new messages and mark viewed
    LaunchedEffect(messages.size) {
        if (activeRoomId.isNotBlank()) {
            viewModel.markRoomMessagesAsRead(activeRoomId)
        }
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
        containerColor = com.ashuchat.app.ui.theme.NeoBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(com.ashuchat.app.ui.theme.NeoHeaderYellow)
                    .border(androidx.compose.foundation.BorderStroke(2.5.dp, com.ashuchat.app.ui.theme.NeoBlack))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        com.ashuchat.app.ui.components.NeoBrutalIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            backgroundColor = Color.White,
                            size = 40.dp,
                            onClick = {
                                viewModel.leaveActiveRoom()
                                onNavigateBack()
                            }
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        com.ashuchat.app.ui.components.NeoEmojiAvatar(
                            name = partnerName,
                            size = 40.dp
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = partnerName.uppercase(),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp,
                                color = com.ashuchat.app.ui.theme.NeoBlack,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val statusText = if (isPartnerTyping) {
                                "TYPING..."
                            } else if (connectionState == P2PConnectionState.CONNECTED_P2P) {
                                "ONLINE • DIRECT"
                            } else {
                                "OFFLINE"
                            }
                            Text(
                                text = statusText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp,
                                color = if (isPartnerTyping) Color(0xFFD84315) else if (connectionState == P2PConnectionState.CONNECTED_P2P) Color(0xFF2E7D32) else com.ashuchat.app.ui.theme.NeoBlack.copy(alpha = 0.55f)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        com.ashuchat.app.ui.components.NeoBrutalIconButton(
                            icon = Icons.Default.Phone,
                            contentDescription = "Voice Call",
                            backgroundColor = com.ashuchat.app.ui.theme.NeoCardPeach,
                            size = 40.dp,
                            onClick = {
                                val hasAudioPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.RECORD_AUDIO
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (hasAudioPermission) {
                                    viewModel.startCall()
                                } else {
                                    recordAudioLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        )

                        if (onNavigateToShowQr != null && activeRoomId.isNotBlank()) {
                            com.ashuchat.app.ui.components.NeoBrutalIconButton(
                                icon = Icons.Default.QrCode,
                                contentDescription = "Show QR",
                                backgroundColor = com.ashuchat.app.ui.theme.NeoBtnMint,
                                size = 40.dp,
                                onClick = { onNavigateToShowQr(activeRoomId) }
                            )
                        }

                        Box {
                            com.ashuchat.app.ui.components.NeoBrutalIconButton(
                                icon = Icons.Default.MoreVert,
                                contentDescription = "Room Options",
                                backgroundColor = Color.White,
                                size = 40.dp,
                                onClick = { showRoomMenu = true }
                            )

                            DropdownMenu(
                                expanded = showRoomMenu,
                                onDismissRequest = { showRoomMenu = false },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White)
                                    .border(2.dp, com.ashuchat.app.ui.theme.NeoBlack, RoundedCornerShape(16.dp))
                            ) {
                                if (onNavigateToShowQr != null && activeRoomId.isNotBlank()) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "ROOM QR CODE",
                                                fontWeight = FontWeight.Black,
                                                color = com.ashuchat.app.ui.theme.NeoBlack,
                                                fontSize = 13.sp
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.QrCode,
                                                contentDescription = null,
                                                tint = com.ashuchat.app.ui.theme.NeoBlack,
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
                                        Text(
                                            "CLEAR CHAT",
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFFD32F2F),
                                            fontSize = 13.sp
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = null,
                                            tint = Color(0xFFD32F2F),
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
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .border(androidx.compose.foundation.BorderStroke(2.5.dp, com.ashuchat.app.ui.theme.NeoBlack))
                    .imePadding()
            ) {
                // Reply Preview Bar
                if (replyingToMessage != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(com.ashuchat.app.ui.theme.NeoCardYellow)
                            .border(androidx.compose.foundation.BorderStroke(1.5.dp, com.ashuchat.app.ui.theme.NeoBlack))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(32.dp)
                                .background(com.ashuchat.app.ui.theme.NeoBlack)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "REPLYING TO ${(replyingToMessage?.senderName ?: "Peer").uppercase()}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = com.ashuchat.app.ui.theme.NeoBlack
                            )
                            Text(
                                text = replyingToMessage?.text?.ifBlank { replyingToMessage?.attachmentName ?: "Attachment" } ?: "",
                                fontSize = 12.sp,
                                color = com.ashuchat.app.ui.theme.NeoBlack.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = {
                                com.ashuchat.app.utils.HapticHelper.playClick(context)
                                viewModel.setReplyingTo(null)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel reply",
                                tint = com.ashuchat.app.ui.theme.NeoBlack,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Message Input Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    if (isRecordingVoice) {
                        // Voice Note Recording Mode
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(com.ashuchat.app.ui.theme.NeoBackground)
                                .border(2.dp, com.ashuchat.app.ui.theme.NeoBlack, RoundedCornerShape(16.dp))
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
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color.Red.copy(alpha = dotAlpha))
                                    .border(1.dp, com.ashuchat.app.ui.theme.NeoBlack, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            val sec = voiceRecordDurationMs / 1000
                            Text(
                                text = String.format(Locale.getDefault(), "%d:%02d", sec / 60, sec % 60),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = com.ashuchat.app.ui.theme.NeoBlack
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(18.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val barCount = 14
                                    for (i in 0 until barCount) {
                                        val factor = ((voiceAmplitude * 1.5f + (i * 0.05f)) % 1f).coerceIn(0.2f, 1f)
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .height((factor * 18).dp)
                                                .clip(RoundedCornerShape(1.5.dp))
                                                .background(com.ashuchat.app.ui.theme.NeoBlack)
                                        )
                                    }
                                }
                            }

                            com.ashuchat.app.ui.components.NeoBrutalIconButton(
                                icon = Icons.Default.Delete,
                                contentDescription = "Cancel recording",
                                backgroundColor = Color(0xFFFF6B6B),
                                size = 34.dp,
                                onClick = { viewModel.cancelVoiceRecording() }
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        com.ashuchat.app.ui.components.NeoBrutalIconButton(
                            icon = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Voice Note",
                            backgroundColor = com.ashuchat.app.ui.theme.NeoHeaderYellow,
                            size = 46.dp,
                            onClick = { viewModel.stopAndSendVoiceRecording() }
                        )
                    } else {
                        // Attachments button
                        com.ashuchat.app.ui.components.NeoBrutalIconButton(
                            icon = Icons.Default.Add,
                            contentDescription = "Attach",
                            backgroundColor = com.ashuchat.app.ui.theme.NeoBtnMint,
                            size = 44.dp,
                            onClick = { showAttachmentSheet = true }
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Text Input Field
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .border(2.dp, com.ashuchat.app.ui.theme.NeoBlack, RoundedCornerShape(16.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            if (inputText.isEmpty()) {
                                Text(
                                    text = "Type message...",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = com.ashuchat.app.ui.theme.NeoBlack.copy(alpha = 0.55f)
                                )
                            }
                            BasicTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                textStyle = TextStyle(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = com.ashuchat.app.ui.theme.NeoBlack
                                ),
                                cursorBrush = SolidColor(com.ashuchat.app.ui.theme.NeoBlack),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        val isSendEnabled = inputText.trim().isNotEmpty()
                        if (isSendEnabled) {
                            com.ashuchat.app.ui.components.NeoBrutalIconButton(
                                icon = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                backgroundColor = com.ashuchat.app.ui.theme.NeoHeaderYellow,
                                size = 44.dp,
                                onClick = {
                                    viewModel.sendTextMessage(inputText)
                                    inputText = ""
                                }
                            )
                        } else {
                            com.ashuchat.app.ui.components.NeoBrutalIconButton(
                                icon = Icons.Default.Mic,
                                contentDescription = "Record Voice Note",
                                backgroundColor = com.ashuchat.app.ui.theme.NeoCardPeach,
                                size = 44.dp,
                                onClick = {
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
                            )
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
                            .background(com.ashuchat.app.ui.theme.NeoCardPeach)
                            .border(androidx.compose.foundation.BorderStroke(1.5.dp, com.ashuchat.app.ui.theme.NeoBlack))
                            .padding(vertical = 8.dp, horizontal = 16.dp)
                    ) {
                        Text(
                            text = "$partnerName is offline • Messages queued for direct delivery",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = com.ashuchat.app.ui.theme.NeoBlack
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (messages.isEmpty()) {
                        // Neo Brutal comic empty state
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            com.ashuchat.app.ui.components.NeoChatBubbleIcon(
                                modifier = Modifier.size(68.dp)
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                text = "READY TO CHAT!",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp,
                                color = com.ashuchat.app.ui.theme.NeoBlack
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Send a text, photo, audio note, or tap + to ping $partnerName",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = com.ashuchat.app.ui.theme.NeoBlack.copy(alpha = 0.7f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            com.ashuchat.app.ui.components.NeoPillBadge(
                                text = "⚡ DIRECT P2P ENCRYPTION",
                                backgroundColor = com.ashuchat.app.ui.theme.NeoCardYellow
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
                val hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED

                if (hasCameraPermission) {
                    launchCameraPhoto()
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
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
                Text(
                    text = "CLEAR CHAT HISTORY?",
                    fontWeight = FontWeight.Black,
                    color = com.ashuchat.app.ui.theme.NeoBlack
                )
            },
            text = {
                Text(
                    text = "This will permanently remove all messages and media records for this room on this device.",
                    color = com.ashuchat.app.ui.theme.NeoBlack.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearRoomDialog = false
                        viewModel.clearChat()
                    }
                ) {
                    Text(
                        "CLEAR",
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFD32F2F)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearRoomDialog = false }) {
                    Text(
                        "CANCEL",
                        fontWeight = FontWeight.Bold,
                        color = com.ashuchat.app.ui.theme.NeoBlack
                    )
                }
            },
            containerColor = Color.White
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
}


@Composable
private fun PingAlertBanner(partnerName: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        com.ashuchat.app.ui.components.NeoPillBadge(
            text = "⚡ PING RECEIVED FROM ${partnerName.uppercase()}!",
            backgroundColor = com.ashuchat.app.ui.theme.NeoCardYellow,
            borderWidth = 2.dp,
            shadowOffsetX = 3.dp,
            shadowOffsetY = 3.dp
        )
    }
}
