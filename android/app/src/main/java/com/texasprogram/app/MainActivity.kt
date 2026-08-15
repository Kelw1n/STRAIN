package com.texasprogram.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.texasprogram.app.data.AppStore
import com.texasprogram.app.data.BackupService
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.TrainingProgramKind
import com.texasprogram.app.ui.AppBackground
import com.texasprogram.app.ui.BenchWaveScreen
import com.texasprogram.app.ui.DayCustomizeScreen
import com.texasprogram.app.ui.DayDetailScreen
import com.texasprogram.app.ui.GuideScreen
import com.texasprogram.app.ui.KeepScreenOn
import com.texasprogram.app.service.RestTimer
import com.texasprogram.app.ui.Motion
import com.texasprogram.app.ui.OnboardingScreen
import com.texasprogram.app.ui.PlanScreen
import com.texasprogram.app.ui.ProgressScreen
import com.texasprogram.app.ui.SettingsScreen
import com.texasprogram.app.ui.Theme
import com.texasprogram.app.ui.TodayScreen
import com.texasprogram.app.ui.RestTimerBar
import com.texasprogram.app.ui.pressable
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val store = remember { AppStore(applicationContext) }
            val timer = remember { RestTimer(applicationContext) }
            // Отсчёт в интерфейсе: сам таймер считает от даты окончания,
            // это лишь перерисовка раз в секунду.
            LaunchedEffect(timer.isRunning) {
                while (timer.isRunning) {
                    timer.tick()
                    delay(1000)
                }
            }
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Theme.accent,
                    background = Theme.base,
                    surface = Theme.surface,
                    onSurface = Theme.textPrimary
                )
            ) {
                AppBackground { AppRoot(store, timer) }
            }
        }
    }
}

private enum class AppTab(val title: String, val icon: ImageVector) {
    TODAY("Сегодня", Icons.Filled.Bolt),
    PLAN("План", Icons.Filled.Layers),
    BENCH("Жим 14", Icons.Filled.MonitorHeart),
    PROGRESS("Прогресс", Icons.Filled.BarChart),
    GUIDE("Инструкция", Icons.Filled.MenuBook)
}

