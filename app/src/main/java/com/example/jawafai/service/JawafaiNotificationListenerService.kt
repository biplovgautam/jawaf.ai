package com.example.jawafai.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import android.provider.Settings
import android.util.Log
import android.app.RemoteInput
import android.app.Notification
import android.app.PendingIntent
import android.os.Bundle
import kotlin.random.Random

class JawafaiNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "JawafaiNotifService"
        const val NOTIFICATION_BROADCAST_ACTION = "com.example.jawafai.NOTIFICATION_LISTENER_EVENT"
        const val AI_REPLY_BROADCAST_ACTION = "com.example.jawafai.AI_REPLY_REQUEST"
        const val REPLY_GENERATED_ACTION = "com.example.jawafai.REPLY_GENERATED"
        const val REPLY_SENT_ACTION = "com.example.jawafai.REPLY_SENT"

        // Define the package names of apps we want to capture notifications from
        private val SUPPORTED_APPS = mapOf(
            "com.instagram.android" to "Instagram",
            "com.whatsapp" to "WhatsApp",
            "com.facebook.orca" to "Facebook Messenger",
            "com.whatsapp.w4b" to "WhatsApp Business",
            "com.facebook.katana" to "Facebook",
            "com.snapchat.android" to "Snapchat",
            "com.twitter.android" to "Twitter",
            "com.telegram.messenger" to "Telegram"
        )

        // Utility to check if notification access is enabled
        fun isNotificationAccessEnabled(context: Context): Boolean {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            return enabledListeners.contains(context.packageName)
        }
    }

    private val localBroadcastManager by lazy { LocalBroadcastManager.getInstance(this) }

    // Broadcast receiver for handling AI-generated replies
    private val replyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                REPLY_GENERATED_ACTION -> handleGeneratedReply(intent)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Smart Messaging Assistant connected!")

        // Register broadcast receiver for AI replies
        val filter = IntentFilter(REPLY_GENERATED_ACTION)
        localBroadcastManager.registerReceiver(replyReceiver, filter)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Smart Messaging Assistant disconnected!")

        // Unregister broadcast receiver
        try {
            localBroadcastManager.unregisterReceiver(replyReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver: ${e.message}")
        }
    }

    private fun isSupportedApp(packageName: String): Boolean {
        return SUPPORTED_APPS.containsKey(packageName)
    }

    /**
     * Check if the app is both supported AND connected by user
     */
    private fun isAppConnected(packageName: String): Boolean {
        // First check if it's a supported app
        if (!isSupportedApp(packageName)) {
            return false
        }

        // Then check if user has connected this app
        return com.example.jawafai.managers.ConnectedAppsManager.shouldProcessNotification(packageName)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return

        // Skip our own notifications
        if (packageName == this.packageName) {
            return
        }

        // Skip if not a supported app
        if (!isSupportedApp(packageName)) {
            Log.d(TAG, "Notification ignored - not from supported app: $packageName")
            return
        }

        // Skip if app is not connected by user in settings
        if (!isAppConnected(packageName)) {
            Log.d(TAG, "Notification ignored - app not connected in settings: $packageName")
            return
        }

        Log.d(TAG, "Processing notification from ${SUPPORTED_APPS[packageName]} (connected)")

        try {
            val smartNotification = extractNotificationData(sbn)

            // Store notification with deduplication
            val isNewNotification = NotificationMemoryStore.addNotification(smartNotification)

            if (isNewNotification) {
                Log.d(TAG, "New notification stored: ${smartNotification.conversationId}")

                // Broadcast to AI module if reply action is available
                if (smartNotification.hasReplyAction) {
                    triggerAIReplyGeneration(smartNotification)
                }
            } else {
                Log.d(TAG, "Duplicate notification ignored")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification: ${e.message}", e)
        }
    }

    /**
     * Extract comprehensive notification data for smart messaging
     */
    private fun extractNotificationData(sbn: StatusBarNotification): NotificationMemoryStore.ExternalNotification {
        val notification = sbn.notification
        val extras = notification.extras
        val packageName = sbn.packageName

        // Extract basic notification fields
        val title = extras.getString(Notification.EXTRA_TITLE) ?: "(No Title)"
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "(No Text)"
        val sender = extras.getString(Notification.EXTRA_SUB_TEXT)
        val conversationTitle = extras.getString(Notification.EXTRA_CONVERSATION_TITLE)
        val timestamp = sbn.postTime

        // Generate conversation ID - use notification key or create custom one
        val conversationId = generateConversationId(sbn, title, sender)

        // Check for reply action and RemoteInput
        val (hasReplyAction, replyAction, remoteInput) = extractReplyAction(notification)

        // Generate hash for deduplication
        val hash = NotificationMemoryStore.generateHash(title, text, packageName)

        // Capture ALL raw extras for debugging/analysis
        val rawExtras = mutableMapOf<String, String>()
        extras.keySet()?.forEach { key ->
            val value = extras.get(key)
            rawExtras[key] = when (value) {
                is CharSequence -> value.toString()
                is Array<*> -> value.joinToString(", ") { it.toString() }
                else -> value.toString()
            }
        }

        Log.d(TAG, "Extracted notification data:")
        Log.d(TAG, "  Title: $title")
        Log.d(TAG, "  Text: $text")
        Log.d(TAG, "  Sender: $sender")
        Log.d(TAG, "  ConversationId: $conversationId")
        Log.d(TAG, "  HasReplyAction: $hasReplyAction")
        Log.d(TAG, "  Raw Extras Count: ${rawExtras.size}")

        return NotificationMemoryStore.ExternalNotification(
            title = title,
            text = text,
            packageName = packageName,
            time = timestamp,
            sender = sender,
            conversationTitle = conversationTitle,
            conversationId = conversationId,
            hasReplyAction = hasReplyAction,
            replyAction = replyAction,
            remoteInput = remoteInput,
            hash = hash,
            rawExtras = rawExtras
        )
    }

    /**
     * Generate unique conversation ID
     */
    private fun generateConversationId(sbn: StatusBarNotification, title: String, sender: String?): String {
        // Try to use notification key first
        val notificationKey = sbn.key
        if (notificationKey.isNotBlank()) {
            return "${sbn.packageName}_${notificationKey.hashCode()}"
        }

        // Fallback to combination of package, title, and sender
        val identifier = "${sbn.packageName}_${title}_${sender ?: "unknown"}"
        return identifier.hashCode().toString()
    }

    /**
     * Extract reply action and RemoteInput from notification
     */
    private fun extractReplyAction(notification: Notification): Triple<Boolean, Notification.Action?, RemoteInput?> {
        val actions = notification.actions ?: return Triple(false, null, null)

        for (action in actions) {
            val remoteInputs = action.remoteInputs
            if (remoteInputs != null && remoteInputs.isNotEmpty()) {
                // Found reply action with RemoteInput
                Log.d(TAG, "Reply action found: ${action.title}")
                return Triple(true, action, remoteInputs[0])
            }
        }

        return Triple(false, null, null)
    }

    /**
     * Trigger AI reply generation via broadcast
     */
    private fun triggerAIReplyGeneration(notification: NotificationMemoryStore.ExternalNotification) {
        Log.d(TAG, "Triggering AI reply generation for: ${notification.conversationId}")

        val intent = Intent(AI_REPLY_BROADCAST_ACTION).apply {
            putExtra("conversationId", notification.conversationId)
            putExtra("title", notification.title)
            putExtra("text", notification.text)
            putExtra("sender", notification.sender)
            putExtra("packageName", notification.packageName)
            putExtra("timestamp", notification.time)
        }

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    /**
     * Send smart reply using RemoteInput
     */
    fun sendSmartReply(sbn: StatusBarNotification, replyText: String): Boolean {
        try {
            val notification = sbn.notification
            val actions = notification.actions ?: return false

            // Find reply action
            var replyAction: Notification.Action? = null
            var remoteInput: RemoteInput? = null

            for (action in actions) {
                val remoteInputs = action.remoteInputs
                if (remoteInputs != null && remoteInputs.isNotEmpty()) {
                    replyAction = action
                    remoteInput = remoteInputs[0]
                    break
                }
            }

            if (replyAction == null || remoteInput == null) {
                Log.e(TAG, "No reply action found for notification")
                return false
            }

            // Create reply intent
            val replyIntent = Intent()
            val bundle = Bundle()
            bundle.putCharSequence(remoteInput.resultKey, replyText)
            RemoteInput.addResultsToIntent(arrayOf(remoteInput), replyIntent, bundle)

            // Send reply
            replyAction.actionIntent.send(this, 0, replyIntent)

            Log.d(TAG, "Smart reply sent: $replyText")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error sending smart reply: ${e.message}", e)
            return false
        }
    }

    /**
     * Send smart reply by conversation ID
     */
    fun sendSmartReplyByConversationId(conversationId: String, replyText: String): Boolean {
        val notifications = NotificationMemoryStore.getNotificationsByConversation(conversationId)
        val latestNotificationWithReply = notifications.firstOrNull { it.hasReplyAction }

        if (latestNotificationWithReply?.replyAction != null && latestNotificationWithReply.remoteInput != null) {
            try {
                val replyIntent = Intent()
                val bundle = Bundle()
                bundle.putCharSequence(latestNotificationWithReply.remoteInput.resultKey, replyText)
                RemoteInput.addResultsToIntent(arrayOf(latestNotificationWithReply.remoteInput), replyIntent, bundle)

                latestNotificationWithReply.replyAction.actionIntent.send(this, 0, replyIntent)

                Log.d(TAG, "Smart reply sent via conversation ID: $replyText")
                return true

            } catch (e: Exception) {
                Log.e(TAG, "Error sending smart reply via conversation ID: ${e.message}", e)
                return false
            }
        }

        return false
    }

    /**
     * Handle AI-generated reply and attempt to send it
     */
    private fun handleGeneratedReply(intent: Intent) {
        val conversationId = intent.getStringExtra("conversationId") ?: return
        val replyText = intent.getStringExtra("replyText") ?: return
        val packageName = intent.getStringExtra("packageName") ?: return
        val notificationHash = intent.getStringExtra("notificationHash") ?: return

        Log.d(TAG, "📨 Received AI-generated reply for conversation: $conversationId")
        Log.d(TAG, "💬 Reply: ${replyText.take(100)}...")

        // Attempt to send the reply
        val success = sendSmartReplyByConversationId(conversationId, replyText)

        // Update notification status
        if (success) {
            NotificationMemoryStore.markAsSent(notificationHash)
            Log.d(TAG, "✅ Reply sent successfully")
        } else {
            Log.e(TAG, "❌ Failed to send reply")
        }

        // Broadcast reply sent status
        val statusIntent = Intent(REPLY_SENT_ACTION).apply {
            putExtra("conversationId", conversationId)
            putExtra("notificationHash", notificationHash)
            putExtra("success", success)
            putExtra("replyText", replyText)
        }
        localBroadcastManager.sendBroadcast(statusIntent)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return
        if (isSupportedApp(packageName)) {
            Log.d(TAG, "Notification removed from ${SUPPORTED_APPS[packageName]}")
        }
    }
}
