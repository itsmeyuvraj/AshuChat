package com.ashuchat.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * Control packets sent over WebRTC DataChannel (as JSON strings).
 */
data class DataChannelPacket(
    @SerializedName("type")
    val type: String, // "text", "file_header", "file_ack", "ack", "typing", "love_ping"

    @SerializedName("messageId")
    val messageId: String? = null,

    @SerializedName("senderName")
    val senderName: String? = null,

    @SerializedName("text")
    val text: String? = null,

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    // File transfer metadata
    @SerializedName("fileId")
    val fileId: String? = null,

    @SerializedName("fileName")
    val fileName: String? = null,

    @SerializedName("fileSize")
    val fileSize: Long = 0L,

    @SerializedName("mimeType")
    val mimeType: String? = null,

    @SerializedName("totalChunks")
    val totalChunks: Int = 0,

    @SerializedName("chunkSize")
    val chunkSize: Int = 0,

    // Status or Typing
    @SerializedName("status")
    val status: String? = null,

    @SerializedName("isTyping")
    val isTyping: Boolean? = null,

    // Base64 payload for direct image and file transfer (mailbox & instant P2P)
    @SerializedName("dataBase64")
    val dataBase64: String? = null
)
