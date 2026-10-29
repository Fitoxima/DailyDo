package com.dailydo.app
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TimePickerSelectionMode
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.dailydo.app.ui.theme.TodoAppTheme
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

// ═════════════════════════════════════════════════════════════
// TASK RUN LOGIC
// ═════════════════════════════════════════════════════════════

fun shouldRunToday(task: TodoItem, date: LocalDate): Boolean {
    val dueDateMillis = task.dueDateMillis ?: return false
    val dueDate = Instant.ofEpochMilli(dueDateMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    return dueDate == date
}

class MainActivity : ComponentActivity() {
    private var editTaskIdState = mutableStateOf(-1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val dao = AppDatabase.getDatabase(this).todoDao()
        val settingsManager = SettingsManager(this)

        setContent {
            val displayMode by settingsManager.displayMode.collectAsState(initial = "Dark")
            val themeId by settingsManager.themeId.collectAsState(initial = 1)

            TodoAppTheme(
                displayMode = displayMode,
                themeId = themeId
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) {
                    val vm: TodoViewModel = viewModel(
                        factory = TodoViewModelFactory(dao)
                    )
                    MainTabs(
                        vm = vm,
                        initialEditTaskId = editTaskIdState.value,
                        settingsManager = settingsManager,
                        currentDisplayMode = displayMode,
                        currentThemeId = themeId
                    )
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════
// TOP-LEVEL NAVIGATION (Drawer + Bottom Nav + Tab switching)
// ═════════════════════════════════════════════════════════════

@Composable
fun MainTabs(
    vm: TodoViewModel,
    initialEditTaskId: Int = -1,
    settingsManager: SettingsManager,
    currentDisplayMode: String,
    currentThemeId: Int
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // State for adding/editing
    var showSheetGlobal by remember { mutableStateOf(false) }
    var editingTaskGlobal by remember { mutableStateOf<TodoItem?>(null) }
    var savedTaskForPopup by remember { mutableStateOf<TodoItem?>(null) }
    var isEditPopup by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Handle Notification Edit click
    LaunchedEffect(initialEditTaskId) {
        if (initialEditTaskId != -1) {
            val task = AppDatabase.getDatabase(context).todoDao().getById(initialEditTaskId)
            if (task != null) {
                editingTaskGlobal = task
            }
        }
    }

    var showCompletedScreen by remember { mutableStateOf(false) }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch {
            drawerState.close()
        }
    }

    BackHandler(enabled = selectedTab == 1 && !showCompletedScreen) {
        selectedTab = 0
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            AppDrawerContent(
                currentDisplayMode = currentDisplayMode,
                currentThemeId = currentThemeId,
                onSelectDisplayMode = { mode ->
                    scope.launch {
                        settingsManager.saveDisplayMode(mode)
                    }
                },
                onSelectTheme = { id ->
                    scope.launch {
                        settingsManager.saveThemeId(id)
                    }
                }
            )
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,

            floatingActionButton = {
                if (!showCompletedScreen) {
                    RippleFab(onClick = { showSheetGlobal = true })
                }
            },

            bottomBar = {
                if (!showCompletedScreen) {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                        ) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 0.dp,
                                windowInsets = WindowInsets(0, 0, 0, 0)
                            ) {
                                // ── Menu (opens drawer) ─────────────────
                                NavigationBarItem(
                                    selected = false,
                                    onClick = {
                                        scope.launch {
                                            if (drawerState.isClosed) drawerState.open()
                                            else drawerState.close()
                                        }
                                    },
                                    icon = {
                                        Box {
                                            Icon(
                                                Icons.Outlined.Menu,
                                                contentDescription = "Menu",
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFE53935))
                                                    .align(Alignment.TopEnd)
                                                    .offset(x = 6.dp, y = (-4).dp)
                                            )
                                        }
                                    },
                                    label = { Text("Menu", fontSize = 12.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = Color(0xFF9E9E9E),
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        unselectedTextColor = Color(0xFF9E9E9E),
                                        indicatorColor = Color.Transparent
                                    )
                                )

                                // ── Tasks / Calendar ────────────────────
                                val items = listOf(
                                    Triple("Tasks", Icons.Default.CheckCircle, Icons.Outlined.CheckCircle),
                                    Triple("Calendar", Icons.Default.DateRange, Icons.Outlined.DateRange)
                                )
                                items.forEachIndexed { index, (label, filled, outlined) ->
                                    NavigationBarItem(
                                        selected = selectedTab == index,
                                        onClick = {
                                            selectedTab = index
                                            showCompletedScreen = false
                                        },
                                        icon = {
                                            Icon(
                                                if (selectedTab == index) filled else outlined,
                                                contentDescription = label,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        },
                                        label = {
                                            Text(
                                                label,
                                                fontSize = 12.sp,
                                                fontWeight = if (selectedTab == index) FontWeight.Medium
                                                else FontWeight.Normal
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            unselectedIconColor = Color(0xFF9E9E9E),
                                            unselectedTextColor = Color(0xFF9E9E9E),
                                            indicatorColor = Color.Transparent
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                if (showCompletedScreen) {
                    BackHandler {
                        showCompletedScreen = false
                    }
                    CompletedTasksScreen(
                        vm = vm,
                        onBack = { showCompletedScreen = false }
                    )
                } else {
                    when (selectedTab) {
                        0 -> TasksScreen(
                            vm = vm,
                            settingsManager = settingsManager,
                            onOpenCompleted = { showCompletedScreen = true },
                            onEditTask = { editingTaskGlobal = it }
                        )
                        1 -> CalendarScreen(
                            vm = vm,
                            onEditTask = { editingTaskGlobal = it }
                        )
                    }
                }
            }
        }
    }

    // ── Add New Task ────────────────────────────────────────
    if (showSheetGlobal) {
        AddTaskSheet(
            initialTask = null,
            initialDate = null,
            onDismiss = { showSheetGlobal = false },
            onSave = { task ->
                scope.launch {
                    val savedId = vm.addTodoFull(task)
                    val savedTask = task.copy(id = savedId.toInt())
                    TestReminderHelper.scheduleReminder(context, savedTask)
                    showSheetGlobal = false
                    isEditPopup = false
                    savedTaskForPopup = savedTask
                }
            }
        )
    }

    // ── Edit Existing Task (List/Calendar/Notification) ─────
    if (editingTaskGlobal != null) {
        AddTaskSheet(
            initialTask = editingTaskGlobal,
            initialDate = null,
            onDismiss = { editingTaskGlobal = null },
            onSave = { updated ->
                scope.launch {
                    vm.updateTodo(updated)
                    TestReminderHelper.scheduleReminder(context, updated)

                    // Close sheet and wait for animation
                    editingTaskGlobal = null
                    delay(400)

                    // Show popup
                    isEditPopup = true
                    savedTaskForPopup = updated
                }
            }
        )
    }

    // ── Success Popup ───────────────────────────────────────
    if (savedTaskForPopup != null) {
        TaskSavedBottomSheet(
            task = savedTaskForPopup!!,
            isEdit = isEditPopup,
            onDismiss = { savedTaskForPopup = null }
        )
    }
}

// ═════════════════════════════════════════════════════════════
// RIPPLE FAB COMPOSABLE
// ═════════════════════════════════════════════════════════════

@Composable
fun RippleFab(onClick: () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "rippleFab")

    val progress1 by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring1"
    )
    val progress2 by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(1000)
        ),
        label = "ring2"
    )

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        label = "fabScale"
    )

    Box(
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        listOf(progress1, progress2).forEach { p ->
            val size = 56.dp + (96.dp - 56.dp) * p
            val alpha = 1f - p
            Box(
                modifier = Modifier
                    .size(size)
                    .alpha(alpha)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
            )
        }

        FloatingActionButton(
            onClick = onClick,
            interactionSource = interaction,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add task",
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════
// SPLIT FILTER BUTTON COMPOSABLE
// ═════════════════════════════════════════════════════════════

@Composable
fun RowScope.SplitFilterButton(
    icon: String,
    label: String,
    value: String,
    active: Boolean,
    indicatorColor: Color? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .weight(1f)
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                BorderStroke(
                    1.5.dp,
                    if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
                ),
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            fontSize = 15.sp,
            color = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label.uppercase(),
                fontSize = 10.sp,
                letterSpacing = 0.4.sp,
                fontWeight = FontWeight.Medium,
                color = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (indicatorColor != null) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════
// FILTER PILL COMPOSABLE
// ═════════════════════════════════════════════════════════════

@Composable
fun FilterPill(
    label: String,
    selected: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) activeColor else MaterialTheme.colorScheme.surface)
            .border(
                BorderStroke(
                    1.5.dp,
                    if (selected) activeColor else MaterialTheme.colorScheme.outline
                ),
                RoundedCornerShape(50)
            )
            .clickable { onClick() }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ═════════════════════════════════════════════════════════════
// TASKS SCREEN
// ═════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun TasksScreen(
    vm: TodoViewModel,
    settingsManager: SettingsManager,
    onOpenCompleted: () -> Unit = {},
    onEditTask: (TodoItem) -> Unit
) {
    val todos by vm.todos.collectAsState()
    val context = LocalContext.current
    val customCategories by settingsManager.customCategories.collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()

    var selectedCategory by remember { mutableStateOf("All") }
    var selectedPriority by remember { mutableStateOf("All") }
    var showPrevious by remember { mutableStateOf(true) }
    var showCompleted by remember { mutableStateOf(true) }

    var showCategorySheet by remember { mutableStateOf(false) }
    var showPrioritySheet by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var categoryToDelete by remember { mutableStateOf<String?>(null) }

    var batteryWarningDismissed by remember { mutableStateOf(false) }
    val isIgnoringBattery = remember { BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context) }

    if (!isIgnoringBattery && !batteryWarningDismissed) {
        AlertDialog(
            onDismissRequest = { batteryWarningDismissed = true },
            title = { Text("Background Activity Required") },
            text = { Text("To ensure reminders work when the app is closed, please allow background activity.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        BatteryOptimizationHelper.openManufacturerSettings(context)
                        batteryWarningDismissed = true
                    }
                ) {
                    Text("Fix Now")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { batteryWarningDismissed = true }
                ) {
                    Text("Dismiss")
                }
            }
        )
    }

    val today = LocalDate.now()
    val pendingFiltered = todos.filter {
        !it.isDone &&
                shouldRunToday(it, today) &&
                (selectedCategory == "All" || it.category == selectedCategory) &&
                (selectedPriority == "All" || it.priority == selectedPriority)
    }
    val completedFiltered = todos.filter {
        it.isDone &&
                shouldRunToday(it, today) &&
                (selectedCategory == "All" || it.category == selectedCategory) &&
                (selectedPriority == "All" || it.priority == selectedPriority)
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── App Name on Top of Main Screen ───────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "My To Do List",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )

            // ── Test Button ──────────────────────────────────────
            Button(
                onClick = {
                    TestReminderHelper.schedule5SecondReminder(context)
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text("Test 5s", fontSize = 12.sp)
            }
        }

        // ── Top Row: Split Filter Buttons ──────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SplitFilterButton(
                icon = "🏷",
                label = "Category",
                value = selectedCategory,
                active = selectedCategory != "All",
                onClick = { showCategorySheet = true }
            )
            SplitFilterButton(
                icon = "🚩",
                label = "Priority",
                value = selectedPriority,
                active = selectedPriority != "All",
                indicatorColor = if (selectedPriority == "All") null
                else priorityColor(selectedPriority),
                onClick = { showPrioritySheet = true }
            )
        }

        // ── Task list ───────────────────────────────────────
        LazyColumn(
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (pendingFiltered.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Previous (${pendingFiltered.size})",
                        expanded = showPrevious,
                        onToggle = { showPrevious = !showPrevious }
                    )
                }
                if (showPrevious) {
                    items(pendingFiltered, key = { it.id }) { item ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            TaskCard(
                                title = item.title,
                                priority = item.priority,
                                done = item.isDone,
                                onToggle = {
                                    vm.toggleDone(item)
                                    if (item.isDone) {
                                        TestReminderHelper.cancelReminder(context, item.id)
                                    } else {
                                        TestReminderHelper.scheduleReminder(context, item)
                                    }
                                },
                                onDelete = {
                                    vm.delete(item)
                                    TestReminderHelper.cancelReminder(context, item.id)
                                },
                                onLongClick = { onEditTask(item) }
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Completed Today (${completedFiltered.size})",
                    expanded = showCompleted,
                    onToggle = { showCompleted = !showCompleted }
                )
            }
            if (showCompleted) {
                if (completedFiltered.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Check all completed tasks",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textDecoration = TextDecoration.Underline,
                                fontSize = 14.sp,
                                modifier = Modifier.clickable { onOpenCompleted() }
                            )
                        }
                    }
                } else {
                    items(completedFiltered, key = { it.id }) { item ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            TaskCard(
                                title = item.title,
                                priority = item.priority,
                                done = item.isDone,
                                onToggle = {
                                    vm.toggleDone(item)
                                    if (item.isDone) {
                                        TestReminderHelper.cancelReminder(context, item.id)
                                    } else {
                                        TestReminderHelper.scheduleReminder(context, item)
                                    }
                                },
                                onDelete = {
                                    vm.delete(item)
                                    TestReminderHelper.cancelReminder(context, item.id)
                                },
                                onLongClick = { onEditTask(item) }
                            )
                        }
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Check all completed tasks",
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline,
                                fontSize = 14.sp,
                                modifier = Modifier.clickable { onOpenCompleted() }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCategorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showCategorySheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Category",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val defaultCategories = listOf("All", "Work", "Personal", "Wishlist")
                    val allCategories = (defaultCategories + customCategories + "+ New").distinct()
                    allCategories.forEach { cat ->
                        val selected = selectedCategory == cat
                        if (cat == "+ New") {
                            Box(
                                modifier = Modifier
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(
                                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                                        RoundedCornerShape(50)
                                    )
                                    .clickable {
                                        showCategorySheet = false
                                        showAddCategoryDialog = true
                                    }
                                    .padding(horizontal = 18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+ New",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                    .border(
                                        BorderStroke(
                                            1.5.dp,
                                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        ),
                                        RoundedCornerShape(50)
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            selectedCategory = cat
                                            showCategorySheet = false
                                        },
                                        onLongClick = {
                                            if (cat !in defaultCategories) {
                                                categoryToDelete = cat
                                                showCategorySheet = false
                                            }
                                        }
                                    )
                                    .padding(horizontal = 18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPrioritySheet) {
        ModalBottomSheet(
            onDismissRequest = { showPrioritySheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Priority",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Low", "Medium", "High").forEach { prio ->
                        val selected = selectedPriority == prio
                        val activeColor = when (prio) {
                            "Low"    -> PriorityLowColor
                            "Medium" -> PriorityMediumColor
                            "High"   -> PriorityHighColor
                            else     -> MaterialTheme.colorScheme.primary
                        }
                        Row(
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (selected) activeColor
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    BorderStroke(
                                        1.5.dp,
                                        if (selected) activeColor
                                        else MaterialTheme.colorScheme.outline
                                    ),
                                    RoundedCornerShape(50)
                                )
                                .clickable {
                                    selectedPriority = prio
                                    showPrioritySheet = false
                                }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(activeColor)
                            )
                            Text(
                                text = prio,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = when {
                                    selected && prio == "Medium" -> Color.Black
                                    selected -> Color.White
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false; newCategoryName = "" },
            title = { Text("Add Custom Category") },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    placeholder = { Text("Category name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = newCategoryName.trim()
                        if (trimmed.isNotEmpty()) {
                            scope.launch {
                                settingsManager.saveCustomCategory(trimmed)
                            }
                            selectedCategory = trimmed
                        }
                        newCategoryName = ""
                        showAddCategoryDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        newCategoryName = ""
                        showAddCategoryDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (categoryToDelete != null) {
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Delete '$categoryToDelete' category?") },
            text = { Text("Tasks will remain but lose this category.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelete = categoryToDelete!!
                        scope.launch {
                            settingsManager.deleteCustomCategory(toDelete)
                        }
                        if (selectedCategory == toDelete) {
                            selectedCategory = "All"
                        }
                        categoryToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE53935))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { categoryToDelete = null }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ═════════════════════════════════════════════════════════════
// SHARED COMPONENTS
// ═════════════════════════════════════════════════════════════

@Composable
fun SectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskCard(
    title: String,
    priority: String,
    done: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .combinedClickable(
                onClick = { onToggle() },
                onLongClick = { onLongClick() }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (done) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onToggle() },
                contentAlignment = Alignment.Center
            ) {
                if (done) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        Icons.Outlined.Circle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            // Priority dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(priorityColor(priority))
            )

            Spacer(Modifier.width(8.dp))

            // Title
            Text(
                text = title,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.width(8.dp))

            // Priority pill tag
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(priorityColor(priority).copy(alpha = 0.18f))
                    .border(
                        BorderStroke(1.dp, priorityColor(priority).copy(alpha = 0.6f)),
                        RoundedCornerShape(50)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = priority,
                    color = priorityColor(priority),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.3.sp
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════
// ADD TASK SHEET (Pills Hybrid Design)
// ═════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskSheet(
    initialTask: TodoItem? = null,
    initialDate: LocalDate? = null,
    onDismiss: () -> Unit,
    onSave: (TodoItem) -> Unit
) {
    var title by remember { mutableStateOf(initialTask?.title ?: "") }
    var description by remember { mutableStateOf(initialTask?.description ?: "") }
    var selectedPriority by remember { mutableStateOf(initialTask?.priority ?: "Medium") }
    var selectedCategory by remember { mutableStateOf(initialTask?.category ?: "Personal") }

    val initialReminderOpt = remember {
        val rem = initialTask?.reminderTimeMillis
        val due = initialTask?.dueTimeMillis ?: initialTask?.dueDateMillis
        when {
            rem == null -> "None"
            due != null && rem == due -> "At time"
            due != null && rem == due - 10 * 60 * 1000L -> "10 min before"
            due != null && rem == due - 60 * 60 * 1000L -> "1 hour before"
            else -> "At time"
        }
    }
    var selectedReminder by remember { mutableStateOf(initialReminderOpt) }

    var dueDate by remember {
        mutableStateOf(
            initialTask?.dueDateMillis?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
            } ?: initialDate ?: LocalDate.now()
        )
    }
    var dueTime by remember {
        mutableStateOf(
            initialTask?.dueTimeMillis?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime()
            } ?: LocalTime.of(9, 0)
        )
    }
    var showTimeWarning by remember { mutableStateOf(false) }
    LaunchedEffect(dueDate, dueTime, selectedReminder) {
        showTimeWarning = false
    }
    var advancedExpanded by remember { mutableStateOf(true) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()
    val customCategoriesSet by settingsManager.customCategories.collectAsState(initial = emptySet())

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Header Row ──────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (initialTask == null) "New Task" else "Edit Task",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // ── Task Title Field ────────────────────────────────
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = {
                    Text(
                        "What needs to be done?",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            // ── Description Field ──────────────────────────────
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                placeholder = {
                    Text(
                        "Description (optional)…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            // ── Priority Pills Header ──────────────────────────
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Flag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "PRIORITY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Priority Pills Row ─────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val priorities = listOf(
                    Triple("Low", "🟢 Low", Color(0xFF66BB6A)),
                    Triple("Medium", "🟡 Medium", Color(0xFFFFB300)),
                    Triple("High", "🔴 High", Color(0xFFE53935))
                )
                priorities.forEach { (key, label, activeColor) ->
                    val selected = selectedPriority == key
                    val textColor = if (selected) {
                        if (key == "Medium") Color.Black else Color.White
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val bg = if (selected) activeColor else MaterialTheme.colorScheme.surface
                    val border = if (selected) activeColor else MaterialTheme.colorScheme.outline

                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(50))
                            .background(bg)
                            .border(BorderStroke(1.5.dp, border), RoundedCornerShape(50))
                            .clickable { selectedPriority = key }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor
                        )
                    }
                }
            }

            // ── Category Pills Header ──────────────────────────
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Label,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "CATEGORY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Category Pills LazyRow ─────────────────────────
            val defaultCategories = listOf("All", "Personal", "Work", "Wishlist")
            val allCategories = (defaultCategories + customCategoriesSet + "+ New").distinct()
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(allCategories) { cat ->
                    val selected = selectedCategory == cat
                    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    val textColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(50))
                            .background(bg)
                            .border(BorderStroke(1.5.dp, border), RoundedCornerShape(50))
                            .clickable {
                                if (cat == "+ New") {
                                    showAddCategoryDialog = true
                                } else {
                                    selectedCategory = cat
                                }
                            }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cat,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor
                        )
                    }
                }
            }

            // ── "More options" Toggle Row ──────────────────────
            val rotateAngle by animateFloatAsState(
                targetValue = if (advancedExpanded) 90f else 0f,
                label = "ChevronRotation"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { advancedExpanded = !advancedExpanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Expand options",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotateAngle)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "More options",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // ── Advanced Section (Collapsible) ─────────────────
            if (advancedExpanded) {
                val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
                val timeFormatter = remember { DateTimeFormatter.ofPattern("hh:mm a") }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = dueDate.format(dateFormatter),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Due Date", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = false,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledTextColor = MaterialTheme.colorScheme.onBackground,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            trailingIcon = {
                                Icon(
                                    Icons.Outlined.DateRange,
                                    contentDescription = "Select Date",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { showDatePicker = true }
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = dueTime.format(timeFormatter),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Due Time", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = false,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledTextColor = MaterialTheme.colorScheme.onBackground,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            trailingIcon = {
                                Icon(
                                    Icons.Outlined.Schedule,
                                    contentDescription = "Select Time",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { showTimePicker = true }
                        )
                    }
                }

                // ── REMINDER Section ─────────────────────────────
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "REMINDER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val reminderOptions = listOf("None", "At time", "10 min before", "1 hour before")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(reminderOptions) { option ->
                        val selected = selectedReminder == option
                        val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                        val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        val textColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(RoundedCornerShape(50))
                                .background(bg)
                                .border(BorderStroke(1.5.dp, border), RoundedCornerShape(50))
                                .clickable { selectedReminder = option }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = textColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

            if (showTimeWarning) {
                Text(
                    text = "Warning: The selected reminder time has already passed. Please choose a future time or select 'None'.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        "Cancel",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            val dueDateMillis = dueDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            val dueTimeMillis = dueTime.atDate(dueDate).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            val reminderTimeMillis: Long? = when (selectedReminder) {
                                "None" -> null
                                "At time" -> dueTimeMillis
                                "10 min before" -> dueTimeMillis - (10 * 60 * 1000L)
                                "1 hour before" -> dueTimeMillis - (60 * 60 * 1000L)
                                else -> null
                            }

                            if (reminderTimeMillis != null && reminderTimeMillis < System.currentTimeMillis()) {
                                showTimeWarning = true
                                return@Button
                            }

                            if (reminderTimeMillis != null) {
                                val formattedReminderTime = SimpleDateFormat("MMM d, yyyy 'at' hh:mm a", Locale.getDefault()).format(
                                    Date(reminderTimeMillis)
                                )
                                Toast.makeText(context, "Reminder scheduled for $formattedReminderTime", Toast.LENGTH_SHORT).show()
                            }

                            val task = (initialTask ?: TodoItem(title = "")).copy(
                                title = title,
                                description = description,
                                priority = selectedPriority,
                                category = selectedCategory,
                                dueDateMillis = dueDateMillis,
                                dueTimeMillis = dueTimeMillis,
                                reminderTimeMillis = reminderTimeMillis
                            )
                            onSave(task)
                        }
                    },
                    enabled = title.isNotBlank(),
                    modifier = Modifier
                        .weight(2f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (initialTask == null) "Save Task" else "Update Task",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dueDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        dueDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = dueTime.hour,
            initialMinute = dueTime.minute
        )

        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dueTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isHourSelected = timePickerState.selection == TimePickerSelectionMode.Hour
                        TextButton(
                            onClick = { timePickerState.selection = TimePickerSelectionMode.Hour }
                        ) {
                            Text(
                                text = "Hours",
                                fontSize = 16.sp,
                                fontWeight = if (isHourSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isHourSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "/",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { timePickerState.selection = TimePickerSelectionMode.Minute }
                        ) {
                            Text(
                                text = "Minutes",
                                fontSize = 16.sp,
                                fontWeight = if (!isHourSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (!isHourSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                        TimePicker(state = timePickerState)
                    }
                }
            }
        )
    }

    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false; newCategoryName = "" },
            title = { Text("Add Custom Category") },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    placeholder = { Text("Category name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = newCategoryName.trim()
                        if (trimmed.isNotEmpty()) {
                            scope.launch {
                                settingsManager.saveCustomCategory(trimmed)
                            }
                            selectedCategory = trimmed
                        }
                        newCategoryName = ""
                        showAddCategoryDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        newCategoryName = ""
                        showAddCategoryDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
