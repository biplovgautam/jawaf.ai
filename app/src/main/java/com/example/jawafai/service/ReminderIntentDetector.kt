package com.example.jawafai.service

import android.util.Log
import com.example.jawafai.managers.GroqApiManager
import com.example.jawafai.model.DetectedReminderIntent
import com.example.jawafai.model.EventType
import com.example.jawafai.model.ReminderSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

/**
 * Intent Detection Service for identifying reminder-worthy events from conversations
 */
object ReminderIntentDetector {

    private const val TAG = "ReminderIntentDetector"

    // Common time-related keywords
    private val TIME_KEYWORDS = listOf(
        "today", "tomorrow", "tonight", "morning", "afternoon", "evening", "night",
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
        "next week", "this week", "next month", "weekend", "am", "pm", "o'clock"
    )

    // Intent trigger keywords
    private val REMINDER_TRIGGERS = listOf(
        "remind", "remember", "don't forget", "note down", "schedule",
        "meeting", "appointment", "call", "event", "plan"
    )

    // Acceptance keywords
    private val ACCEPTANCE_KEYWORDS = listOf(
        "yes", "sure", "okay", "ok", "definitely", "of course", "count me in",
        "i'll be there", "i'm in", "sounds good", "let's do it", "i would love",
        "i'd love", "perfect", "great", "absolutely"
    )

    /**
     * Analyze a conversation/message for reminder intent
     */
    suspend fun detectReminderIntent(
        message: String,
        conversationContext: List<String> = emptyList(),
        source: ReminderSource = ReminderSource.CHAT_NOTIFICATION,
        conversationId: String = ""
    ): DetectedReminderIntent? = withContext(Dispatchers.IO) {

        Log.d(TAG, "🔍 Analyzing message for reminder intent: ${message.take(100)}...")

        val fullContext = buildString {
            if (conversationContext.isNotEmpty()) {
                conversationContext.takeLast(5).forEach { append("$it\n") }
            }
            append(message)
        }

        // Quick check for time reference
        if (!containsTimeReference(fullContext)) {
            Log.d(TAG, "❌ No time reference found, skipping")
            return@withContext null
        }

        val isAcceptance = containsAcceptance(message)

        if (!isAcceptance && !containsReminderTrigger(fullContext)) {
            Log.d(TAG, "❌ No reminder trigger or acceptance found")
            return@withContext null
        }

        Log.d(TAG, "✅ Potential reminder detected, extracting details...")

        // Try LLM extraction
        val extractedIntent = extractReminderWithLLM(fullContext, source, conversationId)

        if (extractedIntent != null && extractedIntent.detectedDateTime != null) {
            Log.d(TAG, "✅ Reminder intent extracted: ${extractedIntent.title}")
            return@withContext extractedIntent
        }

        // Fallback to rule-based extraction
        val ruleBasedIntent = extractReminderRuleBased(fullContext, source, conversationId)

        if (ruleBasedIntent != null) {
            Log.d(TAG, "✅ Rule-based extraction: ${ruleBasedIntent.title}")
            return@withContext ruleBasedIntent
        }

        Log.d(TAG, "❌ Could not extract valid reminder intent")
        return@withContext null
    }

