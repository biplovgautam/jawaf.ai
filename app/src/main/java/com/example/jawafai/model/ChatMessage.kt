package com.example.jawafai.model

data class ChatMessage(
    val messageId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val text: String = "", // Changed from messageText to text to match your spec
    val timestamp: Long = System.currentTimeMillis(),
    var seen: Boolean = false // Changed from isSeen to seen to match your spec
)

data class LastMessage(
    val text: String = "",
    val timestamp: Long = 0,
    val seen: Boolean = false
)

data class TypingStatus(
    val typingTo: String = "",
    val isTyping: Boolean = false,
    val lastUpdate: Long = System.currentTimeMillis()
)
