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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jawafai.managers.ReminderFirebaseManager
import com.example.jawafai.model.Reminder
import com.example.jawafai.model.EventType as ModelEventType
import com.example.jawafai.ui.theme.AppFonts
import kotlinx.coroutines.launch
import java.time.LocalDate
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

    // Filter events for selected date and upcoming
    val eventsForSelectedDate = reminderEvents.filter { it.date == selectedDate }
    val upcomingEvents = reminderEvents.filter { it.date >= LocalDate.now() }.sortedBy { it.date }

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
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

                        // Add reminder button
                        FloatingActionButton(
                            onClick = { /* TODO: Add reminder */ },
                            modifier = Modifier.size(40.dp),
                            containerColor = JawafAccent,
                            contentColor = Color.White,
                            elevation = FloatingActionButtonDefaults.elevation(
                                defaultElevation = 2.dp
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Reminder",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

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
                onClick = { /* TODO: Add reminder */ },
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

            // Selected Date Events
            item {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (selectedDate == LocalDate.now()) "Today's Events"
                           else selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
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

            // Upcoming Events Section
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

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
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

