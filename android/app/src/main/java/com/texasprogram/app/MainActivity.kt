package com.texasprogram.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import com.texasprogram.app.data.AppStore
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.TrainingProgramKind
import com.texasprogram.app.ui.AppBackground
import com.texasprogram.app.ui.BenchWaveScreen
import com.texasprogram.app.ui.DayDetailScreen
import com.texasprogram.app.ui.GuideScreen
import com.texasprogram.app.ui.Motion
import com.texasprogram.app.ui.OnboardingScreen
import com.texasprogram.app.ui.PlanScreen
import com.texasprogram.app.ui.ProgressScreen
import com.texasprogram.app.ui.SettingsScreen
import com.texasprogram.app.ui.Theme
import com.texasprogram.app.ui.TodayScreen
import com.texasprogram.app.ui.pressable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val store = remember { AppStore(applicationContext) }
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Theme.accent,
                    background = Theme.base,
                    surface = Theme.surface,
                    onSurface = Theme.textPrimary
                )
            ) {
                AppBackground { AppRoot(store) }
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
private fun AppRoot(store: AppStore) {
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
        onAddProfile = { addingProfile = true }
    )
}

@Composable
private fun MainScaffold(store: AppStore, profile: ProgramProfile, onAddProfile: () -> Unit) {
    var tab by remember(profile.id) { mutableStateOf(AppTab.TODAY) }
    var benchFocus by remember { mutableStateOf<Int?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var dayDetail by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val isUpperLower = profile.programKind == TrainingProgramKind.UPPER_LOWER
    val tabs = AppTab.entries.filter { it != AppTab.BENCH || isUpperLower }

    val openBench: ((Int) -> Unit)? = if (isUpperLower) {
        { session ->
            benchFocus = session
            dayDetail = null
            tab = AppTab.BENCH
        }
    } else null

    val contentPadding = screenPadding(bottomExtra = 96.dp)

    BackHandler(enabled = showSettings || dayDetail != null) {
        when {
            showSettings -> showSettings = false
            dayDetail != null -> dayDetail = null
        }
    }

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
                    onToggleDay = { week, day -> store.updateActive { it.toggleCompleted(week, day) } },
                    onOpenBench = openBench,
                    onOpenDay = { week, day -> dayDetail = week to day },
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
                AppTab.PROGRESS -> ProgressScreen(profile, contentPadding)
                AppTab.GUIDE -> GuideScreen(profile.programKind, contentPadding)
            }
        }

        BottomBar(
            tabs = tabs,
            selected = tab,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) { tab = it }

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
                            onOpenBench = openBench,
                            contentPadding = screenPadding(bottomExtra = 32.dp)
                        )
                        CloseButton(Modifier.align(Alignment.TopEnd)) { dayDetail = null }
                    }
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
                        profiles = store.profiles,
                        onUpdate = { updated -> store.update(updated.id) { updated } },
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
