package com.example.jawafai.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver for handling notification action buttons (Mark Done, Snooze)
 */
class ReminderActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderActionReceiver"

        const val ACTION_MARK_DONE = "com.example.jawafai.ACTION_MARK_DONE"
        const val ACTION_SNOOZE = "com.example.jawafai.ACTION_SNOOZE"

        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DESCRIPTION = "extra_description"
        const val EXTRA_EVENT_TIME = "extra_event_time"
        const val EXTRA_EVENT_TYPE = "extra_event_type"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "📲 Reminder action received: ${intent.action}")

        when (intent.action) {
            ACTION_MARK_DONE -> handleMarkDone(context, intent)
            ACTION_SNOOZE -> handleSnooze(context, intent)
        }
    }

    private fun handleMarkDone(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return

        Log.d(TAG, "✅ Marking reminder as done: $reminderId")

        // Cancel the notification
        ReminderNotificationManager.cancelNotification(context, reminderId)

        // Mark as completed in Firebase
        CoroutineScope(Dispatchers.IO).launch {
            try {
                com.example.jawafai.managers.ReminderFirebaseManager.markAsCompleted(reminderId)
                Log.d(TAG, "✅ Reminder marked as completed in Firebase")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to mark reminder as completed: ${e.message}", e)
            }
        }
    }

    private fun handleSnooze(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Reminder"
        val description = intent.getStringExtra(EXTRA_DESCRIPTION) ?: ""
        val eventTime = intent.getLongExtra(EXTRA_EVENT_TIME, System.currentTimeMillis())
        val eventType = intent.getStringExtra(EXTRA_EVENT_TYPE) ?: "OTHER"

        Log.d(TAG, "⏰ Snoozing reminder: $title")

        // Cancel current notification
        ReminderNotificationManager.cancelNotification(context, reminderId)

        // Schedule snooze (10 minutes from now)
        ReminderScheduler.scheduleSnooze(
            context = context,
            reminderId = reminderId,
            title = title,
            description = description,
            originalEventTime = eventTime,
            eventType = eventType
        )

        // Show a toast (optional - using a simple log for now)
        Log.d(TAG, "⏰ Reminder snoozed for 10 minutes")
    }
}

