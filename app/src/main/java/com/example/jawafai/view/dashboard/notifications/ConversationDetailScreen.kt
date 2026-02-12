package com.example.jawafai.view.dashboard.notifications

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jawafai.managers.GroqApiManager
import com.example.jawafai.service.NotificationAIReplyManager
import com.example.jawafai.service.NotificationMemoryStore
import com.example.jawafai.service.RemoteReplyService
import com.example.jawafai.ui.theme.AppFonts
import com.example.jawafai.view.ui.theme.JawafAccent
import com.example.jawafai.view.ui.theme.JawafText
import com.example.jawafai.managers.NotificationFirebaseManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/**
 * Dedicated Conversation Screen - Opens when clicking a chat in Smart Notifications
 * Provides a full messaging experience with:
 * - Message bubbles (incoming/outgoing)
 * - AI reply generation
 * - Editable replies
 * - Typing input at bottom
 * - Send functionality
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationDetailScreen(
    conversationId: String,
    onBackClick: () -> Unit,
    onNavigateToPersona: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    // Get conversation and messages - use observable state for reactivity
    val conversationsState = NotificationMemoryStore.getConversationsState()
    val messagesState = NotificationMemoryStore.getMessagesState()

    val conversation by remember(conversationId) {
        derivedStateOf {
            conversationsState.find { it.convo_id == conversationId }
        }
    }

    val messages by remember(conversationId) {
        derivedStateOf {
            messagesState.filter { it.convo_id == conversationId }.sortedBy { it.timestamp }
        }
    }

    // Platform detection
    val platform = remember(conversation) {
        when {
            conversation?.package_name?.contains("whatsapp", true) == true -> ChatPlatform.WHATSAPP
            conversation?.package_name?.contains("instagram", true) == true -> ChatPlatform.INSTAGRAM
            conversation?.package_name?.contains("messenger", true) == true ||
            conversation?.package_name?.contains("facebook.orca", true) == true -> ChatPlatform.MESSENGER
            else -> ChatPlatform.GENERAL
        }
    }

    // State
    var inputText by remember { mutableStateOf("") }
    var isGeneratingReply by remember { mutableStateOf(false) }
    var generatingForHash by remember { mutableStateOf<String?>(null) }
    var selectedMessageForReply by remember { mutableStateOf<NotificationMemoryStore.Message?>(null) }
    var editableReplyText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var showAIReplyDialog by remember { mutableStateOf(false) }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Load user persona from Firebase - defined as a suspend function
    suspend fun loadUserPersonaFromFirebase(): Map<String, Any>? {
        return try {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                ?: return null

            val personaRef = FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUserId)
                .collection("persona")

            val personaData = personaRef.get().await()

            val persona = mutableMapOf<String, Any>()
            personaData.documents.forEach { document ->
                document.getString("answer")?.let { answer ->
                    if (answer.isNotBlank()) {
                        persona[document.id] = answer
                    }
                }
            }

            if (persona.isNotEmpty()) persona else null
        } catch (e: Exception) {
            Log.e("ConversationDetail", "Failed to load user persona: ${e.message}")
            null
        }
    }

    // Check if user persona is completed (needs at least 8 answers)
    suspend fun isPersonaCompleted(): Boolean {
        return try {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                ?: return false

            val personaRef = FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUserId)
                .collection("persona")

            val personaData = personaRef.get().await()

            var validAnswers = 0
            personaData.documents.forEach { document ->
                document.getString("answer")?.let { answer ->
                    if (answer.isNotBlank()) {
                        validAnswers++
                    }
                }
            }

            validAnswers >= 8
        } catch (e: Exception) {
            Log.e("ConversationDetail", "Failed to check persona: ${e.message}")
            false
        }
    }

    // Generate AI Reply function with conversation context and user persona
    fun generateAIReply(message: NotificationMemoryStore.Message) {
        coroutineScope.launch {
            try {
                isGeneratingReply = true
                generatingForHash = message.msg_hash

                // Check if persona is completed first
                val personaComplete = isPersonaCompleted()
                if (!personaComplete) {
                    isGeneratingReply = false
                    generatingForHash = null
                    Toast.makeText(
                        context,
                        "Please complete your persona to use AI replies",
                        Toast.LENGTH_LONG
                    ).show()
                    onNavigateToPersona()
                    return@launch
                }

                // Find the original notification
                val notification = NotificationMemoryStore.getAllNotifications()
                    .find { it.hash == message.msg_hash }

                if (notification != null) {
                    // Load user persona from Firebase for personalized responses
                    val userPersona = loadUserPersonaFromFirebase()

                    // Generate AI reply with persona context
                    val result = NotificationAIReplyManager.generateAIReplyWithContext(
                        notification = notification,
                        conversationId = conversationId,
                        maxContextMessages = 15, // Last 15 messages for better context
                        userPersona = userPersona,
                        context = context
                    )

                    if (result.success && result.reply != null) {
                        editableReplyText = result.reply
                        selectedMessageForReply = message
                        showAIReplyDialog = true
                        Toast.makeText(context, "AI reply generated! ✨", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed: ${result.error}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    // Fallback: Create notification from message if not found in store
                    val userPersona = loadUserPersonaFromFirebase()

                    val result = NotificationAIReplyManager.generateAIReplyFromMessage(
                        message = message,
                        conversationId = conversationId,
                        displayName = conversation?.display_name ?: "Unknown",
                        packageName = conversation?.package_name ?: "",
                        maxContextMessages = 15,
                        userPersona = userPersona,
                        context = context
                    )

                    if (result.success && result.reply != null) {
                        editableReplyText = result.reply
                        selectedMessageForReply = message
                        showAIReplyDialog = true
                        Toast.makeText(context, "AI reply generated! ✨", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed: ${result.error}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isGeneratingReply = false
                generatingForHash = null
            }
        }
    }

    // Send Reply function
    fun sendReply(replyText: String) {
        if (replyText.isBlank()) return

        coroutineScope.launch {
            try {
                isSending = true

                val result = RemoteReplyService.sendReply(
                    context = context,
                    conversationId = conversationId,
                    replyText = replyText
                )

                if (result.success) {
                    Toast.makeText(context, "Reply sent! ✓", Toast.LENGTH_SHORT).show()
                    selectedMessageForReply?.let { msg ->
                        NotificationMemoryStore.markMessageAsSent(msg.msg_hash)
                    }
                    showAIReplyDialog = false
                    editableReplyText = ""
                    selectedMessageForReply = null
                } else {
                    Toast.makeText(context, "Failed to send reply", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isSending = false
            }
        }
    }

    // Send custom message from input
    fun sendCustomMessage() {
        if (inputText.isBlank()) return

        val textToSend = inputText
        inputText = ""
        keyboardController?.hide()

        coroutineScope.launch {
            try {
                isSending = true

                val result = RemoteReplyService.sendReply(
                    context = context,
                    conversationId = conversationId,
                    replyText = textToSend
                )

                if (result.success) {
                    Toast.makeText(context, "Message sent! ✓", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to send message", Toast.LENGTH_LONG).show()
                    inputText = textToSend // Restore input on failure
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                inputText = textToSend
            } finally {
                isSending = false
            }
        }
    }

    Scaffold(
        topBar = {
            // Conversation Header - Clean white design
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF191919)
                        )
                    }

                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1BC994).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = conversation?.display_name?.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = AppFonts.KarlaFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1BC994)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Name and platform
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = conversation?.display_name ?: "Unknown",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = AppFonts.KarlaFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color(0xFF191919)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(platform.color)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = platform.displayName,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = AppFonts.KaiseiDecolFontFamily,
                                    fontSize = 12.sp,
                                    color = Color(0xFF666666)
                                )
                            )
                        }
                    }

                    // AI Quick Generate button
                    IconButton(
                        onClick = {
                            // Generate AI reply for last incoming message
                            messages.lastOrNull { !it.is_outgoing }?.let { lastMessage ->
                                generateAIReply(lastMessage)
                            }
                        },
                        enabled = !isGeneratingReply
                    ) {
                        if (isGeneratingReply) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFF1BC994),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Generate AI Reply",
                                tint = Color(0xFF1BC994)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Message Input Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .padding(bottom = 56.dp), // Extra padding for bottom nav bar
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Text input
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                text = "Type a message...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = AppFonts.KaiseiDecolFontFamily,
                                    color = Color.Gray
                                )
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                            fontSize = 16.sp,
                            color = Color(0xFF191919) // Dark themed text color
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF191919),
                            unfocusedTextColor = Color(0xFF191919),
                            focusedBorderColor = Color(0xFF1BC994),
                            unfocusedBorderColor = Color(0xFFE0E0E0),
                            focusedContainerColor = Color(0xFFF8F8F8),
                            unfocusedContainerColor = Color(0xFFF8F8F8),
                            cursorColor = Color(0xFF1BC994)
                        ),
                        shape = RoundedCornerShape(24.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendCustomMessage() }),
                        maxLines = 4,
                        trailingIcon = {
                            // AI Generate button inside input
                            IconButton(
                                onClick = {
                                    messages.lastOrNull { !it.is_outgoing }?.let { lastMessage ->
                                        generateAIReply(lastMessage)
                                    }
                                },
                                enabled = !isGeneratingReply
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Generate",
                                    tint = if (isGeneratingReply) Color.Gray else Color(0xFF1BC994),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Send button
                    FloatingActionButton(
                        onClick = { sendCustomMessage() },
                        modifier = Modifier.size(48.dp),
                        containerColor = if (inputText.isNotBlank()) Color(0xFF1BC994) else Color(0xFFE0E0E0),
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 2.dp,
                            pressedElevation = 4.dp
                        )
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = if (inputText.isNotBlank()) Color.White else Color.Gray
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        // Messages List
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF5F5F5))
        ) {
            if (messages.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Forum,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No messages yet",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                            color = Color.Gray
                        )
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 80.dp // Extra padding to prevent content hiding behind bottom bar
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { message ->
                        ConversationMessageItem(
                            message = message,
                            isGenerating = generatingForHash == message.msg_hash,
                            onGenerateReply = { generateAIReply(message) },
                            onQuickSend = { replyText ->
                                editableReplyText = replyText
                                selectedMessageForReply = message
                                sendReply(replyText)
                            }
                        )
                    }

                    // Add some space at the bottom for scrolling
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    // AI Reply Edit Dialog
    if (showAIReplyDialog && selectedMessageForReply != null) {
        AlertDialog(
            onDismissRequest = {
                showAIReplyDialog = false
                selectedMessageForReply = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFF1BC994),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI Generated Reply",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Reply to: \"${selectedMessageForReply?.msg_content?.take(50)}...\"",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                            color = Color.Gray
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editableReplyText,
                        onValueChange = { editableReplyText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = AppFonts.KaiseiDecolFontFamily
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF1BC994),
                            unfocusedBorderColor = Color(0xFFE0E0E0)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 3,
                        maxLines = 8
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { sendReply(editableReplyText) },
                    enabled = editableReplyText.isNotBlank() && !isSending,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1BC994)
                    )
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAIReplyDialog = false
                    selectedMessageForReply = null
                }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun ConversationMessageItem(
    message: NotificationMemoryStore.Message,
    isGenerating: Boolean,
    onGenerateReply: () -> Unit,
    onQuickSend: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Message bubble
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.is_outgoing) Arrangement.End else Arrangement.Start
        ) {
            Card(
                shape = if (message.is_outgoing) {
                    RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
                } else {
                    RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
                },
                colors = CardDefaults.cardColors(
                    containerColor = if (message.is_outgoing) Color(0xFFDCF8C6) else Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    // Sender name (only for incoming)
                    if (!message.is_outgoing) {
                        Text(
                            text = message.sender_name,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = AppFonts.KarlaFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF1BC994)
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Message content
                    Text(
                        text = message.msg_content,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                            fontSize = 15.sp,
                            color = Color(0xFF333333)
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Timestamp and status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = SimpleDateFormat("HH:mm", Locale.getDefault())
                                .format(Date(message.timestamp)),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = AppFonts.KaiseiDecolFontFamily,
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        )

                        if (message.is_outgoing || message.is_sent) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = "Delivered",
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFF1BC994)
                            )
                        }
                    }
                }
            }
        }

        // AI Reply section (for incoming messages)
        if (!message.is_outgoing) {
            Spacer(modifier = Modifier.height(6.dp))

            if (message.ai_reply.isNotBlank()) {
                // Show AI reply
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1BC994)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "AI Reply",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = AppFonts.KarlaFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                )
                                if (message.is_sent) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Sent",
                                        modifier = Modifier.size(12.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = message.ai_reply,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = AppFonts.KaiseiDecolFontFamily,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                            )

                            // Quick send button if not sent
                            if (!message.is_sent && message.has_reply_action) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = { onQuickSend(message.ai_reply) },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Send,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Send",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontFamily = AppFonts.KarlaFontFamily,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (!message.is_sent) {
                // Show generate button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    OutlinedButton(
                        onClick = onGenerateReply,
                        enabled = !isGenerating,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF1BC994)
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF1BC994))
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color(0xFF1BC994),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Generating...",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = AppFonts.KarlaFontFamily
                                )
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Generate AI Reply",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = AppFonts.KarlaFontFamily
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

