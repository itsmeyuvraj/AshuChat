package com.ashuchat.app.webrtc

import android.content.Context
import android.net.Uri
import android.os.PowerManager
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
import com.ashuchat.app.utils.TimeUtils
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
import java.io.File
import java.util.UUID

/**
 * Global persistent connection & background coordinator.
 * Maintains WebRTC P2P and serverless signaling connections across both
 * foreground and background, enabling real-time calls and message reception
 * even when the app is closed.
 */
object ChatConnectionManager {

    private const val TAG = "ChatConnectionMgr"
    private val gson = com.google.gson.Gson()


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

    private val _isVideoCall = MutableStateFlow(false)
    val isVideoCall: StateFlow<Boolean> = _isVideoCall.asStateFlow()

    val remoteVideoTrack: StateFlow<org.webrtc.VideoTrack?> get() = webRtcManager.remoteVideoTrackFlow
    val localVideoTrack: StateFlow<org.webrtc.VideoTrack?> get() = webRtcManager.localVideoTrackFlow
    val isLocalVideoEnabled: StateFlow<Boolean> get() = webRtcManager.isLocalVideoEnabled
    val isFrontCamera: StateFlow<Boolean> get() = webRtcManager.isFrontCamera
    val eglBaseContext: org.webrtc.EglBase.Context get() = webRtcManager.eglBaseContext

    private val _isPartnerTyping = MutableStateFlow(false)
    val isPartnerTyping: StateFlow<Boolean> = _isPartnerTyping.asStateFlow()

    private val _showPingEffect = MutableStateFlow(false)
    val showPingEffect: StateFlow<Boolean> = _showPingEffect.asStateFlow()

    private const val CALL_RINGING_TIMEOUT_MS = 40_000L

    private var callDurationJob: Job? = null
    private var typingJob: Job? = null
    private var outgoingCallTimeoutJob: Job? = null
    private var incomingCallTimeoutJob: Job? = null
    private var callDisconnectJob: Job? = null
    private var proximityWakeLock: PowerManager.WakeLock? = null

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
        observeConnectionState()

        // Sync video call flag if remote video track arrives
        scope.launch {
            webRtcManager.remoteVideoTrackFlow.collect { track ->
                if (track != null) {
                    _isVideoCall.value = true
                }
            }
        }

