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
