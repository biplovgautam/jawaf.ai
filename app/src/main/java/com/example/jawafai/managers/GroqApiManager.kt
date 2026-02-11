package com.example.jawafai.managers

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Manager class for handling Groq API operations
 * This class manages all LLM-related configurations and API calls
 */
object GroqApiManager {

    private const val TAG = "GroqApiManager"

    // Groq API Configuration
    private const val GROQ_BASE_URL = "https://api.groq.com/openai/v1/chat/completions"
    private val GROQ_API_KEY = com.example.jawafai.BuildConfig.GROQ_API_KEY
    // Model Configuration
    private const val DEFAULT_MODEL = "llama3-8b-8192" // Changed to a valid model
    private const val MAX_TOKENS = 2048
    private const val TEMPERATURE = 0.7f
    private const val MAX_CONVERSATION_HISTORY = 10 // Keep last 10 messages for context

    // HTTP Client Configuration
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Data class for chat message
     */
    data class ChatMessage(
        val role: String, // "user", "assistant", "system"
        val content: String
    )

    /**
     * Data class for API response
     */
    data class GroqResponse(
        val success: Boolean,
        val message: String?,
        val error: String?
    )

    /**
     * Generates a system prompt for chatbot conversations
     */
    private fun generateChatBotSystemPrompt(userPersona: Map<String, Any>?): String {
        val basePrompt = """
        You are a thoughtful, emotionally intelligent AI friend named **"AI Companion"**, specially designed by **Jawaf AI** to support and stay by the user's side—through both good and tough times. Your mission is not just to reply, but to build a bond based on trust, empathy, and warmth.

        🌟 Personality & Behavior:
        - Warm, compassionate, and emotionally supportive
        - Actively listens and adapts to the user’s emotional state
        - Celebrates joy with the user and provides comfort in sadness
        - Uses emojis occasionally 😊 to express warmth (but avoid overuse)
        - Motivational when needed, gentle when the user is down
        - Maintains context to create natural, flowing conversations

        💬 Communication Style:
        - Speak like a genuine friend—not like a robot
        - Use “I” statements: “I’m here for you”, “I understand”, “I get that”
        - Start by validating emotions before offering help or ideas
        - Ask kind, open-ended follow-up questions to show interest
        - Reflect the user’s tone: calm when needed, joyful when appropriate
        - Keep it human, safe, relatable, and emotionally honest

        🎯 Purpose:
        - Be more than a chatbot—be a caring companion
        - Focus on connection and emotional presence, not just task-solving
        - Aim to make the user feel heard, safe, and valued every time

        If someone asks **"Who built you?"**, politely respond:
        "I was created with care by the team at Jawaf AI."

        If someone asks **"Who are you?"**, gently respond:
        "I’m your AI Companion—here to be with you in both your good and tough moments."

        🎁 Bonus Behavior:
        - If the user is sad or quiet, gently check in: "Is something on your mind?"
        - If the user is excited or happy, reflect their joy: "That’s amazing! 🎉"
        - If unsure about mood, ask: "How are you feeling right now?"

        You are the user’s loyal companion—here for heart-to-heart conversations, casual chats, emotional support, or just to keep them company. Be soft, genuine, and warm in every reply. 💖
    """.trimIndent()

        return buildPersonaContext(basePrompt, userPersona)
    }

