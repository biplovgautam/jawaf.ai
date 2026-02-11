package com.example.jawafai.managers

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.jawafai.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object CloudinaryManager {

    private const val TAG = "CloudinaryManager"
    private var isInitialized = false

    /**
     * Initializes the Cloudinary MediaManager with credentials from BuildConfig.
     * This should be called once, ideally in the Application's onCreate method.
     */
    fun init(context: Context) {
        if (isInitialized) {
            Log.d(TAG, "MediaManager is already initialized.")
            return
        }
        try {
            val config = mapOf(
                "cloud_name" to BuildConfig.CLOUDINARY_CLOUD_NAME,
                "api_key" to BuildConfig.CLOUDINARY_API_KEY,
                "api_secret" to BuildConfig.CLOUDINARY_API_SECRET
            )

            Log.d(TAG, "Initializing with cloud_name: ${BuildConfig.CLOUDINARY_CLOUD_NAME}")

            MediaManager.init(context, config)
            isInitialized = true
            Log.d(TAG, "MediaManager initialized successfully for cloud: ${BuildConfig.CLOUDINARY_CLOUD_NAME}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaManager", e)
            throw e
        }
    }

    /**
     * Uploads an image to Cloudinary using the given content URI.
     * @param uri The content URI of the image to upload.
     * @return The secure URL of the uploaded image, or null on failure.
     */
    suspend fun uploadImage(uri: Uri): String? {
        if (!isInitialized) {
            Log.e(TAG, "MediaManager is not initialized. Cannot upload image.")
            throw IllegalStateException("CloudinaryManager not initialized. Call init() first.")
        }

        return suspendCancellableCoroutine { continuation ->
            try {
                val requestId = MediaManager.get().upload(uri)
                    .option("folder", "profile_images")
                    .option("public_id", "profile_${System.currentTimeMillis()}")
                    .option("transformation", "c_fill,w_300,h_300,q_auto,f_auto")
                    .callback(object : UploadCallback {
                        override fun onStart(requestId: String) {
                            Log.d(TAG, "Upload started for requestId: $requestId")
                        }

                        override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                            val progress = (bytes.toDouble() / totalBytes * 100).toInt()
                            Log.d(TAG, "Upload progress for $requestId: $progress%")
                        }

                        override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                            val secureUrl = resultData["secure_url"] as? String
                            Log.d(TAG, "Upload success for $requestId. URL: $secureUrl")
                            if (continuation.isActive) {
                                continuation.resume(secureUrl)
                            }
                        }

                        override fun onError(requestId: String, error: ErrorInfo) {
                            Log.e(TAG, "Upload error for $requestId: ${error.description}")
                            Log.e(TAG, "Error code: ${error.code}")
                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }

                        override fun onReschedule(requestId: String, error: ErrorInfo) {
                            Log.w(TAG, "Upload rescheduled for $requestId: ${error.description}")
                        }
                    }).dispatch()

                continuation.invokeOnCancellation {
                    Log.d(TAG, "Upload cancelled for requestId: $requestId")
                    try {
                        MediaManager.get().cancelRequest(requestId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error cancelling request: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception starting upload: ${e.message}", e)
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }
    }

    /**
     * Check if CloudinaryManager is initialized
     */
    fun isInitialized(): Boolean = isInitialized
}
