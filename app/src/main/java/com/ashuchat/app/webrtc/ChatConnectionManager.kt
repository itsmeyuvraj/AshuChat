package com.ashuchat.app.webrtc

import android.content.Context
import android.util.Base64
import android.util.Log
import com.ashuchat.app.AshuChatApp
import com.ashuchat.app.data.local.AppDatabase
import com.ashuchat.app.data.local.RoomEntity
import com.ashuchat.app.data.model.CallState
import com.ashuchat.app.data.model.ChatMessage
import com.ashuchat.app.data.model.DataChannelPacket
import com.ashuchat.app.data.model.MessageStatus
import com.ashuchat.app.data.model.MessageType
import com.ashuchat.app.data.repository.ChatRepository
import com.ashuchat.app.data.repository.PreferencesRepository
import com.ashuchat.app.utils.CallNotificationHelper
import com.ashuchat.app.utils.FileUtils
import com.ashuchat.app.utils.NetworkMonitor
import com.ashuchat.app.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Global persistent connection & background coordinator.
 * Maintains WebRTC P2P and serverless signaling connections across both
 * foreground and background, enabling real-time calls and message reception
 * even when the app is closed.
 */
object ChatConnectionManager {

    private const val TAG = "ChatConnectionMgr"

    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var applicationContext: Context? = null
    var isInitialized = false
        private set

    lateinit var prefs: PreferencesRepository
        private set
    lateinit var chatRepository: ChatRepository
        private set
    lateinit var networkMonitor: NetworkMonitor
        private set
    lateinit var webRtcManager: WebRtcManager
        private set

    // Connection & Room state
    private val _activeRoomId = MutableStateFlow("")
    val activeRoomId: StateFlow<String> = _activeRoomId.asStateFlow()

    private val _partnerName = MutableStateFlow("Peer")
    val partnerName: StateFlow<String> = _partnerName.asStateFlow()

    val connectionState: StateFlow<P2PConnectionState>
        get() = webRtcManager.connectionState

    // Voice Calling State
    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _callDurationSeconds = MutableStateFlow(0)
    val callDurationSeconds: StateFlow<Int> = _callDurationSeconds.asStateFlow()

    private val _isCallMuted = MutableStateFlow(false)
    val isCallMuted: StateFlow<Boolean> = _isCallMuted.asStateFlow()

    private val _isCallSpeakerOn = MutableStateFlow(false)
    val isCallSpeakerOn: StateFlow<Boolean> = _isCallSpeakerOn.asStateFlow()

    private val _isPartnerTyping = MutableStateFlow(false)
    val isPartnerTyping: StateFlow<Boolean> = _isPartnerTyping.asStateFlow()

    private val _showPingEffect = MutableStateFlow(false)
    val showPingEffect: StateFlow<Boolean> = _showPingEffect.asStateFlow()

    private var callDurationJob: Job? = null
    private var typingJob: Job? = null

    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return
        val appContext = context.applicationContext
        applicationContext = appContext

        prefs = PreferencesRepository(appContext)
        val database = AppDatabase.getDatabase(appContext)
        chatRepository = ChatRepository(database.messageDao(), database.roomDao())
        networkMonitor = NetworkMonitor(appContext)
        webRtcManager = WebRtcManager(appContext, scope)

        _activeRoomId.value = prefs.roomId.trim().uppercase()
        _partnerName.value = prefs.partnerName.ifBlank { "Peer" }

        observeIncomingPackets()
        observeNetworkState()