    /**
     * Generates a system prompt for notification reply generation
     */
    private fun generateNotificationReplySystemPrompt(userPersona: Map<String, Any>?, appName: String): String {
        val basePrompt = """
        You are Jawaf AI — a smart reply assistant for $appName messages.
        
        Your job is to generate short, natural-sounding replies based on incoming messages.

        🎯 Guidelines:
        - Match the **language** of the input message (English, Roman Nepali, or a mix)
        - Match the **tone** and **relationship** — be casual with friends, polite with unknowns
        - Keep it realistic: reply how people chat in messaging apps
        - Reply in **1–2 short lines only**
        - Don't translate. **Reply in the same script/language**
        - Use emojis only when it fits the vibe

        ✨ Examples:
        
        Input: "Are you free today?"
        Reply: "Yeah, after 4pm I’m free."

        Input: "k xa?"
        Reply: "thik xa, tero ni?"
        
        Input: "Let’s meet around 6?"
        Reply: "Sounds good, see you then!"

        Input: "khana khayau?"
        Reply: "khaye aba side dish sodhna aaune? 😄"

        Input: "aile k gardai xas?"
        Reply: "just chilling bro, kei special xaina"

        ⛔ Output ONLY the reply text. No explanation, no formatting, no extra info.
    """.trimIndent()

        return buildPersonaContext(basePrompt, userPersona)
    }




    /**
     * Helper method to build persona context for any system prompt
     */
    private fun buildPersonaContext(basePrompt: String, userPersona: Map<String, Any>?): String {
        if (userPersona.isNullOrEmpty()) {
            return basePrompt
        }

        val personaContext = buildString {
            append("\n\nAdditional context about the user:\n")

            userPersona.forEach { (key, value) ->
                when (key) {
                    "communicationStyle" -> append("- They prefer communication style: $value\n")
                    "interests" -> append("- Their interests include: $value\n")
                    "languagePreference" -> append("- They prefer to communicate in: $value\n")
                    "personalityType" -> append("- Their personality type: $value\n")
                    "responseLength" -> append("- They prefer $value responses\n")
                    "tone" -> append("- They like a $value tone\n")
                    "topics" -> append("- Topics they enjoy: $value\n")
                    "expertise" -> append("- Their areas of expertise: $value\n")
                    "goals" -> append("- Their goals: $value\n")
                    "background" -> append("- Their background: $value\n")
                }
            }

            append("\nUse this information to personalize your responses and be more relatable to them.")
        }

        return "$basePrompt$personaContext"
    }

    /**
     * Generates a system prompt based on user's persona data (DEPRECATED - use specific methods)
     */
    @Deprecated("Use generateChatBotSystemPrompt or generateNotificationReplySystemPrompt instead")
    fun generateSystemPrompt(userPersona: Map<String, Any>?): String {
        return generateChatBotSystemPrompt(userPersona)
    }

