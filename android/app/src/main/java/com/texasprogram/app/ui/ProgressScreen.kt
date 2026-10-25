package com.texasprogram.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.TrainingProgramKind
import com.texasprogram.app.model.formatWeight
import com.texasprogram.app.service.MuscleGroupAnalytics
import com.texasprogram.app.service.UpperLowerCalculator

@Composable
fun ProgressScreen(
    profile: ProgramProfile,
    contentPadding: PaddingValues,
    onOpenHistory: () -> Unit = {},
    onOpenNotes: () -> Unit = {},
    onOpenBodyWeight: () -> Unit = {},
    onUpdate: (ProgramProfile) -> Unit = {}
) {
    val plan = profile.workoutPlan
    val done = profile.completedDayCount
    val total = profile.totalDays
    val ratio = if (total > 0) done.toFloat() / total else 0f
    var showMuscleDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    OnTabReselect(MainTabItem.PROGRESS) { listState.scrollToTop() }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "title") { ScreenTitle("Прогресс") }
        item(key = "level") { LevelCard(profile) }

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

        // Итоги месяца и года историями.
        item(key = "recap") {
            TrainingRecapEntryCard(profile, Modifier.appearIn(1))
        }

        item(key = "streak") {
            val streak = profile.weekStreak
            StreakCard(streak.first, streak.second, Modifier.appearIn(1))
        }

        item(key = "chart") {
            ProgressChartView(profile.completionLog, Modifier.appearIn(1))
        }

        item(key = "bodyweight") {
            BodyWeightCard(profile, Modifier.appearIn(1), onOpen = onOpenBodyWeight)
        }

        item(key = "muscles") {
            MuscleGroupsRadarCard(profile, Modifier.appearIn(1)) {
                showMuscleDialog = true
            }
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

    if (showMuscleDialog) {
        MuscleGroupsDetailDialog(profile) { showMuscleDialog = false }
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

@Composable
fun MuscleGroupsRadarCard(
    profile: ProgramProfile,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val result = remember(profile) { MuscleGroupAnalytics.calculate(profile) }

    CardView(
        modifier = modifier.pressable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Группы мышц",
                color = Theme.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Баланс нагрузки",
                color = Theme.textSecondary,
                fontSize = 12.sp
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "›",
                color = Theme.textSecondary,
                fontSize = 20.sp
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
            contentAlignment = Alignment.Center
        ) {
            RadarWebCanvas(
                values = result.radarValues,
                levels = 3,
                modifier = Modifier.size(100.dp)
            )
        }

        Text(
            text = result.subtitle,
            color = Theme.textSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun RadarWebCanvas(
    values: List<Float>,
    levels: Int = 3,
    modifier: Modifier = Modifier
) {
    val accent = Theme.accent
    val sides = values.size.coerceAtLeast(3)

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = (minOf(size.width, size.height) / 2f) * 0.90f
        val angleStep = (2.0 * Math.PI) / sides.toDouble()

        // 1. Осевые линии
        for (i in 0 until sides) {
            val angle = i * angleStep - (Math.PI / 2.0)
            val px = center.x + (radius * Math.cos(angle)).toFloat()
            val py = center.y + (radius * Math.sin(angle)).toFloat()
            drawLine(
                color = Color.White.copy(alpha = 0.12f),
                start = center,
                end = Offset(px, py),
                strokeWidth = 1.dp.toPx()
            )
        }

        // 2. Концентрические кольца паутины
        for (lvl in 1..levels) {
            val curRadius = radius * (lvl.toFloat() / levels.toFloat())
            val path = Path()
            for (i in 0 until sides) {
                val angle = i * angleStep - (Math.PI / 2.0)
                val px = center.x + (curRadius * Math.cos(angle)).toFloat()
                val py = center.y + (curRadius * Math.sin(angle)).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.10f),
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // 3. Данные полигона
        if (values.isNotEmpty()) {
            val dataPath = Path()
            for (i in 0 until sides) {
                val valRatio = values.getOrElse(i) { 0.2f }.coerceIn(0.1f, 1.0f)
                val angle = i * angleStep - (Math.PI / 2.0)
                val curRadius = radius * valRatio
                val px = center.x + (curRadius * Math.cos(angle)).toFloat()
                val py = center.y + (curRadius * Math.sin(angle)).toFloat()
                if (i == 0) dataPath.moveTo(px, py) else dataPath.lineTo(px, py)
            }
            dataPath.close()

            // Заливка
            drawPath(
                path = dataPath,
                color = accent.copy(alpha = 0.25f)
            )
            // Обводка
            drawPath(
                path = dataPath,
                color = accent,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

@Composable
fun MuscleGroupsDetailDialog(
    profile: ProgramProfile,
    onDismiss: () -> Unit
) {
    val result = remember(profile) { MuscleGroupAnalytics.calculate(profile) }

    AlertDialog(
        modifier = Modifier.popIn(),
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161616),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Группы мышц",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Закрыть",
                    color = Theme.accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.pressable { onDismiss() }
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF1C1C1F))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Баланс распределения нагрузки",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )

                        RadarWebCanvas(
                            values = result.radarValues,
                            levels = 4,
                            modifier = Modifier.size(160.dp)
                        )

                        Text(
                            text = result.subtitle,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                items(result.items.size) { idx ->
                    val group = result.items[idx]
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1C1C1F))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = group.category.displayName,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = group.exercises,
                                    color = Color.White.copy(alpha = 0.45f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${group.percentage}%",
                                    color = Theme.accent,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${group.sets} подх.",
                                    color = Color.White.copy(alpha = 0.45f),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Полоса прогресса
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(group.percentage / 100f)
                                    .height(6.dp)
                                    .clip(CircleShape)
                                    .background(Theme.accentGradient)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}
