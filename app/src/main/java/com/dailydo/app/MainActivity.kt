package com.dailydo.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import kotlin.math.ceil

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

fun getDueText(task: TodoItem): String? {
    if (task.isDone) return null
    val due = task.dueTimeMillis ?: return null
    val now = System.currentTimeMillis()
    val reminder = task.reminderTimeMillis

    // If reminder exists, only show after the reminder time triggers
    if (reminder != null && now < reminder) return null

    // If reminder is null (silent), only show when the task is actually due (or 1 min before)
    if (reminder == null && now < due - 60_000L) return null

    val diff = due - now
    return when {
        diff <= 0L -> "Due now"
        diff < 60_000L -> "Due in < 1 min"
        diff < 60 * 60 * 1000L -> "Due in ${ceil(diff / (1000.0 * 60.0)).toInt()} min"
        else -> "Due in ${ceil(diff / (1000.0 * 60.0 * 60.0)).toInt()} h"
    }
}

class MainActivity : ComponentActivity() {
    private var editTaskIdState = mutableStateOf(-1)
    private var openEditState = mutableStateOf(false)
    private var openTasksState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Read extras from notification click
        editTaskIdState.value = intent.getIntExtra("EXTRA_TASK_ID", -1)
        openEditState.value = intent.getBooleanExtra("EXTRA_OPEN_EDIT", false)
        openTasksState.value = intent.getBooleanExtra("EXTRA_OPEN_TASKS", false)

        val dao = AppDatabase.getDatabase(this).todoDao()
        val settingsManager = SettingsManager(this)

