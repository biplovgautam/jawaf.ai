package com.example.jawafai.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object NotificationPermissionUtils {

    private const val TAG = "NotificationPermUtils"

    /**
     * Check if the NotificationListenerService is enabled for this app
     */
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val packageName = context.packageName
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )

        if (!TextUtils.isEmpty(flat)) {
            val names = flat.split(":").toTypedArray()
            for (name in names) {
                val componentName = ComponentName.unflattenFromString(name)
                if (componentName != null) {
                    if (TextUtils.equals(packageName, componentName.packageName)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Open the notification access settings page
     */
    fun openNotificationAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    /**
     * Get the service component name for this app's notification listener
     */
    fun getNotificationListenerComponentName(context: Context): ComponentName {
        return ComponentName(
            context.packageName,
            "com.example.jawafai.service.JawafaiNotificationListenerService"
        )
    }

    // ==================== BATTERY OPTIMIZATION ====================

    /**
     * Check if the app is ignoring battery optimizations
     * Returns true if the app is whitelisted from battery optimization (good for background work)
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Request to be added to battery optimization whitelist
     * This allows the app to run in the background without being killed
     *
     * Note: This opens a system dialog asking the user to allow the app
     * to ignore battery optimizations. The user must approve.
     *
     * @param activity The activity context (needed for startActivityForResult)
     * @param requestCode The request code for onActivityResult callback
     */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(activity: Activity, requestCode: Int = 1001) {
        if (!isIgnoringBatteryOptimizations(activity)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivityForResult(intent, requestCode)
                Log.d(TAG, "Requested battery optimization exemption")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request battery optimization: ${e.message}")
                // Fallback: open battery optimization settings
                openBatteryOptimizationSettings(activity)
            }
        } else {
            Log.d(TAG, "Already ignoring battery optimizations")
        }
    }

    /**
     * Open battery optimization settings (fallback if direct request fails)
     */
    fun openBatteryOptimizationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open battery settings: ${e.message}")
            // Try generic app settings as last resort
            openAppSettings(context)
        }
    }

    /**
     * Open the app's settings page
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app settings: ${e.message}")
        }
    }

    // ==================== POST_NOTIFICATIONS PERMISSION (Android 13+) ====================

    /**
     * Check if POST_NOTIFICATIONS permission is granted (Android 13+)
     */
    fun hasPostNotificationsPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required before Android 13
        }
    }

    /**
     * Request POST_NOTIFICATIONS permission (Android 13+)
     */
    fun requestPostNotificationsPermission(activity: Activity, requestCode: Int = 1002) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasPostNotificationsPermission(activity)) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    requestCode
                )
                Log.d(TAG, "Requested POST_NOTIFICATIONS permission")
            }
        }
    }

    // ==================== COMPREHENSIVE PERMISSION CHECK ====================

    /**
     * Data class representing all permission states
     */
    data class PermissionState(
        val notificationListenerEnabled: Boolean,
        val batteryOptimizationIgnored: Boolean,
        val postNotificationsGranted: Boolean
    ) {
        val allPermissionsGranted: Boolean
            get() = notificationListenerEnabled && batteryOptimizationIgnored && postNotificationsGranted

        val criticalPermissionsGranted: Boolean
            get() = notificationListenerEnabled // Most important for functionality
    }

    /**
     * Get comprehensive permission state
     */
    fun getPermissionState(context: Context): PermissionState {
        return PermissionState(
            notificationListenerEnabled = isNotificationListenerEnabled(context),
            batteryOptimizationIgnored = isIgnoringBatteryOptimizations(context),
            postNotificationsGranted = hasPostNotificationsPermission(context)
        )
    }

    /**
     * Log current permission state for debugging
     */
    fun logPermissionState(context: Context) {
        val state = getPermissionState(context)
        Log.d(TAG, """
            📋 Permission State:
            - Notification Listener: ${if (state.notificationListenerEnabled) "✅ Enabled" else "❌ Disabled"}
            - Battery Optimization: ${if (state.batteryOptimizationIgnored) "✅ Ignored" else "⚠️ Active"}
            - POST_NOTIFICATIONS: ${if (state.postNotificationsGranted) "✅ Granted" else "❌ Denied"}
            - All Permissions: ${if (state.allPermissionsGranted) "✅ Complete" else "⚠️ Incomplete"}
        """.trimIndent())
    }
}
