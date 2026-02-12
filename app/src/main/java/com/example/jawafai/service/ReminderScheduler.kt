package com.example.jawafai.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.jawafai.model.Reminder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Schedules reminder alarms using AlarmManager
 * Reminders are scheduled to trigger 5 minutes before the event time
 */
object ReminderScheduler {

    private const val TAG = "ReminderScheduler"

    // Time before event to send notification (5 minutes in milliseconds)
    const val NOTIFICATION_ADVANCE_TIME_MS = 5 * 60 * 1000L // 5 minutes

    // Snooze duration (10 minutes)
    const val SNOOZE_DURATION_MS = 10 * 60 * 1000L // 10 minutes

    /**
     * Schedule a reminder notification
     * Will trigger 5 minutes before the event time
     */
    fun scheduleReminder(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Calculate notification time (5 minutes before event)
        val notificationTime = reminder.eventDate - NOTIFICATION_ADVANCE_TIME_MS

        // Don't schedule if the notification time has already passed
        if (notificationTime <= System.currentTimeMillis()) {
            Log.d(TAG, "⏭️ Skipping past reminder: ${reminder.title}")
            return
        }

        val intent = createReminderIntent(context, reminder)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Schedule the alarm
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ requires checking if exact alarms are allowed
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        notificationTime,
                        pendingIntent
                    )
                } else {
                    // Fallback to inexact alarm
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        notificationTime,
                        pendingIntent
                    )
                    Log.w(TAG, "⚠️ Exact alarms not allowed, using inexact alarm")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    notificationTime,
                    pendingIntent
                )
            }

            val eventDateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(reminder.eventDate),
                ZoneId.systemDefault()
            )
            val notifyDateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(notificationTime),
                ZoneId.systemDefault()
            )

            Log.d(TAG, "✅ Reminder scheduled: ${reminder.title}")
            Log.d(TAG, "   Event time: ${eventDateTime.format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))}")
            Log.d(TAG, "   Notify at: ${notifyDateTime.format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))}")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to schedule reminder: ${e.message}", e)
        }
    }

    /**
     * Schedule a snoozed reminder (10 minutes from now)
     */
    fun scheduleSnooze(
        context: Context,
        reminderId: String,
        title: String,
        description: String,
        originalEventTime: Long,
        eventType: String
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val snoozeTime = System.currentTimeMillis() + SNOOZE_DURATION_MS

        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_SHOW_REMINDER
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminderId)
            putExtra(ReminderAlarmReceiver.EXTRA_TITLE, title)
            putExtra(ReminderAlarmReceiver.EXTRA_DESCRIPTION, description)
            putExtra(ReminderAlarmReceiver.EXTRA_EVENT_TIME, originalEventTime)
            putExtra(ReminderAlarmReceiver.EXTRA_EVENT_TYPE, eventType)
            putExtra(ReminderAlarmReceiver.EXTRA_IS_SNOOZE, true)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode() + 1000, // Different request code for snooze
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeTime, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeTime, pendingIntent)
            }

            Log.d(TAG, "⏰ Reminder snoozed for 10 minutes: $title")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to schedule snooze: ${e.message}", e)
        }
    }

    /**
     * Cancel a scheduled reminder
     */
    fun cancelReminder(context: Context, reminderId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_SHOW_REMINDER
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "🚫 Reminder cancelled: $reminderId")
    }

    /**
     * Reschedule all active reminders (e.g., after device reboot)
     */
    suspend fun rescheduleAllReminders(context: Context) {
        try {
            val result = com.example.jawafai.managers.ReminderFirebaseManager.getUpcomingReminders()
            result.onSuccess { reminders ->
                Log.d(TAG, "📅 Rescheduling ${reminders.size} reminders...")
                reminders.forEach { reminder ->
                    if (!reminder.isCompleted) {
                        scheduleReminder(context, reminder)
                    }
                }
                Log.d(TAG, "✅ All reminders rescheduled")
            }
            result.onFailure { error ->
                Log.e(TAG, "❌ Failed to fetch reminders for rescheduling: ${error.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error rescheduling reminders: ${e.message}", e)
        }
    }

    /**
     * Create the intent for reminder alarm
     */
    private fun createReminderIntent(context: Context, reminder: Reminder): Intent {
        return Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_SHOW_REMINDER
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminder.id)
            putExtra(ReminderAlarmReceiver.EXTRA_TITLE, reminder.title)
            putExtra(ReminderAlarmReceiver.EXTRA_DESCRIPTION, reminder.description)
            putExtra(ReminderAlarmReceiver.EXTRA_EVENT_TIME, reminder.eventDate)
            putExtra(ReminderAlarmReceiver.EXTRA_EVENT_TYPE, reminder.eventType)
        }
    }
}

