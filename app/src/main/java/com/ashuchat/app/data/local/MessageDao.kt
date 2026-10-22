package com.ashuchat.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE roomId = :roomId ORDER BY timestamp ASC")
    fun getMessagesForRoomFlow(roomId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun getAllMessagesFlow(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE roomId = :roomId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessageForRoom(roomId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateMessageStatus(id: String, status: String)

    @Query("UPDATE messages SET expiresAt = :expiresAt WHERE id = :id")
    suspend fun updateMessageExpiration(id: String, expiresAt: Long)

    @Query("SELECT * FROM messages WHERE roomId = :roomId AND isFromMe = 0 AND status != 'READ'")
    suspend fun getUnreadIncomingMessagesForRoom(roomId: String): List<MessageEntity>

    @Query("UPDATE messages SET status = 'READ', expiresAt = CASE WHEN :expiresAt IS NOT NULL AND expiresAt IS NULL THEN :expiresAt ELSE expiresAt END WHERE id = :id")
    suspend fun markMessageAsRead(id: String, expiresAt: Long?)

    @Query("UPDATE messages SET attachmentPath = :path WHERE id = :id")
    suspend fun updateAttachmentPath(id: String, path: String)

    @Query("UPDATE messages SET reaction = :reaction WHERE id = :id")
    suspend fun updateMessageReaction(id: String, reaction: String?)

    @Query("SELECT * FROM messages WHERE expiresAt IS NOT NULL AND expiresAt <= :currentTime")
    suspend fun getExpiredMessages(currentTime: Long): List<MessageEntity>

    @Query("DELETE FROM messages WHERE expiresAt IS NOT NULL AND expiresAt <= :currentTime")
    suspend fun deleteExpiredMessages(currentTime: Long): Int

    @Query("SELECT * FROM messages WHERE roomId = :roomId AND isFromMe = 1 AND (status = 'SENDING' OR status = 'FAILED') ORDER BY timestamp ASC")
    suspend fun getPendingOutgoingMessagesForRoom(roomId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE isFromMe = 1 AND (status = 'SENDING' OR status = 'FAILED') ORDER BY timestamp ASC")
    suspend fun getPendingOutgoingMessages(): List<MessageEntity>

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("DELETE FROM messages WHERE roomId = :roomId")
    suspend fun deleteMessagesForRoom(roomId: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()
}
