package com.example.jawafai.view.dashboard.chat

import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.airbnb.lottie.compose.*
import com.example.jawafai.R
import com.example.jawafai.model.ChatSummary
import com.example.jawafai.repository.ChatRepositoryImpl
import com.example.jawafai.repository.UserRepositoryImpl
import com.example.jawafai.ui.theme.AppFonts
import com.example.jawafai.utils.UserMigrationUtils
import com.example.jawafai.viewmodel.ChatViewModel
import com.example.jawafai.viewmodel.ChatViewModelFactory
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    onNavigateToChat: (chatId: String, otherUserId: String) -> Unit,
    onNavigateToChatBot: () -> Unit = {}
) {
    val auth = FirebaseAuth.getInstance()
    val chatRepository = remember { ChatRepositoryImpl() }
    val userRepository = remember { UserRepositoryImpl(auth, FirebaseFirestore.getInstance()) }
    val viewModel: ChatViewModel = viewModel(
        factory = ChatViewModelFactory(chatRepository, userRepository, auth)
    )

    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    val chatSummaries by viewModel.chatSummaries.collectAsState()
    val foundUser by viewModel.foundUser.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var isNewChatDialogVisible by remember { mutableStateOf(false) }
    var selectedUser by remember { mutableStateOf<com.example.jawafai.repository.UserProfile?>(null) }
    var showInitialLoading by remember { mutableStateOf(true) }
    var isSearchLoading by remember { mutableStateOf(false) }
    var chatToDelete by remember { mutableStateOf<ChatSummary?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Auto-migrate current user to database when screen loads
    LaunchedEffect(Unit) {
        UserMigrationUtils.saveCurrentUserToDatabase()
        UserMigrationUtils.showAllUsersInDatabase()
        // Set up real-time unread count monitoring
        viewModel.refreshUnreadCounts()
    }

    // Monitor chat summaries for real-time updates
    LaunchedEffect(chatSummaries) {
        if (chatSummaries.isNotEmpty()) {
            showInitialLoading = false
        }
    }

    // Clear search state when chat summaries update (important for real-time updates)
    LaunchedEffect(chatSummaries) {
        // If we're searching and the search results change due to chat deletion
        if (isSearchActive && searchQuery.isNotEmpty()) {
            // Update search results immediately
            delay(100) // Small delay to ensure UI consistency
        }
    }

    // Monitor for successful chat deletion and provide feedback
    val errorMessage by viewModel.errorMessage.collectAsState()
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            // Show error message if deletion fails
            // You can add a snackbar or toast here if needed
            delay(3000)
            viewModel.clearError()
        }
    }

    // Pull-to-refresh state
    val isRefreshing by remember { derivedStateOf { isLoading } }
    val pullToRefreshState = rememberSwipeRefreshState(isRefreshing)

    // Handle back press for search
    DisposableEffect(isSearchActive) {
        val callback = object : OnBackPressedCallback(isSearchActive) {
            override fun handleOnBackPressed() {
                if (isSearchActive) {
                    searchQuery = ""
                    isSearchActive = false
                    keyboardController?.hide()
                    viewModel.clearFoundUser()
                }
            }
        }
        backDispatcher?.addCallback(callback)
        onDispose {
            callback.remove()
        }
    }

    // Combined search results: existing chats + found users
    val searchResults = remember(searchQuery, chatSummaries, foundUser) {
        if (searchQuery.isBlank()) {
            chatSummaries.map { SearchResult.ExistingChat(it) }
        } else {
            val filteredChats = chatSummaries.filter { summary ->
                summary.otherUserName.contains(searchQuery, ignoreCase = true) ||
                summary.lastMessage.contains(searchQuery, ignoreCase = true)
            }.map { SearchResult.ExistingChat(it) }

            val userResults = if (foundUser != null) {
                listOf(SearchResult.NewUser(foundUser!!))
            } else {
                emptyList()
            }

            filteredChats + userResults
        }
    }

    // Debounced search for users when query changes
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            isSearchLoading = true
            delay(500) // Wait for user to finish typing
            delay(100) // Show loading animation for 0.1s
            viewModel.findUserByEmailOrUsername(searchQuery)
            isSearchLoading = false
        } else {
            viewModel.clearFoundUser()
            isSearchLoading = false
        }
    }

    // Handle initial loading
    LaunchedEffect(chatSummaries) {
        if (chatSummaries.isNotEmpty()) {
            showInitialLoading = false
        } else {
            delay(1000)
            showInitialLoading = false
        }
    }

    // Handle refresh
    fun handleRefresh() {
        coroutineScope.launch {
            viewModel.refreshChatSummaries()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Chats",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = Color(0xFF395B64)
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
        floatingActionButton = {
            // Lottie animation for bot
            val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.bot))
            val progress by animateLottieCompositionAsState(
                composition,
                iterations = LottieConstants.IterateForever
            )

            Box(
                modifier = Modifier
                    .padding(
                        bottom = WindowInsets.navigationBars
                            .asPaddingValues()
                            .calculateBottomPadding() + 70.dp // Add extra spacing above nav bar
                    )
            ) {
                FloatingActionButton(
                    onClick = { onNavigateToChatBot() },
                    containerColor = Color.Transparent,
                    contentColor = Color.Transparent,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp
                    ),
                    modifier = Modifier.size(100.dp)
                ) {
                    LottieAnimation(
                        composition = composition,
                        progress = { progress },
                        modifier = Modifier.size(90.dp)
                    )
                }
            }
        }

    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            SwipeRefresh(
                state = pullToRefreshState,
                onRefresh = { handleRefresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                ) {
                    // Reduced padding for search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            isSearchActive = it.isNotEmpty()
                        },
                        placeholder = {
                            Text(
                                "Search chats or find users...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = AppFonts.KaiseiDecolFontFamily,
                                    color = Color(0xFF666666)
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color(0xFF395B64)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    isSearchActive = false
                                    keyboardController?.hide()
                                    viewModel.clearFoundUser()
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear search",
                                        tint = Color(0xFF666666)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp)
                            .focusRequester(focusRequester),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF395B64),
                            unfocusedTextColor = Color(0xFF395B64),
                            cursorColor = Color(0xFF395B64),
                            focusedBorderColor = Color(0xFF395B64),
                            unfocusedBorderColor = Color(0xFF666666).copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )

                    // Show search results when searching
                    if (isSearchActive && searchQuery.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .navigationBarsPadding(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(searchResults) { result ->
                                when (result) {
                                    is SearchResult.ExistingChat -> {
                                        ChatItem(
                                            chatSummary = result.chatSummary,
                                            onClick = {
                                                onNavigateToChat(result.chatSummary.chatId, result.chatSummary.otherUserId)
                                                isSearchActive = false
                                                keyboardController?.hide()
                                            },
                                            onLongClick = {
                                                chatToDelete = result.chatSummary
                                                showDeleteDialog = true
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        )
                                    }
                                    is SearchResult.NewUser -> {
                                        UserSearchItem(
                                            user = result.user,
                                            onClick = {
                                                selectedUser = result.user
                                                isSearchActive = false
                                                keyboardController?.hide()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Show chat list when not searching
                        if (showInitialLoading) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF395B64)
                                    )
                                    Text(
                                        text = "Loading chats...",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                                            color = Color(0xFF666666)
                                        )
                                    )
                                }
                            }
                        } else {
                            ChatList(
                                chatSummaries = chatSummaries,
                                onChatClick = onNavigateToChat,
                                onChatLongClick = { chatSummary ->
                                    chatToDelete = chatSummary
                                    showDeleteDialog = true
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        // New chat dialog
        if (isNewChatDialogVisible) {
            NewChatDialog(
                onDismiss = { isNewChatDialogVisible = false },
                onStartChat = { user ->
                    selectedUser = user
                    isNewChatDialogVisible = false
                },
                viewModel = viewModel
            )
        }

        // Delete confirmation dialog
        if (showDeleteDialog && chatToDelete != null) {
            AlertDialog(
                onDismissRequest = {
                    showDeleteDialog = false
                    chatToDelete = null
                },
                title = {
                    Text(
                        text = "Delete Chat",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF395B64)
                        )
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to delete this chat with ${chatToDelete?.otherUserName}? This action cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                            color = Color(0xFF666666)
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            chatToDelete?.let { chat ->
                                viewModel.deleteChat(chat.otherUserId)
                            }
                            showDeleteDialog = false
                            chatToDelete = null
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(
                            text = "Delete",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = AppFonts.KarlaFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            chatToDelete = null
                        }
                    ) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = AppFonts.KarlaFontFamily,
                                color = Color(0xFF666666)
                            )
                        )
                    }
                }
            )
        }

        // Selected user actions
        selectedUser?.let { user ->
            LaunchedEffect(user) {
                coroutineScope.launch {
                    val chatId = viewModel.createChatWithUser(user.userId)
                    if (chatId != null) {
                        onNavigateToChat(chatId, user.userId)
                    }
                    selectedUser = null
                }
            }
        }
    }
}

