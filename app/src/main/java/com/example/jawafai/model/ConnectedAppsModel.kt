package com.example.jawafai.model

/**
 * Model for storing user's connected apps preferences
 * Stored in Firebase under users/{userId}/connectedApps
 */
data class ConnectedAppsModel(
    val whatsapp: Boolean = false,
    val instagram: Boolean = false,
    val messenger: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
) {
    // Convert to map for Firestore
    fun toMap(): Map<String, Any> {
        return mapOf(
            "whatsapp" to whatsapp,
            "instagram" to instagram,
            "messenger" to messenger,
            "updatedAt" to updatedAt
        )
    }

    // Get count of connected apps
    fun getConnectedCount(): Int {
        var count = 0
        if (whatsapp) count++
        if (instagram) count++
        if (messenger) count++
        return count
    }

    // Check if a specific app is connected
    fun isConnected(packageName: String): Boolean {
        return when {
            packageName.contains("whatsapp", ignoreCase = true) -> whatsapp
            packageName.contains("instagram", ignoreCase = true) -> instagram
            packageName.contains("messenger", ignoreCase = true) ||
            packageName.contains("facebook.orca", ignoreCase = true) -> messenger
            else -> false
        }
    }

    // Get list of connected package name patterns
    fun getConnectedPackages(): List<String> {
        val packages = mutableListOf<String>()
        if (whatsapp) packages.add("com.whatsapp")
        if (instagram) packages.add("com.instagram.android")
        if (messenger) {
            packages.add("com.facebook.orca")
            packages.add("com.facebook.mlite")
        }
        return packages
    }

    companion object {
        const val MAX_FREE_APPS = 2
        const val COLLECTION_NAME = "connectedApps"

        // Create from Firestore document
        fun fromMap(map: Map<String, Any?>): ConnectedAppsModel {
            return ConnectedAppsModel(
                whatsapp = map["whatsapp"] as? Boolean ?: false,
                instagram = map["instagram"] as? Boolean ?: false,
                messenger = map["messenger"] as? Boolean ?: false,
                updatedAt = map["updatedAt"] as? Long ?: System.currentTimeMillis()
            )
        }

        // Default empty model
        fun empty(): ConnectedAppsModel {
            return ConnectedAppsModel()
        }
    }
}

/**
 * Enum for supported platforms
 */
enum class SupportedPlatform(
    val displayName: String,
    val packagePatterns: List<String>,
    val fieldName: String
) {
    WHATSAPP(
        displayName = "WhatsApp",
        packagePatterns = listOf("com.whatsapp"),
        fieldName = "whatsapp"
    ),
    INSTAGRAM(
        displayName = "Instagram",
        packagePatterns = listOf("com.instagram.android"),
        fieldName = "instagram"
    ),
    MESSENGER(
        displayName = "Messenger",
        packagePatterns = listOf("com.facebook.orca", "com.facebook.mlite"),
        fieldName = "messenger"
    );

    companion object {
        fun fromPackageName(packageName: String): SupportedPlatform? {
            return values().find { platform ->
                platform.packagePatterns.any { packageName.contains(it, ignoreCase = true) }
            }
        }

        fun isSupported(packageName: String): Boolean {
            return fromPackageName(packageName) != null
        }
    }
}

