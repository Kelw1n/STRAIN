package com.texasprogram.app

import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import android.view.ViewTreeObserver
import android.os.SystemClock
import androidx.compose.runtime.rememberUpdatedState
import com.texasprogram.app.ui.popIn
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
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
import com.texasprogram.app.ui.TogetherInviteBanner
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.snapshotFlow
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
import com.texasprogram.app.ui.CommunityScreen
import com.texasprogram.app.ui.FeedScreen
import com.texasprogram.app.ui.ProfileTabScreen
import com.texasprogram.app.service.SocialService
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
import androidx.lifecycle.lifecycleScope
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
        SocialService.init(applicationContext)
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
                    // Белое на акценте: иначе у включённых переключателей тёмно-фиолетовый кружок.
                    onPrimary = Color.White,
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
        // Неотправленные посты — в путь, как только приложение снова на экране.
        SocialService.flushOutbox()
        lifecycleScope.launch { SocialService.refreshUnread() }
        // Вышла ли новая версия; вернулись из настроек с разрешением — ставим дальше.
        com.texasprogram.app.service.AppUpdater.onResume(this)
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
        if (data.host == "timer") {
            val timer = RestTimer.get(applicationContext)
            when (data.lastPathSegment) {
                "minus30" -> timer.add(-30)
                "add30" -> timer.add(30)
                "stop" -> timer.stop()
            }
            return
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
                SocialService.adoptBackupToken(archive.socialToken)
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
    // Прежний экран «Сегодня» — из «Тренировки»; настройки — за шестерёнкой в профиле.
    var showToday by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val isUpperLower = profile.programKind == TrainingProgramKind.UPPER_LOWER

    val openBench: ((Int) -> Unit)? = if (isUpperLower) {
        { session ->
            benchFocus = session
            dayDetail = null
        }
    } else null

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    /// Программа, медали и вес — в профиль ленты, когда что-то поменялось.
    fun syncProfileInBackground() {
        store.active?.let { SocialService.scheduleSync(it) }
    }

    var backupMessage by remember { mutableStateOf<String?>(null) }

    // Системные диалоги файлов: приложению не нужны разрешения на хранилище.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupMessage = try {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(BackupService.export(store.profiles, SocialService.token).toByteArray())
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

    // Лента: неотправленные посты — в путь, программа, медали и вес — в профиль.
    LaunchedEffect(Unit) {
        SocialService.flushOutbox()
        SocialService.refreshMe()
        // Непрочитанные сообщения и активность — сразу и потом раз в минуту.
        // Идущая тренировка заодно напоминает о себе, чтобы статус у друзей не протух.
        while (true) {
            SocialService.refreshUnread()
            SocialService.reportLive(WorkoutSessionManager.liveStatus)
            delay(60_000)
        }
    }
    // Друзья видят, что идёт тренировка и какой подход.
    LaunchedEffect(Unit) {
        snapshotFlow { WorkoutSessionManager.liveStatus }.collect { SocialService.reportLive(it) }
    }
    LaunchedEffect(profile.completionLog, profile.bodyWeightLog, SocialService.isSignedIn) {
        SocialService.scheduleSync(profile)
    }

    // Профиль на момент нажатия, а не на момент последней перерисовки:
    // два быстрых нажатия по подходам не затирают друг друга.
    val freshProfile = remember(store) { { store.active } }
    val updateProfile = remember(store) { { updated: ProgramProfile -> store.update(updated.id) { updated } } }

    // На Android 13+ уведомления (конец отдыха, отсчёт в шторке) нужно разрешить.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    // Одна и та же функция между перерисовками: иначе вкладка «Тренировка»,
    // которой она передаётся, пересобиралась бы на каждое изменение экрана.
    val currentProfile by rememberUpdatedState(profile)
    val startWorkout = remember(store, notificationPermission) {
        { workout: com.texasprogram.app.service.ScheduledWorkout ->
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                runCatching { notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
            }
            WorkoutSessionManager.requestStart(workout, store.active ?: currentProfile)
        }
    }

    BackHandler(
        enabled = WorkoutSessionManager.isPresented || showPlan || benchFocus != null ||
            dayDetail != null || customizing != null || showHistoryList || historyFor != null || showNotes ||
            showBodyWeight || showToday || showSettings
    ) {
        when {
            WorkoutSessionManager.isPresented -> WorkoutSessionManager.minimize()
            // Правка и день открываются поверх плана — «Назад» сначала закрывает их.
            customizing != null -> customizing = null
            benchFocus != null -> benchFocus = null
            dayDetail != null -> dayDetail = null
            showPlan -> showPlan = false
            historyFor != null -> historyFor = null
            showHistoryList -> showHistoryList = false
            showNotes -> showNotes = false
            showBodyWeight -> showBodyWeight = false
            showSettings -> showSettings = false
            showToday -> showToday = false
        }
    }

    // Телефон лежит на лавке между подходами — гасить экран тут незачем.
    KeepScreenOn(profile.keepScreenOn)

    Box(Modifier.fillMaxSize()) {
        // Открытые вкладки живут дальше: при возврате экран не собирается заново —
        // без пустого кадра, перезагрузки ленты и рывка анимации.
        // Пока поверх вкладок открыт полноэкранный раздел, вкладку под ним
        // не пересчитываем — её не видно, а отметки подходов идут часто.
        val covered = WorkoutSessionManager.isPresented || showPlan || dayDetail != null || customizing != null ||
            benchFocus != null || showHistoryList || historyFor != null || showNotes || showBodyWeight ||
            showToday || showSettings
        // Вкладка передаётся лямбдой: её смена перестраивает только KeepAliveTabs,
        // а не весь этот экран — и открываемая вкладка не пересобирается заново.
        KeepAliveTabs(selectedTab = { tab }, frozen = covered) { current ->
            when (current) {
                MainTabItem.TODAY -> FeedScreen(
                    profile = profile,
                    contentPadding = contentPadding,
                    onFindPeople = { tab = MainTabItem.COMMUNITY },
                    onCreateProfile = { tab = MainTabItem.PROFILE }
                )
                MainTabItem.WORKOUT -> WorkoutHubScreen(
                    profile = profile,
                    contentPadding = contentPadding,
                    onStartWorkout = { workout -> startWorkout(workout) },
                    onOpenPlan = { showPlan = true },
                    onOpenToday = { showToday = true },
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
                MainTabItem.COMMUNITY -> CommunityScreen(
                    profile = profile,
                    contentPadding = contentPadding,
                    onCreateProfile = { tab = MainTabItem.PROFILE }
                )
                MainTabItem.PROFILE -> ProfileTabScreen(
                    profile = profile,
                    contentPadding = contentPadding,
                    onSettings = { showSettings = true }
                )
            }
        }

        // Плашки сверху поверх любой вкладки: друг зовёт тренироваться вместе
        // и вышла новая версия приложения.
        Column(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val invite = SocialService.together.incoming.firstOrNull()
            AnimatedVisibility(
                visible = invite != null,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut()
            ) {
                invite?.let { TogetherInviteBanner(it) }
            }
            com.texasprogram.app.ui.AppUpdateBanner()
        }

        Column(Modifier.align(Alignment.BottomCenter)) {
            // В переписке внизу поле ввода: свёрнутая тренировка, отдых и панель
            // вкладок уезжают вниз, чтобы его не закрывать. Отдых при этом идёт
            // дальше и виден в уведомлении; вышел из чата — плашки вернулись.
            val chrome = !SocialService.hidesTabBar
            AnimatedVisibility(
                visible = chrome && WorkoutSessionManager.isRunning && !WorkoutSessionManager.isPresented,
                enter = slideInVertically(Motion.sheet()) { it } + fadeIn(),
                exit = slideOutVertically(Motion.sheet()) { it } + fadeOut()
            ) {
                MinimizedWorkoutBar()
            }
            // Карточка сама прячется и показывается с анимацией.
            AnimatedVisibility(
                visible = chrome,
                enter = slideInVertically(Motion.sheet()) { it } + fadeIn(),
                exit = slideOutVertically(Motion.sheet()) { it } + fadeOut()
            ) {
                RebrnRestTimerCard(timer, Modifier.padding(bottom = 8.dp))
            }
            AnimatedVisibility(
                visible = chrome,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                LiquidGlassTabBar(
                    selectedTab = tab,
                    onTabSelected = { tab = it },
                    // Новые сообщения — на «Друзьях», новая активность — на «Главном».
                    badges = buildSet {
                        if (SocialService.unreadMessages > 0) add(MainTabItem.COMMUNITY)
                        if (SocialService.unseenActivity > 0) add(MainTabItem.TODAY)
                    }
                )
            }
        }

        // Экран «Сегодня» с точками подходов и настройки — поверх вкладок.
        AnimatedContent(
            targetState = showToday,
            transitionSpec = { Motion.modalEnter() togetherWith Motion.modalExit() },
            label = "today"
        ) { visible ->
            if (visible) {
                Box(Modifier.fillMaxSize().background(Theme.base)) {
                    TodayScreen(
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
                        onSettings = { showSettings = true },
                        onStartWorkout = { workout -> startWorkout(workout) },
                        contentPadding = screenPadding(bottomExtra = 110.dp)
                    )
                    CloseButton(Modifier.align(Alignment.TopEnd)) { showToday = false }
                }
            }
        }

        AnimatedContent(
            targetState = showSettings,
            transitionSpec = { Motion.modalEnter() togetherWith Motion.modalExit() },
            label = "settings"
        ) { visible ->
            if (visible) {
                Box(Modifier.fillMaxSize().background(Theme.base)) {
                    SettingsScreen(
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
                        onClose = { showSettings = false },
                        contentPadding = screenPadding(bottomExtra = 32.dp)
                    )
                    CloseButton(Modifier.align(Alignment.TopEnd)) { showSettings = false }
                }
            }
        }

        // Модальный экран плана тренировок. Ниже дня и его правки: день,
        // открытый из плана, должен лечь поверх плана, а не под него.
        AnimatedContent(
            targetState = showPlan,
            transitionSpec = { Motion.modalEnter() togetherWith Motion.modalExit() },
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

        // Детали дня и настройки — поверх вкладок, как модальные экраны в iOS.
        AnimatedContent(
            targetState = dayDetail,
            transitionSpec = {
                Motion.pushEnter() togetherWith Motion.pushExit()
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
                Motion.sheetEnter() togetherWith Motion.sheetExit()
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

        // Модальный экран жимовой волны
        AnimatedContent(
            targetState = benchFocus,
            transitionSpec = { Motion.modalEnter() togetherWith Motion.modalExit() },
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
                Motion.sheetEnter() togetherWith Motion.sheetExit()
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
                modifier = Modifier.popIn(),
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
            transitionSpec = { Motion.modalEnter() togetherWith Motion.modalExit() },
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
            transitionSpec = { Motion.modalEnter() togetherWith Motion.modalExit() },
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
            transitionSpec = { Motion.modalEnter() togetherWith Motion.modalExit() },
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
            transitionSpec = { Motion.modalEnter() togetherWith Motion.modalExit() },
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
        // Будильник без разрешения на уведомления срабатывает молча: на Android 13+
        // просим его, как только напоминания включены, а не только при старте тренировки.
        if (profile.reminderEnabled && android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
        }
    }
}


@Composable
private fun CloseButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier
            .padding(top = topInset + 12.dp, end = 16.dp)
            .size(40.dp)
            .pressable(onClick = onClick)
            .background(Theme.surfaceSoft, androidx.compose.foundation.shape.CircleShape),
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

/// Вкладки, открытые хотя бы раз, остаются в памяти; видна только выбранная.
/// Переход — короткое проявление и сдвиг на 24 dp, рисуется слоем без пересборки
/// экранов.
///
/// Скрытая вкладка остаётся размещённой, но прозрачной: прозрачный слой система
/// не рисует, а при показе его не надо заново раскладывать и записывать — смена
/// вкладки, даже на ходу под пальцем, это только смена прозрачности. Касания
/// скрытой вкладке не достаются: выбранная лежит сверху и ловит их целиком,
/// а скрытая гасит всё, что до неё дошло.
///
/// Скрытая вкладка ещё и «заморожена»: она держит прежний экран и не
/// пересчитывается на каждую отметку подхода. Так же — видимая, пока поверх
/// открыт полноэкранный раздел (`frozen`): её всё равно не видно.
@Composable
private fun KeepAliveTabs(
    selectedTab: () -> MainTabItem,
    frozen: Boolean,
    content: @Composable (MainTabItem) -> Unit
) {
    val selected = selectedTab()
    val visited = remember { mutableStateListOf(selected) }
    if (selected !in visited) visited.add(selected)
    // Остальные вкладки собираются заранее, по одной: когда ведёшь пальцем
    // по панели, они открываются мгновенно, без сборки на лету. Собираем только
    // в паузу — когда экран ничего не рисует. Пока листают ленту или идёт
    // анимация, не мешаем; если пауз нет (идёт отдых с таймером), ждём не дольше 5 с.
    val view = LocalView.current
    LaunchedEffect(Unit) {
        var lastDraw = SystemClock.uptimeMillis()
        val onDraw = ViewTreeObserver.OnDrawListener { lastDraw = SystemClock.uptimeMillis() }
        view.viewTreeObserver.addOnDrawListener(onDraw)
        try {
            for (item in MainTabItem.entries) {
                if (item in visited) continue
                delay(900)
                val waitUntil = SystemClock.uptimeMillis() + 5_000
                while (SystemClock.uptimeMillis() - lastDraw < 350 && SystemClock.uptimeMillis() < waitUntil) delay(120)
                if (item !in visited) visited.add(item)
            }
        } finally {
            if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(onDraw)
        }
    }
    val shift = with(LocalDensity.current) { 24.dp.toPx() }
    Box(Modifier.fillMaxSize()) {
        for (item in MainTabItem.entries) {
            if (item !in visited) continue
            key(item) {
                val active = item == selected
                val progress = remember { Animatable(if (active) 1f else 0f) }
                LaunchedEffect(active) {
                    progress.animateTo(if (active) 1f else 0f, tween(if (active) 220 else 160, easing = FastOutSlowInEasing))
                }
                val side = if (item.ordinal > selected.ordinal) 1f else -1f
                // Новый экран берём, только пока вкладку видно и её ничто не закрывает.
                val held = remember { arrayOfNulls<@Composable (MainTabItem) -> Unit>(1) }
                if ((active && !frozen) || held[0] == null) held[0] = content
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(if (active) 1f else 0f)
                        .graphicsLayer {
                            alpha = progress.value
                            translationX = (1f - progress.value) * side * shift
                        }
                        .then(if (active) CatchTouches else SwallowTouches)
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(com.texasprogram.app.ui.LocalTabActive provides active) {
                        TabContent(held[0]!!, item)
                    }
                }
            }
        }
    }
}

/// Выбранная вкладка «ловит» касание в любой своей точке, даже пустой: иначе
/// оно провалилось бы к скрытой вкладке под ней.
private val CatchTouches = Modifier.pointerInput(Unit) {
    awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Final) }
}

/// Скрытая вкладка гасит касания раньше своих кнопок и списков — и не читается TalkBack.
private val SwallowTouches = Modifier
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
    .clearAndSetSemantics {}

/// Отдельная функция — чтобы Compose мог пропустить её целиком, когда экран
/// вкладки тот же самый (заморожен).
@Composable
private fun TabContent(content: @Composable (MainTabItem) -> Unit, item: MainTabItem) {
    content(item)
}
