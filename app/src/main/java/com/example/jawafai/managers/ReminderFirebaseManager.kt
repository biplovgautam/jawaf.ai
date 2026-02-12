package com.example.jawafai.managers

import android.util.Log
import com.example.jawafai.model.Reminder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Firebase Manager for Reminder operations
 * Handles CRUD operations for reminders in Firebase Realtime Database
 */
object ReminderFirebaseManager {

    private const val TAG = "ReminderFirebaseManager"
    private const val REMINDERS_PATH = "reminders"

    private val database: DatabaseReference by lazy {
        FirebaseDatabase.getInstance().reference
    }

    private val currentUserId: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid

    /**
     * Get the reminders reference for current user
     */
    private fun getUserRemindersRef(): DatabaseReference? {
        val userId = currentUserId ?: return null
        return database.child(REMINDERS_PATH).child(userId)
    }

    /**
     * Save a new reminder to Firebase
     */
    suspend fun saveReminder(reminder: Reminder): Result<Reminder> = suspendCancellableCoroutine { continuation ->
        val ref = getUserRemindersRef()
        if (ref == null) {
            continuation.resume(Result.failure(Exception("User not logged in")))
            return@suspendCancellableCoroutine
        }

        val userId = currentUserId ?: run {
            continuation.resume(Result.failure(Exception("User not logged in")))
            return@suspendCancellableCoroutine
        }

        // Generate ID if not provided
        val reminderId = if (reminder.id.isBlank()) {
            ref.push().key ?: java.util.UUID.randomUUID().toString()
        } else {
            reminder.id
        }

        val reminderWithId = reminder.copy(
            id = reminderId,
            userId = userId,
            updatedAt = System.currentTimeMillis()
        )

        ref.child(reminderId).setValue(reminderWithId.toMap())
            .addOnSuccessListener {
                Log.d(TAG, "✅ Reminder saved successfully: $reminderId")
                continuation.resume(Result.success(reminderWithId))
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to save reminder: ${e.message}", e)
                continuation.resume(Result.failure(e))
            }
    }

    /**
     * Update an existing reminder
     */
    suspend fun updateReminder(reminder: Reminder): Result<Reminder> = suspendCancellableCoroutine { continuation ->
        val ref = getUserRemindersRef()
        if (ref == null) {
            continuation.resume(Result.failure(Exception("User not logged in")))
            return@suspendCancellableCoroutine
        }

        val updatedReminder = reminder.copy(updatedAt = System.currentTimeMillis())

        ref.child(reminder.id).updateChildren(updatedReminder.toMap())
            .addOnSuccessListener {
                Log.d(TAG, "✅ Reminder updated successfully: ${reminder.id}")
                continuation.resume(Result.success(updatedReminder))
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to update reminder: ${e.message}", e)
                continuation.resume(Result.failure(e))
            }
    }

    /**
     * Delete a reminder
     */
    suspend fun deleteReminder(reminderId: String): Result<Unit> = suspendCancellableCoroutine { continuation ->
        val ref = getUserRemindersRef()
        if (ref == null) {
            continuation.resume(Result.failure(Exception("User not logged in")))
            return@suspendCancellableCoroutine
        }

        ref.child(reminderId).removeValue()
            .addOnSuccessListener {
                Log.d(TAG, "✅ Reminder deleted successfully: $reminderId")
                continuation.resume(Result.success(Unit))
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to delete reminder: ${e.message}", e)
                continuation.resume(Result.failure(e))
            }
    }

