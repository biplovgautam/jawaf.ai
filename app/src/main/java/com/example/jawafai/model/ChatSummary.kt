package com.example.jawafai.model

data class ChatSummary(
    val chatId: String = "",
    val otherUserId: String = "",
    val otherUserName: String = "",
    val otherUserImageUrl: String? = null,
    val lastMessage: String = "",
    val lastMessageTimestamp: Long = 0,
    val isLastMessageSeen: Boolean = false,
    val unreadCount: Int = 0
)

