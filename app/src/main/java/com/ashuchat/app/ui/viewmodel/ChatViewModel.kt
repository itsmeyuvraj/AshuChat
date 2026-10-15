package com.ashuchat.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ashuchat.app.data.local.AppDatabase
import com.ashuchat.app.data.model.ChatMessage
import com.ashuchat.app.data.model.DataChannelPacket
import com.ashuchat.app.data.model.MessageStatus
import com.ashuchat.app.data.model.MessageType
import com.ashuchat.app.data.repository.ChatRepository
import com.ashuchat.app.data.repository.PreferencesRepository
import com.ashuchat.app.utils.FileUtils
import com.ashuchat.app.utils.NotificationHelper
import com.ashuchat.app.webrtc.P2PConnectionState
import com.ashuchat.app.webrtc.WebRtcManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val chatRepository = ChatRepository(db.messageDao())
    val prefs = PreferencesRepository(application)

    val webRtcManager = WebRtcManager(application, viewModelScope)

    val connectionState: StateFlow<P2PConnectionState> = webRtcManager.connectionState

    private val _isPartnerTyping = MutableStateFlow(false)
    val isPartnerTyping: StateFlow<Boolean> = _isPartnerTyping.asStateFlow()

    private val _showLoveHeartEffect = MutableStateFlow(false)
    val showLoveHeartEffect: StateFlow<Boolean> = _showLoveHeartEffect.asStateFlow()

    private val _activeFileProgress = MutableStateFlow<Map<String, Float>>(emptyMap())

    // Merged messages with active transfer progress
    val messages: StateFlow<List<ChatMessage>> = combine(
        chatRepository.getAllMessages(),
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
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private var typingJob: Job? = null

    init {
        observeIncomingPackets()
        observeFileTransfers()
        observeConnectionChanges()
    }

    fun startConnection() {
        val roomId = prefs.roomId
        if (roomId.isNotBlank()) {
            webRtcManager.start(
                serverUrl = prefs.signalingUrl,
                roomId = roomId,
                clientId = prefs.clientId
            )
            flushPendingMessages()
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
                when (packet.type) {
                    "text" -> {
                        val messageId = packet.messageId ?: UUID.randomUUID().toString()
                        val existing = chatRepository.getMessage(messageId)
                        if (existing == null) {
                            val incomingMessage = ChatMessage(
                                id = messageId,
                                senderName = packet.senderName ?: prefs.partnerName,
                                isFromMe = false,
                                text = packet.text ?: "",
                                type = MessageType.TEXT,
                                timestamp = packet.timestamp,
                                status = MessageStatus.DELIVERED
                            )
                            chatRepository.insertMessage(incomingMessage)

                            NotificationHelper.showMessageNotification(
                                getApplication(),
                                prefs.partnerName,
                                packet.text ?: "New message"
                            )
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

                    "love_ping" -> {
                        val messageId = packet.messageId ?: UUID.randomUUID().toString()
                        val existing = chatRepository.getMessage(messageId)
                        if (existing == null) {
                            val loveMessage = ChatMessage(
                                id = messageId,
                                senderName = packet.senderName ?: prefs.partnerName,
                                isFromMe = false,
                                text = "",
                                type = MessageType.LOVE_PING,
                                timestamp = packet.timestamp,
                                status = MessageStatus.DELIVERED
                            )
                            chatRepository.insertMessage(loveMessage)

                            // Trigger romantic heartbeat vibration & visual effect
                            NotificationHelper.vibrateLovePing(getApplication())
                            triggerLoveAnimation()
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
                            senderName = packet.senderName ?: prefs.partnerName,
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
                    NotificationHelper.showMessageNotification(
                        getApplication(),
                        prefs.partnerName,
                        "Received attachment"
                    )
                }
            }
        }
    }

    fun sendTextMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val messageId = UUID.randomUUID().toString()
        val chatMessage = ChatMessage(
            id = messageId,
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
                    return@launch
                }
            }

            // Partner offline or P2P not yet open: deliver via Serverless Cloud Mailbox
            webRtcManager.sendOfflinePacket(packet) { success ->
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

        val messageId = UUID.randomUUID().toString()
        val chatMessage = ChatMessage(
            id = messageId,
            senderName = prefs.myName,
            isFromMe = true,
            text = "",
            type = if (isImage) MessageType.IMAGE else MessageType.FILE,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENDING,
            attachmentPath = uri.toString(),
            attachmentName = fileName,
            attachmentSize = fileSize,
            mimeType = mimeType,
            transferProgress = 0f
        )

        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.insertMessage(chatMessage)
            webRtcManager.sendFileAttachment(
                messageId = messageId,
                uri = uri,
                fileName = fileName,
                fileSize = fileSize,
                mimeType = mimeType
            )
        }
    }

    fun sendLovePing() {
        val messageId = UUID.randomUUID().toString()
        val loveMessage = ChatMessage(
            id = messageId,
            senderName = prefs.myName,
            isFromMe = true,
            text = "",
            type = MessageType.LOVE_PING,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENDING
        )

        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.insertMessage(loveMessage)

            val packet = DataChannelPacket(
                type = "love_ping",
                messageId = messageId,
                senderName = prefs.myName,
                timestamp = loveMessage.timestamp
            )

            NotificationHelper.vibrateLovePing(getApplication())
            triggerLoveAnimation()

            if (webRtcManager.isP2pConnected()) {
                val sent = webRtcManager.sendPacket(packet)
                if (sent) {
                    chatRepository.updateMessageStatus(messageId, MessageStatus.SENT)
                    return@launch
                }
            }

            webRtcManager.sendOfflinePacket(packet) { success ->
                viewModelScope.launch(Dispatchers.IO) {
                    val newStatus = if (success) MessageStatus.SENT else MessageStatus.SENDING
                    chatRepository.updateMessageStatus(messageId, newStatus)
                }
            }
        }
    }

    fun setTyping(isTyping: Boolean) {
        webRtcManager.sendPacket(
            DataChannelPacket(
                type = "typing",
                isTyping = isTyping
            )
        )
    }

    private fun triggerLoveAnimation() {
        viewModelScope.launch {
            _showLoveHeartEffect.value = true
            delay(2500)
            _showLoveHeartEffect.value = false
        }
    }

    fun clearChat() {
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.clearAllMessages()
        }
    }

    fun flushPendingMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            val pendingList = chatRepository.getPendingOutgoingMessages()
            for (msg in pendingList) {
                val packet = DataChannelPacket(
                    type = if (msg.type == MessageType.LOVE_PING) "love_ping" else "text",
                    messageId = msg.id,
                    senderName = prefs.myName,
                    text = msg.text,
                    timestamp = msg.timestamp
                )

                if (webRtcManager.isP2pConnected()) {
                    val sent = webRtcManager.sendPacket(packet)
                    if (sent) {
                        chatRepository.updateMessageStatus(msg.id, MessageStatus.SENT)
                    }
                } else {
                    webRtcManager.sendOfflinePacket(packet) { success ->
                        if (success) {
                            viewModelScope.launch(Dispatchers.IO) {
                                chatRepository.updateMessageStatus(msg.id, MessageStatus.SENT)
                            }
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
                type = if (msg.type == MessageType.LOVE_PING) "love_ping" else "text",
                messageId = msg.id,
                senderName = prefs.myName,
                text = msg.text,
                timestamp = msg.timestamp
            )

            if (webRtcManager.isP2pConnected()) {
                val sent = webRtcManager.sendPacket(packet)
                val newStatus = if (sent) MessageStatus.SENT else MessageStatus.FAILED
                chatRepository.updateMessageStatus(messageId, newStatus)
            } else {
                webRtcManager.sendOfflinePacket(packet) { success ->
                    viewModelScope.launch(Dispatchers.IO) {
                        val newStatus = if (success) MessageStatus.SENT else MessageStatus.FAILED
                        chatRepository.updateMessageStatus(messageId, newStatus)
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        webRtcManager.stop()
    }
}
