package com.example.jawafai.repository

import android.net.Uri
import android.util.Log
import com.example.jawafai.managers.CloudinaryManager
import com.example.jawafai.model.UserModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : UserRepository {

    private val TAG = "UserRepositoryImpl"
    private val usersCollection = firestore.collection("users")
    private val database = FirebaseDatabase.getInstance("https://jawafai-d2c23-default-rtdb.firebaseio.com/")
    private val usersRef = database.getReference("users")

    /**
     * Syncs user profile to both Firestore and Realtime Database
     * This ensures users are searchable and have consistent data across both databases
     */
    override suspend fun syncUserProfileToFirebase(user: FirebaseUser, userModel: UserModel): Unit = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 Starting user profile sync for UID: ${user.uid}")

            // 1. Check if Firestore document exists
            val firestoreDoc = usersCollection.document(user.uid).get().await()

            if (!firestoreDoc.exists()) {
                Log.d(TAG, "📝 User not found in Firestore, creating profile...")

                // Create user profile in Firestore
                val userWithId = userModel.copy(
                    id = user.uid,
                    password = "", // Never store password
                    email = user.email ?: userModel.email,
                    createdAt = System.currentTimeMillis()
                )

                usersCollection.document(user.uid).set(userWithId.toMap()).await()
                Log.d(TAG, "✅ User profile created in Firestore")
            } else {
                Log.d(TAG, "👤 User profile already exists in Firestore")
            }

            // 2. Sync to Realtime Database for chat functionality
            val realtimeUserData = mapOf(
                "username" to userModel.username,
                "email" to (user.email ?: userModel.email),
                "displayName" to userModel.let {
                    "${it.firstName} ${it.lastName}".trim().ifEmpty { it.username }
                },
                "profileImageUrl" to userModel.imageUrl,
                "onlineStatus" to true,
                "lastSeen" to System.currentTimeMillis(),
                "createdAt" to System.currentTimeMillis()
            )

            usersRef.child(user.uid).setValue(realtimeUserData).await()
            Log.d(TAG, "✅ User profile synced to Realtime Database")

            Log.d(TAG, "🎉 User profile sync completed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error syncing user profile: ${e.message}", e)
            throw e
        }
    }

    /**
     * Finds a user by username or email in Firestore
     * This provides better search capabilities than the current Realtime Database approach
     */
    override suspend fun findUserByEmailOrUsername(query: String): UserModel? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔍 Searching for user with query: '$query'")

            val cleanQuery = query.trim()
            if (cleanQuery.isBlank()) {
                Log.w(TAG, "Empty search query provided")
                return@withContext null
            }

            // First try to find by email
            val emailQuery = usersCollection
                .whereEqualTo("email", cleanQuery)
                .limit(1)
                .get()
                .await()

            if (!emailQuery.isEmpty) {
                val userDoc = emailQuery.documents.first()
                val userModel = UserModel.fromMap(userDoc.data ?: emptyMap())
                Log.d(TAG, "✅ Found user by email: ${userModel.username}")
                return@withContext userModel
            }

            // Then try to find by username (case-insensitive)
            val usernameQuery = usersCollection
                .whereEqualTo("username", cleanQuery)
                .limit(1)
                .get()
                .await()

            if (!usernameQuery.isEmpty) {
                val userDoc = usernameQuery.documents.first()
                val userModel = UserModel.fromMap(userDoc.data ?: emptyMap())
                Log.d(TAG, "✅ Found user by username: ${userModel.username}")
                return@withContext userModel
            }

            // If exact match fails, try case-insensitive search
            val allUsersQuery = usersCollection.get().await()
            val matchingUser = allUsersQuery.documents.find { doc ->
                val data = doc.data ?: return@find false
                val email = data["email"] as? String ?: ""
                val username = data["username"] as? String ?: ""

                email.equals(cleanQuery, ignoreCase = true) ||
                username.equals(cleanQuery, ignoreCase = true)
            }

            if (matchingUser != null) {
                val userModel = UserModel.fromMap(matchingUser.data ?: emptyMap())
                Log.d(TAG, "✅ Found user by case-insensitive search: ${userModel.username}")
                return@withContext userModel
            }

            Log.d(TAG, "❌ No user found with query: '$cleanQuery'")
            return@withContext null

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error searching for user: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * Checks if a username already exists in Firestore
     * @param username The username to check
     * @return Boolean true if username exists, false otherwise
     */
    override suspend fun isUsernameExists(username: String): Boolean = suspendCoroutine { continuation ->
        if (username.isEmpty()) {
            continuation.resume(false)
            return@suspendCoroutine
        }

        usersCollection
            .whereEqualTo("username", username)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                continuation.resume(!documents.isEmpty)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error checking username existence: ${e.message}")
                continuation.resume(false) // Assume it doesn't exist on error
            }
    }

    /**
     * Checks if an email already exists in Firestore
     * Note: Firebase Auth already enforces unique emails, but this is a double-check
     * @param email The email to check
     * @return Boolean true if email exists, false otherwise
     */
    override suspend fun isEmailExists(email: String): Boolean = suspendCoroutine { continuation ->
        if (email.isEmpty()) {
            continuation.resume(false)
            return@suspendCoroutine
        }

        usersCollection
            .whereEqualTo("email", email)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                continuation.resume(!documents.isEmpty)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error checking email existence: ${e.message}")
                continuation.resume(false) // Assume it doesn't exist on error
            }
    }

    override suspend fun registerUser(user: UserModel, password: String): Boolean =
        suspendCoroutine { continuation ->
            Log.d(TAG, "Starting user registration for email: ${user.email}")
            try {
                if (user.username.isNotEmpty()) {
                    usersCollection
                        .whereEqualTo("username", user.username)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { documents ->
                            if (!documents.isEmpty) {
                                Log.w(TAG, "Username already exists: ${user.username}")
                                continuation.resumeWithException(Exception("Username already exists"))
                                return@addOnSuccessListener
                            }
                            // Username doesn't exist, proceed with registration
                            createUserWithAuthAndFirestore(user, password, continuation)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error checking username: ${e.message}")
                            continuation.resumeWithException(e)
                        }
                } else {
                    createUserWithAuthAndFirestore(user, password, continuation)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in registration process: ${e.message}")
                continuation.resumeWithException(e)
            }
        }

    private fun createUserWithAuthAndFirestore(user: UserModel, password: String, continuation: kotlin.coroutines.Continuation<Boolean>) {
        auth.createUserWithEmailAndPassword(user.email, password)
            .addOnSuccessListener { authResult ->
                val uid = authResult.user?.uid
                if (uid == null) {
                    Log.e(TAG, "Auth succeeded but user ID is null")
                    continuation.resume(false)
                    return@addOnSuccessListener
                }
                Log.d(TAG, "Authentication successful, user created with ID: $uid")
                val userWithId = user.copy(id = uid, password = "")

                // Log what we're trying to write to Firestore
                Log.d(TAG, "Attempting to write user data to Firestore: ${userWithId.toMap()}")

                usersCollection.document(uid)
                    .set(userWithId.toMap())
                    .addOnSuccessListener {
                        Log.d(TAG, "Firestore document created successfully")

                        // Now sync to Realtime Database as well
                        authResult.user?.let { firebaseUser ->
                            // Launch coroutine to sync profile to both databases
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    syncUserProfileToFirebase(firebaseUser, userWithId)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error syncing user profile during registration: ${e.message}")
                                }
                            }
                        }

                        continuation.resume(true)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Firestore write failed with error: ${e.message}", e)

                        // We'll consider registration successful even if Firestore write fails
                        // This way the user can at least log in, even if their profile data isn't saved
                        continuation.resume(true)

                        // This allows registration to "succeed" even though Firestore write failed
                        // The user can update their profile later
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Authentication failed: ${e.message}")
                continuation.resumeWithException(e)
            }
    }

    override suspend fun updateUser(user: UserModel): Boolean =
        suspendCoroutine { continuation ->
            val currentFirebaseUser = auth.currentUser
            if (currentFirebaseUser == null) {
                continuation.resume(false)
                return@suspendCoroutine
            }
            val currentUid = currentFirebaseUser.uid

            val updates = mapOf(
                "firstName" to user.firstName,
                "lastName" to user.lastName,
                "username" to user.username,
                "dateOfBirth" to user.dateOfBirth,
                "bio" to user.bio,
                "imageUrl" to user.imageUrl
            )

            val docRef = usersCollection.document(currentUid)

            val firestoreUpdateAction = {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName("${user.firstName} ${user.lastName}".trim().ifEmpty { user.username })
                    .setPhotoUri(user.imageUrl?.let { Uri.parse(it) })
                    .build()

                currentFirebaseUser.updateProfile(profileUpdates)
                    .addOnSuccessListener {
                        continuation.resume(true)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to update Firebase Auth profile", e)
                        continuation.resume(true) // Firestore succeeded, so we count it as success
                    }
            }

            docRef.get()
                .addOnSuccessListener { documentSnapshot ->
                    val task = if (documentSnapshot.exists()) {
                        docRef.update(updates)
                    } else {
                        docRef.set(updates, SetOptions.merge())
                    }
                    task.addOnSuccessListener { firestoreUpdateAction() }
                        .addOnFailureListener { e -> continuation.resumeWithException(e) }
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }

    override suspend fun deleteUser(userId: String): Boolean =
        suspendCoroutine { continuation ->
            // First delete from Firestore
            usersCollection.document(userId)
                .delete()
                .addOnSuccessListener {
                    // Then delete the Firebase Auth user
                    auth.currentUser?.delete()
                        ?.addOnSuccessListener {
                            continuation.resume(true)
                        }
                        ?.addOnFailureListener { e ->
                            continuation.resumeWithException(e)
                        }
                        ?: continuation.resume(false)
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }

    override fun logout() {
        auth.signOut()
    }

    /**
     * Uploads a profile image using the CloudinaryManager.
     * @param imageUri The URI of the image to upload.
     * @return The secure URL of the uploaded image, or null on failure.
     */
    override suspend fun uploadProfileImage(imageUri: Uri): String? {
        return try {
            Log.d(TAG, "Uploading image through CloudinaryManager for URI: $imageUri")

            if (!CloudinaryManager.isInitialized()) {
                Log.e(TAG, "CloudinaryManager not initialized")
                return null
            }

            val result = CloudinaryManager.uploadImage(imageUri)
            if (result != null) {
                Log.d(TAG, "Image upload successful: $result")
            } else {
                Log.e(TAG, "Image upload failed: null result")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Exception during image upload: ${e.message}", e)
            null
        }
    }

    override suspend fun updateFcmToken(userId: String, fcmToken: String) {
        withContext(Dispatchers.IO) {
            try {
                // Update in Firestore
                usersCollection.document(userId).set(mapOf("fcmToken" to fcmToken), SetOptions.merge()).await()
                // Update in Realtime Database
                usersRef.child(userId).child("fcmToken").setValue(fcmToken).await()
                Log.d(TAG, "FCM token updated for user $userId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update FCM token for user $userId: ${e.message}", e)
            }
        }
    }

    override suspend fun findUserById(userId: String): UserModel? = withContext(Dispatchers.IO) {
        try {
            val doc = usersCollection.document(userId).get().await()
            if (doc.exists()) {
                val data = doc.data ?: return@withContext null
                com.example.jawafai.model.UserModel.fromMap(data)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user by id: ${e.message}", e)
            null
        }
    }
}
