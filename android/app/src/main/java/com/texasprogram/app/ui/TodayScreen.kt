package com.texasprogram.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.TrainingProgramKind
import com.texasprogram.app.service.RuDate
import com.texasprogram.app.service.ScheduledWorkout
import com.texasprogram.app.service.WorkoutSchedule
import java.time.LocalDate

@Composable
fun TodayScreen(
    profile: ProgramProfile,
    timer: com.texasprogram.app.service.RestTimer,
    onToggleDay: (Int, Int) -> Unit,
    onToggleSet: (ScheduledWorkout, com.texasprogram.app.model.ExercisePrescription, Int) -> Unit,
    onSelectRest: (Int) -> Unit,
    onOpenBench: ((Int) -> Unit)?,
    onOpenDay: (Int, Int) -> Unit,
    onCustomize: (Int, Int) -> Unit,
    onSettings: () -> Unit,
    contentPadding: PaddingValues
) {
    val today = LocalDate.now()
    val schedule = profile.schedule(today)
    val focus = schedule.focus
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "title") {
            ScreenTitle("Сегодня") {
                if (focus != null) {
                    val edited = profile.edits(focus.week, focus.day.number).isNotEmpty()
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Theme.surfaceSoft)
                            .pressable { onCustomize(focus.week, focus.day.number) },
                        contentAlignment = Alignment.Center
                    ) {
                        // Изменённый день подсвечиваем цветом значка, а не отдельной меткой.
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Свои упражнения",
                            tint = if (edited) Theme.warning else Theme.textPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Theme.surfaceSoft)
                        .pressable(onClick = onSettings),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Tune, contentDescription = "Настройки", tint = Theme.textPrimary, modifier = Modifier.size(20.dp))
                }
            }
        }

        if (focus == null) {
            item(key = "finished") { FinishedCard(profile, Modifier.appearIn(0)) }
            return@LazyColumn
        }

        if (schedule.todayAlreadyDone) {
            item(key = "status") {
                StatusCard(
                    icon = Icons.Filled.CheckCircle,
                    gradient = Theme.successGradient,
                    title = "Сегодня уже отмечено",
                    subtitle = dateLine(today),
                    modifier = Modifier.appearIn(0)
                )
            }
        } else if (schedule.isRestDay) {
            item(key = "status") {
                StatusCard(
                    icon = Icons.Filled.Bedtime,
                    gradient = Theme.deepGradient,
                    title = "Сегодня отдых",
                    subtitle = dateLine(today),
                    modifier = Modifier.appearIn(0)
                )
            }
        }

        item(key = "hero") {
            HeroCard(profile, focus, schedule, Modifier.appearIn(1))
        }

        if (schedule.overdue.isNotEmpty()) {
            item(key = "overdue") {
                OverdueCard(profile, schedule.overdue, onOpenDay, Modifier.appearIn(2))
            }
        }

        val benchSession = focus.benchSession
        if (onOpenBench != null && benchSession != null) {
            item(key = "teaser") {
                BenchTeaser(profile, benchSession, Modifier.appearIn(3)) { onOpenBench(benchSession) }
            }
        }

        // Вспомогательные упражнения — из дня программы, жим — из волны.
        val exercises = profile.exercises(focus)
        itemsIndexed(exercises, key = { index, item -> "ex-$index-${item.name}" }) { index, exercise ->
            ExerciseCard(
                exercise = exercise,
                modifier = Modifier.appearIn(index + 4),
                onOpenBench = onOpenBench,
                sets = SetTracker(
                    done = profile.completedSets(focus.week, focus.day.number, exercise)
                ) { dot -> onToggleSet(focus, exercise, dot) }
            )
        }

        item(key = "rest") {
            RestTimerLauncher(timer, profile.defaultRestSeconds, onSelectRest, Modifier.appearIn(exercises.size + 4))
        }

        item(key = "complete") {
            CompleteButton(focus.isCompleted, Modifier.padding(top = 4.dp)) {
                onToggleDay(focus.week, focus.day.number)
            }
        }
    }
}

@Composable
private fun HeroCard(
    profile: ProgramProfile,
    workout: ScheduledWorkout,
    schedule: WorkoutSchedule,
    modifier: Modifier = Modifier
) {
    val total = profile.totalDays
    val done = profile.completedDayCount
    HighlightCard(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientText(
                    (schedule.relativeTitle(workout) + " · " + RuDate.dayMonth(workout.date)).uppercase(),
                    fontSize = 11.sp,
                    weight = FontWeight.Bold,
                    letterSpacing = 1.3f
                )
                Text(
                    "Неделя ${workout.week}",
                    color = Theme.textPrimary,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    workout.fullTitle,
                    color = Theme.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.width(12.dp))
            RingProgress(
                value = if (total > 0) done.toFloat() / total else 0f,
                modifier = Modifier.size(76.dp),
                lineWidth = 9.dp
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$done", color = Theme.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("из $total", color = Theme.textSecondary, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun OverdueCard(
    profile: ProgramProfile,
    items: List<ScheduledWorkout>,
    onOpenDay: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    CardView(modifier, padding = 16.dp, spacing = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.History, contentDescription = null, tint = Theme.warning, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Пропущено на этой неделе", color = Theme.warning, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        items.forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(Theme.warning.copy(alpha = 0.09f))
                    .pressable { onOpenDay(item.week, item.day.number) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    item.shortWeekday.uppercase(),
                    color = Theme.warning,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.width(28.dp)
                )
                Text(
                    item.day.title,
                    color = Theme.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                Text(RuDate.dayMonth(item.date), color = Theme.textSecondary, fontSize = 11.sp)
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Theme.textSecondary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun BenchTeaser(
    profile: ProgramProfile,
    session: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val plan = profile.benchWave.firstOrNull { it.id == session }
    CardView(modifier.pressable(onClick = onClick), padding = 16.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(plan?.highlight?.gradient ?: Theme.accentGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MonitorHeart, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Жим 14 · тренировка $session из 14",
                    color = Theme.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (plan != null) {
                    Text(
                        "Верх до ${Math.round(plan.topWeight)} кг · ${plan.sets.size} подходов",
                        color = Theme.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Theme.textSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun FinishedCard(profile: ProgramProfile, modifier: Modifier = Modifier) {
    CardView(modifier.padding(top = 40.dp), padding = 28.dp) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                Icons.Filled.Verified,
                contentDescription = null,
                tint = Theme.success,
                modifier = Modifier.size(54.dp)
            )
            Text("Цикл завершён", color = Theme.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                if (profile.programKind == TrainingProgramKind.TEXAS)
                    "Все 12 недель отмечены. Можно запустить пикирование из настроек."
                else
                    "Все 7 недель программы «Верх / Низ» отмечены.",
                color = Theme.textSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

fun dateLine(date: LocalDate): String =
    RuDate.full(RuDate.weekdayOf(date)).replaceFirstChar { it.uppercase() } + ", " + RuDate.dayMonth(date)
