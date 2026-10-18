package com.texasprogram.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.service.RuPlural
import com.texasprogram.app.service.ScheduledWorkout
import com.texasprogram.app.service.WorkoutSessionManager
import java.time.LocalDate

@Composable
fun WorkoutHubScreen(
    profile: ProgramProfile,
    contentPadding: PaddingValues,
    onStartWorkout: (ScheduledWorkout) -> Unit,
    onOpenPlan: () -> Unit,
    onOpenToday: () -> Unit = {},
    onUpdateProfile: (ProgramProfile) -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    var showingStreak by remember { mutableStateOf(false) }
    var showingGoal by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }
    val schedule = remember(profile, today) { profile.schedule(today) }
    val focus = schedule.focus
    val isRunning = WorkoutSessionManager.isRunning

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "title") {
                // Прежний экран «Сегодня» с точками подходов: «Главное» теперь лента.
                ScreenTitle("Тренировка") {
                    Box(
                        Modifier
                            .size(42.dp)
                            .pressable(onClick = onOpenToday)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.ViewAgenda, contentDescription = "План на сегодня", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }

            item(key = "streak") {
                ActivityStreakCard(profile, onClick = { showingStreak = true })
            }

            item(key = "cards") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Карточка «Цель»
                    GoalCard(
                        profile = profile,
                        modifier = Modifier
                            .weight(1f)
                            .pressable { showingGoal = true }
                    )

                    // Карточка «Программы»
                    ProgramsCard(
                        programTitle = profile.programKind.title,
                        modifier = Modifier
                            .weight(1f)
                            .pressable { onOpenPlan() }
                    )
                }
            }

            if (focus != null) {
                item(key = "planPreview") {
                    TodaysWorkoutPreviewCard(
                        profile = profile,
                        workout = focus
                    )
                }
            } else {
                item(key = "rest") {
                    RestDayCard()
                }
            }

            item(key = "bottomSpacer") {
                Spacer(Modifier.height(84.dp))
            }
        }

        // Большая плавающая кнопка «НАЧАТЬ ТРЕНИРОВКУ» / «ПРОДОЛЖИТЬ ТРЕНИРОВКУ»
        if (focus != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 84.dp) // зазор над LiquidGlassTabBar
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (isRunning) {
                                WorkoutSessionManager.expand()
                            } else {
                                onStartWorkout(focus)
                            }
                        }
                        .shadow(16.dp, CircleShape, ambientColor = Color.White.copy(alpha = 0.2f))
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isRunning) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2ED573))
                            )
                            Text(
                                text = "ПРОДОЛЖИТЬ ТРЕНИРОВКУ",
                                color = Color.Black,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        } else {
                            Text(
                                text = "НАЧАТЬ ТРЕНИРОВКУ",
                                color = Color.Black,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        }
    }

    if (showingStreak) {
        StreakCalendarSheet(profile) { showingStreak = false }
    }
    if (showingGoal) {
        GoalSheet(profile, onSave = onUpdateProfile) { showingGoal = false }
    }
}

// MARK: - Карточка «Серия активностей»

@Composable
private fun ActivityStreakCard(profile: ProgramProfile, onClick: () -> Unit) {
    // Серия по календарю, как в листе: неделя с хотя бы одной тренировкой засчитана.
    val weeks = remember(profile.completionLog) { profile.calendarStreak().weeks }
    val completionDates = remember(profile.completionLog) {
        profile.completionLog.map { LocalDate.ofEpochDay(it.epochDay) }.toSet()
    }
    val today = remember { LocalDate.now() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF161616))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Серия активностей",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Этот месяц",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(14.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Огонь и количество
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.width(80.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = if (weeks > 0) Theme.accent else Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.size(38.dp)
                )
                Text(
                    text = "$weeks",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = streakWord(weeks),
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.weight(1f))

            // Календарь текущего месяца, как у REBRN: столбцы — дни недели
            // с понедельника, строки — недели. Закрытые дни залиты акцентом,
            // прошедшие — серым, будущие тренировочные обведены, сегодня подчёркнуто.
            MonthCalendar(
                today = today,
                done = completionDates,
                trainingWeekdays = remember(profile.weekdays) { profile.weekdays.toSet() }
            )
        }

        Text(
            text = if (weeks > 0) "Серия активна! Продолжай в том же духе." else "Запиши тренировку, чтобы начать серию",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 12.sp
        )
    }
}

