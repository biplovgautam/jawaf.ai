package com.example.jawafai.view.dashboard.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
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
import com.example.jawafai.service.NotificationMemoryStore
import com.example.jawafai.view.dashboard.notifications.ChatPlatform
import com.example.jawafai.viewmodel.ChatViewModel
import com.example.jawafai.viewmodel.ChatViewModelFactory
import com.example.jawafai.viewmodel.UserViewModel
import com.example.jawafai.viewmodel.UserViewModelFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.airbnb.lottie.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

// Theme colors
private val JawafText = Color(0xFF191919)
private val JawafTextSecondary = Color(0xFF666666)
private val JawafBackground = Color(0xFFFAFAFA)

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
    onSettingsClick: () -> Unit = {},
    onChatBotClick: () -> Unit = {},
    onCompletePersonaClick: () -> Unit = {},
    onRecentChatClick: (String, String) -> Unit = { _, _ -> },
    onNotificationClick: () -> Unit = {},
    onSeeAllChatsClick: () -> Unit = {},
    onAnalyticsClick: () -> Unit = {}
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

    // Check if user is PRO (you can adjust this logic based on your pro status field)
    val isUserPro = remember(currentUserProfile) {
        currentUserProfile?.isPro ?: false
    }

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

    // Get top 3 conversations from NotificationMemoryStore for display
    val allConversations = remember { NotificationMemoryStore.getAllConversations() }
    val topConversations = remember(allConversations) {
        allConversations.sortedByDescending { it.last_msg_time }.take(3)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = JawafBackground,
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // App Logo image - ZOOMED
                    Image(
                        painter = painterResource(id = R.drawable.logotext),
                        contentDescription = "Jawaf.AI Logo",
                        modifier = Modifier
                            .height(36.dp)
                            .scale(1.2f),
                        contentScale = ContentScale.FillHeight
                    )

                    // Enhanced Username and Profile section with PRO badge
                    Card(
                        modifier = Modifier
                            .clickable { onSettingsClick() },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = JawafAccent.copy(alpha = 0.1f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Animated text - switches between username and PRO
                            AnimatedUserBadge(
                                username = userUsername ?: "User",
                                isPro = isUserPro
                            )

                            // Profile Picture
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(JawafAccent.copy(alpha = 0.2f))
                                    .border(2.dp, JawafAccent, CircleShape)
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
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(18.dp),
                                        tint = JawafAccent
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        // Get analytics data
        val messages = remember { NotificationMemoryStore.getAllMessages() }
        val totalMessages = messages.size
        val repliesSent = messages.count { it.is_sent }
        val aiRepliesGenerated = messages.count { it.ai_reply.isNotBlank() }

        // Calculate communication health score (EXACT SAME as Analytics page)
        val replySpeed = remember(messages) {
            calculateHomeReplySpeed(messages)
        }

        val incomingMessages = messages.filter { !it.is_outgoing }
        val ignoredMessages = incomingMessages.count { msg ->
            val hasReply = messages.any {
                it.convo_id == msg.convo_id &&
                it.is_outgoing &&
                it.timestamp > msg.timestamp
            }
            !hasReply && msg.ai_reply.isBlank()
        }
        val ghostingRate = if (incomingMessages.isNotEmpty()) {
            ((ignoredMessages.toFloat() / incomingMessages.size) * 100).toInt().coerceIn(0, 100)
        } else 0

        // Use ALL conversations for calculation, not just top 3
        val consistency = remember(messages, allConversations) {
            calculateHomeConsistency(messages, allConversations)
        }

        val engagement = remember(messages, allConversations) {
            calculateHomeEngagement(messages, allConversations)
        }

        val inverseGhosting = 100 - ghostingRate
        val communicationHealth = ((replySpeed * 0.30f) + (inverseGhosting * 0.30f) + (consistency * 0.20f) + (engagement * 0.20f)).toInt().coerceIn(0, 100)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(JawafBackground)
                .padding(top = paddingValues.calculateTopPadding())
                .padding(bottom = 36.dp)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Hero Section - Compact
            item {
                HeroSection(userFirstName = userFirstName ?: "User")
            }

            // Complete Persona Section - ONLY show if NOT completed
            if (!isPersonaCompleted) {
                item {
                    CompletePersonaSection(
                        onCompletePersonaClick = onCompletePersonaClick,
                        isPersonaCompleted = isPersonaCompleted
                    )
                }
            }

            // 2 Column Row: Chat Bot & Activity with Health
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // AI Companion Card - Left Column (with Lottie)
                    CompactChatBotCard(
                        onChatBotClick = onChatBotClick,
                        modifier = Modifier.weight(1f)
                    )

                    // Activity Card with Communication Health - Right Column (Clickable)
                    ActivityHealthCard(
                        healthPercentage = communicationHealth,
                        onClick = onAnalyticsClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Messages Section
            item {
                MessagesSection(
                    conversations = topConversations,
                    onMessageClick = onNotificationClick,
                    onSeeAllClick = onNotificationClick
                )
            }

            // Bottom padding
            item {
                Spacer(modifier = Modifier.navigationBarsPadding())
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

/**
 * Analytics Stats Row - 2x2 Grid
 */
@Composable
fun AnalyticsStatsRow(
    totalMessages: Int,
    conversations: Int,
    repliesSent: Int,
    aiGenerated: Int
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Your Stats",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = AppFonts.KarlaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = JawafText
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                value = totalMessages.toString(),
                label = "Messages",
                icon = Icons.Filled.Message,
                color = JawafAccent,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                value = conversations.toString(),
                label = "Chats",
                icon = Icons.Filled.Chat,
                color = Color(0xFF4285F4),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                value = repliesSent.toString(),
                label = "Sent",
                icon = Icons.AutoMirrored.Filled.Send,
                color = Color(0xFF34A853),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                value = aiGenerated.toString(),
                label = "AI",
                icon = Icons.Filled.AutoAwesome,
                color = Color(0xFFFFB800),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun StatCard(
    value: String,
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = AppFonts.KarlaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = JawafText
                )
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = AppFonts.KaiseiDecolFontFamily,
                    fontSize = 10.sp,
                    color = JawafTextSecondary
                )
            )
        }
    }
}

/**
 * Compact Chat Bot Card with Lottie animation
 */
@Composable
fun CompactChatBotCard(
    onChatBotClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Lottie animation for live_chatbot
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.live_chatbot))
    val progress by animateLottieCompositionAsState(
        composition,
        iterations = LottieConstants.IterateForever
    )

    Card(
        modifier = modifier
            .height(160.dp)
            .clickable { onChatBotClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = JawafAccent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Lottie Animation
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                composition?.let {
                    LottieAnimation(
                        composition = it,
                        progress = { progress },
                        modifier = Modifier.size(50.dp)
                    )
                }
            }

            Column {
                Text(
                    text = "AI Companion",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                )
                Text(
                    text = "Chat now →",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = AppFonts.KaiseiDecolFontFamily,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                )
            }
        }
    }
}

