package com.ashuchat.app.webrtc

import android.content.Context
import android.net.Uri
import android.util.Log
import com.ashuchat.app.data.model.DataChannelPacket
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
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

    private val _fileCompleteFlow = MutableSharedFlow<Triple<String, String, Boolean>>(extraBufferCapacity = 64)
    val fileCompleteFlow: SharedFlow<Triple<String, String, Boolean>> = _fileCompleteFlow.asSharedFlow()

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null

    private var isInitiator = false
    private val pendingIceCandidates = mutableListOf<IceCandidate>()

    init {
        initPeerConnectionFactory()
    }

    private fun initPeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

    /**
     * Start connection: Connect to signaling server with roomId and clientId.
     */
    fun start(serverUrl: String, roomId: String, clientId: String) {
        _connectionState.value = P2PConnectionState.CONNECTING_SIGNALING
        signalingClient.connect(serverUrl, roomId, clientId)
    }

    private fun setupPeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            object : PeerConnectionObserverAdapter() {
                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate?.let {
                        Log.d(TAG, "Local ICE Candidate generated: ${it.sdpMid}")
                        signalingClient.sendIceCandidate(it)
                    }
                }

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    Log.d(TAG, "ICE Connection State changed: $state")
                    when (state) {
                        PeerConnection.IceConnectionState.DISCONNECTED,
                        PeerConnection.IceConnectionState.CLOSED -> {
                            _connectionState.value = P2PConnectionState.WAITING_FOR_PEER
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            _connectionState.value = P2PConnectionState.FAILED
                        }
                        else -> {}
                    }
                }

                override fun onDataChannel(remoteChannel: DataChannel?) {
                    Log.d(TAG, "Remote DataChannel received: ${remoteChannel?.label()}")
                    remoteChannel?.let { setupDataChannel(it) }
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
                    }
                    DataChannel.State.CLOSING,
                    DataChannel.State.CLOSED -> {
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
            Log.d(TAG, "Received DataChannel packet: ${packet.type}")

            if (packet.type == "file_header") {
                fileTransferManager.handleFileHeader(packet)
            }

            scope.launch {
                _incomingPackets.emit(packet)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing DataChannel text packet: ${e.localizedMessage}", e)
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

    /**
     * Send file attachment over DataChannel
     */
    fun sendFileAttachment(
        messageId: String,
        uri: Uri,
        fileName: String,
        fileSize: Long,
        mimeType: String?
    ) {
        val dc = dataChannel
        if (dc != null && dc.state() == DataChannel.State.OPEN) {
            fileTransferManager.sendFile(dc, messageId, uri, fileName, fileSize, mimeType)
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
        Log.d(TAG, "Peer joined: $clientId. Initiating WebRTC offer as caller...")
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
    }

    override fun onPeerLeft(clientId: String) {
        Log.d(TAG, "Peer left: $clientId")
        _connectionState.value = P2PConnectionState.WAITING_FOR_PEER
        cleanupPeerConnection()
    }

    override fun onOfferReceived(sdp: String) {
        Log.d(TAG, "Offer received. Handling as callee...")
        isInitiator = false
        _connectionState.value = P2PConnectionState.NEGOTIATING_WEBRTC

        setupPeerConnection()

        val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, sdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "Remote description set for Offer. Drain pending ICE candidates.")
                drainPendingIceCandidates()

                // Create Answer
                val constraints = MediaConstraints()
                peerConnection?.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(answerDesc: SessionDescription?) {
                        answerDesc?.let { answer ->
                            peerConnection?.setLocalDescription(object : SdpObserver {
                                override fun onCreateSuccess(d: SessionDescription?) {}
                                override fun onSetSuccess() {
                                    Log.d(TAG, "Local description set for Answer. Sending answer.")
                                    signalingClient.sendAnswer(answer.description)
                                }
                                override fun onCreateFailure(err: String?) {}
                                override fun onSetFailure(err: String?) {}
                            }, answer)
                        }
                    }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(err: String?) {}
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
        val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "Remote description set for Answer. Draining pending ICE candidates.")
                drainPendingIceCandidates()
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
    override fun onFileTransferProgress(fileId: String, progress: Float) {
        scope.launch {
            _fileProgressFlow.emit(Pair(fileId, progress))
        }
    }

    override fun onFileTransferComplete(messageId: String, fileId: String, localPath: String, isIncoming: Boolean) {
        scope.launch {
            _fileCompleteFlow.emit(Triple(messageId, localPath, isIncoming))
        }
    }

    override fun onFileTransferFailed(fileId: String, error: String) {
        Log.e(TAG, "File transfer failed for $fileId: $error")
    }

    override fun sendControlPacket(packet: DataChannelPacket) {
        sendPacket(packet)
    }

    private fun cleanupPeerConnection() {
        try {
            dataChannel?.close()
            dataChannel = null
            peerConnection?.close()
            peerConnection = null
            pendingIceCandidates.clear()
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
