package com.example.jawafai.repository

import android.util.Log
import com.example.jawafai.managers.GroqApiManager
import com.example.jawafai.model.ChatBotConversationModel
import com.example.jawafai.model.ChatBotMessageModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatBotRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : ChatBotRepository {

    private val TAG = "ChatBotRepository"
    private val database = FirebaseDatabase.getInstance("https://jawafai-d2c23-default-rtdb.firebaseio.com/")
    private val conversationsRef = database.getReference("chatbot_conversations")
    private val messagesRef = database.getReference("chatbot_messages")

    // Groq API configuration
    private val GROQ_API_KEY = "gsk_your_groq_api_key_here" // Replace with your actual Groq API key
    private val GROQ_BASE_URL = "https://api.groq.com/openai/v1/chat/completions"

    override suspend fun createConversation(conversation: ChatBotConversationModel): String? {
        return try {
            val conversationId = conversationsRef.push().key ?: return null
            val conversationWithId = conversation.copy(id = conversationId)

            conversationsRef.child(conversationId).setValue(conversationWithId.toMap()).await()
            Log.d(TAG, "✅ Conversation created with ID: $conversationId")
            conversationId
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creating conversation: ${e.message}", e)
            null
        }
    }

    override suspend fun getUserConversations(userId: String): Flow<List<ChatBotConversationModel>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val conversations = mutableListOf<ChatBotConversationModel>()

                for (child in snapshot.children) {
                    try {
                        val data = child.value as? Map<String, Any> ?: continue
                        if (data["userId"] == userId && data["isActive"] == true) {
                            val conversation = ChatBotConversationModel.fromMap(data)
                            conversations.add(conversation)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing conversation: ${e.message}")
                    }
                }

                // Sort by updatedAt descending
                conversations.sortByDescending { it.updatedAt }
                trySend(conversations)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Database error: ${error.message}")
                close(error.toException())
            }
        }

        conversationsRef.addValueEventListener(listener)
        awaitClose { conversationsRef.removeEventListener(listener) }
    }

    override suspend fun getConversation(conversationId: String): ChatBotConversationModel? {
        return try {
            val snapshot = conversationsRef.child(conversationId).get().await()
            val data = snapshot.value as? Map<String, Any> ?: return null
            ChatBotConversationModel.fromMap(data)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting conversation: ${e.message}", e)
            null
        }
    }

    override suspend fun sendMessage(message: ChatBotMessageModel): Boolean {
        return try {
            val messageId = messagesRef.push().key ?: return false
            val messageWithId = message.copy(id = messageId)

            messagesRef.child(messageId).setValue(messageWithId.toMap()).await()

            // Update conversation's updatedAt timestamp
            conversationsRef.child(message.conversationId)
                .child("updatedAt")
                .setValue(System.currentTimeMillis())
                .await()

            Log.d(TAG, "✅ Message sent successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error sending message: ${e.message}", e)
            false
        }
    }

    override suspend fun getConversationMessages(conversationId: String): Flow<List<ChatBotMessageModel>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = mutableListOf<ChatBotMessageModel>()

                for (child in snapshot.children) {
                    try {
                        val data = child.value as? Map<String, Any> ?: continue
                        if (data["conversationId"] == conversationId) {
                            val message = ChatBotMessageModel.fromMap(data)
                            messages.add(message)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing message: ${e.message}")
                    }
                }

                // Sort by timestamp ascending
                messages.sortBy { it.timestamp }
                trySend(messages)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Database error: ${error.message}")
                close(error.toException())
            }
        }

        messagesRef.addValueEventListener(listener)
        awaitClose { messagesRef.removeEventListener(listener) }
    }

    override suspend fun updateConversationTitle(conversationId: String, title: String): Boolean {
        return try {
            conversationsRef.child(conversationId)
                .child("title")
                .setValue(title)
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating conversation title: ${e.message}", e)
            false
        }
    }

    override suspend fun deleteConversation(conversationId: String): Boolean {
        return try {
            // Mark conversation as inactive instead of deleting
            conversationsRef.child(conversationId)
                .child("isActive")
                .setValue(false)
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deleting conversation: ${e.message}", e)
            false
        }
    }

    override suspend fun getChatBotResponse(message: String, conversationHistory: List<ChatBotMessageModel>): String? {
        return try {
            // Check message limit (20 messages max)
            if (conversationHistory.size >= 20) {
                Log.w(TAG, "⚠️ Conversation history exceeds 20 messages limit")
                return "I notice we've been chatting for quite a while! To keep our conversation fresh and focused, please start a new chat. This helps me provide better responses. 😊"
            }

            // Get user persona for context (if available)
            val userPersona = getUserPersona(conversationHistory.firstOrNull()?.conversationId ?: "")

            // Convert conversation history to GroqApiManager format
            val chatHistory = GroqApiManager.convertToChatMessages(conversationHistory)

            // Call GroqApiManager with the specific chatbot method
            val response = GroqApiManager.getChatBotResponse(
                userMessage = message,
                conversationHistory = chatHistory,
                userPersona = userPersona
            )

            if (response.success) {
                Log.d(TAG, "✅ Received chatbot response from Groq API")
                return response.message
            } else {
                Log.e(TAG, "❌ Groq API chatbot error: ${response.error}")
                return null
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting chatbot response: ${e.message}", e)
            null
        }
    }

    override suspend fun getUserPersona(userId: String): Map<String, Any>? {
        return try {
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("persona")
                .get()
                .await()

            val persona = mutableMapOf<String, Any>()
            snapshot.documents.forEach { doc ->
                persona[doc.id] = doc.data ?: emptyMap<String, Any>()
            }

            if (persona.isNotEmpty()) persona else null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting user persona: ${e.message}", e)
            null
        }
    }
}
