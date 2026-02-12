package com.example.jawafai.view.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.jawafai.model.DetectedReminderIntent
import com.example.jawafai.model.EventType
import com.example.jawafai.model.Reminder
import com.example.jawafai.ui.theme.AppFonts
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Theme colors
private val JawafAccent = Color(0xFF1BC994)
private val JawafText = Color(0xFF191919)
private val JawafTextSecondary = Color(0xFF666666)

/**
 * Dialog shown when a reminder intent is detected from conversation
 * Allows user to review, edit, and confirm the reminder
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderConfirmationDialog(
    detectedIntent: DetectedReminderIntent,
    onConfirm: (Reminder) -> Unit,
    onDismiss: () -> Unit
) {
    // Editable states
    var title by remember { mutableStateOf(detectedIntent.title) }
    var description by remember { mutableStateOf(detectedIntent.description) }
    var selectedDate by remember { mutableStateOf(detectedIntent.detectedDateTime?.toLocalDate() ?: LocalDate.now()) }
    var selectedTime by remember { mutableStateOf(detectedIntent.detectedDateTime?.toLocalTime() ?: LocalTime.of(9, 0)) }
    var selectedEventType by remember { mutableStateOf(detectedIntent.eventType) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header with icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(JawafAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EventAvailable,
                            contentDescription = null,
                            tint = JawafAccent,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Reminder Detected! 🎯",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = AppFonts.KarlaFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = JawafText
                            )
                        )
                        Text(
                            text = "Would you like to save this?",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = AppFonts.KaiseiDecolFontFamily,
                                fontSize = 14.sp,
                                color = JawafTextSecondary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Source message preview
                if (detectedIntent.sourceMessage.isNotBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF5F5F5)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FormatQuote,
                                contentDescription = null,
                                tint = JawafTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = detectedIntent.sourceMessage.take(150) +
                                       if (detectedIntent.sourceMessage.length > 150) "..." else "",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = AppFonts.KaiseiDecolFontFamily,
                                    fontSize = 12.sp,
                                    color = JawafTextSecondary
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Title input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Event Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JawafAccent,
                        unfocusedBorderColor = Color(0xFFE0E0E0),
                        focusedTextColor = JawafText,
                        unfocusedTextColor = JawafText,
                        cursorColor = JawafAccent,
                        focusedLabelColor = JawafAccent
                    ),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Description input
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JawafAccent,
                        unfocusedBorderColor = Color(0xFFE0E0E0),
                        focusedTextColor = JawafText,
                        unfocusedTextColor = JawafText,
                        cursorColor = JawafAccent,
                        focusedLabelColor = JawafAccent
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Date and Time row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Date picker button
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = JawafText
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = selectedDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontSize = 14.sp
                        )
                    }

                    // Time picker button
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = JawafText
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = selectedTime.format(DateTimeFormatter.ofPattern("h:mm a")),
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Event Type selector
                Text(
                    text = "Event Type",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Medium,
                        color = JawafTextSecondary
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EventType.values().take(4).forEach { type ->
                        EventTypeChip(
                            type = type,
                            isSelected = selectedEventType == type,
                            onClick = { selectedEventType = type }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = JawafTextSecondary
                        )
                    ) {
                        Text(
                            text = "Cancel",
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = {
                            // Create reminder from edited values
                            val dateTime = LocalDateTime.of(selectedDate, selectedTime)
                            val eventTimestamp = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            val reminderTimestamp = eventTimestamp - (30 * 60 * 1000) // 30 min before

                            val reminder = Reminder(
                                id = "",
                                userId = "",
                                title = title.ifBlank { "Event" },
                                description = description,
                                eventDate = eventTimestamp,
                                reminderTime = reminderTimestamp,
                                eventType = selectedEventType.name,
                                source = detectedIntent.source.name,
                                sourceConversationId = detectedIntent.sourceConversationId,
                                color = selectedEventType.defaultColor
                            )
                            onConfirm(reminder)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JawafAccent
                        ),
                        enabled = title.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Remind Me",
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedDate = java.time.Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK", color = JawafAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Time Picker Dialog
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedTime.hour,
            initialMinute = selectedTime.minute
        )

        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Time") },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                        showTimePicker = false
                    }
                ) {
                    Text("OK", color = JawafAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EventTypeChip(
    type: EventType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        Color(android.graphics.Color.parseColor(type.defaultColor)).copy(alpha = 0.15f)
    } else {
        Color(0xFFF5F5F5)
    }

    val contentColor = if (isSelected) {
        Color(android.graphics.Color.parseColor(type.defaultColor))
    } else {
        JawafTextSecondary
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (type) {
                    EventType.MEETING -> Icons.Outlined.Groups
                    EventType.WORK -> Icons.Outlined.Work
                    EventType.PERSONAL -> Icons.Outlined.Person
                    EventType.HEALTH -> Icons.Outlined.FavoriteBorder
                    EventType.SPORTS -> Icons.Outlined.FitnessCenter
                    EventType.SOCIAL -> Icons.Outlined.Celebration
                    EventType.REMINDER -> Icons.Outlined.NotificationsActive
                    EventType.OTHER -> Icons.Outlined.Event
                },
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = type.displayName,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = AppFonts.KarlaFontFamily,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = contentColor
                )
            )
        }
    }
}