        setContent {
            val displayMode by settingsManager.displayMode.collectAsState(initial = "Dark")
            val themeId by settingsManager.themeId.collectAsState(initial = 1)
            val privacyAccepted by settingsManager.privacyAccepted.collectAsState(initial = null)
            var showCustomSplash by remember { mutableStateOf(true) }

            TodoAppTheme(
                displayMode = displayMode,
                themeId = themeId
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) {
                    when {
                        showCustomSplash -> {
                            CustomSplashScreen(onFinished = { showCustomSplash = false })
                        }
                        privacyAccepted == null -> {
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                        }
                        privacyAccepted == false -> {
                            PrivacyPolicyScreen(
                                settingsManager = settingsManager,
                                onAccepted = {
                                    // privacyAccepted flow updates automatically via settingsManager
                                }
                            )
                        }
                        else -> {
                            val vm: TodoViewModel = viewModel(
                                factory = TodoViewModelFactory(dao)
                            )
                            MainTabs(
                                vm = vm,
                                initialEditTaskId = editTaskIdState.value,
                                initialOpenEdit = openEditState.value,
                                initialOpenTasks = openTasksState.value,
                                settingsManager = settingsManager,
                                currentDisplayMode = displayMode,
                                currentThemeId = themeId
                            )
                        }
                    }
                }
            }
        }
    }

    // Handle notification clicks when app is already open
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newTaskId = intent.getIntExtra("EXTRA_TASK_ID", -1)
        if (newTaskId != -1) {
            editTaskIdState.value = newTaskId
            openEditState.value = intent.getBooleanExtra("EXTRA_OPEN_EDIT", false)
            openTasksState.value = intent.getBooleanExtra("EXTRA_OPEN_TASKS", false)
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
    initialOpenEdit: Boolean = false,
    initialOpenTasks: Boolean = false,
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

    var showCompletedScreen by remember { mutableStateOf(false) }

    LaunchedEffect(initialOpenTasks) {
        if (initialOpenTasks) {
            selectedTab = 0
            showCompletedScreen = false
        }
    }

    // Handle Notification Edit click or Postpone action
    var handledInitialEdit by remember { mutableStateOf(false) }
    LaunchedEffect(initialEditTaskId, initialOpenEdit) {
        if (initialEditTaskId != -1 && !handledInitialEdit) {
            handledInitialEdit = true
            if (initialOpenEdit) {
                val task = AppDatabase.getDatabase(context).todoDao().getById(initialEditTaskId)
                if (task != null) {
                    editingTaskGlobal = task
                }
            }
        }
    }

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

    // 10-second ticker for live countdown updates
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000L)
            nowMillis = System.currentTimeMillis()
        }
    }


    val notifPromptShown by settingsManager.notificationPromptShown.collectAsState(initial = false)
    val batteryPromptDismissed by settingsManager.batteryPromptDismissed.collectAsState(initial = false)

    var showNotifExplanationDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission && !notifPromptShown) {
                showNotifExplanationDialog = true
            }
        }
    }

    if (showNotifExplanationDialog) {
        AlertDialog(
            onDismissRequest = {
                scope.launch { settingsManager.setNotificationPromptShown(true) }
                showNotifExplanationDialog = false
            },
            title = { Text("Enable Notifications") },
            text = { Text("DailyDo needs notification permission to remind you of your scheduled tasks on time.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { settingsManager.setNotificationPromptShown(true) }
                        showNotifExplanationDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                ) {
                    Text("Allow")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        scope.launch { settingsManager.setNotificationPromptShown(true) }
                        showNotifExplanationDialog = false
                    }
                ) {
                    Text("Not Now")
                }
            }
        )
    }

    val isIgnoringBattery = remember { BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context) }
    if (!isIgnoringBattery && !batteryPromptDismissed) {
        AlertDialog(
            onDismissRequest = {
                scope.launch { settingsManager.setBatteryPromptDismissed(true) }
            },
            title = { Text("Background Activity Required") },
            text = { Text("To ensure reminders work when the app is closed, please allow background activity.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        BatteryOptimizationHelper.openManufacturerSettings(context)
                        scope.launch { settingsManager.setBatteryPromptDismissed(true) }
                    }
                ) {
                    Text("Fix Now")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        scope.launch { settingsManager.setBatteryPromptDismissed(true) }
                    }
                ) {
                    Text("Dismiss")
                }
            }
        )
    }

    val hasRuntimePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var systemNotifsEnabled by remember {
        mutableStateOf(NotificationPermissionHelper.areNotificationsFullyEnabled(context))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                systemNotifsEnabled =
                    NotificationPermissionHelper.areNotificationsFullyEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val showSystemNotifBanner = hasRuntimePermission && !systemNotifsEnabled

    var selectedCategory by remember { mutableStateOf("All") }
    var selectedPriority by remember { mutableStateOf("All") }
    var showPrevious by remember { mutableStateOf(true) }
    var showCompleted by remember { mutableStateOf(true) }

    var showCategorySheet by remember { mutableStateOf(false) }
    var showPrioritySheet by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var categoryToDelete by remember { mutableStateOf<String?>(null) }

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
        }

        // ── System Notification Warning Banner ───────────────────
        if (showSystemNotifBanner) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable {
                        NotificationPermissionHelper.openNotificationSettings(context)
                    },
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.NotificationsOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Notifications are off in system settings — tap to fix",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
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
                                category = item.category,
                                done = item.isDone,
                                dueText = getDueText(item),
                                hasReminder = item.reminderTimeMillis != null,
                                onToggle = {
                                    val updated = item.copy(isDone = !item.isDone)
                                    vm.toggleDone(item)
                                    if (updated.isDone) {
                                        TestReminderHelper.cancelReminder(context, updated.id)
                                    } else {
                                        TestReminderHelper.scheduleReminder(context, updated)
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
                                category = item.category,
                                done = item.isDone,
                                dueText = null,
                                hasReminder = item.reminderTimeMillis != null,
                                onToggle = {
                                    val updated = item.copy(isDone = !item.isDone)
                                    vm.toggleDone(item)
                                    if (updated.isDone) {
                                        TestReminderHelper.cancelReminder(context, updated.id)
                                    } else {
                                        TestReminderHelper.scheduleReminder(context, updated)
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
    category: String = "",
    done: Boolean,
    dueText: String? = null,
    hasReminder: Boolean = true,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dueNowAnim")

    val rotation by infiniteTransition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotation"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .then(if (dueText != null) Modifier.graphicsLayer { rotationZ = rotation } else Modifier)
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

            if (dueText != null) {
                Text(
                    text = dueText,
                    color = Color(0xFFE53935).copy(alpha = glowAlpha),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, end = 8.dp)
                )
            } else {
                Spacer(Modifier.width(8.dp))
            }

            // Right-side grouping
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                
                // 1. Golden Silent Bell (Left)
                if (!hasReminder) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFC107).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFFFFC107).copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsOff,
                            contentDescription = "Silent",
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // 2. Category Pill (Right)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            RoundedCornerShape(50)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = category,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.3.sp
                    )
                }
            }

            // 3. Delete Icon (Far Right)
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
// PRIVACY POLICY SCREEN COMPOSABLE
// ═════════════════════════════════════════════════════════════

