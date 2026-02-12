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
import android.os.IBinder
import android.content.pm.ServiceInfo
import com.example.jawafai.R
import com.example.jawafai.view.dashboard.DashboardActivity
import kotlin.random.Random

class JawafaiNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "JawafaiNotifService"
        const val NOTIFICATION_BROADCAST_ACTION = "com.example.jawafai.NOTIFICATION_LISTENER_EVENT"
        const val AI_REPLY_BROADCAST_ACTION = "com.example.jawafai.AI_REPLY_REQUEST"
        const val REPLY_GENERATED_ACTION = "com.example.jawafai.REPLY_GENERATED"
        const val REPLY_SENT_ACTION = "com.example.jawafai.REPLY_SENT"

        // Foreground service constants
        private const val FOREGROUND_SERVICE_ID = 1001
        private const val FOREGROUND_CHANNEL_ID = "jawafai_foreground_service"
        private const val FOREGROUND_CHANNEL_NAME = "Jawaf.AI Background Service"

        // Action to start as foreground
        const val ACTION_START_FOREGROUND = "com.example.jawafai.START_FOREGROUND_SERVICE"
        const val ACTION_STOP_FOREGROUND = "com.example.jawafai.STOP_FOREGROUND_SERVICE"

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

        /**
         * Start the service as a foreground service
         */
        fun startForegroundService(context: Context) {
            val intent = Intent(context, JawafaiNotificationListenerService::class.java).apply {
                action = ACTION_START_FOREGROUND
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.d(TAG, "Requested foreground service start")
        }

        /**
         * Stop the foreground service (but keep listener active)
         */
        fun stopForegroundService(context: Context) {
            val intent = Intent(context, JawafaiNotificationListenerService::class.java).apply {
                action = ACTION_STOP_FOREGROUND
            }
            context.startService(intent)
        }
    }

    private val localBroadcastManager by lazy { LocalBroadcastManager.getInstance(this) }
    private var isRunningInForeground = false

    // Broadcast receiver for handling AI-generated replies
    private val replyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                REPLY_GENERATED_ACTION -> handleGeneratedReply(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        createForegroundNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand received: action=${intent?.action}")

        when (intent?.action) {
            ACTION_START_FOREGROUND -> {
                startForegroundWithNotification()
            }
            ACTION_STOP_FOREGROUND -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                isRunningInForeground = false
                Log.d(TAG, "Stopped foreground mode")
            }
            else -> {
                // Default: start in foreground mode for persistence
                if (!isRunningInForeground) {
                    startForegroundWithNotification()
                }
            }
        }

        // START_STICKY ensures the service is restarted if killed
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Start foreground when bound to ensure persistence
        if (!isRunningInForeground) {
            startForegroundWithNotification()
        }
        return super.onBind(intent)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Smart Messaging Assistant connected!")

        // Ensure we're running in foreground for persistence
        if (!isRunningInForeground) {
            startForegroundWithNotification()
        }

        // Register broadcast receiver for AI replies
        val filter = IntentFilter(REPLY_GENERATED_ACTION)
        localBroadcastManager.registerReceiver(replyReceiver, filter)

        // Notify health manager that service is connected
        com.example.jawafai.managers.NotificationHealthManager.recordServiceConnected(this)
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

        // Notify health manager that service disconnected
        com.example.jawafai.managers.NotificationHealthManager.recordServiceDisconnected(this)

        // Request rebind to reconnect
        requestRebind(android.content.ComponentName(this, JawafaiNotificationListenerService::class.java))
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy - will be restarted due to START_STICKY")
    }

    /**
     * Create the notification channel for foreground service
     */
    private fun createForegroundNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                FOREGROUND_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // Low importance = no sound, minimal visibility
            ).apply {
                description = "Keeps Jawaf.AI running to capture your messages"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Created foreground notification channel")
        }
    }

    /**
     * Start running as a foreground service with persistent notification
     */
    private fun startForegroundWithNotification() {
        try {
            val notification = buildForegroundNotification()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ requires specific foreground service type
                startForeground(
                    FOREGROUND_SERVICE_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10-13: Use data sync type as fallback
                startForeground(
                    FOREGROUND_SERVICE_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                // Android 9 and below
                startForeground(FOREGROUND_SERVICE_ID, notification)
            }

            isRunningInForeground = true
            Log.d(TAG, "✅ Started foreground service with persistent notification")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start foreground: ${e.message}")
            // Fallback: try without service type
            try {
                val notification = buildForegroundNotification()
                @Suppress("DEPRECATION")
                startForeground(FOREGROUND_SERVICE_ID, notification)
                isRunningInForeground = true
                Log.d(TAG, "✅ Started foreground service (fallback)")
            } catch (e2: Exception) {
                Log.e(TAG, "❌ Fallback also failed: ${e2.message}")
            }
        }
    }

    /**
     * Build the persistent foreground notification
     */
    private fun buildForegroundNotification(): Notification {
        // Intent to open the app when notification is tapped
        val openAppIntent = Intent(this, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setContentTitle("Jawaf.AI Active")
            .setContentText("Listening for messages in background")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .setShowWhen(false)
            .build()
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

                // Record notification received for health monitoring
                com.example.jawafai.managers.NotificationHealthManager.recordNotificationReceived(this)

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
