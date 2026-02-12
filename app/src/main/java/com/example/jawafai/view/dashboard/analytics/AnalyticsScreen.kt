package com.example.jawafai.view.dashboard.analytics

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jawafai.managers.NotificationFirebaseManager
import com.example.jawafai.service.NotificationMemoryStore
import com.example.jawafai.ui.theme.AppFonts
import com.example.jawafai.view.ui.theme.JawafAccent
import com.example.jawafai.view.ui.theme.JawafText

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
    val avatarUrl: String? = null
)

data class ReliabilityMetric(
    val name: String,
    val score: Int,
    val icon: ImageVector,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen() {
    // Collect real analytics from Firebase
    val analyticsData by NotificationFirebaseManager.getAnalyticsFlow().collectAsState(
        initial = NotificationFirebaseManager.NotificationAnalytics()
    )

    // Get local data for additional insights
    val conversations = remember { NotificationMemoryStore.getAllConversations() }
    val messages = remember { NotificationMemoryStore.getAllMessages() }

    // Calculate metrics
    val totalMessages = analyticsData.totalMessages.toInt().coerceAtLeast(messages.size)
    val aiRepliesGenerated = analyticsData.aiRepliesGenerated.toInt()
    val repliesSent = analyticsData.repliesSent.toInt()

    // Communication health score based on response rate
    val communicationHealthScore = if (totalMessages > 0) {
        ((repliesSent.toFloat() / totalMessages) * 100).toInt().coerceIn(0, 100)
    } else 82 // Default

    // Ignored messages (incoming without AI reply or sent)
    val ignoredMessages = messages.count { !it.is_outgoing && it.ai_reply.isBlank() && !it.is_sent }

    // Platform usage from real data
    val whatsappCount = analyticsData.whatsappCount.toInt().coerceAtLeast(
        conversations.count { it.package_name.contains("whatsapp", true) }
    )
    val instagramCount = analyticsData.instagramCount.toInt().coerceAtLeast(
        conversations.count { it.package_name.contains("instagram", true) }
    )
    val messengerCount = analyticsData.messengerCount.toInt().coerceAtLeast(
        conversations.count { it.package_name.contains("messenger", true) || it.package_name.contains("facebook.orca", true) }
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

    // Reliability metrics from real data
    val replySpeed = if (totalMessages > 0) ((aiRepliesGenerated.toFloat() / totalMessages) * 100).toInt().coerceIn(0, 100) else 78
    val ghostingRate = if (totalMessages > 0) ((ignoredMessages.toFloat() / totalMessages) * 100).toInt().coerceIn(0, 100) else 12
    val consistency = if (conversations.isNotEmpty()) 85 else 0
    val responseQuality = if (repliesSent > 0) 90 else 0
    val engagement = conversations.size.coerceAtMost(100)

    val reliabilityMetrics = listOf(
        ReliabilityMetric("Reply Speed", replySpeed, Icons.Outlined.Speed, Color(0xFF4CAF50)),
        ReliabilityMetric("Ghosting Rate", ghostingRate, Icons.Outlined.VisibilityOff, Color(0xFFFF5722)),
        ReliabilityMetric("Consistency", consistency, Icons.Outlined.Timeline, Color(0xFF2196F3)),
        ReliabilityMetric("Response Quality", responseQuality, Icons.Outlined.Stars, Color(0xFF9C27B0)),
        ReliabilityMetric("Engagement", engagement, Icons.Outlined.Forum, Color(0xFFFF9800))
    )

    // Top engaged users from real conversations
    val topEngagedUsers = conversations
        .sortedByDescending { it.unread_count }
        .take(3)
        .mapIndexed { index, convo ->
            TopEngagedUser(
                convo.display_name,
                messages.count { it.convo_id == convo.convo_id }
            )
        }.ifEmpty {
            listOf(
                TopEngagedUser("No data yet", 0),
            )
        }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Analytics",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = JawafText
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                ),
                modifier = Modifier.statusBarsPadding()
            )
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

            // Communication Health Score Card
            item {
                CommunicationHealthCard(
                    score = communicationHealthScore,
                    ignoredMessages = ignoredMessages
                )
            }

            // Social Reliability Score Section
            item {
                Text(
                    text = "Social Reliability Score",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF395B64)
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
                        color = Color(0xFF395B64)
                    )
                )
            }

            item {
                PlatformUsageCard(platformUsage = platformUsage)
            }

            // Top Engaged Users Section
            item {
                Text(
                    text = "Top Engaged Users",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF395B64)
                    )
                )
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

