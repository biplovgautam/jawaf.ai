package com.example.jawafai.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Reminder data model for storing events in Firebase Realtime Database
 */
data class Reminder(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val eventDate: Long = 0L, // Timestamp in milliseconds
    val reminderTime: Long = 0L, // When to remind (can be before event)
    val eventType: String = EventType.OTHER.name,
    val source: String = ReminderSource.MANUAL.name, // Where this reminder came from
    val sourceConversationId: String = "", // If from a conversation
    val isCompleted: Boolean = false,
    val isNotified: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val color: String = "#1BC994" // Default accent color
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "userId" to userId,
            "title" to title,
            "description" to description,
            "eventDate" to eventDate,
            "reminderTime" to reminderTime,
            "eventType" to eventType,
            "source" to source,
            "sourceConversationId" to sourceConversationId,
            "isCompleted" to isCompleted,
            "isNotified" to isNotified,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
            "color" to color
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): Reminder {
            return Reminder(
                id = map["id"] as? String ?: "",
                userId = map["userId"] as? String ?: "",
                title = map["title"] as? String ?: "",
                description = map["description"] as? String ?: "",
                eventDate = (map["eventDate"] as? Number)?.toLong() ?: 0L,
                reminderTime = (map["reminderTime"] as? Number)?.toLong() ?: 0L,
                eventType = map["eventType"] as? String ?: EventType.OTHER.name,
                source = map["source"] as? String ?: ReminderSource.MANUAL.name,
                sourceConversationId = map["sourceConversationId"] as? String ?: "",
                isCompleted = map["isCompleted"] as? Boolean ?: false,
                isNotified = map["isNotified"] as? Boolean ?: false,
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                color = map["color"] as? String ?: "#1BC994"
            )
        }
    }

    fun getFormattedDate(): String {
        val dateTime = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(eventDate),
            ZoneId.systemDefault()
        )
        return dateTime.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy"))
    }

    fun getFormattedTime(): String {
        val dateTime = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(eventDate),
            ZoneId.systemDefault()
        )
        return dateTime.format(DateTimeFormatter.ofPattern("h:mm a"))
    }

    fun getLocalDate(): LocalDate {
        return LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(eventDate),
            ZoneId.systemDefault()
        ).toLocalDate()
    }

    fun getLocalTime(): LocalTime {
        return LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(eventDate),
            ZoneId.systemDefault()
        ).toLocalTime()
    }
}

/**
 * Detected reminder intent - temporary model before user confirmation
 */
data class DetectedReminderIntent(
    val title: String = "",
    val description: String = "",
    val detectedDateTime: LocalDateTime? = null,
    val eventType: EventType = EventType.OTHER,
    val confidence: Float = 0f, // 0.0 to 1.0
    val sourceMessage: String = "", // Original message that triggered detection
    val source: ReminderSource = ReminderSource.CHAT_NOTIFICATION,
    val sourceConversationId: String = "",
    val rawDateTimeText: String = "" // The actual text that was parsed (e.g., "tomorrow at 11am")
) {
    fun toReminder(userId: String): Reminder {
        val eventTimestamp = detectedDateTime?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
            ?: System.currentTimeMillis()

        // Set reminder 30 minutes before event by default
        val reminderTimestamp = eventTimestamp - (30 * 60 * 1000)

        return Reminder(
            id = java.util.UUID.randomUUID().toString(),
            userId = userId,
            title = title,
            description = description,
            eventDate = eventTimestamp,
            reminderTime = reminderTimestamp,
            eventType = eventType.name,
            source = source.name,
            sourceConversationId = sourceConversationId,
            color = eventType.defaultColor
        )
    }

    fun getFormattedDateTime(): String {
        return detectedDateTime?.format(DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a"))
            ?: "Time not detected"
    }
}

/**
 * Event types for categorization
 */
enum class EventType(val displayName: String, val defaultColor: String) {
    MEETING("Meeting", "#4285F4"),
    WORK("Work", "#FBBC04"),
    PERSONAL("Personal", "#34A853"),
    HEALTH("Health", "#EA4335"),
    SPORTS("Sports", "#FF6D00"),
    SOCIAL("Social", "#9C27B0"),
    REMINDER("Reminder", "#1BC994"),
    OTHER("Other", "#757575");

    companion object {
        fun fromKeywords(text: String): EventType {
            val lowerText = text.lowercase()
            return when {
                lowerText.containsAny("meeting", "call", "conference", "interview", "sync") -> MEETING
                lowerText.containsAny("work", "office", "project", "deadline", "task", "report") -> WORK
                lowerText.containsAny("doctor", "hospital", "medicine", "health", "checkup", "appointment") -> HEALTH
                lowerText.containsAny("futsal", "football", "gym", "workout", "game", "match", "sports", "cricket", "basketball") -> SPORTS
                lowerText.containsAny("party", "birthday", "dinner", "lunch", "hangout", "meet", "friend") -> SOCIAL
                lowerText.containsAny("remind", "remember", "don't forget", "note") -> REMINDER
                lowerText.containsAny("personal", "home", "family") -> PERSONAL
                else -> OTHER
            }
        }

        private fun String.containsAny(vararg keywords: String): Boolean {
            return keywords.any { this.contains(it, ignoreCase = true) }
        }
    }
}

/**
 * Source of the reminder
 */
enum class ReminderSource(val displayName: String) {
    MANUAL("Manually Created"),
    CHAT_NOTIFICATION("From Chat Message"),
    CHATBOT("From AI Companion"),
    CALENDAR_IMPORT("Imported from Calendar")
}

