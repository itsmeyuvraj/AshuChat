package com.ashuchat.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ashuchat.app.data.model.ChatMessage
import com.ashuchat.app.data.model.MessageStatus
import com.ashuchat.app.data.model.MessageType

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["roomId"]),
        Index(value = ["roomId", "timestamp"]),
        Index(value = ["roomId", "isFromMe", "status"]),
        Index(value = ["expiresAt"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val roomId: String = "",
    val senderName: String,
    val isFromMe: Boolean,
    val text: String,
    val type: String, // "TEXT", "IMAGE", "FILE", "AUDIO", "LOVE_PING"
    val timestamp: Long,
    val status: String, // "SENDING", "SENT", "DELIVERED", "READ", "FAILED"
    val attachmentPath: String?,
    val attachmentName: String?,
    val attachmentSize: Long,
    val mimeType: String?,
    val reaction: String? = null,
    val replyToId: String? = null,
    val replyToText: String? = null,
    val replyToSender: String? = null,
    val expiresAt: Long? = null,
    val disappearingDuration: Long? = null
) {
    fun toChatMessage(progress: Float = 1.0f): ChatMessage {
        val parsedType = try {
            val t = MessageType.valueOf(type)
            if (t == MessageType.LOVE_PING) MessageType.PING else t
        } catch (_: Exception) {
            MessageType.TEXT
        }

        val parsedStatus = try {
            MessageStatus.valueOf(status)
        } catch (_: Exception) {
            MessageStatus.SENT
        }

        return ChatMessage(
            id = id,
            roomId = roomId,
            senderName = senderName,
            isFromMe = isFromMe,
            text = text,
            type = parsedType,
            timestamp = timestamp,
            status = parsedStatus,
            attachmentPath = attachmentPath,
            attachmentName = attachmentName,
            attachmentSize = attachmentSize,
            mimeType = mimeType,
            transferProgress = progress,
            reaction = reaction,
            replyToId = replyToId,
            replyToText = replyToText,
            replyToSender = replyToSender,
            expiresAt = expiresAt,
            disappearingDuration = disappearingDuration
        )
    }

    companion object {
        fun fromChatMessage(msg: ChatMessage): MessageEntity {
            return MessageEntity(
                id = msg.id,
                roomId = msg.roomId,
                senderName = msg.senderName,
                isFromMe = msg.isFromMe,
                text = msg.text,
                type = msg.type.name,
                timestamp = msg.timestamp,
                status = msg.status.name,
                attachmentPath = msg.attachmentPath,
                attachmentName = msg.attachmentName,
                attachmentSize = msg.attachmentSize,
                mimeType = msg.mimeType,
                reaction = msg.reaction,
                replyToId = msg.replyToId,
                replyToText = msg.replyToText,
                replyToSender = msg.replyToSender,
                expiresAt = msg.expiresAt,
                disappearingDuration = msg.disappearingDuration
            )
        }
    }
}
