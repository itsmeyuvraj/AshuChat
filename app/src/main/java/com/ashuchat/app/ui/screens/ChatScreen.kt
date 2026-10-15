package com.ashuchat.app.ui.screens

import android.net.Uri
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val isPartnerTyping by viewModel.isPartnerTyping.collectAsState()
    val showLoveEffect by viewModel.showLoveHeartEffect.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var activeViewerMedia by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var cameraPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val listState = rememberLazyListState()

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

    Scaffold(
        containerColor = MidnightBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceElevated
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
                                text = viewModel.prefs.partnerName.take(1).uppercase(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = "${viewModel.prefs.partnerName} ❤️",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            if (isPartnerTyping) {
                                Text(
                                    text = "typing...",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = RoseAccent
                                )
                            } else {
                                ConnectionBadge(state = connectionState)
                            }
                        }
                    }
                },
                actions = {
                    // Quick Love Ping Button
                    IconButton(
                        onClick = { viewModel.sendLovePing() },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(RosePrimary.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Love Ping",
                            tint = RosePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = TextSecondary
                        )
                    }
                }
            )
        },
        bottomBar = {
            // Message Input Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceElevated)
                    .imePadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
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
                            text = "Message ${viewModel.prefs.partnerName}...",
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

                // Send Button
                val isSendEnabled = inputText.trim().isNotEmpty()
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSendEnabled) {
                                Brush.linearGradient(listOf(RosePrimary, RoseDark))
                            } else {
                                Brush.linearGradient(listOf(SurfaceCard, SurfaceCard))
                            }
                        )
                        .clickable(enabled = isSendEnabled) {
                            viewModel.sendTextMessage(inputText)
                            inputText = ""
                        }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (isSendEnabled) Color.White else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
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
                            .background(Color(0xFF221738))
                            .padding(vertical = 6.dp, horizontal = 16.dp)
                    ) {
                        Text(
                            text = "☁️ ${viewModel.prefs.partnerName} is offline • Messages deliver instantly to cloud mailbox",
                            fontSize = 11.sp,
                            color = Color(0xFFDDD2F5)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (messages.isEmpty()) {
                        // Empty romantic placeholder
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
                                    .background(RosePrimary.copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = RosePrimary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "AshuChat Room Ready",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Send your first message or a Love Ping to Ashu ❤️",
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
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Floating Love Animation Overlay
            androidx.compose.animation.AnimatedVisibility(
                visible = showLoveEffect,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FloatingHeartsOverlay()
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
            onSendLovePing = {
                viewModel.sendLovePing()
            }
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
private fun FloatingHeartsOverlay() {
    val heartCount = 16
    Box(modifier = Modifier.fillMaxSize()) {
        for (i in 0 until heartCount) {
            FloatingSingleHeart(index = i)
        }
    }
}

@Composable
private fun FloatingSingleHeart(index: Int) {
    val randomX = remember { Random.nextFloat() * 320f }
    val randomDelay = remember { Random.nextInt(0, 400) }
    val randomSize = remember { Random.nextInt(24, 48) }

    val transition = rememberInfiniteTransition(label = "floatingHeart")
    val offsetY by transition.animateFloat(
        initialValue = 800f,
        targetValue = -100f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000 + (index * 80), delayMillis = randomDelay, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "heartY"
    )

    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000 + (index * 80), delayMillis = randomDelay, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "heartAlpha"
    )

    Icon(
        imageVector = Icons.Default.Favorite,
        contentDescription = null,
        tint = RosePrimary,
        modifier = Modifier
            .offset { IntOffset(randomX.dp.roundToPx(), offsetY.toInt()) }
            .size(randomSize.dp)
            .alpha(alpha)
    )
}
