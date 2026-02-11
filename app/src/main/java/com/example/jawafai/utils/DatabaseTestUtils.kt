package com.example.jawafai.utils

import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

object DatabaseTestUtils {

    private val database = FirebaseDatabase.getInstance("https://jawafai-d2c23-default-rtdb.firebaseio.com/")
    private val usersRef = database.getReference("users")

    suspend fun createTestUsers() {
        try {
            println("🔧 Creating test users...")

            // Test User 1
            val testUser1 = mapOf(
                "email" to "john@example.com",
                "username" to "johndoe",
                "displayName" to "John Doe",
                "profileImageUrl" to null
            )

            // Test User 2
            val testUser2 = mapOf(
                "email" to "jane@example.com",
                "username" to "janedoe",
                "displayName" to "Jane Doe",
                "profileImageUrl" to null
            )

            // Test User 3
            val testUser3 = mapOf(
                "email" to "test@jawafai.com",
                "username" to "testuser",
                "displayName" to "Test User",
                "profileImageUrl" to null
            )

            // Add users to Firebase with auto-generated IDs
            val user1Id = usersRef.push().key!!
            val user2Id = usersRef.push().key!!
            val user3Id = usersRef.push().key!!

            usersRef.child(user1Id).setValue(testUser1).await()
            usersRef.child(user2Id).setValue(testUser2).await()
            usersRef.child(user3Id).setValue(testUser3).await()

            println("✅ Test users created successfully!")
            println("🔧 User IDs: $user1Id, $user2Id, $user3Id")

        } catch (e: Exception) {
            println("❌ Error creating test users: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun getCurrentUserProfile(userId: String): Map<String, Any?>? {
        return try {
            val snapshot = usersRef.child(userId).get().await()
            if (snapshot.exists()) {
                mapOf(
                    "userId" to snapshot.key,
                    "email" to snapshot.child("email").getValue(String::class.java),
                    "username" to snapshot.child("username").getValue(String::class.java),
                    "displayName" to snapshot.child("displayName").getValue(String::class.java),
                    "profileImageUrl" to snapshot.child("profileImageUrl").getValue(String::class.java)
                )
            } else {
                null
            }
        } catch (e: Exception) {
            println("❌ Error getting user profile: ${e.message}")
            null
        }
    }
}
