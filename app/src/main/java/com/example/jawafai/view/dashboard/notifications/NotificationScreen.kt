package com.example.jawafai.view.dashboard.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import coil.compose.AsyncImage
import com.example.jawafai.R
import com.example.jawafai.managers.GroqApiManager
import com.example.jawafai.service.NotificationMemoryStore
import com.example.jawafai.service.NotificationAIReplyManager
import com.example.jawafai.service.SmartReplyAIModule
import com.example.jawafai.service.RemoteReplyService
import com.example.jawafai.ui.theme.AppFonts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Data classes for chat notifications
data class ChatNotification(
    val id: String,
    val platform: ChatPlatform,
    val senderName: String,
    val senderAvatar: String?,
    val message: String,
    val timestamp: Long,
    val isRead: Boolean,
    val hasGeneratedReply: Boolean = false,
    val generatedReply: String = "",
    val hasReplyAction: Boolean = false,
    val isSent: Boolean = false,
    val conversationId: String = "",
    val notificationHash: String = ""
)

enum class ChatPlatform(
    val displayName: String,
    val color: Color,
    val iconRes: Int? = null
) {
    WHATSAPP("WhatsApp", Color(0xFF25D366)),
    INSTAGRAM("Instagram", Color(0xFFE4405F)),
    MESSENGER("Messenger", Color(0xFF0084FF)),
    GENERAL("General", Color(0xFF395B64))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    // State management
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf<ChatPlatform?>(null) }
    var generatingReplyFor by remember { mutableStateOf<String?>(null) }

    // Send status tracking
    var sendingStatus by remember { mutableStateOf<Map<String, RemoteReplyService.ReplyStatus>>(emptyMap()) }
    var sendingMessages by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    // Broadcast receiver for reply status updates
    val replyStatusReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    "com.example.jawafai.REPLY_STATUS" -> {
                        val conversationId = intent.getStringExtra("conversationId") ?: return
                        val status = intent.getStringExtra("status") ?: return
                        val message = intent.getStringExtra("message")

                        sendingStatus = sendingStatus.toMutableMap().apply {
                            this[conversationId] = RemoteReplyService.ReplyStatus.valueOf(status)
                        }

                        if (message != null) {
                            sendingMessages = sendingMessages.toMutableMap().apply {
                                this[conversationId] = message
                            }
                        }
                    }
                }
            }
        }
    }

    // Register broadcast receiver
    LaunchedEffect(Unit) {
        val filter = IntentFilter("com.example.jawafai.REPLY_STATUS")
        LocalBroadcastManager.getInstance(context).registerReceiver(replyStatusReceiver, filter)
    }

    // Cleanup receiver
    DisposableEffect(Unit) {
        onDispose {
            try {
                LocalBroadcastManager.getInstance(context).unregisterReceiver(replyStatusReceiver)
            } catch (e: Exception) {
                // Receiver might already be unregistered
            }
        }
    }

    // Observe external notifications from NotificationMemoryStore
    val externalNotifications by remember {
        derivedStateOf {
            NotificationMemoryStore.getAllNotifications()
        }
    }

    // Map external notifications to ChatNotification for display
    val liveNotifications = remember(externalNotifications) {
        externalNotifications.map { notification ->
            ChatNotification(
                id = notification.hash,
                platform = when {
                    notification.packageName.contains("whatsapp", true) -> ChatPlatform.WHATSAPP
                    notification.packageName.contains("instagram", true) -> ChatPlatform.INSTAGRAM
                    notification.packageName.contains("messenger", true) ||
                    notification.packageName.contains("facebook.orca", true) -> ChatPlatform.MESSENGER
                    else -> ChatPlatform.GENERAL
                },
                senderName = notification.sender?.takeIf { it.isNotBlank() } ?: notification.title.ifBlank { notification.packageName },
                senderAvatar = null,
                message = notification.text,
                timestamp = notification.time,
                isRead = false,
                hasGeneratedReply = notification.ai_reply.isNotBlank(),
                generatedReply = notification.ai_reply,
                hasReplyAction = notification.hasReplyAction,
                isSent = notification.is_sent,
                conversationId = notification.conversationId,
                notificationHash = notification.hash
            )
        }
    }

    // Filter notifications based on search and platform filter
    val filteredNotifications = remember(selectedFilter, liveNotifications) {
        liveNotifications.filter { notification ->
            val matchesFilter = selectedFilter?.let { filter ->
                notification.platform == filter
            } ?: true

            matchesFilter
        }
    }

    // Function to generate AI reply with enhanced debugging
    fun generateAIReply(notificationHash: String) {
        coroutineScope.launch {
            try {
                Log.d("NotificationScreen", "🚀 Starting AI reply generation for hash: $notificationHash")
                generatingReplyFor = notificationHash

                // Get the notification from memory store
                val notification = NotificationMemoryStore.getAllNotifications()
                    .find { it.hash == notificationHash }

                if (notification == null) {
                    Log.e("NotificationScreen", "❌ Notification not found in memory store")
                    Toast.makeText(context, "Error: Notification not found", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                Log.d("NotificationScreen", "✅ Found notification: ${notification.text}")

                // Check if API key is configured
                if (!GroqApiManager.isApiKeyConfigured()) {
                    Log.e("NotificationScreen", "❌ Groq API key not configured")
                    Toast.makeText(context, "Error: API key not configured. Please check your settings.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                Log.d("NotificationScreen", "✅ API key configured, generating reply...")
                Toast.makeText(context, "Generating AI reply...", Toast.LENGTH_SHORT).show()

                // Generate AI reply
                val result = NotificationAIReplyManager.generateAIReply(
                    notification = notification,
                    userPersona = null, // You can pass user persona here
                    context = context
                )

                Log.d("NotificationScreen", "📤 AI reply result: success=${result.success}, error=${result.error}")

                if (result.success && result.reply != null) {
                    Log.d("NotificationScreen", "✅ AI reply generated successfully: ${result.reply.take(100)}...")
                    Toast.makeText(context, "AI reply generated successfully! 🎉", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e("NotificationScreen", "❌ AI reply generation failed: ${result.error}")
                    val errorMessage = when {
                        result.error?.contains("API key not configured") == true ->
                            "Please configure your Groq API key in settings"
                        result.error?.contains("401") == true ->
                            "Invalid API key. Please check your configuration."
                        result.error?.contains("429") == true ->
                            "Rate limit exceeded. Please try again later."
                        result.error?.contains("network") == true ->
                            "Network error. Please check your connection."
                        result.error?.contains("timeout") == true ->
                            "Request timeout. Please try again."
                        else ->
                            "Failed to generate reply: ${result.error ?: "Unknown error"}"
                    }
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Log.e("NotificationScreen", "❌ Exception in AI reply generation: ${e.message}", e)
                Toast.makeText(context, "Error generating reply: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                generatingReplyFor = null
            }
        }
    }

    // Enhanced function to send reply with status tracking
    fun sendReply(conversationId: String, replyText: String) {
        coroutineScope.launch {
            try {
                // Use RemoteReplyService to send the reply with retry mechanism
                val result = RemoteReplyService.sendReply(
                    context = context,
                    conversationId = conversationId,
                    replyText = replyText
                )

                if (result.success) {
                    Toast.makeText(context, "Reply sent successfully! 🎉", Toast.LENGTH_SHORT).show()
                    // Clear any error messages
                    sendingMessages = sendingMessages.toMutableMap().apply {
                        remove(conversationId)
                    }
                } else {
                    val errorMessage = when {
                        result.error?.contains("No notification with reply action") == true ->
                            "Cannot send reply: This notification doesn't support direct replies."
                        result.canRetry ->
                            "Failed to send reply after ${RemoteReplyService.MAX_RETRY_ATTEMPTS} attempts: ${result.error}"
                        else ->
                            "Failed to send reply: ${result.error}"
                    }
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error sending reply: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Manual retry function
    fun retryReply(conversationId: String, replyText: String) {
        // Clear previous status
        RemoteReplyService.clearSendingStatus(conversationId)
        sendingStatus = sendingStatus.toMutableMap().apply {
            remove(conversationId)
        }
        sendingMessages = sendingMessages.toMutableMap().apply {
            remove(conversationId)
        }

        // Retry sending
        sendReply(conversationId, replyText)
    }

    // Pull to refresh simulation
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            delay(1000) // Simulate refresh
            isRefreshing = false
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Smart Notifications",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = Color(0xFF395B64)
                        )
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            // Clear notification memory store
                            NotificationMemoryStore.clear()
                            Toast.makeText(context, "Notifications cleared", Toast.LENGTH_SHORT).show()
                            isRefreshing = true
                        }
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Clear notifications",
                            tint = Color(0xFF395B64)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Platform Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                item {
                    FilterChip(
                        onClick = { selectedFilter = null },
                        label = { Text("All") },
                        selected = selectedFilter == null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF395B64),
                            selectedLabelColor = Color.White
                        )
                    )
                }

                items(ChatPlatform.values().toList()) { platform ->
                    FilterChip(
                        onClick = {
                            selectedFilter = if (selectedFilter == platform) null else platform
                        },
                        label = { Text(platform.displayName) },
                        selected = selectedFilter == platform,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = platform.color,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Loading indicator
            AnimatedVisibility(visible = isRefreshing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF395B64),
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp
                    )
                }
            }

            // Notifications List
            if (filteredNotifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "No notifications",
                            modifier = Modifier.size(48.dp),
                            tint = Color(0xFF666666).copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No notifications yet",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = AppFonts.KaiseiDecolFontFamily,
                                fontSize = 16.sp,
                                color = Color(0xFF666666)
                            )
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredNotifications) { notification ->
                        EnhancedNotificationCard(
                            notification = notification,
                            onGenerateReply = { generateAIReply(notification.notificationHash) },
                            onSendReply = { sendReply(notification.conversationId, notification.generatedReply) },
                            onMarkAsRead = { /* Handle mark as read */ },
                            isGeneratingReply = generatingReplyFor == notification.notificationHash,
                            sendingStatus = sendingStatus[notification.conversationId],
                            sentMessage = sendingMessages[notification.conversationId]
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun EnhancedNotificationCard(
    notification: ChatNotification,
    onGenerateReply: () -> Unit,
    onSendReply: () -> Unit,
    onMarkAsRead: (String) -> Unit,
    isGeneratingReply: Boolean,
    sendingStatus: RemoteReplyService.ReplyStatus?,
    sentMessage: String?
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isRead) Color.White else Color(0xFFF0F8FF)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with platform and timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(notification.platform.color)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = notification.platform.displayName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = notification.platform.color
                        )
                    )

                    // Reply action indicator
                    if (notification.hasReplyAction) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Reply,
                            contentDescription = "Reply Available",
                            modifier = Modifier.size(12.dp),
                            tint = Color(0xFF4CAF50)
                        )
                    }
                }

                Text(
                    text = formatTimestamp(notification.timestamp),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = AppFonts.KaiseiDecolFontFamily,
                        fontSize = 12.sp,
                        color = Color(0xFF666666)
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sender and message
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFA5C9CA))
                ) {
                    if (notification.senderAvatar != null) {
                        AsyncImage(
                            model = notification.senderAvatar,
                            contentDescription = "Sender Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Default Avatar",
                            modifier = Modifier.align(Alignment.Center),
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Message content
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = notification.senderName,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF395B64)
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = notification.message,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                            fontSize = 14.sp,
                            color = Color(0xFF666666)
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Unread indicator
                if (!notification.isRead) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF395B64))
                    )
                }
            }

            // AI Generated Reply Section
            if (notification.hasGeneratedReply && notification.generatedReply.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF0F8FF).copy(alpha = 0.7f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Generated",
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF395B64)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AI Generated Reply",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = AppFonts.KarlaFontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF395B64)
                                )
                            )

                            // Status indicators
                            Spacer(modifier = Modifier.weight(1f))

                            when (sendingStatus) {
                                RemoteReplyService.ReplyStatus.SENDING -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF2196F3)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Sending...",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 10.sp,
                                            color = Color(0xFF2196F3)
                                        )
                                    )
                                }
                                RemoteReplyService.ReplyStatus.RETRYING -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFFFF9800)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Retrying...",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 10.sp,
                                            color = Color(0xFFFF9800)
                                        )
                                    )
                                }
                                RemoteReplyService.ReplyStatus.SENT -> {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Sent",
                                        modifier = Modifier.size(16.dp),
                                        tint = Color(0xFF4CAF50)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Sent",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 10.sp,
                                            color = Color(0xFF4CAF50)
                                        )
                                    )
                                }
                                RemoteReplyService.ReplyStatus.FAILED -> {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = "Failed",
                                        modifier = Modifier.size(16.dp),
                                        tint = Color(0xFFE53E3E)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Failed",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 10.sp,
                                            color = Color(0xFFE53E3E)
                                        )
                                    )
                                }
                                else -> {
                                    if (notification.isSent) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Sent",
                                            modifier = Modifier.size(16.dp),
                                            tint = Color(0xFF4CAF50)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = notification.generatedReply,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = AppFonts.KaiseiDecolFontFamily,
                                fontSize = 14.sp,
                                color = Color(0xFF333333)
                            )
                        )

                        // Show status message if available
                        if (sentMessage != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = sentMessage,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    color = when (sendingStatus) {
                                        RemoteReplyService.ReplyStatus.FAILED -> Color(0xFFE53E3E)
                                        RemoteReplyService.ReplyStatus.RETRYING -> Color(0xFFFF9800)
                                        else -> Color(0xFF666666)
                                    }
                                )
                            )
                        }
                    }
                }
            }

            // Action buttons
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Generate Reply Button
                if (!notification.hasGeneratedReply && !isGeneratingReply) {
                    Button(
                        onClick = onGenerateReply,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF395B64)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Generate Reply",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Generate Reply",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = AppFonts.KarlaFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1
                        )
                    }
                }

                // Loading state for reply generation
                if (isGeneratingReply) {
                    Button(
                        onClick = { /* Do nothing while generating */ },
                        enabled = false,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF395B64).copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Generating...",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = AppFonts.KarlaFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1
                        )
                    }
                }

                // Regenerate Reply Button (if reply exists and not currently sending)
                if (notification.hasGeneratedReply &&
                    !isGeneratingReply &&
                    sendingStatus != RemoteReplyService.ReplyStatus.SENDING &&
                    sendingStatus != RemoteReplyService.ReplyStatus.RETRYING) {

                    OutlinedButton(
                        onClick = onGenerateReply,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF395B64)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.widthIn(min = 120.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Regenerate",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Regenerate",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = AppFonts.KarlaFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1
                        )
                    }
                }

                // Spacer to push send button to the right
                Spacer(modifier = Modifier.weight(1f))

                // Send Reply Button (moved to the right side)
                if (notification.hasGeneratedReply &&
                    notification.hasReplyAction &&
                    !notification.isSent &&
                    !isGeneratingReply &&
                    sendingStatus != RemoteReplyService.ReplyStatus.SENDING &&
                    sendingStatus != RemoteReplyService.ReplyStatus.RETRYING) {

                    Button(
                        onClick = onSendReply,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send Reply",
                            modifier = Modifier.size(18.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Send",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = AppFonts.KarlaFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                }

                // Sending state button
                if (sendingStatus == RemoteReplyService.ReplyStatus.SENDING) {
                    Button(
                        onClick = { /* Do nothing while sending */ },
                        enabled = false,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2196F3).copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sending...",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = AppFonts.KarlaFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                }

                // Retrying state button
                if (sendingStatus == RemoteReplyService.ReplyStatus.RETRYING) {
                    Button(
                        onClick = { /* Do nothing while retrying */ },
                        enabled = false,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF9800).copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Retrying...",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = AppFonts.KarlaFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                }

                // Retry button for failed sends
                if (sendingStatus == RemoteReplyService.ReplyStatus.FAILED) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                // Retry sending
                                RemoteReplyService.sendReply(
                                    context = context,
                                    conversationId = notification.conversationId,
                                    replyText = notification.generatedReply
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE53E3E)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Retry",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = AppFonts.KarlaFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// Helper function to format timestamps
@Composable
fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60 * 1000 -> "now"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}m ago"
        diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}h ago"
        diff < 48 * 60 * 60 * 1000 -> "yesterday"
        else -> {
            SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
        }
    }
}
