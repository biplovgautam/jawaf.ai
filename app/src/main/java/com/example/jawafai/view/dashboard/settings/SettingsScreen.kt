package com.example.jawafai.view.dashboard.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import android.provider.Settings
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.jawafai.model.UserModel
import com.example.jawafai.repository.UserRepositoryImpl
import com.example.jawafai.ui.theme.AppFonts
import com.example.jawafai.viewmodel.UserViewModel
import com.example.jawafai.viewmodel.UserViewModelFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.runtime.livedata.observeAsState
import kotlinx.coroutines.tasks.await
import com.airbnb.lottie.compose.*
import com.example.jawafai.R
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import android.widget.Toast
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

data class SettingsItemData(
    val icon: ImageVector,
    val title: String,
    val subtitle: String? = null,
    val onClick: () -> Unit,
    val tint: Color = Color.Unspecified
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    onProfileClicked: () -> Unit,
    onPersonaClicked: () -> Unit,
    viewModel: UserViewModel = viewModel(
        factory = UserViewModelFactory(
            UserRepositoryImpl(FirebaseAuth.getInstance(), FirebaseFirestore.getInstance()),
            FirebaseAuth.getInstance()
        )
    )
) {
    val userProfile by viewModel.userProfile.observeAsState()
    val personaCompleted = remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Fetch user profile when the screen is first composed
    LaunchedEffect(Unit) {
        viewModel.fetchUserProfile()

        // Check if persona is completed with new questions
        try {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            if (currentUserId != null) {
                val personaRef = FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(currentUserId)
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
                personaCompleted.value = validAnswers.size >= 8
            }
        } catch (e: Exception) {
            personaCompleted.value = false
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
                        text = "Settings",
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
        }
    ) { paddingValues ->
        SettingsContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .background(Color.White),
            onLogout = onLogout,
            onProfileClicked = onProfileClicked,
            onPersonaClicked = onPersonaClicked,
            userModel = userProfile,
            personaCompleted = personaCompleted.value
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SettingsContent(
    modifier: Modifier = Modifier,
    onLogout: () -> Unit,
    onProfileClicked: () -> Unit,
    onPersonaClicked: () -> Unit,
    userModel: UserModel?,
    personaCompleted: Boolean
) {
    val userEmail = userModel?.email ?: "User"
    val userName = userModel?.let {
        "${it.firstName} ${it.lastName}".trim().ifEmpty { it.username }
    } ?: userEmail.substringBefore("@")
    val profileImage = userModel?.imageUrl

    // State for expandable about section
    var isAboutExpanded by remember { mutableStateOf(false) }

    // State for expandable integrated platforms section
    var isPlatformsExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Profile Section
        item {
            UserProfileCard(
                name = userName,
                email = userEmail,
                profileImageUrl = profileImage ?: "",
                onClick = onProfileClicked
            )
        }

        // Persona Section
        item {
            Text(
                text = "Personalization",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = AppFonts.KarlaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF395B64)
                )
            )
        }

        item {
            PersonaCard(
                onClick = onPersonaClicked,
                completed = personaCompleted
            )
        }

        // Integrated Platforms Section
        item {
            Text(
                text = "Integrated Platforms",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = AppFonts.KarlaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF395B64)
                )
            )
        }

        item {
            IntegratedPlatformsCard(
                isExpanded = isPlatformsExpanded,
                onClick = { isPlatformsExpanded = !isPlatformsExpanded }
            )
        }

        // About Section
        item {
            Text(
                text = "App Information",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = AppFonts.KarlaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF395B64)
                )
            )
        }

        item {
            AboutCard(
                isExpanded = isAboutExpanded,
                onClick = { isAboutExpanded = !isAboutExpanded }
            )
        }

        // Account Actions
        item {
            Text(
                text = "Account",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = AppFonts.KarlaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF395B64)
                )
            )
        }

        item {
            LogoutCard(onLogout = onLogout)
        }

        // Add bottom padding for navigation bar
        item {
            Spacer(modifier = Modifier.navigationBarsPadding())
            Spacer(modifier = Modifier.height(80.dp)) // Extra space for bottom navigation
        }
    }
}

