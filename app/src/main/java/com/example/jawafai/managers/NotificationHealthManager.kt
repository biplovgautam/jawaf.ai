package com.example.jawafai.managers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.example.jawafai.R
import com.example.jawafai.service.JawafaiNotificationListenerService
import com.example.jawafai.view.dashboard.DashboardActivity
import java.util.concurrent.TimeUnit

/**
 * Enhanced Notification Health Manager with Active Monitoring
 * - Tracks last notification received time
 * - Monitors if NotificationListenerService is bound
 * - Sends high-priority reminders if service disconnected
 * - Uses WorkManager for 15-minute periodic checks
 * - Actively nudges system to reconnect service
 */
object NotificationHealthManager {

    private const val TAG = "NotificationHealth"
    private const val PREFS_NAME = "notification_health_prefs"
    private const val KEY_LAST_NOTIFICATION_TIME = "last_notification_time"
    private const val KEY_LAST_HEALTH_CHECK = "last_health_check"
    private const val KEY_NOTIFICATION_COUNT_TODAY = "notification_count_today"
    private const val KEY_LAST_COUNT_RESET_DATE = "last_count_reset_date"
    private const val KEY_SERVICE_CONNECTED = "service_connected"
    private const val KEY_LAST_SERVICE_CONNECT_TIME = "last_service_connect_time"
    private const val KEY_SERVICE_DISCONNECT_COUNT = "service_disconnect_count"

    private const val HEALTH_CHECK_CHANNEL_ID = "jawafai_health_check"
    private const val HEALTH_CHECK_NOTIFICATION_ID = 9999
    private const val SERVICE_ALERT_NOTIFICATION_ID = 9998

    // Work tags
    private const val HEALTH_CHECK_WORK_TAG = "notification_health_check"
    private const val SERVICE_MONITOR_WORK_TAG = "service_monitor_check"

    // Time thresholds
    private const val WARNING_THRESHOLD_MS = 30 * 60 * 1000L // 30 minutes
    private const val CRITICAL_THRESHOLD_MS = 60 * 60 * 1000L // 1 hour
    private const val HEALTH_CHECK_INTERVAL_MINUTES = 15L // Check every 15 minutes

    private var prefs: SharedPreferences? = null

    @Volatile
    private var isServiceCurrentlyConnected = false

    /**
     * Initialize the health manager
     */
    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        createNotificationChannels(context)
        scheduleHealthCheckWorker(context)
        scheduleServiceMonitorWorker(context)

        // Attempt to start foreground service on init
        tryStartForegroundService(context)

