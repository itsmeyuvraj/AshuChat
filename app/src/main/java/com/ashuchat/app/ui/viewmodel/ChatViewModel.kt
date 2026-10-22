package com.ashuchat.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ashuchat.app.data.local.AppDatabase
import com.ashuchat.app.data.local.RoomEntity
import com.ashuchat.app.data.model.ChatMessage
import com.ashuchat.app.data.model.DataChannelPacket
import com.ashuchat.app.data.model.MessageStatus
import com.ashuchat.app.data.model.MessageType
import com.ashuchat.app.data.repository.ChatRepository
import com.ashuchat.app.data.repository.PreferencesRepository
import com.ashuchat.app.AshuChatApp
import com.ashuchat.app.utils.AppUpdateInfo
import com.ashuchat.app.utils.FileUtils
import com.ashuchat.app.utils.NetworkMonitor
import com.ashuchat.app.utils.NotificationHelper
import com.ashuchat.app.utils.AudioPlayerHelper
import com.ashuchat.app.utils.AudioRecorderHelper
import com.ashuchat.app.utils.UpdateManager
import com.ashuchat.app.webrtc.P2PConnectionState
import com.ashuchat.app.webrtc.WebRtcManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import java.io.File
import java.util.UUID
import com.ashuchat.app.data.model.CallState
import com.ashuchat.app.webrtc.ChatConnectionManager

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    val chatRepository: ChatRepository get() = ChatConnectionManager.chatRepository
    val prefs: PreferencesRepository get() = ChatConnectionManager.prefs
    val updateManager = UpdateManager(application)
    val networkMonitor: NetworkMonitor get() = ChatConnectionManager.networkMonitor

    val webRtcManager: WebRtcManager get() = ChatConnectionManager.webRtcManager

    val connectionState: StateFlow<P2PConnectionState> get() = ChatConnectionManager.connectionState

    // In-App Auto-Update State
    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo: StateFlow<AppUpdateInfo?> = _updateInfo.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    private val _isDownloadingUpdate = MutableStateFlow(false)
    val isDownloadingUpdate: StateFlow<Boolean> = _isDownloadingUpdate.asStateFlow()

    private val _updateCheckMessage = MutableStateFlow<String?>(null)
    val updateCheckMessage: StateFlow<String?> = _updateCheckMessage.asStateFlow()

    // WhatsApp-style Room List
    val rooms: StateFlow<List<RoomEntity>> = chatRepository.getAllRooms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Room Identifier
    val activeRoomId: StateFlow<String> get() = ChatConnectionManager.activeRoomId
    val partnerName: StateFlow<String> get() = ChatConnectionManager.partnerName

    val isPartnerTyping: StateFlow<Boolean> get() = ChatConnectionManager.isPartnerTyping
    val showPingEffect: StateFlow<Boolean> get() = ChatConnectionManager.showPingEffect
    val showLoveHeartEffect: StateFlow<Boolean> get() = showPingEffect

    private val _activeFileProgress = MutableStateFlow<Map<String, Float>>(emptyMap())

    // Messages for the currently active room with ongoing file progress
    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<ChatMessage>> = activeRoomId
        .flatMapLatest { roomId ->
            if (roomId.isBlank()) {
                flowOf(emptyList())
            } else {
                combine(
                    chatRepository.getMessagesForRoom(roomId),
                    _activeFileProgress
                ) { msgList, progressMap ->
                    msgList.map { msg ->
                        val progress = progressMap[msg.id] ?: 1.0f
                        if (progress < 1.0f) {
                            msg.copy(transferProgress = progress)
                        } else {
                            msg
                        }
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Swipe-to-Reply
    private val _replyingToMessage = MutableStateFlow<ChatMessage?>(null)
    val replyingToMessage: StateFlow<ChatMessage?> = _replyingToMessage.asStateFlow()

    // Push-to-Talk Voice Notes
    val audioRecorderHelper = AudioRecorderHelper(application)
    val audioPlayerHelper = AudioPlayerHelper(application)

    private val _isRecordingVoice = MutableStateFlow(false)
    val isRecordingVoice: StateFlow<Boolean> = _isRecordingVoice.asStateFlow()

    private val _voiceRecordDurationMs = MutableStateFlow(0L)
    val voiceRecordDurationMs: StateFlow<Long> = _voiceRecordDurationMs.asStateFlow()

    private val _voiceAmplitude = MutableStateFlow(0f)
    val voiceAmplitude: StateFlow<Float> = _voiceAmplitude.asStateFlow()

    private var voiceTimerJob: Job? = null

    // P2P Voice Calling
    val callState: StateFlow<CallState> get() = ChatConnectionManager.callState
    val callDurationSeconds: StateFlow<Int> get() = ChatConnectionManager.callDurationSeconds
    val isCallMuted: StateFlow<Boolean> get() = ChatConnectionManager.isCallMuted
    val isCallSpeakerOn: StateFlow<Boolean> get() = ChatConnectionManager.isCallSpeakerOn

    init {
        ChatConnectionManager.init(application)
        observeFileTransfers()
        observeConnectionChanges()
        observeNetworkState()
        observePeerNameSync()
        seedInitialRoomIfAny()

        // Periodic cleanup of expired disappearing messages
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(3000)
                try {
                    chatRepository.deleteExpiredMessages(getApplication())
                } catch (e: Exception) {
                    Log.w(TAG, "Error cleaning expired messages: ${e.localizedMessage}")
                }
            }
        }

        // Silently check for app updates in background after startup
        viewModelScope.launch {
            delay(2500)
            checkForUpdates(silent = true)
        }
    }

    private fun observeNetworkState() {
        viewModelScope.launch {
            networkMonitor.isConnected.collect { isConnected ->
                if (isConnected) {
                    flushAllPendingMessages()
                    val currentRoom = activeRoomId.value.ifBlank { prefs.roomId.trim().uppercase() }
                    if (currentRoom.isNotBlank() && !webRtcManager.isP2pConnected()) {
                        startConnection()
                    }
                }
            }
        }
    }

    private fun seedInitialRoomIfAny() {
        viewModelScope.launch(Dispatchers.IO) {
            val initialRoom = prefs.roomId.trim().uppercase()
            if (initialRoom.isNotBlank()) {
                val existing = chatRepository.getRoom(initialRoom)
                if (existing == null) {
                    chatRepository.insertOrUpdateRoom(
                        RoomEntity(
                            roomId = initialRoom,
                            partnerName = prefs.partnerName.ifBlank { "Peer" },
                            lastMessage = "Tap to chat",
                            lastTimestamp = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    fun openRoom(roomId: String, forceReconnect: Boolean = false) {
        val clean = roomId.trim().uppercase()
        if (clean.isBlank()) return
        ChatConnectionManager.openRoom(clean, forceReconnect)
        flushPendingMessages()
    }

    fun createOrJoinRoom(roomId: String, partnerName: String = "Peer"): String {
        val clean = roomId.trim().uppercase()
        if (clean.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                val existing = chatRepository.getRoom(clean)
                if (existing == null) {
                    chatRepository.insertOrUpdateRoom(
                        RoomEntity(
                            roomId = clean,
                            partnerName = partnerName,
                            lastMessage = "Room created",
                            lastTimestamp = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
        return clean
    }

    fun leaveActiveRoom() {
        webRtcManager.stop()
        ChatConnectionManager.openRoom("")
    }

    fun deleteRoom(roomId: String) {
        val clean = roomId.trim().uppercase()
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.deleteRoom(clean)
            if (activeRoomId.value == clean) {
                leaveActiveRoom()
            }
        }
    }

    fun startConnection() {
        ChatConnectionManager.start()
    }

    private fun observePeerNameSync() {
        viewModelScope.launch {
            webRtcManager.peerNameFlow.collect { name ->
                updatePartnerName(name)
            }
        }
    }

    fun updatePartnerName(name: String) {
        ChatConnectionManager.updatePartnerName(name)
    }

    fun updateMyName(name: String) {
        val clean = name.trim()
        if (clean.isNotBlank()) {
            prefs.myName = clean
            if (webRtcManager.isP2pConnected()) {
                webRtcManager.sendPacket(
                    DataChannelPacket(
                        type = "name_sync",
                        senderName = clean
                    )
                )
            }
        }
    }

    private fun observeConnectionChanges() {
        viewModelScope.launch {
            webRtcManager.connectionState.collect { state ->
                if (state == P2PConnectionState.CONNECTED_P2P) {
                    flushPendingMessages()
                }
            }
        }
    }

    // Incoming packets and calls are handled globally and persistently by ChatConnectionManager

    private fun observeFileTransfers() {
        viewModelScope.launch {
            webRtcManager.fileProgressFlow.collect { (fileId, progress) ->
                _activeFileProgress.value = _activeFileProgress.value + (fileId to progress)
            }
        }

        viewModelScope.launch {
            webRtcManager.fileCompleteFlow.collect { event ->
                val messageId = event.messageId
                val localPath = event.localPath
                val isIncoming = event.isIncoming
                val disappearingDuration = event.disappearingDuration

                val currentMap = _activeFileProgress.value.toMutableMap()
                currentMap.remove(messageId)
                _activeFileProgress.value = currentMap

                val currentRoom = activeRoomId.value.ifBlank { prefs.roomId.trim().uppercase() }
                val isViewed = AshuChatApp.isAppInForeground && AshuChatApp.activeChatRoom == currentRoom
                val initialStatus = if (isIncoming) {
                    if (isViewed) MessageStatus.READ else MessageStatus.DELIVERED
                } else MessageStatus.SENT

                val expiresAt = if (isIncoming && isViewed && disappearingDuration != null && disappearingDuration > 0) {
                    System.currentTimeMillis() + (disappearingDuration * 1000L)
                } else null

                val existing = chatRepository.getMessage(messageId)
                if (existing != null) {
                    chatRepository.updateAttachmentPath(messageId, localPath)
                    chatRepository.updateMessageStatus(messageId, initialStatus)
                    if (isIncoming && isViewed) {
                        if (expiresAt != null) chatRepository.updateMessageExpiration(messageId, expiresAt)
                        ChatConnectionManager.sendReadAck(messageId, currentRoom)
                    }
                } else {
                    val isImage = FileUtils.isImage(localPath, null)
                    val fileMsg = ChatMessage(
                        id = messageId,
                        roomId = currentRoom,
                        senderName = partnerName.value,
                        isFromMe = !isIncoming,
                        text = "",
                        type = if (isImage) MessageType.IMAGE else MessageType.FILE,
                        timestamp = System.currentTimeMillis(),
                        status = initialStatus,
                        attachmentPath = localPath,
                        attachmentName = File(localPath).name,
                        transferProgress = 1.0f,
                        disappearingDuration = disappearingDuration,
                        expiresAt = expiresAt
                    )
                    chatRepository.insertMessage(fileMsg)
                    if (isIncoming && isViewed) {
                        ChatConnectionManager.sendReadAck(messageId, currentRoom)
                    }
                }

                if (isIncoming && !isViewed) {
                    NotificationHelper.showMessageNotification(
                        getApplication(),
                        partnerName.value,
                        "Received attachment"
                    )
                }
            }
        }

        viewModelScope.launch {
            webRtcManager.fileFailedFlow.collect { (messageId, error) ->
                Log.e(TAG, "File transfer failed callback for $messageId: $error")
                val currentMap = _activeFileProgress.value.toMutableMap()
                currentMap.remove(messageId)
                _activeFileProgress.value = currentMap
                chatRepository.updateMessageStatus(messageId, MessageStatus.FAILED)
            }
        }
    }

    fun sendTextMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val currentRoom = activeRoomId.value.ifBlank { prefs.roomId.trim().uppercase() }
        val messageId = UUID.randomUUID().toString()
        val replyingTo = _replyingToMessage.value
        _replyingToMessage.value = null

        val disappearingDuration = prefs.disappearingDurationSeconds
        val durationToUse = if (disappearingDuration > 0) disappearingDuration else null

        val chatMessage = ChatMessage(
            id = messageId,
            roomId = currentRoom,
            senderName = prefs.myName,
            isFromMe = true,
            text = trimmed,
            type = MessageType.TEXT,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENDING,
            replyToId = replyingTo?.id,
            replyToText = replyingTo?.text?.ifBlank { replyingTo.attachmentName ?: "Attachment" },
            replyToSender = replyingTo?.senderName,
            expiresAt = null,
            disappearingDuration = durationToUse
        )

        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.insertMessage(chatMessage)

            val packet = DataChannelPacket(
                type = "text",
                messageId = messageId,
                senderName = prefs.myName,
                text = trimmed,
                timestamp = chatMessage.timestamp,
                replyToId = chatMessage.replyToId,
                replyToText = chatMessage.replyToText,
                replyToSender = chatMessage.replyToSender,
                disappearingDuration = durationToUse
            )

            // Direct P2P send if open
            if (webRtcManager.isP2pConnected()) {
                val sent = webRtcManager.sendPacket(packet)
                if (sent) {
                    chatRepository.updateMessageStatus(messageId, MessageStatus.SENT)
                    // P2P ACK watchdog: if peer does not acknowledge within 4.5s, fallback to cloud relay
                    launch {
                        delay(4500)
                        val msg = chatRepository.getMessage(messageId)
                        if (msg != null && msg.status != MessageStatus.DELIVERED && msg.status != MessageStatus.READ) {
                            webRtcManager.sendOfflinePacketForRoom(currentRoom, packet)
                        }
                    }
                    return@launch
                }
            }

            // Partner offline or P2P not yet open: deliver via Serverless Cloud Mailbox
            webRtcManager.sendOfflinePacketForRoom(currentRoom, packet) { success ->
                viewModelScope.launch(Dispatchers.IO) {
                    val newStatus = if (success) MessageStatus.SENT else MessageStatus.SENDING
                    chatRepository.updateMessageStatus(messageId, newStatus)
                }
            }
        }
    }

    fun sendAttachment(uri: Uri) {
        val context = getApplication<Application>()
        val fileName = FileUtils.getFileName(context, uri)
        val fileSize = FileUtils.getFileSize(context, uri)
        val mimeType = FileUtils.getMimeType(context, uri)
        val isImage = FileUtils.isImage(fileName, mimeType)

        // Save permanent local copy for sender so photo is immediately visible and persistent
        val permanentLocalPath = try {
            FileUtils.copyUriToAppStorage(context, uri, fileName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed copying attachment to app storage: ${e.localizedMessage}")
            uri.toString()
        }

        val fileUriToSend = if (permanentLocalPath.startsWith("/")) {
            Uri.fromFile(File(permanentLocalPath))
        } else {
            uri
        }

        val currentRoom = activeRoomId.value.ifBlank { prefs.roomId.trim().uppercase() }
        val messageId = UUID.randomUUID().toString()
        val disappearingDuration = prefs.disappearingDurationSeconds
        val durationToUse = if (disappearingDuration > 0) disappearingDuration else null

        val chatMessage = ChatMessage(
            id = messageId,
            roomId = currentRoom,
            senderName = prefs.myName,
            isFromMe = true,
            text = "",
            type = if (isImage) MessageType.IMAGE else MessageType.FILE,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENDING,
            attachmentPath = permanentLocalPath,
            attachmentName = fileName,
            attachmentSize = fileSize,
            mimeType = mimeType,
            transferProgress = 1.0f, // Local sender already has file available
            expiresAt = null,
            disappearingDuration = durationToUse
        )

        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.insertMessage(chatMessage)

            if (isImage) {
                // Compress image for ultra-reliable transfer across both P2P and cloud mailbox
                val compressedBytes = FileUtils.compressImageForTransfer(context, fileUriToSend)
                if (compressedBytes != null && compressedBytes.size < 2 * 1024 * 1024) {
                    val base64 = android.util.Base64.encodeToString(compressedBytes, android.util.Base64.NO_WRAP)
                    val imagePacket = DataChannelPacket(
                        type = "image",
                        messageId = messageId,
                        senderName = prefs.myName,
                        fileName = fileName,
                        fileSize = compressedBytes.size.toLong(),
                        mimeType = "image/jpeg",
                        timestamp = chatMessage.timestamp,
                        dataBase64 = base64,
                        disappearingDuration = durationToUse
                    )

                    // 1. Direct P2P send if WebRTC DataChannel is open
                    if (webRtcManager.isP2pConnected()) {
                        val sent = webRtcManager.sendPacket(imagePacket)
                        if (sent) {
                            chatRepository.updateMessageStatus(messageId, MessageStatus.SENT)
                            // P2P ACK watchdog: if peer does not ack within 4.5s, fallback to cloud relay
                            launch {
                                delay(4500)
                                val msg = chatRepository.getMessage(messageId)
                                if (msg != null && msg.status != MessageStatus.DELIVERED && msg.status != MessageStatus.READ) {
                                    webRtcManager.sendOfflinePacketForRoom(currentRoom, imagePacket)
                                }
                            }
                            return@launch
                        }
                    }

                    // 2. Direct P2P not open or send failed: deliver via Serverless Cloud Mailbox!
                    webRtcManager.sendOfflinePacketForRoom(currentRoom, imagePacket) { success ->
                        viewModelScope.launch(Dispatchers.IO) {
                            val newStatus = if (success) MessageStatus.SENT else MessageStatus.SENDING
                            chatRepository.updateMessageStatus(messageId, newStatus)
                        }
                    }
                    return@launch
                }
            }

            // For non-images or large files:
            if (webRtcManager.isP2pConnected()) {
                webRtcManager.sendFileAttachment(
                    messageId = messageId,
                    uri = fileUriToSend,
                    fileName = fileName,
                    fileSize = fileSize,
                    mimeType = mimeType,
                    senderName = prefs.myName,
                    disappearingDuration = durationToUse
                )
            } else {
                // Try sending small non-image files (< 1MB) via cloud mailbox
                if (fileSize in 1..1024 * 1024) {
                    try {
                        val stream = FileUtils.openSafeInputStream(context, fileUriToSend)
                            ?: try { File(permanentLocalPath).inputStream() } catch (_: Exception) { null }
                        if (stream == null) {
                            chatRepository.updateMessageStatus(messageId, MessageStatus.FAILED)
                            return@launch
                        }
                        val bytes = stream.use { it.readBytes() }
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        val filePacket = DataChannelPacket(
                            type = "file",
                            messageId = messageId,
                            senderName = prefs.myName,
                            fileName = fileName,
                            fileSize = fileSize,
                            mimeType = mimeType,
                            timestamp = chatMessage.timestamp,
                            dataBase64 = base64,
                            disappearingDuration = durationToUse
                        )
                        webRtcManager.sendOfflinePacketForRoom(currentRoom, filePacket) { success ->
                            viewModelScope.launch(Dispatchers.IO) {
                                val newStatus = if (success) MessageStatus.SENT else MessageStatus.SENDING
                                chatRepository.updateMessageStatus(messageId, newStatus)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed reading file for mailbox fallback: ${e.localizedMessage}")
                    }
                } else {
                    Log.w(TAG, "P2P not connected and file too large for cloud mailbox (${fileSize} bytes)")
                }
            }
        }
    }

    fun sendPing() {
        val currentRoom = activeRoomId.value.ifBlank { prefs.roomId.trim().uppercase() }
        val messageId = UUID.randomUUID().toString()
        val pingMessage = ChatMessage(
            id = messageId,
            roomId = currentRoom,
            senderName = prefs.myName,
            isFromMe = true,
            text = "",
            type = MessageType.PING,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENDING
        )

        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.insertMessage(pingMessage)

            val packet = DataChannelPacket(
                type = "ping",
                messageId = messageId,
                senderName = prefs.myName,
                timestamp = pingMessage.timestamp
            )

            // Do NOT vibrate or trigger "Ping received from..." for the sender

            if (webRtcManager.isP2pConnected()) {
                val sent = webRtcManager.sendPacket(packet)
                if (sent) {
                    chatRepository.updateMessageStatus(messageId, MessageStatus.SENT)
                    launch {
                        delay(4500)
                        val msg = chatRepository.getMessage(messageId)
                        if (msg != null && msg.status != MessageStatus.DELIVERED && msg.status != MessageStatus.READ) {
                            webRtcManager.sendOfflinePacketForRoom(currentRoom, packet)
                        }
                    }
                    return@launch
                }
            }

            webRtcManager.sendOfflinePacketForRoom(currentRoom, packet) { success ->
                viewModelScope.launch(Dispatchers.IO) {
                    val newStatus = if (success) MessageStatus.SENT else MessageStatus.SENDING
                    chatRepository.updateMessageStatus(messageId, newStatus)
                }
            }
        }
    }

    @Deprecated("Use sendPing()", ReplaceWith("sendPing()"))
    fun sendLovePing() = sendPing()

    fun setTyping(isTyping: Boolean) {
        webRtcManager.sendPacket(
            DataChannelPacket(
                type = "typing",
                isTyping = isTyping
            )
        )
    }

    private fun triggerPingAnimation() {
        ChatConnectionManager.triggerPingAnimation()
    }

    @Deprecated("Use triggerPingAnimation()", ReplaceWith("triggerPingAnimation()"))
    private fun triggerLoveAnimation() = triggerPingAnimation()

    fun clearChat() {
        val currentRoom = activeRoomId.value.ifBlank { prefs.roomId.trim().uppercase() }
        if (currentRoom.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                chatRepository.clearRoomMessages(currentRoom, getApplication())
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.clearAllData(getApplication())
            prefs.clearRoom()
            leaveActiveRoom()
        }
    }

    fun flushPendingMessages() {
        val currentRoom = activeRoomId.value.ifBlank { prefs.roomId.trim().uppercase() }
        flushAllPendingMessages(currentRoom)
    }

    fun flushAllPendingMessages(targetRoomId: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val pendingList = if (!targetRoomId.isNullOrBlank()) {
                chatRepository.getPendingOutgoingMessagesForRoom(targetRoomId)
            } else {
                chatRepository.getPendingOutgoingMessages()
            }

            for (msg in pendingList) {
                dispatchOutgoingMessage(msg)
            }
        }
    }

    fun retryMessage(messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val msg = chatRepository.getMessage(messageId) ?: return@launch
            chatRepository.updateMessageStatus(messageId, MessageStatus.SENDING)
            dispatchOutgoingMessage(msg)
        }
    }

    private suspend fun dispatchOutgoingMessage(msg: ChatMessage) {
        val context = getApplication<Application>()
        when (msg.type) {
            MessageType.TEXT, MessageType.PING, MessageType.LOVE_PING -> {
                val packet = DataChannelPacket(
                    type = if (msg.type == MessageType.PING || msg.type == MessageType.LOVE_PING) "ping" else "text",
                    messageId = msg.id,
                    senderName = prefs.myName,
                    text = msg.text,
                    timestamp = msg.timestamp,
                    disappearingDuration = msg.disappearingDuration
                )

                if (msg.roomId == activeRoomId.value && webRtcManager.isP2pConnected()) {
                    val sent = webRtcManager.sendPacket(packet)
                    if (sent) {
                        chatRepository.updateMessageStatus(msg.id, MessageStatus.SENT)
                        viewModelScope.launch {
                            delay(4500)
                            val curr = chatRepository.getMessage(msg.id)
                            if (curr != null && curr.status != MessageStatus.DELIVERED && curr.status != MessageStatus.READ) {
                                webRtcManager.sendOfflinePacketForRoom(msg.roomId, packet)
                            }
                        }
                        return
                    }
                }

                webRtcManager.sendOfflinePacketForRoom(msg.roomId, packet) { success ->
                    viewModelScope.launch(Dispatchers.IO) {
                        val newStatus = if (success) MessageStatus.SENT else MessageStatus.FAILED
                        chatRepository.updateMessageStatus(msg.id, newStatus)
                    }
                }
            }

            MessageType.IMAGE -> {
                val path = msg.attachmentPath
                if (path.isNullOrBlank()) {
                    chatRepository.updateMessageStatus(msg.id, MessageStatus.FAILED)
                    return
                }

                val fileUri = if (path.startsWith("/")) Uri.fromFile(File(path)) else Uri.parse(path)
                val compressedBytes = FileUtils.compressImageForTransfer(context, fileUri)
                    ?: try {
                        val f = File(path)
                        if (f.exists()) f.readBytes() else null
                    } catch (_: Exception) { null }

                if (compressedBytes != null && compressedBytes.size < 2 * 1024 * 1024) {
                    val base64 = android.util.Base64.encodeToString(compressedBytes, android.util.Base64.NO_WRAP)
                    val imagePacket = DataChannelPacket(
                        type = "image",
                        messageId = msg.id,
                        senderName = prefs.myName,
                        fileName = msg.attachmentName ?: "photo.jpg",
                        fileSize = compressedBytes.size.toLong(),
                        mimeType = msg.mimeType ?: "image/jpeg",
                        timestamp = msg.timestamp,
                        dataBase64 = base64,
                        disappearingDuration = msg.disappearingDuration
                    )

                    if (msg.roomId == activeRoomId.value && webRtcManager.isP2pConnected()) {
                        val sent = webRtcManager.sendPacket(imagePacket)
                        if (sent) {
                            chatRepository.updateMessageStatus(msg.id, MessageStatus.SENT)
                            viewModelScope.launch {
                                delay(4500)
                                val curr = chatRepository.getMessage(msg.id)
                                if (curr != null && curr.status != MessageStatus.DELIVERED && curr.status != MessageStatus.READ) {
                                    webRtcManager.sendOfflinePacketForRoom(msg.roomId, imagePacket)
                                }
                            }
                            return
                        }
                    }

                    webRtcManager.sendOfflinePacketForRoom(msg.roomId, imagePacket) { success ->
                        viewModelScope.launch(Dispatchers.IO) {
                            val newStatus = if (success) MessageStatus.SENT else MessageStatus.FAILED
                            chatRepository.updateMessageStatus(msg.id, newStatus)
                        }
                    }
                } else {
                    Log.w(TAG, "Cannot send image: compression failed or file too large")
                    chatRepository.updateMessageStatus(msg.id, MessageStatus.FAILED)
                }
            }

            MessageType.FILE -> {
                val path = msg.attachmentPath
                val file = if (!path.isNullOrBlank()) File(path) else null
                if (file == null || !file.exists()) {
                    chatRepository.updateMessageStatus(msg.id, MessageStatus.FAILED)
                    return
                }

                val fileUri = Uri.fromFile(file)
                if (msg.roomId == activeRoomId.value && webRtcManager.isP2pConnected()) {
                    webRtcManager.sendFileAttachment(
                        messageId = msg.id,
                        uri = fileUri,
                        fileName = msg.attachmentName ?: file.name,
                        fileSize = msg.attachmentSize.takeIf { it > 0 } ?: file.length(),
                        mimeType = msg.mimeType,
                        senderName = prefs.myName,
                        disappearingDuration = msg.disappearingDuration
                    )
                } else if (file.length() in 1..1024 * 1024) {
                    try {
                        val bytes = file.readBytes()
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        val filePacket = DataChannelPacket(
                            type = "file",
                            messageId = msg.id,
                            senderName = prefs.myName,
                            fileName = msg.attachmentName ?: file.name,
                            fileSize = file.length(),
                            mimeType = msg.mimeType,
                            timestamp = msg.timestamp,
                            dataBase64 = base64,
                            disappearingDuration = msg.disappearingDuration
                        )
                        webRtcManager.sendOfflinePacketForRoom(msg.roomId, filePacket) { success ->
                            viewModelScope.launch(Dispatchers.IO) {
                                val newStatus = if (success) MessageStatus.SENT else MessageStatus.FAILED
                                chatRepository.updateMessageStatus(msg.id, newStatus)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed reading file for retry/flush: ${e.localizedMessage}")
                        chatRepository.updateMessageStatus(msg.id, MessageStatus.FAILED)
                    }
                } else {
                    Log.w(TAG, "Cannot send file offline: file exceeds 1MB threshold")
                    chatRepository.updateMessageStatus(msg.id, MessageStatus.FAILED)
                }
            }

            MessageType.AUDIO -> {
                val path = msg.attachmentPath
                val file = if (!path.isNullOrBlank()) File(path) else null
                if (file == null || !file.exists()) {
                    chatRepository.updateMessageStatus(msg.id, MessageStatus.FAILED)
                    return
                }

                if (file.length() in 1..2 * 1024 * 1024) {
                    try {
                        val bytes = file.readBytes()
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        val audioPacket = DataChannelPacket(
                            type = "audio",
                            messageId = msg.id,
                            senderName = prefs.myName,
                            fileName = msg.attachmentName ?: file.name,
                            fileSize = file.length(),
                            mimeType = msg.mimeType ?: "audio/mp4",
                            timestamp = msg.timestamp,
                            dataBase64 = base64,
                            replyToId = msg.replyToId,
                            replyToText = msg.replyToText,
                            replyToSender = msg.replyToSender,
                            disappearingDuration = msg.disappearingDuration
                        )
                        if (msg.roomId == activeRoomId.value && webRtcManager.isP2pConnected()) {
                            val sent = webRtcManager.sendPacket(audioPacket)
                            if (sent) {
                                chatRepository.updateMessageStatus(msg.id, MessageStatus.SENT)
                                return
                            }
                        }
                        webRtcManager.sendOfflinePacketForRoom(msg.roomId, audioPacket) { success ->
                            viewModelScope.launch(Dispatchers.IO) {
                                val newStatus = if (success) MessageStatus.SENT else MessageStatus.FAILED
                                chatRepository.updateMessageStatus(msg.id, newStatus)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed reading audio file for retry/flush: ${e.localizedMessage}")
                        chatRepository.updateMessageStatus(msg.id, MessageStatus.FAILED)
                    }
                }
            }
        }
    }

    fun markRoomMessagesAsRead(roomId: String) {
        val cleanRoom = roomId.trim().uppercase()
        if (cleanRoom.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val unread = chatRepository.getUnreadIncomingMessagesForRoom(cleanRoom)
            if (unread.isEmpty()) return@launch

            val now = System.currentTimeMillis()
            for (msg in unread) {
                val duration = msg.disappearingDuration
                val expiresAt = if (duration != null && duration > 0) {
                    now + (duration * 1000L)
                } else null

                chatRepository.markMessageAsRead(msg.id, expiresAt)
                ChatConnectionManager.sendReadAck(msg.id, cleanRoom)
            }
        }
    }

    fun setReplyingTo(message: ChatMessage?) {
        _replyingToMessage.value = message
    }

    fun sendReaction(messageId: String, emoji: String) {
        val currentRoom = activeRoomId.value.ifBlank { prefs.roomId.trim().uppercase() }
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.updateMessageReaction(messageId, emoji)

            val packet = DataChannelPacket(
                type = "reaction",
                messageId = messageId,
                reaction = emoji,
                senderName = prefs.myName,
                timestamp = System.currentTimeMillis()
            )

            if (webRtcManager.isP2pConnected()) {
                webRtcManager.sendPacket(packet)
            } else {
                webRtcManager.sendOfflinePacketForRoom(currentRoom, packet)
            }
        }
    }

    fun startVoiceRecording() {
        val started = audioRecorderHelper.startRecording()
        if (started) {
            _isRecordingVoice.value = true
            _voiceRecordDurationMs.value = 0L
            _voiceAmplitude.value = 0f
            voiceTimerJob?.cancel()
            voiceTimerJob = viewModelScope.launch {
                val startTime = System.currentTimeMillis()
                while (isActive && _isRecordingVoice.value) {
                    delay(100)
                    _voiceRecordDurationMs.value = System.currentTimeMillis() - startTime
                    val maxAmp = audioRecorderHelper.getMaxAmplitude()
                    _voiceAmplitude.value = (maxAmp / 32767f).coerceIn(0f, 1f)
                }
            }
        }
    }

    fun cancelVoiceRecording() {
        voiceTimerJob?.cancel()
        _isRecordingVoice.value = false
        _voiceRecordDurationMs.value = 0L
        _voiceAmplitude.value = 0f
        audioRecorderHelper.stopRecording(discard = true)
    }

    fun stopAndSendVoiceRecording() {
        voiceTimerJob?.cancel()
        _isRecordingVoice.value = false
        _voiceRecordDurationMs.value = 0L
        _voiceAmplitude.value = 0f
        val result = audioRecorderHelper.stopRecording(discard = false) ?: return

        val file = result.first
        val durationMs = result.second
        val currentRoom = activeRoomId.value.ifBlank { prefs.roomId.trim().uppercase() }
        val messageId = UUID.randomUUID().toString()

        val disappearingDuration = prefs.disappearingDurationSeconds
        val durationToUse = if (disappearingDuration > 0) disappearingDuration else null

        val replyingTo = _replyingToMessage.value
        _replyingToMessage.value = null

        val chatMessage = ChatMessage(
            id = messageId,
            roomId = currentRoom,
            senderName = prefs.myName,
            isFromMe = true,
            text = "Voice message (${durationMs / 1000}s)",
            type = MessageType.AUDIO,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENDING,
            attachmentPath = file.absolutePath,
            attachmentName = file.name,
            attachmentSize = file.length(),
            mimeType = "audio/mp4",
            transferProgress = 1.0f,
            replyToId = replyingTo?.id,
            replyToText = replyingTo?.text?.ifBlank { replyingTo.attachmentName ?: "Attachment" },
            replyToSender = replyingTo?.senderName,
            expiresAt = null,
            disappearingDuration = durationToUse
        )

        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.insertMessage(chatMessage)

            val bytes = try { file.readBytes() } catch (e: Exception) { null }
            if (bytes != null && bytes.size < 2 * 1024 * 1024) {
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                val audioPacket = DataChannelPacket(
                    type = "audio",
                    messageId = messageId,
                    senderName = prefs.myName,
                    fileName = file.name,
                    fileSize = file.length(),
                    mimeType = "audio/mp4",
                    audioDurationMs = durationMs,
                    timestamp = chatMessage.timestamp,
                    dataBase64 = base64,
                    replyToId = chatMessage.replyToId,
                    replyToText = chatMessage.replyToText,
                    replyToSender = chatMessage.replyToSender,
                    disappearingDuration = durationToUse
                )

                if (webRtcManager.isP2pConnected()) {
                    val sent = webRtcManager.sendPacket(audioPacket)
                    if (sent) {
                        chatRepository.updateMessageStatus(messageId, MessageStatus.SENT)
                        launch {
                            delay(4500)
                            val msg = chatRepository.getMessage(messageId)
                            if (msg != null && msg.status != MessageStatus.DELIVERED && msg.status != MessageStatus.READ) {
                                webRtcManager.sendOfflinePacketForRoom(currentRoom, audioPacket)
                            }
                        }
                        return@launch
                    }
                }

                webRtcManager.sendOfflinePacketForRoom(currentRoom, audioPacket) { success ->
                    viewModelScope.launch(Dispatchers.IO) {
                        val newStatus = if (success) MessageStatus.SENT else MessageStatus.SENDING
                        chatRepository.updateMessageStatus(messageId, newStatus)
                    }
                }
            } else {
                Log.w(TAG, "Audio recording exceeds 2MB limit: ${bytes?.size}")
                chatRepository.updateMessageStatus(messageId, MessageStatus.FAILED)
            }
        }
    }

    fun startCall() = ChatConnectionManager.startCall()
    fun acceptIncomingCall() = ChatConnectionManager.acceptCall()
    fun rejectIncomingCall() = ChatConnectionManager.rejectCall()
    fun endCall() = ChatConnectionManager.endCall()
    fun toggleCallMute() = ChatConnectionManager.toggleCallMute()
    fun toggleCallSpeaker() = ChatConnectionManager.toggleCallSpeaker()

    fun checkForUpdates(silent: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _isCheckingUpdate.value = true
            _updateCheckMessage.value = null
            try {
                val info = updateManager.checkForUpdate()
                if (info != null && info.isUpdateAvailable) {
                    _updateInfo.value = info
                } else {
                    _updateInfo.value = null
                    _updateCheckMessage.value = null
                }
            } catch (e: Exception) {
                if (!silent) {
                    _updateCheckMessage.value = "Failed to check for updates: ${e.localizedMessage}"
                }
            } finally {
                _isCheckingUpdate.value = false
            }
        }
    }

    fun startAppUpdate(downloadUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isDownloadingUpdate.value = true
            _downloadProgress.value = 0f
            val apkFile = updateManager.downloadApk(downloadUrl) { progress ->
                _downloadProgress.value = progress
            }
            _isDownloadingUpdate.value = false
            _downloadProgress.value = null
            if (apkFile != null) {
                withContext(Dispatchers.Main) {
                    updateManager.promptInstall(apkFile)
                }
            } else {
                _updateCheckMessage.value = "Failed to download update APK"
            }
        }
    }

    fun dismissUpdateDialog() {
        _updateInfo.value = null
    }

    fun clearUpdateMessage() {
        _updateCheckMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayerHelper.stop()
        audioRecorderHelper.stopRecording(discard = true)
    }

    companion object {
        private const val TAG = "ChatViewModel"
    }
}
