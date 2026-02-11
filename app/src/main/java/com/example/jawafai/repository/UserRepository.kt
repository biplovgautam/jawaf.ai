package com.example.jawafai.repository

import android.net.Uri
import com.example.jawafai.model.UserModel
import com.google.firebase.auth.FirebaseUser

/**
 * Repository interface for handling user authentication and Firestore operations
 */
interface UserRepository {
    /**
     * Registers a new user with Firebase Authentication and stores their data in Firestore
     * @param user The user model containing registration information
     * @param password The user's password for authentication
     * @return Boolean indicating success or failure
     */
    suspend fun registerUser(user: UserModel, password: String): Boolean

    /**
     * Checks if a username already exists in the database
     * @param username The username to check
     * @return Boolean indicating if the username exists
     */
    suspend fun isUsernameExists(username: String): Boolean

    /**
     * Checks if an email already exists in the database
     * @param email The email to check
     * @return Boolean indicating if the email exists
     */
    suspend fun isEmailExists(email: String): Boolean

    /**
     * Updates an existing user's information in Firestore
     * @param user The updated user model
     * @return Boolean indicating success or failure
     */
    suspend fun updateUser(user: UserModel): Boolean

    /**
     * Deletes a user's account and their associated data
     * @param userId The Firebase UID of the user to delete
     * @return Boolean indicating success or failure
     */
    suspend fun deleteUser(userId: String): Boolean

    /**
     * Signs out the current user from Firebase Authentication
     */
    fun logout()

    /**
     * Uploads a profile image to Cloudinary and returns the image URL
     */
    suspend fun uploadProfileImage(imageUri: Uri): String?

    /**
     * Syncs user profile to both Firestore and Realtime Database
     * @param user The Firebase user to sync
     * @param userModel The user model with complete profile data
     */
    suspend fun syncUserProfileToFirebase(user: FirebaseUser, userModel: UserModel)

    /**
     * Finds a user by username or email in Firestore
     * @param query The username or email to search for
     * @return UserModel if found, null otherwise
     */
    suspend fun findUserByEmailOrUsername(query: String): UserModel?

    /**
     * Updates the FCM token for the current user in both Firestore and Realtime Database
     */
    suspend fun updateFcmToken(userId: String, fcmToken: String)

    /**
     * Fetch a user by their UID
     */
    suspend fun findUserById(userId: String): UserModel?
}
