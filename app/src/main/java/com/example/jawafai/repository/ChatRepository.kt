package com.example.jawafai.repository

import com.example.jawafai.model.ChatMessage
import com.example.jawafai.model.ChatSummary
import com.example.jawafai.model.LastMessage
import com.example.jawafai.model.TypingStatus
import kotlinx.coroutines.flow.Flow

data class UserProfile(
    val userId: String = "",
    val username: String = "",
    val email: String = "",
    val displayName: String = "",
    val profileImageUrl: String? = null
)

interface ChatRepository {
    suspend fun sendMessage(senderId: String, receiverId: String, message: String)
    fun getMessages(senderId: String, receiverId: String): Flow<List<ChatMessage>>
    suspend fun markMessagesAsSeen(senderId: String, receiverId: String, currentUserId: String)
    fun getChatSummaries(userId: String): Flow<List<ChatSummary>>
    suspend fun findUserByEmailOrUsername(query: String): UserProfile?
    suspend fun findUserById(userId: String): UserProfile?
    suspend fun createChatWithUser(currentUserId: String, otherUserId: String): String

    // New methods for improved functionality
    suspend fun updateTypingStatus(userId: String, typingTo: String, isTyping: Boolean)
    fun getTypingStatus(userId: String): Flow<TypingStatus?>
    fun getLastMessages(userId: String): Flow<Map<String, LastMessage>>
    
    // Delete message functionality
    suspend fun deleteMessage(messageId: String, senderId: String, receiverId: String)

    // Delete entire chat functionality
    suspend fun deleteChat(currentUserId: String, otherUserId: String)
}