        // Periodic cleanup of expired disappearing messages
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(3000)
                try {
                    chatRepository.deleteExpiredMessages(applicationContext)
                } catch (e: Exception) {
                    Log.w(TAG, "Error cleaning expired messages: ${e.localizedMessage}")
                }
            }
        }

        isInitialized = true
        Log.d(TAG, "ChatConnectionManager initialized")
    }

    /**
     * Start background listening for the user's configured room.
     */
    fun start() {
        val currentRoom = _activeRoomId.value.ifBlank { prefs.roomId.trim().uppercase() }
        if (currentRoom.isNotBlank()) {
            openRoom(currentRoom)
        }
    }

    fun openRoom(roomId: String, forceReconnect: Boolean = false) {
        val clean = roomId.trim().uppercase()
        if (clean.isBlank()) return

        val wasSameRoom = (_activeRoomId.value == clean)
        _activeRoomId.value = clean
        prefs.roomId = clean

        scope.launch {
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

        val currentState = webRtcManager.connectionState.value
        val isAlreadyConnectedOrConnecting = wasSameRoom && (
            currentState == P2PConnectionState.CONNECTED_P2P ||
            currentState == P2PConnectionState.NEGOTIATING_WEBRTC ||
            currentState == P2PConnectionState.CONNECTING_SIGNALING ||
            currentState == P2PConnectionState.WAITING_FOR_PEER
        )

        if (!isAlreadyConnectedOrConnecting || forceReconnect) {
            webRtcManager.stop()
            webRtcManager.start(
                serverUrl = prefs.signalingUrl,
                roomId = clean,
                clientId = prefs.clientId,
                clientName = prefs.myName
            )
            Log.d(TAG, "Connecting to room $clean on signaling ${prefs.signalingUrl}")
        }
    }

    private fun observeNetworkState() {
        scope.launch {
            networkMonitor.isConnected.collect { isConnected ->
                if (isConnected) {
                    val currentRoom = _activeRoomId.value.ifBlank { prefs.roomId.trim().uppercase() }
                    if (currentRoom.isNotBlank()) {
                        Log.d(TAG, "Network restored. Ensuring room connection: $currentRoom")
                        openRoom(currentRoom, forceReconnect = false)
                    }
                }
            }
        }
    }

    fun updatePartnerName(name: String) {
        val clean = name.trim()
        if (clean.isNotBlank() && clean != _partnerName.value) {
            _partnerName.value = clean
            val currentRoom = _activeRoomId.value
            if (currentRoom.isNotBlank()) {
                scope.launch {
                    chatRepository.updateRoomPartnerName(currentRoom, clean)
                }
            }
        }
    }

    private fun observeIncomingPackets() {
        scope.launch {
            webRtcManager.incomingPackets.collect { packet ->
                handleIncomingPacket(packet)
            }
        }
    }

    private suspend fun handleIncomingPacket(packet: DataChannelPacket) {
        val context = applicationContext ?: return
        val currentRoom = packet.roomId?.takeIf { it.isNotBlank() }
            ?: _activeRoomId.value.ifBlank { prefs.roomId.trim().uppercase() }

        packet.senderName?.let { sender ->
            val clean = sender.trim()
            if (clean.isNotBlank() && clean != "Peer") {
                if (currentRoom == _activeRoomId.value || _partnerName.value == "Peer") {
                    _partnerName.value = clean
                }
                if (currentRoom.isNotBlank()) {
                    scope.launch {
                        chatRepository.updateRoomPartnerName(currentRoom, clean)
                    }
                }
            }
        }

        val currentPartner = packet.senderName ?: _partnerName.value
        when (packet.type) {
            "name_sync" -> {
                // Handled above via senderName sync
            }

            "text" -> {
                val messageId = packet.messageId ?: UUID.randomUUID().toString()
                val existing = chatRepository.getMessage(messageId)
                val isViewed = AshuChatApp.isAppInForeground && AshuChatApp.activeChatRoom == currentRoom
                val duration = packet.disappearingDuration
                val initialStatus = if (isViewed) MessageStatus.READ else MessageStatus.DELIVERED
                val expiresAt = if (isViewed && duration != null && duration > 0) {
                    System.currentTimeMillis() + (duration * 1000L)
                } else null

                if (existing == null) {
                    val incomingMessage = ChatMessage(
                        id = messageId,
                        roomId = currentRoom,
                        senderName = packet.senderName ?: currentPartner,
                        isFromMe = false,
                        text = packet.text ?: "",
                        type = MessageType.TEXT,
                        timestamp = packet.timestamp,
                        status = initialStatus,
                        reaction = packet.reaction,
                        replyToId = packet.replyToId,
                        replyToText = packet.replyToText,
                        replyToSender = packet.replyToSender,
                        expiresAt = expiresAt,
                        disappearingDuration = duration
                    )
                    chatRepository.insertMessage(incomingMessage)

                    if (!isViewed) {
                        NotificationHelper.showMessageNotification(
                            context = context,
                            senderName = packet.senderName ?: currentPartner,
                            messageText = packet.text ?: "New message",
                            roomId = currentRoom
                        )
                    }
                }

                if (isViewed) {
                    sendReadAck(messageId, currentRoom)
                } else {
                    sendDeliveryAck(messageId, currentRoom)
                }
            }

            "ping", "love_ping" -> {
                if (packet.senderName == prefs.myName) return

                val messageId = packet.messageId ?: UUID.randomUUID().toString()
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

                    NotificationHelper.vibratePing(context)
                    triggerPingAnimation()

                    val shouldNotify = !AshuChatApp.isAppInForeground || AshuChatApp.activeChatRoom != currentRoom
                    if (shouldNotify) {
                        NotificationHelper.showMessageNotification(
                            context = context,
                            senderName = packet.senderName ?: currentPartner,
                            messageText = "⚡ Sent you a Ping!",
                            roomId = currentRoom
                        )
                    }
                }
                sendDeliveryAck(messageId, currentRoom)
            }

            "image" -> {
                val messageId = packet.messageId ?: UUID.randomUUID().toString()
                val existing = chatRepository.getMessage(messageId)

                var localPath: String? = null
                val base64 = packet.dataBase64
                if (!base64.isNullOrBlank()) {
                    try {
                        val bytes = Base64.decode(base64, Base64.DEFAULT)
                        localPath = FileUtils.saveTransferBytesToAppStorage(
                            context,
                            bytes,
                            packet.fileName ?: "photo.jpg"
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed saving incoming image: ${e.localizedMessage}")
                    }
                }

                val isViewed = AshuChatApp.isAppInForeground && AshuChatApp.activeChatRoom == currentRoom
                val duration = packet.disappearingDuration
                val initialStatus = if (isViewed) MessageStatus.READ else MessageStatus.DELIVERED
                val expiresAt = if (isViewed && duration != null && duration > 0) {
                    System.currentTimeMillis() + (duration * 1000L)
                } else null

                if (existing == null) {
                    val incomingMessage = ChatMessage(
                        id = messageId,
                        roomId = currentRoom,
                        senderName = packet.senderName ?: currentPartner,
                        isFromMe = false,
                        text = packet.text ?: "",
                        type = MessageType.IMAGE,
                        timestamp = packet.timestamp,
                        status = initialStatus,
                        attachmentPath = localPath,
                        attachmentName = packet.fileName ?: "photo.jpg",
                        attachmentSize = packet.fileSize,
                        mimeType = packet.mimeType ?: "image/jpeg",
                        transferProgress = 1.0f,
                        replyToId = packet.replyToId,
                        replyToText = packet.replyToText,
                        replyToSender = packet.replyToSender,
                        expiresAt = expiresAt,
                        disappearingDuration = duration
                    )
                    chatRepository.insertMessage(incomingMessage)

                    if (!isViewed) {
                        NotificationHelper.showMessageNotification(
                            context = context,
                            senderName = packet.senderName ?: currentPartner,
                            messageText = "📷 Sent you a photo",
                            roomId = currentRoom
                        )
                    }
                } else if (localPath != null && existing.attachmentPath.isNullOrBlank()) {
                    chatRepository.updateAttachmentPath(messageId, localPath)
                    chatRepository.updateMessageStatus(messageId, initialStatus)
                }

                if (isViewed) {
                    sendReadAck(messageId, currentRoom)
                } else {
                    sendDeliveryAck(messageId, currentRoom)
                }
            }

            "file" -> {
                val messageId = packet.messageId ?: UUID.randomUUID().toString()
                val existing = chatRepository.getMessage(messageId)

                var localPath: String? = null
                val base64 = packet.dataBase64
                if (!base64.isNullOrBlank()) {
                    try {
                        val bytes = Base64.decode(base64, Base64.DEFAULT)
                        localPath = FileUtils.saveTransferBytesToAppStorage(
                            context,
                            bytes,
                            packet.fileName ?: "attachment"
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed saving incoming file: ${e.localizedMessage}")
                    }
                }

                val isViewed = AshuChatApp.isAppInForeground && AshuChatApp.activeChatRoom == currentRoom
                val duration = packet.disappearingDuration
                val initialStatus = if (isViewed) MessageStatus.READ else MessageStatus.DELIVERED
                val expiresAt = if (isViewed && duration != null && duration > 0) {
                    System.currentTimeMillis() + (duration * 1000L)
                } else null

                if (existing == null) {
                    val incomingMessage = ChatMessage(
                        id = messageId,
                        roomId = currentRoom,
                        senderName = packet.senderName ?: currentPartner,
                        isFromMe = false,
                        text = packet.text ?: "",
                        type = MessageType.FILE,
                        timestamp = packet.timestamp,
                        status = initialStatus,
                        attachmentPath = localPath,
                        attachmentName = packet.fileName ?: "attachment",
                        attachmentSize = packet.fileSize,
                        mimeType = packet.mimeType,
                        transferProgress = 1.0f,
                        replyToId = packet.replyToId,
                        replyToText = packet.replyToText,
                        replyToSender = packet.replyToSender,
                        expiresAt = expiresAt,
                        disappearingDuration = duration
                    )
                    chatRepository.insertMessage(incomingMessage)

                    if (!isViewed) {
                        NotificationHelper.showMessageNotification(
                            context = context,
                            senderName = packet.senderName ?: currentPartner,
                            messageText = "📁 Sent you a file (${packet.fileName ?: "attachment"})",
                            roomId = currentRoom
                        )
                    }
                } else if (localPath != null && existing.attachmentPath.isNullOrBlank()) {
                    chatRepository.updateAttachmentPath(messageId, localPath)
                    chatRepository.updateMessageStatus(messageId, initialStatus)
                }

                if (isViewed) {
                    sendReadAck(messageId, currentRoom)
                } else {
                    sendDeliveryAck(messageId, currentRoom)
                }
            }

            "audio" -> {
                val messageId = packet.messageId ?: UUID.randomUUID().toString()
                val existing = chatRepository.getMessage(messageId)

                var localPath: String? = null
                val base64 = packet.dataBase64
                if (!base64.isNullOrBlank()) {
                    try {
                        val bytes = Base64.decode(base64, Base64.DEFAULT)
                        localPath = FileUtils.saveTransferBytesToAppStorage(
                            context,
                            bytes,
                            packet.fileName ?: "voice_note.m4a"
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed saving incoming audio: ${e.localizedMessage}")
                    }
                }

                val isViewed = AshuChatApp.isAppInForeground && AshuChatApp.activeChatRoom == currentRoom
                val duration = packet.disappearingDuration
                val initialStatus = if (isViewed) MessageStatus.READ else MessageStatus.DELIVERED
                val expiresAt = if (isViewed && duration != null && duration > 0) {
                    System.currentTimeMillis() + (duration * 1000L)
                } else null

                if (existing == null) {
                    val durationSec = (packet.audioDurationMs ?: 0L) / 1000
                    val incomingMessage = ChatMessage(
                        id = messageId,
                        roomId = currentRoom,
                        senderName = packet.senderName ?: currentPartner,
                        isFromMe = false,
                        text = "Voice message (${durationSec}s)",
                        type = MessageType.AUDIO,
                        timestamp = packet.timestamp,
                        status = initialStatus,
                        attachmentPath = localPath,
                        attachmentName = packet.fileName ?: "voice_note.m4a",
                        attachmentSize = packet.fileSize,
                        mimeType = packet.mimeType ?: "audio/mp4",
                        transferProgress = 1.0f,
                        replyToId = packet.replyToId,
                        replyToText = packet.replyToText,
                        replyToSender = packet.replyToSender,
                        expiresAt = expiresAt,
                        disappearingDuration = duration
                    )
                    chatRepository.insertMessage(incomingMessage)

                    if (!isViewed) {
                        NotificationHelper.showMessageNotification(
                            context = context,
                            senderName = packet.senderName ?: currentPartner,
                            messageText = "🎙️ Sent you a voice message",
                            roomId = currentRoom
                        )
                    }
                }

                if (isViewed) {
                    sendReadAck(messageId, currentRoom)
                } else {
                    sendDeliveryAck(messageId, currentRoom)
                }
            }

            "ack" -> {
                packet.messageId?.let { id ->
                    val status = if (packet.status == "READ") MessageStatus.READ else MessageStatus.DELIVERED
                    chatRepository.updateMessageStatus(id, status)

                    if (status == MessageStatus.READ) {
                        val msg = chatRepository.getMessage(id)
                        if (msg != null && msg.disappearingDuration != null && msg.disappearingDuration > 0) {
                            if (msg.expiresAt == null) {
                                val readTimestamp = if (packet.timestamp > 0) packet.timestamp else System.currentTimeMillis()
                                val expiresAt = readTimestamp + (msg.disappearingDuration * 1000L)
                                chatRepository.updateMessageExpiration(id, expiresAt)
                            }
                        }
                    }
                }
            }

            "reaction" -> {
                packet.messageId?.let { id ->
                    packet.reaction?.let { r ->
                        chatRepository.updateMessageReaction(id, r)
                    }
                }
            }

            "typing" -> {
                _isPartnerTyping.value = packet.isTyping == true
                typingJob?.cancel()
                if (packet.isTyping == true) {
                    typingJob = scope.launch {
                        delay(3000)
                        _isPartnerTyping.value = false
                    }
                }
            }

            "call_offer" -> {
                Log.d(TAG, "Incoming call offer received from ${packet.senderName}")
                if (_callState.value == CallState.IDLE) {
                    _callState.value = CallState.INCOMING
                    val caller = packet.senderName ?: _partnerName.value
                    CallNotificationHelper.showIncomingCallNotification(
                        context = context,
                        callerName = caller,
                        roomId = currentRoom
                    )
                }
            }

            "call_answer" -> {
                Log.d(TAG, "Incoming call answer received")
                if (_callState.value == CallState.OUTGOING) {
                    _callState.value = CallState.CONNECTED
                    CallNotificationHelper.dismissIncomingCallNotification(context)
                    startCallDurationTimer()
                    webRtcManager.sendRenegotiationOffer()
                }
            }

            "call_reject", "call_end" -> {
                Log.d(TAG, "Call ended or rejected by peer")
                _callState.value = CallState.IDLE
                stopCallDurationTimer()
                webRtcManager.endAudioCall()
                CallNotificationHelper.dismissIncomingCallNotification(context)
            }

            "sdp_offer" -> {
                Log.d(TAG, "Direct P2P SDP Offer received")
                packet.text?.let { webRtcManager.onOfferReceived(it) }
            }

            "sdp_answer" -> {
                Log.d(TAG, "Direct P2P SDP Answer received")
                packet.text?.let { webRtcManager.onAnswerReceived(it) }
            }
        }
    }

    fun sendReadAck(messageId: String, roomId: String) {
        val ackPacket = DataChannelPacket(
            type = "ack",
            messageId = messageId,
            status = "READ",
            roomId = roomId,
            timestamp = System.currentTimeMillis()
        )
        if (webRtcManager.isP2pConnected()) {
            webRtcManager.sendPacket(ackPacket)
        } else {
            webRtcManager.sendOfflineAckForRoom(roomId, messageId, "READ")
        }
    }

    private fun sendDeliveryAck(messageId: String, roomId: String) {
        val ackPacket = DataChannelPacket(
            type = "ack",
            messageId = messageId,
            status = "DELIVERED",
            roomId = roomId,
            timestamp = System.currentTimeMillis()
        )
        if (webRtcManager.isP2pConnected()) {
            webRtcManager.sendPacket(ackPacket)
        } else {
            webRtcManager.sendOfflineAck(messageId, "DELIVERED")
        }
    }

    fun triggerPingAnimation() {
        scope.launch {
            _showPingEffect.value = true
            delay(2500)
            _showPingEffect.value = false
        }
    }

    // Call Actions
    fun startCall() {
        val currentRoom = _activeRoomId.value.ifBlank { prefs.roomId.trim().uppercase() }
        _callState.value = CallState.OUTGOING
        _callDurationSeconds.value = 0
        _isCallMuted.value = false
        _isCallSpeakerOn.value = true

        webRtcManager.startAudioCall()

        val packet = DataChannelPacket(
            type = "call_offer",
            senderName = prefs.myName,
            roomId = currentRoom,
            timestamp = System.currentTimeMillis()
        )
        if (webRtcManager.isP2pConnected()) {
            webRtcManager.sendPacket(packet)
        } else {
            webRtcManager.sendOfflinePacketForRoom(currentRoom, packet)
        }
    }

    fun acceptCall() {
        val context = applicationContext
        if (context != null) {
            CallNotificationHelper.dismissIncomingCallNotification(context)
        }

        val currentRoom = _activeRoomId.value.ifBlank { prefs.roomId.trim().uppercase() }
        _callState.value = CallState.CONNECTED
        _callDurationSeconds.value = 0
        _isCallMuted.value = false
        _isCallSpeakerOn.value = true

        webRtcManager.acceptAudioCall()

        val packet = DataChannelPacket(
            type = "call_answer",
            senderName = prefs.myName,
            roomId = currentRoom,
            timestamp = System.currentTimeMillis()
        )
        if (webRtcManager.isP2pConnected()) {
            webRtcManager.sendPacket(packet)
        } else {
            webRtcManager.sendOfflinePacketForRoom(currentRoom, packet)
        }

        startCallDurationTimer()
    }

    fun rejectCall() {
        val context = applicationContext
        if (context != null) {
            CallNotificationHelper.dismissIncomingCallNotification(context)
        }

        val currentRoom = _activeRoomId.value.ifBlank { prefs.roomId.trim().uppercase() }
        _callState.value = CallState.IDLE
        stopCallDurationTimer()
        webRtcManager.endAudioCall()

        val packet = DataChannelPacket(
            type = "call_reject",
            senderName = prefs.myName,
            roomId = currentRoom,
            timestamp = System.currentTimeMillis()
        )
        if (webRtcManager.isP2pConnected()) {
            webRtcManager.sendPacket(packet)
        } else {
            webRtcManager.sendOfflinePacketForRoom(currentRoom, packet)
        }
    }

    fun endCall() {
        val context = applicationContext
        if (context != null) {
            CallNotificationHelper.dismissIncomingCallNotification(context)
        }

        val currentRoom = _activeRoomId.value.ifBlank { prefs.roomId.trim().uppercase() }
        _callState.value = CallState.IDLE
        stopCallDurationTimer()
        webRtcManager.endAudioCall()

        val packet = DataChannelPacket(
            type = "call_end",
            senderName = prefs.myName,
            roomId = currentRoom,
            timestamp = System.currentTimeMillis()
        )
        if (webRtcManager.isP2pConnected()) {
            webRtcManager.sendPacket(packet)
        } else {
            webRtcManager.sendOfflinePacketForRoom(currentRoom, packet)
        }
    }

    fun toggleCallMute() {
        val newMuted = !_isCallMuted.value
        _isCallMuted.value = newMuted
        webRtcManager.toggleMute(newMuted)
    }

    fun toggleCallSpeaker() {
        val newSpeaker = !_isCallSpeakerOn.value
        _isCallSpeakerOn.value = newSpeaker
        webRtcManager.toggleSpeaker(newSpeaker)
    }

    private fun startCallDurationTimer() {
        callDurationJob?.cancel()
        callDurationJob = scope.launch {
            while (isActive && _callState.value == CallState.CONNECTED) {
                delay(1000)
                _callDurationSeconds.value += 1
            }
        }
    }

    private fun stopCallDurationTimer() {
        callDurationJob?.cancel()
        callDurationJob = null
    }
}
