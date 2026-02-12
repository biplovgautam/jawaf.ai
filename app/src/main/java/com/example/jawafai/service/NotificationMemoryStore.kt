package com.example.jawafai.service

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import android.app.RemoteInput
import android.app.Notification
import android.util.Log
import java.security.MessageDigest

// Enhanced in-memory notification store for smart messaging assistant
object NotificationMemoryStore {

    // Table 1: Conversations (Inbox view)
    // Note: display_name should always be the OTHER person's name (not "You" or current user)
    // This matches how WhatsApp/Messenger show conversations
    data class Conversation(
        val convo_id: String,              // e.g., "com.whatsapp_170762839"
        val package_name: String,          // e.g., "com.whatsapp"
        val display_name: String,          // OTHER person's name (e.g., "Biplov Gautam")
        val last_msg_time: Long,           // Timestamp for sorting
        val last_msg_content: String,      // Last message preview
        val platform_id: String? = null,   // Optional platform-specific ID
        val unread_count: Int = 0          // Number of unread messages
    )

    // Table 2: Messages (Chat history)
    // Note: sender_name identifies WHO sent each specific message
    // This allows distinguishing between messages from different people in a conversation
    data class Message(
        val msg_id: String,                // Using hash as unique ID
        val convo_id: String,              // Foreign key to Conversation
        val sender_name: String,           // WHO sent this specific message (could be different people)
        val msg_content: String,           // The text
        val timestamp: Long,               // When it was sent
        val is_outgoing: Boolean = false,  // True if from current user (all notifications are incoming)
        val msg_hash: String,              // Deduplication
        val has_reply_action: Boolean = false,
        val ai_reply: String = "",
        val is_sent: Boolean = false
    )

