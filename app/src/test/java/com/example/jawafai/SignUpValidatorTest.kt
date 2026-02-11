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

class SignUpValidatorTest {

    @Mock
    private lateinit var mockFirebaseAuth: FirebaseAuth

    @Mock
    private lateinit var mockTask: Task<AuthResult>

    @Captor
    private lateinit var captor: ArgumentCaptor<OnCompleteListener<AuthResult>>

    private lateinit var signUpValidator: SignUpValidator

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        signUpValidator = SignUpValidator(mockFirebaseAuth)
    }

    @Test
    fun `valid signup credentials`() {
        val result = signUpValidator.isValidSignUp(
            email = "newuser@gmail.com",
            password = "validPass123",
            confirmPassword = "validPass123",
            username = "validusername"
        )
        assertTrue(result)
    }

    @Test
    fun `empty email`() {
        val result = signUpValidator.isValidSignUp(
            email = "",
            password = "validPass123",
            confirmPassword = "validPass123",
            username = "validusername"
        )
        assertFalse(result)
    }

    @Test
    fun `password too short`() {
        val result = signUpValidator.isValidSignUp(
            email = "newuser@gmail.com",
            password = "123",
            confirmPassword = "123",
            username = "validusername"
        )
        assertFalse(result)
    }

    @Test
    fun `invalid gmail email`() {
        val result = signUpValidator.isValidSignUp(
            email = "newuser@yahoo.com",
            password = "validPass123",
            confirmPassword = "validPass123",
            username = "validusername"
        )
        assertFalse(result)
    }

    @Test
    fun `passwords do not match`() {
        val result = signUpValidator.isValidSignUp(
            email = "newuser@gmail.com",
            password = "validPass123",
            confirmPassword = "differentPass456",
            username = "validusername"
        )
        assertFalse(result)
    }

    @Test
    fun `empty username`() {
        val result = signUpValidator.isValidSignUp(
            email = "newuser@gmail.com",
            password = "validPass123",
            confirmPassword = "validPass123",
            username = ""
        )
        assertFalse(result)
    }

    @Test
    fun `username too short`() {
        val result = signUpValidator.isValidSignUp(
            email = "newuser@gmail.com",
            password = "validPass123",
            confirmPassword = "validPass123",
            username = "ab"
        )
        assertFalse(result)
    }

    @Test
    fun `password exactly 6 characters`() {
        val result = signUpValidator.isValidSignUp(
            email = "newuser@gmail.com",
            password = "123456",
            confirmPassword = "123456",
            username = "validuser"
        )
        assertTrue(result)
    }

    @Test
    fun `username with special characters`() {
        val result = signUpValidator.isValidSignUp(
            email = "newuser@gmail.com",
            password = "validPass123",
            confirmPassword = "validPass123",
            username = "user@123"
        )
        assertFalse(result)
    }

    @Test
    fun `blank password confirmation`() {
        val result = signUpValidator.isValidSignUp(
            email = "newuser@gmail.com",
            password = "validPass123",
            confirmPassword = "   ",
            username = "validuser"
        )
        assertFalse(result)
    }

    @Test
    fun `firebase auth signup successful`() {
        val email = "test@gmail.com"
        val password = "testPassword"
        var authResult = "Initial Value"

        // Mock successful Firebase authentication
        `when`(mockTask.isSuccessful).thenReturn(true)
        `when`(mockFirebaseAuth.createUserWithEmailAndPassword(any(), any()))
            .thenReturn(mockTask)

        // Define callback for authentication result
        val callback = { success: Boolean, message: String? ->
            authResult = message ?: "Callback message is null"
        }

        // Call Firebase signup through SignUpValidator
        signUpValidator.createAccountWithFirebase(email, password, callback)

        // Verify Firebase auth was called and capture the listener
        verify(mockTask).addOnCompleteListener(captor.capture())
        captor.value.onComplete(mockTask)

        // Assert successful signup
        assertEquals("Account created successfully", authResult)
    }

    @Test
    fun `firebase auth signup failed`() {
        val email = "test@gmail.com"
        val password = "testPassword"
        var authResult = "Initial Value"

        // Mock failed Firebase authentication
        `when`(mockTask.isSuccessful).thenReturn(false)
        `when`(mockTask.exception).thenReturn(RuntimeException("Email already exists"))
        `when`(mockFirebaseAuth.createUserWithEmailAndPassword(any(), any()))
            .thenReturn(mockTask)

        // Define callback for authentication result
        val callback = { success: Boolean, message: String? ->
            authResult = message ?: "Callback message is null"
        }

        // Call Firebase signup through SignUpValidator
        signUpValidator.createAccountWithFirebase(email, password, callback)

        // Verify Firebase auth was called and capture the listener
        verify(mockTask).addOnCompleteListener(captor.capture())
        captor.value.onComplete(mockTask)

        // Assert failed signup
        assertEquals("Email already exists", authResult)
    }
}

class SignUpValidator(private val firebaseAuth: FirebaseAuth) {
    private val EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@gmail\\.com$".toRegex()
    private val USERNAME_REGEX = "^[A-Za-z0-9_]{3,20}$".toRegex()

    fun isValidSignUp(email: String, password: String, confirmPassword: String, username: String): Boolean {
        // Email must be a valid Gmail address
        if (email.isBlank() || !EMAIL_REGEX.matches(email)) {
            return false
        }

        // Password must be at least 6 characters
        if (password.length < 6) {
            return false
        }

        // Passwords must match
        if (password != confirmPassword) {
            return false
        }

        // Username must be 3-20 characters, alphanumeric and underscore only
        if (username.isBlank() || !USERNAME_REGEX.matches(username)) {
            return false
        }

        return true
    }

    fun createAccountWithFirebase(email: String, password: String, callback: (Boolean, String?) -> Unit) {
        firebaseAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    callback(true, "Account created successfully")
                } else {
                    callback(false, task.exception?.message)
                }
            }
    }
}