@Composable
fun PrivacyPolicyScreen(
    settingsManager: SettingsManager,
    onAccepted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val privacyPolicyUrl = "https://github.com/Fitoxima/DailyDo/blob/main/PRIVACY_POLICY.md"
    val warningColor = Color(0xFFFFC107)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // Top Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Privacy Policy",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = warningColor
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Before using DailyDo, please review our Privacy Policy.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }

        // Scrollable Policy Card
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 4.dp)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left yellow border
                    Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(warningColor))
                    
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "DailyDo is a local to-do app. We do not collect, store, or transmit any personal data to external servers.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                        Text(
                            text = "All your tasks, categories, and settings are stored locally on your device using Room Database and DataStore.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                        Text(
                            text = "The app uses notifications and AlarmManager to remind you of tasks. These permissions are used solely for the app's core functionality.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                        Text(
                            text = "For the full policy, tap the link below.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }

        // Bottom Area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TextButton(onClick = { uriHandler.openUri(privacyPolicyUrl) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("View Full Policy on GitHub", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = warningColor)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = warningColor, modifier = Modifier.size(16.dp))
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            Button(
                onClick = {
                    scope.launch {
                        settingsManager.setPrivacyAccepted(true)
                        onAccepted()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = warningColor,
                    contentColor = Color.Black
                )
            ) {
                Text("I Agree", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
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
            initialTask == null -> "At time"
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
                        text = "REMINDER IN ADVANCE",
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
            initialMinute = dueTime.minute,
            is24Hour = false
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

// ═════════════════════════════════════════════════════════════
// CUSTOM SPLASH SCREEN COMPOSABLE
// ═════════════════════════════════════════════════════════════

@Composable
fun CustomSplashScreen(onFinished: () -> Unit) {
    // Animation States
    var startAnim by remember { mutableStateOf(false) }

    // Icon Animation (Fades and scales in)
    val iconAlpha by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = tween(400, easing = LinearEasing),
        label = "iconAlpha"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0.9f,
        animationSpec = tween(400, easing = LinearEasing),
        label = "iconScale"
    )

    // Text Animation (Slides up)
    val textAlpha by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = tween(500, delayMillis = 400, easing = LinearEasing),
        label = "textAlpha"
    )
    val textOffset by animateFloatAsState(
        targetValue = if (startAnim) 0f else 30f,
        animationSpec = tween(500, delayMillis = 400, easing = LinearEasing),
        label = "textOffset"
    )

    // Trail Animation (Draws left to right)
    val trailProgress by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = tween(1400, delayMillis = 900, easing = LinearEasing),
        label = "trailProgress"
    )

    // Trigger animations and completion
    LaunchedEffect(Unit) {
        startAnim = true
        delay(2500) // Wait for all animations to finish
        onFinished()
    }

    // UI Layout
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.offset(y = (-20).dp) // Slight upward shift looks better visually
        ) {
            // 1. App Icon
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .graphicsLayer {
                        alpha = iconAlpha
                        scaleX = iconScale
                        scaleY = iconScale
                    }
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White)
            ) {
                // Re-use the launcher icon as a foreground image
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground_custom),
                    contentDescription = "App Icon",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(22.dp))
                        .graphicsLayer { scaleX = 1.4f; scaleY = 1.4f }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 2. DailyDo Text
            Text(
                text = "DailyDo",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.graphicsLayer {
                    alpha = textAlpha
                    translationY = textOffset
                }
            )

            Spacer(modifier = Modifier.height(40.dp))

            // 3. Dotted Trail (Using Row for 100% safe cross-device rendering)
            val dotSizes = listOf(3.dp, 6.dp, 4.dp, 7.dp, 5.dp, 3.dp, 6.dp, 4.dp, 8.dp, 5.dp, 4.dp, 6.dp, 3.dp, 5.dp, 4.dp)
            val totalDots = dotSizes.size

            Box(
                modifier = Modifier
                    .width(240.dp)
                    .height(24.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    dotSizes.forEachIndexed { index, size ->
                        val dotProgress = (trailProgress * totalDots) - index
                        val currentAlpha = dotProgress.coerceIn(0f, 1f)

                        Box(
                            modifier = Modifier
                                .size(size)
                                .graphicsLayer { alpha = currentAlpha }
                                .background(Color(0xFF9E9E9E), CircleShape)
                        )
                    }
                }

                // Moving Pencil
                val pencilX = (trailProgress * 240.dp.value)
                Box(
                    modifier = Modifier
                        .offset(x = pencilX.dp, y = (-10).dp)
                        .width(8.dp)
                        .height(36.dp)
                        .graphicsLayer { alpha = if (trailProgress > 0f && trailProgress < 1f) 1f else 0f }
                ) {
                    // Simplified Pencil Shape
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFFFC107), RoundedCornerShape(2.dp))
                    ) {
                        // Pink Eraser
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(Color(0xFFFF8A80), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        )
                        // Grey Band
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .offset(y = 6.dp)
                                .background(Color(0xFFB0BEC5))
                        )
                    }
                }
            }
        }
    }
}
