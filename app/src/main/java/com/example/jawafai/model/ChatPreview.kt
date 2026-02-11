package com.example.jawafai.model

data class ChatPreview(
    val id: String,
    val userName: String,
    val lastMessage: String,
    val userImage: String,
    val time: String,
    val unreadCount: Int = 0,
    val isOnline: Boolean = false
)

