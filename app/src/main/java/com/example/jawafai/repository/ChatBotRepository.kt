package com.example.jawafai.repository

import com.example.jawafai.model.ChatBotConversationModel
import com.example.jawafai.model.ChatBotMessageModel
import kotlinx.coroutines.flow.Flow

interface ChatBotRepository {
    /**
     * Creates a new chatbot conversation
     */
    suspend fun createConversation(conversation: ChatBotConversationModel): String?

    /**
     * Gets all conversations for a user
     */
    suspend fun getUserConversations(userId: String): Flow<List<ChatBotConversationModel>>

    /**
     * Gets a specific conversation by ID
     */
    suspend fun getConversation(conversationId: String): ChatBotConversationModel?

    /**
     * Sends a message to the chatbot conversation
     */
    suspend fun sendMessage(message: ChatBotMessageModel): Boolean

    /**
     * Gets all messages for a conversation as a flow
     */
    suspend fun getConversationMessages(conversationId: String): Flow<List<ChatBotMessageModel>>

    /**
     * Updates conversation title
     */
    suspend fun updateConversationTitle(conversationId: String, title: String): Boolean

    /**
     * Deletes a conversation and all its messages
     */
    suspend fun deleteConversation(conversationId: String): Boolean

    /**
     * Sends message to Groq API and gets AI response
     */
    suspend fun getChatBotResponse(message: String, conversationHistory: List<ChatBotMessageModel>): String?

    /**
     * Gets user's persona data for context
     */
    suspend fun getUserPersona(userId: String): Map<String, Any>?
}
