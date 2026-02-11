package com.example.jawafai.model

data class ChatBotConversationModel(
    val id: String = "",
    val userId: String = "",
    val title: String = "New Chat",
    val messages: List<ChatBotMessageModel> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "userId" to userId,
            "title" to title,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
            "isActive" to isActive
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): ChatBotConversationModel {
            return ChatBotConversationModel(
                id = map["id"] as? String ?: "",
                userId = map["userId"] as? String ?: "",
                title = map["title"] as? String ?: "New Chat",
                createdAt = map["createdAt"] as? Long ?: System.currentTimeMillis(),
                updatedAt = map["updatedAt"] as? Long ?: System.currentTimeMillis(),
                isActive = map["isActive"] as? Boolean ?: true
            )
        }
    }
}

data class ChatBotMessageModel(
    val id: String = "",
    val conversationId: String = "",
    val message: String = "",
    val isFromUser: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
    val messageType: ChatBotMessageType = ChatBotMessageType.TEXT,
    val isTyping: Boolean = false
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "conversationId" to conversationId,
            "message" to message,
            "isFromUser" to isFromUser,
            "timestamp" to timestamp,
            "messageType" to messageType.name,
            "isTyping" to isTyping
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): ChatBotMessageModel {
            return ChatBotMessageModel(
                id = map["id"] as? String ?: "",
                conversationId = map["conversationId"] as? String ?: "",
                message = map["message"] as? String ?: "",
                isFromUser = map["isFromUser"] as? Boolean ?: true,
                timestamp = map["timestamp"] as? Long ?: System.currentTimeMillis(),
                messageType = ChatBotMessageType.valueOf(
                    map["messageType"] as? String ?: ChatBotMessageType.TEXT.name
                ),
                isTyping = map["isTyping"] as? Boolean ?: false
            )
        }
    }
}

enum class ChatBotMessageType {
    TEXT,
    TYPING,
    ERROR,
    SYSTEM
}
