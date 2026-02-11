package com.example.jawafai

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.AuthResult
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.OnCompleteListener
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.ArgumentMatchers.any
import junit.framework.TestCase.assertEquals

class LoginValidatorTest {

    @Mock
    private lateinit var mockFirebaseAuth: FirebaseAuth

    @Mock
    private lateinit var mockTask: Task<AuthResult>

    @Captor
    private lateinit var captor: ArgumentCaptor<OnCompleteListener<AuthResult>>

    private lateinit var loginValidator: LoginValidator

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        loginValidator = LoginValidator(mockFirebaseAuth)
    }

    @Test
    fun `valid username and password`() {
        val result = loginValidator.isValidLogin("validUser@gmail.com", "validPass123")
        assertTrue(result)
    }

    @Test
    fun `empty username`() {
        val result = loginValidator.isValidLogin("", "validPass123")
        assertFalse(result)
    }

    @Test
    fun `password too short`() {
        val result = loginValidator.isValidLogin("validUser@gmail.com", "123")
        assertFalse(result)
    }

    @Test
    fun `invalid gmail username`() {
        val result = loginValidator.isValidLogin("invalidUser@yahoo.com", "validPass123")
        assertFalse(result)
    }

    @Test
    fun `blank username`() {
        val result = loginValidator.isValidLogin("   ", "validPass123")
        assertFalse(result)
    }

    @Test
    fun `username without at symbol`() {
        val result = loginValidator.isValidLogin("invalidusergmail.com", "validPass123")
        assertFalse(result)
    }

    @Test
    fun `password exactly 6 characters`() {
        val result = loginValidator.isValidLogin("validUser@gmail.com", "123456")
        assertTrue(result)
    }

    @Test
    fun `password with special characters`() {
        val result = loginValidator.isValidLogin("validUser@gmail.com", "pass@123!")
        assertTrue(result)
    }

    @Test
    fun `firebase auth login successful`() {
        val email = "test@gmail.com"
        val password = "testPassword"
        var authResult = "Initial Value"

        // Mock successful Firebase authentication
        `when`(mockTask.isSuccessful).thenReturn(true)
        `when`(mockFirebaseAuth.signInWithEmailAndPassword(any(), any()))
            .thenReturn(mockTask)

        // Define callback for authentication result
        val callback = { success: Boolean, message: String? ->
            authResult = message ?: "Callback message is null"
        }

        // Call Firebase authentication through LoginValidator
        loginValidator.authenticateWithFirebase(email, password, callback)

        // Verify Firebase auth was called and capture the listener
        verify(mockTask).addOnCompleteListener(captor.capture())
        captor.value.onComplete(mockTask)

        // Assert successful authentication
        assertEquals("Authentication successful", authResult)
    }

    @Test
    fun `firebase auth login failed`() {
        val email = "test@gmail.com"
        val password = "wrongPassword"
        var authResult = "Initial Value"

        // Mock failed Firebase authentication
        `when`(mockTask.isSuccessful).thenReturn(false)
        `when`(mockTask.exception).thenReturn(RuntimeException("Invalid credentials"))
        `when`(mockFirebaseAuth.signInWithEmailAndPassword(any(), any()))
            .thenReturn(mockTask)

        // Define callback for authentication result
        val callback = { success: Boolean, message: String? ->
            authResult = message ?: "Callback message is null"
        }

        // Call Firebase authentication through LoginValidator
        loginValidator.authenticateWithFirebase(email, password, callback)

        // Verify Firebase auth was called and capture the listener
        verify(mockTask).addOnCompleteListener(captor.capture())
        captor.value.onComplete(mockTask)

        // Assert failed authentication
        assertEquals("Invalid credentials", authResult)
    }
}

class LoginValidator(private val firebaseAuth: FirebaseAuth) {
    private val EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@gmail\\.com$".toRegex()

    fun isValidLogin(username: String, password: String): Boolean {
        // Username must be a valid Gmail address
        if (username.isBlank() || !EMAIL_REGEX.matches(username)) {
            return false
        }
        // Password must be at least 6 characters
        if (password.length < 6) {
            return false
        }
        return true
    }

    fun authenticateWithFirebase(email: String, password: String, callback: (Boolean, String?) -> Unit) {
        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    callback(true, "Authentication successful")
                } else {
                    callback(false, task.exception?.message)
                }
            }
    }
}
