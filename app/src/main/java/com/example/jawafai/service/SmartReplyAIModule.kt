package com.example.jawafai.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.*

/**
 * Enhanced AI Module for generating smart replies using NotificationAIReplyManager
 * This module receives broadcasts from NotificationListenerService and generates
 * contextually appropriate responses using conversation history and user persona
 */
class SmartReplyAIModule(private val context: Context) {

    companion object {
        private const val TAG = "SmartReplyAI"
        private const val AI_REPLY_BROADCAST_ACTION = "com.example.jawafai.AI_REPLY_REQUEST"
        private const val REPLY_GENERATED_ACTION = "com.example.jawafai.REPLY_GENERATED"
        private const val REPLY_SENT_ACTION = "com.example.jawafai.REPLY_SENT"
    }

    private val aiScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val localBroadcastManager = LocalBroadcastManager.getInstance(context)

    // Store user persona (this could be loaded from SharedPreferences or database)
    private var userPersona: Map<String, Any>? = null

    // AI reply receiver
    private val aiReplyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AI_REPLY_BROADCAST_ACTION -> handleAIReplyRequest(intent)
                REPLY_SENT_ACTION -> handleReplySent(intent)
            }
        }
    }

    /**
     * Initialize the AI module and register broadcast receivers
     */
    fun initialize() {
        Log.d(TAG, "🤖 Initializing Enhanced Smart Reply AI Module")

        val filter = IntentFilter().apply {
            addAction(AI_REPLY_BROADCAST_ACTION)
            addAction(REPLY_SENT_ACTION)
        }
        localBroadcastManager.registerReceiver(aiReplyReceiver, filter)

        // Load user persona (placeholder - implement actual loading)
        loadUserPersona()

        Log.d(TAG, "✅ AI Module initialized successfully")
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        Log.d(TAG, "🧹 Cleaning up AI Module")
        localBroadcastManager.unregisterReceiver(aiReplyReceiver)
        aiScope.cancel()
    }

    /**
     * Load user persona from preferences/database
     */
    private fun loadUserPersona() {
        // Placeholder implementation - replace with actual persona loading
        userPersona = mapOf(
            "communicationStyle" to "casual_friendly",
            "tone" to "warm_empathetic",
            "responseLength" to "medium",
            "languagePreference" to "english",
            "personalityType" to "helpful_supportive"
        )

        Log.d(TAG, "👤 User persona loaded: $userPersona")
    }

    /**
     * Handle AI reply request from notification service
     */
    private fun handleAIReplyRequest(intent: Intent) {
        val conversationId = intent.getStringExtra("conversationId") ?: return
        val title = intent.getStringExtra("title") ?: ""
        val text = intent.getStringExtra("text") ?: ""
        val sender = intent.getStringExtra("sender")
        val packageName = intent.getStringExtra("packageName") ?: ""
        val timestamp = intent.getLongExtra("timestamp", 0L)

        Log.d(TAG, "📨 Processing AI reply request")
        Log.d(TAG, "🔗 Conversation ID: $conversationId")
        Log.d(TAG, "📱 App: $packageName")
        Log.d(TAG, "💬 Message: $text")

        // Generate reply asynchronously using enhanced AI system
        aiScope.launch {
            try {
                // Get the notification from memory store
                val notifications = NotificationMemoryStore.getNotificationsByConversation(conversationId)
                val currentNotification = notifications.firstOrNull {
                    it.text == text && it.time == timestamp
                }

                if (currentNotification == null) {
                    Log.e(TAG, "❌ Could not find notification in memory store")
                    return@launch
                }

                Log.d(TAG, "🔍 Found notification, generating AI reply...")

                // Generate AI reply using NotificationAIReplyManager
                val replyResult = NotificationAIReplyManager.generateAIReply(
                    notification = currentNotification,
                    userPersona = userPersona,
                    context = context
                )

                if (replyResult.success && replyResult.reply != null) {
                    Log.d(TAG, "✅ AI reply generated successfully")
                    Log.d(TAG, "🎯 Reply: ${replyResult.reply.take(100)}...")

                    // Broadcast the generated reply
                    broadcastGeneratedReply(
                        conversationId = conversationId,
                        reply = replyResult.reply,
                        packageName = packageName,
                        notificationHash = currentNotification.hash
                    )

                    // Log conversation statistics
                    val stats = NotificationAIReplyManager.getConversationStats(conversationId)
                    Log.d(TAG, "📊 Conversation stats: $stats")

                } else {
                    Log.e(TAG, "❌ AI reply generation failed: ${replyResult.error}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in AI reply generation: ${e.message}", e)
            }
        }
    }

    /**
     * Handle reply sent confirmation
     */
    private fun handleReplySent(intent: Intent) {
        val conversationId = intent.getStringExtra("conversationId") ?: return
        val notificationHash = intent.getStringExtra("notificationHash") ?: return
        val success = intent.getBooleanExtra("success", false)

        Log.d(TAG, "📤 Reply sent confirmation: $success for conversation: $conversationId")

        if (success) {
            // Mark notification as sent
            NotificationMemoryStore.markAsSent(notificationHash)
            Log.d(TAG, "✅ Notification marked as sent")
        }
    }

    /**
     * Broadcast generated reply for the notification service to send
     */
    private fun broadcastGeneratedReply(
        conversationId: String,
        reply: String,
        packageName: String,
        notificationHash: String
    ) {
        Log.d(TAG, "📢 Broadcasting generated reply")

        val intent = Intent(REPLY_GENERATED_ACTION).apply {
            putExtra("conversationId", conversationId)
            putExtra("replyText", reply)
            putExtra("packageName", packageName)
            putExtra("notificationHash", notificationHash)
            putExtra("timestamp", System.currentTimeMillis())
        }

        localBroadcastManager.sendBroadcast(intent)
    }

    /**
     * Update user persona
     */
    fun updateUserPersona(newPersona: Map<String, Any>) {
        userPersona = newPersona
        Log.d(TAG, "👤 User persona updated: $userPersona")
    }

    /**
     * Get current user persona
     */
    fun getUserPersona(): Map<String, Any>? = userPersona

    /**
     * Get conversation insights
     */
    fun getConversationInsights(conversationId: String): NotificationAIReplyManager.ConversationStats {
        return NotificationAIReplyManager.getConversationStats(conversationId)
    }

    /**
     * Manual reply generation for testing
     */
    suspend fun generateManualReply(
        conversationId: String,
        messageText: String,
        packageName: String = "com.whatsapp"
    ): String? {
        return try {
            // Create a mock notification for testing
            val mockNotification = NotificationMemoryStore.ExternalNotification(
                title = "Test User",
                text = messageText,
                packageName = packageName,
                time = System.currentTimeMillis(),
                conversationId = conversationId,
                hash = NotificationMemoryStore.generateHash("Test User", messageText, packageName)
            )

            val result = NotificationAIReplyManager.generateAIReply(
                notification = mockNotification,
                userPersona = userPersona,
                context = context
            )

            result.reply
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in manual reply generation: ${e.message}", e)
            null
        }
    }

    /**
     * Get analytics for AI reply performance
     */
    fun getAIReplyAnalytics(): Map<String, Any> {
        val allNotifications = NotificationMemoryStore.getAllNotifications()
        val withReplies = allNotifications.count { it.ai_reply.isNotBlank() }
        val sent = allNotifications.count { it.is_sent }
        val successRate = if (withReplies > 0) (sent.toFloat() / withReplies * 100) else 0f

        return mapOf(
            "totalNotifications" to allNotifications.size,
            "repliesGenerated" to withReplies,
            "repliesSent" to sent,
            "successRate" to successRate,
            "pendingReplies" to NotificationMemoryStore.getUnsentAIReplies().size
        )
    }
}
