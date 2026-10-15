package com.ashuchat.app.data.repository

import com.ashuchat.app.data.local.MessageDao
import com.ashuchat.app.data.local.MessageEntity
import com.ashuchat.app.data.local.RoomDao
import com.ashuchat.app.data.local.RoomEntity
import com.ashuchat.app.data.model.ChatMessage
import com.ashuchat.app.data.model.MessageStatus
import com.ashuchat.app.data.model.MessageType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepository(
    private val messageDao: MessageDao,
    private val roomDao: RoomDao
) {

    // Room operations
    fun getAllRooms(): Flow<List<RoomEntity>> {
        return roomDao.getAllRoomsFlow()
    }

    suspend fun getRoom(roomId: String): RoomEntity? {
        return roomDao.getRoomById(roomId)
    }

    suspend fun insertOrUpdateRoom(room: RoomEntity) {
        roomDao.insertOrUpdateRoom(room)
    }

    suspend fun updateRoomLastMessage(roomId: String, snippet: String, timestamp: Long) {
        roomDao.updateLastMessage(roomId, snippet, timestamp)
    }

    suspend fun updateRoomPartnerName(roomId: String, partnerName: String) {
        roomDao.updatePartnerName(roomId, partnerName)
    }

    suspend fun updateRoomUnreadCount(roomId: String, count: Int) {
        roomDao.updateUnreadCount(roomId, count)
    }

    suspend fun deleteRoom(roomId: String) {
        messageDao.deleteMessagesForRoom(roomId)
        roomDao.deleteRoom(roomId)
    }

    // Message operations
    fun getMessagesForRoom(roomId: String): Flow<List<ChatMessage>> {
        return messageDao.getMessagesForRoomFlow(roomId).map { entities ->
            entities.map { it.toChatMessage() }
        }
    }

    fun getAllMessages(): Flow<List<ChatMessage>> {
        return messageDao.getAllMessagesFlow().map { entities ->
            entities.map { it.toChatMessage() }
        }
    }

    suspend fun getMessage(id: String): ChatMessage? {
        return messageDao.getMessageById(id)?.toChatMessage()
    }

    suspend fun getPendingOutgoingMessagesForRoom(roomId: String): List<ChatMessage> {
        return messageDao.getPendingOutgoingMessagesForRoom(roomId).map { it.toChatMessage() }
    }

    suspend fun getPendingOutgoingMessages(): List<ChatMessage> {
        return messageDao.getPendingOutgoingMessages().map { it.toChatMessage() }
    }

    suspend fun insertMessage(message: ChatMessage) {
        messageDao.insertMessage(MessageEntity.fromChatMessage(message))

        // Auto-update room last message snippet & timestamp
        val snippet = when (message.type) {
            MessageType.TEXT -> message.text
            MessageType.IMAGE -> "📷 Photo"
            MessageType.FILE -> "📄 ${message.attachmentName ?: "File"}"
            MessageType.AUDIO -> "🎵 Audio"
            MessageType.LOVE_PING -> "❤️ Love Ping"
        }
        val existingRoom = roomDao.getRoomById(message.roomId)
        if (existingRoom != null) {
            roomDao.updateLastMessage(message.roomId, snippet, message.timestamp)
        } else if (message.roomId.isNotBlank()) {
            roomDao.insertOrUpdateRoom(
                RoomEntity(
                    roomId = message.roomId,
                    partnerName = if (!message.isFromMe) message.senderName else "Peer",
                    lastMessage = snippet,
                    lastTimestamp = message.timestamp
                )
            )
        }
    }

    suspend fun updateMessageStatus(id: String, status: MessageStatus) {
        messageDao.updateMessageStatus(id, status.name)
    }

    suspend fun updateAttachmentPath(id: String, path: String) {
        messageDao.updateAttachmentPath(id, path)
    }

    suspend fun deleteMessage(id: String) {
        messageDao.deleteMessage(id)
    }

    suspend fun clearRoomMessages(roomId: String) {
        messageDao.deleteMessagesForRoom(roomId)
        roomDao.updateLastMessage(roomId, "Chat cleared", System.currentTimeMillis())
    }

    suspend fun clearAllMessages() {
        messageDao.deleteAllMessages()
        roomDao.deleteAllRooms()
    }
}
