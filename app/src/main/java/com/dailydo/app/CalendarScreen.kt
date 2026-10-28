package com.dailydo.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CalendarScreen(
    vm: TodoViewModel,
    onEditTask: (TodoItem) -> Unit
) {
    val todos by vm.todos.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var viewMode by remember { mutableStateOf("Monthly") }
    var showViewModeSheet by remember { mutableStateOf(false) }

    var showAddTaskSheet by remember { mutableStateOf(false) }
    var addTaskSheetDate by remember { mutableStateOf<LocalDate?>(null) }

    val daysWithTasks = remember(todos, currentMonth) {
        todos.filter { !it.isDone }.mapNotNull { task ->
            task.dueDateMillis?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
            }
        }.toSet()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ════════════════════════════════════════════
        // TOP ROW: navigation + title + view filter button
        // ════════════════════════════════════════════
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                when (viewMode) {
                    "Weekly" -> selectedDate = selectedDate.minusWeeks(1)
                    "Daily" -> selectedDate = selectedDate.minusDays(1)
                    "Annually" -> currentMonth = currentMonth.minusYears(1)
                    else -> currentMonth = currentMonth.minusMonths(1)
                }
            }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val titleText = when (viewMode) {
                "Daily" -> selectedDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                "Annually" -> "${currentMonth.year}"
                else -> "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${currentMonth.year}"
            }

            Text(
                text = titleText,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = if (viewMode == "Annually") 22.sp else 20.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )

            IconButton(onClick = {
                when (viewMode) {
                    "Weekly" -> selectedDate = selectedDate.plusWeeks(1)
                    "Daily" -> selectedDate = selectedDate.plusDays(1)
                    "Annually" -> currentMonth = currentMonth.plusYears(1)
                    else -> currentMonth = currentMonth.plusMonths(1)
                }
            }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = { /* filter */ }) {
                Icon(
                    Icons.Outlined.FilterAlt,
                    contentDescription = "Filter",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // View Mode Filter Button
            OutlinedButton(
                onClick = { showViewModeSheet = true },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(viewMode, fontSize = 12.sp)
            }
        }

        // ════════════════════════════════════════════
        // MAIN CONTENT AREA BASED ON viewMode
        // ════════════════════════════════════════════
        when (viewMode) {
            "Monthly" -> {
                // Weekday Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    val weekdays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                    weekdays.forEach { day ->
                        Text(
                            text = day,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                CalendarGrid(
                    currentMonth = currentMonth,
                    selectedDate = selectedDate,
                    daysWithTasks = daysWithTasks,
                    onDayClick = { selectedDate = it },
                    onDayLongClick = { date ->
                        addTaskSheetDate = date
                        showAddTaskSheet = true
                    }
                )

                Spacer(Modifier.height(16.dp))

                TaskListForDate(todos, selectedDate, vm, onEditTask)
            }

            "Weekly" -> {
                // Weekly Horizontal LazyRow view
                val startOfWeek = selectedDate.minusDays((selectedDate.dayOfWeek.value % 7).toLong())
                val weekDays = (0..6).map { startOfWeek.plusDays(it.toLong()) }

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(weekDays) { date ->
                        val isSelected = date == selectedDate
                        val hasTask = daysWithTasks.contains(date)
                        val dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

                        Card(
                            modifier = Modifier
                                .width(56.dp)
                                .height(72.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .combinedClickable(
                                    onClick = { selectedDate = date },
                                    onLongClick = {
                                        addTaskSheetDate = date
                                        showAddTaskSheet = true
                                    }
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = dayName,
                                    fontSize = 12.sp,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onBackground
                                )
                                if (hasTask) {
                                    Spacer(Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) Color.White else MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                TaskListForDate(todos, selectedDate, vm, onEditTask)
            }

            "Daily" -> {
                // Daily View Header with arrows
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { selectedDate = selectedDate.minusDays(1) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous day",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(onClick = { selectedDate = selectedDate.plusDays(1) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next day",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Box(modifier = Modifier.weight(1f)) {
                    TaskListForDate(todos, selectedDate, vm, onEditTask)
                }
            }

            "Annually" -> {
                // Annually View: 3x4 grid showing 12 months of current year
                val year = currentMonth.year
                val months = (1..12).map { YearMonth.of(year, it) }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    gridItems(months) { ym ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .clickable {
                                    currentMonth = ym
                                    viewMode = "Monthly"
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = ym.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Add Task Sheet (Direct from Calendar) ───────────────
    if (showAddTaskSheet) {
        AddTaskSheet(
            initialTask = null,
            initialDate = addTaskSheetDate,
            onDismiss = {
                showAddTaskSheet = false
                addTaskSheetDate = null
            },
            onSave = { newTask ->
                scope.launch {
                    val savedId = vm.addTodoFull(newTask)
                    val savedTask = newTask.copy(id = savedId.toInt())
                    TestReminderHelper.scheduleReminder(context, savedTask)
                    showAddTaskSheet = false
                    addTaskSheetDate = null
                }
            }
        )
    }

    // ── View Mode Sheet ─────────────────────────────────────
    if (showViewModeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showViewModeSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "View Mode",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                listOf("Monthly", "Daily", "Weekly", "Annually").forEach { mode ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (viewMode == mode) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable {
                                viewMode = mode
                                showViewModeSheet = false
                            }
                            .padding(14.dp)
                    ) {
                        Text(
                            text = mode,
                            fontSize = 16.sp,
                            fontWeight = if (viewMode == mode) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (viewMode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════
// HELPER: TASK LIST FOR DATE
// ═════════════════════════════════════════════════════════════

@Composable
fun TaskListForDate(
    todos: List<TodoItem>,
    selectedDate: LocalDate,
    vm: TodoViewModel,
    onEditTask: (TodoItem) -> Unit
) {
    val context = LocalContext.current
    val tasksForDay = todos.filter { shouldRunToday(it, selectedDate) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (tasksForDay.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No tasks for this day",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            items(tasksForDay, key = { it.id }) { task ->
                CalendarTaskRow(
                    task = task,
                    onToggle = {
                        vm.toggleDone(task)
                        if (task.isDone) {
                            TestReminderHelper.cancelReminder(context, task.id)
                        } else {
                            TestReminderHelper.scheduleReminder(context, task)
                        }
                    },
                    onDelete = {
                        vm.delete(task)
                        TestReminderHelper.cancelReminder(context, task.id)
                    },
                    onEdit = {
                        onEditTask(task)
                    }
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════
// CALENDAR GRID
// ═════════════════════════════════════════════════════════════

@Composable
fun CalendarGrid(
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    daysWithTasks: Set<LocalDate>,
    onDayClick: (LocalDate) -> Unit,
    onDayLongClick: (LocalDate) -> Unit
) {
    val firstDay = currentMonth.atDay(1)
    val daysInMonth = currentMonth.lengthOfMonth()
    val firstDayOfWeek = firstDay.dayOfWeek.value % 7

    val days: List<LocalDate?> = buildList {
        repeat(firstDayOfWeek) { add(null) }
        for (day in 1..daysInMonth) {
            add(currentMonth.atDay(day))
        }
        while (size % 7 != 0) add(null)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        userScrollEnabled = false
    ) {
        gridItems(days) { date ->
            if (date == null) {
                Spacer(Modifier.aspectRatio(1f))
            } else {
                DayCell(
                    date = date,
                    isSelected = date == selectedDate,
                    hasTask = daysWithTasks.contains(date),
                    onClick = { onDayClick(date) },
                    onLongClick = { onDayLongClick(date) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DayCell(
    date: LocalDate,
    isSelected: Boolean,
    hasTask: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val today = LocalDate.now()
    val isPast = date.isBefore(today)

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(4.dp)
            .clip(CircleShape)
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                color = when {
                    isSelected -> Color.White
                    isPast -> Color(0xFF8B8B8B)
                    else -> MaterialTheme.colorScheme.onBackground
                },
                fontSize = 16.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )

            if (hasTask) {
                Spacer(Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) Color.White
                            else MaterialTheme.colorScheme.primary
                        )
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════
// TASK ROW (in calendar screen)
// ═════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarTaskRow(
    task: TodoItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    var showActionSheet by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable { showActionSheet = true },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left vertical priority bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(priorityColor(task.priority))
            )

            Spacer(Modifier.width(14.dp))

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = task.title,
                    color = if (task.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
                    fontSize = 16.sp
                )
            }

            // Priority-Colored Flag in Calendar
            IconButton(onClick = { showActionSheet = true }) {
                Icon(
                    Icons.Outlined.Flag,
                    contentDescription = "Flag",
                    tint = priorityColor(task.priority),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showActionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showActionSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = task.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))
                TaskActionRow(
                    icon = Icons.Default.Check,
                    label = if (task.isDone) "Mark as incomplete" else "Mark as complete",
                    onClick = {
                        showActionSheet = false
                        onToggle()
                    }
                )
                TaskActionRow(
                    icon = Icons.Outlined.Edit,
                    label = "Edit task",
                    onClick = {
                        showActionSheet = false
                        onEdit()
                    }
                )
                TaskActionRow(
                    icon = Icons.Outlined.Delete,
                    label = "Delete task",
                    tint = Color(0xFFE53935),
                    onClick = {
                        showActionSheet = false
                        onDelete()
                    }
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun TaskActionRow(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onBackground,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            fontSize = 16.sp,
            color = tint,
            fontWeight = FontWeight.Medium
        )
    }
}
