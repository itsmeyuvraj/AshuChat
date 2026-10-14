package com.ashuchat.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ashuchat.app.data.model.ChatMessage
import com.ashuchat.app.data.model.MessageStatus
import com.ashuchat.app.data.model.MessageType

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val senderName: String,
    val isFromMe: Boolean,
    val text: String,
    val type: String, // "TEXT", "IMAGE", "FILE", "AUDIO", "LOVE_PING"
    val timestamp: Long,
    val status: String, // "SENDING", "SENT", "DELIVERED", "READ", "FAILED"
    val attachmentPath: String?,
    val attachmentName: String?,
    val attachmentSize: Long,
    val mimeType: String?
) {
    fun toChatMessage(progress: Float = 1.0f): ChatMessage {
        val parsedType = try {
            MessageType.valueOf(type)
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
            transferProgress = progress
        )
    }

    companion object {
        fun fromChatMessage(msg: ChatMessage): MessageEntity {
            return MessageEntity(
                id = msg.id,
                senderName = msg.senderName,
                isFromMe = msg.isFromMe,
                text = msg.text,
                type = msg.type.name,
                timestamp = msg.timestamp,
                status = msg.status.name,
                attachmentPath = msg.attachmentPath,
                attachmentName = msg.attachmentName,
                attachmentSize = msg.attachmentSize,
                mimeType = msg.mimeType
            )
        }
    }
}
