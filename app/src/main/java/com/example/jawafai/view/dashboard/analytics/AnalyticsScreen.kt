package com.example.jawafai.view.dashboard.analytics

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jawafai.managers.GroqApiManager
import com.example.jawafai.managers.NotificationFirebaseManager
import com.example.jawafai.service.NotificationMemoryStore
import com.example.jawafai.ui.theme.AppFonts
import kotlinx.coroutines.launch

// Theme colors matching app
private val JawafAccent = Color(0xFF1BC994)
private val JawafText = Color(0xFF191919)
private val JawafTextSecondary = Color(0xFF666666)
private val JawafBackground = Color(0xFFFAFAFA)
private val JawafCardBackground = Color.White

// Data classes for analytics
data class PlatformUsage(
    val name: String,
    val percentage: Float,
    val color: Color,
    val messageCount: Int
)

data class TopEngagedUser(
    val name: String,
    val messageCount: Int,
    val platformName: String,
    val packageName: String,
    val convoId: String
)

data class ReliabilityMetric(
    val name: String,
    val score: Int,
    val icon: ImageVector,
    val color: Color,
    val isInverted: Boolean = false // For ghosting rate, lower is better
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Collect real analytics from Firebase
    val analyticsData by NotificationFirebaseManager.getAnalyticsFlow().collectAsState(
        initial = NotificationFirebaseManager.NotificationAnalytics()
    )

    // Get local data for additional insights
    val conversations = remember { NotificationMemoryStore.getAllConversations() }
    val messages = remember { NotificationMemoryStore.getAllMessages() }

    // State for AI insight
    var aiInsight by remember { mutableStateOf<String?>(null) }
    var isLoadingInsight by remember { mutableStateOf(false) }

    // Calculate metrics
    val totalMessages = analyticsData.totalMessages.toInt().coerceAtLeast(messages.size)
    val aiRepliesGenerated = analyticsData.aiRepliesGenerated.toInt()
    val repliesSent = analyticsData.repliesSent.toInt()

    // Calculate Reply Speed based on time difference between received and sent messages
    val replySpeed = remember(messages) {
        calculateReplySpeed(messages)
    }

    // Ghosting Rate: Messages received without any reply
    val incomingMessages = messages.filter { !it.is_outgoing }
    val ignoredMessages = incomingMessages.count { msg ->
        // Check if there's a reply after this message in the same conversation
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

    // Consistency: How regularly user responds (based on conversation activity)
    val consistency = remember(messages, conversations) {
        calculateConsistency(messages, conversations)
    }

    // Engagement: Based on conversation count and message frequency
    val engagement = remember(messages, conversations) {
        calculateEngagement(messages, conversations)
    }

    // Communication Health Score: Weighted average of metrics
    // Reply Speed (30%) + Inverse Ghosting (30%) + Consistency (20%) + Engagement (20%)
    val communicationHealthScore = run {
        val inverseGhosting = 100 - ghostingRate
        ((replySpeed * 0.30f) + (inverseGhosting * 0.30f) + (consistency * 0.20f) + (engagement * 0.20f)).toInt().coerceIn(0, 100)
    }

    // Reliability metrics (removed Response Quality)
    val reliabilityMetrics = listOf(
        ReliabilityMetric("Reply Speed", replySpeed, Icons.Outlined.Speed, Color(0xFF4CAF50)),
        ReliabilityMetric("Ghosting Rate", ghostingRate, Icons.Outlined.VisibilityOff, Color(0xFFFF5722), isInverted = true),
        ReliabilityMetric("Consistency", consistency, Icons.Outlined.Timeline, Color(0xFF2196F3)),
        ReliabilityMetric("Engagement", engagement, Icons.Outlined.Forum, JawafAccent)
    )

    // Platform usage from real data
    val whatsappCount = analyticsData.whatsappCount.toInt().coerceAtLeast(
        messages.count { it.convo_id.contains("whatsapp", true) }
    )
    val instagramCount = analyticsData.instagramCount.toInt().coerceAtLeast(
        messages.count { it.convo_id.contains("instagram", true) }
    )
    val messengerCount = analyticsData.messengerCount.toInt().coerceAtLeast(
        messages.count { it.convo_id.contains("messenger", true) || it.convo_id.contains("facebook.orca", true) }
    )

    val totalPlatformCount = (whatsappCount + instagramCount + messengerCount).coerceAtLeast(1)

    val platformUsage = listOf(
        PlatformUsage(
            "WhatsApp",
            (whatsappCount.toFloat() / totalPlatformCount) * 100,
            Color(0xFF25D366),
            whatsappCount
        ),
        PlatformUsage(
            "Instagram",
            (instagramCount.toFloat() / totalPlatformCount) * 100,
            Color(0xFFE4405F),
            instagramCount
        ),
        PlatformUsage(
            "Messenger",
            (messengerCount.toFloat() / totalPlatformCount) * 100,
            Color(0xFF0084FF),
            messengerCount
        )
    )

    // Top engaged users: Most messages in past 24 hours
    val past24Hours = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
    val topEngagedUsers = remember(messages, conversations) {
        val recentMessagesByConvo = messages
            .filter { it.timestamp >= past24Hours }
            .groupBy { it.convo_id }
            .mapValues { it.value.size }

        conversations
            .map { convo ->
                val msgCount = recentMessagesByConvo[convo.convo_id] ?: 0
                val platformName = getPlatformName(convo.package_name)
                TopEngagedUser(
                    name = convo.display_name,
                    messageCount = msgCount,
                    platformName = platformName,
                    packageName = convo.package_name,
                    convoId = convo.convo_id
                )
            }
            .filter { it.messageCount > 0 }
            .sortedByDescending { it.messageCount }
            .take(5)
            .ifEmpty {
                // Fallback to all-time top conversations if no recent activity
                conversations
                    .map { convo ->
                        val msgCount = messages.count { it.convo_id == convo.convo_id }
                        TopEngagedUser(
                            name = convo.display_name,
                            messageCount = msgCount,
                            platformName = getPlatformName(convo.package_name),
                            packageName = convo.package_name,
                            convoId = convo.convo_id
                        )
                    }
                    .sortedByDescending { it.messageCount }
                    .take(3)
            }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = JawafBackground,
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = JawafCardBackground,
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = "Analytics",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            color = JawafText
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your communication insights",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                            fontSize = 14.sp,
                            color = JawafTextSecondary
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Communication Health Score Card with AI Insight
            item {
                CommunicationHealthCard(
                    score = communicationHealthScore,
                    aiInsight = aiInsight,
                    isLoadingInsight = isLoadingInsight,
                    onRefreshInsight = {
                        coroutineScope.launch {
                            isLoadingInsight = true
                            aiInsight = generateAIInsight(
                                healthScore = communicationHealthScore,
                                replySpeed = replySpeed,
                                ghostingRate = ghostingRate,
                                consistency = consistency,
                                engagement = engagement,
                                totalMessages = totalMessages
                            )
                            isLoadingInsight = false
                        }
                    }
                )
            }

            // Social Reliability Score Section
            item {
                Text(
                    text = "Reliability Metrics",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = JawafText
                    )
                )
            }

            item {
                ReliabilityScoreCard(metrics = reliabilityMetrics)
            }

            // Platform Usage Section
            item {
                Text(
                    text = "Platform Usage",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = JawafText
                    )
                )
            }

            item {
                PlatformUsageCard(platformUsage = platformUsage)
            }

            // Top Engaged Users Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Top Conversations",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = JawafText
                        )
                    )
                    Text(
                        text = "Last 24h",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                            fontSize = 12.sp,
                            color = JawafAccent
                        )
                    )
                }
            }

            item {
                TopEngagedUsersCard(users = topEngagedUsers)
            }

            // Bottom spacing for navigation bar
            item {
                Spacer(modifier = Modifier.navigationBarsPadding())
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

/**
 * Calculate reply speed based on time difference between received and sent messages
 * Returns a score from 0-100 where higher is faster
 */
private fun calculateReplySpeed(messages: List<NotificationMemoryStore.Message>): Int {
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
    // < 1 min = 100, 1-5 min = 90, 5-15 min = 80, 15-30 min = 70, 30-60 min = 60
    // 1-2 hours = 50, 2-6 hours = 40, 6-12 hours = 30, 12-24 hours = 20
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
private fun calculateConsistency(
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
private fun calculateEngagement(
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

/**
 * Get platform display name from package name
 */
private fun getPlatformName(packageName: String): String {
    return when {
        packageName.contains("whatsapp", true) -> "WhatsApp"
        packageName.contains("instagram", true) -> "Instagram"
        packageName.contains("messenger", true) || packageName.contains("facebook.orca", true) -> "Messenger"
        packageName.contains("telegram", true) -> "Telegram"
        packageName.contains("snapchat", true) -> "Snapchat"
        else -> "Other"
    }
}

/**
 * Get platform color from package name
 */
private fun getPlatformColor(packageName: String): Color {
    return when {
        packageName.contains("whatsapp", true) -> Color(0xFF25D366)
        packageName.contains("instagram", true) -> Color(0xFFE4405F)
        packageName.contains("messenger", true) || packageName.contains("facebook.orca", true) -> Color(0xFF0084FF)
        packageName.contains("telegram", true) -> Color(0xFF0088CC)
        packageName.contains("snapchat", true) -> Color(0xFFFFFC00)
        else -> Color(0xFF666666)
    }
}

/**
 * Generate AI insight based on metrics - SHORT and informative
 */
private suspend fun generateAIInsight(
    healthScore: Int,
    replySpeed: Int,
    ghostingRate: Int,
    consistency: Int,
    engagement: Int,
    totalMessages: Int
): String {
    return try {
        val prompt = """
Generate ONE short sentence (max 15 words) about communication habits. Include one emoji.
Metrics: Health $healthScore%, Speed $replySpeed%, Ghosting $ghostingRate%, Consistency $consistency%, Engagement $engagement%
Be friendly and focus on the most notable metric. No markdown.
        """.trimIndent()

        val response = GroqApiManager.getChatResponse(prompt, emptyList())
        if (response.success && response.message != null) {
            response.message.trim().take(100)
        } else {
            getDefaultInsight(healthScore, ghostingRate)
        }
    } catch (e: Exception) {
        getDefaultInsight(healthScore, ghostingRate)
    }
}

private fun getDefaultInsight(healthScore: Int, ghostingRate: Int): String {
    return when {
        healthScore >= 80 -> "Great communication habits! Your connections appreciate your responsiveness. ✨"
        healthScore >= 60 -> "You're doing well! A few more timely replies could boost your score. 📈"
        ghostingRate > 50 -> "Some messages might need attention. Check your unreads when you can! 📬"
        else -> "Room to grow! Small improvements in response time can make a big difference. 💪"
    }
}

@Composable
fun CommunicationHealthCard(
    score: Int,
    aiInsight: String?,
    isLoadingInsight: Boolean,
    onRefreshInsight: () -> Unit
) {
    val animatedScore by animateFloatAsState(
        targetValue = score.toFloat(),
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "score_animation"
    )

    val healthStatus = when {
        score >= 80 -> "Excellent"
        score >= 60 -> "Good"
        score >= 40 -> "Fair"
        else -> "Needs Work"
    }

    val statusColor = when {
        score >= 80 -> Color(0xFF4CAF50)
        score >= 60 -> JawafAccent
        score >= 40 -> Color(0xFFFFB800)
        else -> Color(0xFFFF5722)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = JawafText
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Communication Health",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                )

                Text(
                    text = "This Week",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = AppFonts.KaiseiDecolFontFamily,
                        fontSize = 12.sp,
                        color = JawafAccent
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Circular Progress with Score
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(160.dp)
            ) {
                // Background circle
                Canvas(modifier = Modifier.size(160.dp)) {
                    drawArc(
                        color = Color.White.copy(alpha = 0.1f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round),
                        size = Size(size.width, size.height)
                    )
                }

                // Progress arc
                Canvas(modifier = Modifier.size(160.dp)) {
                    drawArc(
                        color = statusColor,
                        startAngle = -90f,
                        sweepAngle = (animatedScore / 100f) * 360f,
                        useCenter = false,
                        style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round),
                        size = Size(size.width, size.height)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${animatedScore.toInt()}%",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 42.sp,
                            color = Color.White
                        )
                    )
                    Text(
                        text = healthStatus,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                            fontSize = 14.sp,
                            color = statusColor
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Generate Insight Button
            if (aiInsight == null && !isLoadingInsight) {
                OutlinedButton(
                    onClick = onRefreshInsight,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = JawafAccent
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, JawafAccent)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Generate AI Insight",
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            } else {
                // AI Insight Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.1f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = JawafAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))

                            if (isLoadingInsight) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = JawafAccent,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Analyzing...",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                    )
                                }
                            } else {
                                Text(
                                    text = aiInsight ?: "",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = AppFonts.KaiseiDecolFontFamily,
                                        fontSize = 12.sp,
                                        color = Color.White,
                                        lineHeight = 18.sp
                                    ),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Regenerate button
                        if (!isLoadingInsight && aiInsight != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = onRefreshInsight,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = null,
                                        tint = JawafAccent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Refresh",
                                        color = JawafAccent,
                                        fontSize = 11.sp,
                                        fontFamily = AppFonts.KarlaFontFamily
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReliabilityScoreCard(metrics: List<ReliabilityMetric>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = JawafCardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            metrics.forEach { metric ->
                ReliabilityMetricRow(metric = metric)
            }
        }
    }
}

@Composable
fun ReliabilityMetricRow(metric: ReliabilityMetric) {
    val animatedProgress by animateFloatAsState(
        targetValue = metric.score / 100f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "metric_animation"
    )

    // For inverted metrics (like ghosting), lower score is better
    val displayColor = if (metric.isInverted) {
        when {
            metric.score <= 20 -> Color(0xFF4CAF50) // Low ghosting = green
            metric.score <= 40 -> JawafAccent
            metric.score <= 60 -> Color(0xFFFFB800)
            else -> Color(0xFFFF5722) // High ghosting = red
        }
    } else {
        metric.color
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(displayColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = metric.icon,
                contentDescription = null,
                tint = displayColor,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = metric.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = JawafText
                    )
                )
                Text(
                    text = "${metric.score}%",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = displayColor
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFE8E8E8))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(displayColor)
                )
            }
        }
    }
}

@Composable
fun PlatformUsageCard(platformUsage: List<PlatformUsage>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = JawafCardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pie Chart
                Box(
                    modifier = Modifier.size(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PieChart(platformUsage = platformUsage)

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${platformUsage.sumOf { it.messageCount }}",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = AppFonts.KarlaFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                color = JawafText
                            )
                        )
                        Text(
                            text = "Total",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = AppFonts.KaiseiDecolFontFamily,
                                fontSize = 11.sp,
                                color = JawafTextSecondary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(24.dp))

                // Legend
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    platformUsage.forEach { platform ->
                        PlatformLegendItem(platform = platform)
                    }
                }
            }
        }
    }
}

