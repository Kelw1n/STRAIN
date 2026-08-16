package com.texasprogram.app.ui

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.ScheduleMode
import com.texasprogram.app.model.WorkoutDayPlan
import com.texasprogram.app.service.RuDate
import com.texasprogram.app.service.ScheduledWorkout

@Composable
fun PlanScreen(
    profile: ProgramProfile,
    onOpenDay: (Int, Int) -> Unit,
    onSetCurrent: (Int, Int) -> Unit,
    contentPadding: PaddingValues
) {
    val plan = profile.workoutPlan
    // Открываемся на текущей неделе, а не на первой. Дальше выбор за
    // пользователем, поэтому пересчёта на каждую перерисовку нет.
    var selectedWeek by remember { mutableIntStateOf(profile.currentWeek) }
    val weekListState = rememberLazyListState()
    // Даты берём из расписания: в очереди день недели определяется местом в очереди,
    // а не номером дня в программе.
    val scheduled = remember(profile) { profile.schedule().allPending.associateBy { it.id } }

    LaunchedEffect(selectedWeek) {
        weekListState.animateScrollToItem((selectedWeek - 1).coerceAtLeast(0))
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "title") { ScreenTitle("План") }

        item(key = "weeks") {
            LazyRow(
                state = weekListState,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                items(plan.weeks, key = { it.number }) { week ->
                    val selected = week.number == selectedWeek
                    val done = week.days.all { profile.isCompleted(week.number, it.number) }
                    WeekChip(week.number, selected, done) { selectedWeek = week.number }
                }
            }
        }

        val week = plan.weeks.firstOrNull { it.number == selectedWeek }
        if (week != null) {
            itemsIndexed(week.days, key = { _, day -> "day-${week.number}-${day.number}" }) { index, day ->
                DaySummaryCard(
                    profile = profile,
                    week = week.number,
                    day = day,
                    scheduled = scheduled["${week.number}-${day.number}"],
                    modifier = Modifier.appearIn(index),
                    onClick = { onOpenDay(week.number, day.number) },
                    onSetCurrent = { onSetCurrent(week.number, day.number) }
                )
            }
        }
    }
}

@Composable
private fun WeekChip(number: Int, selected: Boolean, done: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1f else 0.96f, Motion.snappy(), label = "chip")
    Box(
        Modifier
            .size(width = 48.dp, height = 52.dp)
            .clip(CircleShape)
            .background(if (selected) Theme.accentGradient else androidx.compose.ui.graphics.SolidColor(Theme.surface))
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "$number",
                color = if (selected) Color.White else if (done) Theme.success else Theme.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            if (done) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = if (selected) Color.White else Theme.success,
                    modifier = Modifier.size(10.dp)
                )
            } else {
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
fun DaySummaryCard(
    profile: ProgramProfile,
    week: Int,
    day: WorkoutDayPlan,
    scheduled: ScheduledWorkout? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onSetCurrent: () -> Unit
) {
    val isDone = profile.isCompleted(week, day.number)
    // У запланированной тренировки номер жима приходит из волны.
    val benchSession = scheduled?.benchSession ?: day.exercises.firstNotNullOfOrNull { it.benchSession }
    // Выполненный день показываем без дня недели, невыполненный — с реальной датой.
    val title = scheduled?.fullTitle ?: if (isDone) day.title else profile.fullTitle(day)

    CardView(modifier.pressable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (isDone) Theme.successGradient else androidx.compose.ui.graphics.SolidColor(Theme.surfaceSoft)),
                contentAlignment = Alignment.Center
            ) {
                if (isDone) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                } else {
                    Text("${day.number}", color = Theme.textSecondary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    title,
                    color = Theme.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${day.exercises.size} упражнений", color = Theme.textSecondary, fontSize = 12.sp)
                    if (scheduled != null) {
                        Text("· ${RuDate.dayMonth(scheduled.date)}", color = Theme.textSecondary, fontSize = 12.sp)
                    }
                    if (benchSession != null) OutlineBadge("Жим №$benchSession")
                }
            }
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .pressable(onClick = onSetCurrent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.SportsScore, contentDescription = "Начать с этого дня", tint = Theme.textTertiary, modifier = Modifier.size(17.dp))
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Theme.textSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun DayDetailScreen(
    profile: ProgramProfile,
    week: Int,
    day: WorkoutDayPlan,
    scheduled: ScheduledWorkout?,
    onToggleDay: (Int, Int) -> Unit,
    onToggleSet: (com.texasprogram.app.model.ExercisePrescription, Int) -> Unit,
    onOpenBench: ((Int) -> Unit)?,
    onCustomize: () -> Unit,
    onHoldSet: (com.texasprogram.app.model.ExercisePrescription, Int) -> Unit,
    onUpdateProfile: (com.texasprogram.app.model.ProgramProfile) -> Unit,
    onOpenHistory: (String) -> Unit,
    contentPadding: PaddingValues
) {
    // У запланированной тренировки жим берётся из волны, у выполненной — как в плане.
    val exercises = if (scheduled != null) profile.exercises(scheduled) else day.exercises
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "title") {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Неделя $week", color = Theme.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    // В очереди день недели зависит от места в очереди, поэтому здесь только тип дня.
                    Text(
                        if (profile.scheduleMode == ScheduleMode.QUEUE) day.title else profile.fullTitle(day),
                        color = Theme.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.width(44.dp))
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Theme.surfaceSoft)
                        .pressable(onClick = onCustomize),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Свои упражнения",
                        tint = if (profile.edits(week, day.number).isNotEmpty()) Theme.warning else Theme.textPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
        itemsIndexed(exercises, key = { index, item -> "d-$index-${item.name}" }) { index, exercise ->
            ExerciseCard(
                exercise = exercise,
                modifier = Modifier.appearIn(index),
                onOpenBench = onOpenBench,
                sets = SetTracker(
                    onHold = { dot -> onHoldSet(exercise, dot) },
                    logged = profile.loggedSets(week, day.number, exercise),
                    done = profile.completedSets(week, day.number, exercise)
                ) { dot -> onToggleSet(exercise, dot) },
                onOpenHistory = { onOpenHistory(exercise.name) },
                hint = profile.accessoryHint(exercise, week, day.number)
            )
        }
        item(key = "asplanned") {
            AsPlannedButton(profile, week, day.number, Modifier, onUpdateProfile)
        }

        // Веса берём из того же списка, что показан выше: у запланированного
        // дня жим приходит из волны, а не из плана.
        val resolved = day.copy(exercises = exercises)
        if (autoregulationLifts(profile, week, resolved).isNotEmpty()) {
            item(key = "autoreg") {
                AutoregulationCard(profile, week, resolved, Modifier.padding(top = 4.dp), onUpdateProfile)
            }
        }

        if (profile.stalledLifts.isNotEmpty()) {
            item(key = "stall") { StallNotice(profile) }
        }

        item(key = "note") {
            WorkoutNoteCard(profile, week, day.number, Modifier, onUpdateProfile)
        }

        item(key = "skip") {
            SkipCard(profile, week, day.number, Modifier.padding(top = 4.dp), onUpdateProfile)
        }

        item(key = "complete") {
            CompleteButton(profile.isCompleted(week, day.number), Modifier.padding(top = 4.dp)) {
                onToggleDay(week, day.number)
            }
        }
    }
}