/**
 * Activity Card with Communication Health percentage - Clickable to navigate to Analytics
 */
@Composable
fun ActivityHealthCard(
    healthPercentage: Int,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val healthStatus = when {
        healthPercentage >= 80 -> "Excellent"
        healthPercentage >= 60 -> "Good"
        healthPercentage >= 40 -> "Fair"
        else -> "Needs Work"
    }

    val statusColor = when {
        healthPercentage >= 80 -> Color(0xFF4CAF50)
        healthPercentage >= 60 -> JawafAccent
        healthPercentage >= 40 -> Color(0xFFFFB800)
        else -> Color(0xFFFF5722)
    }

    Card(
        modifier = modifier
            .height(160.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF191919)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Health",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
            }

            Column {
                Text(
                    text = "$healthPercentage%",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp,
                        color = Color.White
                    )
                )
                Text(
                    text = healthStatus,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = AppFonts.KaiseiDecolFontFamily,
                        fontSize = 12.sp,
                        color = statusColor
                    )
                )
            }
        }
    }
}

/**
 * Quick Stats Card for 2-column layout (kept for reference)
 */
@Composable
fun QuickStatsCard(
    messagesCount: Int,
    conversationsCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(140.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF191919)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Activity",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                )
                Icon(
                    imageVector = Icons.Filled.TrendingUp,
                    contentDescription = null,
                    tint = JawafAccent,
                    modifier = Modifier.size(16.dp)
                )
            }

            Column {
                Text(
                    text = "${messagesCount + conversationsCount}",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        color = Color.White
                    )
                )
                Text(
                    text = "Total interactions",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = AppFonts.KaiseiDecolFontFamily,
                        fontSize = 11.sp,
                        color = JawafAccent
                    )
                )
            }
        }
    }
}

