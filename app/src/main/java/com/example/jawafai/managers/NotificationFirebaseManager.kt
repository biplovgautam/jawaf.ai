package com.example.jawafai.managers

import android.util.Log
import com.example.jawafai.service.NotificationMemoryStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Manager for syncing notification data with Firebase Realtime Database
 * Structure:
 * notifications/
 *   {userId}/
 *     conversations/
 *       {convoId}/
 *         display_name: String
 *         package_name: String
 *         last_msg_time: Long
 *         last_msg_content: String
 *         unread_count: Int
 *         messages/
 *           {msgId}/
 *             sender_name: String
 *             msg_content: String
 *             timestamp: Long
 *             is_outgoing: Boolean
 *             ai_reply: String
 *             is_sent: Boolean
 */
object NotificationFirebaseManager {

    private const val TAG = "NotificationFirebase"
    private const val ROOT_PATH = "notifications"
    private const val CONVERSATIONS_PATH = "conversations"
    private const val MESSAGES_PATH = "messages"
    private const val ANALYTICS_PATH = "analytics"

    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Get current user ID
     */
    private fun getUserId(): String? = auth.currentUser?.uid

    /**
     * Get reference to user's notifications
     */
    private fun getUserRef(): DatabaseReference? {
        val userId = getUserId() ?: return null
        return database.reference.child(ROOT_PATH).child(userId)
    }

