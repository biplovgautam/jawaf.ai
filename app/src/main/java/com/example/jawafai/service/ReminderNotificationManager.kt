package com.example.jawafai.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.jawafai.R
import com.example.jawafai.view.dashboard.DashboardActivity
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Manages reminder notifications - creating channels and showing notifications
 */
object ReminderNotificationManager {

    private const val TAG = "ReminderNotification"

    const val CHANNEL_ID = "jawafai_reminders"
    const val CHANNEL_NAME = "Reminders"
    const val CHANNEL_DESCRIPTION = "Notifications for scheduled reminders"

    private const val NOTIFICATION_GROUP = "com.example.jawafai.REMINDERS"

    /**
     * Create notification channel (required for Android O+)
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                lightColor = android.graphics.Color.parseColor("#1BC994")
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "✅ Reminder notification channel created")
        }
    }

    /**
     * Show a reminder notification
     */
    fun showReminderNotification(
        context: Context,
        reminderId: String,
        title: String,
        description: String,
        eventTime: Long,
        eventType: String
    ) {
        Log.d(TAG, "🔔 Showing reminder notification: $title")

        // Create notification channel if needed
        createNotificationChannel(context)

        // Format event time
        val eventDateTime = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(eventTime),
            java.time.ZoneId.systemDefault()
        )
        val timeStr = eventDateTime.format(DateTimeFormatter.ofPattern("h:mm a"))
        val dateStr = eventDateTime.format(DateTimeFormatter.ofPattern("EEE, MMM d"))

        // Create intent to open app when notification is tapped
        val intent = Intent(context, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_reminders", true)
            putExtra("reminder_id", reminderId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create "Mark Done" action
        val markDoneIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_MARK_DONE
            putExtra(ReminderActionReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        val markDonePendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode() + 1,
            markDoneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create "Snooze" action (snooze for 10 minutes)
        val snoozeIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_SNOOZE
            putExtra(ReminderActionReceiver.EXTRA_REMINDER_ID, reminderId)
            putExtra(ReminderActionReceiver.EXTRA_TITLE, title)
            putExtra(ReminderActionReceiver.EXTRA_DESCRIPTION, description)
            putExtra(ReminderActionReceiver.EXTRA_EVENT_TIME, eventTime)
            putExtra(ReminderActionReceiver.EXTRA_EVENT_TYPE, eventType)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode() + 2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Get notification icon based on event type
        val iconRes = getEventTypeIcon(eventType)

        // Build notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⏰ $title")
            .setContentText("Coming up at $timeStr")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$description\n\n📅 $dateStr at $timeStr"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setColor(android.graphics.Color.parseColor("#1BC994"))
            .setGroup(NOTIFICATION_GROUP)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "✓ Done",
                markDonePendingIntent
            )
            .addAction(
                R.drawable.ic_launcher_foreground,
                "⏰ Snooze 10m",
                snoozePendingIntent
            )
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(reminderId.hashCode(), notification)

        Log.d(TAG, "✅ Reminder notification shown: $title at $timeStr")
    }

    /**
     * Cancel a specific reminder notification
     */
    fun cancelNotification(context: Context, reminderId: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(reminderId.hashCode())
        Log.d(TAG, "🚫 Reminder notification cancelled: $reminderId")
    }

    /**
     * Get icon resource based on event type
     */
    private fun getEventTypeIcon(eventType: String): Int {
        // For now, use default icon - can be expanded later
        return R.drawable.ic_launcher_foreground
    }
}

