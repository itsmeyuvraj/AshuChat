package com.ashuchat.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rooms",
    indices = [Index(value = ["lastTimestamp"])]
)
data class RoomEntity(
    @PrimaryKey
    val roomId: String,
    val partnerName: String = "Peer",
    val lastMessage: String = "",
    val lastTimestamp: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
