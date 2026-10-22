package com.ashuchat.app.data.model

data class ChatMessage(
    val id: String,
    val roomId: String = "",
    val senderName: String,
    val isFromMe: Boolean,
    val text: String = "",
    val type: MessageType = MessageType.TEXT,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT,
    val attachmentPath: String? = null,
    val attachmentName: String? = null,
    val attachmentSize: Long = 0L,
    val mimeType: String? = null,
    val transferProgress: Float = 1.0f, // 0.0 to 1.0 for ongoing transfers
    val reaction: String? = null,
    val replyToId: String? = null,
    val replyToText: String? = null,
    val replyToSender: String? = null,
    val expiresAt: Long? = null
)
