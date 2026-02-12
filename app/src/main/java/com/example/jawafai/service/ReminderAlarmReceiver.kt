package com.example.jawafai.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that receives reminder alarms and shows notifications
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderAlarmReceiver"

        const val ACTION_SHOW_REMINDER = "com.example.jawafai.ACTION_SHOW_REMINDER"

        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DESCRIPTION = "extra_description"
        const val EXTRA_EVENT_TIME = "extra_event_time"
        const val EXTRA_EVENT_TYPE = "extra_event_type"
        const val EXTRA_IS_SNOOZE = "extra_is_snooze"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "⏰ Reminder alarm received: ${intent.action}")

        when (intent.action) {
            ACTION_SHOW_REMINDER -> handleShowReminder(context, intent)
            Intent.ACTION_BOOT_COMPLETED -> handleBootCompleted(context)
            else -> Log.w(TAG, "Unknown action: ${intent.action}")
        }
    }

    private fun handleShowReminder(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: run {
            Log.e(TAG, "❌ No reminder ID in intent")
            return
        }

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Reminder"
        val description = intent.getStringExtra(EXTRA_DESCRIPTION) ?: ""
        val eventTime = intent.getLongExtra(EXTRA_EVENT_TIME, System.currentTimeMillis())
        val eventType = intent.getStringExtra(EXTRA_EVENT_TYPE) ?: "OTHER"
        val isSnooze = intent.getBooleanExtra(EXTRA_IS_SNOOZE, false)

        Log.d(TAG, "📢 Showing reminder notification: $title (snooze: $isSnooze)")

        // Show the notification
        ReminderNotificationManager.showReminderNotification(
            context = context,
            reminderId = reminderId,
            title = if (isSnooze) "⏰ $title (Snoozed)" else title,
            description = description,
            eventTime = eventTime,
            eventType = eventType
        )
    }

    private fun handleBootCompleted(context: Context) {
        Log.d(TAG, "📱 Device boot completed - rescheduling reminders")

        // Reschedule all reminders after device reboot
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.rescheduleAllReminders(context)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to reschedule reminders after boot: ${e.message}", e)
            }
        }
    }
}

