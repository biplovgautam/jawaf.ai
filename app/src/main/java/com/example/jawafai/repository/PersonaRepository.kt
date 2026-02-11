package com.example.jawafai.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.snapshots
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Repository for managing user persona data in Firebase Realtime Database.
 */
class PersonaRepository private constructor() {
    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Path to the users' persona data in Firebase Realtime Database
     */
    private val personaPath = "users/%s/persona"

    companion object {
        @Volatile
        private var instance: PersonaRepository? = null

        fun getInstance(): PersonaRepository {
            return instance ?: synchronized(this) {
                instance ?: PersonaRepository().also { instance = it }
            }
        }
    }

    /**
     * Gets the current user ID or throws an exception if not logged in
     */
    private fun getCurrentUserId(): String {
        return auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
    }

    /**
     * Gets the persona data for the current user as a Flow
     * @return Flow emitting a map of question IDs to answers
     */
    fun getPersonaFlow(): Flow<Map<String, String>> = callbackFlow {
        val userId = getCurrentUserId()
        val personaRef = database.getReference(String.format(personaPath, userId))

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Update to use the KTX API pattern
                val personaMap = snapshot.children.associate {
                    it.key.toString() to (it.value?.toString() ?: "")
                }
                trySend(personaMap)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        personaRef.addValueEventListener(listener)

        awaitClose {
            personaRef.removeEventListener(listener)
        }
    }

    /**
     * Gets the persona data for a specific user
     * @param uid User ID to fetch persona for
     * @return Map of question IDs to answers
     */
    suspend fun getPersonaMap(uid: String): Map<String, String> {
        return try {
            val personaRef = database.getReference(String.format(personaPath, uid))
            val snapshot = personaRef.get().await()
            // Update to use the KTX API pattern
            snapshot.children.associate {
                it.key.toString() to (it.value?.toString() ?: "")
            }
        } catch (e: Exception) {
            // Log error here if needed
            emptyMap()
        }
    }

    /**
     * Saves the persona data for the current user
     * @param personaData Map of question IDs to answers
     * @return true if successful, false otherwise
     */
    suspend fun savePersona(personaData: Map<String, String>): Boolean = suspendCoroutine { continuation ->
        try {
            val userId = getCurrentUserId()
            val personaRef = database.getReference(String.format(personaPath, userId))

            personaRef.setValue(personaData)
                .addOnSuccessListener {
                    continuation.resume(true)
                }
                .addOnFailureListener { e ->
                    continuation.resume(false)
                }
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }

    /**
     * Deletes the persona data for the current user
     * @return true if successful, false otherwise
     */
    suspend fun deletePersona(): Boolean = suspendCoroutine { continuation ->
        try {
            val userId = getCurrentUserId()
            val personaRef = database.getReference(String.format(personaPath, userId))

            personaRef.removeValue()
                .addOnSuccessListener {
                    continuation.resume(true)
                }
                .addOnFailureListener { e ->
                    continuation.resume(false)
                }
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }
}
