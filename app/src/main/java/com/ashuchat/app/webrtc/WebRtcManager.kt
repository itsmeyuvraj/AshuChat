package com.ashuchat.app.webrtc

import android.content.Context
import android.net.Uri
import android.util.Log
import com.ashuchat.app.data.model.DataChannelPacket
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.EglBase
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.HardwareVideoEncoderFactory
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpSender
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoCodecInfo
import org.webrtc.VideoDecoder
import org.webrtc.VideoDecoderFactory
import org.webrtc.VideoEncoder
import org.webrtc.VideoEncoderFactory
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

enum class P2PConnectionState {
    DISCONNECTED,
    CONNECTING_SIGNALING,
    WAITING_FOR_PEER,
    NEGOTIATING_WEBRTC,
    CONNECTED_P2P,
    FAILED
}

class WebRtcManager(
    private val context: Context,
    private val scope: CoroutineScope
) : SignalingClient.Listener, FileTransferManager.Listener {

    companion object {
        private const val TAG = "WebRtcManager"
        private const val DATA_CHANNEL_LABEL = "ashuchat_p2p_channel"
        private const val HEARTBEAT_INTERVAL_MS = 18_000L
        private const val HEARTBEAT_TIMEOUT_MS = 45_000L
    }

    private val gson = Gson()
    private val signalingClient = SignalingClient(this)
    val fileTransferManager = FileTransferManager(context, scope, this)

    private val _connectionState = MutableStateFlow(P2PConnectionState.DISCONNECTED)
    val connectionState: StateFlow<P2PConnectionState> = _connectionState.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<DataChannelPacket>(extraBufferCapacity = 64)
    val incomingPackets: SharedFlow<DataChannelPacket> = _incomingPackets.asSharedFlow()

    private val _fileProgressFlow = MutableSharedFlow<Pair<String, Float>>(extraBufferCapacity = 64)
    val fileProgressFlow: SharedFlow<Pair<String, Float>> = _fileProgressFlow.asSharedFlow()

    data class FileTransferCompleteEvent(
        val messageId: String,
        val localPath: String,
        val isIncoming: Boolean,
        val disappearingDuration: Long? = null
    )

    private val _fileCompleteFlow = MutableSharedFlow<FileTransferCompleteEvent>(extraBufferCapacity = 64)
    val fileCompleteFlow: SharedFlow<FileTransferCompleteEvent> = _fileCompleteFlow.asSharedFlow()

    private val _fileFailedFlow = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 64)
    val fileFailedFlow: SharedFlow<Pair<String, String>> = _fileFailedFlow.asSharedFlow()

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null

    private var isInitiator = false
    private val pendingIceCandidates = mutableListOf<IceCandidate>()
    private var currentClientId: String? = null
    private var currentClientName: String = ""
    private var currentServerUrl: String = ""
    private var currentRoomId: String? = null
    private var connectedPeerId: String? = null

    private var heartbeatJob: Job? = null
    private var lastPongTimestamp: Long = 0L

    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var remoteAudioTrack: AudioTrack? = null
    private var localAudioSender: RtpSender? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    val rootEglBase: EglBase by lazy { EglBase.create() }
    val eglBaseContext: EglBase.Context get() = rootEglBase.eglBaseContext

    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var localVideoSender: RtpSender? = null

    private val _localVideoTrackFlow = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrackFlow: StateFlow<VideoTrack?> = _localVideoTrackFlow.asStateFlow()

    private val _remoteVideoTrackFlow = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrackFlow: StateFlow<VideoTrack?> = _remoteVideoTrackFlow.asStateFlow()

    private val _isVideoCallActive = MutableStateFlow(false)
    val isVideoCallActive: StateFlow<Boolean> = _isVideoCallActive.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(true)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    private val _isLocalVideoEnabled = MutableStateFlow(true)
    val isLocalVideoEnabled: StateFlow<Boolean> = _isLocalVideoEnabled.asStateFlow()

    private val _peerNameFlow = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val peerNameFlow: SharedFlow<String> = _peerNameFlow.asSharedFlow()

    init {
        initPeerConnectionFactory()
    }

    private fun initPeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val adm = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
                override fun onWebRtcAudioRecordInitError(errorMessage: String?) {
                    Log.e(TAG, "AudioRecord init error: $errorMessage")
                }
                override fun onWebRtcAudioRecordStartError(errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode?, errorMessage: String?) {
                    Log.e(TAG, "AudioRecord start error: $errorCode, $errorMessage")
                }
                override fun onWebRtcAudioRecordError(errorMessage: String?) {
                    Log.e(TAG, "AudioRecord error: $errorMessage")
                }
            })
            .setAudioTrackErrorCallback(object : JavaAudioDeviceModule.AudioTrackErrorCallback {
                override fun onWebRtcAudioTrackInitError(errorMessage: String?) {
                    Log.e(TAG, "AudioTrack init error: $errorMessage")
                }
                override fun onWebRtcAudioTrackStartError(errorCode: JavaAudioDeviceModule.AudioTrackStartErrorCode?, errorMessage: String?) {
                    Log.e(TAG, "AudioTrack start error: $errorCode, $errorMessage")
                }
                override fun onWebRtcAudioTrackError(errorMessage: String?) {
                    Log.e(TAG, "AudioTrack error: $errorMessage")
                }
            })
            .createAudioDeviceModule()

        val defaultEncoder = DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true)
        val videoEncoderFactory = object : VideoEncoderFactory {
            override fun createEncoder(info: VideoCodecInfo): VideoEncoder? {
                return try {
                    val encoder = defaultEncoder.createEncoder(info)
                    Log.d(TAG, "Video encoder created for ${info.name}: $encoder")
                    encoder
                } catch (e: Exception) {
                    Log.w(TAG, "Video encoder creation failed for ${info.name}: ${e.localizedMessage}")
                    null
                }
            }

            override fun getSupportedCodecs(): Array<VideoCodecInfo> {
                return try {
                    val codecs = defaultEncoder.supportedCodecs
                    Log.d(TAG, "Supported video encoder codecs: ${codecs.map { c -> c.name }}")
                    codecs
                } catch (e: Exception) {
                    emptyArray()
                }
            }
        }

        val defaultDecoder = DefaultVideoDecoderFactory(rootEglBase.eglBaseContext)
        val videoDecoderFactory = object : VideoDecoderFactory {
            override fun createDecoder(info: VideoCodecInfo): VideoDecoder? {
                return try {
                    val decoder = defaultDecoder.createDecoder(info)
                    Log.d(TAG, "Video decoder created for ${info.name}: $decoder")
                    decoder
                } catch (e: Exception) {
                    Log.w(TAG, "Video decoder creation failed for ${info.name}: ${e.localizedMessage}")
                    null
                }
            }

            override fun getSupportedCodecs(): Array<VideoCodecInfo> {
                return try {
                    val codecs = defaultDecoder.supportedCodecs
                    Log.d(TAG, "Supported video decoder codecs: ${codecs.map { c -> c.name }}")
                    codecs
                } catch (e: Exception) {
                    emptyArray()
                }
            }
        }

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(videoEncoderFactory)
            .setVideoDecoderFactory(videoDecoderFactory)
            .createPeerConnectionFactory()
    }

    /**
     * Start connection: Connect to signaling server with roomId and clientId.
     */
    fun start(serverUrl: String, roomId: String, clientId: String, clientName: String = "") {
        currentServerUrl = serverUrl.trim()
        currentRoomId = roomId.trim().uppercase()
        currentClientId = clientId
        currentClientName = clientName
        _connectionState.value = P2PConnectionState.CONNECTING_SIGNALING
        signalingClient.connect(serverUrl, roomId, clientId, clientName)
    }

    private fun setupPeerConnection() {
        if (peerConnection != null) {
            cleanupPeerConnection()
        }

        val iceServers = listOf(
            // Primary Google STUN
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
            // Cloudflare STUN
            PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer(),
            // Metered.ca OpenRelay Public STUN & TURN Servers (UDP/TCP/TLS 443)
            PeerConnection.IceServer.builder("stun:openrelay.metered.ca:80").createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer(),
            PeerConnection.IceServer.builder("turns:openrelay.metered.ca:443?transport=tcp")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            object : PeerConnectionObserverAdapter() {
                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate?.let {
                        Log.d(TAG, "Local ICE Candidate generated: ${it.sdpMid}")
                        if (isP2pConnected()) {
                            val packet = DataChannelPacket(
                                messageId = java.util.UUID.randomUUID().toString(),
                                type = "ice_candidate",
                                text = gson.toJson(it),
                                roomId = currentRoomId
                            )
                            sendPacket(packet)
                        }
                        signalingClient.sendIceCandidate(it)
                    }
                }

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    Log.d(TAG, "ICE Connection State changed: $state")
                    when (state) {
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            Log.w(TAG, "ICE disconnected. Verifying link in 5s...")
                            scope.launch {
                                delay(5000L)
                                if (peerConnection?.iceConnectionState() == PeerConnection.IceConnectionState.DISCONNECTED) {
                                    restartP2pConnection()
                                }
                            }
                        }
                        PeerConnection.IceConnectionState.CLOSED -> {
                            stopHeartbeat()
                            _connectionState.value = P2PConnectionState.WAITING_FOR_PEER
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            Log.w(TAG, "ICE Connection FAILED. Restarting P2P link...")
                            stopHeartbeat()
                            _connectionState.value = P2PConnectionState.FAILED
                            scope.launch {
                                delay(1500L)
                                restartP2pConnection()
                            }
                        }
                        else -> {}
                    }
                }

                override fun onDataChannel(dataChannel: DataChannel?) {
                    Log.d(TAG, "Remote DataChannel received: ${dataChannel?.label()}")
                    dataChannel?.let { setupDataChannel(it) }
                }

                override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                    super.onAddTrack(receiver, mediaStreams)
                    val track = receiver?.track()
                    if (track is AudioTrack) {
                        remoteAudioTrack = track
                        track.setEnabled(true)
                        track.setVolume(10.0)
                        Log.d(TAG, "Incoming WebRTC audio track enabled with volume 10.0")
                    } else if (track is VideoTrack) {
                        remoteVideoTrack = track
                        track.setEnabled(true)
                        _remoteVideoTrackFlow.value = track
                        _isVideoCallActive.value = true
                        Log.d(TAG, "Incoming WebRTC video track enabled")
                    }
                }
            }
        )
    }

    private fun setupDataChannel(channel: DataChannel) {
        dataChannel = channel
        channel.registerObserver(object : DataChannelObserverAdapter() {
            override fun onStateChange() {
                Log.d(TAG, "DataChannel state: ${channel.state()}")
                when (channel.state()) {
                    DataChannel.State.OPEN -> {
                        _connectionState.value = P2PConnectionState.CONNECTED_P2P
                        startHeartbeat()
                        if (currentClientName.isNotBlank()) {
                            sendPacket(
                                DataChannelPacket(
                                    type = "name_sync",
                                    roomId = currentRoomId,
                                    senderName = currentClientName
                                )
                            )
                        }
                    }
                    DataChannel.State.CLOSING,
                    DataChannel.State.CLOSED -> {
                        stopHeartbeat()
                        _connectionState.value = P2PConnectionState.WAITING_FOR_PEER
                    }
                    else -> {}
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer?) {
                buffer ?: return
                if (buffer.binary) {
                    fileTransferManager.handleBinaryChunk(buffer)
                } else {
                    val bytes = ByteArray(buffer.data.remaining())
                    buffer.data.get(bytes)
                    val text = String(bytes, StandardCharsets.UTF_8)
                    handleDataChannelText(text)
                }
            }
        })
    }

    private fun handleDataChannelText(text: String) {
        try {
            val packet = gson.fromJson(text, DataChannelPacket::class.java) ?: return

            // P1: Handle NAT keep-alive heartbeats internally (never clutter chat UI or collide with user pings)
            if (packet.type == "heartbeat_ping") {
                sendPacket(DataChannelPacket(type = "heartbeat_pong", timestamp = System.currentTimeMillis()))
                return
            }
            if (packet.type == "heartbeat_pong" || packet.type == "pong") {
                lastPongTimestamp = System.currentTimeMillis()
                return
            }

            Log.d(TAG, "Received DataChannel packet: ${packet.type}")

            if (packet.type == "file_header") {
                fileTransferManager.handleFileHeader(packet)
            }

            packet.senderName?.let { name ->
                if (name.isNotBlank()) {
                    scope.launch {
                        _peerNameFlow.emit(name)
                    }
                }
            }

            scope.launch {
                _incomingPackets.emit(packet)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing DataChannel text packet: ${e.localizedMessage}", e)
        }
    }

    /**
     * P1: NAT Keep-Alive Heartbeat timer.
     * Pings peer every 18 seconds to keep carrier NAT translation tables open.
     * Detects zombie connections and recovers after 45s of silence.
     */
    private fun startHeartbeat() {
        stopHeartbeat()
        lastPongTimestamp = System.currentTimeMillis()
        heartbeatJob = scope.launch {
            while (isActive && isP2pConnected()) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (!isP2pConnected()) break

                val now = System.currentTimeMillis()
                if (lastPongTimestamp > 0 && now - lastPongTimestamp > HEARTBEAT_TIMEOUT_MS) {
                    Log.w(TAG, "Keep-alive heartbeat timeout (>45s). Recovering connection...")
                    restartP2pConnection()
                    break
                }

                sendPacket(DataChannelPacket(type = "heartbeat_ping", timestamp = now))
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    fun restartP2pConnection() {
        Log.d(TAG, "Restarting P2P connection to recover link...")
        stopHeartbeat()
        cleanupPeerConnection()
        _connectionState.value = P2PConnectionState.CONNECTING_SIGNALING
        val server = currentServerUrl
        val room = currentRoomId ?: return
        val id = currentClientId ?: return
        val name = currentClientName
        if (server.isNotBlank()) {
            start(server, room, id, name)
        }
    }

    /**
     * Send structured control packet (text, typing, ack, love ping, file header)
     */
    fun sendPacket(packet: DataChannelPacket): Boolean {
        val dc = dataChannel
        if (dc == null || dc.state() != DataChannel.State.OPEN) {
            Log.w(TAG, "DataChannel not open. Cannot send packet: ${packet.type}")
            return false
        }
        return try {
            val json = gson.toJson(packet)
            val bytes = json.toByteArray(StandardCharsets.UTF_8)
            val buffer = DataChannel.Buffer(ByteBuffer.wrap(bytes), false)
            dc.send(buffer)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send packet over DataChannel: ${e.localizedMessage}", e)
            false
        }
    }

    fun isP2pConnected(): Boolean {
        return dataChannel?.state() == DataChannel.State.OPEN
    }

    fun sendOfflinePacket(packet: DataChannelPacket, onComplete: ((Boolean) -> Unit)? = null) {
        signalingClient.sendOfflineMessage(packet, onComplete)
    }

    fun sendOfflinePacketForRoom(roomId: String, packet: DataChannelPacket, onComplete: ((Boolean) -> Unit)? = null) {
        signalingClient.sendOfflineMessageForRoom(roomId, packet, onComplete)
    }

    fun sendOfflineAck(messageId: String, status: String) {
        signalingClient.sendOfflineAck(messageId, status)
    }

    fun sendOfflineAckForRoom(roomId: String, messageId: String, status: String) {
        signalingClient.sendOfflineAckForRoom(roomId, messageId, status)
    }

    /**
     * Send file attachment over DataChannel
     */
    fun sendFileAttachment(
        messageId: String,
        uri: Uri,
        fileName: String,
        fileSize: Long,
        mimeType: String?,
        senderName: String? = null,
        disappearingDuration: Long? = null
    ) {
        val dc = dataChannel
        if (dc != null && dc.state() == DataChannel.State.OPEN) {
            fileTransferManager.sendFile(dc, messageId, uri, fileName, fileSize, mimeType, senderName, disappearingDuration)
        } else {
            Log.w(TAG, "DataChannel is not open, cannot send file")
        }
    }

    // SignalingClient.Listener Callbacks
    override fun onSignalingConnected() {
        _connectionState.value = P2PConnectionState.WAITING_FOR_PEER
    }

    override fun onSignalingDisconnected() {
        _connectionState.value = P2PConnectionState.DISCONNECTED
    }

    override fun onPeerJoined(clientId: String) {
        Log.d(TAG, "onPeerJoined: $clientId (myClientId: $currentClientId)")

        // Ignore duplicate events if we're already negotiating or actively connected on DataChannel with this peer
        if (connectedPeerId == clientId && 
            (_connectionState.value == P2PConnectionState.CONNECTED_P2P && dataChannel?.state() == DataChannel.State.OPEN)) {
            Log.d(TAG, "Already active with peer $clientId. Skipping duplicate join.")
            return
        }
        connectedPeerId = clientId

        val myId = currentClientId ?: ""
        // Deterministic role selection: the peer with higher string value initiates the offer
        val shouldInitiate = myId > clientId
        Log.d(TAG, "Role decision: shouldInitiate=$shouldInitiate (myId=$myId, peerId=$clientId)")

        if (shouldInitiate) {
            isInitiator = true
            _connectionState.value = P2PConnectionState.NEGOTIATING_WEBRTC

            setupPeerConnection()

            // Create DataChannel on initiator side
            val dcInit = DataChannel.Init().apply {
                ordered = true
            }
            val channel = peerConnection?.createDataChannel(DATA_CHANNEL_LABEL, dcInit)
            channel?.let { setupDataChannel(it) }

            // Create WebRTC Offer
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            }

            peerConnection?.createOffer(object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    desc?.let { sdp ->
                        peerConnection?.setLocalDescription(object : SdpObserver {
                            override fun onCreateSuccess(d: SessionDescription?) {}
                            override fun onSetSuccess() {
                                Log.d(TAG, "Local description set successfully. Sending offer.")
                                signalingClient.sendOffer(sdp.description)
                            }
                            override fun onCreateFailure(err: String?) {}
                            override fun onSetFailure(err: String?) {
                                Log.e(TAG, "Failed to set local description: $err")
                            }
                        }, sdp)
                    }
                }

                override fun onSetSuccess() {}
                override fun onCreateFailure(error: String?) {
                    Log.e(TAG, "Failed to create offer: $error")
                }
                override fun onSetFailure(error: String?) {}
            }, constraints)
        } else {
            isInitiator = false
            _connectionState.value = P2PConnectionState.NEGOTIATING_WEBRTC
            setupPeerConnection()
            Log.d(TAG, "Acting as callee. Waiting for offer from initiator peer $clientId...")
        }
    }

    override fun onPeerLeft(clientId: String) {
        Log.d(TAG, "Peer left: $clientId")
        if (connectedPeerId == clientId) {
            connectedPeerId = null
            _connectionState.value = P2PConnectionState.WAITING_FOR_PEER
            cleanupPeerConnection()
        }
    }

    override fun onOfferReceived(sdp: String) {
        Log.d(TAG, "Offer received. Handling as callee...")
        isInitiator = false

        if (peerConnection == null || peerConnection?.signalingState() == PeerConnection.SignalingState.CLOSED) {
            setupPeerConnection()
        }

        val pc = peerConnection ?: return
        if (pc.signalingState() != PeerConnection.SignalingState.STABLE) {
            Log.w(TAG, "Ignoring offer because signalingState is not stable: ${pc.signalingState()}")
            return
        }

        val isVideoOffer = _isVideoCallActive.value || (sdp.contains("m=video") && !sdp.contains("m=video 0"))
        if (isVideoOffer) {
            _isVideoCallActive.value = true
            ensureLocalAudioTrack()
            ensureLocalVideoTrack()
        } else if (sdp.contains("m=audio") && !sdp.contains("m=audio 0")) {
            ensureLocalAudioTrack()
        }

        val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, sdp)
        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "Remote description set for Offer. Drain pending ICE candidates.")
                drainPendingIceCandidates()

                // Configure transceivers explicitly for callee answer
                for (transceiver in pc.transceivers) {
                    if (transceiver.mediaType == org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO) {
                        transceiver.direction = if (localVideoTrack != null) {
                            org.webrtc.RtpTransceiver.RtpTransceiverDirection.SEND_RECV
                        } else {
                            org.webrtc.RtpTransceiver.RtpTransceiverDirection.RECV_ONLY
                        }
                        Log.d(TAG, "Callee set video transceiver direction to ${transceiver.direction}")
                    } else if (transceiver.mediaType == org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO) {
                        transceiver.direction = org.webrtc.RtpTransceiver.RtpTransceiverDirection.SEND_RECV
                    }
                }

                // Create Answer
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (isVideoOffer) "true" else "false"))
                }
                pc.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(answerDesc: SessionDescription?) {
                        answerDesc?.let { answer ->
                            pc.setLocalDescription(object : SdpObserver {
                                override fun onCreateSuccess(d: SessionDescription?) {}
                                override fun onSetSuccess() {
                                    Log.d(TAG, "Local description set for Answer. Sending answer.")
                                    val packet = DataChannelPacket(
                                        messageId = java.util.UUID.randomUUID().toString(),
                                        type = "sdp_answer",
                                        text = answer.description,
                                        roomId = currentRoomId
                                    )
                                    if (isP2pConnected()) {
                                        sendPacket(packet)
                                    } else {
                                        signalingClient.sendAnswer(answer.description)
                                    }
                                    _connectionState.value = P2PConnectionState.CONNECTED_P2P
                                }
                                override fun onCreateFailure(err: String?) {}
                                override fun onSetFailure(err: String?) {}
                            }, answer)
                        }
                    }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(err: String?) {
                        Log.e(TAG, "Failed creating answer: $err")
                    }
                    override fun onSetFailure(err: String?) {}
                }, constraints)
            }
            override fun onCreateFailure(err: String?) {}
            override fun onSetFailure(err: String?) {
                Log.e(TAG, "Failed to set remote description on Offer: $err")
            }
        }, remoteDesc)
    }

    override fun onAnswerReceived(sdp: String) {
        Log.d(TAG, "Answer received on initiator.")
        val pc = peerConnection
        if (pc == null || pc.signalingState() == PeerConnection.SignalingState.CLOSED) {
            Log.w(TAG, "PeerConnection not available to set remote answer")
            return
        }
        if (pc.signalingState() != PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
            Log.w(TAG, "Ignoring answer because signalingState is not HAVE_LOCAL_OFFER: ${pc.signalingState()}")
            return
        }
        val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "Remote description set for Answer. Draining pending ICE candidates.")
                drainPendingIceCandidates()
                _connectionState.value = P2PConnectionState.CONNECTED_P2P
            }
            override fun onCreateFailure(err: String?) {}
            override fun onSetFailure(err: String?) {
                Log.e(TAG, "Failed to set remote description on Answer: $err")
            }
        }, remoteDesc)
    }

    override fun onIceCandidateReceived(candidate: IceCandidate) {
        val pc = peerConnection
        if (pc != null && pc.remoteDescription != null) {
            pc.addIceCandidate(candidate)
        } else {
            pendingIceCandidates.add(candidate)
        }
    }

    override fun onOfflinePacketReceived(packet: DataChannelPacket) {
        Log.d(TAG, "Offline packet received from cloud relay: ${packet.type}")
        if (packet.type == "file_header") {
            fileTransferManager.handleFileHeader(packet)
        }
        scope.launch {
            _incomingPackets.emit(packet)
        }
    }

    override fun onOfflineAckReceived(messageId: String, status: String) {
        Log.d(TAG, "Offline ack received from cloud relay: $messageId -> $status")
        scope.launch {
            _incomingPackets.emit(
                DataChannelPacket(
                    type = "ack",
                    messageId = messageId,
                    status = status
                )
            )
        }
    }

    override fun onPeerNameReceived(name: String) {
        Log.d(TAG, "Peer name received: $name")
        scope.launch {
            _peerNameFlow.emit(name)
        }
    }

    private fun drainPendingIceCandidates() {
        val pc = peerConnection ?: return
        for (candidate in pendingIceCandidates) {
            pc.addIceCandidate(candidate)
        }
        pendingIceCandidates.clear()
    }

    override fun onError(message: String) {
        Log.e(TAG, "Signaling error: $message")
    }

    // FileTransferManager.Listener Callbacks
    override fun onFileTransferProgress(messageId: String, progress: Float) {
        scope.launch {
            _fileProgressFlow.emit(Pair(messageId, progress))
        }
    }

    override fun onFileTransferComplete(messageId: String, fileId: String, localPath: String, isIncoming: Boolean, disappearingDuration: Long?) {
        scope.launch {
            _fileCompleteFlow.emit(FileTransferCompleteEvent(messageId, localPath, isIncoming, disappearingDuration))
        }
    }

    override fun onFileTransferFailed(messageId: String, fileId: String, error: String) {
        Log.e(TAG, "File transfer failed for message $messageId (file $fileId): $error")
        scope.launch {
            _fileFailedFlow.emit(Pair(messageId, error))
        }
    }

    override fun sendControlPacket(packet: DataChannelPacket) {
        sendPacket(packet)
    }

    fun ensureLocalAudioTrack(): Boolean {
        if (localAudioTrack != null) return true
        return try {
            val factory = peerConnectionFactory ?: return false
            val audioConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            }
            audioSource = factory.createAudioSource(audioConstraints)
            val track = factory.createAudioTrack("ARDAMSa0", audioSource)
            track.setEnabled(true)
            localAudioTrack = track

            val sender = localAudioSender
            if (sender != null) {
                sender.setTrack(track, false)
            } else {
                localAudioSender = peerConnection?.addTrack(track, listOf("ARDAMS"))
            }
            Log.d(TAG, "Local audio track created and attached to PeerConnection")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed creating local audio track: ${e.localizedMessage}", e)
            false
        }
    }

    private fun setupAudioRouting(speakerOn: Boolean) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager = am ?: return

            am.mode = AudioManager.MODE_IN_COMMUNICATION

            // Request AudioFocus for voice communication
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener { /* maintain focus */ }
                    .build()
                audioFocusRequest = focusRequest
                am.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN)
            }

            // Ensure voice call volume is at a clear audible level
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            val curVol = am.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
            if (curVol < maxVol / 2) {
                am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, (maxVol * 0.85).toInt(), 0)
            }

            toggleSpeaker(speakerOn)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up audio routing: ${e.localizedMessage}")
        }
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        return try {
            val enumerator = if (Camera2Enumerator.isSupported(context)) {
                Camera2Enumerator(context)
            } else {
                Camera1Enumerator(true)
            }
            val deviceNames = enumerator.deviceNames
            if (deviceNames.isNullOrEmpty()) {
                Log.w(TAG, "No camera devices found by enumerator")
                return null
            }
            // First try front-facing camera
            for (deviceName in deviceNames) {
                if (enumerator.isFrontFacing(deviceName)) {
                    val capturer = enumerator.createCapturer(deviceName, null)
                    if (capturer != null) {
                        _isFrontCamera.value = true
                        return capturer
                    }
                }
            }
            // Fallback to back-facing camera
            for (deviceName in deviceNames) {
                if (!enumerator.isFrontFacing(deviceName)) {
                    val capturer = enumerator.createCapturer(deviceName, null)
                    if (capturer != null) {
                        _isFrontCamera.value = false
                        return capturer
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error creating camera capturer: ${e.localizedMessage}")
            null
        }
    }

    fun ensureLocalVideoTrack(): Boolean {
        if (localVideoTrack != null) return true
        return try {
            val factory = peerConnectionFactory ?: return false
            val capturer = createCameraCapturer()
            if (capturer == null) {
                Log.w(TAG, "No camera found on this device. Ensuring receive-only video transceiver.")
                ensureVideoReceiverTransceiver()
                return false
            }
            videoCapturer = capturer

            val surfaceHelper = SurfaceTextureHelper.create("AshuChatCaptureThread", rootEglBase.eglBaseContext)
            surfaceTextureHelper = surfaceHelper

            val source = factory.createVideoSource(capturer.isScreencast)
            videoSource = source

            capturer.initialize(surfaceHelper, context, source.capturerObserver)
            capturer.startCapture(1280, 720, 30)

            val track = factory.createVideoTrack("ARDAMSv0", source)
            track.setEnabled(_isLocalVideoEnabled.value)
            localVideoTrack = track
            _localVideoTrackFlow.value = track

            val sender = localVideoSender
            if (sender != null) {
                sender.setTrack(track, false)
            } else {
                localVideoSender = peerConnection?.addTrack(track, listOf("ARDAMS"))
            }
            Log.d(TAG, "Local video track created and attached to PeerConnection")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed creating local video track: ${e.localizedMessage}", e)
            ensureVideoReceiverTransceiver()
            false
        }
    }

    private fun ensureVideoReceiverTransceiver() {
        try {
            val pc = peerConnection ?: return
            val videoTransceivers = pc.transceivers.filter {
                it.mediaType == org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO
            }
            if (videoTransceivers.isEmpty()) {
                val init = org.webrtc.RtpTransceiver.RtpTransceiverInit(
                    org.webrtc.RtpTransceiver.RtpTransceiverDirection.RECV_ONLY
                )
                pc.addTransceiver(org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, init)
                Log.d(TAG, "Added RECV_ONLY video transceiver for camera-less device")
            } else {
                videoTransceivers.forEach { tr ->
                    if (tr.direction == org.webrtc.RtpTransceiver.RtpTransceiverDirection.INACTIVE ||
                        tr.direction == org.webrtc.RtpTransceiver.RtpTransceiverDirection.SEND_ONLY) {
                        tr.direction = org.webrtc.RtpTransceiver.RtpTransceiverDirection.RECV_ONLY
                        Log.d(TAG, "Updated existing video transceiver direction to RECV_ONLY")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error ensuring video receiver transceiver: ${e.localizedMessage}")
        }
    }

    fun prepareLocalAudioCall(): Boolean {
        return try {
            _isVideoCallActive.value = false
            ensureLocalAudioTrack()
            setupAudioRouting(speakerOn = true)
            Log.d(TAG, "Prepared local audio call tracks")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing local audio call: ${e.localizedMessage}", e)
            false
        }
    }

    fun prepareLocalVideoCall(): Boolean {
        return try {
            _isVideoCallActive.value = true
            _isLocalVideoEnabled.value = true
            ensureLocalAudioTrack()
            ensureLocalVideoTrack()
            setupAudioRouting(speakerOn = true)
            Log.d(TAG, "Prepared local video call tracks")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing local video call: ${e.localizedMessage}", e)
            false
        }
    }

    fun startAudioCall(): Boolean = prepareLocalAudioCall()
    fun startVideoCall(): Boolean = prepareLocalVideoCall()
    fun acceptAudioCall(): Boolean = prepareLocalAudioCall()
    fun acceptVideoCall(): Boolean = prepareLocalVideoCall()

    fun sendRenegotiationOffer(isVideo: Boolean = false) {
        val pc = peerConnection
        if (pc == null || pc.signalingState() == PeerConnection.SignalingState.CLOSED) {
            Log.w(TAG, "Cannot send renegotiation offer: PeerConnection is null or closed")
            return
        }
        if (pc.signalingState() != PeerConnection.SignalingState.STABLE) {
            Log.w(TAG, "Cannot send renegotiation offer: Signaling state is not stable (${pc.signalingState()})")
            return
        }
        val isVideoOffer = _isVideoCallActive.value || isVideo
        val hasVideoTransceiver = pc.transceivers.any {
            it.mediaType == org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO
        }
        if (!hasVideoTransceiver && isVideoOffer) {
            ensureVideoReceiverTransceiver()
        }
        for (transceiver in pc.transceivers) {
            if (transceiver.mediaType == org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO) {
                transceiver.direction = if (localVideoTrack != null) {
                    org.webrtc.RtpTransceiver.RtpTransceiverDirection.SEND_RECV
                } else {
                    org.webrtc.RtpTransceiver.RtpTransceiverDirection.RECV_ONLY
                }
                Log.d(TAG, "Caller set video transceiver direction to ${transceiver.direction}")
            } else if (transceiver.mediaType == org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO) {
                transceiver.direction = org.webrtc.RtpTransceiver.RtpTransceiverDirection.SEND_RECV
            }
        }
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (isVideoOffer) "true" else "false"))
        }
        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let { sdp ->
                    pc.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(d: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.d(TAG, "Local description set successfully for renegotiation. Sending offer.")
                            val packet = DataChannelPacket(
                                messageId = java.util.UUID.randomUUID().toString(),
                                type = "sdp_offer",
                                text = sdp.description,
                                roomId = currentRoomId
                            )
                            if (isP2pConnected()) {
                                sendPacket(packet)
                            } else {
                                signalingClient.sendOffer(sdp.description)
                            }
                        }
                        override fun onCreateFailure(err: String?) {}
                        override fun onSetFailure(err: String?) {
                            Log.e(TAG, "Failed to set local description: $err")
                        }
                    }, sdp)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Failed to create renegotiation offer: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    fun switchCamera() {
        val capturer = videoCapturer ?: return
        capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFront: Boolean) {
                _isFrontCamera.value = isFront
                Log.d(TAG, "Camera switched. Front-facing: $isFront")
            }
            override fun onCameraSwitchError(error: String?) {
                Log.e(TAG, "Error switching camera: $error")
            }
        })
    }

    fun toggleVideo(enabled: Boolean) {
        _isLocalVideoEnabled.value = enabled
        localVideoTrack?.setEnabled(enabled)
    }

    fun endCall() {
        endAudioCall()
        endVideo()
    }

    fun endAudioCall() {
        try {
            localAudioSender?.setTrack(null, false)
            localAudioTrack?.setEnabled(false)
            localAudioTrack?.dispose()
            localAudioTrack = null
            audioSource?.dispose()
            audioSource = null

            remoteAudioTrack?.setEnabled(false)
            remoteAudioTrack = null

            val am = audioManager
            if (am != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    am.clearCommunicationDevice()
                }
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = false
                am.mode = AudioManager.MODE_NORMAL

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                    audioFocusRequest = null
                } else {
                    @Suppress("DEPRECATION")
                    am.abandonAudioFocus(null)
                }
            }
            Log.d(TAG, "WebRTC audio call ended and audio hardware reset")
        } catch (e: Exception) {
            Log.w(TAG, "Error ending audio call: ${e.localizedMessage}")
        }
    }

    fun endVideo() {
        try {
            _isVideoCallActive.value = false
            try {
                videoCapturer?.stopCapture()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping video capturer: ${e.localizedMessage}")
            }
            videoCapturer?.dispose()
            videoCapturer = null

            localVideoSender?.setTrack(null, false)
            localVideoTrack?.setEnabled(false)
            localVideoTrack?.dispose()
            localVideoTrack = null
            _localVideoTrackFlow.value = null

            videoSource?.dispose()
            videoSource = null

            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null

            remoteVideoTrack?.setEnabled(false)
            remoteVideoTrack = null
            _remoteVideoTrackFlow.value = null
            Log.d(TAG, "WebRTC video call ended and video hardware released")
        } catch (e: Exception) {
            Log.w(TAG, "Error ending video call: ${e.localizedMessage}")
        }
    }

    fun toggleMute(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    fun toggleSpeaker(speakerOn: Boolean) {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (speakerOn) {
                    val speakerDevice = am.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speakerDevice != null) {
                        am.setCommunicationDevice(speakerDevice)
                    }
                } else {
                    val earpieceDevice = am.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    }
                    if (earpieceDevice != null) {
                        am.setCommunicationDevice(earpieceDevice)
                    } else {
                        am.clearCommunicationDevice()
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = speakerOn
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling speaker: ${e.localizedMessage}")
        }
    }

    private fun cleanupPeerConnection() {
        stopHeartbeat()
        endCall()
        try {
            fileTransferManager.cancelAll()
            dataChannel?.close()
            dataChannel = null
            peerConnection?.close()
            peerConnection = null
            pendingIceCandidates.clear()
            localAudioSender = null
            localVideoSender = null
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up PeerConnection: ${e.localizedMessage}")
        }
    }

    fun stop() {
        cleanupPeerConnection()
        signalingClient.disconnect()
        _connectionState.value = P2PConnectionState.DISCONNECTED
    }
}
