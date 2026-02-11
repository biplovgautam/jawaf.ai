package com.example.jawafai.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jawafai.model.UserModel
import com.example.jawafai.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class UserViewModel(
    private val repository: UserRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    // States
    private val _userState = MutableLiveData<UserOperationResult>(UserOperationResult.Initial)
    val userState: LiveData<UserOperationResult> = _userState

    private val _userProfile = MutableLiveData<UserModel?>()
    val userProfile: LiveData<UserModel?> = _userProfile

    // Add email validation state
    private val _emailValidationState = MutableLiveData<EmailValidationState>()
    val emailValidationState: LiveData<EmailValidationState> = _emailValidationState

    sealed class UserOperationResult {
        object Initial : UserOperationResult()
        object Loading : UserOperationResult()
        data class Success(val message: String = "") : UserOperationResult()
        data class Error(val message: String) : UserOperationResult()
        object PasswordResetSent : UserOperationResult() // Added for password reset
    }

    sealed class EmailValidationState {
        object Initial : EmailValidationState()
        object Checking : EmailValidationState()
        object Valid : EmailValidationState()
        object Invalid : EmailValidationState()
    }

    // Added login function for Firebase authentication
    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                _userState.value = UserOperationResult.Loading

                // Use suspendCoroutine to handle the Firebase callback-based auth
                val user = suspendCoroutine<FirebaseUser?> { continuation ->
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnSuccessListener { authResult ->
                            continuation.resume(authResult.user)
                        }
                        .addOnFailureListener { e ->
                            continuation.resumeWithException(e)
                        }
                }

                if (user != null) {
                    // After successful login, sync user profile to both databases
                    try {
                        // Fetch user profile from Firestore
                        val userDocRef = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(user.uid)
                        val userDoc = userDocRef.get().await()

                        if (userDoc.exists()) {
                            // User profile exists, sync to Realtime Database
                            val userModel = com.example.jawafai.model.UserModel.fromMap(userDoc.data ?: emptyMap())
                            repository.syncUserProfileToFirebase(user, userModel)
                            Log.d("UserViewModel", "✅ User profile synced after login")
                        } else {
                            // Create minimal profile if doesn't exist
                            val defaultUsername = user.email?.substringBefore('@') ?: "user${user.uid.take(6)}"
                            val displayName = user.displayName
                            val firstName = displayName?.split(" ")?.getOrNull(0) ?: ""
                            val lastName = displayName?.split(" ")?.getOrNull(1) ?: ""

                            val defaultUser = UserModel(
                                id = user.uid,
                                email = user.email ?: "",
                                username = defaultUsername,
                                firstName = firstName,
                                lastName = lastName,
                                imageUrl = user.photoUrl?.toString()
                            )

                            // Sync the default profile to both databases
                            repository.syncUserProfileToFirebase(user, defaultUser)
                            Log.d("UserViewModel", "✅ Default user profile created and synced")
                        }
                    } catch (e: Exception) {
                        Log.e("UserViewModel", "Error syncing user profile after login: ${e.message}", e)
                        // Don't fail login if sync fails
                    }

                    _userState.value = UserOperationResult.Success("Login successful")
                } else {
                    _userState.value = UserOperationResult.Error("Login failed: Unknown error")
                }
            } catch (e: Exception) {
                _userState.value = UserOperationResult.Error("Login failed: ${e.message}")
            }
        }
    }

    fun register(email: String, password: String, user: UserModel, imageUri: Uri?) {
        viewModelScope.launch {
            _userState.value = UserOperationResult.Loading
            try {
                // Check if the username is already taken
                try {
                    if (repository.isUsernameExists(user.username)) {
                        _userState.value = UserOperationResult.Error("Username already exists")
                        return@launch
                    }
                } catch (e: Exception) {
                    // If checking username fails due to permissions, log it but continue
                    Log.w("UserViewModel", "Failed to check if username exists: ${e.message}")
                    // Continue with registration anyway
                }

                // 1. Upload image if it exists
                val imageUrl = if (imageUri != null) {
                    try {
                        Log.d("UserViewModel", "Uploading profile image...")
                        repository.uploadProfileImage(imageUri)
                    } catch (e: Exception) {
                        Log.e("UserViewModel", "Image upload failed: ${e.message}")
                        null // Continue without image
                    }
                } else {
                    null
                }

                // 2. Create user model with the image URL (might be null)
                val userWithImage = user.copy(imageUrl = imageUrl)

                // 3. Register user in auth and firestore
                Log.d("UserViewModel", "Registering user details...")
                try {
                    val result = repository.registerUser(userWithImage, password)
                    if (result) {
                        _userState.value = UserOperationResult.Success("Registration successful")
                    } else {
                        _userState.value = UserOperationResult.Error("Registration failed")
                    }
                } catch (e: Exception) {
                    // Special handling for permission denied errors
                    if (e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true) {
                        Log.w("UserViewModel", "Firestore permission denied but auth succeeded. " +
                                "Registration considered successful, but profile data not saved.")
                        _userState.value = UserOperationResult.Success("Registration successful. You can update your profile later.")
                    } else {
                        throw e  // Re-throw other exceptions to be caught by outer catch block
                    }
                }
            } catch (e: Exception) {
                Log.e("UserViewModel", "Exception during registration process", e)
                // Return more user-friendly error messages
                val errorMessage = when {
                    e.message?.contains("username", ignoreCase = true) == true -> "Username already exists"
                    e.message?.contains("email", ignoreCase = true) == true ||
                    e.message?.contains("already in use", ignoreCase = true) == true -> "Email already in use"
                    else -> e.message ?: "An unknown error occurred"
                }
                _userState.value = UserOperationResult.Error(errorMessage)
            }
        }
    }

    fun fetchUserProfile() {
        viewModelScope.launch {
            _userState.value = UserOperationResult.Loading
            try {
                val firebaseUser = auth.currentUser
                if (firebaseUser != null) {
                    val userDocRef = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(firebaseUser.uid)
                    val userDoc = userDocRef.get().await()

                    if (userDoc.exists()) {
                        // Profile exists, load it
                        val userMap = userDoc.data ?: emptyMap<String, Any>()

                        // Check if persona is completed by fetching persona data
                        // This line had the error - reference doesn't exist, replaced with correct path
                        val personaRef = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(firebaseUser.uid)
                            .collection("persona")

                        // Create UserModel with personaCompleted status
                        val userModel = com.example.jawafai.model.UserModel.fromMap(userMap)

                        try {
                            // Try to fetch persona data to determine completion
                            val personaSnapshot = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(firebaseUser.uid)
                                .collection("persona")
                                .get()
                                .await()

                            // Consider persona completed if there are at least 5 answers
                            val personaCompleted = !personaSnapshot.isEmpty && personaSnapshot.size() >= 5

                            // Update the user model with persona completion status
                            _userProfile.value = userModel.copy(personaCompleted = personaCompleted)
                        } catch (e: Exception) {
                            // If there's an error fetching persona data, just use the user model as is
                            _userProfile.value = userModel
                        }

                        _userState.value = UserOperationResult.Success()
                    } else {
                        // Profile doesn't exist, create a default one
                        Log.w("UserViewModel", "User document not found for UID: ${firebaseUser.uid}. Creating a default one.")
                        val defaultUsername = firebaseUser.email?.substringBefore('@') ?: "user${firebaseUser.uid.take(6)}"
                        val displayName = firebaseUser.displayName
                        val firstName = displayName?.split(" ")?.getOrNull(0) ?: ""
                        val lastName = displayName?.split(" ")?.getOrNull(1) ?: ""

                        val defaultUser = UserModel(
                            id = firebaseUser.uid,
                            email = firebaseUser.email ?: "",
                            username = defaultUsername,
                            firstName = firstName,
                            lastName = lastName,
                            imageUrl = firebaseUser.photoUrl?.toString()
                        )

                        // Save the new default profile to Firestore and update local state
                        userDocRef.set(defaultUser.toMap()).await()
                        _userProfile.value = defaultUser
                        _userState.value = UserOperationResult.Success("Default profile created.")
                    }
                } else {
                    _userProfile.value = null
                    _userState.value = UserOperationResult.Error("User not logged in.")
                }
            } catch (e: Exception) {
                _userProfile.value = null
                _userState.value = UserOperationResult.Error(e.message ?: "Failed to fetch user profile.")
                Log.e("UserViewModel", "Error fetching user profile", e)
            }
        }
    }

    fun updateUser(user: UserModel) {
        viewModelScope.launch {
            _userState.value = UserOperationResult.Loading
            try {
                val result = repository.updateUser(user)
                if (result) {
                    // Update the local profile directly for a smoother experience
                    _userProfile.value = user
                    _userState.value = UserOperationResult.Success("Profile updated successfully")
                } else {
                    _userState.value = UserOperationResult.Error("Update failed")
                }
            } catch (e: Exception) {
                _userState.value = UserOperationResult.Error(e.message ?: "Unknown error occurred")
            }
        }
    }

    fun deleteUser(userId: String) {
        viewModelScope.launch {
            try {
                _userState.value = UserOperationResult.Loading
                val result = repository.deleteUser(userId)
                if (result) {
                    _userState.value = UserOperationResult.Success("Account deleted successfully")
                } else {
                    _userState.value = UserOperationResult.Error("Delete failed")
                }
            } catch (e: Exception) {
                _userState.value = UserOperationResult.Error(e.message ?: "Unknown error occurred")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                repository.logout()
                _userState.value = UserOperationResult.Success("Logged out successfully")
            } catch (e: Exception) {
                _userState.value = UserOperationResult.Error(e.message ?: "Logout failed")
            }
        }
    }

    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    fun resetState() {
        _userState.value = UserOperationResult.Initial
        _userProfile.value = null
    }

    /**
     * Uploads a profile image and returns the URL.
     * This is used by the ProfileScreen to update the user's avatar.
     * Note: This does NOT save the URL to the database. The user must click "Save" on the profile screen
     * to persist the change via the updateUser function.
     */
    fun uploadProfileImage(imageUri: Uri, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            _userState.value = UserOperationResult.Loading
            try {
                val url = repository.uploadProfileImage(imageUri)
                if (url != null) {
                    // Success, but don't show a message yet.
                    // The user has to save the profile for the change to be permanent.
                    _userState.value = UserOperationResult.Initial
                    onResult(url)
                } else {
                    _userState.value = UserOperationResult.Error("Image upload failed")
                    onResult(null)
                }
            } catch (e: Exception) {
                Log.e("UserViewModel", "Exception during image upload", e)
                _userState.value = UserOperationResult.Error(e.message ?: "Image upload failed")
                onResult(null)
            }
        }
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            _userState.value = UserOperationResult.Loading
            try {
                suspendCoroutine<Unit> { continuation ->
                    auth.sendPasswordResetEmail(email)
                        .addOnSuccessListener { continuation.resume(Unit) }
                        .addOnFailureListener { e ->
                            android.util.Log.e("UserViewModel", "Password reset failed: ${e.message}", e)
                            continuation.resumeWithException(e)
                        }
                }
                _userState.value = UserOperationResult.PasswordResetSent
            } catch (e: Exception) {
                android.util.Log.e("UserViewModel", "Password reset exception: ${e.message}", e)
                _userState.value = UserOperationResult.Error(e.message ?: "Failed to send reset email.")
            }
        }
    }

    // Add email validation function
    fun validateEmail(email: String) {
        viewModelScope.launch {
            if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                _emailValidationState.value = EmailValidationState.Invalid
                return@launch
            }

            _emailValidationState.value = EmailValidationState.Checking
            try {
                val exists = repository.isEmailExists(email)
                _emailValidationState.value = if (exists) EmailValidationState.Valid else EmailValidationState.Invalid
            } catch (e: Exception) {
                _emailValidationState.value = EmailValidationState.Invalid
            }
        }
    }

    fun resetEmailValidation() {
        _emailValidationState.value = EmailValidationState.Initial
    }
}
