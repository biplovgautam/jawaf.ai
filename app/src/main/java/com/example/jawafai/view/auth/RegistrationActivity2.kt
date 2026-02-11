package com.example.jawafai.view.auth

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jawafai.R
import com.example.jawafai.model.UserModel
import com.example.jawafai.repository.UserRepositoryImpl
import com.example.jawafai.ui.theme.JawafaiTheme
import com.example.jawafai.ui.theme.AppFonts
import com.example.jawafai.viewmodel.UserViewModel
import com.example.jawafai.viewmodel.UserViewModelFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.airbnb.lottie.compose.*
import java.util.*
import java.text.SimpleDateFormat
import kotlinx.coroutines.delay

class RegistrationActivity : ComponentActivity() {
    private lateinit var viewModel: UserViewModel
    private val TAG = "RegistrationActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Activity created")

        // Enable full screen immersive mode to match LoginActivity
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Initialize Firebase components
        val auth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance()
        val repository = UserRepositoryImpl(auth, firestore)

        // Initialize ViewModel
        viewModel = UserViewModelFactory(repository, auth).create(UserViewModel::class.java)
        Log.d(TAG, "onCreate: ViewModel initialized")

        setContent {
            JawafaiTheme {
                RegistrationScreen(
                    viewModel = viewModel,
                    onSuccessfulRegistration = {
                        Log.d(TAG, "onSuccessfulRegistration: Navigating to login screen")
                        val intent = Intent(this, LoginActivity::class.java)
                        startActivity(intent)
                        finish() // Close registration activity
                    },
                    onNavigateToLogin = {
                        val intent = Intent(this, LoginActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun RegistrationScreen(
    viewModel: UserViewModel = viewModel(),
    onSuccessfulRegistration: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {}
) {
    val context = LocalContext.current

    // State variables for form fields
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var dob by remember { mutableStateOf("") }
    var acceptTerms by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    // Username validation states
    var isUsernameValid by remember { mutableStateOf(true) }
    var isEmailValid by remember { mutableStateOf(true) }
    var showTermsError by remember { mutableStateOf(false) }

    // Focus requesters for smooth keyboard navigation
    val lastNameFocusRequester = remember { FocusRequester() }
    val usernameFocusRequester = remember { FocusRequester() }
    val emailFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }

    // Lottie animation composition - matching LoginActivity
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.live_chatbot))
    val progress by animateLottieCompositionAsState(
        composition,
        iterations = LottieConstants.IterateForever
    )

    // Observe ViewModel state
    val userOperationState = viewModel.userState.observeAsState(initial = UserViewModel.UserOperationResult.Initial)

    // Handle registration state changes
    LaunchedEffect(userOperationState.value) {
        when (val state = userOperationState.value) {
            is UserViewModel.UserOperationResult.Success -> {
                isLoading = false
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                delay(1500)
                onSuccessfulRegistration()
            }
            is UserViewModel.UserOperationResult.Error -> {
                isLoading = false
                val errorMessage = state.message

                // Handle specific error messages
                when {
                    errorMessage.contains("username", ignoreCase = true) -> {
                        isUsernameValid = false
                        Toast.makeText(context, "Username already exists", Toast.LENGTH_LONG).show()
                        usernameFocusRequester.requestFocus()
                    }
                    errorMessage.contains("email", ignoreCase = true) || errorMessage.contains("already in use", ignoreCase = true) -> {
                        isEmailValid = false
                        Toast.makeText(context, "Email already in use", Toast.LENGTH_LONG).show()
                        emailFocusRequester.requestFocus()
                    }
                    else -> {
                        Toast.makeText(context, "Error: ${state.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            is UserViewModel.UserOperationResult.Loading -> {
                isLoading = true
            }
            else -> {}
        }
    }

    // Date picker setup
    val calendar = remember { Calendar.getInstance() }
    calendar.add(Calendar.YEAR, -18) // Default to 18 years ago
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    val year = remember { mutableStateOf(calendar.get(Calendar.YEAR)) }
    val month = remember { mutableStateOf(calendar.get(Calendar.MONTH)) }
    val day = remember { mutableStateOf(calendar.get(Calendar.DAY_OF_MONTH)) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Date picker dialog
    if (showDatePicker) {
        DatePickerDialog(
            context,
            { _, selectedYear, selectedMonth, selectedDay ->
                year.value = selectedYear
                month.value = selectedMonth
                day.value = selectedDay

                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(selectedYear, selectedMonth, selectedDay)
                dob = dateFormatter.format(selectedCalendar.time)
                showDatePicker = false
            },
            year.value,
            month.value,
            day.value
        ).apply {
            // Set date constraints
            val minAgeCalendar = Calendar.getInstance()
            minAgeCalendar.add(Calendar.YEAR, -13)
            datePicker.maxDate = minAgeCalendar.timeInMillis

            val maxAgeCalendar = Calendar.getInstance()
            maxAgeCalendar.add(Calendar.YEAR, -100)
            datePicker.minDate = maxAgeCalendar.timeInMillis
            show()
        }
    }

    // Function to handle registration
    fun handleRegistration() {
        // Reset previous errors
        showTermsError = false

        // Check terms and conditions first
        if (!acceptTerms) {
            showTermsError = true
            Toast.makeText(context, "You must accept the Terms & Conditions to register", Toast.LENGTH_LONG).show()
            return
        }

        // Field validation
        when {
            firstName.isBlank() -> {
                Toast.makeText(context, "First name is required", Toast.LENGTH_SHORT).show()
                return
            }
            lastName.isBlank() -> {
                Toast.makeText(context, "Last name is required", Toast.LENGTH_SHORT).show()
                lastNameFocusRequester.requestFocus()
                return
            }
            username.isBlank() -> {
                Toast.makeText(context, "Username is required", Toast.LENGTH_SHORT).show()
                usernameFocusRequester.requestFocus()
                return
            }
            email.isBlank() -> {
                Toast.makeText(context, "Email is required", Toast.LENGTH_SHORT).show()
                emailFocusRequester.requestFocus()
                return
            }
            !email.contains('@') -> {
                Toast.makeText(context, "Enter a valid email address", Toast.LENGTH_SHORT).show()
                emailFocusRequester.requestFocus()
                return
            }
            password.length < 6 -> {
                Toast.makeText(context, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                passwordFocusRequester.requestFocus()
                return
            }
            dob.isBlank() -> {
                Toast.makeText(context, "Date of birth is required", Toast.LENGTH_SHORT).show()
                return
            }
            else -> {
                val userModel = UserModel(
                    firstName = firstName,
                    lastName = lastName,
                    username = username,
                    email = email,
                    dateOfBirth = dob
                )
                viewModel.register(email, password, userModel, null)
            }
        }
    }

    // Main UI matching LoginActivity style
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.White
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .statusBarsPadding()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp)
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Lottie Animation
                item {
                    LottieAnimation(
                        composition = composition,
                        progress = { progress },
                        modifier = Modifier.size(120.dp) // Smaller for registration
                    )
                }

                // App Title - Using Kadwa font
                item {
                    Text(
                        text = "जवाफ.AI",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = AppFonts.KadwaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp, // Slightly smaller for registration
                            color = Color(0xFF395B64)
                        ),
                        textAlign = TextAlign.Center
                    )
                }

                // Subtitle
                item {
                    Text(
                        text = if (isLoading) "Creating your account..." else "Create your account to get started",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontSize = 16.sp,
                            color = if (isLoading) Color(0xFF395B64) else Color(0xFF666666)
                        ),
                        textAlign = TextAlign.Center
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // First Name TextField
                item {
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        label = {
                            Text(
                                "First Name",
                                fontFamily = AppFonts.KarlaFontFamily,
                                color = Color(0xFF666666)
                            )
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = AppFonts.KarlaFontFamily,
                            color = Color.Black,
                            fontSize = 16.sp
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { lastNameFocusRequester.requestFocus() }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF395B64),
                            unfocusedBorderColor = Color(0xFFE0E0E0),
                            focusedLabelColor = Color(0xFF395B64),
                            unfocusedLabelColor = Color(0xFF666666),
                            cursorColor = Color(0xFF395B64),
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        ),
                        enabled = !isLoading,
                        singleLine = true
                    )
                }

                // Last Name TextField
                item {
                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        label = {
                            Text(
                                "Last Name",
                                fontFamily = AppFonts.KarlaFontFamily,
                                color = Color(0xFF666666)
                            )
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = AppFonts.KarlaFontFamily,
                            color = Color.Black,
                            fontSize = 16.sp
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { usernameFocusRequester.requestFocus() }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .focusRequester(lastNameFocusRequester),
                        shape = RoundedCornerShape(28.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF395B64),
                            unfocusedBorderColor = Color(0xFFE0E0E0),
                            focusedLabelColor = Color(0xFF395B64),
                            unfocusedLabelColor = Color(0xFF666666),
                            cursorColor = Color(0xFF395B64),
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        ),
                        enabled = !isLoading,
                        singleLine = true
                    )
                }

                // Username TextField
                item {
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            isUsernameValid = true
                        },
                        label = {
                            Text(
                                "Username",
                                fontFamily = AppFonts.KarlaFontFamily,
                                color = Color(0xFF666666)
                            )
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = AppFonts.KarlaFontFamily,
                            color = Color.Black,
                            fontSize = 16.sp
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { emailFocusRequester.requestFocus() }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .focusRequester(usernameFocusRequester),
                        shape = RoundedCornerShape(28.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (isUsernameValid) Color(0xFF395B64) else MaterialTheme.colorScheme.error,
                            unfocusedBorderColor = if (isUsernameValid) Color(0xFFE0E0E0) else MaterialTheme.colorScheme.error,
                            focusedLabelColor = Color(0xFF395B64),
                            unfocusedLabelColor = Color(0xFF666666),
                            cursorColor = Color(0xFF395B64),
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        ),
                        enabled = !isLoading,
                        isError = !isUsernameValid,
                        singleLine = true
                    )
                }

                // Email TextField
                item {
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            isEmailValid = true
                        },
                        label = {
                            Text(
                                "Email",
                                fontFamily = AppFonts.KarlaFontFamily,
                                color = Color(0xFF666666)
                            )
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = AppFonts.KarlaFontFamily,
                            color = Color.Black,
                            fontSize = 16.sp
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { passwordFocusRequester.requestFocus() }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .focusRequester(emailFocusRequester),
                        shape = RoundedCornerShape(28.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (isEmailValid) Color(0xFF395B64) else MaterialTheme.colorScheme.error,
                            unfocusedBorderColor = if (isEmailValid) Color(0xFFE0E0E0) else MaterialTheme.colorScheme.error,
                            focusedLabelColor = Color(0xFF395B64),
                            unfocusedLabelColor = Color(0xFF666666),
                            cursorColor = Color(0xFF395B64),
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        ),
                        enabled = !isLoading,
                        isError = !isEmailValid,
                        singleLine = true
                    )
                }

                // Password TextField
                item {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = {
                            Text(
                                "Password",
                                fontFamily = AppFonts.KarlaFontFamily,
                                color = Color(0xFF666666)
                            )
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = AppFonts.KarlaFontFamily,
                            color = Color.Black,
                            fontSize = 16.sp
                        ),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                handleRegistration()
                            }
                        ),
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            val description = if (passwordVisible) "Hide password" else "Show password"

                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                enabled = !isLoading
                            ) {
                                Icon(
                                    imageVector = image,
                                    contentDescription = description,
                                    tint = Color(0xFF395B64)
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .focusRequester(passwordFocusRequester),
                        shape = RoundedCornerShape(28.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF395B64),
                            unfocusedBorderColor = Color(0xFFE0E0E0),
                            focusedLabelColor = Color(0xFF395B64),
                            unfocusedLabelColor = Color(0xFF666666),
                            cursorColor = Color(0xFF395B64),
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        ),
                        enabled = !isLoading,
                        singleLine = true
                    )
                }

                // Date of Birth TextField
                item {
                    OutlinedTextField(
                        value = dob,
                        onValueChange = { /* Read only */ },
                        label = {
                            Text(
                                "Date of Birth",
                                fontFamily = AppFonts.KarlaFontFamily,
                                color = Color(0xFF666666)
                            )
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = AppFonts.KarlaFontFamily,
                            color = Color.Black,
                            fontSize = 16.sp
                        ),
                        placeholder = {
                            Text(
                                "Select your birth date",
                                fontFamily = AppFonts.KarlaFontFamily,
                                color = Color(0xFF999999)
                            )
                        },
                        trailingIcon = {
                            Icon(
                                painter = painterResource(id = android.R.drawable.ic_menu_my_calendar),
                                contentDescription = "Select date",
                                tint = Color(0xFF395B64),
                                modifier = Modifier.clickable(enabled = !isLoading) {
                                    if (!isLoading) showDatePicker = true
                                }
                            )
                        },
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clickable(enabled = !isLoading) {
                                if (!isLoading) showDatePicker = true
                            },
                        shape = RoundedCornerShape(28.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF395B64),
                            unfocusedBorderColor = Color(0xFFE0E0E0),
                            focusedLabelColor = Color(0xFF395B64),
                            unfocusedLabelColor = Color(0xFF666666),
                            cursorColor = Color(0xFF395B64),
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        ),
                        enabled = !isLoading
                    )
                }

                // Terms and Conditions Checkbox
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = acceptTerms,
                            onCheckedChange = {
                                if (!isLoading) {
                                    acceptTerms = it
                                    showTermsError = false
                                }
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF395B64),
                                uncheckedColor = if (showTermsError) MaterialTheme.colorScheme.error else Color(0xFF666666)
                            ),
                            enabled = !isLoading
                        )
                        Text(
                            "I accept the Terms & Conditions",
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontSize = 14.sp,
                            color = if (showTermsError) MaterialTheme.colorScheme.error else Color(0xFF666666)
                        )
                    }
                }

                // Register Button
                item {
                    Button(
                        onClick = { handleRegistration() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF395B64)
                        ),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Creating Account...",
                                    fontFamily = AppFonts.KarlaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                            }
                        } else {
                            Text(
                                text = "Create Account",
                                fontFamily = AppFonts.KarlaFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Already have account text
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Already have an account? ",
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontSize = 14.sp,
                            color = Color(0xFFA5C9CA)
                        )
                        Text(
                            text = "Sign In",
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF395B64),
                            modifier = Modifier.clickable(enabled = !isLoading) {
                                if (!isLoading) {
                                    onNavigateToLogin()
                                }
                            }
                        )
                    }
                }

                // Add bottom padding for navigation bar
                item {
                    Spacer(modifier = Modifier.navigationBarsPadding())
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun RegistrationScreenPreview() {
    JawafaiTheme {
        RegistrationScreen()
    }
}
