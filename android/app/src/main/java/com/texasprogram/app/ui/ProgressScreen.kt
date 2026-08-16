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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.TrainingProgramKind
import com.texasprogram.app.model.formatWeight
import com.texasprogram.app.service.UpperLowerCalculator

@Composable
fun ProgressScreen(
    profile: ProgramProfile,
    contentPadding: PaddingValues,
    onOpenHistory: () -> Unit = {},
    onOpenNotes: () -> Unit = {},
    onUpdate: (ProgramProfile) -> Unit = {}
) {
    val plan = profile.workoutPlan
    val done = profile.completedDayCount
    val total = profile.totalDays
    val ratio = if (total > 0) done.toFloat() / total else 0f

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "title") { ScreenTitle("Прогресс") }

        item(key = "hero") {
            HighlightCard(Modifier.appearIn(0)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RingProgress(ratio, Modifier.size(96.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${Math.round(ratio * 100)}", color = Theme.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text("%", color = Theme.textSecondary, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.width(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        GradientText("ЦИКЛ", fontSize = 11.sp, weight = FontWeight.Bold, letterSpacing = 1.3f)
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("$done", color = Theme.textPrimary, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                            Text("из $total", color = Theme.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(bottom = 7.dp))
                        }
                        Text("тренировочных дней отмечено", color = Theme.textSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        item(key = "streak") {
            val streak = profile.weekStreak
            StreakCard(streak.first, streak.second, Modifier.appearIn(1))
        }

        item(key = "chart") {
            ProgressChartView(profile.completionLog, Modifier.appearIn(1))
        }

        item(key = "bodyweight") {
            BodyWeightCard(profile, Modifier.appearIn(1), onUpdate)
        }

        item(key = "links") {
            CardView(Modifier.appearIn(1), padding = 6.dp, spacing = 0.dp) {
                // Пока история пустая, а отметки есть, зовём именно достроить её:
                // иначе про такую возможность никто не узнает.
                val pending = profile.backfillableWorkouts
                LinkRow(
                    title = "История упражнений",
                    subtitle = if (pending > 0) "можно заполнить по отметкам"
                    else countText(profile.loggedExerciseNames.size, "движение", "движения", "движений"),
                    icon = Icons.Filled.ShowChart,
                    onClick = onOpenHistory
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .height(1.dp)
                        .background(Theme.hairline)
                )
                LinkRow(
                    title = "Заметки",
                    subtitle = countText(
                        profile.workoutNotes.keys.count { profile.isOwnKey(it) },
                        "запись", "записи", "записей"
                    ),
                    icon = Icons.Filled.Notes,
                    onClick = onOpenNotes
                )
            }
        }

        item(key = "maxes") {
            CardView(Modifier.appearIn(1)) {
                Text("Текущие ${profile.maximumLabel}", color = Theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                PRRow("Приседания", profile.squat5RM)
                PRRow("Жим лёжа", profile.bench5RM)
                PRRow("Становая тяга", profile.deadlift5RM)
            }
        }

        if (profile.programKind == TrainingProgramKind.UPPER_LOWER) {
            item(key = "bench") {
                val benchTotal = UpperLowerCalculator.benchSessionCount
                val benchDone = profile.completedBenchCount
                CardView(Modifier.appearIn(2)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MonitorHeart, contentDescription = null, tint = Theme.record, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Волна «Жим 14»", color = Theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        GradientText("$benchDone/$benchTotal", fontSize = 17.sp, weight = FontWeight.Bold, gradient = Theme.recordGradient)
                    }
                    LineMeter(
                        value = if (benchTotal > 0) benchDone.toFloat() / benchTotal else 0f,
                        gradient = Theme.recordGradient
                    )
                    val next = profile.nextBenchSession
                    Text(
                        if (next != null) "Следующая жимовая тренировка — №$next" else "Волна пройдена. Пора тестировать новый 1ПМ.",
                        color = if (next != null) Theme.textSecondary else Theme.success,
                        fontSize = 12.sp
                    )
                }
            }
        }

        item(key = "history") {
            HistoryCard(profile.completionLog, Modifier.appearIn(3))
        }

        item(key = "weeks") {
            CardView(Modifier.appearIn(3)) {
                Text("Недели", color = Theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    plan.weeks.forEachIndexed { weekIndex, week ->
                        val weekDone = week.days.count { profile.isCompleted(week.number, it.number) }
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "${week.number}",
                                color = Theme.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(18.dp)
                            )
                            week.days.forEach { day ->
                                HeatCell(
                                    isDone = profile.isCompleted(week.number, day.number),
                                    delayMs = weekIndex * 30,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "$weekDone/${week.days.size}",
                                color = if (weekDone == week.days.size) Theme.success else Theme.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatCell(isDone: Boolean, delayMs: Int, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(if (isDone) 1f else 1f, Motion.bouncy(), label = "cell")
    Box(
        modifier
            .height(20.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isDone) Theme.successGradient else androidx.compose.ui.graphics.SolidColor(Theme.surfaceSoft))
    )
}

@Composable
private fun PRRow(title: String, value: Double) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Theme.accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(title, color = Theme.textPrimary, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Text("${formatWeight(value)} кг", color = Theme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

/// Строка-вход в раздел: значок, название и подпись со счётчиком.
@Composable
private fun LinkRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Theme.accentGradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Theme.textPrimary, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Theme.textSecondary, fontSize = 11.sp)
        }
        Text("›", color = Theme.textSecondary, fontSize = 20.sp)
    }
}

/// Русский счёт: одна запись, две записи, пять записей.
private fun countText(count: Int, one: String, few: String, many: String): String {
    if (count <= 0) return "пока пусто"
    val last = count % 10
    val hundred = count % 100
    return when {
        hundred in 11..14 -> "$count $many"
        last == 1 -> "$count $one"
        last in 2..4 -> "$count $few"
        else -> "$count $many"
    }
}
