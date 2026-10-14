package com.texasprogram.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.border
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import com.texasprogram.app.ui.ThemeManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.texasprogram.app.ui.LaunchReveal
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.texasprogram.app.data.AppStore
import com.texasprogram.app.data.BackupService
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.TrainingProgramKind
import com.texasprogram.app.ui.AppBackground
import com.texasprogram.app.ui.BenchWaveScreen
import com.texasprogram.app.ui.BodyWeightScreen
import com.texasprogram.app.ui.BroTrackerScreen
import com.texasprogram.app.ui.DayCustomizeScreen
import com.texasprogram.app.ui.DayDetailScreen
import com.texasprogram.app.ui.ExerciseHistoryListScreen
import com.texasprogram.app.ui.ExerciseHistoryScreen
import com.texasprogram.app.ui.GuideScreen
import com.texasprogram.app.ui.KeepScreenOn
import com.texasprogram.app.service.CSVExport
import com.texasprogram.app.service.ReminderScheduler
import com.texasprogram.app.service.RestTimer
import com.texasprogram.app.ui.Motion
import com.texasprogram.app.ui.OnboardingScreen
import com.texasprogram.app.ui.NotesListScreen
import com.texasprogram.app.ui.PlanScreen
import com.texasprogram.app.ui.ProgressScreen
import com.texasprogram.app.ui.SettingsScreen
import com.texasprogram.app.model.LoadPrescription
import com.texasprogram.app.ui.Theme
import com.texasprogram.app.ui.TodayScreen
import com.texasprogram.app.ui.RestTimerBar
import com.texasprogram.app.ui.RebrnRestTimerCard
import com.texasprogram.app.ui.pressable
import com.texasprogram.app.service.WorkoutSessionManager
import com.texasprogram.app.ui.ActiveWorkoutSessionScreen
import com.texasprogram.app.ui.LiquidGlassTabBar
import com.texasprogram.app.ui.MainTabItem
import com.texasprogram.app.ui.MinimizedWorkoutBar
import com.texasprogram.app.ui.WorkoutHubScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // До super: системный экран запуска (чёрный фон и знак) живёт до первого кадра.
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ThemeManager.init(applicationContext)
        WorkoutSessionManager.attach(filesDir)
        handleDeepLink(intent)
        setContent {
            val store = remember { AppStore.get(applicationContext) }
            val timer = remember { RestTimer.get(applicationContext) }
            // Отсчёт в интерфейсе: сам таймер считает от даты окончания,
            // это лишь перерисовка — на границе каждой секунды до конца.
            LaunchedEffect(timer.isRunning) {
                while (timer.isRunning) {
                    timer.tick()
                    val phase = Math.floorMod(timer.endsAtMillis - System.currentTimeMillis(), 1000L)
                    delay(if (phase < 20) 1000L else phase + 5)
                }
            }
            val isDark = Theme.isDark
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !isDark
                        isAppearanceLightNavigationBars = !isDark
                    }
                }
            }
            val colorScheme = if (isDark) {
                darkColorScheme(
                    primary = Theme.accent,
                    background = Theme.base,
                    surface = Theme.surface,
                    onSurface = Theme.textPrimary
                )
            } else {
                lightColorScheme(
                    primary = Theme.accent,
                    background = Theme.base,
                    surface = Theme.surface,
                    onSurface = Theme.textPrimary
                )
            }
            // Заставка — один раз на запуск, не на каждый поворот экрана.
            var revealed by rememberSaveable { mutableStateOf(false) }
            MaterialTheme(colorScheme = colorScheme) {
                Box(Modifier.fillMaxSize()) {
                    AppBackground { AppRoot(store, timer) }
                    if (!revealed) LaunchReveal { revealed = true }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Вернулись из фона: отсчёт сразу догоняет часы, а не ждёт тика.
        RestTimer.get(applicationContext).tick()
    }

    override fun onStop() {
        super.onStop()
        // Уходим в фон — тренировка ложится на диск сразу, а не через задержку.
        WorkoutSessionManager.writeNow()
        // И профили: система может выгрузить свёрнутое приложение в любой момент.
        AppStore.get(applicationContext).flush()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: android.content.Intent?) {
        val data = intent?.data ?: return
        val url = data.toString()
        if (data.host == "timer") {
            val timer = RestTimer.get(applicationContext)
            when (data.lastPathSegment) {
                "minus30" -> timer.add(-30)
                "add30" -> timer.add(30)
                "stop" -> timer.stop()
            }
            return
        }
        if (url.contains("bro")) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                com.texasprogram.app.service.BroTrackerService.getInstance(applicationContext).addBuddy(url)
            }
        }
    }
}

