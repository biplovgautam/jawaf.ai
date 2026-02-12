package com.example.jawafai.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.jawafai.managers.NotificationHealthManager
import com.example.jawafai.service.JawafaiNotificationListenerService
import com.example.jawafai.service.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Boot Receiver for Self-Healing Service Restart
 *
 * Receives ACTION_BOOT_COMPLETED and ACTION_LOCKED_BOOT_COMPLETED broadcasts
 * to restart the JawafaiNotificationListenerService after device boot.
 *
 * This ensures 24/7 persistence even after device restarts.
 * Also reschedules all reminder notifications.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received broadcast: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                handleBootCompleted(context)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // App was updated, restart service
                Log.d(TAG, "App updated - restarting service")
                handleBootCompleted(context)
            }
        }
    }

    private fun handleBootCompleted(context: Context) {
        Log.d(TAG, "📱 Device booted - initializing Jawaf.AI services")

        try {
            // Initialize NotificationHealthManager
            NotificationHealthManager.initialize(context)
            Log.d(TAG, "✅ NotificationHealthManager initialized")

            // Check if notification access is enabled
            if (NotificationHealthManager.isNotificationAccessEnabled(context)) {
                // Start the notification listener as foreground service
                JawafaiNotificationListenerService.startForegroundService(context)
                Log.d(TAG, "✅ Requested foreground service start")
            } else {
                Log.w(TAG, "⚠️ Notification access not enabled - cannot start service")
                // Show reminder to enable notification access
                NotificationHealthManager.showHealthReminder(
                    context,
                    NotificationHealthManager.HealthStatus.NO_ACCESS
                )
            }

            // Schedule health check workers
            NotificationHealthManager.scheduleHealthCheckWorker(context)
            Log.d(TAG, "✅ Health check workers scheduled")

            // Reschedule all reminder notifications
            rescheduleReminders(context)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during boot initialization: ${e.message}", e)
        }
    }

    /**
     * Reschedule all upcoming reminders after device boot
     */
    private fun rescheduleReminders(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "📅 Rescheduling all reminder notifications...")
                ReminderScheduler.rescheduleAllReminders(context)
                Log.d(TAG, "✅ Reminder notifications rescheduled")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to reschedule reminders: ${e.message}", e)
            }
        }
    }
}

