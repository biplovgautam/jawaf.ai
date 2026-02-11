package com.example.jawafai.utils

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

object UserMigrationUtils {

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance("https://jawafai-d2c23-default-rtdb.firebaseio.com/")
    private val usersRef = database.getReference("users")

    /**
     * Saves the current authenticated user to Realtime Database
     * Call this after user login or in your main screen
     */
    suspend fun saveCurrentUserToDatabase() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            try {
                println("🔄 Migrating current user to database...")

                val userProfile = mapOf(
                    "email" to (currentUser.email ?: ""),
                    "username" to extractUsernameFromEmail(currentUser.email ?: ""),
                    "displayName" to (currentUser.displayName ?: extractUsernameFromEmail(currentUser.email ?: "")),
                    "profileImageUrl" to currentUser.photoUrl?.toString(),
                    "createdAt" to System.currentTimeMillis(),
                    "lastLoginAt" to System.currentTimeMillis()
                )

                // Save using the Firebase Auth UID as the key
                usersRef.child(currentUser.uid).setValue(userProfile).await()

                println("✅ Current user saved to database:")
                println("   UID: ${currentUser.uid}")
                println("   Email: ${currentUser.email}")
                println("   Display Name: ${currentUser.displayName}")

            } catch (e: Exception) {
                println("❌ Error saving user to database: ${e.message}")
                e.printStackTrace()
            }
        } else {
            println("⚠️ No authenticated user found")
        }
    }

    /**
     * Creates a username from email by taking the part before @
     */
    private fun extractUsernameFromEmail(email: String): String {
        return email.substringBefore("@").lowercase()
    }

    /**
     * Checks if current user exists in database
     */
    suspend fun checkIfUserExistsInDatabase(): Boolean {
        val currentUser = auth.currentUser
        return if (currentUser != null) {
            try {
                val snapshot = usersRef.child(currentUser.uid).get().await()
                val exists = snapshot.exists()
                println("🔍 User exists in database: $exists")
                exists
            } catch (e: Exception) {
                println("❌ Error checking user existence: ${e.message}")
                false
            }
        } else {
            false
        }
    }

    /**
     * Debug function to show all users in the database
     */
    suspend fun showAllUsersInDatabase() {
        try {
            println("📋 Listing all users in database:")
            val snapshot = usersRef.get().await()

            if (snapshot.exists()) {
                snapshot.children.forEach { userSnapshot ->
                    val userId = userSnapshot.key
                    val email = userSnapshot.child("email").getValue(String::class.java)
                    val username = userSnapshot.child("username").getValue(String::class.java)
                    val displayName = userSnapshot.child("displayName").getValue(String::class.java)

                    println("   User ID: $userId")
                    println("   Email: $email")
                    println("   Username: $username")
                    println("   Display Name: $displayName")
                    println("   ---")
                }
                println("📊 Total users: ${snapshot.childrenCount}")
            } else {
                println("📭 No users found in database")
            }
        } catch (e: Exception) {
            println("❌ Error listing users: ${e.message}")
        }
    }
}
