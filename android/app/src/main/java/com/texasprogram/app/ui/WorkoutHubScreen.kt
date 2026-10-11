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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.texasprogram.app.service.ScheduledWorkout
import com.texasprogram.app.service.WorkoutSessionManager
import java.time.LocalDate

@Composable
fun WorkoutHubScreen(
    profile: ProgramProfile,
    contentPadding: PaddingValues,
    onStartWorkout: (ScheduledWorkout) -> Unit,
    onOpenPlan: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
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
                ScreenTitle("Тренировка")
            }

            item(key = "streak") {
                ActivityStreakCard(profile)
            }

            item(key = "cards") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Карточка «Цель»
                    GoalCard(
                        profile = profile,
                        hasTodayWorkout = schedule.today != null,
                        modifier = Modifier.weight(1f)
                    )

                    // Карточка «Программы»
                    ProgramsCard(
                        programTitle = profile.programKind.title,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenPlan() }
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
                        .shadow(16.dp, CircleShape, ambientColor = Color.White.copy(alpha = 0.2f))
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (isRunning) {
                                WorkoutSessionManager.expand()
                            } else {
                                onStartWorkout(focus)
                            }
                        }
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
}

// MARK: - Карточка «Серия активностей»

@Composable
private fun ActivityStreakCard(profile: ProgramProfile) {
    val streak = profile.weekStreak
    val completionDates = remember(profile.completionLog) {
        profile.completionLog.map { LocalDate.ofEpochDay(it.epochDay) }.toSet()
    }
    val today = remember { LocalDate.now() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF141416))
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
                    tint = if (streak.first > 0) Theme.accent else Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.size(38.dp)
                )
                Text(
                    text = "${streak.first}",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = streakWord(streak.first),
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.weight(1f))

            // Матрица точек (4 недели по 7 дней)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                for (weekOffset in 0 until 4) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        for (dayOffset in 0 until 7) {
                            val daysAgo = (3 - weekOffset) * 7 + (6 - dayOffset)
                            val targetDate = today.minusDays(daysAgo.toLong())
                            val isDone = completionDates.contains(targetDate)
                            val isToday = targetDate == today

                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isDone) Theme.accent else Color.Transparent)
                                    .border(
                                        width = if (isToday) 2.dp else 1.2.dp,
                                        color = if (isToday) Color.White else (if (isDone) Theme.accent else Color.White.copy(alpha = 0.18f)),
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = if (streak.first > 0) "Серия активна! Продолжай в том же духе." else "Запиши тренировку, чтобы начать серию",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 12.sp
        )
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
    hasTodayWorkout: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF141416))
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

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.CalendarToday,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = if (hasTodayWorkout) "Сегодня" else "Завтра",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Тренировка в ${String.format("%02d:%02d", profile.reminderHour, profile.reminderMinute)}",
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
            .background(Color(0xFF141416))
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
            .background(Color(0xFF141416))
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
            .background(Color(0xFF141416))
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
