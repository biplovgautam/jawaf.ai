package com.example.jawafai.view.dashboard.chat

import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.jawafai.model.ChatMessage
import com.example.jawafai.repository.ChatRepositoryImpl
import com.example.jawafai.repository.UserRepositoryImpl
import com.example.jawafai.ui.theme.AppFonts
import com.example.jawafai.viewmodel.ChatViewModel
import com.example.jawafai.viewmodel.ChatViewModelFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    chatId: String,
    otherUserId: String,
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = viewModel(
        factory = ChatViewModelFactory(
            ChatRepositoryImpl(),
            UserRepositoryImpl(FirebaseAuth.getInstance(), FirebaseFirestore.getInstance()),
            FirebaseAuth.getInstance()
        )
    )
) {
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val clipboardManager = LocalClipboardManager.current
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    // Get screen dimensions
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }

    // Track keyboard state
    val imeInsets = WindowInsets.ime
    val imeHeight = with(density) { imeInsets.getBottom(density).toDp() }
    val isKeyboardOpen = imeHeight > 0.dp

    // Calculate dynamic height for LazyColumn
    val lazyColumnHeight by animateDpAsState(
        targetValue = if (isKeyboardOpen) {
            // When keyboard is open: much smaller height to ensure input field is visible
            (configuration.screenHeightDp.dp - imeHeight - 200.dp).coerceAtLeast(100.dp)
        } else {
            // When keyboard is closed: leave more space for input field
            (configuration.screenHeightDp.dp - 250.dp).coerceAtLeast(200.dp)
        },
        animationSpec = tween(300),
        label = "LazyColumnHeight"
    )

    // Handle back press properly
    DisposableEffect(backDispatcher) {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onNavigateBack()
            }
        }
        backDispatcher?.addCallback(callback)
        onDispose {
            callback.remove()
        }
    }

    val messages by viewModel.messages.collectAsState()
    val typingStatus by viewModel.typingStatus.collectAsState()
    var newMessageText by remember { mutableStateOf("") }
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    // State for message action popup
    var selectedMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showMessageActions by remember { mutableStateOf(false) }

    // Get user info for the other user
    var otherUserName by remember { mutableStateOf("Chat") }
    var otherUserImageUrl by remember { mutableStateOf<String?>(null) }

    // Scroll state for auto-scroll behavior
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Load messages when screen opens
    LaunchedEffect(chatId, currentUserId) {
        if (currentUserId != null) {
            try {
                viewModel.getMessagesForChat(currentUserId, otherUserId)
                viewModel.markMessagesAsSeen(currentUserId, otherUserId)
            } catch (e: Exception) {
                println("🔍 DEBUG: ChatDetailScreen - Error loading messages: ${e.message}")
            }
        }
    }

    // Load user info for the other user
    LaunchedEffect(otherUserId) {
        try {
            println("🔍 DEBUG: ChatDetailScreen - Looking up user with ID: $otherUserId")
            val userProfile = viewModel.findUserById(otherUserId)
            if (userProfile != null) {
                otherUserName = userProfile.displayName.ifEmpty {
                    userProfile.username.ifEmpty { userProfile.email }
                }
                otherUserImageUrl = userProfile.profileImageUrl
                println("🔍 DEBUG: ChatDetailScreen - Set user name to: $otherUserName")
            } else {
                println("🔍 DEBUG: ChatDetailScreen - Could not find user with ID: $otherUserId")
                otherUserName = "Unknown User"
            }
        } catch (e: Exception) {
            println("🔍 DEBUG: ChatDetailScreen - Error loading user info: ${e.message}")
            otherUserName = "Unknown User"
        }
    }

    // Filter messages to only show non-empty ones
    val filteredMessages = messages.filter { it.text.isNotBlank() }

    // Auto-scroll to bottom when new messages arrive (for bottom-attached behavior)
    LaunchedEffect(filteredMessages.size) {
        if (filteredMessages.isNotEmpty()) {
            // Always scroll to index 0 (bottom) when new messages arrive
            delay(100) // Small delay to ensure UI is ready
            listState.animateScrollToItem(0)
        }
    }

    // Group messages by date for timestamp separators
    val groupedMessages = remember(filteredMessages) {
        groupMessagesByDate(filteredMessages)
    }

    // Modern chat app layout with proper keyboard handling
    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        containerColor = Color(0xFFF8F9FA),
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .statusBarsPadding()
        ) {
            // Fixed top bar - always stays at the top
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Profile picture with enhanced styling
                        Card(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFA5C9CA)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (otherUserImageUrl != null) {
                                    AsyncImage(
                                        model = otherUserImageUrl,
                                        contentDescription = "User Avatar",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Default Avatar",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        // User name and status with improved styling
                        Column {
                            Text(
                                text = otherUserName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = AppFonts.KarlaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Color(0xFF395B64)
                                )
                            )

                            // Enhanced typing indicator or online status
                            AnimatedVisibility(
                                visible = typingStatus != null && typingStatus?.isTyping == true,
                                enter = fadeIn() + slideInVertically(),
                                exit = fadeOut() + slideOutVertically()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    repeat(3) { index ->
                                        val infiniteTransition = rememberInfiniteTransition(label = "typing")
                                        val alpha by infiniteTransition.animateFloat(
                                            initialValue = 0.3f,
                                            targetValue = 1f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(600, delayMillis = index * 200),
                                                repeatMode = RepeatMode.Reverse
                                            ), label = "alpha"
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
                                        text = "typing...",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                                            fontSize = 12.sp,
                                            color = Color(0xFF4CAF50)
                                        )
                                    )
                                }
                            }

                            // Online status when not typing
                            AnimatedVisibility(
                                visible = typingStatus == null || typingStatus?.isTyping == false,
                                enter = fadeIn() + slideInVertically(),
                                exit = fadeOut() + slideOutVertically()
                            ) {
                                Text(
                                    text = "online",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = AppFonts.KaiseiDecolFontFamily,
                                        fontSize = 12.sp,
                                        color = Color(0xFF666666)
                                    )
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF395B64)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )

            // Messages area that adapts to keyboard
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (filteredMessages.isEmpty()) {
                    // Enhanced empty state
                    Box(
                        modifier = Modifier
                            .height(lazyColumnHeight)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Card(
                                modifier = Modifier.size(80.dp),
                                shape = CircleShape,
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF395B64).copy(alpha = 0.1f)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Chat,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                        tint = Color(0xFF395B64).copy(alpha = 0.6f)
                                    )
                                }
                            }

                            Text(
                                text = "Start your conversation",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = AppFonts.KarlaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF395B64)
                                )
                            )

                            Text(
                                text = "Send a message to ${otherUserName}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = AppFonts.KaiseiDecolFontFamily,
                                    color = Color(0xFF666666)
                                )
                            )
                        }
                    }
                } else {
                    // Messages list with bottom-attached behavior (like modern chat apps)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .height(lazyColumnHeight)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        reverseLayout = true, // This makes messages stick to bottom
                        contentPadding = PaddingValues(
                            top = 16.dp,
                            bottom = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Reverse the order of grouped messages to show newest at bottom
                        groupedMessages.entries.reversed().forEach { (date, messagesInDay) ->
                            // Messages in reverse order (newest first in the reversed list)
                            items(messagesInDay.reversed(), key = { it.messageId }) { message ->
                                EnhancedMessageBubble(
                                    message = message,
                                    isFromCurrentUser = message.senderId == currentUserId,
                                    onLongPress = {
                                        selectedMessage = message
                                        showMessageActions = true
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                )
                            }

                            // Date separator
                            item {
                                DateSeparator(date = date)
                            }
                        }
                    }
                }
            }

            // Bottom section with typing indicator and input - no extra padding
            Column(
                modifier = Modifier.fillMaxWidth()
                // Removed windowInsetsPadding(WindowInsets.ime) - this was causing the issue
            ) {
                // Typing indicator above input bar
                AnimatedVisibility(
                    visible = typingStatus != null && typingStatus?.isTyping == true,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    TypingIndicatorBar()
                }

                // Enhanced message input bar - properly positioned at bottom
                EnhancedMessageInputBar(
                    message = newMessageText,
                    onMessageChange = { newMessageText = it },
                    onSendMessage = {
                        if (newMessageText.isNotBlank() && currentUserId != null) {
                            viewModel.sendMessage(
                                receiverId = otherUserId,
                                message = newMessageText
                            )
                            newMessageText = ""

                            // Auto-scroll to bottom when sending (index 0 in reversed layout)
                            coroutineScope.launch {
                                delay(100)
                                listState.animateScrollToItem(0)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Enhanced message actions popup
        if (showMessageActions && selectedMessage != null) {
            Popup(
                alignment = Alignment.Center,
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                ),
                onDismissRequest = {
                    showMessageActions = false
                    selectedMessage = null
                }
            ) {
                EnhancedMessageActionsPopup(
                    message = selectedMessage!!,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(selectedMessage!!.text))
                        showMessageActions = false
                        selectedMessage = null
                    },
                    onDelete = {
                        if (currentUserId != null) {
                            viewModel.deleteMessage(selectedMessage!!.messageId, currentUserId, otherUserId)
                        }
                        showMessageActions = false
                        selectedMessage = null
                    },
                    onDismiss = {
                        showMessageActions = false
                        selectedMessage = null
                    }
                )
            }
        }
    }
}

