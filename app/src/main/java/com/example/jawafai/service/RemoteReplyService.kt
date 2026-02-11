package com.example.jawafai.service

import android.content.Context
import android.content.Intent
import android.app.RemoteInput
import android.os.Bundle
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service for handling RemoteInput replies to external notifications
 * This service works with the NotificationListenerService to send replies
 * directly through the original app's notification system
 */
object RemoteReplyService {

    private const val TAG = "RemoteReplyService"
    private const val SEND_REPLY_ACTION = "com.example.jawafai.SEND_REPLY"
    private const val REPLY_STATUS_ACTION = "com.example.jawafai.REPLY_STATUS"
    const val MAX_RETRY_ATTEMPTS = 3  // Made public
    private const val RETRY_DELAY_MS = 2000L

    // Track sending status
    private val sendingStatus = mutableMapOf<String, ReplyStatus>()

    /**
     * Send reply using RemoteInput for a specific conversation with retry mechanism
     */
    suspend fun sendReply(
        context: Context,
        conversationId: String,
        replyText: String,
        retryAttempt: Int = 0
    ): ReplyResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🚀 Attempting to send reply for conversation: $conversationId (attempt ${retryAttempt + 1})")
            Log.d(TAG, "💬 Reply text: ${replyText.take(100)}...")

            // Update status to sending
            updateSendingStatus(context, conversationId, ReplyStatus.SENDING)

            // Get the notification with reply action for this conversation
            val notifications = NotificationMemoryStore.getNotificationsByConversation(conversationId)
            val notificationWithReply = notifications.firstOrNull {
                it.hasReplyAction && it.replyAction != null && it.remoteInput != null
            }

            if (notificationWithReply == null) {
                Log.e(TAG, "❌ No notification with reply action found for conversation: $conversationId")
                val result = ReplyResult(
                    success = false,
                    error = "No notification with reply action found",
                    canRetry = false,
                    conversationId = conversationId
                )
                updateSendingStatus(context, conversationId, ReplyStatus.FAILED, result.error)
                return@withContext result
            }

            val replyAction = notificationWithReply.replyAction!!
            val remoteInput = notificationWithReply.remoteInput!!

            Log.d(TAG, "✅ Found reply action for ${notificationWithReply.packageName}")

            // Create intent with reply text
            val replyIntent = Intent()
            val bundle = Bundle()
            bundle.putCharSequence(remoteInput.resultKey, replyText)
            RemoteInput.addResultsToIntent(arrayOf(remoteInput), replyIntent, bundle)

            // Send the reply through the original app's PendingIntent
            replyAction.actionIntent.send(context, 0, replyIntent)

            // Wait a bit to see if send was successful
            delay(1000)

            Log.d(TAG, "✅ Reply sent successfully via RemoteInput")

            // Mark as sent in our store
            NotificationMemoryStore.markAsSent(notificationWithReply.hash)

            // Update status to success
            val result = ReplyResult(
                success = true,
                error = null,
                canRetry = false,
                conversationId = conversationId
            )
            updateSendingStatus(context, conversationId, ReplyStatus.SENT)

            return@withContext result

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error sending reply (attempt ${retryAttempt + 1}): ${e.message}", e)

            val canRetry = retryAttempt < MAX_RETRY_ATTEMPTS - 1
            val result = ReplyResult(
                success = false,
                error = e.message ?: "Unknown error",
                canRetry = canRetry,
                conversationId = conversationId
            )

