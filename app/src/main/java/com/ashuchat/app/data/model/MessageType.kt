package com.ashuchat.app.data.model

enum class MessageType {
    TEXT,
    IMAGE,
    FILE,
    AUDIO,
    PING,
    LOVE_PING // Backward compatibility with previous records
}