        Log.d(TAG, "NotificationHealthManager initialized with enhanced monitoring")
    }

    /**
     * Try to start the notification listener as foreground service
     */
    fun tryStartForegroundService(context: Context) {
        try {
            if (isNotificationAccessEnabled(context)) {
                JawafaiNotificationListenerService.startForegroundService(context)
                Log.d(TAG, "Requested foreground service start")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}")
        }
    }

    /**
     * Record that the NotificationListenerService has connected
     */
    fun recordServiceConnected(context: Context) {
        isServiceCurrentlyConnected = true
        prefs?.edit()?.apply {
            putBoolean(KEY_SERVICE_CONNECTED, true)
            putLong(KEY_LAST_SERVICE_CONNECT_TIME, System.currentTimeMillis())
            apply()
        }
        Log.d(TAG, "✅ Service connected recorded")

        // Cancel any outstanding service alerts
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(SERVICE_ALERT_NOTIFICATION_ID)
    }

    /**
     * Record that the NotificationListenerService has disconnected
     */
    fun recordServiceDisconnected(context: Context) {
        isServiceCurrentlyConnected = false
        val disconnectCount = (prefs?.getInt(KEY_SERVICE_DISCONNECT_COUNT, 0) ?: 0) + 1
        prefs?.edit()?.apply {
            putBoolean(KEY_SERVICE_CONNECTED, false)
            putInt(KEY_SERVICE_DISCONNECT_COUNT, disconnectCount)
            apply()
        }
        Log.w(TAG, "⚠️ Service disconnected (count: $disconnectCount)")

        // Show alert if service keeps disconnecting
        if (disconnectCount >= 2) {
            showServiceDisconnectedAlert(context)
        }
    }

    /**
     * Check if the NotificationListenerService is currently bound
     */
    fun isServiceBound(context: Context): Boolean {
        // First check our cached state
        if (isServiceCurrentlyConnected) return true

        // Then verify with system
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false

        if (!enabledListeners.contains(context.packageName)) {
            return false
        }

        // Check if service is actually running by checking component state
        val componentName = ComponentName(context, JawafaiNotificationListenerService::class.java)
        return try {
            val pm = context.packageManager
            val serviceInfo = pm.getServiceInfo(componentName, 0)
            serviceInfo != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Record that a notification was received
     */
    fun recordNotificationReceived(context: Context) {
        val now = System.currentTimeMillis()
        prefs?.edit()?.apply {
            putLong(KEY_LAST_NOTIFICATION_TIME, now)
            putBoolean(KEY_SERVICE_CONNECTED, true) // If we got a notification, service is connected

            // Increment daily count
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date())
            val lastResetDate = prefs?.getString(KEY_LAST_COUNT_RESET_DATE, "") ?: ""

            if (lastResetDate != today) {
                putInt(KEY_NOTIFICATION_COUNT_TODAY, 1)
                putString(KEY_LAST_COUNT_RESET_DATE, today)
            } else {
                val currentCount = prefs?.getInt(KEY_NOTIFICATION_COUNT_TODAY, 0) ?: 0
                putInt(KEY_NOTIFICATION_COUNT_TODAY, currentCount + 1)
            }

            apply()
        }

        isServiceCurrentlyConnected = true
        Log.d(TAG, "Recorded notification received at $now")
    }

    /**
     * Get time since last notification in milliseconds
     */
    fun getTimeSinceLastNotification(): Long {
        val lastTime = prefs?.getLong(KEY_LAST_NOTIFICATION_TIME, 0L) ?: 0L
        return if (lastTime > 0) System.currentTimeMillis() - lastTime else -1L
    }

    /**
     * Get notification count for today
     */
    fun getTodayNotificationCount(): Int {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
        val lastResetDate = prefs?.getString(KEY_LAST_COUNT_RESET_DATE, "") ?: ""

        return if (lastResetDate == today) {
            prefs?.getInt(KEY_NOTIFICATION_COUNT_TODAY, 0) ?: 0
        } else {
            0
        }
    }

    /**
     * Check notification listener health status
     */
    fun checkHealth(context: Context): HealthStatus {
        // Check if notification access is enabled
        if (!isNotificationAccessEnabled(context)) {
            return HealthStatus.NO_ACCESS
        }

        // Check if service is bound
        if (!isServiceBound(context)) {
            return HealthStatus.SERVICE_DISCONNECTED
        }

        val timeSinceLastNotification = getTimeSinceLastNotification()

        return when {
            timeSinceLastNotification < 0 -> HealthStatus.NO_DATA
            timeSinceLastNotification > CRITICAL_THRESHOLD_MS -> HealthStatus.CRITICAL
            timeSinceLastNotification > WARNING_THRESHOLD_MS -> HealthStatus.WARNING
            else -> HealthStatus.HEALTHY
        }
    }

    /**
     * Check if notification access is enabled
     */
    fun isNotificationAccessEnabled(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabledListeners.contains(context.packageName)
    }

    /**
     * Show high-priority alert when service is disconnected
     */
    private fun showServiceDisconnectedAlert(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent to open notification settings
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, HEALTH_CHECK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🔴 Jawaf.AI Service Stopped")
            .setContentText("Message listening has stopped. Tap to re-enable.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Jawaf.AI can no longer capture your messages. Please re-enable notification access to continue using smart replies."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Enable Now",
                pendingIntent
            )
            .build()

        notificationManager.notify(SERVICE_ALERT_NOTIFICATION_ID, notification)
        Log.d(TAG, "Showed service disconnected alert")
    }

    /**
     * Show health reminder notification
     */
    fun showHealthReminder(context: Context, status: HealthStatus) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val (title, message, priority) = when (status) {
            HealthStatus.NO_ACCESS -> Triple(
                "⚠️ Notification Access Disabled",
                "Jawaf.AI can't read your messages. Tap to enable notification access.",
                NotificationCompat.PRIORITY_HIGH
            )
            HealthStatus.SERVICE_DISCONNECTED -> Triple(
                "🔴 Service Disconnected",
                "Message listening stopped. Tap to reconnect.",
                NotificationCompat.PRIORITY_HIGH
            )
            HealthStatus.CRITICAL -> Triple(
                "🔴 No Messages in 1+ Hour",
                "Haven't received any messages? Tap to check if everything is working.",
                NotificationCompat.PRIORITY_DEFAULT
            )
            HealthStatus.WARNING -> Triple(
                "🟡 No Messages in 30+ Minutes",
                "It's been quiet! Make sure Jawaf.AI is working properly.",
                NotificationCompat.PRIORITY_LOW
            )
            HealthStatus.NO_DATA -> Triple(
                "👋 Waiting for First Message",
                "Jawaf.AI is ready! Messages will appear once your friends text you.",
                NotificationCompat.PRIORITY_LOW
            )
            HealthStatus.HEALTHY -> return
        }

        // Create intent based on status
        val intent = when (status) {
            HealthStatus.NO_ACCESS, HealthStatus.SERVICE_DISCONNECTED ->
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            else -> Intent(context, DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, HEALTH_CHECK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(HEALTH_CHECK_NOTIFICATION_ID, notification)
        Log.d(TAG, "Showed health reminder: $status")
    }

    /**
     * Create notification channels for health checks
     */
    private fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Health check channel (normal priority)
            val healthChannel = NotificationChannel(
                HEALTH_CHECK_CHANNEL_ID,
                "Notification Health",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders about notification listener status"
            }
            notificationManager.createNotificationChannel(healthChannel)
        }
    }

    /**
     * Schedule periodic health check using WorkManager (every 15 minutes)
     */
    fun scheduleHealthCheckWorker(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(false) // Run even on low battery
            .build()

        val healthCheckRequest = PeriodicWorkRequestBuilder<NotificationHealthWorker>(
            HEALTH_CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInitialDelay(5, TimeUnit.MINUTES)
            .addTag(HEALTH_CHECK_WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            HEALTH_CHECK_WORK_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            healthCheckRequest
        )

        Log.d(TAG, "Scheduled health check worker (every ${HEALTH_CHECK_INTERVAL_MINUTES} min)")
    }

    /**
     * Schedule service monitor worker (checks if service is bound)
     */
    private fun scheduleServiceMonitorWorker(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(false)
            .build()

        val monitorRequest = PeriodicWorkRequestBuilder<ServiceMonitorWorker>(
            HEALTH_CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInitialDelay(2, TimeUnit.MINUTES)
            .addTag(SERVICE_MONITOR_WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SERVICE_MONITOR_WORK_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            monitorRequest
        )

        Log.d(TAG, "Scheduled service monitor worker")
    }

    /**
     * Cancel all health check workers
     */
    fun cancelHealthCheckWorker(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(HEALTH_CHECK_WORK_TAG)
        WorkManager.getInstance(context).cancelUniqueWork(SERVICE_MONITOR_WORK_TAG)
    }

    /**
     * Force an immediate health check
     */
    fun forceHealthCheck(context: Context) {
        val oneTimeRequest = OneTimeWorkRequestBuilder<NotificationHealthWorker>()
            .build()
        WorkManager.getInstance(context).enqueue(oneTimeRequest)
    }

    enum class HealthStatus {
        HEALTHY,
        WARNING,
        CRITICAL,
        NO_ACCESS,
        NO_DATA,
        SERVICE_DISCONNECTED
    }
}

/**
 * WorkManager worker for periodic health checks
 */
class NotificationHealthWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        Log.d("NotificationHealthWorker", "Running health check...")

        val status = NotificationHealthManager.checkHealth(applicationContext)

        when (status) {
            NotificationHealthManager.HealthStatus.WARNING,
            NotificationHealthManager.HealthStatus.CRITICAL,
            NotificationHealthManager.HealthStatus.NO_ACCESS,
            NotificationHealthManager.HealthStatus.SERVICE_DISCONNECTED -> {
                NotificationHealthManager.showHealthReminder(applicationContext, status)
            }
            else -> {
                Log.d("NotificationHealthWorker", "Health status: $status - no action needed")
            }
        }

        return Result.success()
    }
}

/**
 * WorkManager worker for monitoring if NotificationListenerService is bound
 */
class ServiceMonitorWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        Log.d("ServiceMonitorWorker", "Checking if service is bound...")

        val context = applicationContext

        // Check if notification access is enabled
        if (!NotificationHealthManager.isNotificationAccessEnabled(context)) {
            Log.w("ServiceMonitorWorker", "Notification access not enabled")
            NotificationHealthManager.showHealthReminder(
                context,
                NotificationHealthManager.HealthStatus.NO_ACCESS
            )
            return Result.success()
        }

        // Check if service is bound
        if (!NotificationHealthManager.isServiceBound(context)) {
            Log.w("ServiceMonitorWorker", "Service not bound - attempting restart")

            // Try to restart foreground service
            NotificationHealthManager.tryStartForegroundService(context)

            // If still not bound after attempt, show alert
            if (!NotificationHealthManager.isServiceBound(context)) {
                NotificationHealthManager.showHealthReminder(
                    context,
                    NotificationHealthManager.HealthStatus.SERVICE_DISCONNECTED
                )
            }
        } else {
            Log.d("ServiceMonitorWorker", "Service is bound and running")
        }

        return Result.success()
    }
}

