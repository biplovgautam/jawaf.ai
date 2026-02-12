package com.example.jawafai.view.dashboard.reminder

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.jawafai.managers.ReminderFirebaseManager
import com.example.jawafai.model.Reminder
import com.example.jawafai.model.EventType as ModelEventType
import com.example.jawafai.service.ReminderScheduler
import com.example.jawafai.ui.theme.AppFonts
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

// Theme colors
private val JawafAccent = Color(0xFF1BC994)
private val JawafText = Color(0xFF191919)
private val JawafTextSecondary = Color(0xFF666666)
private val JawafBackground = Color(0xFFFAFAFA)

/**
 * Reminder Screen with Calendar and Upcoming Events
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var isLoading by remember { mutableStateOf(true) }
    var reminders by remember { mutableStateOf<List<Reminder>>(emptyList()) }

    // New Reminder Dialog state
    var showNewReminderDialog by remember { mutableStateOf(false) }

    // Load reminders from Firebase
    LaunchedEffect(Unit) {
        isLoading = true
        val result = ReminderFirebaseManager.getAllReminders()
        result.onSuccess { fetchedReminders ->
            reminders = fetchedReminders
        }.onFailure { error ->
            Toast.makeText(context, "Failed to load reminders: ${error.message}", Toast.LENGTH_SHORT).show()
        }
        isLoading = false
    }

    // Convert Reminder to ReminderEvent for UI
    val reminderEvents = remember(reminders) {
        reminders.map { reminder ->
            ReminderEvent(
                id = reminder.id,
                title = reminder.title,
                description = reminder.description,
                date = reminder.getLocalDate(),
                time = reminder.getFormattedTime(),
                type = try {
                    EventType.valueOf(reminder.eventType)
                } catch (e: Exception) {
                    EventType.OTHER
                },
                color = try {
                    Color(android.graphics.Color.parseColor(reminder.color))
                } catch (e: Exception) {
                    JawafAccent
                }
            )
        }
    }

    // Filter events for selected date and upcoming (excluding today's events from upcoming)
    val todayEvents = reminderEvents.filter { it.date == LocalDate.now() }
    val eventsForSelectedDate = reminderEvents.filter { it.date == selectedDate }
    val upcomingEvents = reminderEvents.filter { it.date > LocalDate.now() }.sortedBy { it.date }

    Scaffold(
        containerColor = JawafBackground,
        topBar = {
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
                    Text(
                        text = "Reminders",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            color = JawafText
                        )
                    )


                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Stay organized with your schedule",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                            fontSize = 14.sp,
                            color = JawafTextSecondary
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewReminderDialog = true },
                containerColor = JawafAccent,
                contentColor = Color.White,
                modifier = Modifier.padding(bottom = 70.dp) // Space for bottom nav
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "New Reminder",
                    fontFamily = AppFonts.KarlaFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // Calendar Card
            item {
                CalendarCard(
                    currentMonth = currentMonth,
                    selectedDate = selectedDate,
                    onDateSelected = { selectedDate = it },
                    onMonthChanged = { currentMonth = it },
                    eventsMap = reminderEvents.groupBy { it.date }
                )
            }

            // Today's Events Section - Only show if there are events today
            if (todayEvents.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Today's Events",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = JawafText
                        ),
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }

                items(todayEvents) { event ->
                    EventCard(event = event)
                }
            }

            // Selected Date Events - Only show if selected date is NOT today
            if (selectedDate != LocalDate.now()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = JawafText
                        ),
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (eventsForSelectedDate.isEmpty()) {
                    item {
                        EmptyEventsCard(
                            message = "No events for this day",
                            icon = Icons.Outlined.EventBusy
                        )
                    }
                } else {
                    items(eventsForSelectedDate) { event ->
                        EventCard(event = event)
                    }
                }
            }

            // Upcoming Events Section - Excludes today's events
            if (upcomingEvents.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Upcoming Events",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = AppFonts.KarlaFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = JawafText
                            )
                        )

                        Text(
                            text = "${upcomingEvents.size} events",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = AppFonts.KaiseiDecolFontFamily,
                                fontSize = 12.sp,
                                color = JawafAccent
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                items(upcomingEvents) { event ->
                    EventCard(event = event, showDate = true)
                }
            }

            // Empty state if no events at all
            if (todayEvents.isEmpty() && upcomingEvents.isEmpty() && selectedDate == LocalDate.now()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    EmptyEventsCard(
                        message = "No upcoming events",
                        icon = Icons.Outlined.EventAvailable
                    )
                }
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // New Reminder Dialog
    if (showNewReminderDialog) {
        NewReminderDialog(
            initialDate = selectedDate,
            onDismiss = { showNewReminderDialog = false },
            onSave = { reminder ->
                coroutineScope.launch {
                    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                    val reminderWithUser = reminder.copy(userId = userId)

                    val result = ReminderFirebaseManager.saveReminder(reminderWithUser)
                    result.onSuccess { savedReminder ->
                        // Schedule the notification
                        ReminderScheduler.scheduleReminder(context, savedReminder)

                        // Refresh reminders list
                        val refreshResult = ReminderFirebaseManager.getAllReminders()
                        refreshResult.onSuccess { fetchedReminders ->
                            reminders = fetchedReminders
                        }

                        Toast.makeText(
                            context,
                            "Reminder saved & scheduled! 🎯",
                            Toast.LENGTH_SHORT
                        ).show()
                    }.onFailure { error ->
                        Toast.makeText(
                            context,
                            "Failed to save: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                showNewReminderDialog = false
            }
        )
    }
}

/**
 * Dialog for creating a new reminder
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewReminderDialog(
    initialDate: LocalDate = LocalDate.now(),
    onDismiss: () -> Unit,
    onSave: (Reminder) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(initialDate) }
    var selectedTime by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var selectedEventType by remember { mutableStateOf(EventType.OTHER) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
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
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(JawafAccent.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationAdd,
                                contentDescription = null,
                                tint = JawafAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "New Reminder",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = AppFonts.KarlaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = JawafText
                                )
                            )
                            Text(
                                text = "Set a new reminder",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = AppFonts.KaiseiDecolFontFamily,
                                    color = JawafTextSecondary
                                )
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = JawafTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Title Input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title *") },
                    placeholder = { Text("e.g., Team Meeting") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JawafAccent,
                        cursorColor = JawafAccent
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Description Input
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    placeholder = { Text("Add details...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JawafAccent,
                        cursorColor = JawafAccent
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Date & Time Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Date Picker
                    OutlinedCard(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = Color(0xFFF8F8F8)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarToday,
                                contentDescription = null,
                                tint = JawafAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Date",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = JawafTextSecondary
                                    )
                                )
                                Text(
                                    text = selectedDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = AppFonts.KarlaFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        color = JawafText
                                    )
                                )
                            }
                        }
                    }

                    // Time Picker
                    OutlinedCard(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = Color(0xFFF8F8F8)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Schedule,
                                contentDescription = null,
                                tint = JawafAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Time",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = JawafTextSecondary
                                    )
                                )
                                Text(
                                    text = selectedTime.format(DateTimeFormatter.ofPattern("h:mm a")),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = AppFonts.KarlaFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        color = JawafText
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Event Type Selection
                Text(
                    text = "Event Type",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Medium,
                        color = JawafText
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(EventType.entries.toTypedArray()) { type ->
                        FilterChip(
                            selected = selectedEventType == type,
                            onClick = { selectedEventType = type },
                            label = {
                                Text(
                                    text = type.label,
                                    fontFamily = AppFonts.KarlaFontFamily,
                                    fontSize = 12.sp
                                )
                            },
                            leadingIcon = if (selectedEventType == type) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = JawafAccent.copy(alpha = 0.2f),
                                selectedLabelColor = JawafAccent
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
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
                            val dateTime = LocalDateTime.of(selectedDate, selectedTime)
                            val eventTimestamp = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            val reminderTimestamp = eventTimestamp - (5 * 60 * 1000) // 5 min before

                            val eventColor = when (selectedEventType) {
                                EventType.MEETING -> "#4285F4"
                                EventType.WORK -> "#EA4335"
                                EventType.PERSONAL -> "#FBBC05"
                                EventType.HEALTH -> "#34A853"
                                EventType.OTHER -> "#1BC994"
                            }

                            val reminder = Reminder(
                                id = "",
                                userId = "",
                                title = title.ifBlank { "Reminder" },
                                description = description,
                                eventDate = eventTimestamp,
                                reminderTime = reminderTimestamp,
                                eventType = selectedEventType.name,
                                source = "MANUAL",
                                sourceConversationId = "",
                                color = eventColor
                            )
                            onSave(reminder)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JawafAccent
                        ),
                        enabled = title.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Save",
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
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    selectedDayContainerColor = JawafAccent,
                    todayDateBorderColor = JawafAccent
                )
            )
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
            },
            title = { Text("Select Time") },
            text = {
                TimePicker(
                    state = timePickerState,
                    colors = TimePickerDefaults.colors(
                        selectorColor = JawafAccent,
                        timeSelectorSelectedContainerColor = JawafAccent.copy(alpha = 0.2f)
                    )
                )
            }
        )
    }
}

@Composable
fun CalendarCard(
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onMonthChanged: (YearMonth) -> Unit,
    eventsMap: Map<LocalDate, List<ReminderEvent>>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Month Navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onMonthChanged(currentMonth.minusMonths(1)) }
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Previous Month",
                        tint = JawafText
                    )
                }

                Text(
                    text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = JawafText
                    )
                )

                IconButton(
                    onClick = { onMonthChanged(currentMonth.plusMonths(1)) }
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Next Month",
                        tint = JawafText
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Day of Week Headers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { day ->
                    Text(
                        text = day,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = JawafTextSecondary
                        ),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Calendar Grid
            val firstDayOfMonth = currentMonth.atDay(1)
            val lastDayOfMonth = currentMonth.atEndOfMonth()
            val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value % 7 // Sunday = 0
            val daysInMonth = currentMonth.lengthOfMonth()

            val totalCells = firstDayOfWeek + daysInMonth
            val weeks = (totalCells + 6) / 7

            for (week in 0 until weeks) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (dayOfWeek in 0..6) {
                        val dayIndex = week * 7 + dayOfWeek - firstDayOfWeek + 1

                        if (dayIndex in 1..daysInMonth) {
                            val date = currentMonth.atDay(dayIndex)
                            val isSelected = date == selectedDate
                            val isToday = date == LocalDate.now()
                            val hasEvents = eventsMap.containsKey(date)

                            CalendarDay(
                                day = dayIndex,
                                isSelected = isSelected,
                                isToday = isToday,
                                hasEvents = hasEvents,
                                onClick = { onDateSelected(date) },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarDay(
    day: Int,
    isSelected: Boolean,
    isToday: Boolean,
    hasEvents: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(
                when {
                    isSelected -> JawafAccent
                    isToday -> JawafAccent.copy(alpha = 0.1f)
                    else -> Color.Transparent
                }
            )
            .then(
                if (isToday && !isSelected) {
                    Modifier.border(1.dp, JawafAccent, CircleShape)
                } else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = AppFonts.KarlaFontFamily,
                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp,
                    color = when {
                        isSelected -> Color.White
                        isToday -> JawafAccent
                        else -> JawafText
                    }
                )
            )

            // Event indicator dot
            if (hasEvents && !isSelected) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(JawafAccent)
                )
            }
        }
    }
}

@Composable
fun EventCard(
    event: ReminderEvent,
    showDate: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color indicator
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(event.color)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Event icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(event.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = event.type.icon,
                    contentDescription = null,
                    tint = event.color,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Event details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = JawafText
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = event.description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = AppFonts.KaiseiDecolFontFamily,
                        fontSize = 12.sp,
                        color = JawafTextSecondary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (showDate) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = event.date.format(DateTimeFormatter.ofPattern("EEE, MMM d")),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                            fontSize = 11.sp,
                            color = JawafAccent
                        )
                    )
                }
            }

            // Time
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = event.time,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = JawafText
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Event type badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = event.color.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = event.type.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = AppFonts.KarlaFontFamily,
                            fontSize = 10.sp,
                            color = event.color
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyEventsCard(
    message: String,
    icon: ImageVector
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = JawafTextSecondary.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = AppFonts.KaiseiDecolFontFamily,
                    fontSize = 14.sp,
                    color = JawafTextSecondary
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { /* TODO: Add event */ },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = JawafAccent
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(JawafAccent)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Add Event",
                    fontFamily = AppFonts.KarlaFontFamily,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// Data classes
data class ReminderEvent(
    val id: String,
    val title: String,
    val description: String,
    val date: LocalDate,
    val time: String,
    val type: EventType,
    val color: Color
)

enum class EventType(val label: String, val icon: ImageVector) {
    MEETING("Meeting", Icons.Outlined.Groups),
    WORK("Work", Icons.Outlined.Work),
    PERSONAL("Personal", Icons.Outlined.Person),
    HEALTH("Health", Icons.Outlined.FavoriteBorder),
    OTHER("Other", Icons.Outlined.Event)
}

