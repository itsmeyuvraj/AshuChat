package com.ashuchat.app.data.repository

import com.ashuchat.app.data.local.MessageDao
import com.ashuchat.app.data.local.MessageEntity
import com.ashuchat.app.data.model.ChatMessage
import com.ashuchat.app.data.model.MessageStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepository(private val messageDao: MessageDao) {

    fun getAllMessages(): Flow<List<ChatMessage>> {
        return messageDao.getAllMessagesFlow().map { entities ->
            entities.map { it.toChatMessage() }
        }
    }

    suspend fun getMessage(id: String): ChatMessage? {
        return messageDao.getMessageById(id)?.toChatMessage()
    }

    suspend fun insertMessage(message: ChatMessage) {
        messageDao.insertMessage(MessageEntity.fromChatMessage(message))
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

    suspend fun clearAllMessages() {
        messageDao.deleteAllMessages()
    }
}
