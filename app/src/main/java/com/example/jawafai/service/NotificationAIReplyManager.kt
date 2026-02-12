package com.example.jawafai.service

import android.content.Context
import android.util.Log
import com.example.jawafai.managers.GroqApiManager
import com.example.jawafai.managers.ReminderFirebaseManager
import com.example.jawafai.model.DetectedReminderIntent
import com.example.jawafai.model.Reminder
import com.example.jawafai.model.ReminderSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * AI Reply Manager for generating contextually aware replies to notifications
 * Uses GroqApiManager for LLM integration with conversation context and user persona
 */
object NotificationAIReplyManager {

    private const val TAG = "NotificationAIReply"
    private const val MAX_CONTEXT_MESSAGES = 10
    private const val DEFAULT_CONTEXT_MESSAGES = 15

    /**
     * Generate AI reply for a notification with conversation context
     */
    suspend fun generateAIReply(
        notification: NotificationMemoryStore.ExternalNotification,
        userPersona: Map<String, Any>? = null,
        context: Context
    ): AIReplyResult = withContext(Dispatchers.IO) {

        try {
            Log.d(TAG, "🤖 Generating AI reply for notification")
            Log.d(TAG, "📱 App: ${notification.packageName}")
            Log.d(TAG, "👤 Sender: ${notification.sender}")
            Log.d(TAG, "💬 Message: ${notification.text}")

            // Get conversation history for this specific sender (last 10 messages)
            val senderHistory = NotificationMemoryStore.getConversationContext(
                notification.conversationId,
                MAX_CONTEXT_MESSAGES
            )

            Log.d(TAG, "📚 Sender conversation history: ${senderHistory.size} messages")

            // Convert notification history to chat messages format for GroqApiManager
            val chatHistory = convertNotificationHistoryToChatMessages(senderHistory)

            // Get app name for context
            val appName = getAppName(notification.packageName)
            val senderName = notification.sender ?: notification.title

            // Generate reply using GroqApiManager's notification-specific method
            val groqResponse = GroqApiManager.getNotificationReply(
                currentMessage = notification.text,
                senderName = senderName,
                appName = appName,
                conversationHistory = chatHistory,
                userPersona = userPersona
            )

            if (groqResponse.success && groqResponse.message != null) {
                Log.d(TAG, "✅ AI reply generated successfully")
                Log.d(TAG, "🎯 Reply: ${groqResponse.message.take(100)}...")

                // Update notification with AI reply
                NotificationMemoryStore.updateAIReply(notification.hash, groqResponse.message)

                return@withContext AIReplyResult(
                    success = true,
                    reply = groqResponse.message,
                    error = null,
                    conversationId = notification.conversationId
                )
            } else {
                Log.e(TAG, "❌ AI reply generation failed: ${groqResponse.error}")
                return@withContext AIReplyResult(
                    success = false,
                    reply = null,
                    error = groqResponse.error ?: "Unknown error",
                    conversationId = notification.conversationId
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception in AI reply generation: ${e.message}", e)
            return@withContext AIReplyResult(
                success = false,
                reply = null,
                error = e.message ?: "Unknown error",
                conversationId = notification.conversationId
            )
        }
    }

    /**
     * Generate AI reply with enhanced context - uses conversation ID to fetch history
     * This is the preferred method for generating personalized replies
     */
    suspend fun generateAIReplyWithContext(
        notification: NotificationMemoryStore.ExternalNotification,
        conversationId: String,
        maxContextMessages: Int = DEFAULT_CONTEXT_MESSAGES,
        userPersona: Map<String, Any>? = null,
        context: Context
    ): AIReplyResult = withContext(Dispatchers.IO) {

        try {
            Log.d(TAG, "🤖 Generating AI reply with enhanced context")
            Log.d(TAG, "📱 App: ${notification.packageName}")
            Log.d(TAG, "👤 Sender: ${notification.sender}")
            Log.d(TAG, "💬 Current Message: ${notification.text}")
            Log.d(TAG, "🔑 Conversation ID: $conversationId")
            Log.d(TAG, "📊 Max Context Messages: $maxContextMessages")

            // Get conversation messages from memory store
            val conversationMessages = NotificationMemoryStore.getMessagesForConversation(conversationId)
                .takeLast(maxContextMessages)

            Log.d(TAG, "📚 Retrieved ${conversationMessages.size} messages for context")

            // Convert messages to chat format
            val chatHistory = convertMessagesToChatFormat(conversationMessages)

            // Get app name for context
            val appName = getAppName(notification.packageName)
            val senderName = notification.sender ?: notification.title

            // Build persona context string
            val personaContext = buildPersonaContext(userPersona)
            if (personaContext != null) {
                Log.d(TAG, "👤 User persona loaded with ${userPersona?.size ?: 0} attributes")
            }

            // Generate reply using GroqApiManager with enhanced context
            val groqResponse = GroqApiManager.getNotificationReplyWithPersona(
                currentMessage = notification.text,
                senderName = senderName,
                appName = appName,
                conversationHistory = chatHistory,
                personaContext = personaContext
            )

            if (groqResponse.success && groqResponse.message != null) {
                Log.d(TAG, "✅ AI reply generated successfully with context")
                Log.d(TAG, "🎯 Reply: ${groqResponse.message.take(100)}...")

                // Update message with AI reply
                NotificationMemoryStore.updateAIReply(notification.hash, groqResponse.message)

                return@withContext AIReplyResult(
                    success = true,
                    reply = groqResponse.message,
                    error = null,
                    conversationId = conversationId
                )
            } else {
                Log.e(TAG, "❌ AI reply generation failed: ${groqResponse.error}")
                return@withContext AIReplyResult(
                    success = false,
                    reply = null,
                    error = groqResponse.error ?: "Unknown error",
                    conversationId = conversationId
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception in AI reply generation: ${e.message}", e)
            return@withContext AIReplyResult(
                success = false,
                reply = null,
                error = e.message ?: "Unknown error",
                conversationId = conversationId
            )
        }
    }

    /**
     * Generate AI reply from a Message object (when notification is not available)
     */
    suspend fun generateAIReplyFromMessage(
        message: NotificationMemoryStore.Message,
        conversationId: String,
        displayName: String,
        packageName: String,
        maxContextMessages: Int = DEFAULT_CONTEXT_MESSAGES,
        userPersona: Map<String, Any>? = null,
        context: Context
    ): AIReplyResult = withContext(Dispatchers.IO) {

        try {
            Log.d(TAG, "🤖 Generating AI reply from message object")
            Log.d(TAG, "💬 Message: ${message.msg_content}")
            Log.d(TAG, "👤 Sender: ${message.sender_name}")
            Log.d(TAG, "🔑 Conversation ID: $conversationId")

            // Get conversation messages for context
            val conversationMessages = NotificationMemoryStore.getMessagesForConversation(conversationId)
                .takeLast(maxContextMessages)

            Log.d(TAG, "📚 Retrieved ${conversationMessages.size} messages for context")

            // Convert messages to chat format
            val chatHistory = convertMessagesToChatFormat(conversationMessages)

            // Get app name
            val appName = getAppName(packageName)

            // Build persona context string
            val personaContext = buildPersonaContext(userPersona)

            // Generate reply
            val groqResponse = GroqApiManager.getNotificationReplyWithPersona(
                currentMessage = message.msg_content,
                senderName = message.sender_name.ifBlank { displayName },
                appName = appName,
                conversationHistory = chatHistory,
                personaContext = personaContext
            )

            if (groqResponse.success && groqResponse.message != null) {
                Log.d(TAG, "✅ AI reply generated successfully")

                // Update message with AI reply
                NotificationMemoryStore.updateMessageAIReply(message.msg_hash, groqResponse.message)

                return@withContext AIReplyResult(
                    success = true,
                    reply = groqResponse.message,
                    error = null,
                    conversationId = conversationId
                )
            } else {
                return@withContext AIReplyResult(
                    success = false,
                    reply = null,
                    error = groqResponse.error ?: "Unknown error",
                    conversationId = conversationId
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception: ${e.message}", e)
            return@withContext AIReplyResult(
                success = false,
                reply = null,
                error = e.message ?: "Unknown error",
                conversationId = conversationId
            )
        }
    }

    /**
     * Convert Message objects to chat format for AI
     */
    private fun convertMessagesToChatFormat(
        messages: List<NotificationMemoryStore.Message>
    ): List<GroqApiManager.ChatMessage> {
        val chatMessages = mutableListOf<GroqApiManager.ChatMessage>()

        messages.forEach { message ->
            // Incoming messages as "user" role
            if (!message.is_outgoing) {
                chatMessages.add(
                    GroqApiManager.ChatMessage(
                        role = "user",
                        content = "${message.sender_name}: ${message.msg_content}"
                    )
                )
            } else {
                // Outgoing messages or AI replies as "assistant" role
                chatMessages.add(
                    GroqApiManager.ChatMessage(
                        role = "assistant",
                        content = message.msg_content
                    )
                )
            }

            // Add AI reply if available
            if (message.ai_reply.isNotBlank() && !message.is_outgoing) {
                chatMessages.add(
                    GroqApiManager.ChatMessage(
                        role = "assistant",
                        content = message.ai_reply
                    )
                )
            }
        }

        return chatMessages
    }

    /**
     * Build persona context string from user persona map
     */
    private fun buildPersonaContext(userPersona: Map<String, Any>?): String? {
        if (userPersona.isNullOrEmpty()) return null

        val personaBuilder = StringBuilder()
        personaBuilder.append("User Persona:\n")

        userPersona.forEach { (key, value) ->
            val formattedKey = key.replace("_", " ").replaceFirstChar { it.uppercase() }
            personaBuilder.append("- $formattedKey: $value\n")
        }

        return personaBuilder.toString()
    }

    /**
     * Build context-aware prompt for AI reply generation (DEPRECATED - now handled in GroqApiManager)
     */
    @Deprecated("Context building is now handled in GroqApiManager.getNotificationReply")
    private fun buildContextPrompt(
        currentNotification: NotificationMemoryStore.ExternalNotification,
        conversationHistory: List<NotificationMemoryStore.ExternalNotification>,
        userPersona: Map<String, Any>?
    ): String {
        // This method is deprecated - context building is now handled in GroqApiManager
        return currentNotification.text
    }

    /**
     * Convert notification history to chat messages format (DEPRECATED - use private method)
     */
    @Deprecated("Use private convertNotificationHistoryToChatMessages method")
    private fun convertToChatMessages(
        notifications: List<NotificationMemoryStore.ExternalNotification>
    ): List<GroqApiManager.ChatMessage> {
        return convertNotificationHistoryToChatMessages(notifications)
    }

    /**
     * Convert notification history to chat messages format for GroqApiManager
     */
    private fun convertNotificationHistoryToChatMessages(
        notifications: List<NotificationMemoryStore.ExternalNotification>
    ): List<GroqApiManager.ChatMessage> {

        val chatMessages = mutableListOf<GroqApiManager.ChatMessage>()

        // Process notifications in chronological order to build conversation context
        notifications.forEach { notification ->
            // Add the original message from sender as user message
            chatMessages.add(
                GroqApiManager.ChatMessage(
                    role = "user",
                    content = "${notification.sender ?: notification.title}: ${notification.text}"
                )
            )

            // Add AI reply if available as assistant message
            if (notification.ai_reply.isNotBlank()) {
                chatMessages.add(
                    GroqApiManager.ChatMessage(
                        role = "assistant",
                        content = notification.ai_reply
                    )
                )
            }
        }

        return chatMessages
    }

    /**
     * Get app name from package name
     */
    private fun getAppName(packageName: String): String {
        return when (packageName) {
            "com.whatsapp" -> "WhatsApp"
            "com.whatsapp.w4b" -> "WhatsApp Business"
            "com.instagram.android" -> "Instagram"
            "com.facebook.orca" -> "Facebook Messenger"
            "com.facebook.katana" -> "Facebook"
            "com.telegram.messenger" -> "Telegram"
            "com.snapchat.android" -> "Snapchat"
            "com.twitter.android" -> "Twitter"
            else -> packageName
        }
    }

    /**
     * Format timestamp for display
     */
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /**
     * Analyze conversation tone from history
     */
    fun analyzeConversationTone(
        conversationHistory: List<NotificationMemoryStore.ExternalNotification>
    ): ConversationTone {

        if (conversationHistory.isEmpty()) {
            return ConversationTone.NEUTRAL
        }

        val recentMessages = conversationHistory.take(5)
        val combinedText = recentMessages.joinToString(" ") { it.text.lowercase() }

        return when {
            combinedText.contains(Regex("(haha|lol|😂|😄|😊|funny|joke)")) -> ConversationTone.CASUAL_FUNNY
            combinedText.contains(Regex("(thanks|thank you|appreciate|please|sorry)")) -> ConversationTone.POLITE_FORMAL
            combinedText.contains(Regex("(love|miss|care|heart|❤️|💕)")) -> ConversationTone.AFFECTIONATE
            combinedText.contains(Regex("(urgent|asap|important|quick|now)")) -> ConversationTone.URGENT
            combinedText.contains(Regex("(meeting|work|business|project|task)")) -> ConversationTone.PROFESSIONAL
            else -> ConversationTone.NEUTRAL
        }
    }

    /**
     * Get conversation statistics
     */
    fun getConversationStats(conversationId: String): ConversationStats {
        val notifications = NotificationMemoryStore.getNotificationsByConversation(conversationId)
        val withReplies = notifications.count { it.ai_reply.isNotBlank() }
        val sent = notifications.count { it.is_sent }

        return ConversationStats(
            totalMessages = notifications.size,
            messagesWithAIReplies = withReplies,
            sentReplies = sent,
            tone = analyzeConversationTone(notifications)
        )
    }

    /**
     * Data classes for results
     */
    data class AIReplyResult(
        val success: Boolean,
        val reply: String?,
        val error: String?,
        val conversationId: String,
        val detectedReminderIntent: DetectedReminderIntent? = null // Reminder intent if detected
    )

    data class ConversationStats(
        val totalMessages: Int,
        val messagesWithAIReplies: Int,
        val sentReplies: Int,
        val tone: ConversationTone
    )

    enum class ConversationTone {
        CASUAL_FUNNY,
        POLITE_FORMAL,
        AFFECTIONATE,
        URGENT,
        PROFESSIONAL,
        NEUTRAL
    }

    // ==================== SCHEDULE-AWARE REPLY GENERATION ====================

    /**
     * Keywords that indicate availability-related questions
     */
    private val AVAILABILITY_KEYWORDS = listOf(
        "free", "busy", "available", "plans", "doing anything", "occupied",
        "schedule", "time", "can you", "are you", "when", "meet", "hang out",
        "come over", "join", "tomorrow", "today", "tonight", "this weekend",
        "next week", "monday", "tuesday", "wednesday", "thursday", "friday",
        "saturday", "sunday"
    )

    /**
     * Check if a message is asking about availability
     */
    fun isAvailabilityQuestion(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Must contain at least one availability keyword
        val hasAvailabilityKeyword = AVAILABILITY_KEYWORDS.any { lowerMessage.contains(it) }

        // And should be phrased as a question or invitation
        val isQuestion = lowerMessage.contains("?") ||
                         lowerMessage.contains("are you") ||
                         lowerMessage.contains("can you") ||
                         lowerMessage.contains("will you") ||
                         lowerMessage.contains("want to") ||
                         lowerMessage.contains("wanna") ||
                         lowerMessage.contains("let's") ||
                         lowerMessage.contains("shall we")

        return hasAvailabilityKeyword && isQuestion
    }

    /**
     * Extract the date being asked about from the message
     */
    fun extractDateFromMessage(message: String): LocalDate? {
        val lowerMessage = message.lowercase()
        val today = LocalDate.now()

        return when {
            lowerMessage.contains("today") || lowerMessage.contains("tonight") -> today
            lowerMessage.contains("tomorrow") -> today.plusDays(1)
            lowerMessage.contains("day after tomorrow") -> today.plusDays(2)
            lowerMessage.contains("this weekend") -> {
                // Find the next Saturday
                val daysUntilSaturday = (6 - today.dayOfWeek.value + 7) % 7
                today.plusDays(daysUntilSaturday.toLong())
            }
            lowerMessage.contains("next week") -> today.plusWeeks(1)
            lowerMessage.contains("monday") -> getNextDayOfWeek(today, java.time.DayOfWeek.MONDAY)
            lowerMessage.contains("tuesday") -> getNextDayOfWeek(today, java.time.DayOfWeek.TUESDAY)
            lowerMessage.contains("wednesday") -> getNextDayOfWeek(today, java.time.DayOfWeek.WEDNESDAY)
            lowerMessage.contains("thursday") -> getNextDayOfWeek(today, java.time.DayOfWeek.THURSDAY)
            lowerMessage.contains("friday") -> getNextDayOfWeek(today, java.time.DayOfWeek.FRIDAY)
            lowerMessage.contains("saturday") -> getNextDayOfWeek(today, java.time.DayOfWeek.SATURDAY)
            lowerMessage.contains("sunday") -> getNextDayOfWeek(today, java.time.DayOfWeek.SUNDAY)
            else -> null
        }
    }

    private fun getNextDayOfWeek(from: LocalDate, targetDay: java.time.DayOfWeek): LocalDate {
        var date = from
        while (date.dayOfWeek != targetDay) {
            date = date.plusDays(1)
        }
        // If it's today and the day matches, return today
        if (from.dayOfWeek == targetDay) return from
        return date
    }

    /**
     * Get user's schedule/reminders for a specific date
     */
    suspend fun getScheduleForDate(date: LocalDate): List<Reminder> {
        return try {
            val result = ReminderFirebaseManager.getRemindersForDate(date)
            result.getOrNull() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch schedule: ${e.message}")
            emptyList()
        }
    }

    /**
     * Format schedule into a readable summary for AI context
     */
    fun formatScheduleSummary(reminders: List<Reminder>, date: LocalDate): String {
        if (reminders.isEmpty()) {
            val dateStr = when {
                date == LocalDate.now() -> "today"
                date == LocalDate.now().plusDays(1) -> "tomorrow"
                else -> date.format(DateTimeFormatter.ofPattern("EEEE"))
            }
            return "I have no scheduled events $dateStr, so I'm completely free!"
        }

        val dateStr = when {
            date == LocalDate.now() -> "today"
            date == LocalDate.now().plusDays(1) -> "tomorrow"
            else -> "on ${date.format(DateTimeFormatter.ofPattern("EEEE"))}"
        }

        val sortedReminders = reminders.sortedBy { it.eventDate }
        val busyTimes = sortedReminders.map { reminder ->
            val time = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(reminder.eventDate),
                ZoneId.systemDefault()
            ).toLocalTime()
            val timeStr = time.format(DateTimeFormatter.ofPattern("h:mm a"))
            "${reminder.title} at $timeStr"
        }

        return when (busyTimes.size) {
            1 -> "I have ${busyTimes[0]} $dateStr, but free otherwise"
            2 -> "I have ${busyTimes[0]} and ${busyTimes[1]} $dateStr"
            else -> {
                val lastTime = busyTimes.last()
                val otherTimes = busyTimes.dropLast(1).joinToString(", ")
                "I have $otherTimes, and $lastTime $dateStr"
            }
        }
    }

    /**
     * Generate schedule-aware AI reply with reminder intent detection
     * This method checks the user's calendar, incorporates availability into the response,
     * and also detects any reminder-worthy events in the conversation
     */
    suspend fun generateScheduleAwareReply(
        notification: NotificationMemoryStore.ExternalNotification,
        conversationId: String,
        maxContextMessages: Int = DEFAULT_CONTEXT_MESSAGES,
        userPersona: Map<String, Any>? = null,
        context: Context
    ): AIReplyResult = withContext(Dispatchers.IO) {

        try {
            val messageText = notification.text
            Log.d(TAG, "🗓️ Generating schedule-aware reply for: $messageText")

            // Check if this is an availability question
            val isAvailabilityQ = isAvailabilityQuestion(messageText)
            Log.d(TAG, "📅 Is availability question: $isAvailabilityQ")

            var scheduleContext: String? = null

            if (isAvailabilityQ) {
                // Extract the date being asked about
                val targetDate = extractDateFromMessage(messageText)
                Log.d(TAG, "📆 Target date: $targetDate")

                if (targetDate != null) {
                    // Fetch user's schedule for that date
                    val reminders = getScheduleForDate(targetDate)
                    Log.d(TAG, "📋 Found ${reminders.size} events on $targetDate")

                    // Build schedule summary
                    scheduleContext = formatScheduleSummary(reminders, targetDate)
                    Log.d(TAG, "📝 Schedule context: $scheduleContext")
                } else {
                    // No specific date mentioned, ask for clarification in a natural way
                    scheduleContext = "The message asks about availability but doesn't specify a time. Respond naturally and ask what time or day they're thinking about."
                }
            }

            // Get conversation messages for context
            val conversationMessages = NotificationMemoryStore.getMessagesForConversation(conversationId)
                .takeLast(maxContextMessages)

            // Convert messages to chat format
            val chatHistory = convertMessagesToChatFormat(conversationMessages)

            // Get app name
            val appName = getAppName(notification.packageName)
            val senderName = notification.sender ?: notification.title

            // Build enhanced persona context with schedule
            val personaContext = buildScheduleAwarePersonaContext(userPersona, scheduleContext)
            Log.d(TAG, "👤 Enhanced persona context built")

            // Generate reply using GroqApiManager
            val groqResponse = GroqApiManager.getNotificationReplyWithPersona(
                currentMessage = messageText,
                senderName = senderName,
                appName = appName,
                conversationHistory = chatHistory,
                personaContext = personaContext
            )

            if (groqResponse.success && groqResponse.message != null) {
                Log.d(TAG, "✅ Schedule-aware AI reply generated: ${groqResponse.message.take(100)}...")

                // Update message with AI reply
                NotificationMemoryStore.updateAIReply(notification.hash, groqResponse.message)

                // ===== REMINDER INTENT DETECTION =====
                // Check if the conversation contains reminder-worthy events
                var detectedIntent: DetectedReminderIntent? = null
                try {
                    Log.d(TAG, "🔍 Checking for reminder intent in conversation...")

                    // Build conversation context for intent detection
                    val conversationContext = conversationMessages.takeLast(5).map { it.msg_content }

                    // Detect reminder intent
                    detectedIntent = ReminderIntentDetector.detectReminderIntent(
                        message = messageText,
                        conversationContext = conversationContext,
                        source = ReminderSource.CHAT_NOTIFICATION,
                        conversationId = conversationId
                    )

                    if (detectedIntent != null) {
                        Log.d(TAG, "🎯 Reminder intent detected: ${detectedIntent.title} at ${detectedIntent.detectedDateTime}")
                    } else {
                        Log.d(TAG, "📭 No reminder intent detected")
                    }
                } catch (reminderError: Exception) {
                    Log.e(TAG, "⚠️ Reminder detection error (non-fatal): ${reminderError.message}")
                    // Continue without reminder - this is non-fatal
                }

                return@withContext AIReplyResult(
                    success = true,
                    reply = groqResponse.message,
                    error = null,
                    conversationId = conversationId,
                    detectedReminderIntent = detectedIntent
                )
            } else {
                return@withContext AIReplyResult(
                    success = false,
                    reply = null,
                    error = groqResponse.error ?: "Unknown error",
                    conversationId = conversationId
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception in schedule-aware reply: ${e.message}", e)
            return@withContext AIReplyResult(
                success = false,
                reply = null,
                error = e.message ?: "Unknown error",
                conversationId = conversationId
            )
        }
    }

    /**
     * Build persona context with schedule awareness
     */
    private fun buildScheduleAwarePersonaContext(
        userPersona: Map<String, Any>?,
        scheduleContext: String?
    ): String {
        val contextBuilder = StringBuilder()

        // Add user persona if available
        if (!userPersona.isNullOrEmpty()) {
            contextBuilder.append("User Persona:\n")
            userPersona.forEach { (key, value) ->
                val formattedKey = key.replace("_", " ").replaceFirstChar { it.uppercase() }
                contextBuilder.append("- $formattedKey: $value\n")
            }
            contextBuilder.append("\n")
        }

        // Add schedule context if available
        if (!scheduleContext.isNullOrBlank()) {
            contextBuilder.append("📅 SCHEDULE CONTEXT (IMPORTANT - Use this for availability questions):\n")
            contextBuilder.append(scheduleContext)
            contextBuilder.append("\n\n")
            contextBuilder.append("When responding about availability, naturally incorporate this schedule information. ")
            contextBuilder.append("Don't just say 'yes' or 'no' - mention when you're busy and when you're free. ")
            contextBuilder.append("Sound natural, like a real person checking their calendar. ")
            contextBuilder.append("Example: 'Tomorrow I have a meeting at 2pm, but I'm free after 4pm. What's up?'")
        }

        return contextBuilder.toString()
    }

    /**
     * Generate schedule-aware reply from a Message object with reminder intent detection
     */
    suspend fun generateScheduleAwareReplyFromMessage(
        message: NotificationMemoryStore.Message,
        conversationId: String,
        displayName: String,
        packageName: String,
        maxContextMessages: Int = DEFAULT_CONTEXT_MESSAGES,
        userPersona: Map<String, Any>? = null,
        context: Context
    ): AIReplyResult = withContext(Dispatchers.IO) {

        try {
            val messageText = message.msg_content
            Log.d(TAG, "🗓️ Generating schedule-aware reply from message: $messageText")

            // Check if this is an availability question
            val isAvailabilityQ = isAvailabilityQuestion(messageText)
            var scheduleContext: String? = null

            if (isAvailabilityQ) {
                val targetDate = extractDateFromMessage(messageText)
                if (targetDate != null) {
                    val reminders = getScheduleForDate(targetDate)
                    scheduleContext = formatScheduleSummary(reminders, targetDate)
                } else {
                    scheduleContext = "The message asks about availability but doesn't specify a time. Ask what time or day they're thinking about in a friendly way."
                }
            }

            // Get conversation messages for context
            val conversationMessages = NotificationMemoryStore.getMessagesForConversation(conversationId)
                .takeLast(maxContextMessages)

            // Convert messages to chat format
            val chatHistory = convertMessagesToChatFormat(conversationMessages)

            // Get app name
            val appName = getAppName(packageName)

            // Build enhanced persona context with schedule
            val personaContext = buildScheduleAwarePersonaContext(userPersona, scheduleContext)

            // Generate reply
            val groqResponse = GroqApiManager.getNotificationReplyWithPersona(
                currentMessage = messageText,
                senderName = message.sender_name.ifBlank { displayName },
                appName = appName,
                conversationHistory = chatHistory,
                personaContext = personaContext
            )

            if (groqResponse.success && groqResponse.message != null) {
                Log.d(TAG, "✅ Schedule-aware reply generated: ${groqResponse.message.take(100)}...")

                // Update message with AI reply
                NotificationMemoryStore.updateMessageAIReply(message.msg_hash, groqResponse.message)

                // ===== REMINDER INTENT DETECTION =====
                // Check if the conversation contains reminder-worthy events
                var detectedIntent: DetectedReminderIntent? = null
                try {
                    Log.d(TAG, "🔍 Checking for reminder intent in message conversation...")

                    // Build conversation context for intent detection
                    val conversationContext = conversationMessages.takeLast(5).map { it.msg_content }

                    // Detect reminder intent
                    detectedIntent = ReminderIntentDetector.detectReminderIntent(
                        message = messageText,
                        conversationContext = conversationContext,
                        source = ReminderSource.CHAT_NOTIFICATION,
                        conversationId = conversationId
                    )

                    if (detectedIntent != null) {
                        Log.d(TAG, "🎯 Reminder intent detected: ${detectedIntent.title} at ${detectedIntent.detectedDateTime}")
                    } else {
                        Log.d(TAG, "📭 No reminder intent detected")
                    }
                } catch (reminderError: Exception) {
                    Log.e(TAG, "⚠️ Reminder detection error (non-fatal): ${reminderError.message}")
                    // Continue without reminder - this is non-fatal
                }

                return@withContext AIReplyResult(
                    success = true,
                    reply = groqResponse.message,
                    error = null,
                    conversationId = conversationId,
                    detectedReminderIntent = detectedIntent
                )
            } else {
                return@withContext AIReplyResult(
                    success = false,
                    reply = null,
                    error = groqResponse.error ?: "Unknown error",
                    conversationId = conversationId
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception: ${e.message}", e)
            return@withContext AIReplyResult(
                success = false,
                reply = null,
                error = e.message ?: "Unknown error",
                conversationId = conversationId
            )
        }
    }
}
