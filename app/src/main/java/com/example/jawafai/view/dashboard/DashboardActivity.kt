package com.example.jawafai.view.dashboard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.jawafai.ui.theme.JawafaiTheme
import com.google.firebase.auth.FirebaseAuth
import androidx.core.view.WindowCompat
import com.example.jawafai.view.auth.LoginActivity
import com.example.jawafai.view.dashboard.chat.ChatBotScreen
import com.example.jawafai.view.dashboard.chat.ChatDetailScreen
import com.example.jawafai.view.dashboard.chat.ChatScreen
import com.example.jawafai.view.dashboard.home.HomeScreen
import com.example.jawafai.view.dashboard.notifications.NotificationScreen
import com.example.jawafai.view.dashboard.notifications.ConversationDetailScreen
import com.example.jawafai.view.dashboard.settings.PersonaScreen
import com.example.jawafai.view.dashboard.settings.ProfileScreen
import com.example.jawafai.view.dashboard.settings.SettingsScreen
import com.example.jawafai.view.dashboard.analytics.AnalyticsScreen
import com.example.jawafai.utils.WithNetworkMonitoring
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.outlined.BarChart
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog
import com.example.jawafai.viewmodel.UserViewModel
import com.example.jawafai.viewmodel.UserViewModelFactory
import com.example.jawafai.repository.UserRepositoryImpl
import com.google.firebase.firestore.FirebaseFirestore

class DashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enable full screen immersive mode
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        setContent {
            JawafaiTheme {
                WithNetworkMonitoring {
                    DashboardScreen(
                        onLogout = {
                            // Sign out from Firebase
                            FirebaseAuth.getInstance().signOut()

                            // Navigate directly to login screen
                            val intent = Intent(this@DashboardActivity, LoginActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish() // Close dashboard activity
                        }
                    )
                }
            }
        }
    }

    // Used to trigger permission re-check on resume
    private var onResumePermissionsCheck: (() -> Unit)? = null
    override fun onResume() {
        super.onResume()
    }

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

}