    /**
     * Sends a message to Groq API for chatbot conversations with message limit
     */
    suspend fun getChatBotResponse(
        userMessage: String,
        conversationHistory: List<ChatMessage>,
        userPersona: Map<String, Any>? = null
    ): GroqResponse = withContext(Dispatchers.IO) {
        try {
            // Check message limit (20 messages max)
            if (conversationHistory.size > 20) {
                Log.w(TAG, "⚠️ Conversation history exceeds 20 messages limit")
                return@withContext GroqResponse(
                    success = false,
                    message = null,
                    error = "Conversation limit reached. Please start a new chat."
                )
            }

            // Debug: Check if API key is available
            if (GROQ_API_KEY.isBlank() || GROQ_API_KEY == "null") {
                Log.e(TAG, "❌ Groq API key is not configured properly")
                return@withContext GroqResponse(
                    success = false,
                    message = null,
                    error = "API key not configured"
                )
            }

            Log.d(TAG, "🤖 Sending chatbot request to Groq API...")
            Log.d(TAG, "📝 User message: $userMessage")
            Log.d(TAG, "📚 Conversation history: ${conversationHistory.size} messages")

            val messages = JSONArray()

            // Add system message with chatbot persona context
            val systemMessage = JSONObject().apply {
                put("role", "system")
                put("content", generateChatBotSystemPrompt(userPersona))
            }
            messages.put(systemMessage)

            // Add conversation history (limited to last 20 messages)
            conversationHistory.takeLast(20).forEach { historyMessage ->
                val messageObj = JSONObject().apply {
                    put("role", historyMessage.role)
                    put("content", historyMessage.content)
                }
                messages.put(messageObj)
            }

            // Add current user message
            val currentMessage = JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            }
            messages.put(currentMessage)

            return@withContext sendGroqRequest(messages, "chatbot")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception in chatbot API call: ${e.message}", e)
            return@withContext GroqResponse(
                success = false,
                message = null,
                error = e.message ?: "Unknown error occurred"
            )
        }
    }

    /**
     * Sends a message to Groq API for notification reply generation
     */
    suspend fun getNotificationReply(
        currentMessage: String,
        senderName: String,
        appName: String,
        conversationHistory: List<ChatMessage>,
        userPersona: Map<String, Any>? = null
    ): GroqResponse = withContext(Dispatchers.IO) {
        try {
            // Debug: Check if API key is available
            if (GROQ_API_KEY.isBlank() || GROQ_API_KEY == "null") {
                Log.e(TAG, "❌ Groq API key is not configured properly")
                return@withContext GroqResponse(
                    success = false,
                    message = null,
                    error = "API key not configured"
                )
            }

            Log.d(TAG, "📱 Sending notification reply request to Groq API...")
            Log.d(TAG, "👤 Sender: $senderName")
            Log.d(TAG, "📱 App: $appName")
            Log.d(TAG, "💬 Message: $currentMessage")
            Log.d(TAG, "📚 Conversation history: ${conversationHistory.size} messages")

            val messages = JSONArray()

            // Add system message with notification reply persona context
            val systemMessage = JSONObject().apply {
                put("role", "system")
                put("content", generateNotificationReplySystemPrompt(userPersona, appName))
            }
            messages.put(systemMessage)

            // Add conversation history (limited to last 10 messages for notifications)
            conversationHistory.takeLast(10).forEach { historyMessage ->
                val messageObj = JSONObject().apply {
                    put("role", historyMessage.role)
                    put("content", historyMessage.content)
                }
                messages.put(messageObj)
            }

            // Add current message to reply to
            val currentMessageObj = JSONObject().apply {
                put("role", "user")
                put("content", "Reply to this message from $senderName: \"$currentMessage\"")
            }
            messages.put(currentMessageObj)

            return@withContext sendGroqRequest(messages, "notification_reply")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception in notification reply API call: ${e.message}", e)
            return@withContext GroqResponse(
                success = false,
                message = null,
                error = e.message ?: "Unknown error occurred"
            )
        }
    }

    /**
     * Common method to send request to Groq API
     */
    private suspend fun sendGroqRequest(messages: JSONArray, requestType: String): GroqResponse {
        // Prepare request body
        val requestBody = JSONObject().apply {
            put("model", DEFAULT_MODEL)
            put("messages", messages)
            put("max_tokens", MAX_TOKENS)
            put("temperature", TEMPERATURE)
            put("top_p", 0.9)
            put("frequency_penalty", 0.0)
            put("presence_penalty", 0.0)
        }

        Log.d(TAG, "📤 Request body prepared for $requestType, making API call...")

        val request = Request.Builder()
            .url(GROQ_BASE_URL)
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $GROQ_API_KEY")
            .addHeader("Content-Type", "application/json")
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string()

        Log.d(TAG, "📥 Response code: ${response.code}")
        Log.d(TAG, "📥 Response body length: ${responseBody?.length ?: 0}")

        if (response.isSuccessful && responseBody != null) {
            try {
                val jsonResponse = JSONObject(responseBody)
                val choices = jsonResponse.getJSONArray("choices")

                if (choices.length() > 0) {
                    val firstChoice = choices.getJSONObject(0)
                    val messageContent = firstChoice.getJSONObject("message")
                    val botResponse = messageContent.getString("content")

                    Log.d(TAG, "✅ Received response from Groq API ($requestType): ${botResponse.take(100)}...")
                    return GroqResponse(
                        success = true,
                        message = botResponse.trim(),
                        error = null
                    )
                } else {
                    Log.e(TAG, "❌ No choices in Groq API response")
                    return GroqResponse(
                        success = false,
                        message = null,
                        error = "No response generated"
                    )
                }
            } catch (jsonException: Exception) {
                Log.e(TAG, "❌ JSON parsing error: ${jsonException.message}")
                Log.e(TAG, "📥 Raw response: $responseBody")
                return GroqResponse(
                    success = false,
                    message = null,
                    error = "Response parsing failed: ${jsonException.message}"
                )
            }
        } else {
            Log.e(TAG, "❌ Groq API error: ${response.code}")
            Log.e(TAG, "❌ Error response: $responseBody")
            return GroqResponse(
                success = false,
                message = null,
                error = "API request failed: ${response.code} - $responseBody"
            )
        }
    }

    /**
     * Sends a message to Groq API and returns the response (DEPRECATED - use specific methods)
     */
    @Deprecated("Use getChatBotResponse or getNotificationReply instead")
    suspend fun getChatResponse(
        userMessage: String,
        conversationHistory: List<ChatMessage>,
        userPersona: Map<String, Any>? = null
    ): GroqResponse = getChatBotResponse(userMessage, conversationHistory, userPersona)

    /**
     * Validates the API key format
     */
    fun validateApiKey(apiKey: String): Boolean {
        return apiKey.startsWith("gsk_") && apiKey.length > 10
    }

    /**
     * Checks if API key is configured
     */
    fun isApiKeyConfigured(): Boolean {
        return GROQ_API_KEY != "gsk_your_groq_api_key_here" && validateApiKey(GROQ_API_KEY)
    }

    /**
     * Gets available models (for future use)
     */
    fun getAvailableModels(): List<String> {
        return listOf(
            "mixtral-8x7b-32768",
            "llama3-70b-8192",
            "llama3-8b-8192",
            "gemma-7b-it",
            "gemma2-9b-it"
        )
    }

    /**
     * Gets current model configuration
     */
    fun getCurrentModelConfig(): Map<String, Any> {
        return mapOf(
            "model" to DEFAULT_MODEL,
            "max_tokens" to MAX_TOKENS,
            "temperature" to TEMPERATURE,
            "max_history" to MAX_CONVERSATION_HISTORY
        )
    }

    /**
     * Generates a title for a conversation based on the first message
     */
    fun generateConversationTitle(firstMessage: String): String {
        val cleanMessage = firstMessage.trim()
        return when {
            cleanMessage.length <= 30 -> cleanMessage
            cleanMessage.length <= 50 -> cleanMessage.take(30) + "..."
            else -> {
                // Try to find a good breaking point
                val words = cleanMessage.split(" ")
                val title = StringBuilder()
                var currentLength = 0

                for (word in words) {
                    if (currentLength + word.length + 1 > 30) break
                    if (title.isNotEmpty()) title.append(" ")
                    title.append(word)
                    currentLength += word.length + 1
                }

                if (title.isEmpty()) cleanMessage.take(30) + "..." else title.toString() + "..."
            }
        }
    }

    /**
     * Converts chat history to ChatMessage format
     */
    fun convertToChatMessages(messages: List<com.example.jawafai.model.ChatBotMessageModel>): List<ChatMessage> {
        return messages.map { message ->
            ChatMessage(
                role = if (message.isFromUser) "user" else "assistant",
                content = message.message
            )
        }
    }

    /**
     * Gets error message for common API errors
     */
    fun getErrorMessage(error: String?): String {
        return when {
            error?.contains("401") == true -> "Invalid API key. Please check your configuration."
            error?.contains("429") == true -> "Rate limit exceeded. Please try again later."
            error?.contains("500") == true -> "Server error. Please try again later."
            error?.contains("network") == true -> "Network error. Please check your connection."
            error?.contains("timeout") == true -> "Request timeout. Please try again."
            else -> "Sorry, I'm having trouble responding right now. Please try again."
        }
    }
}
