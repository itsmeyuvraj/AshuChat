package com.ashuchat.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * Messages exchanged via the signaling WebSocket server.
 */
data class SignalingPayload(
    @SerializedName("type")
    val type: String, // "join", "peer_joined", "offer", "answer", "ice_candidate", "peer_left", "error"

    @SerializedName("roomId")
    val roomId: String? = null,

    @SerializedName("clientId")
    val clientId: String? = null,

    @SerializedName("sdp")
    val sdp: String? = null,

    @SerializedName("sdpMid")
    val sdpMid: String? = null,

    @SerializedName("sdpMLineIndex")
    val sdpMLineIndex: Int? = null,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("clientName")
    val clientName: String? = null
)
