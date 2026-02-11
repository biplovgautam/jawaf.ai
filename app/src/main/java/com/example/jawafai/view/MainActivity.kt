package com.example.jawafai.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.jawafai.ui.theme.JawafaiTheme
import com.google.firebase.auth.FirebaseAuth
import androidx.core.view.WindowCompat
import com.example.jawafai.view.auth.LoginActivity
import com.example.jawafai.view.auth.RegistrationActivity
import com.example.jawafai.view.dashboard.DashboardActivity
import com.example.jawafai.view.splash.OnboardingScreen
import com.example.jawafai.view.splash.SplashScreen
import com.example.jawafai.view.splash.WelcomeScreen
import com.example.jawafai.utils.WithNetworkMonitoring
import com.example.jawafai.utils.NotificationPermissionUtils
import android.util.Log

class MainActivity : ComponentActivity() {

    companion object {
        // Key for tracking if user has seen onboarding
        const val PREFS_NAME = "JawafaiPrefs"
        const val PREF_HAS_SEEN_ONBOARDING = "hasSeenOnboarding"
        const val PREF_REMEMBER_ME = "rememberMe"
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable full screen immersive mode
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        setContent {
            JawafaiTheme {
                // Wrap entire app with network monitoring
                WithNetworkMonitoring {
                    MainNavigationFlow()
                }
            }
        }
    }

    @Composable
    private fun MainNavigationFlow() {
        val navController = rememberNavController()

        // Check user state synchronously
        val userState by remember {
            mutableStateOf(checkUserState())
        }

        // All users now go through splash screen first
        NavHost(
            navController = navController,
            startDestination = "splash"
        ) {
            // Splash screen - shown to ALL users (logged in and not logged in)
            composable("splash") {
                SplashScreen(
                    onNavigate = { destination ->
                        when (destination) {
                            "dashboard" -> {
                                // For logged-in users, navigate to dashboard after splash
                                val intent = Intent(this@MainActivity, DashboardActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            }
                            else -> {
                                // For non-logged-in users, continue with normal navigation
                                navController.navigate(destination) {
                                    popUpTo("splash") { inclusive = true }
                                }
                            }
                        }
                    },
                    checkUserState = userState // Pass the determined user state
                )
            }

            // Onboarding screens - shown only first time after installing
            composable("onboarding") {
                OnboardingScreen(
                    navController = navController,
                    onFinishOnboarding = {
                        // Mark that user has seen onboarding
                        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        sharedPreferences.edit()
                            .putBoolean(PREF_HAS_SEEN_ONBOARDING, true)
                            .apply()
                        navController.navigate("welcome") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }

            // Welcome screen - choice between login and registration
            composable("welcome") {
                WelcomeScreen()
            }

            // Login screen - navigate to LoginActivity
            composable("login") {
                LaunchedEffect(Unit) {
                    val intent = Intent(this@MainActivity, LoginActivity::class.java)
                    startActivity(intent)
                }
            }

            // Registration screen - navigate to RegistrationActivity
            composable("registration") {
                LaunchedEffect(Unit) {
                    val intent = Intent(this@MainActivity, RegistrationActivity::class.java)
                    startActivity(intent)
                }
            }
        }
    }

    /**
     * Check user authentication state synchronously
     */
    private fun checkUserState(): String {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hasSeenOnboarding = sharedPreferences.getBoolean(PREF_HAS_SEEN_ONBOARDING, false)
        val rememberMe = sharedPreferences.getBoolean(PREF_REMEMBER_ME, false)

        // Check notification listener permission status
        val notificationListenerEnabled = NotificationPermissionUtils.isNotificationListenerEnabled(this)
        Log.d(TAG, "Notification Listener Permission: $notificationListenerEnabled")

        // Check if user is logged in
        val auth = FirebaseAuth.getInstance()
        val isLoggedIn = auth.currentUser != null

        return when {
            // If user is logged in and has remember me enabled, go to dashboard
            isLoggedIn && rememberMe -> {
                Log.d(TAG, "User is logged in with Remember Me - navigating to dashboard")
                "dashboard"
            }
            // If user is logged in but remember me is disabled, sign out and proceed as not logged in
            isLoggedIn && !rememberMe -> {
                Log.d(TAG, "User is logged in but Remember Me is disabled - signing out")
                auth.signOut()
                if (hasSeenOnboarding) "welcome" else "onboarding"
            }
            // If user is not logged in, check onboarding status
            !isLoggedIn && hasSeenOnboarding -> {
                Log.d(TAG, "User not logged in but has seen onboarding - navigating to welcome")
                "welcome"
            }
            // If user is not logged in and hasn't seen onboarding, show onboarding
            else -> {
                Log.d(TAG, "User not logged in and hasn't seen onboarding - navigating to onboarding")
                "onboarding"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Check auto-logout when app is resumed
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rememberMe = sharedPreferences.getBoolean(PREF_REMEMBER_ME, false)

        // Check notification listener permission status when app resumes
        val notificationListenerEnabled = NotificationPermissionUtils.isNotificationListenerEnabled(this)
        Log.d(TAG, "onResume - Notification Listener Permission: $notificationListenerEnabled")

        if (!rememberMe && FirebaseAuth.getInstance().currentUser != null) {
            // Auto logout if Remember Me is not checked
            FirebaseAuth.getInstance().signOut()
            // Restart the app to go back to splash screen
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
