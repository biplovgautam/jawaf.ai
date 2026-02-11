package com.example.jawafai.utils

object LoginValidator {
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
}