    /**
     * Load all conversations and messages from Firebase into NotificationMemoryStore
     * Call this on app startup to restore previous data
     */
    suspend fun loadFromFirebase(): Boolean {
        val userRef = getUserRef() ?: run {
            Log.d(TAG, "No user logged in, skipping Firebase load")
            return false
        }

        return try {
            Log.d(TAG, "📥 Loading conversations from Firebase...")

            val conversationsRef = userRef.child(CONVERSATIONS_PATH)
            val snapshot = conversationsRef.get().await()

            if (!snapshot.exists()) {
                Log.d(TAG, "No conversations found in Firebase")
                return true
            }

            var loadedConversations = 0
            var loadedMessages = 0

            for (convoSnapshot in snapshot.children) {
                try {
                    // Load conversation data
                    val convoId = decodeKey(convoSnapshot.child("convo_id").getValue(String::class.java) ?: continue)
                    val packageName = convoSnapshot.child("package_name").getValue(String::class.java) ?: ""
                    val displayName = convoSnapshot.child("display_name").getValue(String::class.java) ?: "Unknown"
                    val lastMsgTime = convoSnapshot.child("last_msg_time").getValue(Long::class.java) ?: 0L
                    val lastMsgContent = convoSnapshot.child("last_msg_content").getValue(String::class.java) ?: ""
                    val unreadCount = convoSnapshot.child("unread_count").getValue(Int::class.java) ?: 0
                    val platformId = convoSnapshot.child("platform_id").getValue(String::class.java)

                    val conversation = NotificationMemoryStore.Conversation(
                        convo_id = convoId,
                        package_name = packageName,
                        display_name = displayName,
                        last_msg_time = lastMsgTime,
                        last_msg_content = lastMsgContent,
                        platform_id = platformId?.takeIf { it.isNotBlank() },
                        unread_count = unreadCount
                    )

                    // Add to memory store
                    NotificationMemoryStore.addConversationFromFirebase(conversation)
                    loadedConversations++

                    // Load messages for this conversation
                    val messagesSnapshot = convoSnapshot.child(MESSAGES_PATH)
                    for (msgSnapshot in messagesSnapshot.children) {
                        try {
                            val msgId = msgSnapshot.child("msg_id").getValue(String::class.java) ?: continue
                            val msgConvoId = msgSnapshot.child("convo_id").getValue(String::class.java) ?: convoId
                            val senderName = msgSnapshot.child("sender_name").getValue(String::class.java) ?: ""
                            val msgContent = msgSnapshot.child("msg_content").getValue(String::class.java) ?: ""
                            val timestamp = msgSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                            val isOutgoing = msgSnapshot.child("is_outgoing").getValue(Boolean::class.java) ?: false
                            val msgHash = msgSnapshot.child("msg_hash").getValue(String::class.java) ?: msgId
                            val hasReplyAction = msgSnapshot.child("has_reply_action").getValue(Boolean::class.java) ?: false
                            val aiReply = msgSnapshot.child("ai_reply").getValue(String::class.java) ?: ""
                            val isSent = msgSnapshot.child("is_sent").getValue(Boolean::class.java) ?: false

                            val message = NotificationMemoryStore.Message(
                                msg_id = msgId,
                                convo_id = msgConvoId,
                                sender_name = senderName,
                                msg_content = msgContent,
                                timestamp = timestamp,
                                is_outgoing = isOutgoing,
                                msg_hash = msgHash,
                                has_reply_action = hasReplyAction,
                                ai_reply = aiReply,
                                is_sent = isSent
                            )

                            NotificationMemoryStore.addMessageFromFirebase(message)
                            loadedMessages++
                        } catch (e: Exception) {
                            Log.e(TAG, "Error loading message: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading conversation: ${e.message}")
                }
            }

            Log.d(TAG, "✅ Loaded $loadedConversations conversations and $loadedMessages messages from Firebase")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading from Firebase: ${e.message}")
            false
        }
    }

    /**
     * Save a conversation to Firebase
     */
    suspend fun saveConversation(conversation: NotificationMemoryStore.Conversation): Boolean {
        val userRef = getUserRef() ?: return false

        return try {
            val convoRef = userRef.child(CONVERSATIONS_PATH).child(encodeKey(conversation.convo_id))

            val convoData = mapOf(
                "convo_id" to conversation.convo_id,
                "package_name" to conversation.package_name,
                "display_name" to conversation.display_name,
                "last_msg_time" to conversation.last_msg_time,
                "last_msg_content" to conversation.last_msg_content,
                "platform_id" to (conversation.platform_id ?: ""),
                "unread_count" to conversation.unread_count
            )

            convoRef.updateChildren(convoData).await()
            Log.d(TAG, "✅ Saved conversation: ${conversation.convo_id}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving conversation: ${e.message}")
            false
        }
    }

    /**
     * Save a message to Firebase
     */
    suspend fun saveMessage(message: NotificationMemoryStore.Message): Boolean {
        val userRef = getUserRef() ?: return false

        return try {
            val msgRef = userRef
                .child(CONVERSATIONS_PATH)
                .child(encodeKey(message.convo_id))
                .child(MESSAGES_PATH)
                .child(encodeKey(message.msg_id))

            val msgData = mapOf(
                "msg_id" to message.msg_id,
                "convo_id" to message.convo_id,
                "sender_name" to message.sender_name,
                "msg_content" to message.msg_content,
                "timestamp" to message.timestamp,
                "is_outgoing" to message.is_outgoing,
                "msg_hash" to message.msg_hash,
                "has_reply_action" to message.has_reply_action,
                "ai_reply" to message.ai_reply,
                "is_sent" to message.is_sent
            )

            msgRef.setValue(msgData).await()
            Log.d(TAG, "✅ Saved message: ${message.msg_id}")

            // Update analytics
            updateAnalytics(message)

            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving message: ${e.message}")
            false
        }
    }

    /**
     * Update analytics data for insights
     */
    private suspend fun updateAnalytics(message: NotificationMemoryStore.Message) {
        val userRef = getUserRef() ?: return
        val analyticsRef = userRef.child(ANALYTICS_PATH)

        try {
            // Increment total messages
            analyticsRef.child("total_messages").runTransaction(object : Transaction.Handler {
                override fun doTransaction(data: MutableData): Transaction.Result {
                    val current = data.getValue(Long::class.java) ?: 0L
                    data.value = current + 1
                    return Transaction.success(data)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
            })

            // Track by platform
            val platform = when {
                message.convo_id.contains("whatsapp", true) -> "whatsapp"
                message.convo_id.contains("instagram", true) -> "instagram"
                message.convo_id.contains("messenger", true) ||
                message.convo_id.contains("facebook.orca", true) -> "messenger"
                else -> "other"
            }

            analyticsRef.child("by_platform").child(platform).runTransaction(object : Transaction.Handler {
                override fun doTransaction(data: MutableData): Transaction.Result {
                    val current = data.getValue(Long::class.java) ?: 0L
                    data.value = current + 1
                    return Transaction.success(data)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
            })

            // Track AI replies
            if (message.ai_reply.isNotBlank()) {
                analyticsRef.child("ai_replies_generated").runTransaction(object : Transaction.Handler {
                    override fun doTransaction(data: MutableData): Transaction.Result {
                        val current = data.getValue(Long::class.java) ?: 0L
                        data.value = current + 1
                        return Transaction.success(data)
                    }
                    override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
                })
            }

            // Track sent replies
            if (message.is_sent) {
                analyticsRef.child("replies_sent").runTransaction(object : Transaction.Handler {
                    override fun doTransaction(data: MutableData): Transaction.Result {
                        val current = data.getValue(Long::class.java) ?: 0L
                        data.value = current + 1
                        return Transaction.success(data)
                    }
                    override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
                })
            }

            // Track incoming vs outgoing
            val direction = if (message.is_outgoing) "outgoing" else "incoming"
            analyticsRef.child("by_direction").child(direction).runTransaction(object : Transaction.Handler {
                override fun doTransaction(data: MutableData): Transaction.Result {
                    val current = data.getValue(Long::class.java) ?: 0L
                    data.value = current + 1
                    return Transaction.success(data)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
            })

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating analytics: ${e.message}")
        }
    }

    /**
     * Delete a message from Firebase
     */
    suspend fun deleteMessage(convoId: String, msgId: String): Boolean {
        val userRef = getUserRef() ?: return false

        return try {
            userRef
                .child(CONVERSATIONS_PATH)
                .child(encodeKey(convoId))
                .child(MESSAGES_PATH)
                .child(encodeKey(msgId))
                .removeValue()
                .await()

            Log.d(TAG, "✅ Deleted message: $msgId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deleting message: ${e.message}")
            false
        }
    }

    /**
     * Delete entire conversation from Firebase
     */
    suspend fun deleteConversation(convoId: String): Boolean {
        val userRef = getUserRef() ?: return false

        return try {
            userRef
                .child(CONVERSATIONS_PATH)
                .child(encodeKey(convoId))
                .removeValue()
                .await()

            Log.d(TAG, "✅ Deleted conversation: $convoId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deleting conversation: ${e.message}")
            false
        }
    }

    /**
     * Get analytics data as Flow
     */
    fun getAnalyticsFlow(): Flow<NotificationAnalytics> = callbackFlow {
        val userRef = getUserRef()
        if (userRef == null) {
            trySend(NotificationAnalytics())
            close()
            return@callbackFlow
        }

        val analyticsRef = userRef.child(ANALYTICS_PATH)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val analytics = NotificationAnalytics(
                    totalMessages = snapshot.child("total_messages").getValue(Long::class.java) ?: 0L,
                    aiRepliesGenerated = snapshot.child("ai_replies_generated").getValue(Long::class.java) ?: 0L,
                    repliesSent = snapshot.child("replies_sent").getValue(Long::class.java) ?: 0L,
                    whatsappCount = snapshot.child("by_platform/whatsapp").getValue(Long::class.java) ?: 0L,
                    instagramCount = snapshot.child("by_platform/instagram").getValue(Long::class.java) ?: 0L,
                    messengerCount = snapshot.child("by_platform/messenger").getValue(Long::class.java) ?: 0L,
                    incomingCount = snapshot.child("by_direction/incoming").getValue(Long::class.java) ?: 0L,
                    outgoingCount = snapshot.child("by_direction/outgoing").getValue(Long::class.java) ?: 0L
                )
                trySend(analytics)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Analytics listener cancelled: ${error.message}")
            }
        }

        analyticsRef.addValueEventListener(listener)

        awaitClose {
            analyticsRef.removeEventListener(listener)
        }
    }

    /**
     * Update AI reply in Firebase
     */
    suspend fun updateAIReply(convoId: String, msgId: String, aiReply: String): Boolean {
        val userRef = getUserRef() ?: return false

        return try {
            userRef
                .child(CONVERSATIONS_PATH)
                .child(encodeKey(convoId))
                .child(MESSAGES_PATH)
                .child(encodeKey(msgId))
                .child("ai_reply")
                .setValue(aiReply)
                .await()

            Log.d(TAG, "✅ Updated AI reply for message: $msgId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating AI reply: ${e.message}")
            false
        }
    }

    /**
     * Mark message as sent in Firebase
     */
    suspend fun markMessageAsSent(convoId: String, msgId: String): Boolean {
        val userRef = getUserRef() ?: return false

        return try {
            userRef
                .child(CONVERSATIONS_PATH)
                .child(encodeKey(convoId))
                .child(MESSAGES_PATH)
                .child(encodeKey(msgId))
                .child("is_sent")
                .setValue(true)
                .await()

            Log.d(TAG, "✅ Marked message as sent: $msgId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error marking message as sent: ${e.message}")
            false
        }
    }

    /**
     * Encode key for Firebase (Firebase doesn't allow . $ # [ ] / in keys)
     */
    private fun encodeKey(key: String): String {
        return key
            .replace(".", "_dot_")
            .replace("$", "_dollar_")
            .replace("#", "_hash_")
            .replace("[", "_lb_")
            .replace("]", "_rb_")
            .replace("/", "_slash_")
    }

    /**
     * Decode key from Firebase
     */
    private fun decodeKey(key: String): String {
        return key
            .replace("_dot_", ".")
            .replace("_dollar_", "$")
            .replace("_hash_", "#")
            .replace("_lb_", "[")
            .replace("_rb_", "]")
            .replace("_slash_", "/")
    }

    /**
     * Data class for analytics
     */
    data class NotificationAnalytics(
        val totalMessages: Long = 0,
        val aiRepliesGenerated: Long = 0,
        val repliesSent: Long = 0,
        val whatsappCount: Long = 0,
        val instagramCount: Long = 0,
        val messengerCount: Long = 0,
        val incomingCount: Long = 0,
        val outgoingCount: Long = 0
    ) {
        val responseRate: Float
            get() = if (totalMessages > 0) (repliesSent.toFloat() / totalMessages) * 100f else 0f

        val aiUsageRate: Float
            get() = if (totalMessages > 0) (aiRepliesGenerated.toFloat() / totalMessages) * 100f else 0f
    }
}

