package com.example.jawafai.model

data class ChatBotMessage(
    val id: String = "",
    val userId: String = "",
    val message: String = "",
    val isFromUser: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
    val messageType: ChatBotMessageType = ChatBotMessageType.TEXT
) {
    // Convert to map for Firebase
    fun toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "userId" to userId,
            "message" to message,
            "isFromUser" to isFromUser,
            "timestamp" to timestamp,
            "messageType" to messageType.name
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): ChatBotMessage {
            return ChatBotMessage(
                id = map["id"] as? String ?: "",
                userId = map["userId"] as? String ?: "",
                message = map["message"] as? String ?: "",
                isFromUser = map["isFromUser"] as? Boolean ?: true,
                timestamp = map["timestamp"] as? Long ?: System.currentTimeMillis(),
                messageType = ChatBotMessageType.valueOf(
                    map["messageType"] as? String ?: ChatBotMessageType.TEXT.name
                )
            )
        }
    }
}

data class ChatBotConversation(
    val id: String = "",
    val userId: String = "",
    val title: String = "New Conversation",
    val lastMessage: String = "",
    val lastMessageTimestamp: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val messageCount: Int = 0
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "userId" to userId,
            "title" to title,
            "lastMessage" to lastMessage,
            "lastMessageTimestamp" to lastMessageTimestamp,
            "createdAt" to createdAt,
            "messageCount" to messageCount
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): ChatBotConversation {
            return ChatBotConversation(
                id = map["id"] as? String ?: "",
                userId = map["userId"] as? String ?: "",
                title = map["title"] as? String ?: "New Conversation",
                lastMessage = map["lastMessage"] as? String ?: "",
                lastMessageTimestamp = map["lastMessageTimestamp"] as? Long ?: System.currentTimeMillis(),
                createdAt = map["createdAt"] as? Long ?: System.currentTimeMillis(),
                messageCount = (map["messageCount"] as? Long)?.toInt() ?: 0
            )
        }
    }
}
