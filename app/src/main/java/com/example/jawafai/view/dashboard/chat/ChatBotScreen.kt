package com.example.jawafai.view.dashboard.chat

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.SmartToy
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.*
import com.example.jawafai.R
import com.example.jawafai.managers.ReminderFirebaseManager
import com.example.jawafai.model.ChatBotMessageModel
import com.example.jawafai.model.DetectedReminderIntent
import com.example.jawafai.model.Reminder
import com.example.jawafai.model.ReminderSource
import com.example.jawafai.service.ReminderIntentDetector
import com.example.jawafai.ui.theme.AppFonts
import com.example.jawafai.view.components.ReminderConfirmationDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatBotScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPersona: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Check persona completion status
    var isPersonaCompleted by remember { mutableStateOf<Boolean?>(null) }
    var isCheckingPersona by remember { mutableStateOf(true) }

    // Check if persona is completed when screen loads
    LaunchedEffect(Unit) {
        try {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            if (currentUserId != null) {
                val personaRef = FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(currentUserId)
                    .collection("persona")

                val personaData = personaRef.get().await()

                // Check if we have valid answers for the questions
                val validAnswers = personaData.documents.filter { doc ->
                    val questionId = doc.id
                    val answer = doc.getString("answer")
                    // Check if this question ID exists in our questions and has a valid answer
                    com.example.jawafai.model.PersonaQuestions.questions.any { it.id == questionId } &&
                            !answer.isNullOrBlank()
                }

                // Need at least 8 valid answers to be considered complete
                isPersonaCompleted = validAnswers.size >= 8

                // If persona is not completed, show toast and redirect
                if (isPersonaCompleted == false) {
                    android.widget.Toast.makeText(
                        context,
                        "Please complete your persona to start using AI Companion",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    // Navigate back and then to persona screen
                    onNavigateBack()
                    onNavigateToPersona()
                }
            } else {
                isPersonaCompleted = false
            }
        } catch (e: Exception) {
            Log.e("ChatBotScreen", "Error checking persona: ${e.message}")
            isPersonaCompleted = false
        } finally {
            isCheckingPersona = false
        }
    }

    // Show loading while checking persona
    if (isCheckingPersona || isPersonaCompleted == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = Color(0xFF395B64)
            )
        }
        return
    }

    // If persona is not completed, return early (user will be redirected)
    if (isPersonaCompleted == false) {
        return
    }

    // State for chatbot functionality - SESSION ONLY (no database persistence)
    var messages by remember { mutableStateOf<List<ChatBotMessageModel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isTyping by remember { mutableStateOf(false) }
    var messageText by remember { mutableStateOf("") }
    var showNewChatDialog by remember { mutableStateOf(false) }

    // Reminder intent detection states
    var detectedReminderIntent by remember { mutableStateOf<DetectedReminderIntent?>(null) }
    var showReminderDialog by remember { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            delay(100)
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Function to send messages with real Groq API integration
    fun sendMessage(message: String) {
        // Check 20 message limit before sending
        if (messages.size >= 20) {
            // Show toast message for limit reached
            android.widget.Toast.makeText(
                context,
                "Chat limit reached (20 messages). Please start a new chat for better responses.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }

        // Add user message
        val userMessage = ChatBotMessageModel(
            id = UUID.randomUUID().toString(),
            conversationId = "current_session",
            message = message,
            isFromUser = true,
            timestamp = System.currentTimeMillis()
        )

        // Add message (limit to 20 messages total)
        messages = (messages + userMessage).takeLast(20)
        isTyping = true

        // Use GroqApiManager for real AI responses
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            try {
                // Convert existing messages to GroqApiManager format (use only messages without current one)
                val chatHistory = com.example.jawafai.managers.GroqApiManager.convertToChatMessages(messages.dropLast(1))

                // Get response from Groq API using the new chatbot-specific method
                val response = com.example.jawafai.managers.GroqApiManager.getChatBotResponse(
                    userMessage = message,
                    conversationHistory = chatHistory,
                    userPersona = null // Could fetch user persona here
                )

                val aiMessage = if (response.success && response.message != null) {
                    ChatBotMessageModel(
                        id = UUID.randomUUID().toString(),
                        conversationId = "current_session",
                        message = response.message,
                        isFromUser = false,
                        timestamp = System.currentTimeMillis()
                    )
                } else {
                    // Handle different error cases
                    val errorMsg = when {
                        response.error?.contains("Conversation limit reached") == true -> {
                            // Show toast for limit reached
                            android.widget.Toast.makeText(
                                context,
                                "Chat limit reached. Please start a new chat.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                            "I notice we've been chatting for quite a while! To keep our conversation fresh and focused, please start a new chat. This helps me provide better responses. 😊"
                        }
                        response.error?.contains("API key not configured") == true -> {
                            "I'm having trouble connecting to my AI service. Please check the app configuration."
                        }
                        else -> {
                            val errorDetails = response.error ?: "Unknown error occurred"
                            Log.e("ChatBotScreen", "API Error: $errorDetails")
                            "I'm having trouble responding right now. Please try again in a moment. Error: $errorDetails" // Added error details for debugging
                        }
                    }

                    ChatBotMessageModel(
                        id = UUID.randomUUID().toString(),
                        conversationId = "current_session",
                        message = errorMsg,
                        isFromUser = false,
                        timestamp = System.currentTimeMillis()
                    )
                }

                // Add AI message (limit to 20 messages total)
                messages = (messages + aiMessage).takeLast(20)
                isTyping = false

                // Check for reminder intent in the conversation
                try {
                    if (ReminderIntentDetector.shouldCheckForReminder(message)) {
                        val conversationContext = messages.takeLast(5).map { it.message }
                        val intent: DetectedReminderIntent? = ReminderIntentDetector.detectReminderIntent(
                            message = message,
                            conversationContext = conversationContext,
                            source = ReminderSource.CHATBOT,
                            conversationId = "chatbot_session"
                        )
                        if (intent != null && intent.detectedDateTime != null) {
                            detectedReminderIntent = intent
                            showReminderDialog = true
                        }
                    }
                } catch (reminderError: Exception) {
                    Log.e("ChatBotScreen", "Reminder detection error: ${reminderError.message}")
                }
            } catch (e: Exception) {
                Log.e("ChatBotScreen", "Exception in sendMessage: ${e.message}", e)
                val errorMessage = ChatBotMessageModel(
                    id = UUID.randomUUID().toString(),
                    conversationId = "current_session",
                    message = "I'm experiencing some technical difficulties. Please try again later.",
                    isFromUser = false,
                    timestamp = System.currentTimeMillis()
                )
                messages = (messages + errorMessage).takeLast(20)
                isTyping = false
            }
        }
    }

    // Function to create new conversation - clears session
    fun createNewConversation() {
        messages = emptyList()
        messageText = ""
        isTyping = false
        // Show toast for new chat started
        android.widget.Toast.makeText(
            context,
            "New chat started! 🎉",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    // Function to show new chat confirmation dialog
    fun showNewChatConfirmation() {
        showNewChatDialog = true
    }

    // Enhanced new chat confirmation dialog
    if (showNewChatDialog) {
        AlertDialog(
            onDismissRequest = { showNewChatDialog = false },
            title = {
                Text(
                    text = "Start New Chat",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color(0xFF395B64)
                    )
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to start a new chat? This will clear the current conversation.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = AppFonts.KaiseiDecolFontFamily,
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        createNewConversation()
                        showNewChatDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF395B64)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Start New Chat",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showNewChatDialog = false },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF395B64)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Reminder Confirmation Dialog
    if (showReminderDialog && detectedReminderIntent != null) {
        ReminderConfirmationDialog(
            detectedIntent = detectedReminderIntent!!,
            onConfirm = { reminder ->
                coroutineScope.launch {
                    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                    val reminderWithUser = reminder.copy(userId = userId)

                    val result = ReminderFirebaseManager.saveReminder(reminderWithUser)
                    result.onSuccess { savedReminder ->
                        // Schedule the reminder notification (5 minutes before event)
                        com.example.jawafai.service.ReminderScheduler.scheduleReminder(
                            context = context,
                            reminder = savedReminder
                        )

                        Toast.makeText(
                            context,
                            "Reminder saved! 🎯 ${reminder.title}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }.onFailure { error ->
                        Toast.makeText(
                            context,
                            "Failed to save reminder: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                showReminderDialog = false
                detectedReminderIntent = null
            },
            onDismiss = {
                showReminderDialog = false
                detectedReminderIntent = null
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Enhanced AI Bot Icon with gradient-like effect
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF395B64))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SmartToy,
                                contentDescription = "AI Bot",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(26.dp)
                                    .align(Alignment.Center)
                            )
                        }

                        Column {
                            Text(
                                text = "AI Companion",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = AppFonts.KarlaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = Color(0xFF395B64)
                                )
                            )

                            AnimatedVisibility(visible = isTyping) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Animated typing dots
                                    repeat(3) { index ->
                                        val infiniteTransition = rememberInfiniteTransition(label = "typing_header")
                                        val alpha by infiniteTransition.animateFloat(
                                            initialValue = 0.3f,
                                            targetValue = 1f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(600, delayMillis = index * 200),
                                                repeatMode = RepeatMode.Reverse
                                            ),
                                            label = "typing_header_dot_$index"
                                        )

                                        Box(
                                            modifier = Modifier
                                                .size(4.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF4CAF50).copy(alpha = alpha))
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    Text(
                                        text = "AI is typing...",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                                            fontSize = 12.sp,
                                            color = Color(0xFF4CAF50)
                                        )
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF395B64)
                        )
                    }
                },
                actions = {
                    // Enhanced new chat button
                    IconButton(
                        onClick = { showNewChatConfirmation() }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF395B64).copy(alpha = 0.1f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New Chat",
                                tint = Color(0xFF395B64),
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.Center)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        bottomBar = {
            // Enhanced Message Input Bar
            EnhancedMessageInputBar(
                messageText = messageText,
                onMessageChange = { messageText = it },
                onSendMessage = {
                    if (messageText.isNotBlank()) {
                        sendMessage(messageText)
                        messageText = ""
                        keyboardController?.hide()
                    }
                },
                isLoading = isTyping
            )
        }
    ) { paddingValues ->
        // Main content with enhanced background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8F9FA))
                .padding(paddingValues)
        ) {
            if (messages.isEmpty() && !isLoading) {
                EnhancedEmptyStateView(
                    onSuggestionClick = { suggestion ->
                        sendMessage(suggestion)
                    }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    items(messages) { message ->
                        EnhancedMessageItem(message = message)
                    }

                    // Enhanced typing indicator
                    if (isTyping) {
                        item {
                            EnhancedTypingIndicator()
                        }
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
fun EnhancedMessageItem(message: ChatBotMessageModel) {
    val isUser = message.isFromUser

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (isUser) {
            Spacer(modifier = Modifier.width(56.dp))
        }

        Column(
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = if (isUser) 20.dp else 6.dp,
                    bottomEnd = if (isUser) 6.dp else 20.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isUser) Color(0xFF395B64) else Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = message.message,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = AppFonts.KaiseiDecolFontFamily,
                        fontSize = 15.sp,
                        color = if (isUser) Color.White else Color(0xFF333333),
                        lineHeight = 20.sp
                    ),
                    modifier = Modifier.padding(16.dp)
                )
            }

            Text(
                text = formatChatTimestamp(message.timestamp),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = AppFonts.KaiseiDecolFontFamily,
                    fontSize = 11.sp,
                    color = Color(0xFF999999)
                ),
                textAlign = if (isUser) TextAlign.End else TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        if (!isUser) {
            Spacer(modifier = Modifier.width(56.dp))
        }
    }
}

@Composable
fun EnhancedTypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = 6.dp,
                bottomEnd = 20.dp
            ),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val infiniteTransition = rememberInfiniteTransition(label = "typing")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, delayMillis = index * 200),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "typing_dot_$index"
                    )

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF395B64).copy(alpha = alpha))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(56.dp))
    }
}

