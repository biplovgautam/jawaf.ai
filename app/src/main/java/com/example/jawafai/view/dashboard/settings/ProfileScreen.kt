package com.example.jawafai.view.dashboard.settings

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.jawafai.repository.UserRepositoryImpl
import com.example.jawafai.ui.theme.AppFonts
import com.example.jawafai.viewmodel.UserViewModel
import com.example.jawafai.viewmodel.UserViewModelFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { UserRepositoryImpl(FirebaseAuth.getInstance(), FirebaseFirestore.getInstance()) }
    val auth = remember { FirebaseAuth.getInstance() }
    val viewModel: UserViewModel = viewModel(factory = UserViewModelFactory(repository, auth))

    val userProfile by viewModel.userProfile.observeAsState()
    val userState by viewModel.userState.observeAsState(UserViewModel.UserOperationResult.Initial)

    var isEditing by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isUploadingImage by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf(0f) }

    // Form state
    var firstName by remember { mutableStateOf(TextFieldValue("")) }
    var lastName by remember { mutableStateOf(TextFieldValue("")) }
    var username by remember { mutableStateOf(TextFieldValue("")) }
    var bio by remember { mutableStateOf(TextFieldValue("")) }
    var imageUrl by remember { mutableStateOf("") }
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    // Image picker launcher with improved error handling
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { selectedUri ->
            isUploadingImage = true
            tempImageUri = selectedUri

            viewModel.uploadProfileImage(selectedUri) { url ->
                isUploadingImage = false
                if (url != null) {
                    imageUrl = url
                    tempImageUri = null
                } else {
                    // Show error message if upload fails
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Image upload failed. Please try again.",
                            actionLabel = "Retry"
                        )
                    }
                    tempImageUri = null
                }
            }
        }
    }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            galleryLauncher.launch("image/*")
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = "Permission required to select images",
                    actionLabel = "OK"
                )
            }
        }
    }

    // Fetch user profile on first composition
    LaunchedEffect(Unit) {
        viewModel.fetchUserProfile()
    }

    // Update local state when userProfile changes
    LaunchedEffect(userProfile) {
        userProfile?.let { profile ->
            firstName = TextFieldValue(profile.firstName)
            lastName = TextFieldValue(profile.lastName)
            username = TextFieldValue(profile.username)
            bio = TextFieldValue(profile.bio)
            imageUrl = profile.imageUrl ?: ""
        }
    }

    // Handle state changes from ViewModel
    LaunchedEffect(userState) {
        when (val result = userState) {
            is UserViewModel.UserOperationResult.Success -> {
                if (result.message.isNotEmpty()) {
                    snackbarHostState.showSnackbar(result.message)
                }
                if (isEditing) {
                    isEditing = false
                    focusManager.clearFocus()
                }
            }
            is UserViewModel.UserOperationResult.Error -> {
                snackbarHostState.showSnackbar(result.message)
            }
            else -> {}
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color(0xFFF8F9FA),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = { snackbarData ->
                    Snackbar(
                        snackbarData = snackbarData,
                        containerColor = Color(0xFF395B64),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Profile",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = Color(0xFF395B64)
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF395B64)
                        )
                    }
                },
                actions = {
                    AnimatedVisibility(
                        visible = !isUploadingImage && userState !is UserViewModel.UserOperationResult.Loading
                    ) {
                        TextButton(
                            onClick = {
                                if (isEditing) {
                                    userProfile?.let {
                                        val updatedUser = it.copy(
                                            firstName = firstName.text,
                                            lastName = lastName.text,
                                            username = username.text,
                                            bio = bio.text,
                                            imageUrl = imageUrl
                                        )
                                        viewModel.updateUser(updatedUser)
                                    }
                                }
                                isEditing = !isEditing
                            }
                        ) {
                            Text(
                                text = if (isEditing) "Save" else "Edit",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = AppFonts.KarlaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color(0xFF395B64)
                                )
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF8F9FA)
                ),
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .background(Color(0xFFF8F9FA))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))

                // Enhanced Profile Image Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isEditing && !isUploadingImage) {
                            permissionLauncher.launch(permission)
                        },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                // Profile Image
                                AsyncImage(
                                    model = tempImageUri ?: imageUrl.ifEmpty {
                                        "https://ui-avatars.com/api/?name=${firstName.text}+${lastName.text}&background=A5C9CA&color=ffffff&size=300"
                                    },
                                    contentDescription = "Profile Picture",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(140.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFA5C9CA))
                                )

                                // Edit button overlay
                                if (isEditing) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF395B64))
                                            .clickable(enabled = !isUploadingImage) {
                                                permissionLauncher.launch(permission)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isUploadingImage) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit Image",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Upload progress overlay
                            if (isUploadingImage) {
                                Box(
                                    modifier = Modifier
                                        .size(140.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = "Uploading...",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = Color.White,
                                                fontFamily = AppFonts.KarlaFontFamily,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // User name display
                        Text(
                            text = "${firstName.text} ${lastName.text}".trim().ifEmpty { username.text },
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = AppFonts.KarlaFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = Color(0xFF395B64)
                            ),
                            textAlign = TextAlign.Center
                        )

                        if (isEditing) {
                            Text(
                                text = "Tap image to change",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = AppFonts.KaiseiDecolFontFamily,
                                    color = Color(0xFF666666),
                                    fontSize = 12.sp
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Enhanced User Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color(0xFF395B64),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Personal Information",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = AppFonts.KarlaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Color(0xFF395B64)
                                )
                            )
                        }

                        ProfileTextField(
                            label = "First Name",
                            value = firstName,
                            onValueChange = { firstName = it },
                            enabled = isEditing,
                            icon = Icons.Default.Person
                        )

                        ProfileTextField(
                            label = "Last Name",
                            value = lastName,
                            onValueChange = { lastName = it },
                            enabled = isEditing,
                            icon = Icons.Default.Person
                        )

                        ProfileTextField(
                            label = "Username",
                            value = username,
                            onValueChange = { username = it },
                            enabled = isEditing,
                            icon = Icons.Default.AccountCircle
                        )

                        ProfileTextField(
                            label = "Bio",
                            value = bio,
                            onValueChange = { bio = it },
                            enabled = isEditing,
                            singleLine = false,
                            minLines = 3,
                            icon = Icons.Default.Info
                        )

                        userProfile?.let { profile ->
                            OutlinedTextField(
                                value = profile.email,
                                onValueChange = {},
                                label = {
                                    Text(
                                        "Email",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                                            color = Color(0xFF666666)
                                        )
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = null,
                                        tint = Color(0xFF666666)
                                    )
                                },
                                enabled = false,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledBorderColor = Color(0xFFE0E0E0),
                                    disabledTextColor = Color(0xFF666666),
                                    disabledLabelColor = Color(0xFF666666)
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Enhanced Account Actions Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = Color(0xFF395B64),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Account Actions",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = AppFonts.KarlaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Color(0xFF395B64)
                                )
                            )
                        }

                        Button(
                            onClick = onLogout,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF395B64)
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExitToApp,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Logout",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = AppFonts.KarlaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                ),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        OutlinedButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Delete Account",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = AppFonts.KarlaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                ),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                // Add bottom padding for navigation bar
                Spacer(modifier = Modifier.navigationBarsPadding())
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Enhanced Loading overlay
            if (userState is UserViewModel.UserOperationResult.Loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF395B64),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Please wait...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = AppFonts.KarlaFontFamily,
                                    color = Color(0xFF666666)
                                )
                            )
                        }
                    }
                }
            }

            if (showDeleteDialog) {
                DeleteAccountDialog(
                    onDismiss = { showDeleteDialog = false },
                    onConfirm = {
                        viewModel.deleteUser(userProfile?.id ?: "")
                        showDeleteDialog = false
                        onLogout()
                    }
                )
            }
        }
    }
}