@Composable
private fun AppRoot(store: AppStore, timer: RestTimer) {
    val active = store.active
    var addingProfile by remember { mutableStateOf(false) }

    if (active == null || addingProfile) {
        val insets = screenPadding(bottomExtra = 24.dp)
        OnboardingScreen(
            canCancel = store.profiles.isNotEmpty(),
            onCancel = { addingProfile = false },
            onSave = { profile ->
                store.add(profile.copy(name = store.suggestedName()))
                addingProfile = false
            },
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
    var tab by remember(profile.id) { mutableStateOf(AppTab.TODAY) }
    var benchFocus by remember { mutableStateOf<Int?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var dayDetail by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var customizing by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var entryTarget by remember { mutableStateOf<com.texasprogram.app.ui.SetEntryTarget?>(null) }

    val isUpperLower = profile.programKind == TrainingProgramKind.UPPER_LOWER
    val tabs = AppTab.entries.filter { it != AppTab.BENCH || isUpperLower }

    val openBench: ((Int) -> Unit)? = if (isUpperLower) {
        { session ->
            benchFocus = session
            dayDetail = null
            tab = AppTab.BENCH
        }
    } else null

    val context = LocalContext.current
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

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupMessage = try {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            val archive = BackupService.parse(text.orEmpty())
            archive.profiles.forEach { store.restore(BackupService.profile(it)) }
            "Восстановлено профилей: ${archive.profiles.size}"
        } catch (e: Exception) {
            "Файл не читается как копия STRAIN"
        }
    }

    val contentPadding = screenPadding(bottomExtra = 96.dp)

    BackHandler(enabled = showSettings || dayDetail != null || customizing != null) {
        when {
            customizing != null -> customizing = null
            showSettings -> showSettings = false
            dayDetail != null -> dayDetail = null
        }
    }

    // Телефон лежит на лавке между подходами — гасить экран тут незачем.
    KeepScreenOn(profile.keepScreenOn)

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(tween(200)) + slideInVertically(tween(260)) { it / 24 }) togetherWith fadeOut(tween(160))
            },
            label = "tab"
        ) { current ->
            when (current) {
                AppTab.TODAY -> TodayScreen(
                    profile = profile,
                    timer = timer,
                    onToggleDay = { week, day -> store.updateActive { it.toggleCompleted(week, day) } },
                    onToggleSet = { workout, exercise, dot ->
                        val adds = profile.willAddSet(workout.week, workout.day.number, exercise, dot)
                        store.updateActive { it.toggleSet(workout.week, workout.day.number, exercise, dot) }
                        if (adds) {
                            val updated = store.active
                            if (updated != null && updated.allSetsDone(workout)) {
                                timer.stop()
                                store.updateActive { it.toggleCompleted(workout.week, workout.day.number) }
                            } else {
                                timer.start(profile.defaultRestSeconds.toLong())
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
                    onSettings = { showSettings = true },
                    contentPadding = contentPadding
                )
                AppTab.PLAN -> PlanScreen(
                    profile = profile,
                    onOpenDay = { week, day -> dayDetail = week to day },
                    onSetCurrent = { week, day -> store.updateActive { it.setCurrentWorkout(week, day) } },
                    contentPadding = contentPadding
                )
                AppTab.BENCH -> BenchWaveScreen(
                    profile = profile,
                    focused = benchFocus,
                    onFocusHandled = { benchFocus = null },
                    onToggleBench = { session -> store.updateActive { it.toggleBenchCompleted(session) } },
                    onSetCurrentBench = { session -> store.updateActive { it.setCurrentBenchSession(session) } },
                    contentPadding = contentPadding
                )
                AppTab.PROGRESS -> ProgressScreen(
                    profile = profile,
                    contentPadding = contentPadding,
                    coachKey = store.coachKey,
                    coachModel = store.coachModel,
                    onUpdate = { updated -> store.update(updated.id) { updated } }
                )
                AppTab.GUIDE -> GuideScreen(profile.programKind, contentPadding)
            }
        }

        Column(Modifier.align(Alignment.BottomCenter)) {
            if (timer.isRunning) {
                RestTimerBar(timer, Modifier.padding(bottom = 8.dp))
            }
            BottomBar(tabs = tabs, selected = tab) { tab = it }
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
                            onToggleDay = { w, d -> store.updateActive { it.toggleCompleted(w, d) } },
                            onToggleSet = { exercise, dot ->
                                val adds = profile.willAddSet(week, dayNumber, exercise, dot)
                                store.updateActive { it.toggleSet(week, dayNumber, exercise, dot) }
                                if (adds) timer.start(profile.defaultRestSeconds.toLong())
                            },
                            onOpenBench = openBench,
                            onCustomize = { customizing = week to dayNumber },
                            onUpdateProfile = { updated -> store.update(updated.id) { updated } },
                            onHoldSet = { exercise, dot ->
                                entryTarget = com.texasprogram.app.ui.SetEntryTarget(week, dayNumber, exercise, dot)
                            },
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

        AnimatedContent(
            targetState = showSettings,
            transitionSpec = {
                if (targetState) {
                    (slideInVertically(tween(320)) { it / 3 } + fadeIn(tween(220))) togetherWith fadeOut(tween(180))
                } else {
                    fadeIn(tween(180)) togetherWith (slideOutVertically(tween(320)) { it / 3 } + fadeOut(tween(200)))
                }
            },
            label = "settings"
        ) { visible ->
            if (visible) {
                Box(Modifier.fillMaxSize().background(Theme.base)) {
                    SettingsScreen(
                        profile = profile,
                        onExportBackup = { exportLauncher.launch(BackupService.FILE_NAME) },
                        onImportBackup = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        backupMessage = backupMessage,
                        profiles = store.profiles,
                        onUpdate = { updated -> store.update(updated.id) { updated } },
                        coachKey = store.coachKey,
                        coachModel = store.coachModel,
                        onCoachKey = store::updateCoachKey,
                        onCoachModel = store::updateCoachModel,
                        onSwitchProfile = { id ->
                            showSettings = false
                            store.setActive(id)
                        },
                        onAddProfile = {
                            showSettings = false
                            onAddProfile()
                        },
                        onDeleteProfile = {
                            showSettings = false
                            store.delete(profile.id)
                        },
                        onClose = { showSettings = false },
                        contentPadding = screenPadding(bottomExtra = 32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBar(
    tabs: List<AppTab>,
    selected: AppTab,
    modifier: Modifier = Modifier,
    onSelect: (AppTab) -> Unit
) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Row(
        modifier
            .fillMaxWidth()
            .background(Color(0xE60D1116))
            .padding(top = 10.dp, bottom = bottomInset + 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { item ->
            val isSelected = item == selected
            Column(
                Modifier
                    .weight(1f)
                    .pressable { onSelect(item) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    item.icon,
                    contentDescription = item.title,
                    tint = if (isSelected) Theme.accent else Theme.textTertiary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    item.title,
                    color = if (isSelected) Theme.accent else Theme.textTertiary,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
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