/**
 * Animated badge that switches between username and PRO label
 */
@Composable
fun AnimatedUserBadge(
    username: String,
    isPro: Boolean
) {
    if (isPro) {
        // Animated switching between name and PRO
        var showPro by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            while (true) {
                delay(3000) // Show name for 3 seconds
                showPro = true
                delay(2000) // Show PRO for 2 seconds
                showPro = false
            }
        }

        Box(
            modifier = Modifier.width(80.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            AnimatedContent(
                targetState = showPro,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(500)) + slideInHorizontally(
                        animationSpec = tween(500),
                        initialOffsetX = { -it }
                    )).togetherWith(
                        fadeOut(animationSpec = tween(500)) + slideOutHorizontally(
                            animationSpec = tween(500),
                            targetOffsetX = { it }
                        )
                    )
                },
                label = "pro_badge_animation"
            ) { targetShowPro ->
                if (targetShowPro) {
                    // PRO badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Pro",
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFFFFD700)
                        )
                        Text(
                            text = "PRO",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = AppFonts.KarlaFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD700)
                            )
                        )
                    }
                } else {
                    // Username
                    Text(
                        text = username,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = JawafText
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    } else {
        // Just show username for non-pro users
        Text(
            text = username,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = AppFonts.KarlaFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = JawafText
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
fun MessagesSection(
    conversations: List<NotificationMemoryStore.Conversation>,
    onMessageClick: () -> Unit,
    onSeeAllClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Messages",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = AppFonts.KarlaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = JawafText
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
                        fontWeight = FontWeight.SemiBold,
                        color = JawafAccent
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (conversations.isEmpty()) {
            // Empty state
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(JawafAccent.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Forum,
                            contentDescription = "No messages",
                            modifier = Modifier.size(32.dp),
                            tint = JawafAccent
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No messages yet",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = JawafText
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Messages from connected apps will appear here",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                            fontSize = 12.sp,
                            color = JawafTextSecondary
                        )
                    )
                }
            }
        } else {
            // Show top 3 conversations
            conversations.forEach { conversation ->
                HomeConversationItem(
                    conversation = conversation,
                    onClick = onMessageClick
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun HomeConversationItem(
    conversation: NotificationMemoryStore.Conversation,
    onClick: () -> Unit
) {
    val platform = when {
        conversation.package_name.contains("whatsapp", true) -> ChatPlatform.WHATSAPP
        conversation.package_name.contains("instagram", true) -> ChatPlatform.INSTAGRAM
        conversation.package_name.contains("messenger", true) ||
        conversation.package_name.contains("facebook.orca", true) -> ChatPlatform.MESSENGER
        else -> ChatPlatform.GENERAL
    }

    val hasUnread = conversation.unread_count > 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasUnread) Color.White else Color(0xFFFAFAFA)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (hasUnread) 4.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with platform color
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(platform.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = conversation.display_name.take(2).uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = platform.color
                    )
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = conversation.display_name,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 15.sp,
                            color = JawafText
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Platform tag
                    Text(
                        text = platform.displayName,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                            fontSize = 10.sp,
                            color = platform.color
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = conversation.last_msg_content,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                            fontSize = 13.sp,
                            color = JawafTextSecondary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Unread badge
                    if (hasUnread) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(JawafAccent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (conversation.unread_count > 9) "9+"
                                       else conversation.unread_count.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = AppFonts.KarlaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    color = Color.White
                                )
                            )
                        }
                    }
                }
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

/**
 * Calculate reply speed based on time difference between received and sent messages
 * Returns a score from 0-100 where higher is faster
 * EXACT SAME as AnalyticsScreen.calculateReplySpeed()
 */
private fun calculateHomeReplySpeed(messages: List<NotificationMemoryStore.Message>): Int {
    if (messages.isEmpty()) return 0

    val replyTimes = mutableListOf<Long>()

    // Group messages by conversation
    val messagesByConvo = messages.groupBy { it.convo_id }

    messagesByConvo.forEach { (_, convoMessages) ->
        val sortedMessages = convoMessages.sortedBy { it.timestamp }

        var lastIncomingTime: Long? = null

        for (msg in sortedMessages) {
            if (!msg.is_outgoing) {
                // Incoming message - record timestamp
                lastIncomingTime = msg.timestamp
            } else if (msg.is_outgoing && lastIncomingTime != null) {
                // Outgoing message after incoming - calculate reply time
                val replyTime = msg.timestamp - lastIncomingTime
                if (replyTime > 0 && replyTime < 24 * 60 * 60 * 1000) { // Within 24 hours
                    replyTimes.add(replyTime)
                }
                lastIncomingTime = null // Reset after reply
            }
        }
    }

    if (replyTimes.isEmpty()) return 50 // Default if no reply data

    val avgReplyTimeMs = replyTimes.average()

    // Convert to score: Faster reply = higher score
    return when {
        avgReplyTimeMs < 1 * 60 * 1000 -> 100
        avgReplyTimeMs < 5 * 60 * 1000 -> 90
        avgReplyTimeMs < 15 * 60 * 1000 -> 80
        avgReplyTimeMs < 30 * 60 * 1000 -> 70
        avgReplyTimeMs < 60 * 60 * 1000 -> 60
        avgReplyTimeMs < 2 * 60 * 60 * 1000 -> 50
        avgReplyTimeMs < 6 * 60 * 60 * 1000 -> 40
        avgReplyTimeMs < 12 * 60 * 60 * 1000 -> 30
        else -> 20
    }
}

/**
 * Calculate consistency based on regular response patterns
 */
private fun calculateHomeConsistency(
    messages: List<NotificationMemoryStore.Message>,
    conversations: List<NotificationMemoryStore.Conversation>
): Int {
    if (conversations.isEmpty()) return 0

    // Count conversations with at least one reply
    val conversationsWithReplies = conversations.count { convo ->
        messages.any { it.convo_id == convo.convo_id && it.is_outgoing }
    }

    val consistencyRate = (conversationsWithReplies.toFloat() / conversations.size) * 100
    return consistencyRate.toInt().coerceIn(0, 100)
}

/**
 * Calculate engagement based on message frequency and conversation count
 */
private fun calculateHomeEngagement(
    messages: List<NotificationMemoryStore.Message>,
    conversations: List<NotificationMemoryStore.Conversation>
): Int {
    if (conversations.isEmpty()) return 0

    val past7Days = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
    val recentMessages = messages.filter { it.timestamp >= past7Days }

    // Engagement based on recent activity
    val avgMessagesPerConvo = if (conversations.isNotEmpty()) {
        recentMessages.size.toFloat() / conversations.size
    } else 0f

    // Score based on average messages per conversation
    return when {
        avgMessagesPerConvo >= 20 -> 100
        avgMessagesPerConvo >= 15 -> 90
        avgMessagesPerConvo >= 10 -> 80
        avgMessagesPerConvo >= 5 -> 70
        avgMessagesPerConvo >= 3 -> 60
        avgMessagesPerConvo >= 1 -> 50
        else -> 30
    }
}
