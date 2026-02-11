package com.example.jawafai.view.dashboard.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jawafai.ui.theme.AppFonts
import com.example.jawafai.model.PersonaQuestions
import com.example.jawafai.model.PersonaQuestion
import com.example.jawafai.model.QuestionType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Composable screen for the Persona Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaScreen(
    onNavigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // States for persona data
    val isLoading = remember { mutableStateOf(true) }
    val personaData = remember { mutableStateOf<Map<String, String>>(mapOf()) }
    val currentAnswers = remember { mutableStateOf<Map<String, String>>(mapOf()) }

    // Use the updated questions from PersonaQuestion.kt
    val questions = remember { PersonaQuestions.questions }

    // Load persona data on first composition
    LaunchedEffect(Unit) {
        try {
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            if (userId != null) {
                val personaRef = FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId)
                    .collection("persona")

                // Check if old persona format exists and clear it
                val snapshot = personaRef.get().await()
                val hasOldQuestions = snapshot.documents.any { doc ->
                    !questions.any { question -> question.id == doc.id }
                }

                if (hasOldQuestions) {
                    // Clear old persona data
                    for (doc in snapshot.documents) {
                        personaRef.document(doc.id).delete().await()
                    }

                    // Also reset personaCompleted flag
                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(userId)
                        .update("personaCompleted", false)
                        .await()

                    // Show message about reset
                    snackbarHostState.showSnackbar("Persona questions have been updated. Please complete the new questions.")
                } else {
                    // Load existing answers for new questions
                    val data = mutableMapOf<String, String>()
                    for (doc in snapshot.documents) {
                        doc.id.let { questionId ->
                            doc.getString("answer")?.let { answer ->
                                data[questionId] = answer
                            }
                        }
                    }
                    personaData.value = data
                    currentAnswers.value = data.toMutableMap()
                }
            }
        } catch (e: Exception) {
            // Handle error
            snackbarHostState.showSnackbar("Failed to load persona data: ${e.message}")
        } finally {
            isLoading.value = false
        }
    }

    // Save function
    val savePersona = {
        coroutineScope.launch {
            try {
                isLoading.value = true
                val userId = FirebaseAuth.getInstance().currentUser?.uid

                if (userId != null) {
                    val personaRef = FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(userId)
                        .collection("persona")

                    // Delete existing answers (to clean up any that are no longer used)
                    val existingDocs = personaRef.get().await()
                    for (doc in existingDocs) {
                        personaRef.document(doc.id).delete().await()
                    }

                    // Add new answers
                    for ((questionId, answer) in currentAnswers.value) {
                        if (answer.isNotBlank()) {
                            personaRef.document(questionId).set(mapOf("answer" to answer)).await()
                        }
                    }

                    // Count answered questions (must answer at least 8 out of 10 questions)
                    val answeredCount = currentAnswers.value.count { it.value.isNotBlank() }
                    val isCompleted = answeredCount >= 8

                    // Update user's personaCompleted field
                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(userId)
                        .update("personaCompleted", isCompleted)
                        .await()

                    if (isCompleted) {
                        snackbarHostState.showSnackbar("Persona saved successfully! ($answeredCount/10 questions answered)")
                    } else {
                        snackbarHostState.showSnackbar("Persona saved! Please answer at least 8 questions to complete. ($answeredCount/10 answered)")
                    }
                }
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Failed to save: ${e.message}")
            } finally {
                isLoading.value = false
            }
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
                        text = "Your Persona",
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
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF395B64)
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = { savePersona() },
                        enabled = !isLoading.value,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF395B64)
                        )
                    ) {
                        Text(
                            text = "Save",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = AppFonts.KarlaFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .padding(horizontal = 16.dp)
        ) {
            if (isLoading.value) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(24.dp),
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
                                    text = "Loading your persona...",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = AppFonts.KaiseiDecolFontFamily,
                                        color = Color(0xFF666666)
                                    )
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Introduction card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "Personalize Your AI Assistant",
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontFamily = AppFonts.KarlaFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp,
                                            color = Color(0xFF395B64)
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Help us understand your communication style so your AI can respond just like you! Answer at least 8 questions to complete your persona.",
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

                    // Progress indicator
                    item {
                        val answeredCount = currentAnswers.value.count { it.value.isNotBlank() }
                        val totalQuestions = questions.size
                        val progress = answeredCount.toFloat() / totalQuestions.toFloat()

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F8FF)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Progress",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = AppFonts.KarlaFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF395B64)
                                        )
                                    )
                                    Text(
                                        text = "$answeredCount/$totalQuestions",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = AppFonts.KarlaFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF395B64)
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = progress,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color(0xFF395B64),
                                    trackColor = Color(0xFFE0E0E0)
                                )
                            }
                        }
                    }

                    // Questions
                    items(questions.size) { index ->
                        val question = questions[index]
                        PersonaQuestionCard(
                            question = question,
                            questionNumber = index + 1,
                            answer = currentAnswers.value[question.id] ?: "",
                            onAnswerChanged = { answer ->
                                val updatedAnswers = currentAnswers.value.toMutableMap()
                                updatedAnswers[question.id] = answer
                                currentAnswers.value = updatedAnswers
                            }
                        )
                    }

                    // Bottom spacing
                    item {
                        Spacer(modifier = Modifier.navigationBarsPadding())
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun PersonaQuestionCard(
    question: PersonaQuestion,
    questionNumber: Int,
    answer: String,
    onAnswerChanged: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Question number and prompt
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = if (answer.isNotBlank()) Color(0xFF395B64) else Color(0xFFE0E0E0),
                            shape = androidx.compose.foundation.shape.CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = questionNumber.toString(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = question.prompt,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF395B64)
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Answer input based on question type
            when (question.type) {
                QuestionType.SINGLE_CHOICE -> {
                    Column(
                        modifier = Modifier
                            .selectableGroup()
                            .fillMaxWidth()
                    ) {
                        question.options?.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = (option == answer),
                                        onClick = { onAnswerChanged(option) },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (option == answer),
                                    onClick = null, // null because the parent is selectable
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFF395B64),
                                        unselectedColor = Color(0xFF666666)
                                    )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = AppFonts.KaiseiDecolFontFamily,
                                        fontSize = 14.sp,
                                        color = Color(0xFF395B64)
                                    )
                                )
                            }
                        }
                    }
                }
                QuestionType.FREE_TEXT -> {
                    OutlinedTextField(
                        value = answer,
                        onValueChange = onAnswerChanged,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "Write your answer here...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = AppFonts.KaiseiDecolFontFamily,
                                    color = Color(0xFF666666)
                                )
                            )
                        },
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF395B64),
                            unfocusedBorderColor = Color(0xFFE0E0E0),
                            focusedTextColor = Color(0xFF395B64),
                            unfocusedTextColor = Color(0xFF395B64),
                            cursorColor = Color(0xFF395B64)
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                            fontSize = 14.sp
                        )
                    )
                }
            }
        }
    }
}