    // Original notification data (kept for compatibility)
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
        val is_sent: Boolean = false,         // Whether reply was sent via RemoteInput
        val rawExtras: Map<String, String> = emptyMap() // ALL raw notification extras for debugging
    )

    private val notifications: SnapshotStateList<ExternalNotification> = mutableStateListOf()
    private val notificationHashes: MutableSet<String> = mutableSetOf()

    // Conversation-based storage
    private val conversations: SnapshotStateList<Conversation> = mutableStateListOf()
    private val messages: SnapshotStateList<Message> = mutableStateListOf()

    /**
     * Check if notification is a WhatsApp summary notification (not actual message)
     * Examples: "2 new messages", "4 messages", "3 new messages from...", "2 messages from 2 chats"
     */
    private fun isSummaryNotification(text: String): Boolean {
        val lowerText = text.lowercase().trim()

        // Pattern 1: "X new messages" or "X messages"
        val summaryPattern1 = Regex("^\\d+\\s+(new\\s+)?messages?$")
        if (summaryPattern1.matches(lowerText)) return true

        // Pattern 2: "X new messages from..." (group chat summary)
        val summaryPattern2 = Regex("^\\d+\\s+new\\s+messages?\\s+from.*")
        if (summaryPattern2.matches(lowerText)) return true

        // Pattern 3: Single digit followed by "messages" (e.g., "2 messages")
        if (lowerText.matches(Regex("^\\d{1,3}\\s+messages?$"))) return true

        // Pattern 4: "X messages from X chats" (WhatsApp multiple chat summary)
        val summaryPattern4 = Regex("^\\d+\\s+messages?\\s+from\\s+\\d+\\s+chats?$")
        if (summaryPattern4.matches(lowerText)) return true

        // Pattern 5: "X new messages from X chats"
        val summaryPattern5 = Regex("^\\d+\\s+new\\s+messages?\\s+from\\s+\\d+\\s+chats?$")
        if (summaryPattern5.matches(lowerText)) return true

        return false
    }

    /**
     * Add notification with deduplication
     * Also updates Conversations and Messages tables
     * Filters out WhatsApp summary notifications
     */
    fun addNotification(notification: ExternalNotification): Boolean {
        // Skip WhatsApp summary notifications (e.g., "2 new messages")
        if (isSummaryNotification(notification.text)) {
            Log.d("NotificationMemoryStore", "Skipping summary notification: ${notification.text}")
            return false
        }

        return if (!notificationHashes.contains(notification.hash)) {
            notifications.add(0, notification) // Add to top
            notificationHashes.add(notification.hash)

            // Add to conversation-based storage
            addToConversationStore(notification)

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
     * Parse notification sender based on platform-specific formats
     * Returns: Triple(is_outgoing, sender_name_for_bubble, display_name_for_conversation)
     *
     * Platform formats:
     * - WhatsApp: title = "SenderName" or "You" (outgoing)
     * - Instagram: title = "username: SenderName", selfDisplayName = "YourName"
     * - Messenger: title = "SenderName" or "You" (outgoing)
     */
    private fun parseNotificationSender(
        packageName: String,
        title: String,
        sender: String?,
        extras: Map<String, String>
    ): Triple<Boolean, String, String> {

        return when {
            // Instagram specific parsing
            packageName.contains("instagram", ignoreCase = true) -> {
                parseInstagramSender(title, extras)
            }

            // WhatsApp specific parsing
            packageName.contains("whatsapp", ignoreCase = true) -> {
                parseWhatsAppSender(title, sender)
            }

            // Messenger specific parsing
            packageName.contains("messenger", ignoreCase = true) ||
            packageName.contains("facebook.orca", ignoreCase = true) -> {
                parseMessengerSender(title, sender)
            }

            // Default fallback
            else -> {
                val is_outgoing = title.equals("You", ignoreCase = true)
                val sender_name = if (is_outgoing) "You" else (sender ?: title)
                Triple(is_outgoing, sender_name, sender_name)
            }
        }
    }

    /**
     * Parse Instagram notification sender
     * Format: title = "username: SenderName"
     * selfDisplayName = phone owner's name
     *
     * Example:
     * - title = "xyzeep.jpeg: Roshan Jaishi" (incoming from Roshan)
     * - title = "xyzeep.jpeg: Pawan" (outgoing, because selfDisplayName = "Pawan")
     */
    private fun parseInstagramSender(title: String, extras: Map<String, String>): Triple<Boolean, String, String> {
        // Get selfDisplayName (phone owner's name)
        val selfDisplayName = extras["android.selfDisplayName"] ?: ""

        // Title format: "username: SenderName"
        val colonIndex = title.lastIndexOf(":")

        if (colonIndex != -1 && colonIndex < title.length - 1) {
            // Extract sender name after the colon
            val senderInTitle = title.substring(colonIndex + 1).trim()

            // Check if sender name matches selfDisplayName (outgoing)
            val is_outgoing = senderInTitle.equals(selfDisplayName, ignoreCase = true)

            if (is_outgoing) {
                // Outgoing message
                // sender_name = "You" for bubble display
                // display_name = extract from title (the part before colon is the username we're chatting with)
                // But for Instagram, we need the OTHER person's name, which we get from previous incoming messages
                return Triple(true, "You", "")
            } else {
                // Incoming message from senderInTitle
                // sender_name = actual sender name
                // display_name = same (for showing in inbox)
                return Triple(false, senderInTitle, senderInTitle)
            }
        }

        // Fallback: check if title matches selfDisplayName directly
        if (title.equals(selfDisplayName, ignoreCase = true)) {
            return Triple(true, "You", "")
        }

        // Default: treat as incoming
        return Triple(false, title, title)
    }

    /**
     * Parse WhatsApp notification sender
     * Format: title = "SenderName" or "You"
     */
    private fun parseWhatsAppSender(title: String, sender: String?): Triple<Boolean, String, String> {
        val is_outgoing = title.equals("You", ignoreCase = true)

        return if (is_outgoing) {
            Triple(true, "You", "")
        } else {
            val sender_name = sender ?: title
            Triple(false, sender_name, sender_name)
        }
    }

    /**
     * Parse Messenger notification sender
     * Similar to WhatsApp
     */
    private fun parseMessengerSender(title: String, sender: String?): Triple<Boolean, String, String> {
        val is_outgoing = title.equals("You", ignoreCase = true)

        return if (is_outgoing) {
            Triple(true, "You", "")
        } else {
            val sender_name = sender ?: title
            Triple(false, sender_name, sender_name)
        }
    }

    /**
     * Add notification to conversation-based storage (Table 1 & 2)
     */
    private fun addToConversationStore(notification: ExternalNotification) {
        val convo_id = notification.conversationId
        val package_name = notification.packageName
        val msg_content = notification.text
        val timestamp = notification.time

        // Get title and extras for platform-specific parsing
        val title = notification.title
        val extras = notification.rawExtras

        // Determine if this is an outgoing message and extract proper sender name
        val (is_outgoing, sender_name, display_name_for_convo) = parseNotificationSender(
            packageName = package_name,
            title = title,
            sender = notification.sender,
            extras = extras
        )

        // Should we update the conversation's display name?
        // Only update if it's an incoming message (we want to show the OTHER person's name)
        val should_update_display_name = !is_outgoing

        // Update or create conversation (Table 1)
        val existingConvoIndex = conversations.indexOfFirst { it.convo_id == convo_id }
        if (existingConvoIndex != -1) {
            // Update existing conversation
            val existingConvo = conversations[existingConvoIndex]
            conversations.removeAt(existingConvoIndex)
            conversations.add(0, existingConvo.copy(
                last_msg_time = timestamp,
                last_msg_content = msg_content,
                unread_count = if (is_outgoing) existingConvo.unread_count else existingConvo.unread_count + 1,
                // Only update display_name if it's NOT an outgoing message
                display_name = if (should_update_display_name && display_name_for_convo.isNotBlank())
                    display_name_for_convo else existingConvo.display_name
            ))
        } else {
            // Create new conversation
            conversations.add(0, Conversation(
                convo_id = convo_id,
                package_name = package_name,
                display_name = if (display_name_for_convo.isNotBlank()) display_name_for_convo else "Unknown",
                last_msg_time = timestamp,
                last_msg_content = msg_content,
                platform_id = null,
                unread_count = if (is_outgoing) 0 else 1
            ))
        }

        // Add message (Table 2)
        val message = Message(
            msg_id = notification.hash,
            convo_id = convo_id,
            sender_name = sender_name, // "You" if outgoing, actual sender if incoming
            msg_content = msg_content,
            timestamp = timestamp,
            is_outgoing = is_outgoing,
            msg_hash = notification.hash,
            has_reply_action = notification.hasReplyAction,
            ai_reply = notification.ai_reply,
            is_sent = notification.is_sent
        )
        messages.add(0, message)

        // Limit messages per conversation
        val convoMessages = messages.filter { it.convo_id == convo_id }
        if (convoMessages.size > 100) {
            val oldestMessage = convoMessages.last()
            messages.remove(oldestMessage)
        }
    }

    /**
     * Get all conversations sorted by last message time
     */
    fun getAllConversations(): List<Conversation> {
        return conversations.sortedByDescending { it.last_msg_time }
    }

    /**
     * Get messages for a specific conversation
     */
    fun getMessagesForConversation(convo_id: String): List<Message> {
        return messages.filter { it.convo_id == convo_id }.sortedBy { it.timestamp }
    }

    /**
     * Mark conversation as read
     */
    fun markConversationAsRead(convo_id: String) {
        val index = conversations.indexOfFirst { it.convo_id == convo_id }
        if (index != -1) {
            conversations[index] = conversations[index].copy(unread_count = 0)
        }
    }

    /**
     * Update AI reply for a message
     */
    fun updateMessageAIReply(msg_hash: String, aiReply: String): Boolean {
        val msgIndex = messages.indexOfFirst { it.msg_hash == msg_hash }
        if (msgIndex != -1) {
            messages[msgIndex] = messages[msgIndex].copy(ai_reply = aiReply)

            // Also update in notifications list
            updateAIReply(msg_hash, aiReply)
            return true
        }
        return false
    }

    /**
     * Mark message as sent
     */
    fun markMessageAsSent(msg_hash: String): Boolean {
        val msgIndex = messages.indexOfFirst { it.msg_hash == msg_hash }
        if (msgIndex != -1) {
            messages[msgIndex] = messages[msgIndex].copy(is_sent = true)

            // Also update in notifications list
            markAsSent(msg_hash)
            return true
        }
        return false
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
     * Clear all notifications, conversations, and messages
     */
    fun clear() {
        notifications.clear()
        notificationHashes.clear()
        conversations.clear()
        messages.clear()
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