@Composable
private fun ProfileTextField(
    label: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    enabled: Boolean,
    singleLine: Boolean = true,
    minLines: Int = 1,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = AppFonts.KaiseiDecolFontFamily,
                    color = if (enabled) Color(0xFF395B64) else Color(0xFF666666)
                )
            )
        },
        leadingIcon = icon?.let { iconVector ->
            {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = if (enabled) Color(0xFF395B64) else Color(0xFF666666)
                )
            }
        },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        singleLine = singleLine,
        minLines = minLines,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF395B64),
            unfocusedBorderColor = Color(0xFFE0E0E0),
            disabledBorderColor = Color(0xFFE0E0E0),
            focusedTextColor = Color(0xFF395B64),
            unfocusedTextColor = Color(0xFF395B64),
            disabledTextColor = Color(0xFF666666),
            focusedLabelColor = Color(0xFF395B64),
            unfocusedLabelColor = Color(0xFF666666),
            disabledLabelColor = Color(0xFF666666),
            cursorColor = Color(0xFF395B64)
        ),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = AppFonts.KaiseiDecolFontFamily,
            fontSize = 16.sp
        )
    )
}

@Composable
private fun DeleteAccountDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Delete Account",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF395B64)
                    )
                )
            }
        },
        text = {
            Text(
                text = "Are you sure you want to delete your account? This action is permanent and cannot be undone. All your data will be lost.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = AppFonts.KaiseiDecolFontFamily,
                    color = Color(0xFF666666)
                )
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(8.dp)
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
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFF666666)
                )
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
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.White
    )
}
