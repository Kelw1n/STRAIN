package com.texasprogram.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.BenchSessionPlan
import com.texasprogram.app.model.BenchSetKind
import com.texasprogram.app.model.BenchSetPlan
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.ScheduleMode
import com.texasprogram.app.service.RuDate
import com.texasprogram.app.service.UpperLowerCalculator
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun BenchWaveScreen(
    profile: ProgramProfile,
    focused: Int?,
    onFocusHandled: () -> Unit,
    onToggleBench: (Int) -> Unit,
    onSetCurrentBench: (Int) -> Unit,
    contentPadding: PaddingValues
) {
    val sessions = profile.benchWave
    val completed = profile.completedBenchSessions.toSet()
    val next = profile.nextBenchSession
    var expanded by remember { mutableStateOf(focused ?: next) }
    var pendingStart by remember { mutableStateOf<BenchSessionPlan?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(focused) {
        if (focused != null) {
            expanded = focused
            val index = sessions.indexOfFirst { it.id == focused }
            if (index >= 0) listState.animateScrollToItem(index + 3)
            onFocusHandled()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "title") { ScreenTitle("Жим 14") }

        item(key = "header") {
            WaveHeader(profile, sessions, completed, next, Modifier.appearIn(0))
        }

        item(key = "chart") {
            CardView(Modifier.appearIn(1), padding = 16.dp, spacing = 12.dp) {
                Text("Верхний вес по тренировкам", color = Theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                BenchWaveChart(sessions, completed, expanded) { expanded = it }
            }
        }

        item(key = "legend") {
            Row(Modifier.appearIn(2), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(BenchSetKind.NEGATIVE, BenchSetKind.TEST, BenchSetKind.RECORD).forEach { kind ->
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(Theme.surface)
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(kind.tint))
                        Text(
                            kind.title.replaceFirstChar { it.uppercase() },
                            color = Theme.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        sessions.forEachIndexed { index, session ->
            item(key = "session-${session.id}") {
                BenchSessionCard(
                    session = session,
                    subtitle = "Неделя ${session.week} · ${profile.weekdayName(session.dayNumber).replaceFirstChar { it.uppercase() }}",
                    isDone = completed.contains(session.id),
                    isNext = next == session.id,
                    isExpanded = expanded == session.id,
                    modifier = Modifier
                        .appearIn(minOf(index + 3, 10))
                        .softScroll(listState, index + 4),
                    onTap = { expanded = if (expanded == session.id) null else session.id },
                    onToggleDone = { onToggleBench(session.id) },
                    onSetCurrent = { pendingStart = session }
                )
            }
        }
    }

    val pending = pendingStart
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { pendingStart = null },
            title = { Text("Начать с тренировки ${pending.id}?") },
            text = { Text(startMessage(profile, pending)) },
            confirmButton = {
                TextButton(onClick = {
                    onSetCurrentBench(pending.id)
                    expanded = pending.id
                    pendingStart = null
                }) { Text("Сделать следующей") }
            },
            dismissButton = {
                TextButton(onClick = { pendingStart = null }) { Text("Отмена") }
            },
            containerColor = Theme.dialog,
            titleContentColor = Theme.textPrimary,
            textContentColor = Theme.textSecondary
        )
    }
}

/// Текст подтверждения: что будет отмечено и на какую дату встанет тренировка.
private fun startMessage(profile: ProgramProfile, session: BenchSessionPlan): String {
    val today = LocalDate.now()
    val date = profile.plannedStartDate(session.dayNumber, today)
    val days = ChronoUnit.DAYS.between(today, date)
    val when_ = when (days) {
        0L -> "сегодня"
        1L -> "завтра"
        else -> "${RuDate.full(RuDate.weekdayOf(date))}, ${RuDate.dayMonth(date)}"
    }
    val prefix = if (session.id > 1) {
        "Тренировки 1–${session.id - 1} будут отмечены выполненными вместе с их днями программы."
    } else {
        "Прогресс волны будет сброшен к началу."
    }
    // В режиме по дням недели тренировка встанет на свой день недели, и раньше неё
    // может оказаться другой день программы — поэтому не обещаем «следующая».
    val tail = if (profile.scheduleMode == ScheduleMode.QUEUE) {
        "Тренировка ${session.id} станет следующей — $when_."
    } else {
        "Тренировка ${session.id} встанет на $when_."
    }
    return "$prefix $tail"
}

@Composable
private fun WaveHeader(
    profile: ProgramProfile,
    sessions: List<BenchSessionPlan>,
    completed: Set<Int>,
    next: Int?,
    modifier: Modifier = Modifier
) {
    HighlightCard(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientText("ВОЛНОВАЯ ПРОГРЕССИЯ", fontSize = 11.sp, weight = FontWeight.Bold, letterSpacing = 1.3f)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${completed.size}", color = Theme.textPrimary, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "из ${sessions.size} тренировок",
                        color = Theme.textSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Text(
                    if (next != null) "Следующая — №$next" else "Волна пройдена полностью",
                    color = if (next != null) Theme.textSecondary else Theme.success,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            RingProgress(
                value = if (sessions.isNotEmpty()) completed.size.toFloat() / sessions.size else 0f,
                modifier = Modifier.size(84.dp),
                lineWidth = 10.dp
            ) {
                Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(26.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BenchStat("1ПМ жима", profile.bench5RM, Theme.accentGradient, Modifier.weight(1f))
            val target = sessions.lastOrNull()?.topWeight
            if (target != null) {
                BenchStat("Цель волны", target, Theme.recordGradient, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BenchStat(
    title: String,
    value: Double,
    gradient: androidx.compose.ui.graphics.Brush,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Theme.surfaceSoft)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(title, color = Theme.textSecondary, fontSize = 11.sp)
        GradientText("${Math.round(value)} кг", fontSize = 19.sp, weight = FontWeight.Bold, gradient = gradient)
    }
}

@Composable
private fun BenchWaveChart(
    sessions: List<BenchSessionPlan>,
    completed: Set<Int>,
    selected: Int?,
    onSelect: (Int) -> Unit
) {
    val maxWeight = sessions.maxOfOrNull { it.topWeight } ?: 1.0
    val minWeight = sessions.minOfOrNull { it.topWeight } ?: 0.0
    val grown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grown.animateTo(1f, Motion.card()) }

    Row(
        Modifier
            .fillMaxWidth()
            .height(122.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        sessions.forEachIndexed { index, session ->
            val ratio = if (maxWeight > minWeight) {
                0.34 + 0.66 * (session.topWeight - minWeight) / (maxWeight - minWeight)
            } else 1.0
            val isSelected = selected == session.id
            val target = (96 * ratio).toFloat()
            val barHeight by animateFloatAsState(
                targetValue = if (grown.value > 0.05f) target else 5f,
                animationSpec = Motion.card(),
                label = "bar$index"
            )
            Column(
                Modifier
                    .weight(1f)
                    .pressable { onSelect(session.id) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(barHeight.dp)
                        .clip(CircleShape)
                        .background(session.highlight?.gradient ?: Theme.accentGradient)
                        .graphicsLayer { alpha = if (selected == null || isSelected) 1f else 0.4f },
                    contentAlignment = Alignment.TopCenter
                ) {
                    if (completed.contains(session.id)) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(11.dp).padding(top = 1.dp)
                        )
                    }
                }
                Text(
                    "${session.id}",
                    color = if (isSelected) (session.highlight?.tint ?: Theme.accent) else Theme.textSecondary,
                    fontSize = 9.sp,
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun BenchSessionCard(
    session: BenchSessionPlan,
    subtitle: String,
    isDone: Boolean,
    isNext: Boolean,
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onToggleDone: () -> Unit,
    onSetCurrent: () -> Unit
) {
    val accent = session.highlight?.gradient ?: Theme.accentGradient
    val accentColor = session.highlight?.tint ?: Theme.accent
    val rotation by animateFloatAsState(if (isExpanded) 180f else 0f, Motion.snappy(), label = "chevron")
    val haptics = rememberHaptics()

    val body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        Row(
            Modifier.fillMaxWidth().pressable(onClick = onTap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (isDone) accent else SolidColor(accentColor.copy(alpha = 0.18f)))
                    .border(1.dp, if (isDone) Color.Transparent else accentColor.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isDone) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                } else {
                    Text("${session.id}", color = accentColor, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Тренировка ${session.id}", color = Theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    session.highlight?.let { OutlineBadge(it.title, tint = it.tint) }
                }
                Text(subtitle, color = Theme.textSecondary, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                GradientText("${Math.round(session.topWeight)} кг", fontSize = 17.sp, weight = FontWeight.Bold, gradient = accent)
                Text("${session.sets.size} подх.", color = Theme.textSecondary, fontSize = 11.sp)
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = Theme.textSecondary,
                modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = rotation }
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(Motion.fade()) + expandVertically(Motion.card()),
            exit = fadeOut(Motion.fade(180)) + shrinkVertically(Motion.card())
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                session.sets.forEach { set -> BenchSetRow(set) }
                session.highlight?.let { kind ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = kind.tint, modifier = Modifier.size(14.dp))
                        Text(kind.hint, color = kind.tint, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(2.dp))
                GradientButton(
                    text = if (isDone) "Жим выполнен" else "Отметить жим",
                    icon = if (isDone) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    gradient = if (isDone) Theme.successGradient else accent
                ) {
                    haptics()
                    onToggleDone()
                }
                SecondaryButton("Начать отсюда", icon = Icons.Filled.SportsScore, tint = accentColor, onClick = onSetCurrent)
            }
        }
    }

    if (isNext && !isDone) {
        HighlightCard(modifier, gradient = accent, content = body)
    } else {
        CardView(modifier, content = body)
    }
}

@Composable
private fun BenchSetRow(set: BenchSetPlan) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Theme.surfaceSoft)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("${set.id}", color = Theme.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(16.dp))
        if (set.kind.isSpecial) {
            GradientText(set.weightText, fontSize = 16.sp, weight = FontWeight.Bold, gradient = set.kind.gradient)
        } else {
            Text(set.weightText, color = Theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Text("×${set.reps}", color = Theme.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        if (set.kind.isSpecial) {
            TagBadge(set.kind.title, gradient = set.kind.gradient)
        } else {
            Text(set.percentText, color = Theme.textTertiary, fontSize = 12.sp)
        }
    }
}