        // Periodic cleanup of expired disappearing messages (optimized interval for battery efficiency)
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(15_000L)
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
        flushPendingMessages(clean)
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
                    flushAllPendingMessages()
                }
            }
        }
    }

    private fun observeConnectionState() {
        scope.launch {
            webRtcManager.connectionState.collect { state ->
                when (state) {
                    P2PConnectionState.CONNECTED_P2P -> {
                        callDisconnectJob?.cancel()
                        callDisconnectJob = null
                        val currentRoom = _activeRoomId.value.ifBlank { prefs.roomId.trim().uppercase() }
                        flushPendingMessages(currentRoom)
                    }
                    P2PConnectionState.FAILED,
                    P2PConnectionState.WAITING_FOR_PEER,
                    P2PConnectionState.DISCONNECTED -> {
                        if (_callState.value == CallState.CONNECTED) {
                            if (callDisconnectJob == null || callDisconnectJob?.isActive == false) {
                                callDisconnectJob = scope.launch {
                                    Log.w(TAG, "WebRTC P2P disconnected during active call. Waiting 10s for auto-reconnect...")
                                    delay(10_000L)
                                    if (_callState.value == CallState.CONNECTED &&
                                        webRtcManager.connectionState.value != P2PConnectionState.CONNECTED_P2P) {
                                        Log.w(TAG, "Call reconnect grace expired. Cleanly ending call.")
                                        endCall()
                                    }
                                }
                            }
                        }
                    }
                    else -> {}
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
                Log.d(TAG, "Incoming call offer received from ${packet.senderName}, isVideo: ${packet.isVideoCall}")
                val isVideo = packet.isVideoCall == true
                val caller = packet.senderName ?: _partnerName.value

                if (_callState.value == CallState.IDLE) {
                    cancelCallTimeouts()
                    _isVideoCall.value = isVideo
                    _callState.value = CallState.INCOMING
                    CallNotificationHelper.showIncomingCallNotification(
                        context = context,
                        callerName = caller,
                        roomId = currentRoom,
                        isVideo = isVideo
                    )
                    incomingCallTimeoutJob = scope.launch {
                        delay(CALL_RINGING_TIMEOUT_MS)
                        if (_callState.value == CallState.INCOMING) {
                            Log.w(TAG, "Incoming call timed out after ${CALL_RINGING_TIMEOUT_MS / 1000}s (missed)")
                            CallNotificationHelper.dismissIncomingCallNotification(context)
                            webRtcManager.endCall()
                            _callState.value = CallState.IDLE
                            _isVideoCall.value = false
                            NotificationHelper.showMissedCallNotification(
                                context = context,
                                callerName = caller,
                                roomId = currentRoom,
                                isVideo = isVideo
                            )
                            recordCallEventInChat(
                                roomId = currentRoom,
                                callerName = caller,
                                isFromMe = false,
                                text = "📞 Missed ${if (isVideo) "video" else "voice"} call"
                            )
                        }
                    }
                } else if (_callState.value == CallState.OUTGOING) {
                    // Glare condition: Both users called each other simultaneously!
                    val myId = prefs.clientId
                    val peerId = packet.senderClientId ?: packet.senderName ?: ""
                    Log.d(TAG, "Call glare collision detected: myId=$myId vs peerId=$peerId")
                    if (peerId > myId) {
                        Log.d(TAG, "Yielding outgoing call to peer $peerId, auto-answering incoming offer")
                        cancelCallTimeouts()
                        _isVideoCall.value = isVideo || _isVideoCall.value
                        acceptCall()
                    } else {
                        Log.d(TAG, "Winning glare collision against $peerId. Waiting for peer to answer.")
                    }
                }
            }

            "call_answer" -> {
                Log.d(TAG, "Incoming call answer received, isVideo: ${packet.isVideoCall}")
                if (_callState.value == CallState.OUTGOING) {
                    cancelCallTimeouts()
                    _callState.value = CallState.CONNECTED
                    if (packet.isVideoCall == true) {
                        _isVideoCall.value = true
                    }
                    CallNotificationHelper.dismissIncomingCallNotification(context)
                    startCallDurationTimer()
                    webRtcManager.sendRenegotiationOffer(isVideo = _isVideoCall.value)
                    updateProximitySensor()
                }
            }

            "call_reject", "call_end" -> {
                Log.d(TAG, "Call ended or rejected by peer (previous state: ${_callState.value})")
                val wasIncoming = (_callState.value == CallState.INCOMING)
                val wasOutgoing = (_callState.value == CallState.OUTGOING)
                val wasConnected = (_callState.value == CallState.CONNECTED)
                val duration = _callDurationSeconds.value
                val isVideo = _isVideoCall.value
                val caller = packet.senderName ?: _partnerName.value

                cancelCallTimeouts()
                callDisconnectJob?.cancel()
                callDisconnectJob = null

                _callState.value = CallState.IDLE
                _isVideoCall.value = false
                stopCallDurationTimer()
                webRtcManager.endCall()
                releaseProximitySensor()
                CallNotificationHelper.dismissIncomingCallNotification(context)

                if (wasIncoming) {
                    // Caller cancelled before answer: notify as missed call and log to chat
                    NotificationHelper.showMissedCallNotification(
                        context = context,
                        callerName = caller,
                        roomId = currentRoom,
                        isVideo = isVideo
                    )
                    recordCallEventInChat(
                        roomId = currentRoom,
                        callerName = caller,
                        isFromMe = false,
                        text = "📞 Missed ${if (isVideo) "video" else "voice"} call"
                    )
                } else if (wasOutgoing) {
                    recordCallEventInChat(
                        roomId = currentRoom,
                        callerName = caller,
                        isFromMe = true,
                        text = "📞 Call declined"
                    )
                } else if (wasConnected && duration > 0) {
                    recordCallEventInChat(
                        roomId = currentRoom,
                        callerName = caller,
                        isFromMe = false,
                        text = "📞 Call ended (${TimeUtils.formatCallDuration(duration)})"
                    )
                }
            }

            "sdp_offer" -> {
                Log.d(TAG, "Direct P2P SDP Offer received")
                packet.text?.let { webRtcManager.onOfferReceived(it) }
            }

            "sdp_answer" -> {
                Log.d(TAG, "Direct P2P SDP Answer received")
                packet.text?.let { webRtcManager.onAnswerReceived(it) }
            }

            "ice_candidate" -> {
                Log.d(TAG, "Direct P2P ICE Candidate received")
                try {
                    val candidate = gson.fromJson(packet.text, org.webrtc.IceCandidate::class.java)
                    if (candidate != null) {
                        webRtcManager.onIceCandidateReceived(candidate)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing P2P ICE candidate: ${e.localizedMessage}")
                }
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

    fun setVideoCall(isVideo: Boolean) {
        _isVideoCall.value = isVideo
    }

    // Call Actions
    fun startCall(isVideo: Boolean = false) {
        val currentRoom = _activeRoomId.value.ifBlank { prefs.roomId.trim().uppercase() }
        cancelCallTimeouts()

        _isVideoCall.value = isVideo
        _callState.value = CallState.OUTGOING
        _callDurationSeconds.value = 0
        _isCallMuted.value = false
        _isCallSpeakerOn.value = true

        if (isVideo) {
            webRtcManager.prepareLocalVideoCall()
        } else {
            webRtcManager.prepareLocalAudioCall()
        }

        val packet = DataChannelPacket(
            messageId = UUID.randomUUID().toString(),
            type = "call_offer",
            senderName = prefs.myName,
            senderClientId = prefs.clientId,
            roomId = currentRoom,
            isVideoCall = isVideo,
            timestamp = System.currentTimeMillis()
        )
        if (webRtcManager.isP2pConnected()) {
            webRtcManager.sendPacket(packet)
        } else {
            webRtcManager.sendOfflinePacketForRoom(currentRoom, packet)
        }

        outgoingCallTimeoutJob = scope.launch {
            delay(CALL_RINGING_TIMEOUT_MS)
            if (_callState.value == CallState.OUTGOING) {
                Log.w(TAG, "Outgoing call timed out after ${CALL_RINGING_TIMEOUT_MS / 1000}s (unanswered)")
                val endPacket = DataChannelPacket(
                    messageId = UUID.randomUUID().toString(),
                    type = "call_end",
                    senderName = prefs.myName,
                    senderClientId = prefs.clientId,
                    roomId = currentRoom,
                    timestamp = System.currentTimeMillis()
                )
                if (webRtcManager.isP2pConnected()) {
                    webRtcManager.sendPacket(endPacket)
                } else {
                    webRtcManager.sendOfflinePacketForRoom(currentRoom, endPacket)
                }

                webRtcManager.endCall()
                _callState.value = CallState.IDLE
                _isVideoCall.value = false
                releaseProximitySensor()

                recordCallEventInChat(
                    roomId = currentRoom,
                    callerName = prefs.myName,
                    isFromMe = true,
                    text = "📞 Unanswered ${if (isVideo) "video" else "voice"} call"
                )
            }
        }
    }

    fun acceptCall() {
        cancelCallTimeouts()
        val context = applicationContext
        if (context != null) {
            CallNotificationHelper.dismissIncomingCallNotification(context)
        }

        val currentRoom = _activeRoomId.value.ifBlank { prefs.roomId.trim().uppercase() }
        _callState.value = CallState.CONNECTED
        _callDurationSeconds.value = 0
        _isCallMuted.value = false
        _isCallSpeakerOn.value = true

        val isVideo = _isVideoCall.value
        if (isVideo) {
            webRtcManager.prepareLocalVideoCall()
        } else {
            webRtcManager.prepareLocalAudioCall()
        }

        val packet = DataChannelPacket(
            messageId = UUID.randomUUID().toString(),
            type = "call_answer",
            senderName = prefs.myName,
            senderClientId = prefs.clientId,
            roomId = currentRoom,
            isVideoCall = isVideo,
            timestamp = System.currentTimeMillis()
        )
        if (webRtcManager.isP2pConnected()) {
            webRtcManager.sendPacket(packet)
        } else {
            webRtcManager.sendOfflinePacketForRoom(currentRoom, packet)
        }

        startCallDurationTimer()
        updateProximitySensor()
    }

    fun rejectCall() {
        cancelCallTimeouts()
        val context = applicationContext
        if (context != null) {
            CallNotificationHelper.dismissIncomingCallNotification(context)
        }

        val currentRoom = _activeRoomId.value.ifBlank { prefs.roomId.trim().uppercase() }
        _callState.value = CallState.IDLE
        _isVideoCall.value = false
        stopCallDurationTimer()
        webRtcManager.endCall()
        releaseProximitySensor()

        val packet = DataChannelPacket(
            messageId = UUID.randomUUID().toString(),
            type = "call_reject",
            senderName = prefs.myName,
            senderClientId = prefs.clientId,
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
        cancelCallTimeouts()
        callDisconnectJob?.cancel()
        callDisconnectJob = null

        val context = applicationContext
        if (context != null) {
            CallNotificationHelper.dismissIncomingCallNotification(context)
        }

        val currentRoom = _activeRoomId.value.ifBlank { prefs.roomId.trim().uppercase() }
        val wasConnected = (_callState.value == CallState.CONNECTED)
        val duration = _callDurationSeconds.value

        _callState.value = CallState.IDLE
        _isVideoCall.value = false
        stopCallDurationTimer()
        webRtcManager.endCall()
        releaseProximitySensor()

        if (wasConnected && duration > 0) {
            scope.launch {
                recordCallEventInChat(
                    roomId = currentRoom,
                    callerName = prefs.myName,
                    isFromMe = true,
                    text = "📞 Call ended (${TimeUtils.formatCallDuration(duration)})"
                )
            }
        }

        val packet = DataChannelPacket(
            messageId = UUID.randomUUID().toString(),
            type = "call_end",
            senderName = prefs.myName,
            senderClientId = prefs.clientId,
            roomId = currentRoom,
            timestamp = System.currentTimeMillis()
        )
        if (webRtcManager.isP2pConnected()) {
            webRtcManager.sendPacket(packet)
        } else {
            webRtcManager.sendOfflinePacketForRoom(currentRoom, packet)
        }
    }

    fun switchCamera() {
        webRtcManager.switchCamera()
    }

    fun toggleCallVideo() {
        val newEnabled = !webRtcManager.isLocalVideoEnabled.value
        webRtcManager.toggleVideo(newEnabled)
        updateProximitySensor()
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
        updateProximitySensor()
    }

    private fun cancelCallTimeouts() {
        outgoingCallTimeoutJob?.cancel()
        outgoingCallTimeoutJob = null
        incomingCallTimeoutJob?.cancel()
        incomingCallTimeoutJob = null
    }

    private fun updateProximitySensor() {
        val context = applicationContext ?: return
        val shouldHoldProximity = (_callState.value == CallState.CONNECTED) &&
                !_isVideoCall.value &&
                !_isCallSpeakerOn.value

        if (shouldHoldProximity) {
            if (proximityWakeLock == null) {
                try {
                    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    if (pm != null && pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                        proximityWakeLock = pm.newWakeLock(
                            PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                            "AshuChat:InCallProximityWakeLock"
                        ).apply {
                            setReferenceCounted(false)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed creating proximity wake lock: ${e.localizedMessage}")
                }
            }
            try {
                if (proximityWakeLock?.isHeld == false) {
                    proximityWakeLock?.acquire(2 * 60 * 60 * 1000L) // Safe 2-hour max acquisition
                    Log.d(TAG, "Proximity wake lock acquired for in-ear audio call")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed acquiring proximity wake lock: ${e.localizedMessage}")
            }
        } else {
            releaseProximitySensor()
        }
    }

    private fun releaseProximitySensor() {
        try {
            if (proximityWakeLock?.isHeld == true) {
                proximityWakeLock?.release()
                Log.d(TAG, "Proximity wake lock released")
            }
        } catch (_: Exception) {}
        proximityWakeLock = null
    }

    private suspend fun recordCallEventInChat(roomId: String, callerName: String, isFromMe: Boolean, text: String) {
        try {
            val msg = ChatMessage(
                id = UUID.randomUUID().toString(),
                roomId = roomId,
                senderName = if (isFromMe) prefs.myName else callerName,
                isFromMe = isFromMe,
                text = text,
                type = MessageType.TEXT,
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.DELIVERED
            )
            chatRepository.insertMessage(msg)
        } catch (e: Exception) {
            Log.w(TAG, "Failed recording call event in chat: ${e.localizedMessage}")
        }
    }

    fun flushPendingMessages(roomId: String? = null) {
        val targetRoom = roomId?.takeIf { it.isNotBlank() }
            ?: _activeRoomId.value.ifBlank { prefs.roomId.trim().uppercase() }
        if (targetRoom.isBlank()) return

        scope.launch(Dispatchers.IO) {
            val pending = chatRepository.getPendingOutgoingMessagesForRoom(targetRoom)
            if (pending.isEmpty()) return@launch
            Log.d(TAG, "Flushing ${pending.size} pending messages for room $targetRoom")
            for (msg in pending) {
                dispatchOutgoingMessage(msg)
                delay(80)
            }
        }
    }

    fun flushAllPendingMessages() {
        scope.launch(Dispatchers.IO) {
            val pending = chatRepository.getPendingOutgoingMessages()
            if (pending.isEmpty()) return@launch
            Log.d(TAG, "Flushing ${pending.size} pending messages across all rooms")
            for (msg in pending) {
                dispatchOutgoingMessage(msg)
                delay(80)
            }
        }
    }

    fun retryMessage(messageId: String) {
        scope.launch(Dispatchers.IO) {
            val msg = chatRepository.getMessage(messageId) ?: return@launch
            chatRepository.updateMessageStatus(messageId, MessageStatus.SENDING)
            dispatchOutgoingMessage(msg)
        }
    }

    suspend fun dispatchOutgoingMessage(msg: ChatMessage) {
        val context = applicationContext ?: return
        when (msg.type) {
            MessageType.TEXT, MessageType.PING, MessageType.LOVE_PING -> {
                val packet = DataChannelPacket(
                    type = if (msg.type == MessageType.PING || msg.type == MessageType.LOVE_PING) "ping" else "text",
                    messageId = msg.id,
                    senderName = prefs.myName,
                    senderClientId = prefs.clientId,
                    text = msg.text,
                    roomId = msg.roomId,
                    timestamp = msg.timestamp,
                    replyToId = msg.replyToId,
                    replyToText = msg.replyToText,
                    replyToSender = msg.replyToSender,
                    disappearingDuration = msg.disappearingDuration
                )

                if (msg.roomId == _activeRoomId.value && webRtcManager.isP2pConnected()) {
                    val sent = webRtcManager.sendPacket(packet)
                    if (sent) {
                        chatRepository.updateMessageStatus(msg.id, MessageStatus.SENT)
                        // P2P ACK watchdog: if peer does not acknowledge within 4.5s, fallback to cloud relay
                        scope.launch {
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
                    scope.launch(Dispatchers.IO) {
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
                    val base64 = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
                    val imagePacket = DataChannelPacket(
                        type = "image",
                        messageId = msg.id,
                        senderName = prefs.myName,
                        senderClientId = prefs.clientId,
                        fileName = msg.attachmentName ?: "photo.jpg",
                        fileSize = compressedBytes.size.toLong(),
                        mimeType = msg.mimeType ?: "image/jpeg",
                        roomId = msg.roomId,
                        timestamp = msg.timestamp,
                        dataBase64 = base64,
                        replyToId = msg.replyToId,
                        replyToText = msg.replyToText,
                        replyToSender = msg.replyToSender,
                        disappearingDuration = msg.disappearingDuration
                    )

                    if (msg.roomId == _activeRoomId.value && webRtcManager.isP2pConnected()) {
                        val sent = webRtcManager.sendPacket(imagePacket)
                        if (sent) {
                            chatRepository.updateMessageStatus(msg.id, MessageStatus.SENT)
                            scope.launch {
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
                        scope.launch(Dispatchers.IO) {
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
                if (msg.roomId == _activeRoomId.value && webRtcManager.isP2pConnected()) {
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
                        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        val filePacket = DataChannelPacket(
                            type = "file",
                            messageId = msg.id,
                            senderName = prefs.myName,
                            senderClientId = prefs.clientId,
                            fileName = msg.attachmentName ?: file.name,
                            fileSize = file.length(),
                            mimeType = msg.mimeType,
                            roomId = msg.roomId,
                            timestamp = msg.timestamp,
                            dataBase64 = base64,
                            disappearingDuration = msg.disappearingDuration
                        )
                        webRtcManager.sendOfflinePacketForRoom(msg.roomId, filePacket) { success ->
                            scope.launch(Dispatchers.IO) {
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
                        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        val audioPacket = DataChannelPacket(
                            type = "audio",
                            messageId = msg.id,
                            senderName = prefs.myName,
                            senderClientId = prefs.clientId,
                            fileName = msg.attachmentName ?: file.name,
                            fileSize = file.length(),
                            mimeType = msg.mimeType ?: "audio/mp4",
                            roomId = msg.roomId,
                            timestamp = msg.timestamp,
                            dataBase64 = base64,
                            replyToId = msg.replyToId,
                            replyToText = msg.replyToText,
                            replyToSender = msg.replyToSender,
                            disappearingDuration = msg.disappearingDuration
                        )
                        if (msg.roomId == _activeRoomId.value && webRtcManager.isP2pConnected()) {
                            val sent = webRtcManager.sendPacket(audioPacket)
                            if (sent) {
                                chatRepository.updateMessageStatus(msg.id, MessageStatus.SENT)
                                scope.launch {
                                    delay(4500)
                                    val curr = chatRepository.getMessage(msg.id)
                                    if (curr != null && curr.status != MessageStatus.DELIVERED && curr.status != MessageStatus.READ) {
                                        webRtcManager.sendOfflinePacketForRoom(msg.roomId, audioPacket)
                                    }
                                }
                                return
                            }
                        }
                        webRtcManager.sendOfflinePacketForRoom(msg.roomId, audioPacket) { success ->
                            scope.launch(Dispatchers.IO) {
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