/// Восстановление из файла копии. Общее для первого экрана и настроек:
/// после переустановки копию можно поднять сразу, не создавая пустой профиль.
@Composable
private fun rememberBackupImport(store: AppStore, onResult: (String) -> Unit) =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val context = store.context
        onResult(
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                val archive = BackupService.parse(text.orEmpty())
                val restored = archive.profiles.map { BackupService.profile(it) }
                if (restored.isEmpty()) {
                    "В файле нет профилей"
                } else {
                    store.restoreAll(restored)
                    "Восстановлено профилей: ${restored.size}. Открыт «${restored.first().name}»."
                }
            } catch (e: Exception) {
                "Файл не читается как копия STRAIN"
            }
        )
    }

@Composable
private fun AppRoot(store: AppStore, timer: RestTimer) {
    val active = store.active
    var addingProfile by remember { mutableStateOf(false) }
    var restoreMessage by remember { mutableStateOf<String?>(null) }
    val restoreLauncher = rememberBackupImport(store) { message ->
        restoreMessage = message
        if (store.active != null) addingProfile = false
    }

    if (active == null || addingProfile) {
        val insets = screenPadding(bottomExtra = 24.dp)
        OnboardingScreen(
            canCancel = store.profiles.isNotEmpty(),
            onCancel = { addingProfile = false },
            onSave = { profile ->
                store.add(profile.copy(name = store.suggestedName()))
                addingProfile = false
            },
            onRestore = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
            restoreMessage = restoreMessage,
            contentPadding = insets
        )
        return
    }

    MainScaffold(
        store = store,
        profile = active,
        timer = timer,
        onAddProfile = { addingProfile = true }
    )
}

