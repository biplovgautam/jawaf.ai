package com.example.jawafai.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jawafai.model.ChatBotConversationModel
import com.example.jawafai.model.ChatBotMessageModel
import com.example.jawafai.model.ChatBotMessageType
import com.example.jawafai.repository.ChatBotRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ChatBotViewModel(
    private val repository: ChatBotRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val TAG = "ChatBotViewModel"

    // Current conversation state
    private val _currentConversation = MutableStateFlow<ChatBotConversationModel?>(null)
    val currentConversation: StateFlow<ChatBotConversationModel?> = _currentConversation.asStateFlow()

    // Messages for current conversation
    private val _messages = MutableStateFlow<List<ChatBotMessageModel>>(emptyList())
    val messages: StateFlow<List<ChatBotMessageModel>> = _messages.asStateFlow()

    // User conversations list
    private val _conversations = MutableStateFlow<List<ChatBotConversationModel>>(emptyList())
    val conversations: StateFlow<List<ChatBotConversationModel>> = _conversations.asStateFlow()

    // UI states
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadUserConversations()
    }

    /**
     * Loads all conversations for the current user
     */
    private fun loadUserConversations() {
        val userId = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                repository.getUserConversations(userId).collect { conversationList ->
                    _conversations.value = conversationList
                    Log.d(TAG, "✅ Loaded ${conversationList.size} conversations")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading conversations: ${e.message}", e)
                _error.value = "Failed to load conversations"
            }
        }
    }

    /**
     * Creates a new conversation
     */
    fun createNewConversation() {
        val userId = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val conversation = ChatBotConversationModel(
                    userId = userId,
                    title = "New Chat"
                )

                val conversationId = repository.createConversation(conversation)
                if (conversationId != null) {
                    val createdConversation = conversation.copy(id = conversationId)
                    _currentConversation.value = createdConversation
                    loadConversationMessages(conversationId)
                    Log.d(TAG, "✅ New conversation created: $conversationId")
                } else {
                    _error.value = "Failed to create conversation"
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error creating conversation: ${e.message}", e)
                _error.value = "Failed to create conversation"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Loads an existing conversation
     */
    fun loadConversation(conversationId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val conversation = repository.getConversation(conversationId)
                if (conversation != null) {
                    _currentConversation.value = conversation
                    loadConversationMessages(conversationId)
                } else {
                    _error.value = "Conversation not found"
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading conversation: ${e.message}", e)
                _error.value = "Failed to load conversation"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Loads messages for a conversation
     */
    private fun loadConversationMessages(conversationId: String) {
        viewModelScope.launch {
            try {
                repository.getConversationMessages(conversationId).collect { messageList ->
                    _messages.value = messageList
                    Log.d(TAG, "✅ Loaded ${messageList.size} messages for conversation: $conversationId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading messages: ${e.message}", e)
                _error.value = "Failed to load messages"
            }
        }
    }

    /**
     * Sends a message to the chatbot
     */
    fun sendMessage(messageText: String) {
        val currentConv = _currentConversation.value
        if (currentConv == null) {
            Log.w(TAG, "No active conversation to send message")
            return
        }

        if (messageText.isBlank()) {
            Log.w(TAG, "Cannot send empty message")
            return
        }

        viewModelScope.launch {
            try {
                _isTyping.value = true

                // Create user message
                val userMessage = ChatBotMessageModel(
                    id = UUID.randomUUID().toString(),
                    conversationId = currentConv.id,
                    message = messageText.trim(),
                    isFromUser = true,
                    timestamp = System.currentTimeMillis(),
                    messageType = ChatBotMessageType.TEXT
                )

                // Send user message
                val userMessageSent = repository.sendMessage(userMessage)
                if (!userMessageSent) {
                    _error.value = "Failed to send message"
                    return@launch
                }

                // Update conversation title if it's the first message
                if (_messages.value.isEmpty() || currentConv.title == "New Chat") {
                    val newTitle = if (messageText.length > 30) {
                        messageText.take(30) + "..."
                    } else {
                        messageText
                    }
                    repository.updateConversationTitle(currentConv.id, newTitle)
                }

                // Get AI response
                val currentMessages = _messages.value
                val aiResponse = repository.getChatBotResponse(messageText, currentMessages)

                if (aiResponse != null) {
                    // Create AI message
                    val aiMessage = ChatBotMessageModel(
                        id = UUID.randomUUID().toString(),
                        conversationId = currentConv.id,
                        message = aiResponse,
                        isFromUser = false,
                        timestamp = System.currentTimeMillis(),
                        messageType = ChatBotMessageType.TEXT
                    )

                    // Send AI message
                    repository.sendMessage(aiMessage)
                } else {
                    // Send error message
                    val errorMessage = ChatBotMessageModel(
                        id = UUID.randomUUID().toString(),
                        conversationId = currentConv.id,
                        message = "Sorry, I'm having trouble responding right now. Please try again.",
                        isFromUser = false,
                        timestamp = System.currentTimeMillis(),
                        messageType = ChatBotMessageType.ERROR
                    )
                    repository.sendMessage(errorMessage)
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error sending message: ${e.message}", e)
                _error.value = "Failed to send message"
            } finally {
                _isTyping.value = false
            }
        }
    }

    /**
     * Deletes a conversation
     */
    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            try {
                val deleted = repository.deleteConversation(conversationId)
                if (deleted) {
                    // If it was the current conversation, clear it
                    if (_currentConversation.value?.id == conversationId) {
                        _currentConversation.value = null
                        _messages.value = emptyList()
                    }
                    Log.d(TAG, "✅ Conversation deleted: $conversationId")
                } else {
                    _error.value = "Failed to delete conversation"
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error deleting conversation: ${e.message}", e)
                _error.value = "Failed to delete conversation"
            }
        }
    }

    /**
     * Clears the current error
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Clears the current conversation (for navigating back)
     */
    fun clearCurrentConversation() {
        _currentConversation.value = null
        _messages.value = emptyList()
    }
}