// Define navigation items for bottom navigation
sealed class BottomNavItem(
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val contentDescription: String
) {
    object Home : BottomNavItem(
        route = "home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        contentDescription = "Home"
    )

    object Analytics : BottomNavItem(
        route = "analytics",
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Outlined.BarChart,
        contentDescription = "Analytics"
    )

    object Chat : BottomNavItem(
        route = "chat",
        selectedIcon = Icons.Rounded.ChatBubble,
        unselectedIcon = Icons.Rounded.ChatBubbleOutline,
        contentDescription = "Chat"
    )

    object Notifications : BottomNavItem(
        route = "notifications",
        selectedIcon = Icons.Filled.Notifications,
        unselectedIcon = Icons.Outlined.Notifications,
        contentDescription = "Notifications"
    )

    object Settings : BottomNavItem(
        route = "settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        contentDescription = "Settings"
    )

    // Profile is no longer a bottom nav item, but a destination from Settings
    object Profile : BottomNavItem(
        route = "profile",
        selectedIcon = Icons.Rounded.Person,
        unselectedIcon = Icons.Rounded.PersonOutline,
        contentDescription = "Profile"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onLogout: () -> Unit,
) {
    val navController = rememberNavController()
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Analytics,
        BottomNavItem.Chat,
        BottomNavItem.Notifications,
        BottomNavItem.Settings
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    // Determine if we should show bottom bar and handle back press differently
    val showBottomBar = items.any { it.route == currentRoute }
    val isInMainScreen = items.any { it.route == currentRoute }

    // Global back press handling
    BackHandler(
        enabled = !isInMainScreen,
        onBack = {
            if (navController.previousBackStackEntry != null) {
                navController.popBackStack()
            }
        }
    )

    // Handle back press for main screens (exit app)
    BackHandler(
        enabled = isInMainScreen,
        onBack = {
            // For main screens, move app to background instead of closing
            (navController.context as? ComponentActivity)?.moveTaskToBack(true)
        }
    )

    // Helper function to get current tab index
    fun getCurrentTabIndex(): Int {
        return items.indexOfFirst { it.route == currentRoute }.takeIf { it >= 0 } ?: 0
    }

    // Helper function to navigate to tab by index with smooth animation
    fun navigateToTab(index: Int) {
        if (index in items.indices) {
            val targetRoute = items[index].route
            if (currentRoute != targetRoute) {
                navController.navigate(targetRoute) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = Color(0xFFE7F6F2),
                    contentColor = Color(0xFF395B64),
                    modifier = Modifier
                        .fillMaxWidth()
                        // Remove the bottom padding to attach directly to bottom
//                        .navigationBarsPadding()
                ) {
                    items.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true

                        NavigationBarItem(
                            icon = {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.contentDescription,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    AnimatedVisibility(visible = selected) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 4.dp)
                                                .width(16.dp)
                                                .height(2.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF395B64))
                                        )
                                    }
                                }
                            },
                            selected = selected,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navigateToTab(items.indexOf(item))
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF395B64),
                                unselectedIconColor = Color(0xFF666666),
                                indicatorColor = Color.Transparent
                            ),
                            alwaysShowLabel = false
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        val unused = paddingValues // Use paddingValues to avoid unused variable warning
        // Main content area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            NavHost(
                navController = navController,
                startDestination = BottomNavItem.Home.route,
                modifier = Modifier.fillMaxSize()
            ) {
                // Home Screen (Index 0 - Leftmost)
                composable(
                    BottomNavItem.Home.route,
                    enterTransition = {
                        val initialRoute = initialState.destination.route
                        val targetIndex = 0
                        val initialIndex = items.indexOfFirst { it.route == initialRoute }

                        when {
                            initialIndex > targetIndex -> {
                                // Coming from right (Chat/Notifications/Settings to Home)
                                slideInHorizontally(
                                    initialOffsetX = { -it },
                                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                                ) + fadeIn(animationSpec = tween(500))
                            }
                            else -> {
                                // Initial load or other transitions
                                slideInHorizontally(
                                    initialOffsetX = { it },
                                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                                ) + fadeIn(animationSpec = tween(500))
                            }
                        }
                    },
                    exitTransition = {
                        val targetRoute = targetState.destination.route
                        val currentIndex = 0
                        val targetIndex = items.indexOfFirst { it.route == targetRoute }

                        when {
                            targetIndex > currentIndex -> {
                                // Going to right (Home to Chat/Notifications/Settings)
                                slideOutHorizontally(
                                    targetOffsetX = { -it },
                                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                                ) + fadeOut(animationSpec = tween(500))
                            }
                            else -> {
                                // Other transitions
                                slideOutHorizontally(
                                    targetOffsetX = { it },
                                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                                ) + fadeOut(animationSpec = tween(500))
                            }
                        }
                    }
                ) {
                    HomeScreen(
                        onProfileClick = { navController.navigate(BottomNavItem.Profile.route) },
                        onChatBotClick = { navController.navigate("chatbot") },
                        onCompletePersonaClick = { navController.navigate("settings/persona") },
                        onRecentChatClick = { chatId, otherUserId ->
                            navController.navigate("chat_detail/$chatId/$otherUserId")
                        },
                        onNotificationClick = {
                            // Navigate to notifications tab instead of using navigate()
                            navigateToTab(3) // Notifications is at index 3 (Home=0, Analytics=1, Chat=2, Notifications=3)
                        },
                        onSeeAllChatsClick = {
                            navigateToTab(2) // Chat is at index 2
                        }
                    )
                }

                // Analytics Screen (Index 1)
                composable(
                    BottomNavItem.Analytics.route,
                    enterTransition = {
                        val initialRoute = initialState.destination.route
                        val targetIndex = 1
                        val initialIndex = items.indexOfFirst { it.route == initialRoute }

                        when {
                            initialIndex < targetIndex -> {
                                slideInHorizontally(
                                    initialOffsetX = { it },
                                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                                ) + fadeIn(animationSpec = tween(500))
                            }
                            initialIndex > targetIndex -> {
                                slideInHorizontally(
                                    initialOffsetX = { -it },
                                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                                ) + fadeIn(animationSpec = tween(500))
                            }
                            else -> {
                                fadeIn(animationSpec = tween(500))
                            }
                        }
                    },
                    exitTransition = {
                        val targetRoute = targetState.destination.route
                        val currentIndex = 1
                        val targetIndex = items.indexOfFirst { it.route == targetRoute }

                        when {
                            targetIndex < currentIndex -> {
                                slideOutHorizontally(
                                    targetOffsetX = { it },
                                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                                ) + fadeOut(animationSpec = tween(500))
                            }
                            targetIndex > currentIndex -> {
                                slideOutHorizontally(
                                    targetOffsetX = { -it },
                                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                                ) + fadeOut(animationSpec = tween(500))
                            }
                            else -> {
                                fadeOut(animationSpec = tween(500))
                            }
                        }
                    }
                ) {
                    AnalyticsScreen()
                }

                // Chat Screen (Index 2)
                composable(
                    BottomNavItem.Chat.route,
                    enterTransition = {
                        val initialRoute = initialState.destination.route
                        val targetIndex = 2
                        val initialIndex = items.indexOfFirst { it.route == initialRoute }

                        when {
                            initialIndex < targetIndex -> {
                                // Coming from left (Home/Analytics to Chat)
                                slideInHorizontally(
                                    initialOffsetX = { it },
                                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                                ) + fadeIn(animationSpec = tween(500))
                            }
                            initialIndex > targetIndex -> {
                                // Coming from right (Notifications/Settings to Chat)
                                slideInHorizontally(
                                    initialOffsetX = { -it },
                                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                                ) + fadeIn(animationSpec = tween(500))
                            }
                            else -> {
                                fadeIn(animationSpec = tween(500))
                            }
                        }
                    },
                    exitTransition = {
                        val targetRoute = targetState.destination.route
                        val currentIndex = 2
                        val targetIndex = items.indexOfFirst { it.route == targetRoute }

                        when {
                            targetIndex < currentIndex -> {
                                // Going to left (Chat to Home)
                                slideOutHorizontally(
                                    targetOffsetX = { it },
                                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                                ) + fadeOut(animationSpec = tween(500))
                            }
                            targetIndex > currentIndex -> {
                                // Going to right (Chat to Notifications/Settings)
                                slideOutHorizontally(
                                    targetOffsetX = { -it },
                                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                                ) + fadeOut(animationSpec = tween(500))
                            }
                            else -> {
                                fadeOut(animationSpec = tween(500))
                            }
                        }
                    }
                ) {
                    ChatScreen(
                        onNavigateToChat = { chatId, otherUserId ->
                            navController.navigate("chat_detail/$chatId/$otherUserId")
                        },
                        onNavigateToChatBot = { navController.navigate("chatbot") }
                    )
                }

                // Notifications Screen (Index 3)
                composable(
                    BottomNavItem.Notifications.route,
                    enterTransition = {
                        val initialRoute = initialState.destination.route
                        val targetIndex = 3
                        val initialIndex = items.indexOfFirst { it.route == initialRoute }

                        when {
                            initialIndex < targetIndex -> {
                                // Coming from left (Home/Analytics/Chat to Notifications)
                                slideInHorizontally(
                                    initialOffsetX = { it },
                                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                                ) + fadeIn(animationSpec = tween(500))
                            }
                            initialIndex > targetIndex -> {
                                // Coming from right (Settings to Notifications)
                                slideInHorizontally(
                                    initialOffsetX = { -it },
                                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                                ) + fadeIn(animationSpec = tween(500))
                            }
                            else -> {
                                fadeIn(animationSpec = tween(500))
                            }
                        }
                    },
                    exitTransition = {
                        val targetRoute = targetState.destination.route
                        val currentIndex = 3
                        val targetIndex = items.indexOfFirst { it.route == targetRoute }

                        when {
                            targetIndex < currentIndex -> {
                                // Going to left (Notifications to Home/Analytics/Chat)
                                slideOutHorizontally(
                                    targetOffsetX = { it },
                                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                                ) + fadeOut(animationSpec = tween(500))
                            }
                            targetIndex > currentIndex -> {
                                // Going to right (Notifications to Settings)
                                slideOutHorizontally(
                                    targetOffsetX = { -it },
                                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                                ) + fadeOut(animationSpec = tween(500))
                            }
                            else -> {
                                fadeOut(animationSpec = tween(500))
                            }
                        }
                    }
                ) {
                    NotificationScreen(
                        onNavigateBack = {
                            navController.popBackStack()
                        },
                        onNavigateToConversation = { conversationId ->
                            navController.navigate("conversation_detail/$conversationId")
                        }
                    )
                }

                // Conversation Detail Screen (Full screen without bottom bar)
                composable(
                    route = "conversation_detail/{conversationId}",
                    arguments = listOf(
                        navArgument("conversationId") { type = NavType.StringType }
                    ),
                    enterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(300))
                    },
                    exitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(300))
                    },
                    popEnterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { -it },
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(300))
                    },
                    popExitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(300))
                    }
                ) { backStackEntry ->
                    val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
                    ConversationDetailScreen(
                        conversationId = conversationId,
                        onBackClick = {
                            navController.popBackStack()
                        }
                    )
                }

                // Settings Screen (Index 4 - Rightmost)
                composable(
                    BottomNavItem.Settings.route,
                    enterTransition = {
                        val initialRoute = initialState.destination.route
                        val targetIndex = 4
                        val initialIndex = items.indexOfFirst { it.route == initialRoute }

                        when {
                            initialIndex < targetIndex -> {
                                // Coming from left (Home/Analytics/Chat/Notifications to Settings)
                                slideInHorizontally(
                                    initialOffsetX = { it },
                                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                                ) + fadeIn(animationSpec = tween(500))
                            }
                            else -> {
                                // Other transitions
                                slideInHorizontally(
                                    initialOffsetX = { -it },
                                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                                ) + fadeIn(animationSpec = tween(500))
                            }
                        }
                    },
                    exitTransition = {
                        val targetRoute = targetState.destination.route
                        val currentIndex = 4
                        val targetIndex = items.indexOfFirst { it.route == targetRoute }

                        when {
                            targetIndex < currentIndex -> {
                                // Going to left (Settings to Home/Chat/Notifications)
                                slideOutHorizontally(
                                    targetOffsetX = { it },
                                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                                ) + fadeOut(animationSpec = tween(500))
                            }
                            else -> {
                                // Other transitions
                                slideOutHorizontally(
                                    targetOffsetX = { -it },
                                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                                ) + fadeOut(animationSpec = tween(500))
                            }
                        }
                    }
                ) {
                    SettingsScreen(
                        onLogout = onLogout,
                        onProfileClicked = {
                            navController.navigate(BottomNavItem.Profile.route)
                        },
                        onPersonaClicked = {
                            navController.navigate("settings/persona")
                        },
                    )
                }

                // Non-main screens with standard transitions
                composable(
                    route = "chat_detail/{chatId}/{otherUserId}",
                    arguments = listOf(
                        navArgument("chatId") { type = NavType.StringType },
                        navArgument("otherUserId") { type = NavType.StringType }
                    ),
                    enterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = tween(400, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(400))
                    },
                    exitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { -it },
                            animationSpec = tween(400, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(400))
                    },
                    popEnterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { -it },
                            animationSpec = tween(400, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(400))
                    },
                    popExitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(400, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(400))
                    },
                ) { backStackEntry ->
                    val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
                    val otherUserId = backStackEntry.arguments?.getString("otherUserId") ?: ""

                    // Wrap ChatDetailScreen in a container that handles window insets properly
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets(0, 0, 0, 0)) // Reset window insets
                    ) {
                        ChatDetailScreen(
                            chatId = chatId,
                            otherUserId = otherUserId,
                            onNavigateBack = {
                                navController.popBackStack()
                            }
                        )
                    }
                }

                // Other screens with similar smooth transitions
                composable(BottomNavItem.Profile.route) {
                    ProfileScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onLogout = onLogout
                    )
                }

                composable("settings/persona") {
                    PersonaScreen(
                        onNavigateBack = {
                            navController.popBackStack()
                        }
                    )
                }

                composable("chatbot") {
                    ChatBotScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToPersona = { navController.navigate("settings/persona") }
                    )
                }
            }
        }
    }
}

// Custom BackHandler composable for better UX
@Composable
fun BackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit
) {
    val currentOnBack by rememberUpdatedState(onBack)
    val backCallback = remember {
        object : OnBackPressedCallback(enabled) {
            override fun handleOnBackPressed() {
                currentOnBack()
            }
        }
    }

    SideEffect {
        backCallback.isEnabled = enabled
    }

    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    DisposableEffect(backDispatcher, backCallback) {
        backDispatcher?.addCallback(backCallback)
        onDispose {
            backCallback.remove()
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun DashboardScreenPreview() {
    DashboardScreen(onLogout = {})
}
