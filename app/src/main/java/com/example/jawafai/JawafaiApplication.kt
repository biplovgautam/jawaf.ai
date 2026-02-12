package com.example.jawafai

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import android.util.Log
import com.example.jawafai.managers.CloudinaryManager
import com.example.jawafai.managers.ConnectedAppsManager
import com.example.jawafai.managers.NotificationFirebaseManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class JawafaiApplication : Application(), DefaultLifecycleObserver {

    companion object {
        const val PREFS_NAME = "JawafaiPrefs"
        const val PREF_REMEMBER_ME = "rememberMe"
        const val PREF_LAST_ACTIVITY_TIME = "lastActivityTime"
    }

    private var isAppInBackground = false
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super<Application>.onCreate()

        // Initialize CloudinaryManager
        CloudinaryManager.init(this)

        // Register lifecycle observer to monitor app state
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        // Initialize ConnectedAppsManager if user is logged in
        FirebaseAuth.getInstance().currentUser?.let {
            applicationScope.launch {
                try {
                    ConnectedAppsManager.initialize()
                    Log.d("JawafaiApp", "ConnectedAppsManager initialized")
                } catch (e: Exception) {
                    Log.e("JawafaiApp", "Failed to initialize ConnectedAppsManager: ${e.message}")
                }
            }

            // Load notifications from Firebase
            applicationScope.launch {
                try {
                    NotificationFirebaseManager.loadFromFirebase()
                    Log.d("JawafaiApp", "NotificationFirebaseManager loaded from Firebase")
                } catch (e: Exception) {
                    Log.e("JawafaiApp", "Failed to load notifications from Firebase: ${e.message}")
                }
            }
        }

        Log.d("JawafaiApp", "Application created")
    }

    override fun onStart(owner: LifecycleOwner) {
        super<DefaultLifecycleObserver>.onStart(owner)
        // App came to foreground
        isAppInBackground = false

        // Update last activity time
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putLong(PREF_LAST_ACTIVITY_TIME, System.currentTimeMillis())
            .apply()

        // Refresh ConnectedAppsManager when app comes to foreground
        FirebaseAuth.getInstance().currentUser?.let {
            applicationScope.launch {
                try {
                    ConnectedAppsManager.initialize()
                } catch (e: Exception) {
                    Log.e("JawafaiApp", "Failed to refresh ConnectedAppsManager: ${e.message}")
                }
            }
        }

        Log.d("JawafaiApp", "App came to foreground")
    }

    override fun onStop(owner: LifecycleOwner) {
        super<DefaultLifecycleObserver>.onStop(owner)
        // App went to background
        isAppInBackground = true

        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rememberMe = sharedPreferences.getBoolean(PREF_REMEMBER_ME, false)

        // Update last activity time
        sharedPreferences.edit()
            .putLong(PREF_LAST_ACTIVITY_TIME, System.currentTimeMillis())
            .apply()

        Log.d("JawafaiApp", "App went to background. Remember Me: $rememberMe")

        // If remember me is not checked, prepare for auto-logout
        // The actual logout will happen when the app is killed/destroyed
        if (!rememberMe && FirebaseAuth.getInstance().currentUser != null) {
            Log.d("JawafaiApp", "Remember Me not checked. User will be logged out when app is destroyed.")
        }
    }

    override fun onTerminate() {
        super.onTerminate()

        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rememberMe = sharedPreferences.getBoolean(PREF_REMEMBER_ME, false)

        // Auto-logout if remember me is not checked
        if (!rememberMe && FirebaseAuth.getInstance().currentUser != null) {
            FirebaseAuth.getInstance().signOut()

            // Clear all session data
            sharedPreferences.edit()
                .remove(PREF_REMEMBER_ME)
                .remove(PREF_LAST_ACTIVITY_TIME)
                .apply()

            Log.d("JawafaiApp", "User logged out due to app termination without Remember Me")
        }

        Log.d("JawafaiApp", "Application terminated")
    }

    /**
     * Check if user should be automatically logged out
     * Call this method when app starts to check if enough time has passed
     */
    fun checkAutoLogout() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rememberMe = sharedPreferences.getBoolean(PREF_REMEMBER_ME, false)
        val lastActivityTime = sharedPreferences.getLong(PREF_LAST_ACTIVITY_TIME, 0)
        val currentTime = System.currentTimeMillis()

        // If remember me is not checked and it's been more than 1 minute since last activity
        // (This helps detect if app was killed and restarted)
        if (!rememberMe && lastActivityTime > 0 && (currentTime - lastActivityTime) > 60000) {
            if (FirebaseAuth.getInstance().currentUser != null) {
                FirebaseAuth.getInstance().signOut()

                // Clear session data
                sharedPreferences.edit()
                    .remove(PREF_REMEMBER_ME)
                    .remove(PREF_LAST_ACTIVITY_TIME)
                    .apply()

                Log.d("JawafaiApp", "User auto-logged out due to app restart without Remember Me")
            }
        }
    }

    /**
     * Save remember me preference
     */
    fun saveRememberMePreference(rememberMe: Boolean) {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putBoolean(PREF_REMEMBER_ME, rememberMe)
            .putLong(PREF_LAST_ACTIVITY_TIME, System.currentTimeMillis())
            .apply()

        Log.d("JawafaiApp", "Remember Me preference saved: $rememberMe")
    }

    /**
     * Get remember me preference
     */
    fun getRememberMePreference(): Boolean {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(PREF_REMEMBER_ME, false)
    }

    /**
     * Clear all session data on logout
     */
    fun clearSessionData() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .remove(PREF_REMEMBER_ME)
            .remove(PREF_LAST_ACTIVITY_TIME)
            .apply()

        Log.d("JawafaiApp", "Session data cleared")
    }
}