            if (canRetry) {
                Log.d(TAG, "🔄 Will retry in ${RETRY_DELAY_MS}ms...")
                updateSendingStatus(context, conversationId, ReplyStatus.RETRYING, "Retrying... (${retryAttempt + 1}/$MAX_RETRY_ATTEMPTS)")
                delay(RETRY_DELAY_MS)
                return@withContext sendReply(context, conversationId, replyText, retryAttempt + 1)
            } else {
                Log.e(TAG, "❌ Max retry attempts reached. Giving up.")
                updateSendingStatus(context, conversationId, ReplyStatus.FAILED, result.error)
                return@withContext result
            }
        }
    }

    /**
     * Send reply using notification hash with retry mechanism
     */
    suspend fun sendReplyByHash(
        context: Context,
        notificationHash: String,
        replyText: String
    ): ReplyResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🚀 Attempting to send reply for notification: $notificationHash")

            // Find the notification
            val notification = NotificationMemoryStore.getAllNotifications()
                .firstOrNull { it.hash == notificationHash }

            if (notification == null) {
                Log.e(TAG, "❌ Notification not found: $notificationHash")
                return@withContext ReplyResult(
                    success = false,
                    error = "Notification not found",
                    canRetry = false,
                    conversationId = ""
                )
            }

            return@withContext sendReply(context, notification.conversationId, replyText)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error sending reply by hash: ${e.message}", e)
            return@withContext ReplyResult(
                success = false,
                error = e.message ?: "Unknown error",
                canRetry = false,
                conversationId = ""
            )
        }
    }

    /**
     * Update sending status and broadcast to UI
     */
    private fun updateSendingStatus(
        context: Context,
        conversationId: String,
        status: ReplyStatus,
        message: String? = null
    ) {
        sendingStatus[conversationId] = status

        // Broadcast status update
        val intent = Intent(REPLY_STATUS_ACTION).apply {
            putExtra("conversationId", conversationId)
            putExtra("status", status.name)
            putExtra("message", message)
            putExtra("timestamp", System.currentTimeMillis())
        }

        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    /**
     * Get current sending status for a conversation
     */
    fun getSendingStatus(conversationId: String): ReplyStatus {
        return sendingStatus[conversationId] ?: ReplyStatus.IDLE
    }

    /**
     * Clear sending status for a conversation
     */
    fun clearSendingStatus(conversationId: String) {
        sendingStatus.remove(conversationId)
    }

    /**
     * Check if reply can be sent for a conversation
     */
    fun canSendReply(conversationId: String): Boolean {
        val notifications = NotificationMemoryStore.getNotificationsByConversation(conversationId)
        return notifications.any {
            it.hasReplyAction && it.replyAction != null && it.remoteInput != null
        }
    }

    /**
     * Get reply capability info for a conversation
     */
    fun getReplyCapability(conversationId: String): ReplyCapability {
        val notifications = NotificationMemoryStore.getNotificationsByConversation(conversationId)
        val notificationWithReply = notifications.firstOrNull {
            it.hasReplyAction && it.replyAction != null && it.remoteInput != null
        }

        return if (notificationWithReply != null) {
            ReplyCapability(
                canReply = true,
                appName = getAppName(notificationWithReply.packageName),
                packageName = notificationWithReply.packageName,
                remoteInputLabel = notificationWithReply.remoteInput?.label?.toString() ?: "Send message"
            )
        } else {
            ReplyCapability(
                canReply = false,
                appName = "Unknown",
                packageName = "",
                remoteInputLabel = ""
            )
        }
    }

    /**
     * Broadcast reply status
     */
    private fun broadcastReplyStatus(
        context: Context,
        conversationId: String,
        notificationHash: String,
        success: Boolean,
        replyText: String
    ) {
        val intent = Intent(JawafaiNotificationListenerService.REPLY_SENT_ACTION).apply {
            putExtra("conversationId", conversationId)
            putExtra("notificationHash", notificationHash)
            putExtra("success", success)
            putExtra("replyText", replyText)
            putExtra("timestamp", System.currentTimeMillis())
        }

        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
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
     * Data class for reply capability information
     */
    data class ReplyCapability(
        val canReply: Boolean,
        val appName: String,
        val packageName: String,
        val remoteInputLabel: String
    )

    /**
     * Data class for reply result with retry information
     */
    data class ReplyResult(
        val success: Boolean,
        val error: String?,
        val canRetry: Boolean,
        val conversationId: String
    )

    /**
     * Enum for reply sending status
     */
    enum class ReplyStatus {
        IDLE,
        SENDING,
        SENT,
        RETRYING,
        FAILED
    }
}
