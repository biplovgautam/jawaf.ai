package com.example.jawafai.service

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import android.app.RemoteInput
import android.app.Notification
import java.security.MessageDigest

// Enhanced in-memory notification store for smart messaging assistant
object NotificationMemoryStore {
    data class ExternalNotification(
        val title: String,                    // Group name or sender
        val text: String,                     // Message content
        val packageName: String,              // App package name
        val time: Long,                       // Timestamp
        val sender: String? = null,           // android.subText - actual sender
        val conversationTitle: String? = null, // For group chats
        val conversationId: String,           // Unique conversation identifier
        val hasReplyAction: Boolean = false,  // Whether reply action is available
        val replyAction: Notification.Action? = null, // Reply action reference
        val remoteInput: RemoteInput? = null, // RemoteInput reference
        val hash: String,                     // For deduplication
        val ai_reply: String = "",            // AI generated reply (empty if not generated)
        val is_sent: Boolean = false          // Whether reply was sent via RemoteInput
    )

    private val notifications: SnapshotStateList<ExternalNotification> = mutableStateListOf()
    private val notificationHashes: MutableSet<String> = mutableSetOf()

    /**
     * Add notification with deduplication
     */
    fun addNotification(notification: ExternalNotification): Boolean {
        return if (!notificationHashes.contains(notification.hash)) {
            notifications.add(0, notification) // Add to top
            notificationHashes.add(notification.hash)

            // Limit store size to prevent memory issues
            if (notifications.size > 500) {
                val removed = notifications.removeAt(notifications.size - 1)
                notificationHashes.remove(removed.hash)
            }
            true
        } else {
            false // Duplicate notification
        }
    }

    /**
     * Get all notifications
     */
    fun getAllNotifications(): List<ExternalNotification> = notifications.toList()

    /**
     * Get notifications by package name
     */
    fun getNotificationsByPackage(packageName: String): List<ExternalNotification> {
        return notifications.filter { it.packageName == packageName }
    }

    /**
     * Get notifications by conversation ID
     */
    fun getNotificationsByConversation(conversationId: String): List<ExternalNotification> {
        return notifications.filter { it.conversationId == conversationId }
    }

    /**
     * Get notifications with reply actions
     */
    fun getNotificationsWithReplyActions(): List<ExternalNotification> {
        return notifications.filter { it.hasReplyAction }
    }

    /**
     * Clear all notifications
     */
    fun clear() {
        notifications.clear()
        notificationHashes.clear()
    }

    /**
     * Generate hash for deduplication
     */
    fun generateHash(title: String, text: String, packageName: String): String {
        val input = "$title|$text|$packageName"
        val digest = MessageDigest.getInstance("MD5")
        val hashBytes = digest.digest(input.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Get conversation context for AI processing
     */
    fun getConversationContext(conversationId: String, limit: Int = 10): List<ExternalNotification> {
        return notifications
            .filter { it.conversationId == conversationId }
            .take(limit)
            .reversed() // Chronological order for context
    }

    /**
     * Update AI reply for a notification
     */
    fun updateAIReply(hash: String, aiReply: String): Boolean {
        val index = notifications.indexOfFirst { it.hash == hash }
        if (index != -1) {
            val notification = notifications[index]
            notifications[index] = notification.copy(ai_reply = aiReply)
            return true
        }
        return false
    }

    /**
     * Mark notification as sent
     */
    fun markAsSent(hash: String): Boolean {
        val index = notifications.indexOfFirst { it.hash == hash }
        if (index != -1) {
            val notification = notifications[index]
            notifications[index] = notification.copy(is_sent = true)
            return true
        }
        return false
    }

    /**
     * Update both AI reply and sent status
     */
    fun updateReplyAndSentStatus(hash: String, aiReply: String, isSent: Boolean): Boolean {
        val index = notifications.indexOfFirst { it.hash == hash }
        if (index != -1) {
            val notification = notifications[index]
            notifications[index] = notification.copy(ai_reply = aiReply, is_sent = isSent)
            return true
        }
        return false
    }

    /**
     * Get notifications with AI replies
     */
    fun getNotificationsWithAIReplies(): List<ExternalNotification> {
        return notifications.filter { it.ai_reply.isNotBlank() }
    }

    /**
     * Get unsent notifications with AI replies
     */
    fun getUnsentAIReplies(): List<ExternalNotification> {
        return notifications.filter { it.ai_reply.isNotBlank() && !it.is_sent }
    }
}