@Composable
fun PieChart(platformUsage: List<PlatformUsage>) {
    var startAngle = -90f

    Canvas(modifier = Modifier.size(130.dp)) {
        platformUsage.forEach { platform ->
            val sweepAngle = (platform.percentage / 100f) * 360f
            drawArc(
                color = platform.color,
                startAngle = startAngle,
                sweepAngle = sweepAngle.coerceAtLeast(1f),
                useCenter = false,
                style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Butt),
                size = Size(size.width, size.height)
            )
            startAngle += sweepAngle
        }
    }
}

@Composable
fun PlatformLegendItem(platform: PlatformUsage) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(platform.color)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column {
            Text(
                text = platform.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = AppFonts.KarlaFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = JawafText
                )
            )
            Text(
                text = "${platform.percentage.toInt()}% • ${platform.messageCount} msgs",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = AppFonts.KaiseiDecolFontFamily,
                    fontSize = 11.sp,
                    color = JawafTextSecondary
                )
            )
        }
    }
}

@Composable
fun TopEngagedUsersCard(users: List<TopEngagedUser>) {
    if (users.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = JawafCardBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Forum,
                        contentDescription = null,
                        tint = JawafTextSecondary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No conversations yet",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                            color = JawafTextSecondary
                        )
                    )
                }
            }
        }
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = JawafCardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            users.forEachIndexed { index, user ->
                TopEngagedUserRow(
                    rank = index + 1,
                    user = user
                )

                if (index < users.size - 1) {
                    HorizontalDivider(
                        color = Color(0xFFF0F0F0),
                        thickness = 1.dp,
                        modifier = Modifier.padding(start = 56.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TopEngagedUserRow(
    rank: Int,
    user: TopEngagedUser
) {
    val rankColors = listOf(
        JawafAccent,           // 1st - Accent
        Color(0xFF4285F4),     // 2nd - Blue
        Color(0xFFFFB800)      // 3rd - Gold
    )

    val platformColor = getPlatformColor(user.packageName)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank badge
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(rankColors.getOrElse(rank - 1) { JawafTextSecondary }),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$rank",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = AppFonts.KarlaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White
                )
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // User avatar placeholder
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(platformColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = user.name.take(2).uppercase(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = AppFonts.KarlaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = platformColor
                )
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // User name and platform
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = AppFonts.KarlaFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = JawafText
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Platform tag
            Text(
                text = user.platformName,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = AppFonts.KaiseiDecolFontFamily,
                    fontSize = 11.sp,
                    color = platformColor
                )
            )
        }

        // Message count
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Chat,
                    contentDescription = null,
                    tint = JawafTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${user.messageCount}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = JawafText
                    )
                )
            }
            Text(
                text = "messages",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = AppFonts.KaiseiDecolFontFamily,
                    fontSize = 10.sp,
                    color = JawafTextSecondary
                )
            )
        }
    }
}

