package com.example.jawafai.utils

import android.util.Log
import com.example.jawafai.model.UserModel
import com.example.jawafai.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Utility class for syncing user profiles between Firestore and Realtime Database
 * This ensures users are searchable and have consistent data across both databases
 */
class UserProfileSyncUtils @Inject constructor(
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {

    companion object {
        private const val TAG = "UserProfileSyncUtils"

        /**
         * Sync current authenticated user's profile to both databases
         * This should be called after login or app startup
         */
        suspend fun syncCurrentUserProfile(
            userRepository: UserRepository,
            auth: FirebaseAuth = FirebaseAuth.getInstance(),
            firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
        ) {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.w(TAG, "No authenticated user found")
                return
            }

            try {
                Log.d(TAG, "🔄 Starting profile sync for user: ${currentUser.uid}")

                // Check if user profile exists in Firestore
                val userDoc = firestore.collection("users")
                    .document(currentUser.uid)
                    .get()
                    .await()

                val userModel = if (userDoc.exists()) {
                    // Profile exists, load it
                    UserModel.fromMap(userDoc.data ?: emptyMap())
                } else {
                    // Profile doesn't exist, create default one
                    Log.d(TAG, "Creating default profile for user: ${currentUser.uid}")
                    createDefaultUserProfile(currentUser)
                }

                // Sync to both databases
                userRepository.syncUserProfileToFirebase(currentUser, userModel)
                Log.d(TAG, "✅ Profile sync completed successfully")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error syncing user profile: ${e.message}", e)
                // Don't throw exception - sync failure shouldn't break the app
            }
        }

        /**
         * Creates a default user profile from Firebase Auth user data
         */
        private fun createDefaultUserProfile(firebaseUser: FirebaseUser): UserModel {
            val defaultUsername = firebaseUser.email?.substringBefore('@') ?: "user${firebaseUser.uid.take(6)}"
            val displayName = firebaseUser.displayName
            val firstName = displayName?.split(" ")?.getOrNull(0) ?: ""
            val lastName = displayName?.split(" ")?.getOrNull(1) ?: ""

            return UserModel(
                id = firebaseUser.uid,
                email = firebaseUser.email ?: "",
                username = defaultUsername,
                firstName = firstName,
                lastName = lastName,
                imageUrl = firebaseUser.photoUrl?.toString(),
                createdAt = System.currentTimeMillis()
            )
        }
    }

    /**
     * Instance method for dependency injection
     */
    suspend fun syncCurrentUser() {
        syncCurrentUserProfile(userRepository, auth, firestore)
    }

    /**
     * Ensures user is searchable by syncing their profile after any profile update
     */
    suspend fun ensureUserIsSearchable(userModel: UserModel) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            try {
                userRepository.syncUserProfileToFirebase(currentUser, userModel)
                Log.d(TAG, "✅ User profile updated and synced for searchability")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error ensuring user is searchable: ${e.message}", e)
            }
        }
    }
}
