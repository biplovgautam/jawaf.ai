package com.example.jawafai.utils

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.jawafai.ui.theme.AppFonts
import kotlinx.coroutines.delay

/**
 * Global Network Manager for handling internet connectivity throughout the app
 */
@Composable
fun GlobalNetworkMonitor() {
    val isConnected = rememberNetworkConnectivity()
    var showNoInternetDialog by remember { mutableStateOf(false) }
    var isRetrying by remember { mutableStateOf(false) }
    var hasEverBeenConnected by remember { mutableStateOf(false) }

    // Track if we've ever been connected
    LaunchedEffect(isConnected) {
        if (isConnected) {
            hasEverBeenConnected = true
            showNoInternetDialog = false
            isRetrying = false
        } else if (hasEverBeenConnected) {
            // Only show dialog if we were previously connected and now lost connection
            showNoInternetDialog = true
        }
    }

    // Show dialog when connection is lost (but not on app start)
    if (showNoInternetDialog && hasEverBeenConnected) {
        NoInternetDialog(
            isRetrying = isRetrying,
            onRetry = {
                if (!isRetrying) {
                    isRetrying = true
                }
            },
            onDismiss = { /* Don't allow dismissal */ }
        )
    }

    // Reset retry state after timeout
    LaunchedEffect(isRetrying) {
        if (isRetrying) {
            delay(3000) // Wait 3 seconds
            if (!isConnected) {
                isRetrying = false // Reset retry state if still no connection
            }
        }
    }
}

@Composable
private fun NoInternetDialog(
    isRetrying: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFE7F6F2) // Same as splash screen
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = "No Internet Connection",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color(0xFF395B64)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Message
                Text(
                    text = "Your internet connection was lost. Please check your connection and try again to continue using जवाफ.AI.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = AppFonts.KaiseiDecolFontFamily,
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Retry Button
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF395B64)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isRetrying) {
                        // Rotating animation for the refresh icon
                        val infiniteTransition = rememberInfiniteTransition(label = "rotation")
                        val rotationAngle by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "rotation"
                        )

                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Checking",
                            modifier = Modifier
                                .size(16.dp)
                                .graphicsLayer(rotationZ = rotationAngle)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (isRetrying) "Checking..." else "Retry Connection",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

/**
 * Composable wrapper that adds network monitoring to any screen
 */
@Composable
fun WithNetworkMonitoring(
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()

        // Add global network monitor
        GlobalNetworkMonitor()
    }
}
