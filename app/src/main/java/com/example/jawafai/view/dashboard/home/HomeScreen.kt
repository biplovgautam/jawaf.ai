package com.example.jawafai.view.dashboard.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.jawafai.R
import com.example.jawafai.repository.ChatRepositoryImpl
import com.example.jawafai.repository.UserRepositoryImpl
import com.example.jawafai.ui.theme.AppFonts
import com.example.jawafai.view.ui.theme.JawafAccent
import com.example.jawafai.view.dashboard.notifications.ChatNotification
import androidx.compose.ui.res.painterResource
import com.example.jawafai.view.dashboard.notifications.ChatPlatform
import com.example.jawafai.viewmodel.ChatViewModel
import com.example.jawafai.viewmodel.ChatViewModelFactory
import com.example.jawafai.viewmodel.UserViewModel
import com.example.jawafai.viewmodel.UserViewModelFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.airbnb.lottie.compose.*
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

// Data classes
data class ChatPreview(
    val id: String,
    val userName: String,
    val userImageUrl: String?,
    val lastMessage: String,
    val timestamp: Long,
    val unreadCount: Int
)

data class Notification(
    val id: String,
    val title: String,
    val message: String,
    val timestamp: Long,
    val isRead: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onProfileClick: () -> Unit = {},
    onChatBotClick: () -> Unit = {},
    onCompletePersonaClick: () -> Unit = {},
    onRecentChatClick: (String, String) -> Unit = { _, _ -> },
    onNotificationClick: () -> Unit = {},
    onSeeAllChatsClick: () -> Unit = {}
) {
    val context = LocalContext.current

    // Initialize ViewModels
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val repository = UserRepositoryImpl(auth, firestore)
    val userViewModel = remember { UserViewModelFactory(repository, auth).create(UserViewModel::class.java) }

    // Initialize ChatViewModel for recent chats
    val chatRepository = remember { ChatRepositoryImpl() }
    val chatViewModel = remember { ChatViewModelFactory(chatRepository, repository, auth).create(ChatViewModel::class.java) }

    // Observe user profile and chat summaries
    val userProfile by userViewModel.userProfile.observeAsState()
    val chatSummaries by chatViewModel.chatSummaries.collectAsState()

    // Store profile data in local variables to avoid smart cast issues
    val currentUserProfile = userProfile
    val userImageUrl = currentUserProfile?.imageUrl
    val userUsername = currentUserProfile?.username
    val userFirstName = currentUserProfile?.firstName

    // Check persona completion status based on new questions
    var isPersonaCompleted by remember { mutableStateOf(false) }

    // Check persona completion when user profile loads
    LaunchedEffect(currentUserProfile) {
        if (currentUserProfile != null) {
            try {
                val personaRef = FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(currentUserProfile.id)
                    .collection("persona")

                val personaData = personaRef.get().await()

                // Check if we have valid answers for the new questions
                val validAnswers = personaData.documents.filter { doc ->
                    val questionId = doc.id
                    val answer = doc.getString("answer")
                    // Check if this question ID exists in our new questions and has a valid answer
                    com.example.jawafai.model.PersonaQuestions.questions.any { it.id == questionId } &&
                            !answer.isNullOrBlank()
                }

                // Need at least 8 valid answers to be considered complete
                isPersonaCompleted = validAnswers.size >= 8
            } catch (e: Exception) {
                isPersonaCompleted = false
            }
        }
    }

    // Fetch user profile when screen loads
    LaunchedEffect(Unit) {
        userViewModel.fetchUserProfile()
    }

    // Get recent chats (only take first 3-4 for home screen)
    val recentChats = remember(chatSummaries) {
        chatSummaries.filter { it.lastMessage.isNotBlank() }.take(3)
    }

    // Observe external notifications from NotificationMemoryStore
    val externalNotifications by remember {
        derivedStateOf {
            com.example.jawafai.service.NotificationMemoryStore.getAllNotifications()
        }
    }

    // Get latest 2-3 notifications for home screen - only supported platforms
    val latestNotifications = remember(externalNotifications) {
        externalNotifications.filter { notification ->
            notification.packageName.contains("whatsapp", true) ||
            notification.packageName.contains("instagram", true) ||
            notification.packageName.contains("messenger", true) ||
            notification.packageName.contains("facebook.orca", true)
        }.take(2).map { notification ->
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        // Disable all system window insets to take full screen
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // App Logo image
                        Image(
                            painter = painterResource(id = R.drawable.logotext),
                            contentDescription = "Jawaf.AI Logo",
                            modifier = Modifier.height(28.dp)
                        )

                        // Enhanced Username and Profile section
                        Card(
                            modifier = Modifier
                                .clickable { onProfileClick() }
                                .padding(end = 16.dp), // Increased right padding
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFF0F8FF)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Username with better styling
                                currentUserProfile?.let { profile ->
                                    Text(
                                        text = profile.username,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = AppFonts.KarlaFontFamily,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = JawafAccent
                                        )
                                    )
                                }

                                // Enhanced Profile Picture
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFA5C9CA))
                                        .border(2.dp, JawafAccent.copy(alpha = 0.2f), CircleShape)
                                ) {
                                    if (userImageUrl != null) {
                                        AsyncImage(
                                            model = userImageUrl,
                                            contentDescription = "Profile Picture",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = "Default Profile",
                                            modifier = Modifier.align(Alignment.Center),
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                ),
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { paddingValues ->
        // Use padding values only for the top bar, handle bottom manually
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(top = paddingValues.calculateTopPadding())
                .padding(bottom = 36.dp) // Add bottom padding for navigation bar
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Hero Section - Enhanced App Purpose
            item {
                HeroSection(userFirstName = userFirstName ?: "User")
            }

            // Complete Persona Section (Always visible with completion indicator)
            item {
                CompletePersonaSection(
                    onCompletePersonaClick = onCompletePersonaClick,
                    isPersonaCompleted = isPersonaCompleted
                )
            }

            // Chat Bot Section with enhanced design
            item {
                ChatBotSection(onChatBotClick = onChatBotClick)
            }

            // Smart Notifications Section (Latest 2-3 notifications)
            item {
                SmartNotificationsSection(
                    notifications = latestNotifications,
                    onNotificationClick = onNotificationClick,
                    onSeeAllClick = onNotificationClick
                )
            }

            // Recent Chats Section
            item {
                RecentChatsSection(
                    chats = recentChats,
                    onChatClick = onRecentChatClick,
                    onSeeAllClick = onSeeAllChatsClick
                )
            }

            // Add bottom padding for navigation bar
            item {
                Spacer(modifier = Modifier.navigationBarsPadding())
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
@Composable
fun CompletePersonaSection(
    onCompletePersonaClick: () -> Unit,
    isPersonaCompleted: Boolean
) {
    // Lottie animation for persona
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.persona))
    val progress by animateLottieCompositionAsState(
        composition,
        iterations = LottieConstants.IterateForever
    )

    // Simple card design
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCompletePersonaClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF8F9FA)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Main content
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Animation
                composition?.let {
                    LottieAnimation(
                        composition = it,
                        progress = { progress },
                        modifier = Modifier.size(60.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Text content
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (isPersonaCompleted) "Persona Complete" else "Setup Persona",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF1A1A1A)
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (isPersonaCompleted)
                            "Your AI knows your communication style"
                        else
                            "Help AI learn your communication style",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                            fontSize = 14.sp,
                            color = Color(0xFF666666)
                        )
                    )
                }
            }

            // Status indicator at top right
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (isPersonaCompleted) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }
        }
    }
}
@Composable
fun ChatBotSection(onChatBotClick: () -> Unit) {
    // Lottie animation for live_chatbot
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.live_chatbot))
    val progress by animateLottieCompositionAsState(
        composition,
        iterations = LottieConstants.IterateForever
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChatBotClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = JawafAccent),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "AI Chat Companion",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = Color.White
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Get instant AI-powered responses tailored to your personality and communication style",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = AppFonts.KaiseiDecolFontFamily,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Start Chat",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Start Chat",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Lottie Animation
            composition?.let {
                LottieAnimation(
                    composition = it,
                    progress = { progress },
                    modifier = Modifier.size(100.dp)
                )
            }
        }
    }
}