/// Дни месяца по неделям с понедельника; пустые клетки — `null`.
internal fun monthRows(date: LocalDate): List<List<LocalDate?>> {
    val first = date.withDayOfMonth(1)
    val leading = first.dayOfWeek.value - 1
    val cells = ArrayList<LocalDate?>()
    repeat(leading) { cells.add(null) }
    for (day in 0 until date.lengthOfMonth()) cells.add(first.plusDays(day.toLong()))
    while (cells.size % 7 != 0) cells.add(null)
    return cells.chunked(7)
}

/// Календарь одним холстом: три десятка точек без отдельного узла на каждую.
@Composable
private fun MonthCalendar(today: LocalDate, done: Set<LocalDate>, trainingWeekdays: Set<Int>) {
    val rows = remember(today) { monthRows(today) }
    val accent = Theme.accent
    val dot = 11.dp
    val gap = 7.dp
    androidx.compose.foundation.Canvas(
        Modifier
            .width(dot * 7 + gap * 6)
            .height(dot * rows.size + gap * (rows.size - 1) + 4.dp)
    ) {
        val d = dot.toPx()
        val g = gap.toPx()
        val stroke = 1.6.dp.toPx()
        rows.forEachIndexed { row, week ->
            week.forEachIndexed { column, date ->
                if (date == null) return@forEachIndexed
                val center = androidx.compose.ui.geometry.Offset(column * (d + g) + d / 2, row * (d + g) + d / 2)
                // Нумерация дней недели как в профиле: 1 — воскресенье.
                val weekday = date.dayOfWeek.value % 7 + 1
                when {
                    date in done -> drawCircle(accent, d / 2, center)
                    date.isBefore(today) -> drawCircle(Color.White.copy(alpha = 0.16f), d / 2, center)
                    else -> drawCircle(
                        if (weekday in trainingWeekdays) accent else Color.White.copy(alpha = 0.18f),
                        d / 2 - stroke / 2,
                        center,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)
                    )
                }
                if (date == today) {
                    val w = 8.dp.toPx()
                    val h = 2.dp.toPx()
                    drawRoundRect(
                        Color.White,
                        topLeft = androidx.compose.ui.geometry.Offset(center.x - w / 2, center.y + d / 2 + 3.dp.toPx() - h),
                        size = androidx.compose.ui.geometry.Size(w, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2)
                    )
                }
            }
        }
    }
}

private fun streakWord(count: Int): String {
    val tens = count % 100
    if (tens in 11..14) return "недель"
    return when (count % 10) {
        1 -> "неделя"
        in 2..4 -> "недели"
        else -> "недель"
    }
}

// MARK: - Карточки «Цель» и «Программы»

@Composable
private fun GoalCard(
    profile: ProgramProfile,
    modifier: Modifier = Modifier
) {
    // Как у REBRN: кольцо из стольких частей, сколько тренировок в неделе;
    // каждая сделанная закрашивает одну. С понедельника — снова с нуля.
    val (done, target) = remember(profile.completionLog, profile.weekdays) { profile.weekGoal() }
    val reached = done >= target
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF161616))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Цель",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(14.dp)
            )
        }

        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            SegmentedRing(total = target, filled = done, modifier = Modifier.size(48.dp))
            Icon(
                imageVector = if (reached) Icons.Filled.Check else Icons.Filled.CalendarToday,
                contentDescription = null,
                tint = if (reached) Theme.accent else Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(if (reached) 22.dp else 18.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = if (reached) "Цель недели есть" else "На этой неделе",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${minOf(done, 99)} из $target ${RuPlural.word(target, "тренировки", "тренировок", "тренировок")}",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProgramsCard(
    programTitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF161616))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Программы",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(14.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.ViewCarousel,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(22.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = programTitle,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Свои и каталог",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp
            )
        }
    }
}

// MARK: - Превью сегодняшней тренировки

@Composable
private fun TodaysWorkoutPreviewCard(
    profile: ProgramProfile,
    workout: ScheduledWorkout
) {
    val exercises = remember(profile, workout) { profile.exercises(workout) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF161616))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "СЕГОДНЯШНИЙ ПЛАН",
                color = Theme.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Text(
                text = workout.fullTitle,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            exercises.forEach { ex ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1C1C1F))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.FitnessCenter,
                        contentDescription = null,
                        tint = Theme.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = ex.name,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${ex.sets} подх.",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun RestDayCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF161616))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(22.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("🌙", fontSize = 36.sp)
        Text(
            text = "Сегодня день отдыха",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Восстановление — неотъемлемая часть прогресса.",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 13.sp
        )
    }
}
