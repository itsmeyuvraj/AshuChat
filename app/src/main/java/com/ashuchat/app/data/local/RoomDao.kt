package com.ashuchat.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomDao {

    @Query("SELECT * FROM rooms ORDER BY lastTimestamp DESC")
    fun getAllRoomsFlow(): Flow<List<RoomEntity>>

    @Query("SELECT * FROM rooms WHERE roomId = :roomId LIMIT 1")
    suspend fun getRoomById(roomId: String): RoomEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateRoom(room: RoomEntity)

    @Query("UPDATE rooms SET lastMessage = :lastMessage, lastTimestamp = :timestamp WHERE roomId = :roomId")
    suspend fun updateLastMessage(roomId: String, lastMessage: String, timestamp: Long)

    @Query("UPDATE rooms SET partnerName = :partnerName WHERE roomId = :roomId")
    suspend fun updatePartnerName(roomId: String, partnerName: String)

    @Query("UPDATE rooms SET unreadCount = :unreadCount WHERE roomId = :roomId")
    suspend fun updateUnreadCount(roomId: String, unreadCount: Int)

    @Query("DELETE FROM rooms WHERE roomId = :roomId")
    suspend fun deleteRoom(roomId: String)

    @Query("DELETE FROM rooms")
    suspend fun deleteAllRooms()
}