@Composable
private fun MainScaffold(store: AppStore, profile: ProgramProfile, timer: RestTimer, onAddProfile: () -> Unit) {
    var tab by remember(profile.id) { mutableStateOf(MainTabItem.TODAY) }
    var benchFocus by remember { mutableStateOf<Int?>(null) }
    var showPlan by remember { mutableStateOf(false) }
    var dayDetail by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var customizing by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var entryTarget by remember { mutableStateOf<com.texasprogram.app.ui.SetEntryTarget?>(null) }
    // Разделы поверх вкладок: список движений, история одного и заметки.
    var showHistoryList by remember { mutableStateOf(false) }
    var historyFor by remember { mutableStateOf<String?>(null) }
    var showNotes by remember { mutableStateOf(false) }
    var showBodyWeight by remember { mutableStateOf(false) }

    val isUpperLower = profile.programKind == TrainingProgramKind.UPPER_LOWER

    val openBench: ((Int) -> Unit)? = if (isUpperLower) {
        { session ->
            benchFocus = session
            dayDetail = null
        }
    } else null

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val broService = remember { com.texasprogram.app.service.BroTrackerService.getInstance(context) }

    fun syncProfileInBackground() {
        val updated = store.active ?: return
        scope.launch(Dispatchers.IO) {
            broService.syncMyProfile(updated)
        }
    }

    var backupMessage by remember { mutableStateOf<String?>(null) }

    // Системные диалоги файлов: приложению не нужны разрешения на хранилище.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupMessage = try {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(BackupService.export(store.profiles).toByteArray())
            }
            "Копия сохранена"
        } catch (e: Exception) {
            "Не удалось сохранить копию"
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupMessage = try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(CSVExport.bytes(store.profiles)) }
            "Таблица сохранена"
        } catch (e: Exception) {
            "Не удалось собрать таблицу"
        }
    }

    val importLauncher = rememberBackupImport(store) { backupMessage = it }

    val contentPadding = screenPadding(bottomExtra = 110.dp)

    // Поднимаем тренировку, прерванную выгрузкой приложения, — свёрнутой.
    LaunchedEffect(profile.id) {
        // Тренировка другого профиля в этот не пишет.
        WorkoutSessionManager.detachIfOtherProfile(profile)
        WorkoutSessionManager.restoreIfNeeded(profile)
    }

    // Профиль на момент нажатия, а не на момент последней перерисовки:
    // два быстрых нажатия по подходам не затирают друг друга.
    val freshProfile = remember(store) { { store.active } }
    val updateProfile = remember(store) { { updated: ProgramProfile -> store.update(updated.id) { updated } } }

    // На Android 13+ уведомления (конец отдыха, отсчёт в шторке) нужно разрешить.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    fun startWorkout(workout: com.texasprogram.app.service.ScheduledWorkout) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
        }
        WorkoutSessionManager.requestStart(workout, store.active ?: profile)
    }

    BackHandler(
        enabled = WorkoutSessionManager.isPresented || showPlan || benchFocus != null ||
            dayDetail != null || customizing != null || showHistoryList || historyFor != null || showNotes ||
            showBodyWeight
    ) {
        when {
            WorkoutSessionManager.isPresented -> WorkoutSessionManager.minimize()
            showPlan -> showPlan = false
            benchFocus != null -> benchFocus = null
            customizing != null -> customizing = null
            historyFor != null -> historyFor = null
            showHistoryList -> showHistoryList = false
            showNotes -> showNotes = false
            showBodyWeight -> showBodyWeight = false
            dayDetail != null -> dayDetail = null
        }
    }

    // Телефон лежит на лавке между подходами — гасить экран тут незачем.
    KeepScreenOn(profile.keepScreenOn)

    Box(Modifier.fillMaxSize()) {
        // Как в iOS-версии: новый раздел въезжает со стороны, куда ведёшь
        // по панели, старый чуть уменьшается и гаснет.
        val tabShift = with(LocalDensity.current) { 36.dp.roundToPx() }
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally(Motion.sheet()) { if (forward) tabShift else -tabShift } + fadeIn(tween(220))) togetherWith
                    (scaleOut(tween(200), targetScale = 0.97f) + fadeOut(tween(160))) using
                    SizeTransform(clip = false)
            },
            label = "tab"
        ) { current ->
            when (current) {
                MainTabItem.TODAY -> TodayScreen(
                    profile = profile,
                    timer = timer,
                    onToggleDay = { week, day ->
                        store.updateActive { it.toggleCompleted(week, day) }
                        syncProfileInBackground()
                    },
                    onToggleSet = { workout, exercise, dot ->
                        val adds = profile.willAddSet(workout.week, workout.day.number, exercise, dot)
                        store.updateActive { it.toggleSet(workout.week, workout.day.number, exercise, dot) }
                        if (adds) {
                            val updated = store.active
                            if (updated != null && updated.allSetsDone(workout)) {
                                timer.stop()
                                store.updateActive { it.toggleCompleted(workout.week, workout.day.number) }
                                syncProfileInBackground()
                            } else {
                                val completedCount = updated?.completedSets(workout.week, workout.day.number, exercise) ?: 0
                                val weightVal = (exercise.load as? LoadPrescription.Kilograms)?.value
                                val weightStr = if (weightVal != null) "${com.texasprogram.app.model.formatWeight(weightVal)} кг" else ""
                                val details = if (weightStr.isNotEmpty()) "$weightStr × ${exercise.reps}" else "${exercise.reps} повт."
                                timer.start(
                                    seconds = profile.defaultRestSeconds.toLong(),
                                    exerciseName = exercise.name,
                                    setDetails = details,
                                    completedSets = completedCount,
                                    totalSets = exercise.sets
                                )
                            }
                        }
                    },
                    onSelectRest = { seconds -> store.updateActive { it.copy(defaultRestSeconds = seconds) } },
                    onOpenBench = openBench,
                    onOpenDay = { week, day -> dayDetail = week to day },
                    onCustomize = { week, day -> customizing = week to day },
                    onUpdateProfile = { updated -> store.update(updated.id) { updated } },
                    onHoldSet = { workout, exercise, dot ->
                        entryTarget = com.texasprogram.app.ui.SetEntryTarget(
                            workout.week, workout.day.number, exercise, dot
                        )
                    },
                    onOpenHistory = { name -> historyFor = name },
                    onOpenBroTracker = { tab = MainTabItem.COMMUNITY },
                    onSettings = { tab = MainTabItem.PROFILE },
                    onStartWorkout = { workout -> startWorkout(workout) },
                    contentPadding = contentPadding
                )
                MainTabItem.WORKOUT -> WorkoutHubScreen(
                    profile = profile,
                    contentPadding = contentPadding,
                    onStartWorkout = { workout -> startWorkout(workout) },
                    onOpenPlan = { showPlan = true },
                    onUpdateProfile = { updated -> store.update(updated.id) { updated } }
                )
                MainTabItem.PROGRESS -> ProgressScreen(
                    profile = profile,
                    contentPadding = contentPadding,
                    onOpenHistory = { showHistoryList = true },
                    onOpenNotes = { showNotes = true },
                    onOpenBodyWeight = { showBodyWeight = true },
                    onUpdate = { updated -> store.update(updated.id) { updated } }
                )
                MainTabItem.COMMUNITY -> BroTrackerScreen(
                    profile = profile,
                    onCopyProgram = { newProfile -> store.add(newProfile) },
                    onUpdateProfile = { updated -> store.update(updated.id) { updated } },
                    onClose = { tab = MainTabItem.TODAY },
                    contentPadding = contentPadding
                )
                MainTabItem.PROFILE -> SettingsScreen(
                    profile = profile,
                    profiles = store.profiles,
                    onUpdate = { updated -> store.update(updated.id) { updated } },
                    onSwitchProfile = { id -> store.setActive(id) },
                    onAddProfile = { onAddProfile() },
                    onDeleteProfile = { store.delete(profile.id) },
                    onExportBackup = { exportLauncher.launch(BackupService.FILE_NAME) },
                    onImportBackup = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    onExportCsv = { csvLauncher.launch(CSVExport.FILE_NAME) },
                    backupMessage = backupMessage,
                    onClose = { tab = MainTabItem.TODAY },
                    contentPadding = contentPadding
                )
            }
        }

        Column(Modifier.align(Alignment.BottomCenter)) {
            if (WorkoutSessionManager.isRunning && !WorkoutSessionManager.isPresented) {
                MinimizedWorkoutBar()
            }
            // Карточка сама прячется и показывается с анимацией.
            RebrnRestTimerCard(timer, Modifier.padding(bottom = 8.dp))
            LiquidGlassTabBar(
                selectedTab = tab,
                onTabSelected = { tab = it }
            )
        }

        // Детали дня и настройки — поверх вкладок, как модальные экраны в iOS.
        AnimatedContent(
            targetState = dayDetail,
            transitionSpec = {
                if (targetState != null) {
                    (slideInHorizontally(tween(300)) { it / 2 } + fadeIn(tween(200))) togetherWith fadeOut(tween(200))
                } else {
                    fadeIn(tween(200)) togetherWith (slideOutHorizontally(tween(300)) { it / 2 } + fadeOut(tween(200)))
                }
            },
            label = "detail"
        ) { detail ->
            if (detail != null) {
                val (week, dayNumber) = detail
                val day = profile.workoutPlan.weeks
                    .firstOrNull { it.number == week }?.days?.firstOrNull { it.number == dayNumber }
                if (day != null) {
                    val scheduled = profile.schedule().allPending
                        .firstOrNull { it.week == week && it.day.number == dayNumber }
                    Box(Modifier.fillMaxSize().background(Theme.base)) {
                        DayDetailScreen(
                            profile = profile,
                            week = week,
                            day = day,
                            scheduled = scheduled,
                            onToggleDay = { w, d ->
                                store.updateActive { it.toggleCompleted(w, d) }
                                syncProfileInBackground()
                            },
                            onToggleSet = { exercise, dot ->
                                val adds = profile.willAddSet(week, dayNumber, exercise, dot)
                                store.updateActive { it.toggleSet(week, dayNumber, exercise, dot) }
                                if (adds) {
                                    // С названием и подходами — как на «Главном»: в шторке
                                    // и на карточке отдыха видно, после чего отдыхаешь.
                                    val updated = store.active
                                    timer.start(
                                        seconds = profile.defaultRestSeconds.toLong(),
                                        exerciseName = exercise.name,
                                        completedSets = updated?.completedSets(week, dayNumber, exercise) ?: 0,
                                        totalSets = exercise.sets
                                    )
                                }
                            },
                            onOpenBench = openBench,
                            onCustomize = { customizing = week to dayNumber },
                            onUpdateProfile = { updated -> store.update(updated.id) { updated } },
                            onHoldSet = { exercise, dot ->
                                entryTarget = com.texasprogram.app.ui.SetEntryTarget(week, dayNumber, exercise, dot)
                            },
                            onOpenHistory = { name -> historyFor = name },
                            contentPadding = screenPadding(bottomExtra = 32.dp)
                        )
                        CloseButton(Modifier.align(Alignment.TopEnd)) { dayDetail = null }
                    }
                }
            }
        }

        entryTarget?.let { target ->
            com.texasprogram.app.ui.SetEntryDialog(
                profile = profile,
                target = target,
                onDismiss = { entryTarget = null },
                onUpdate = { updated -> store.update(updated.id) { updated } }
            )
        }

        AnimatedContent(
            targetState = customizing,
            transitionSpec = {
                if (targetState != null) {
                    (slideInVertically(tween(320)) { it / 3 } + fadeIn(tween(220))) togetherWith fadeOut(tween(180))
                } else {
                    fadeIn(tween(180)) togetherWith (slideOutVertically(tween(320)) { it / 3 } + fadeOut(tween(200)))
                }
            },
            label = "customize"
        ) { target ->
            if (target != null) {
                val (week, dayNumber) = target
                Box(Modifier.fillMaxSize().background(Theme.base)) {
                    DayCustomizeScreen(
                        profile = profile,
                        week = week,
                        dayNumber = dayNumber,
                        onUpdate = { updated -> store.update(updated.id) { updated } },
                        contentPadding = screenPadding(bottomExtra = 32.dp)
                    )
                    CloseButton(Modifier.align(Alignment.TopEnd)) { customizing = null }
                }
            }
        }

        // Модальный экран плана тренировок
        AnimatedContent(
            targetState = showPlan,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(160)) },
            label = "plan"
        ) { visible ->
            if (visible) {
                Box(Modifier.fillMaxSize().background(Theme.base)) {
                    PlanScreen(
                        profile = profile,
                        onOpenDay = { week, day -> dayDetail = week to day },
                        onSetCurrent = { week, day ->
                            store.updateActive { it.setCurrentWorkout(week, day) }
                        },
                        contentPadding = screenPadding(bottomExtra = 32.dp)
                    )
                    CloseButton(Modifier.align(Alignment.TopEnd)) { showPlan = false }
                }
            }
        }

        // Модальный экран жимовой волны
        AnimatedContent(
            targetState = benchFocus,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(160)) },
            label = "benchFocus"
        ) { session ->
            if (session != null) {
                Box(Modifier.fillMaxSize().background(Theme.base)) {
                    BenchWaveScreen(
                        profile = profile,
                        focused = session,
                        onFocusHandled = { benchFocus = null },
                        onToggleBench = { index ->
                            store.updateActive { it.toggleBenchCompleted(index) }
                        },
                        onSetCurrentBench = { index ->
                            store.updateActive { it.setCurrentBenchSession(index) }
                        },
                        contentPadding = screenPadding(bottomExtra = 32.dp)
                    )
                    CloseButton(Modifier.align(Alignment.TopEnd)) { benchFocus = null }
                }
            }
        }

        // Полноэкранный режим активной тренировки
        AnimatedContent(
            targetState = WorkoutSessionManager.isPresented,
            transitionSpec = {
                (slideInVertically(tween(350)) { it } + fadeIn(tween(250))) togetherWith
                        (slideOutVertically(tween(300)) { it } + fadeOut(tween(200)))
            },
            label = "activeWorkout"
        ) { presented ->
            if (presented) {
                ActiveWorkoutSessionScreen(
                    profile = freshProfile,
                    restTimer = timer,
                    onUpdateProfile = updateProfile
                )
            }
        }

        // Просят начать другую тренировку, пока идёт эта: не подменяем молча.
        WorkoutSessionManager.pendingWorkout?.let { next ->
            val current = WorkoutSessionManager.activeWorkout
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { WorkoutSessionManager.cancelReplace() },
                containerColor = Theme.dialog,
                title = { Text("Идёт другая тренировка", color = Theme.textPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Сейчас идёт «${current?.fullTitle.orEmpty()}». Начать «${next.fullTitle}»? Отметки текущей тренировки будут отменены.",
                        color = Theme.textSecondary
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        WorkoutSessionManager.confirmReplace(store.active ?: profile, updateProfile)
                    }) { Text("Начать новую", color = Theme.record, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { WorkoutSessionManager.cancelReplace() }) {
                        Text("Продолжить текущую", color = Theme.accent)
                    }
                }
            )
        }

        // Список движений, история одного и заметки — тоже поверх вкладок.
        AnimatedContent(
            targetState = showHistoryList,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(160)) },
            label = "historyList"
        ) { visible ->
            if (visible) {
                Box(Modifier.fillMaxSize().background(Theme.base)) {
                    ExerciseHistoryListScreen(
                        profile = profile,
                        onOpen = { name -> historyFor = name },
                        onUpdate = { updated -> store.update(updated.id) { updated } },
                        contentPadding = screenPadding(bottomExtra = 32.dp)
                    )
                    CloseButton(Modifier.align(Alignment.TopEnd)) { showHistoryList = false }
                }
            }
        }

        AnimatedContent(
            targetState = historyFor,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(160)) },
            label = "history"
        ) { name ->
            if (name != null) {
                Box(Modifier.fillMaxSize().background(Theme.base)) {
                    ExerciseHistoryScreen(
                        profile = profile,
                        name = name,
                        contentPadding = screenPadding(bottomExtra = 32.dp)
                    )
                    CloseButton(Modifier.align(Alignment.TopEnd)) { historyFor = null }
                }
            }
        }

        AnimatedContent(
            targetState = showNotes,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(160)) },
            label = "notes"
        ) { visible ->
            if (visible) {
                Box(Modifier.fillMaxSize().background(Theme.base)) {
                    NotesListScreen(
                        profile = profile,
                        onUpdate = { updated -> store.update(updated.id) { updated } },
                        contentPadding = screenPadding(bottomExtra = 32.dp)
                    )
                    CloseButton(Modifier.align(Alignment.TopEnd)) { showNotes = false }
                }
            }
        }

        AnimatedContent(
            targetState = showBodyWeight,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(160)) },
            label = "bodyWeight"
        ) { visible ->
            if (visible) {
                Box(Modifier.fillMaxSize().background(Theme.base)) {
                    BodyWeightScreen(
                        profile = profile,
                        onUpdate = { updated -> store.update(updated.id) { updated } },
                        contentPadding = screenPadding(bottomExtra = 32.dp)
                    )
                    CloseButton(Modifier.align(Alignment.TopEnd)) { showBodyWeight = false }
                }
            }
        }
    }

    // Напоминания пересобираем при запуске и на каждое изменение настроек:
    // будильники не переживают перезагрузку телефона.
    LaunchedEffect(
        profile.reminderEnabled,
        profile.reminderHour,
        profile.reminderMinute,
        profile.scheduleWeekdays
    ) {
        ReminderScheduler.reschedule(context, profile)
    }
}


@Composable
private fun CloseButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier
            .padding(top = topInset + 12.dp, end = 16.dp)
            .size(40.dp)
            .background(Theme.surfaceSoft, androidx.compose.foundation.shape.CircleShape)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("✕", color = Theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/// Отступы под системные панели плюс поля экрана.
@Composable
private fun screenPadding(bottomExtra: androidx.compose.ui.unit.Dp): PaddingValues {
    val insets = WindowInsets.systemBars.asPaddingValues()
    return PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = insets.calculateTopPadding() + 8.dp,
        bottom = insets.calculateBottomPadding() + bottomExtra
    )
}