@Composable
fun EnhancedMessageInputBar(
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            TextField(
                value = messageText,
                onValueChange = onMessageChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = "Type your message...",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                            fontSize = 15.sp,
                            color = Color(0xFF999999)
                        )
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = AppFonts.KaiseiDecolFontFamily,
                    fontSize = 15.sp,
                    color = Color(0xFF333333) // Fixed text color for better visibility
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Color(0xFF395B64)
                ),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = { onSendMessage() }
                ),
                maxLines = 4
            )

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        if (messageText.isBlank() || isLoading) Color(0xFFE0E0E0) else Color(0xFF395B64)
                    )
                    .clickable(enabled = messageText.isNotBlank() && !isLoading) {
                        onSendMessage()
                    }
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(26.dp)
                            .align(Alignment.Center),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (messageText.isBlank()) Color(0xFF999999) else Color.White,
                        modifier = Modifier
                            .size(26.dp)
                            .align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
fun EnhancedEmptyStateView(onSuggestionClick: (String) -> Unit) {
    // Lottie animation for chatbot
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.live_chatbot))
    val progress by animateLottieCompositionAsState(
        composition,
        iterations = LottieConstants.IterateForever
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(modifier = Modifier.height(40.dp))

            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier.size(200.dp)
            )
        }

        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Hello! I'm Your AI Companion 💙",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = Color(0xFF395B64)
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "I'm here to listen, support, and help you through whatever you're going through. Let's have a meaningful conversation!",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = AppFonts.KaiseiDecolFontFamily,
                        fontSize = 16.sp,
                        color = Color(0xFF666666),
                        lineHeight = 22.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Enhanced Emotional Support Suggestions
        item {
            EnhancedSuggestionCard(
                title = "💭 Let's Talk",
                suggestions = listOf(
                    "I'm feeling low today",
                    "Tell me a joke to cheer me up",
                    "I need some motivation"
                ),
                onSuggestionClick = onSuggestionClick
            )
        }

        // Enhanced Productivity Suggestions
        item {
            EnhancedSuggestionCard(
                title = "📅 Daily Support",
                suggestions = listOf(
                    "Help me plan my day",
                    "I'm feeling overwhelmed with tasks",
                    "Give me some productivity tips"
                ),
                onSuggestionClick = onSuggestionClick
            )
        }

        // Enhanced Learning & Fun Suggestions
        item {
            EnhancedSuggestionCard(
                title = "🌟 Learn & Explore",
                suggestions = listOf(
                    "Tell me an interesting scientific fact",
                    "Explain something fascinating",
                    "Share some life wisdom"
                ),
                onSuggestionClick = onSuggestionClick
            )
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun EnhancedSuggestionCard(
    title: String,
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = AppFonts.KarlaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF395B64)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            suggestions.forEach { suggestion ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            onSuggestionClick(suggestion)
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F8FF)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                            fontSize = 14.sp,
                            color = Color(0xFF333333)
                        ),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun formatChatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60 * 1000 -> "Just now"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}m ago"
        diff < 24 * 60 * 60 * 1000 -> {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
        else -> {
            SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
    }
}
