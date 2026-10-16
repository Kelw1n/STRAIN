package com.texasprogram.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.service.RuDate
import com.texasprogram.app.service.RuPlural
import java.time.LocalDate
import java.util.Locale

private val SheetColor = Color(0xFF121212)

private val monthNames = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
)

fun monthTitle(date: LocalDate): String = "${monthNames[date.monthValue - 1]} ${date.year}"

/// Полные недели (пн–вс), покрывающие месяц: соседние дни тоже показываем, приглушёнными.
fun calendarWeeks(month: LocalDate): List<List<LocalDate>> {
    val first = month.withDayOfMonth(1)
    val start = RuDate.mondayOf(first)
    val last = first.withDayOfMonth(first.lengthOfMonth())
    val end = RuDate.mondayOf(last).plusDays(6)
    val days = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
    return days.chunked(7)
}

/// Календарь серии, как у REBRN: месяц со стрелками, серия в неделях,
/// тренировки в ней и все дни месяца. Открывается по нажатию на «Серию активностей».
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreakCalendarSheet(profile: ProgramProfile, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberHapticEngine()
    val today = remember { LocalDate.now() }
    var month by remember { mutableStateOf(today.withDayOfMonth(1)) }
    val streak = remember(profile.completionLog) { profile.calendarStreak(today) }
    val done = remember(profile.completionLog) { profile.completionLog.map { LocalDate.ofEpochDay(it.epochDay) }.toSet() }
    val trainingWeekdays = remember(profile.weekdays) { profile.weekdays.toSet() }
    val isCurrentMonth = month.year == today.year && month.monthValue == today.monthValue

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = SheetColor,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    targetState = month,
                    transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                    label = "month",
                    modifier = Modifier.weight(1f)
                ) { value ->
                    Text(monthTitle(value), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                }
                ArrowButton(Icons.Filled.ChevronLeft, "Прошлый месяц", enabled = true) {
                    haptics.selection()
                    month = month.minusMonths(1)
                }
                Spacer(Modifier.width(10.dp))
                ArrowButton(Icons.Filled.ChevronRight, "Следующий месяц", enabled = !isCurrentMonth) {
                    haptics.selection()
                    month = month.plusMonths(1)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                Stat("Твоя серия", "${streak.weeks} ${RuPlural.word(streak.weeks, "неделя", "недели", "недель")}")
                Stat("Тренировок в серии", "${streak.workouts}")
            }

            AnimatedContent(
                targetState = month,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "grid"
            ) { shown ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС").forEach { title ->
                            Text(
                                title,
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                    calendarWeeks(shown).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            week.forEach { day ->
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    DayCell(
                                        date = day,
                                        inMonth = day.monthValue == shown.monthValue,
                                        isToday = day == today,
                                        isPast = day.isBefore(today),
                                        isDone = day in done,
                                        isTrainingDay = RuDate.weekdayOf(day) in trainingWeekdays
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Text(
                "Неделя засчитана, если в ней была хотя бы одна тренировка. Начатая неделя серию не обрывает: у тебя есть время до воскресенья.",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun Stat(title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ArrowButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .background(Color.White.copy(alpha = if (enabled) 0.1f else 0.04f), CircleShape)
            .clip(CircleShape)
            .pressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color.White.copy(alpha = if (enabled) 1f else 0.25f), modifier = Modifier.size(22.dp))
    }
}

/// День в календаре серии. Сделанный — залит акцентом, сегодня — кольцо и точка,
/// будущий тренировочный — оранжевое кольцо, прошедший — серый, чужого месяца — тусклый.
@Composable
private fun DayCell(date: LocalDate, inMonth: Boolean, isToday: Boolean, isPast: Boolean, isDone: Boolean, isTrainingDay: Boolean) {
    val accent = Theme.accent
    Box(
        Modifier
            .size(44.dp)
            .semantics { contentDescription = "${date.dayOfMonth}" + (if (isDone) ", тренировка" else "") + (if (isToday) ", сегодня" else "") },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(44.dp)) {
            val r = size.minDimension / 2
            when {
                !inMonth -> drawCircle(Color.White.copy(alpha = 0.05f), r)
                isDone -> drawCircle(accent, r)
                isToday -> drawCircle(accent, r - 1.25.dp.toPx(), style = Stroke(2.5.dp.toPx()))
                isPast -> drawCircle(Color.White.copy(alpha = 0.12f), r)
                isTrainingDay -> drawCircle(accent, r - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
                else -> drawCircle(Color.White.copy(alpha = 0.15f), r - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
            }
            if (isToday) {
                drawCircle(if (isDone) Color.Black else Color.White, 2.5.dp.toPx(), Offset(center.x, center.y + 13.dp.toPx()))
            }
        }
        Text(
            "${date.dayOfMonth}",
            color = when {
                !inMonth -> Color.White.copy(alpha = 0.22f)
                isDone -> Color.Black
                isPast && !isToday -> Color.White.copy(alpha = 0.85f)
                else -> Color.White
            },
            fontSize = 17.sp,
            fontWeight = if (isToday) FontWeight.Black else FontWeight.SemiBold
        )
    }
}

/// Кольцо цели недели: по части на тренировку, сделанные — акцентом.
@Composable
fun SegmentedRing(total: Int, filled: Int, modifier: Modifier = Modifier, strokeWidth: androidx.compose.ui.unit.Dp = 4.dp) {
    val accent = Theme.accent
    val count = maxOf(total, 1)
    Canvas(
        modifier
            .aspectRatio(1f)
            .semantics { contentDescription = "Сделано ${minOf(filled, count)} из $count" }
    ) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        // Зазор между частями в градусах; у одной части зазора нет. Скругление
        // концов съедает часть зазора, поэтому он с запасом.
        val gap = if (count > 1) 16f else 0f
        val sweep = 360f / count
        for (index in 0 until count) {
            drawArc(
                color = if (index < filled) accent else Color.White.copy(alpha = 0.14f),
                startAngle = -90f + index * sweep + gap / 2,
                sweepAngle = (sweep - gap).coerceAtLeast(1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
    }
}

/// Лист «Цель»: дни тренировок и напоминание. Дней ровно столько, сколько
/// тренировок в неделе программы — иначе расписание не сойдётся с планом.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalSheet(profile: ProgramProfile, onSave: (ProgramProfile) -> Unit, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberHapticEngine()
    val need = profile.trainingDayCount
    var days by remember { mutableStateOf(profile.weekdays.toSet()) }
    var reminder by remember { mutableStateOf(profile.reminderEnabled) }
    var hour by remember { mutableIntStateOf(profile.reminderHour) }
    var minute by remember { mutableIntStateOf(profile.reminderMinute) }
    val ready = days.size == need

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = SheetColor,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("Цель", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetLabel("ДНИ ТРЕНИРОВОК")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RuDate.weekOrder.forEach { weekday ->
                        val on = weekday in days
                        Box(
                            Modifier
                                .weight(1f)
                                .height(46.dp)
                                .background(if (on) Theme.accent else Color(0xFF262626), CircleShape)
                                .clip(CircleShape)
                                .pressable {
                                    haptics.light()
                                    days = if (on) days - weekday else days + weekday
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                RuDate.short(weekday).uppercase(),
                                color = if (on) Color.White else Color.White.copy(alpha = 0.65f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    if (ready) "${RuPlural.word(need, "Тренировка", "Тренировки", "Тренировок").let { "$need $it" }} в неделю — столько же дней в программе."
                    else "Выбери ${need} ${RuPlural.word(need, "день", "дня", "дней")} — столько тренировок в неделе программы. Сейчас ${days.size}.",
                    color = if (ready) Color.White.copy(alpha = 0.45f) else Theme.warning,
                    fontSize = 12.sp
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetLabel("НАПОМИНАНИЕ")
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E1E), RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Напоминать в дни тренировок", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Switch(
                            checked = reminder,
                            onCheckedChange = { reminder = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = Theme.accent)
                        )
                    }
                    if (reminder) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Время", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            StepChip("−") { hour = (hour + 23) % 24 }
                            Text(String.format(Locale.US, "%02d", hour), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                            StepChip("+") { hour = (hour + 1) % 24 }
                            Text(":", color = Color.White.copy(alpha = 0.5f), fontSize = 18.sp, modifier = Modifier.padding(horizontal = 6.dp))
                            StepChip("−") { minute = (minute + 45) % 60 }
                            Text(String.format(Locale.US, "%02d", minute), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                            StepChip("+") { minute = (minute + 15) % 60 }
                        }
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .background(if (ready) Color.White else Color(0xFF2A2A2A), RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .pressable(enabled = ready) {
                        haptics.success()
                        // Порядок с понедельника: первый день программы — первый в неделе.
                        val ordered = RuDate.weekOrder.filter { it in days }
                        onSave(
                            profile.copy(
                                scheduleWeekdays = ordered,
                                reminderEnabled = reminder,
                                reminderHour = hour,
                                reminderMinute = minute
                            )
                        )
                        onDismiss()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("СОХРАНИТЬ ЦЕЛЬ", color = if (ready) Color.Black else Color.White.copy(alpha = 0.4f), fontSize = 16.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
}

@Composable
private fun StepChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .background(Color.White.copy(alpha = 0.1f), CircleShape)
            .clip(CircleShape)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