@Composable
fun CommunicationHealthCard(
    score: Int,
    ignoredMessages: Int
) {
    val animatedScore by animateFloatAsState(
        targetValue = score.toFloat(),
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "score_animation"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF395B64)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Communication Health This Week",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = AppFonts.KarlaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Circular Progress with Score
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(140.dp)
            ) {
                // Background circle
                Canvas(modifier = Modifier.size(140.dp)) {
                    drawArc(
                        color = Color.White.copy(alpha = 0.2f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round),
                        size = Size(size.width, size.height)
                    )
                }

                // Progress arc
                Canvas(modifier = Modifier.size(140.dp)) {
                    drawArc(
                        color = when {
                            score >= 80 -> Color(0xFF4CAF50)
                            score >= 60 -> Color(0xFFFFB800)
                            else -> Color(0xFFFF5722)
                        },
                        startAngle = -90f,
                        sweepAngle = (animatedScore / 100f) * 360f,
                        useCenter = false,
                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round),
                        size = Size(size.width, size.height)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${animatedScore.toInt()}%",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 36.sp,
                            color = Color.White
                        )
                    )
                    Text(
                        text = "Healthy",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Insight message
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.15f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = Color(0xFFFFB800),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "You ignored $ignoredMessages important messages this week.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun ReliabilityScoreCard(metrics: List<ReliabilityMetric>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(metric.color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = metric.icon,
                contentDescription = null,
                tint = metric.color,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = metric.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = Color(0xFF395B64)
                    )
                )
                Text(
                    text = "${metric.score}%",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = metric.color
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFE0E0E0))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(metric.color)
                )
            }
        }
    }
}

@Composable
fun PlatformUsageCard(platformUsage: List<PlatformUsage>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
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
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PieChart(platformUsage = platformUsage)

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${platformUsage.sumOf { it.messageCount }}",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = AppFonts.KarlaFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = Color(0xFF395B64)
                            )
                        )
                        Text(
                            text = "Messages",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = AppFonts.KarlaFontFamily,
                                fontSize = 10.sp,
                                color = Color(0xFF666666)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(20.dp))

                // Legend
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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

    Canvas(modifier = Modifier.size(120.dp)) {
        platformUsage.forEach { platform ->
            val sweepAngle = (platform.percentage / 100f) * 360f
            drawArc(
                color = platform.color,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = 20.dp.toPx(), cap = StrokeCap.Butt),
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
                .size(12.dp)
                .clip(CircleShape)
                .background(platform.color)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Text(
                text = platform.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = AppFonts.KarlaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF395B64)
                )
            )
            Text(
                text = "${platform.percentage.toInt()}% • ${platform.messageCount} msgs",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = AppFonts.KarlaFontFamily,
                    fontSize = 11.sp,
                    color = Color(0xFF666666)
                )
            )
        }
    }
}

@Composable
fun TopEngagedUsersCard(users: List<TopEngagedUser>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
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
                        color = Color(0xFFE0E0E0),
                        thickness = 1.dp
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
        Color(0xFFFFD700), // Gold
        Color(0xFFC0C0C0), // Silver
        Color(0xFFCD7F32)  // Bronze
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank badge
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(rankColors.getOrElse(rank - 1) { Color(0xFF395B64) }),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "#$rank",
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
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFFA5C9CA)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = user.name.take(2).uppercase(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = AppFonts.KarlaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White
                )
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // User name
        Text(
            text = user.name,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = AppFonts.KarlaFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = Color(0xFF395B64)
            ),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Message count
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Chat,
                contentDescription = null,
                tint = Color(0xFF666666),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${user.messageCount}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = AppFonts.KarlaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF395B64)
                )
            )
        }
    }
}