@Composable
fun EnhancedMessageActionsPopup(
    message: ChatMessage,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val isFromCurrentUser = message.senderId == FirebaseAuth.getInstance().currentUser?.uid

    Card(
        modifier = Modifier
            .wrapContentSize()
            .padding(24.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Copy action
            Surface(
                onClick = onCopy,
                color = Color.Transparent,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = Color(0xFF395B64),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Copy Message",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF395B64)
                        )
                    )
                }
            }

            // Delete action (only for current user's messages)
            if (isFromCurrentUser) {
                Surface(
                    onClick = onDelete,
                    color = Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFE53E3E),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Delete Message",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = AppFonts.KarlaFontFamily,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFE53E3E)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TypingIndicatorBar() {
    Surface(
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(3) { index ->
                val infiniteTransition = rememberInfiniteTransition(label = "typing")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, delayMillis = index * 200),
                        repeatMode = RepeatMode.Reverse
                    ), label = "alpha"
                )

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50).copy(alpha = alpha))
                )
            }

            Text(
                text = "typing...",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = AppFonts.KaiseiDecolFontFamily,
                    fontSize = 12.sp,
                    color = Color(0xFF4CAF50)
                )
            )
        }
    }
}

@Composable
fun DateSeparator(date: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF395B64).copy(alpha = 0.1f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Text(
                text = date,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = AppFonts.KaiseiDecolFontFamily,
                    fontSize = 12.sp,
                    color = Color(0xFF666666),
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun EnhancedMessageBubble(
    message: ChatMessage,
    isFromCurrentUser: Boolean,
    onLongPress: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = if (isFromCurrentUser) Alignment.End else Alignment.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isFromCurrentUser) 20.dp else 4.dp,
                bottomEnd = if (isFromCurrentUser) 4.dp else 20.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isFromCurrentUser) {
                    Color(0xFF395B64)
                } else {
                    Color.White
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .widthIn(max = 280.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress()
                        }
                    )
                }
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = AppFonts.KaiseiDecolFontFamily,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        color = if (isFromCurrentUser) {
                            Color.White
                        } else {
                            Color(0xFF333333)
                        }
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = formatMessageTime(message.timestamp),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                            fontSize = 12.sp,
                            color = if (isFromCurrentUser) {
                                Color.White.copy(alpha = 0.7f)
                            } else {
                                Color(0xFF666666)
                            }
                        )
                    )

                    // Enhanced seen indicator
                    if (isFromCurrentUser) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (message.seen) "✓✓" else "✓",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = AppFonts.KaiseiDecolFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (message.seen) {
                                    Color(0xFF4CAF50)
                                } else {
                                    Color.White.copy(alpha = 0.7f)
                                }
                            )
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedMessageInputBar(
    message: String,
    onMessageChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = message,
                onValueChange = onMessageChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Type a message...",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                            color = Color(0xFF666666).copy(alpha = 0.7f)
                        )
                    )
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFF395B64),
                    unfocusedTextColor = Color(0xFF395B64),
                    focusedBorderColor = Color(0xFF395B64),
                    unfocusedBorderColor = Color(0xFF395B64).copy(alpha = 0.3f),
                    cursorColor = Color(0xFF395B64),
                    focusedContainerColor = Color(0xFFF8F9FA),
                    unfocusedContainerColor = Color(0xFFF8F9FA)
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = AppFonts.KaiseiDecolFontFamily,
                    fontSize = 16.sp
                ),
                maxLines = 4
            )

            // Enhanced send button
            Card(
                onClick = onSendMessage,
                enabled = message.isNotBlank(),
                shape = CircleShape,
                colors = CardDefaults.cardColors(
                    containerColor = if (message.isNotBlank()) {
                        Color(0xFF395B64)
                    } else {
                        Color(0xFF666666).copy(alpha = 0.3f)
                    }
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = if (message.isNotBlank()) 4.dp else 0.dp
                ),
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

// Helper function to group messages by date
private fun groupMessagesByDate(messages: List<ChatMessage>): Map<String, List<ChatMessage>> {
    val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
    val today = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(Date())
    val yesterday = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000))

    return messages.groupBy { message ->
        val messageDate = dateFormat.format(Date(message.timestamp))
        when (messageDate) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> messageDate
        }
    }
}

private fun formatMessageTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