    /**
     * Get a single reminder by ID
     */
    suspend fun getReminder(reminderId: String): Result<Reminder?> = suspendCancellableCoroutine { continuation ->
        val ref = getUserRemindersRef()
        if (ref == null) {
            continuation.resume(Result.failure(Exception("User not logged in")))
            return@suspendCancellableCoroutine
        }

        ref.child(reminderId).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val data = snapshot.value as? Map<String, Any?>
                    val reminder = data?.let { Reminder.fromMap(it) }
                    continuation.resume(Result.success(reminder))
                } else {
                    continuation.resume(Result.success(null))
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to get reminder: ${e.message}", e)
                continuation.resume(Result.failure(e))
            }
    }

    /**
     * Get all reminders for current user
     * Note: We fetch all and sort locally to avoid Firebase index requirements
     */
    suspend fun getAllReminders(): Result<List<Reminder>> = suspendCancellableCoroutine { continuation ->
        val ref = getUserRemindersRef()
        if (ref == null) {
            continuation.resume(Result.failure(Exception("User not logged in")))
            return@suspendCancellableCoroutine
        }

        // Fetch all reminders without ordering (to avoid index requirement)
        ref.get()
            .addOnSuccessListener { snapshot ->
                val reminders = mutableListOf<Reminder>()
                snapshot.children.forEach { child ->
                    val data = child.value as? Map<String, Any?>
                    data?.let {
                        reminders.add(Reminder.fromMap(it))
                    }
                }
                // Sort locally by eventDate
                val sortedReminders = reminders.sortedBy { it.eventDate }
                Log.d(TAG, "✅ Fetched ${sortedReminders.size} reminders")
                continuation.resume(Result.success(sortedReminders))
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to get reminders: ${e.message}", e)
                continuation.resume(Result.failure(e))
            }
    }

    /**
     * Get upcoming reminders (from today onwards)
     * Note: We fetch all and filter/sort locally to avoid Firebase index requirements
     */
    suspend fun getUpcomingReminders(): Result<List<Reminder>> = suspendCancellableCoroutine { continuation ->
        val ref = getUserRemindersRef()
        if (ref == null) {
            continuation.resume(Result.failure(Exception("User not logged in")))
            return@suspendCancellableCoroutine
        }

        val todayStart = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        // Fetch all reminders and filter locally
        ref.get()
            .addOnSuccessListener { snapshot ->
                val reminders = mutableListOf<Reminder>()
                snapshot.children.forEach { child ->
                    val data = child.value as? Map<String, Any?>
                    data?.let {
                        val reminder = Reminder.fromMap(it)
                        // Filter: upcoming (eventDate >= today) and not completed
                        if (reminder.eventDate >= todayStart && !reminder.isCompleted) {
                            reminders.add(reminder)
                        }
                    }
                }
                // Sort locally by eventDate
                val sortedReminders = reminders.sortedBy { it.eventDate }
                Log.d(TAG, "✅ Fetched ${sortedReminders.size} upcoming reminders")
                continuation.resume(Result.success(sortedReminders))
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to get upcoming reminders: ${e.message}", e)
                continuation.resume(Result.failure(e))
            }
    }

    /**
     * Get reminders for a specific date
     * Note: We fetch all and filter locally to avoid Firebase index requirements
     */
    suspend fun getRemindersForDate(date: LocalDate): Result<List<Reminder>> = suspendCancellableCoroutine { continuation ->
        val ref = getUserRemindersRef()
        if (ref == null) {
            continuation.resume(Result.failure(Exception("User not logged in")))
            return@suspendCancellableCoroutine
        }

        val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // Fetch all reminders and filter locally
        ref.get()
            .addOnSuccessListener { snapshot ->
                val reminders = mutableListOf<Reminder>()
                snapshot.children.forEach { child ->
                    val data = child.value as? Map<String, Any?>
                    data?.let {
                        val reminder = Reminder.fromMap(it)
                        // Filter: reminders for this specific date
                        if (reminder.eventDate >= startOfDay && reminder.eventDate < endOfDay) {
                            reminders.add(reminder)
                        }
                    }
                }
                Log.d(TAG, "✅ Fetched ${reminders.size} reminders for $date")
                continuation.resume(Result.success(reminders.sortedBy { it.eventDate }))
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to get reminders for date: ${e.message}", e)
                continuation.resume(Result.failure(e))
            }
    }

    /**
     * Mark reminder as completed
     */
    suspend fun markAsCompleted(reminderId: String): Result<Unit> = suspendCancellableCoroutine { continuation ->
        val ref = getUserRemindersRef()
        if (ref == null) {
            continuation.resume(Result.failure(Exception("User not logged in")))
            return@suspendCancellableCoroutine
        }

        val updates = mapOf(
            "isCompleted" to true,
            "updatedAt" to System.currentTimeMillis()
        )

        ref.child(reminderId).updateChildren(updates)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Reminder marked as completed: $reminderId")
                continuation.resume(Result.success(Unit))
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to mark reminder as completed: ${e.message}", e)
                continuation.resume(Result.failure(e))
            }
    }

    /**
     * Listen to reminders in real-time using Flow
     * Note: We fetch all and sort locally to avoid Firebase index requirements
     */
    fun observeReminders(): Flow<List<Reminder>> = callbackFlow {
        val ref = getUserRemindersRef()
        if (ref == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val reminders = mutableListOf<Reminder>()
                snapshot.children.forEach { child ->
                    val data = child.value as? Map<String, Any?>
                    data?.let {
                        reminders.add(Reminder.fromMap(it))
                    }
                }
                // Sort locally by eventDate
                trySend(reminders.sortedBy { it.eventDate })
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "❌ Reminder observation cancelled: ${error.message}")
            }
        }

        // Listen to all reminders without ordering (to avoid index requirement)
        ref.addValueEventListener(listener)

        awaitClose {
            ref.removeEventListener(listener)
        }
    }

    /**
     * Get reminders that have events in a date range (for calendar view)
     * Note: We fetch all and filter locally to avoid Firebase index requirements
     */
    suspend fun getRemindersInRange(startDate: LocalDate, endDate: LocalDate): Result<Map<LocalDate, List<Reminder>>> = suspendCancellableCoroutine { continuation ->
        val ref = getUserRemindersRef()
        if (ref == null) {
            continuation.resume(Result.failure(Exception("User not logged in")))
            return@suspendCancellableCoroutine
        }

        val startTimestamp = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endTimestamp = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // Fetch all reminders and filter locally
        ref.get()
            .addOnSuccessListener { snapshot ->
                val remindersByDate = mutableMapOf<LocalDate, MutableList<Reminder>>()

                snapshot.children.forEach { child ->
                    val data = child.value as? Map<String, Any?>
                    data?.let {
                        val reminder = Reminder.fromMap(it)
                        // Filter: only reminders within the date range
                        if (reminder.eventDate >= startTimestamp && reminder.eventDate < endTimestamp) {
                            val date = reminder.getLocalDate()
                            remindersByDate.getOrPut(date) { mutableListOf() }.add(reminder)
                        }
                    }
                }

                // Sort reminders within each date by time
                remindersByDate.forEach { (_, list) ->
                    list.sortBy { it.eventDate }
                }

                Log.d(TAG, "✅ Fetched reminders for ${remindersByDate.size} dates")
                continuation.resume(Result.success(remindersByDate))
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to get reminders in range: ${e.message}", e)
                continuation.resume(Result.failure(e))
            }
    }
}