@Composable
fun UserProfileCard(
    name: String,
    email: String,
    profileImageUrl: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile image
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFA5C9CA))
            ) {
                Image(
                    painter = rememberAsyncImagePainter(
                        model = profileImageUrl.ifEmpty { "https://ui-avatars.com/api/?name=$name&background=A5C9CA&color=ffffff" }
                    ),
                    contentDescription = "Profile picture",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // User info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color(0xFF395B64)
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = AppFonts.KaiseiDecolFontFamily,
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Edit Profile",
                tint = Color(0xFF395B64),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun PersonaCard(
    onClick: () -> Unit,
    completed: Boolean
) {
    // Lottie animation for persona
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.persona))
    val progress by animateLottieCompositionAsState(
        composition,
        iterations = LottieConstants.IterateForever
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF8F9FA)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Lottie Animation instead of static icon
            Box {
                composition?.let {
                    LottieAnimation(
                        composition = it,
                        progress = { progress },
                        modifier = Modifier.size(56.dp)
                    )
                }

                // Tick mark overlay when completed
                if (completed) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF395B64)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Completed",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Your Persona",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF395B64)
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (completed) "Persona completed" else "Complete your persona for better responses",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = AppFonts.KaiseiDecolFontFamily,
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Navigate",
                tint = Color(0xFF395B64),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun SettingsCard(
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        content()
    }
}

@Composable
fun LogoutCard(onLogout: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onLogout() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5F5)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = "Logout",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = AppFonts.KarlaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.error
                )
            )
        }
    }
}

@Composable
fun SettingsItem(item: SettingsItemData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = item.onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = if (item.tint != Color.Unspecified) item.tint else Color(0xFF395B64),
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Text content
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = AppFonts.KarlaFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = if (item.tint != Color.Unspecified) item.tint else Color(0xFF395B64)
                )
            )

            if (item.subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = AppFonts.KaiseiDecolFontFamily,
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Navigate",
            tint = Color(0xFF666666),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun AboutCard(
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "About This App",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF395B64)
                    ),
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = Color(0xFF395B64),
                    modifier = Modifier.size(24.dp)
                )
            }

            // Expanded content
            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Version: 1.0.0",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = AppFonts.KaiseiDecolFontFamily,
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "This app is designed to provide an intuitive and user-friendly experience. We value your feedback and suggestions.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = AppFonts.KaiseiDecolFontFamily,
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "For more information, visit our website or contact support.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = AppFonts.KaiseiDecolFontFamily,
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                )
            }
        }
    }
}

@Composable
fun IntegratedPlatformsCard(
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF395B64)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Link,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Connected Apps",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF395B64)
                        )
                    )
                    Text(
                        text = "Manage your connected platforms",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                            fontSize = 12.sp,
                            color = Color(0xFF666666)
                        )
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = Color(0xFF395B64),
                    modifier = Modifier.size(24.dp)
                )
            }

            // Expanded content - Platform items
            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(
                    color = Color(0xFFE0E0E0),
                    thickness = 1.dp
                )

                // WhatsApp with Lottie
                PlatformItemWithLottie(
                    animationRes = R.raw.whatsapp,
                    name = "WhatsApp",
                    description = "Connect your WhatsApp"
                )

                HorizontalDivider(
                    color = Color(0xFFE0E0E0),
                    thickness = 1.dp,
                    modifier = Modifier.padding(start = 56.dp)
                )

                // Instagram with Lottie
                PlatformItemWithLottie(
                    animationRes = R.raw.insta,
                    name = "Instagram",
                    description = "Connect your Instagram DMs"
                )

                HorizontalDivider(
                    color = Color(0xFFE0E0E0),
                    thickness = 1.dp,
                    modifier = Modifier.padding(start = 56.dp)
                )

                // Messenger with Lottie
                PlatformItemWithLottie(
                    animationRes = R.raw.messenger,
                    name = "Messenger",
                    description = "Connect Facebook Messenger"
                )
            }
        }
    }
}

@Composable
fun PlatformItemWithLottie(
    animationRes: Int,
    name: String,
    description: String
) {
    // Lottie animation
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(animationRes))
    val progress by animateLottieCompositionAsState(
        composition,
        iterations = LottieConstants.IterateForever
    )

    // Switch state
    var isEnabled by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Lottie Animation for platform icon
        composition?.let {
            LottieAnimation(
                composition = it,
                progress = { progress },
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Platform info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = AppFonts.KarlaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF395B64)
                )
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = AppFonts.KaiseiDecolFontFamily,
                    fontSize = 12.sp,
                    color = Color(0xFF666666)
                )
            )
        }

        // Switch toggle
        Switch(
            checked = isEnabled,
            onCheckedChange = { isEnabled = it },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF395B64),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFE0E0E0),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
fun PlatformItem(
    icon: ImageVector,
    name: String,
    description: String,
    color: Color,
    isConnected: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* Handle platform connection */ }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Platform icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Platform info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = AppFonts.KarlaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF395B64)
                )
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = AppFonts.KaiseiDecolFontFamily,
                    fontSize = 12.sp,
                    color = Color(0xFF666666)
                )
            )
        }

        // Connection status
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isConnected) Color(0xFF395B64) else Color(0xFFE0E0E0)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (isConnected) "Connected" else "Connect",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = AppFonts.KarlaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (isConnected) Color.White else Color(0xFF666666)
                )
            )
        }
    }
}

