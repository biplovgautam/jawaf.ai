package com.example.jawafai.managers

import android.util.Log
import com.example.jawafai.model.ConnectedAppsModel
import com.example.jawafai.model.SupportedPlatform
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Manager for handling connected apps functionality
 * - Saves/loads connected apps from Firebase
 * - Checks if user can connect more apps (pro vs free)
 * - Provides real-time updates via Flow
 */
object ConnectedAppsManager {

    private const val TAG = "ConnectedAppsManager"
    private const val USERS_COLLECTION = "users"
    private const val CONNECTED_APPS_DOC = "connectedApps"
    private const val SETTINGS_COLLECTION = "settings"

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Cached connected apps for quick access
    @Volatile
    private var cachedConnectedApps: ConnectedAppsModel? = null

    /**
     * Get current user's connected apps as a Flow for real-time updates
     */
    fun getConnectedAppsFlow(): Flow<ConnectedAppsModel> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(ConnectedAppsModel.empty())
            close()
            return@callbackFlow
        }

        val docRef = firestore
            .collection(USERS_COLLECTION)
            .document(userId)
            .collection(SETTINGS_COLLECTION)
            .document(CONNECTED_APPS_DOC)

        val listener: ListenerRegistration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error listening to connected apps: ${error.message}")
                trySend(ConnectedAppsModel.empty())
                return@addSnapshotListener
            }

            val connectedApps = if (snapshot != null && snapshot.exists()) {
                ConnectedAppsModel.fromMap(snapshot.data ?: emptyMap())
            } else {
                ConnectedAppsModel.empty()
            }

            cachedConnectedApps = connectedApps
            trySend(connectedApps)
        }

        awaitClose {
            listener.remove()
        }
    }

    /**
     * Get current user's connected apps (suspend function)
     */
    suspend fun getConnectedApps(): ConnectedAppsModel {
        // Return cached if available
        cachedConnectedApps?.let { return it }

        val userId = auth.currentUser?.uid ?: return ConnectedAppsModel.empty()

        return try {
            val doc = firestore
                .collection(USERS_COLLECTION)
                .document(userId)
                .collection(SETTINGS_COLLECTION)
                .document(CONNECTED_APPS_DOC)
                .get()
                .await()

            val connectedApps = if (doc.exists()) {
                ConnectedAppsModel.fromMap(doc.data ?: emptyMap())
            } else {
                ConnectedAppsModel.empty()
            }

            cachedConnectedApps = connectedApps
            connectedApps
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching connected apps: ${e.message}")
            ConnectedAppsModel.empty()
        }
    }

    /**
     * Update connected apps in Firebase
     */
    suspend fun updateConnectedApps(connectedApps: ConnectedAppsModel): Boolean {
        val userId = auth.currentUser?.uid ?: return false

        return try {
            firestore
                .collection(USERS_COLLECTION)
                .document(userId)
                .collection(SETTINGS_COLLECTION)
                .document(CONNECTED_APPS_DOC)
                .set(connectedApps.toMap())
                .await()

            cachedConnectedApps = connectedApps
            Log.d(TAG, "✅ Connected apps updated successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating connected apps: ${e.message}")
            false
        }
    }

    /**
     * Toggle a specific platform connection
     * Returns: Pair<Boolean, String?> - (success, error message if any)
     */
    suspend fun togglePlatform(
        platform: SupportedPlatform,
        enable: Boolean,
        isPro: Boolean
    ): Pair<Boolean, String?> {
        val currentApps = getConnectedApps()

        // Check if trying to enable and already at max for free users
        if (enable && !isPro) {
            val currentCount = currentApps.getConnectedCount()
            if (currentCount >= ConnectedAppsModel.MAX_FREE_APPS) {
                return Pair(false, "Upgrade to Pro to connect more than ${ConnectedAppsModel.MAX_FREE_APPS} apps")
            }
        }

        // Create updated model
        val updatedApps = when (platform) {
            SupportedPlatform.WHATSAPP -> currentApps.copy(
                whatsapp = enable,
                updatedAt = System.currentTimeMillis()
            )
            SupportedPlatform.INSTAGRAM -> currentApps.copy(
                instagram = enable,
                updatedAt = System.currentTimeMillis()
            )
            SupportedPlatform.MESSENGER -> currentApps.copy(
                messenger = enable,
                updatedAt = System.currentTimeMillis()
            )
        }

        val success = updateConnectedApps(updatedApps)
        return if (success) {
            Pair(true, null)
        } else {
            Pair(false, "Failed to update. Please try again.")
        }
    }

    /**
     * Check if a package name is connected
     */
    fun isPackageConnected(packageName: String): Boolean {
        val connectedApps = cachedConnectedApps ?: return false
        return connectedApps.isConnected(packageName)
    }

    /**
     * Check if user can connect more apps
     */
    suspend fun canConnectMoreApps(isPro: Boolean): Boolean {
        if (isPro) return true

        val connectedApps = getConnectedApps()
        return connectedApps.getConnectedCount() < ConnectedAppsModel.MAX_FREE_APPS
    }

    /**
     * Get number of remaining slots for free users
     */
    suspend fun getRemainingSlots(isPro: Boolean): Int {
        if (isPro) return Int.MAX_VALUE

        val connectedApps = getConnectedApps()
        return (ConnectedAppsModel.MAX_FREE_APPS - connectedApps.getConnectedCount()).coerceAtLeast(0)
    }

    /**
     * Check if notification should be processed based on connected apps
     */
    fun shouldProcessNotification(packageName: String): Boolean {
        val connectedApps = cachedConnectedApps

        // If no connected apps data, allow all for now
        if (connectedApps == null) {
            Log.d(TAG, "No connected apps data, allowing notification from $packageName")
            return true
        }

        // Check if it's a supported platform
        val platform = SupportedPlatform.fromPackageName(packageName)
        if (platform == null) {
            Log.d(TAG, "Unsupported platform: $packageName")
            return false
        }

        // Check if this platform is connected
        val isConnected = connectedApps.isConnected(packageName)
        Log.d(TAG, "Package $packageName connected: $isConnected")
        return isConnected
    }

    /**
     * Initialize the manager - load connected apps into cache
     */
    suspend fun initialize() {
        try {
            cachedConnectedApps = getConnectedApps()
            Log.d(TAG, "✅ ConnectedAppsManager initialized")
            Log.d(TAG, "📱 Connected apps: WhatsApp=${cachedConnectedApps?.whatsapp}, Instagram=${cachedConnectedApps?.instagram}, Messenger=${cachedConnectedApps?.messenger}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize ConnectedAppsManager: ${e.message}")
        }
    }

    /**
     * Clear cache (call on logout)
     */
    fun clearCache() {
        cachedConnectedApps = null
    }
}

