package com.example.jawafai.service

import android.content.Context
import android.util.Log
import com.example.jawafai.managers.GroqApiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * AI Reply Manager for generating contextually aware replies to notifications
 * Uses GroqApiManager for LLM integration with conversation context and user persona
 */
object NotificationAIReplyManager {

    private const val TAG = "NotificationAIReply"
    private const val MAX_CONTEXT_MESSAGES = 10
    private const val DEFAULT_CONTEXT_MESSAGES = 15

    /**
     * Generate AI reply for a notification with conversation context
     */
    suspend fun generateAIReply(
        notification: NotificationMemoryStore.ExternalNotification,
        userPersona: Map<String, Any>? = null,
        context: Context
    ): AIReplyResult = withContext(Dispatchers.IO) {

        try {
            Log.d(TAG, "🤖 Generating AI reply for notification")
            Log.d(TAG, "📱 App: ${notification.packageName}")
            Log.d(TAG, "👤 Sender: ${notification.sender}")
            Log.d(TAG, "💬 Message: ${notification.text}")

            // Get conversation history for this specific sender (last 10 messages)
            val senderHistory = NotificationMemoryStore.getConversationContext(
                notification.conversationId,
                MAX_CONTEXT_MESSAGES
            )

            Log.d(TAG, "📚 Sender conversation history: ${senderHistory.size} messages")

            // Convert notification history to chat messages format for GroqApiManager
            val chatHistory = convertNotificationHistoryToChatMessages(senderHistory)

            // Get app name for context
            val appName = getAppName(notification.packageName)
            val senderName = notification.sender ?: notification.title

            // Generate reply using GroqApiManager's notification-specific method
            val groqResponse = GroqApiManager.getNotificationReply(
                currentMessage = notification.text,
                senderName = senderName,
                appName = appName,
                conversationHistory = chatHistory,
                userPersona = userPersona
            )

            if (groqResponse.success && groqResponse.message != null) {
                Log.d(TAG, "✅ AI reply generated successfully")
                Log.d(TAG, "🎯 Reply: ${groqResponse.message.take(100)}...")

                // Update notification with AI reply
                NotificationMemoryStore.updateAIReply(notification.hash, groqResponse.message)

                return@withContext AIReplyResult(
                    success = true,
                    reply = groqResponse.message,
                    error = null,
                    conversationId = notification.conversationId
                )
            } else {
                Log.e(TAG, "❌ AI reply generation failed: ${groqResponse.error}")
                return@withContext AIReplyResult(
                    success = false,
                    reply = null,
                    error = groqResponse.error ?: "Unknown error",
                    conversationId = notification.conversationId
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception in AI reply generation: ${e.message}", e)
            return@withContext AIReplyResult(
                success = false,
                reply = null,
                error = e.message ?: "Unknown error",
                conversationId = notification.conversationId
            )
        }
    }

    /**
     * Generate AI reply with enhanced context - uses conversation ID to fetch history
     * This is the preferred method for generating personalized replies
     */
    suspend fun generateAIReplyWithContext(
        notification: NotificationMemoryStore.ExternalNotification,
        conversationId: String,
        maxContextMessages: Int = DEFAULT_CONTEXT_MESSAGES,
        userPersona: Map<String, Any>? = null,
        context: Context
    ): AIReplyResult = withContext(Dispatchers.IO) {

        try {
            Log.d(TAG, "🤖 Generating AI reply with enhanced context")
            Log.d(TAG, "📱 App: ${notification.packageName}")
            Log.d(TAG, "👤 Sender: ${notification.sender}")
            Log.d(TAG, "💬 Current Message: ${notification.text}")
            Log.d(TAG, "🔑 Conversation ID: $conversationId")
            Log.d(TAG, "📊 Max Context Messages: $maxContextMessages")

            // Get conversation messages from memory store
            val conversationMessages = NotificationMemoryStore.getMessagesForConversation(conversationId)
                .takeLast(maxContextMessages)

            Log.d(TAG, "📚 Retrieved ${conversationMessages.size} messages for context")

            // Convert messages to chat format
            val chatHistory = convertMessagesToChatFormat(conversationMessages)

            // Get app name for context
            val appName = getAppName(notification.packageName)
            val senderName = notification.sender ?: notification.title

            // Build persona context string
            val personaContext = buildPersonaContext(userPersona)
            if (personaContext != null) {
                Log.d(TAG, "👤 User persona loaded with ${userPersona?.size ?: 0} attributes")
            }

            // Generate reply using GroqApiManager with enhanced context
            val groqResponse = GroqApiManager.getNotificationReplyWithPersona(
                currentMessage = notification.text,
                senderName = senderName,
                appName = appName,
                conversationHistory = chatHistory,
                personaContext = personaContext
            )

            if (groqResponse.success && groqResponse.message != null) {
                Log.d(TAG, "✅ AI reply generated successfully with context")
                Log.d(TAG, "🎯 Reply: ${groqResponse.message.take(100)}...")

                // Update message with AI reply
                NotificationMemoryStore.updateAIReply(notification.hash, groqResponse.message)

                return@withContext AIReplyResult(
                    success = true,
                    reply = groqResponse.message,
                    error = null,
                    conversationId = conversationId
                )
            } else {
                Log.e(TAG, "❌ AI reply generation failed: ${groqResponse.error}")
                return@withContext AIReplyResult(
                    success = false,
                    reply = null,
                    error = groqResponse.error ?: "Unknown error",
                    conversationId = conversationId
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception in AI reply generation: ${e.message}", e)
            return@withContext AIReplyResult(
                success = false,
                reply = null,
                error = e.message ?: "Unknown error",
                conversationId = conversationId
            )
        }
    }

    /**
     * Generate AI reply from a Message object (when notification is not available)
     */
    suspend fun generateAIReplyFromMessage(
        message: NotificationMemoryStore.Message,
        conversationId: String,
        displayName: String,
        packageName: String,
        maxContextMessages: Int = DEFAULT_CONTEXT_MESSAGES,
        userPersona: Map<String, Any>? = null,
        context: Context
    ): AIReplyResult = withContext(Dispatchers.IO) {

        try {
            Log.d(TAG, "🤖 Generating AI reply from message object")
            Log.d(TAG, "💬 Message: ${message.msg_content}")
            Log.d(TAG, "👤 Sender: ${message.sender_name}")
            Log.d(TAG, "🔑 Conversation ID: $conversationId")

            // Get conversation messages for context
            val conversationMessages = NotificationMemoryStore.getMessagesForConversation(conversationId)
                .takeLast(maxContextMessages)

            Log.d(TAG, "📚 Retrieved ${conversationMessages.size} messages for context")

            // Convert messages to chat format
            val chatHistory = convertMessagesToChatFormat(conversationMessages)

            // Get app name
            val appName = getAppName(packageName)

            // Build persona context string
            val personaContext = buildPersonaContext(userPersona)

            // Generate reply
            val groqResponse = GroqApiManager.getNotificationReplyWithPersona(
                currentMessage = message.msg_content,
                senderName = message.sender_name.ifBlank { displayName },
                appName = appName,
                conversationHistory = chatHistory,
                personaContext = personaContext
            )

            if (groqResponse.success && groqResponse.message != null) {
                Log.d(TAG, "✅ AI reply generated successfully")

                // Update message with AI reply
                NotificationMemoryStore.updateMessageAIReply(message.msg_hash, groqResponse.message)

                return@withContext AIReplyResult(
                    success = true,
                    reply = groqResponse.message,
                    error = null,
                    conversationId = conversationId
                )
            } else {
                return@withContext AIReplyResult(
                    success = false,
                    reply = null,
                    error = groqResponse.error ?: "Unknown error",
                    conversationId = conversationId
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception: ${e.message}", e)
            return@withContext AIReplyResult(
                success = false,
                reply = null,
                error = e.message ?: "Unknown error",
                conversationId = conversationId
            )
        }
    }

    /**
     * Convert Message objects to chat format for AI
     */
    private fun convertMessagesToChatFormat(
        messages: List<NotificationMemoryStore.Message>
    ): List<GroqApiManager.ChatMessage> {
        val chatMessages = mutableListOf<GroqApiManager.ChatMessage>()

        messages.forEach { message ->
            // Incoming messages as "user" role
            if (!message.is_outgoing) {
                chatMessages.add(
                    GroqApiManager.ChatMessage(
                        role = "user",
                        content = "${message.sender_name}: ${message.msg_content}"
                    )
                )
            } else {
                // Outgoing messages or AI replies as "assistant" role
                chatMessages.add(
                    GroqApiManager.ChatMessage(
                        role = "assistant",
                        content = message.msg_content
                    )
                )
            }

            // Add AI reply if available
            if (message.ai_reply.isNotBlank() && !message.is_outgoing) {
                chatMessages.add(
                    GroqApiManager.ChatMessage(
                        role = "assistant",
                        content = message.ai_reply
                    )
                )
            }
        }

        return chatMessages
    }

    /**
     * Build persona context string from user persona map
     */
    private fun buildPersonaContext(userPersona: Map<String, Any>?): String? {
        if (userPersona.isNullOrEmpty()) return null

        val personaBuilder = StringBuilder()
        personaBuilder.append("User Persona:\n")

        userPersona.forEach { (key, value) ->
            val formattedKey = key.replace("_", " ").replaceFirstChar { it.uppercase() }
            personaBuilder.append("- $formattedKey: $value\n")
        }

        return personaBuilder.toString()
    }

    /**
     * Build context-aware prompt for AI reply generation (DEPRECATED - now handled in GroqApiManager)
     */
    @Deprecated("Context building is now handled in GroqApiManager.getNotificationReply")
    private fun buildContextPrompt(
        currentNotification: NotificationMemoryStore.ExternalNotification,
        conversationHistory: List<NotificationMemoryStore.ExternalNotification>,
        userPersona: Map<String, Any>?
    ): String {
        // This method is deprecated - context building is now handled in GroqApiManager
        return currentNotification.text
    }

    /**
     * Convert notification history to chat messages format (DEPRECATED - use private method)
     */
    @Deprecated("Use private convertNotificationHistoryToChatMessages method")
    private fun convertToChatMessages(
        notifications: List<NotificationMemoryStore.ExternalNotification>
    ): List<GroqApiManager.ChatMessage> {
        return convertNotificationHistoryToChatMessages(notifications)
    }

    /**
     * Convert notification history to chat messages format for GroqApiManager
     */
    private fun convertNotificationHistoryToChatMessages(
        notifications: List<NotificationMemoryStore.ExternalNotification>
    ): List<GroqApiManager.ChatMessage> {

        val chatMessages = mutableListOf<GroqApiManager.ChatMessage>()

        // Process notifications in chronological order to build conversation context
        notifications.forEach { notification ->
            // Add the original message from sender as user message
            chatMessages.add(
                GroqApiManager.ChatMessage(
                    role = "user",
                    content = "${notification.sender ?: notification.title}: ${notification.text}"
                )
            )

            // Add AI reply if available as assistant message
            if (notification.ai_reply.isNotBlank()) {
                chatMessages.add(
                    GroqApiManager.ChatMessage(
                        role = "assistant",
                        content = notification.ai_reply
                    )
                )
            }
        }

        return chatMessages
    }

    /**
     * Get app name from package name
     */
    private fun getAppName(packageName: String): String {
        return when (packageName) {
            "com.whatsapp" -> "WhatsApp"
            "com.whatsapp.w4b" -> "WhatsApp Business"
            "com.instagram.android" -> "Instagram"
            "com.facebook.orca" -> "Facebook Messenger"
            "com.facebook.katana" -> "Facebook"
            "com.telegram.messenger" -> "Telegram"
            "com.snapchat.android" -> "Snapchat"
            "com.twitter.android" -> "Twitter"
            else -> packageName
        }
    }

    /**
     * Format timestamp for display
     */
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /**
     * Analyze conversation tone from history
     */
    fun analyzeConversationTone(
        conversationHistory: List<NotificationMemoryStore.ExternalNotification>
    ): ConversationTone {

        if (conversationHistory.isEmpty()) {
            return ConversationTone.NEUTRAL
        }

        val recentMessages = conversationHistory.take(5)
        val combinedText = recentMessages.joinToString(" ") { it.text.lowercase() }

        return when {
            combinedText.contains(Regex("(haha|lol|😂|😄|😊|funny|joke)")) -> ConversationTone.CASUAL_FUNNY
            combinedText.contains(Regex("(thanks|thank you|appreciate|please|sorry)")) -> ConversationTone.POLITE_FORMAL
            combinedText.contains(Regex("(love|miss|care|heart|❤️|💕)")) -> ConversationTone.AFFECTIONATE
            combinedText.contains(Regex("(urgent|asap|important|quick|now)")) -> ConversationTone.URGENT
            combinedText.contains(Regex("(meeting|work|business|project|task)")) -> ConversationTone.PROFESSIONAL
            else -> ConversationTone.NEUTRAL
        }
    }

    /**
     * Get conversation statistics
     */
    fun getConversationStats(conversationId: String): ConversationStats {
        val notifications = NotificationMemoryStore.getNotificationsByConversation(conversationId)
        val withReplies = notifications.count { it.ai_reply.isNotBlank() }
        val sent = notifications.count { it.is_sent }

        return ConversationStats(
            totalMessages = notifications.size,
            messagesWithAIReplies = withReplies,
            sentReplies = sent,
            tone = analyzeConversationTone(notifications)
        )
    }

    /**
     * Data classes for results
     */
    data class AIReplyResult(
        val success: Boolean,
        val reply: String?,
        val error: String?,
        val conversationId: String
    )

    data class ConversationStats(
        val totalMessages: Int,
        val messagesWithAIReplies: Int,
        val sentReplies: Int,
        val tone: ConversationTone
    )

    enum class ConversationTone {
        CASUAL_FUNNY,
        POLITE_FORMAL,
        AFFECTIONATE,
        URGENT,
        PROFESSIONAL,
        NEUTRAL
    }
}