@Composable
private fun ChatList(
    chatSummaries: List<ChatSummary>,
    onChatClick: (String, String) -> Unit,
    onChatLongClick: (ChatSummary) -> Unit,
    modifier: Modifier = Modifier
) {
    // Filter out chats with empty messages
    val filteredChats = chatSummaries.filter { it.lastMessage.isNotBlank() }

    if (filteredChats.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color(0xFF395B64).copy(alpha = 0.3f)
                )
                Text(
                    text = "No chats yet",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        color = Color(0xFF666666)
                    )
                )
                Text(
                    text = "Start a new conversation",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = AppFonts.KaiseiDecolFontFamily,
                        color = Color(0xFF999999)
                    )
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = filteredChats,
                key = { it.chatId } // Use chatId as key for proper recomposition
            ) { chatSummary ->
                // Animate item appearance/disappearance for smooth deletion
                AnimatedVisibility(
                    visible = true,
                    enter = slideInHorizontally(
                        initialOffsetX = { 300 },
                        animationSpec = tween(300)
                    ) + fadeIn(animationSpec = tween(300)),
                    exit = slideOutHorizontally(
                        targetOffsetX = { -300 },
                        animationSpec = tween(300)
                    ) + fadeOut(animationSpec = tween(300))
                ) {
                    ChatItem(
                        chatSummary = chatSummary,
                        onClick = { onChatClick(chatSummary.chatId, chatSummary.otherUserId) },
                        onLongClick = { onChatLongClick(chatSummary) }
                    )
                }
            }

            // Add bottom padding for floating action button
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatItem(
    chatSummary: ChatSummary,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // User Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFA5C9CA))
            ) {
                if (chatSummary.otherUserImageUrl != null) {
                    AsyncImage(
                        model = chatSummary.otherUserImageUrl,
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
                        text = chatSummary.otherUserName,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF395B64)
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = formatTimestamp(chatSummary.lastMessageTimestamp),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = AppFonts.KaiseiDecolFontFamily,
                                fontSize = 12.sp,
                                color = Color(0xFF666666)
                            )
                        )

                        if (chatSummary.unreadCount > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        Color(0xFF395B64),
                                        CircleShape
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = chatSummary.unreadCount.toString(),
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
                    text = chatSummary.lastMessage,
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

@Composable
private fun UserSearchItem(
    user: com.example.jawafai.repository.UserProfile,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF8F9FA)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // User Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFA5C9CA))
            ) {
                if (user.profileImageUrl != null) {
                    AsyncImage(
                        model = user.profileImageUrl,
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

            // User Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = user.displayName.ifEmpty { user.username },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF395B64)
                    )
                )

                Text(
                    text = "@${user.username}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = AppFonts.KaiseiDecolFontFamily,
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                )
            }

            Icon(
                imageVector = Icons.Default.PersonAdd,
                contentDescription = "Start Chat",
                tint = Color(0xFF395B64),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// Helper classes for search results
sealed class SearchResult {
    data class ExistingChat(val chatSummary: ChatSummary) : SearchResult()
    data class NewUser(val user: com.example.jawafai.repository.UserProfile) : SearchResult()
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

// Placeholder for NewChatDialog - you can implement this based on your needs
@Composable
private fun NewChatDialog(
    onDismiss: () -> Unit,
    onStartChat: (com.example.jawafai.repository.UserProfile) -> Unit,
    viewModel: ChatViewModel
) {
    // Implementation for new chat dialog
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Start New Chat",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF395B64)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Use the search bar above to find users and start chatting!",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = AppFonts.KaiseiDecolFontFamily,
                        color = Color(0xFF666666)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "OK",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = AppFonts.KarlaFontFamily,
                                color = Color(0xFF395B64)
                            )
                        )
                    }
                }
            }
        }
    }
}
