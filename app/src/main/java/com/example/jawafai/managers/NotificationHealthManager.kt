package com.example.jawafai.managers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.jawafai.R
import com.example.jawafai.view.dashboard.DashboardActivity
import java.util.concurrent.TimeUnit

/**
 * Manages notification listener health monitoring
 * - Tracks last notification received time
 * - Sends reminders if no notifications for 30min/1hr
 * - Monitors if notification access is enabled
 */
object NotificationHealthManager {

    private const val TAG = "NotificationHealth"
    private const val PREFS_NAME = "notification_health_prefs"
    private const val KEY_LAST_NOTIFICATION_TIME = "last_notification_time"
    private const val KEY_LAST_HEALTH_CHECK = "last_health_check"
    private const val KEY_NOTIFICATION_COUNT_TODAY = "notification_count_today"
    private const val KEY_LAST_COUNT_RESET_DATE = "last_count_reset_date"

    private const val HEALTH_CHECK_CHANNEL_ID = "jawafai_health_check"
    private const val HEALTH_CHECK_NOTIFICATION_ID = 9999

    // Time thresholds
    private const val WARNING_THRESHOLD_MS = 30 * 60 * 1000L // 30 minutes
    private const val CRITICAL_THRESHOLD_MS = 60 * 60 * 1000L // 1 hour

    private var prefs: SharedPreferences? = null

    /**
     * Initialize the health manager
     */
    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        createNotificationChannel(context)
        scheduleHealthCheckWorker(context)
        Log.d(TAG, "NotificationHealthManager initialized")
    }

    /**
     * Record that a notification was received
     */
    fun recordNotificationReceived(context: Context) {
        val now = System.currentTimeMillis()
        prefs?.edit()?.apply {
            putLong(KEY_LAST_NOTIFICATION_TIME, now)

            // Increment daily count
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date())
            val lastResetDate = prefs?.getString(KEY_LAST_COUNT_RESET_DATE, "") ?: ""

            if (lastResetDate != today) {
                // New day, reset count
                putInt(KEY_NOTIFICATION_COUNT_TODAY, 1)
                putString(KEY_LAST_COUNT_RESET_DATE, today)
            } else {
                val currentCount = prefs?.getInt(KEY_NOTIFICATION_COUNT_TODAY, 0) ?: 0
                putInt(KEY_NOTIFICATION_COUNT_TODAY, currentCount + 1)
            }

            apply()
        }

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

        val timeSinceLastNotification = getTimeSinceLastNotification()

        return when {
            timeSinceLastNotification < 0 -> HealthStatus.NO_DATA // Never received
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
     * Show health reminder notification
     */
    fun showHealthReminder(context: Context, status: HealthStatus) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val (title, message) = when (status) {
            HealthStatus.NO_ACCESS -> Pair(
                "⚠️ Notification Access Disabled",
                "Jawaf.AI can't read your messages. Tap to enable notification access."
            )
            HealthStatus.CRITICAL -> Pair(
                "🔴 No Messages in 1+ Hour",
                "Haven't received any messages? Tap to check if everything is working."
            )
            HealthStatus.WARNING -> Pair(
                "🟡 No Messages in 30+ Minutes",
                "It's been quiet! Make sure Jawaf.AI is working properly."
            )
            HealthStatus.NO_DATA -> Pair(
                "👋 Waiting for First Message",
                "Jawaf.AI is ready! Messages will appear once your friends text you."
            )
            HealthStatus.HEALTHY -> return // Don't show notification if healthy
        }

        // Create intent to open app
        val intent = Intent(context, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_settings", status == HealthStatus.NO_ACCESS)
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
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(HEALTH_CHECK_NOTIFICATION_ID, notification)
        Log.d(TAG, "Showed health reminder: $status")
    }

    /**
     * Create notification channel for health checks
     */
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                HEALTH_CHECK_CHANNEL_ID,
                "Notification Health",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders about notification listener status"
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Schedule periodic health check using WorkManager
     */
    fun scheduleHealthCheckWorker(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        // Run health check every 30 minutes
        val healthCheckRequest = PeriodicWorkRequestBuilder<NotificationHealthWorker>(
            30, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInitialDelay(30, TimeUnit.MINUTES) // Start after 30 min
            .addTag("notification_health_check")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "notification_health_check",
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing if already scheduled
            healthCheckRequest
        )

        Log.d(TAG, "Scheduled health check worker")
    }

    /**
     * Cancel health check worker
     */
    fun cancelHealthCheckWorker(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("notification_health_check")
    }

    enum class HealthStatus {
        HEALTHY,        // Received notification recently
        WARNING,        // No notification for 30+ minutes
        CRITICAL,       // No notification for 1+ hour
        NO_ACCESS,      // Notification access disabled
        NO_DATA         // Never received any notification
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

        // Only show reminder for warning/critical/no_access states
        when (status) {
            NotificationHealthManager.HealthStatus.WARNING,
            NotificationHealthManager.HealthStatus.CRITICAL,
            NotificationHealthManager.HealthStatus.NO_ACCESS -> {
                NotificationHealthManager.showHealthReminder(applicationContext, status)
            }
            else -> {
                Log.d("NotificationHealthWorker", "Health status: $status - no action needed")
            }
        }

        return Result.success()
    }
}

