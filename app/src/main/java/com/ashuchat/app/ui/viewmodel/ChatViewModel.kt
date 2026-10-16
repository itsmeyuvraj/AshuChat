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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import java.io.File
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val chatRepository = ChatRepository(db.messageDao(), db.roomDao())
    val prefs = PreferencesRepository(application)
    val updateManager = UpdateManager(application)
    val networkMonitor = NetworkMonitor(application)

    val webRtcManager = WebRtcManager(application, viewModelScope)

    val connectionState: StateFlow<P2PConnectionState> = webRtcManager.connectionState

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
    private val _activeRoomId = MutableStateFlow(prefs.roomId.trim().uppercase())
    val activeRoomId: StateFlow<String> = _activeRoomId.asStateFlow()

    private val _isPartnerTyping = MutableStateFlow(false)
    val isPartnerTyping: StateFlow<Boolean> = _isPartnerTyping.asStateFlow()

    private val _partnerName = MutableStateFlow(prefs.partnerName)
    val partnerName: StateFlow<String> = _partnerName.asStateFlow()

    private val _showPingEffect = MutableStateFlow(false)
    val showPingEffect: StateFlow<Boolean> = _showPingEffect.asStateFlow()
    val showLoveHeartEffect: StateFlow<Boolean> get() = showPingEffect

    private val _activeFileProgress = MutableStateFlow<Map<String, Float>>(emptyMap())

    // Messages for the currently active room with ongoing file progress
    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<ChatMessage>> = _activeRoomId
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

    private var typingJob: Job? = null

    init {
        observeIncomingPackets()
        observeFileTransfers()
        observeConnectionChanges()
        observeNetworkState()
        observePeerNameSync()
        seedInitialRoomIfAny()

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
                    val currentRoom = _activeRoomId.value.ifBlank { prefs.roomId.trim().uppercase() }
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

    fun openRoom(roomId: String) {
        val clean = roomId.trim().uppercase()
        if (clean.isBlank()) return

        _activeRoomId.value = clean
        prefs.roomId = clean

        viewModelScope.launch(Dispatchers.IO) {
            val existing = chatRepository.getRoom(clean)
            if (existing == null) {
                chatRepository.insertOrUpdateRoom(
                    RoomEntity(
                        roomId = clean,
                        partnerName = "Peer",
                        lastMessage = "Room created",
                        lastTimestamp = System.currentTimeMillis()
                    )
                )
                _partnerName.value = "Peer"
            } else {
                _partnerName.value = existing.partnerName
            }
        }

        // Restart WebRTC cleanly for the new room code
        webRtcManager.stop()
        webRtcManager.start(
            serverUrl = prefs.signalingUrl,
            roomId = clean,
            clientId = prefs.clientId,
            clientName = prefs.myName
        )
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
        _activeRoomId.value = ""
    }

    fun deleteRoom(roomId: String) {
        val clean = roomId.trim().uppercase()
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.deleteRoom(clean)
            if (_activeRoomId.value == clean) {
                leaveActiveRoom()
            }
        }
    }

    fun startConnection() {
        val current = _activeRoomId.value.ifBlank { prefs.roomId.trim().uppercase() }
        if (current.isNotBlank()) {
            openRoom(current)
        }
    }

    private fun observePeerNameSync() {
        viewModelScope.launch {
            webRtcManager.peerNameFlow.collect { name ->
                updatePartnerName(name)
            }
        }
    }

    fun updatePartnerName(name: String) {
        val clean = name.trim()
        if (clean.isNotBlank() && clean != _partnerName.value) {
            _partnerName.value = clean
            val currentRoom = _activeRoomId.value
            if (currentRoom.isNotBlank()) {
                viewModelScope.launch(Dispatchers.IO) {
                    chatRepository.updateRoomPartnerName(currentRoom, clean)
                }
            }
        }
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

    private fun observeIncomingPackets() {
        viewModelScope.launch {
            webRtcManager.incomingPackets.collect { packet ->
                val currentRoom = _activeRoomId.value.ifBlank { prefs.roomId.trim().uppercase() }

                packet.senderName?.let { name ->
                    if (name.isNotBlank()) {
                        updatePartnerName(name)
                    }
                }
                when (packet.type) {
                    "name_sync" -> {
                        packet.senderName?.let { name ->
                            if (name.isNotBlank()) {
                                updatePartnerName(name)
                            }
                        }
                    }

                    "text" -> {
                        val messageId = packet.messageId ?: UUID.randomUUID().toString()
                        val currentPartner = _partnerName.value
                        val existing = chatRepository.getMessage(messageId)
                        if (existing == null) {
                            val incomingMessage = ChatMessage(
                                id = messageId,
                                roomId = currentRoom,
                                senderName = packet.senderName ?: currentPartner,
                                isFromMe = false,
                                text = packet.text ?: "",
                                type = MessageType.TEXT,
                                timestamp = packet.timestamp,
                                status = MessageStatus.DELIVERED
                            )
                            chatRepository.insertMessage(incomingMessage)

                            val shouldNotify = !AshuChatApp.isAppInForeground || AshuChatApp.activeChatRoom != currentRoom
                            if (shouldNotify) {
                                NotificationHelper.showMessageNotification(
                                    getApplication(),
                                    currentPartner,
                                    packet.text ?: "New message"
                                )
                            }
                        }

                        // Send delivery ack back to peer (via P2P if open, or cloud relay)
                        val ackPacket = DataChannelPacket(
                            type = "ack",
                            messageId = messageId,
                            status = "DELIVERED"
                        )
                        if (webRtcManager.isP2pConnected()) {
                            webRtcManager.sendPacket(ackPacket)
                        } else {
                            webRtcManager.sendOfflineAck(messageId, "DELIVERED")
                        }
                    }

                    "ack" -> {
                        packet.messageId?.let { id ->
                            val status = if (packet.status == "READ") MessageStatus.READ else MessageStatus.DELIVERED
                            chatRepository.updateMessageStatus(id, status)
                        }
                    }

                    "typing" -> {
                        _isPartnerTyping.value = packet.isTyping == true
                        typingJob?.cancel()
                        if (packet.isTyping == true) {
                            typingJob = viewModelScope.launch {
                                delay(3000)
                                _isPartnerTyping.value = false
                            }
                        }
                    }

                    "ping", "love_ping" -> {
                        // Ignore self-sent pings (Fixes bug: receiving notification when you ping)
                        if (packet.senderName == prefs.myName) return@collect

                        val messageId = packet.messageId ?: UUID.randomUUID().toString()
                        val currentPartner = _partnerName.value
                        val existing = chatRepository.getMessage(messageId)
                        if (existing == null) {
                            val pingMessage = ChatMessage(
                                id = messageId,
                                roomId = currentRoom,
                                senderName = packet.senderName ?: currentPartner,
                                isFromMe = false,
                                text = "",
                                type = MessageType.PING,
                                timestamp = packet.timestamp,
                                status = MessageStatus.DELIVERED
                            )
                            chatRepository.insertMessage(pingMessage)

                            // Trigger haptic vibration & visual alert for receiver only
                            NotificationHelper.vibratePing(getApplication())
                            triggerPingAnimation()

                            val shouldNotify = !AshuChatApp.isAppInForeground || AshuChatApp.activeChatRoom != currentRoom
                            if (shouldNotify) {
                                NotificationHelper.showMessageNotification(
                                    getApplication(),
                                    packet.senderName ?: currentPartner,
                                    "⚡ Sent you a Ping!"
                                )
                            }
                        }

                        // Send delivery ack back to peer
                        val ackPacket = DataChannelPacket(
                            type = "ack",
                            messageId = messageId,
                            status = "DELIVERED"
                        )
                        if (webRtcManager.isP2pConnected()) {
                            webRtcManager.sendPacket(ackPacket)
                        } else {
                            webRtcManager.sendOfflineAck(messageId, "DELIVERED")
                        }
                    }

                    "file_header" -> {
                        // Insert placeholder message for incoming file transfer
                        val msgId = packet.messageId ?: UUID.randomUUID().toString()
                        val isImage = FileUtils.isImageMimeType(packet.mimeType)
                        val fileMsg = ChatMessage(
                            id = msgId,
                            roomId = currentRoom,
                            senderName = packet.senderName ?: _partnerName.value,
                            isFromMe = false,
                            text = "",
                            type = if (isImage) MessageType.IMAGE else MessageType.FILE,
                            timestamp = packet.timestamp,
                            status = MessageStatus.SENDING,
                            attachmentName = packet.fileName,
                            attachmentSize = packet.fileSize,
                            mimeType = packet.mimeType,
                            transferProgress = 0f
                        )
                        chatRepository.insertMessage(fileMsg)
                    }

                    "file_ack" -> {
                        packet.messageId?.let { id ->
                            chatRepository.updateMessageStatus(id, MessageStatus.DELIVERED)
                        }
                    }
                }
            }
        }
    }

    private fun observeFileTransfers() {
        viewModelScope.launch {
            webRtcManager.fileProgressFlow.collect { (fileId, progress) ->
                _activeFileProgress.value = _activeFileProgress.value + (fileId to progress)
            }
        }

        viewModelScope.launch {
            webRtcManager.fileCompleteFlow.collect { (messageId, localPath, isIncoming) ->
                val currentMap = _activeFileProgress.value.toMutableMap()
                currentMap.remove(messageId)
                _activeFileProgress.value = currentMap

                chatRepository.updateAttachmentPath(messageId, localPath)
                chatRepository.updateMessageStatus(
                    messageId,
                    if (isIncoming) MessageStatus.DELIVERED else MessageStatus.SENT
                )

                if (isIncoming) {
                    val currentRoom = _activeRoomId.value.ifBlank { prefs.roomId.trim().uppercase() }
                    val shouldNotify = !AshuChatApp.isAppInForeground || AshuChatApp.activeChatRoom != currentRoom
                    if (shouldNotify) {
                        NotificationHelper.showMessageNotification(
                            getApplication(),
                            _partnerName.value,
                            "Received attachment"
                        )
                    }
                }
            }
        }
    }

    fun sendTextMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val currentRoom = _activeRoomId.value.ifBlank { prefs.roomId.trim().uppercase() }
        val messageId = UUID.randomUUID().toString()
        val chatMessage = ChatMessage(
            id = messageId,
            roomId = currentRoom,
            senderName = prefs.myName,
            isFromMe = true,
            text = trimmed,
            type = MessageType.TEXT,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENDING
        )

        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.insertMessage(chatMessage)

            val packet = DataChannelPacket(
                type = "text",
                messageId = messageId,
                senderName = prefs.myName,
                text = trimmed,
                timestamp = chatMessage.timestamp
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
        val isImage = FileUtils.isImageMimeType(mimeType)

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

        val currentRoom = _activeRoomId.value.ifBlank { prefs.roomId.trim().uppercase() }
        val messageId = UUID.randomUUID().toString()
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
            transferProgress = 0f
        )

        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.insertMessage(chatMessage)
            webRtcManager.sendFileAttachment(
                messageId = messageId,
                uri = fileUriToSend,
                fileName = fileName,
                fileSize = fileSize,
                mimeType = mimeType
            )
        }
    }

    fun sendPing() {
        val currentRoom = _activeRoomId.value.ifBlank { prefs.roomId.trim().uppercase() }
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
        viewModelScope.launch {
            _showPingEffect.value = true
            delay(2500)
            _showPingEffect.value = false
        }
    }

    @Deprecated("Use triggerPingAnimation()", ReplaceWith("triggerPingAnimation()"))
    private fun triggerLoveAnimation() = triggerPingAnimation()

    fun clearChat() {
        val currentRoom = _activeRoomId.value.ifBlank { prefs.roomId.trim().uppercase() }
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
            _activeRoomId.value = ""
        }
    }

    fun flushPendingMessages() {
        val currentRoom = _activeRoomId.value.ifBlank { prefs.roomId.trim().uppercase() }
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
                val packet = DataChannelPacket(
                    type = if (msg.type == MessageType.PING || msg.type == MessageType.LOVE_PING) "ping" else "text",
                    messageId = msg.id,
                    senderName = prefs.myName,
                    text = msg.text,
                    timestamp = msg.timestamp
                )

                if (msg.roomId == _activeRoomId.value && webRtcManager.isP2pConnected()) {
                    val sent = webRtcManager.sendPacket(packet)
                    if (sent) {
                        chatRepository.updateMessageStatus(msg.id, MessageStatus.SENT)
                        launch {
                            delay(4500)
                            val curr = chatRepository.getMessage(msg.id)
                            if (curr != null && curr.status != MessageStatus.DELIVERED && curr.status != MessageStatus.READ) {
                                webRtcManager.sendOfflinePacketForRoom(msg.roomId, packet)
                            }
                        }
                        continue
                    }
                }

                webRtcManager.sendOfflinePacketForRoom(msg.roomId, packet) { success ->
                    if (success) {
                        viewModelScope.launch(Dispatchers.IO) {
                            chatRepository.updateMessageStatus(msg.id, MessageStatus.SENT)
                        }
                    }
                }
            }
        }
    }

    fun retryMessage(messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val msg = chatRepository.getMessage(messageId) ?: return@launch
            chatRepository.updateMessageStatus(messageId, MessageStatus.SENDING)

            val packet = DataChannelPacket(
                type = if (msg.type == MessageType.PING || msg.type == MessageType.LOVE_PING) "ping" else "text",
                messageId = msg.id,
                senderName = prefs.myName,
                text = msg.text,
                timestamp = msg.timestamp
            )

            if (msg.roomId == _activeRoomId.value && webRtcManager.isP2pConnected()) {
                val sent = webRtcManager.sendPacket(packet)
                if (sent) {
                    chatRepository.updateMessageStatus(messageId, MessageStatus.SENT)
                    launch {
                        delay(4500)
                        val curr = chatRepository.getMessage(messageId)
                        if (curr != null && curr.status != MessageStatus.DELIVERED && curr.status != MessageStatus.READ) {
                            webRtcManager.sendOfflinePacketForRoom(msg.roomId, packet)
                        }
                    }
                    return@launch
                }
            }

            webRtcManager.sendOfflinePacketForRoom(msg.roomId, packet) { success ->
                viewModelScope.launch(Dispatchers.IO) {
                    val newStatus = if (success) MessageStatus.SENT else MessageStatus.FAILED
                    chatRepository.updateMessageStatus(messageId, newStatus)
                }
            }
        }
    }

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
        webRtcManager.stop()
    }

    companion object {
        private const val TAG = "ChatViewModel"
    }
}