    /**
     * Use LLM to extract reminder details
     */
    private suspend fun extractReminderWithLLM(
        text: String,
        source: ReminderSource,
        conversationId: String
    ): DetectedReminderIntent? {
        try {
            val currentDateTime = LocalDateTime.now()
            val currentDateStr = currentDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

            val systemPrompt = """
You are a reminder extraction assistant. Current date/time: $currentDateStr

Analyze the text and extract event/reminder details. 
Respond ONLY with JSON (no markdown):
{"found": true/false, "title": "event title", "description": "brief description", "date": "YYYY-MM-DD", "time": "HH:mm", "event_type": "MEETING/WORK/PERSONAL/HEALTH/SPORTS/SOCIAL/REMINDER/OTHER", "confidence": 0.0-1.0}

If no clear event, return: {"found": false}
            """.trimIndent()

            val messages = listOf(
                GroqApiManager.ChatMessage("system", systemPrompt),
                GroqApiManager.ChatMessage("user", "Extract reminder from: \"$text\"")
            )

            val response = GroqApiManager.sendChatRequest(messages)

            if (response.success && response.message != null) {
                return parseReminderJson(response.message, text, source, conversationId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "LLM extraction failed: ${e.message}", e)
        }
        return null
    }

    /**
     * Parse JSON response from LLM
     */
    private fun parseReminderJson(
        jsonStr: String,
        originalText: String,
        source: ReminderSource,
        conversationId: String
    ): DetectedReminderIntent? {
        try {
            val cleanJson = jsonStr.replace("```json", "").replace("```", "").trim()
            val json = JSONObject(cleanJson)

            if (!json.optBoolean("found", false)) return null

            val dateStr = json.optString("date", "")
            val timeStr = json.optString("time", "")

            if (dateStr.isBlank()) return null

            val date = try { LocalDate.parse(dateStr) } catch (e: Exception) { LocalDate.now() }
            val time = try {
                if (timeStr.isNotBlank()) LocalTime.parse(timeStr) else LocalTime.of(9, 0)
            } catch (e: Exception) { LocalTime.of(9, 0) }

            val dateTime = LocalDateTime.of(date, time)

            if (dateTime.isBefore(LocalDateTime.now())) return null

            val eventTypeStr = json.optString("event_type", "OTHER")
            val eventType = try { EventType.valueOf(eventTypeStr) } catch (e: Exception) {
                EventType.fromKeywords(json.optString("title", ""))
            }

            return DetectedReminderIntent(
                title = json.optString("title", "Event"),
                description = json.optString("description", ""),
                detectedDateTime = dateTime,
                eventType = eventType,
                confidence = json.optDouble("confidence", 0.7).toFloat(),
                sourceMessage = originalText,
                source = source,
                sourceConversationId = conversationId,
                rawDateTimeText = "$dateStr $timeStr"
            )
        } catch (e: Exception) {
            Log.e(TAG, "JSON parsing failed: ${e.message}", e)
            return null
        }
    }

    /**
     * Rule-based extraction fallback
     */
    private fun extractReminderRuleBased(
        text: String,
        source: ReminderSource,
        conversationId: String
    ): DetectedReminderIntent? {
        val lowerText = text.lowercase()
        val dateTime = extractDateTimeFromText(lowerText) ?: return null
        val title = extractEventTitle(lowerText)
        val eventType = EventType.fromKeywords(text)

        return DetectedReminderIntent(
            title = title,
            description = text.take(200),
            detectedDateTime = dateTime,
            eventType = eventType,
            confidence = 0.6f,
            sourceMessage = text,
            source = source,
            sourceConversationId = conversationId
        )
    }

    /**
     * Extract date/time from text using patterns
     */
    private fun extractDateTimeFromText(text: String): LocalDateTime? {
        val now = LocalDateTime.now()

        // Pattern: "tomorrow at 11am"
        val tomorrowPattern = Pattern.compile("tomorrow\\s+(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", Pattern.CASE_INSENSITIVE)
        val tomorrowMatcher = tomorrowPattern.matcher(text)
        if (tomorrowMatcher.find()) {
            val hour = tomorrowMatcher.group(1)?.toIntOrNull() ?: 9
            val minute = tomorrowMatcher.group(2)?.toIntOrNull() ?: 0
            val ampm = tomorrowMatcher.group(3)?.lowercase()
            val adjustedHour = adjustHourForAmPm(hour, ampm)
            return now.plusDays(1).withHour(adjustedHour).withMinute(minute).withSecond(0)
        }

        // Pattern: "today at 5pm"
        val todayPattern = Pattern.compile("today\\s+(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", Pattern.CASE_INSENSITIVE)
        val todayMatcher = todayPattern.matcher(text)
        if (todayMatcher.find()) {
            val hour = todayMatcher.group(1)?.toIntOrNull() ?: 9
            val minute = todayMatcher.group(2)?.toIntOrNull() ?: 0
            val ampm = todayMatcher.group(3)?.lowercase()
            val adjustedHour = adjustHourForAmPm(hour, ampm)
            return now.withHour(adjustedHour).withMinute(minute).withSecond(0)
        }

        // Pattern: "at 3pm"
        val timeOnlyPattern = Pattern.compile("(?:at\\s+)(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", Pattern.CASE_INSENSITIVE)
        val timeOnlyMatcher = timeOnlyPattern.matcher(text)
        if (timeOnlyMatcher.find()) {
            val hour = timeOnlyMatcher.group(1)?.toIntOrNull() ?: 9
            val minute = timeOnlyMatcher.group(2)?.toIntOrNull() ?: 0
            val ampm = timeOnlyMatcher.group(3)?.lowercase()
            val adjustedHour = adjustHourForAmPm(hour, ampm)
            var result = now.withHour(adjustedHour).withMinute(minute).withSecond(0)
            if (result.isBefore(now)) result = result.plusDays(1)
            return result
        }

        // Pattern: Day of week "Monday at 2pm"
        val dayPattern = Pattern.compile("(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\s+(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", Pattern.CASE_INSENSITIVE)
        val dayMatcher = dayPattern.matcher(text)
        if (dayMatcher.find()) {
            val dayName = dayMatcher.group(1)?.lowercase() ?: return null
            val hour = dayMatcher.group(2)?.toIntOrNull() ?: 9
            val minute = dayMatcher.group(3)?.toIntOrNull() ?: 0
            val ampm = dayMatcher.group(4)?.lowercase()
            val targetDay = getDayOfWeek(dayName) ?: return null
            val adjustedHour = adjustHourForAmPm(hour, ampm)
            var result = now.with(java.time.temporal.TemporalAdjusters.nextOrSame(targetDay))
                .withHour(adjustedHour).withMinute(minute).withSecond(0)
            if (result.isBefore(now)) result = result.plusWeeks(1)
            return result
        }

        return null
    }

    private fun adjustHourForAmPm(hour: Int, ampm: String?): Int {
        return when {
            ampm == "pm" && hour < 12 -> hour + 12
            ampm == "am" && hour == 12 -> 0
            else -> hour
        }
    }

    private fun getDayOfWeek(dayName: String): DayOfWeek? {
        return when (dayName.lowercase()) {
            "monday" -> DayOfWeek.MONDAY
            "tuesday" -> DayOfWeek.TUESDAY
            "wednesday" -> DayOfWeek.WEDNESDAY
            "thursday" -> DayOfWeek.THURSDAY
            "friday" -> DayOfWeek.FRIDAY
            "saturday" -> DayOfWeek.SATURDAY
            "sunday" -> DayOfWeek.SUNDAY
            else -> null
        }
    }

    private fun extractEventTitle(text: String): String {
        val eventKeywords = listOf(
            "futsal", "football", "meeting", "call", "appointment", "dinner",
            "lunch", "party", "workout", "gym", "class", "lecture", "interview"
        )
        for (keyword in eventKeywords) {
            if (text.contains(keyword, ignoreCase = true)) {
                return keyword.replaceFirstChar { it.uppercase() }
            }
        }
        return "Event"
    }

    private fun containsTimeReference(text: String): Boolean {
        val lowerText = text.lowercase()
        return TIME_KEYWORDS.any { lowerText.contains(it) } ||
               Pattern.compile("\\d{1,2}(:\\d{2})?\\s*(am|pm)", Pattern.CASE_INSENSITIVE).matcher(text).find()
    }

    private fun containsReminderTrigger(text: String): Boolean {
        val lowerText = text.lowercase()
        return REMINDER_TRIGGERS.any { lowerText.contains(it) }
    }

    private fun containsAcceptance(text: String): Boolean {
        val lowerText = text.lowercase()
        return ACCEPTANCE_KEYWORDS.any { lowerText.contains(it) }
    }

    /**
     * Check if a message should trigger reminder detection
     */
    fun shouldCheckForReminder(userMessage: String): Boolean {
        val lowerText = userMessage.lowercase()

        if (lowerText.contains("remind") || lowerText.contains("reminder")) {
            return true
        }

        if (containsTimeReference(userMessage) &&
            (lowerText.contains("schedule") || lowerText.contains("set") ||
             lowerText.contains("create") || lowerText.contains("add"))) {
            return true
        }

        return false
    }
}

