package com.example.jawafai.view.dashboard.notifications

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jawafai.managers.NotificationFirebaseManager
import com.example.jawafai.service.NotificationMemoryStore
import com.example.jawafai.ui.theme.AppFonts
import com.example.jawafai.view.ui.theme.JawafAccent
import com.example.jawafai.view.ui.theme.JawafText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Platform filter enum
 */
enum class ChatPlatform(val displayName: String, val color: Color) {
    WHATSAPP("WhatsApp", Color(0xFF25D366)),
    INSTAGRAM("Instagram", Color(0xFFE4405F)),
    MESSENGER("Messenger", Color(0xFF0084FF)),
    ALL("All", Color(0xFF1BC994)),
    GENERAL("Other", Color(0xFF666666))
}

/**
 * Chat notification data class for home screen compatibility
 */
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

/**
 * Main Notification Screen with modern UI matching app theme
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    onNavigateBack: () -> Unit,
    onNavigateToConversation: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State
    var selectedFilter by remember { mutableStateOf(ChatPlatform.ALL) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var conversationToDelete by remember { mutableStateOf<NotificationMemoryStore.Conversation?>(null) }
    var isDeleting by remember { mutableStateOf(false) }

    // Get conversations from memory store - use observable state for reactivity
    val conversationsState = NotificationMemoryStore.getConversationsState()

    // Create sorted list that updates when state changes
    val conversations by remember {
        derivedStateOf {
            conversationsState.sortedByDescending { it.last_msg_time }
        }
    }

    // Filter conversations by platform
    val filteredConversations by remember(selectedFilter) {
        derivedStateOf {
            if (selectedFilter == ChatPlatform.ALL) {
                conversations
            } else {
                conversations.filter { convo ->
                    when (selectedFilter) {
                        ChatPlatform.WHATSAPP -> convo.package_name.contains("whatsapp", true)
                        ChatPlatform.INSTAGRAM -> convo.package_name.contains("instagram", true)
                        ChatPlatform.MESSENGER -> convo.package_name.contains("messenger", true) ||
                                convo.package_name.contains("facebook.orca", true)
                        else -> true
                    }
                }
            }
        }
    }

    // Delete conversation function
    fun deleteConversation(convo: NotificationMemoryStore.Conversation) {
        coroutineScope.launch {
            isDeleting = true
            try {
                // Delete from local store
                NotificationMemoryStore.deleteConversation(convo.convo_id)

                // Delete from Firebase
                NotificationFirebaseManager.deleteConversation(convo.convo_id)

                Toast.makeText(context, "Conversation deleted", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to delete", Toast.LENGTH_SHORT).show()
            } finally {
                isDeleting = false
                showDeleteDialog = false
                conversationToDelete = null
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog && conversationToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                conversationToDelete = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = Color(0xFFE57373),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Delete Conversation?",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = JawafText
                    )
                )
            },
            text = {
                Text(
                    text = "This will permanently delete all messages with ${conversationToDelete?.display_name}. This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = AppFonts.KaiseiDecolFontFamily,
                        color = Color(0xFF666666)
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = { conversationToDelete?.let { deleteConversation(it) } },
                    enabled = !isDeleting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373))
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        conversationToDelete = null
                    }
                ) {
                    Text("Cancel", color = JawafText)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }

    Scaffold(
        topBar = {
            // Modern header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    // Title
                    Text(
                        text = "Messages",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            color = JawafText
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${filteredConversations.size} conversations",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                            fontSize = 14.sp,
                            color = Color(0xFF666666)
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Platform filter chips - horizontally scrollable
                    val platforms = listOf(ChatPlatform.ALL, ChatPlatform.WHATSAPP, ChatPlatform.INSTAGRAM, ChatPlatform.MESSENGER)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(platforms) { platform ->
                            FilterChip(
                                selected = selectedFilter == platform,
                                onClick = { selectedFilter = platform },
                                label = {
                                    Text(
                                        text = platform.displayName,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = AppFonts.KarlaFontFamily,
                                            fontWeight = if (selectedFilter == platform) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        ),
                                        maxLines = 1
                                    )
                                },
                                leadingIcon = if (platform != ChatPlatform.ALL) {
                                    {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(platform.color)
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = JawafAccent.copy(alpha = 0.15f),
                                    selectedLabelColor = JawafAccent,
                                    containerColor = Color(0xFFF5F5F5),
                                    labelColor = Color(0xFF666666)
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = Color.Transparent,
                                    selectedBorderColor = JawafAccent,
                                    borderWidth = 1.dp,
                                    selectedBorderWidth = 1.dp,
                                    enabled = true,
                                    selected = selectedFilter == platform
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFFAFAFA))
        ) {
            if (filteredConversations.isEmpty()) {
                // Empty state
                EmptyConversationsState(selectedFilter)
            } else {
                // Conversations list
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(
                        items = filteredConversations,
                        key = { it.convo_id }
                    ) { conversation ->
                        ConversationCard(
                            conversation = conversation,
                            onClick = {
                                NotificationMemoryStore.markConversationAsRead(conversation.convo_id)
                                onNavigateToConversation(conversation.convo_id)
                            },
                            onLongClick = {
                                conversationToDelete = conversation
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationCard(
    conversation: NotificationMemoryStore.Conversation,
    onClick: () -> Unit,
    onLongClick: () -> Unit
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
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(20.dp),
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
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with platform indicator
            Box {
                // Avatar circle
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(platform.color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = conversation.display_name.take(2).uppercase(),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = platform.color
                        )
                    )
                }

                // Platform badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(platform.color)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Name
                    Text(
                        text = conversation.display_name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = JawafText
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Time
                    Text(
                        text = formatRelativeTime(conversation.last_msg_time),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                            fontSize = 11.sp,
                            color = if (hasUnread) JawafAccent else Color(0xFF999999)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Platform tag
                Text(
                    text = platform.displayName,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = AppFonts.KaiseiDecolFontFamily,
                        fontSize = 10.sp,
                        color = platform.color
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Last message preview
                    Text(
                        text = conversation.last_msg_content,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                            fontSize = 13.sp,
                            color = if (hasUnread) Color(0xFF444444) else Color(0xFF888888)
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
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(JawafAccent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (conversation.unread_count > 99) "99+"
                                       else conversation.unread_count.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = AppFonts.KarlaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
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
fun EmptyConversationsState(filter: ChatPlatform) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(JawafAccent.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Forum,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = JawafAccent
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (filter == ChatPlatform.ALL) "No messages yet"
                   else "No ${filter.displayName} messages",
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = AppFonts.KarlaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = JawafText
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Messages from connected apps will appear here",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = AppFonts.KaiseiDecolFontFamily,
                fontSize = 14.sp,
                color = Color(0xFF888888)
            )
        )
    }
}

/**
 * Format timestamp to relative time (e.g., "2m", "1h", "Yesterday")
 */
fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days < 2 -> "Yesterday"
        days < 7 -> "${days}d"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}