@Composable
fun SmartNotificationsSection(
    notifications: List<ChatNotification>,
    onNotificationClick: () -> Unit,
    onSeeAllClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Smart Notifications",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = AppFonts.KarlaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = JawafAccent
                )
            )
            TextButton(
                onClick = onSeeAllClick
            ) {
                Text(
                    text = "See All",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontSize = 14.sp,
                        color = JawafAccent
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (notifications.isEmpty()) {
            // Show empty state
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
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
                            fontSize = 14.sp,
                            color = Color(0xFF666666)
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Notifications from supported apps will appear here",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                            fontSize = 12.sp,
                            color = Color(0xFF999999)
                        )
                    )
                }
            }
        } else {
            notifications.forEach { notification ->
                SmartNotificationItem(
                    notification = notification,
                    onClick = { onNotificationClick() }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun SmartNotificationItem(
    notification: ChatNotification,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF0F8FF)
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
                            imageVector = Icons.AutoMirrored.Filled.Reply,
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
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFA5C9CA))
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Sender Avatar",
                        modifier = Modifier.align(Alignment.Center),
                        tint = Color.White
                    )
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
                            fontSize = 14.sp,
                            color = JawafAccent
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = notification.message,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                            fontSize = 13.sp,
                            color = Color(0xFF666666)
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // AI reply indicator
            if (notification.hasGeneratedReply) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Reply",
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFF4CAF50)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (notification.isSent) "AI reply sent" else "AI reply generated",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontSize = 11.sp,
                            color = Color(0xFF4CAF50)
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun RecentChatsSection(
    chats: List<com.example.jawafai.model.ChatSummary>,
    onChatClick: (String, String) -> Unit,
    onSeeAllClick: () -> Unit = {}
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Chats",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = AppFonts.KarlaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = JawafAccent
                )
            )
            TextButton(
                onClick = onSeeAllClick
            ) {
                Text(
                    text = "See All",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontSize = 14.sp,
                        color = JawafAccent
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (chats.isEmpty()) {
            // Show empty state
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChatBubble,
                        contentDescription = "No chats",
                        modifier = Modifier.size(48.dp),
                        tint = Color(0xFF666666).copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No recent chats",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                            fontSize = 14.sp,
                            color = Color(0xFF666666)
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Start a conversation to see your chats here",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                            fontSize = 12.sp,
                            color = Color(0xFF999999)
                        )
                    )
                }
            }
        } else {
            chats.forEach { chat ->
                RecentChatItem(
                    chat = chat,
                    onClick = { onChatClick(chat.chatId, chat.otherUserId) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun RecentChatItem(
    chat: com.example.jawafai.model.ChatSummary,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // User Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFA5C9CA))
            ) {
                if (chat.otherUserImageUrl != null) {
                    AsyncImage(
                        model = chat.otherUserImageUrl,
                        contentDescription = "User Avatar",
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

            // Chat Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = chat.otherUserName,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = JawafAccent
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = formatTimestamp(chat.lastMessageTimestamp),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = AppFonts.KaiseiDecolFontFamily,
                                fontSize = 12.sp,
                                color = Color(0xFF666666)
                            )
                        )

                        if (chat.unreadCount > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        JawafAccent,
                                        CircleShape
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = chat.unreadCount.toString(),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = AppFonts.KarlaFontFamily,
                                        fontSize = 10.sp,
                                        color = Color.White
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = chat.lastMessage,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = AppFonts.KaiseiDecolFontFamily,
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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

@Composable
fun HeroSection(userFirstName: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = JawafAccent),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Welcome text - more compact
            Text(
                text = "Welcome back, $userFirstName!",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = AppFonts.KarlaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // App description - more concise
            Text(
                text = "AI-powered messaging assistant for WhatsApp, Instagram, and Messenger with personalized replies.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = AppFonts.KaiseiDecolFontFamily,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Supported Platforms integrated inside
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // WhatsApp
                CompactPlatformIcon(
                    animationRes = R.raw.whatsapp,
                    name = "WhatsApp",
                    color = Color(0xFF25D366)
                )

                // Instagram
                CompactPlatformIcon(
                    animationRes = R.raw.insta,
                    name = "Instagram",
                    color = Color(0xFFE4405F)
                )

                // Messenger
                CompactPlatformIcon(
                    animationRes = R.raw.messenger,
                    name = "Messenger",
                    color = Color(0xFF0084FF)
                )
            }
        }
    }
}

@Composable
fun CompactPlatformIcon(
    animationRes: Int,
    name: String,
    color: Color
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(animationRes))
    val progress by animateLottieCompositionAsState(
        composition,
        iterations = LottieConstants.IterateForever
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Remove background and increase animation size
        composition?.let {
            LottieAnimation(
                composition = it,
                progress = { progress },
                modifier = Modifier.size(50.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = AppFonts.KarlaFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.8f)
            )
        )
    }
}
