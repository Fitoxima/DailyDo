package com.dailydo.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletedTasksScreen(
    vm: TodoViewModel,
    onBack: () -> Unit
) {
    val completedTodos by vm.completedTodos.collectAsState()
    val focusManager = LocalFocusManager.current

    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("All Time") }
    var appliedStartDate by remember { mutableStateOf<Long?>(null) }
    var appliedEndDate by remember { mutableStateOf<Long?>(null) }

    var showFilterSheet by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<TodoItem?>(null) }

    val trimmedQuery = searchQuery.trim()
    val filteredTasks = completedTodos.filter { task ->
        val matchesQuery = trimmedQuery.isEmpty() || task.title.contains(trimmedQuery, ignoreCase = true)
        val taskTime = task.dueDateMillis ?: task.dueTimeMillis

        val matchesDate = when {
            appliedStartDate == null && appliedEndDate == null -> true
            taskTime == null -> false
            appliedStartDate != null && appliedEndDate != null -> {
                val start = normalizeToStartOfDay(appliedStartDate!!)
                val end = normalizeToEndOfDay(appliedEndDate!!)
                taskTime in start..end
            }
            appliedStartDate != null -> {
                val start = normalizeToStartOfDay(appliedStartDate!!)
                val end = normalizeToEndOfDay(appliedStartDate!!)
                taskTime in start..end
            }
            appliedEndDate != null -> {
                val start = normalizeToStartOfDay(appliedEndDate!!)
                val end = normalizeToEndOfDay(appliedEndDate!!)
                taskTime in start..end
            }
            else -> true
        }

        matchesQuery && matchesDate
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Top Bar ──────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = "Completed Tasks",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        // ── Search & Filter Row ──────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "Search completed tasks…",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            )

            OutlinedButton(
                onClick = { showFilterSheet = true },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                border = BorderStroke(
                    1.5.dp,
                    if (appliedStartDate != null || appliedEndDate != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (appliedStartDate != null || appliedEndDate != null)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.height(56.dp)
            ) {
                Icon(
                    Icons.Outlined.FilterAlt,
                    contentDescription = "Filter",
                    tint = if (appliedStartDate != null || appliedEndDate != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Filter",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (appliedStartDate != null || appliedEndDate != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── Active Filter Chip ───────────────────────────────
        if (appliedStartDate != null || appliedEndDate != null) {
            val filterChipText = when {
                appliedStartDate != null && appliedEndDate != null && appliedStartDate != appliedEndDate ->
                    "📅 ${formatDate(appliedStartDate!!)} - ${formatDate(appliedEndDate!!)}"
                appliedStartDate != null -> "📅 ${formatDate(appliedStartDate!!)}"
                appliedEndDate != null -> "📅 ${formatDate(appliedEndDate!!)}"
                else -> ""
            }

            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = filterChipText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Clear filter",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .clickable {
                            appliedStartDate = null
                            appliedEndDate = null
                            filterMode = "All Time"
                        }
                )
            }
        }

        // ── List of Completed Tasks ──────────────────────────
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (filteredTasks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No completed tasks found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 15.sp
                        )
                    }
                }
            } else {
                items(filteredTasks, key = { it.id }) { item ->
                    TaskCard(
                        title = item.title,
                        priority = item.priority,
                        done = item.isDone,
                        onToggle = {
                            vm.toggleDone(item)
                        },
                        onDelete = {
                            vm.delete(item)
                        },
                        onLongClick = { editingTask = item }
                    )
                }
            }
        }
    }

    // ── Edit Task Sheet ──────────────────────────────────────
    if (editingTask != null) {
        AddTaskSheet(
            initialTask = editingTask,
            initialDate = null,
            onDismiss = { editingTask = null },
            onSave = { updated ->
                vm.updateTodo(updated)
                editingTask = null
            }
        )
    }

    // ── Filter Bottom Sheet ──────────────────────────────────
    if (showFilterSheet) {
        FilterBottomSheet(
            currentMode = filterMode,
            currentStartDate = appliedStartDate,
            currentEndDate = appliedEndDate,
            onApply = { mode, start, end ->
                filterMode = mode
                appliedStartDate = start
                appliedEndDate = end
                showFilterSheet = false
            },
            onDismiss = { showFilterSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    currentMode: String,
    currentStartDate: Long?,
    currentEndDate: Long?,
    onApply: (mode: String, startDate: Long?, endDate: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf(currentMode) }
    var tempStartDate by remember { mutableStateOf(currentStartDate) }
    var tempEndDate by remember { mutableStateOf(currentEndDate) }

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Filter Completed Tasks",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Mode Pills: [ All Time ] [ Specific Date ] [ Date Period ]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All Time", "Specific Date", "Date Period").forEach { m ->
                    val selected = mode == m
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                            .border(BorderStroke(1.5.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline), RoundedCornerShape(50))
                            .clickable {
                                mode = m
                                if (m == "All Time") {
                                    tempStartDate = null
                                    tempEndDate = null
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = m,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Selection Controls based on mode
            when (mode) {
                "Specific Date" -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Date",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { showStartPicker = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = tempStartDate?.let { formatDate(it) } ?: "Select Date",
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                "Date Period" -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "From",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = { showStartPicker = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = tempStartDate?.let { formatDate(it) } ?: "Start Date",
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "To",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = { showEndPicker = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = tempEndDate?.let { formatDate(it) } ?: "End Date",
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            // Action Buttons: Clear & Apply
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onApply("All Time", null, null)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.onBackground)
                }

                Button(
                    onClick = {
                        if (mode == "Specific Date") {
                            onApply(mode, tempStartDate, tempStartDate)
                        } else {
                            onApply(mode, tempStartDate, tempEndDate)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Apply", fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    // Date Pickers
    if (showStartPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = tempStartDate ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    tempStartDate = datePickerState.selectedDateMillis
                    showStartPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEndPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = tempEndDate ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    tempEndDate = datePickerState.selectedDateMillis
                    showEndPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun normalizeToStartOfDay(millis: Long): Long {
    val localDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    return localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

private fun normalizeToEndOfDay(millis: Long): Long {
    val localDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    return localDate.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

private fun formatDate(millis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(formatter)
}
