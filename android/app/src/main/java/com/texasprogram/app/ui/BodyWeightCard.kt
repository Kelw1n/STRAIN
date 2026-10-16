package com.texasprogram.app.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.BodyWeightEntry
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.formatWeight
import com.texasprogram.app.service.RuDate
import java.time.LocalDate
import kotlin.math.abs

/// «Замеры веса» в разделе прогресса: текущий вес, изменение за месяц и
/// короткий график. Нажатие открывает экран со всеми замерами.
@Composable
fun BodyWeightCard(
    profile: ProgramProfile,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit
) {
    val series = remember(profile.bodyWeightLog) { profile.bodyWeightSeries }
    val latest = series.lastOrNull()?.weight
    val change = remember(profile.bodyWeightLog) { profile.bodyWeightChange(30) }

    CardView(modifier.pressable(onClick = onOpen)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Замеры веса", color = Theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("›", color = Theme.textSecondary, fontSize = 20.sp)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(latest?.let { formatWeight(it) } ?: "—", color = Theme.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Text("кг", color = Theme.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 5.dp))
                }
                Text(
                    when {
                        latest == null -> "Запиши вес — увидишь динамику и силу к своему весу"
                        change != null -> "${signedKilograms(change)} за 30 дней"
                        else -> lastMeasurementLine(series)
                    },
                    color = Theme.textSecondary,
                    fontSize = 12.sp
                )
            }
            if (series.size > 1) {
                Sparkline(series.takeLast(12), Modifier.width(110.dp).height(44.dp))
            }
        }
    }
}

/// Экран «Замеры веса» во весь экран: крупный текущий вес, запись замера,
/// график, сила к своему весу и все замеры списком — как в iOS-версии.
@Composable
fun BodyWeightScreen(
    profile: ProgramProfile,
    onUpdate: (ProgramProfile) -> Unit,
    contentPadding: PaddingValues
) {
    val series = remember(profile.bodyWeightLog) { profile.bodyWeightSeries }
    val haptics = rememberHapticEngine()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "title") { ScreenTitle("Замеры веса") }
        item(key = "hero") { WeightHero(profile, series) }
        item(key = "entry") {
            EntryCard { value ->
                haptics.success()
                onUpdate(profile.recordBodyWeight(value))
            }
        }
        if (series.size > 1) {
            item(key = "chart") { ChartCard(series) }
        }
        if (series.isNotEmpty()) {
            item(key = "ratio") { RatioCard(profile) }
            item(key = "history") {
                HistoryCard(series) { entry ->
                    haptics.medium()
                    onUpdate(profile.removeBodyWeight(entry.epochDay))
                }
            }
        }
    }
}

@Composable
private fun WeightHero(profile: ProgramProfile, series: List<BodyWeightEntry>) {
    val change = remember(profile.bodyWeightLog) { profile.bodyWeightChange(30) }
    CardView(corner = 24.dp, spacing = 6.dp, padding = 20.dp) {
        Text("ТЕКУЩИЙ ВЕС", color = Theme.textTertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                series.lastOrNull()?.let { formatWeight(it.weight) } ?: "—",
                color = Theme.textPrimary,
                fontSize = 54.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                "кг",
                color = Theme.textSecondary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp, bottom = 10.dp)
            )
            Spacer(Modifier.weight(1f))
            if (change != null) {
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 10.dp)) {
                    Text(
                        signedKilograms(change),
                        color = if (change == 0.0) Theme.textSecondary else Theme.accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("за 30 дней", color = Theme.textTertiary, fontSize = 11.sp)
                }
            }
        }
        Text(lastMeasurementLine(series), color = Theme.textSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun EntryCard(onRecord: (Double) -> Unit) {
    var input by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    // Запятая и точка на клавиатуре разные, а значат одно и то же.
    val parsed = input.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it > 20 && it < 400 }
    val record = {
        parsed?.let {
            onRecord(it)
            input = ""
            focus.clearFocus()
        }
    }

    CardView(corner = 22.dp, spacing = 12.dp) {
        Text("Новый замер", color = Theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Theme.surfaceSoft)
                    .border(1.5.dp, if (focused) Theme.accent else Color.Transparent, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (input.isEmpty()) {
                    Text("Сегодня, кг", color = Theme.textTertiary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                BasicTextField(
                    value = input,
                    onValueChange = { text -> input = text.filter { it.isDigit() || it == ',' || it == '.' }.take(6) },
                    singleLine = true,
                    textStyle = TextStyle(color = Theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    cursorBrush = SolidColor(Theme.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { record() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused }
                )
            }
            Box(
                Modifier
                    .height(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (parsed == null) Theme.surfaceSoft else Theme.accent)
                    .pressable(enabled = parsed != null) { record() }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Записать",
                    color = if (parsed == null) Theme.textTertiary else Color.Black,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            "Второй замер за день заменяет первый. Лучше взвешиваться утром, натощак.",
            color = Theme.textTertiary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ChartCard(series: List<BodyWeightEntry>) {
    val total = series.last().weight - series.first().weight
    val minWeight = series.minOf { it.weight }
    val maxWeight = series.maxOf { it.weight }
    // Полкило сверху и снизу, чтобы ровный вес не прилипал к краю графика.
    val low = minWeight - 0.5
    val high = maxWeight + 0.5
    CardView(corner = 22.dp, spacing = 12.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Динамика", color = Theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${signedKilograms(total)} с первого замера", color = Theme.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(Modifier.fillMaxWidth().height(170.dp)) {
            Column(
                Modifier.fillMaxHeight().padding(end = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(high, (high + low) / 2, low).forEach {
                    Text(formatWeight(Math.round(it * 10) / 10.0), color = Theme.textTertiary, fontSize = 10.sp)
                }
            }
            WeightChart(series, low, high, Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.fillMaxWidth()) {
            Text(RuDate.shortDayMonth(LocalDate.ofEpochDay(series.first().epochDay)), color = Theme.textTertiary, fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            Text(RuDate.shortDayMonth(LocalDate.ofEpochDay(series.last().epochDay)), color = Theme.textTertiary, fontSize = 10.sp)
        }
    }
}

/// График по датам, а не по номерам замеров: пропуск в неделю виден как пропуск.
@Composable
private fun WeightChart(series: List<BodyWeightEntry>, low: Double, high: Double, modifier: Modifier) {
    val accent = Theme.accent
    val grid = Theme.hairline
    Canvas(modifier) {
        val minDay = series.first().epochDay
        val maxDay = series.last().epochDay
        val span = (high - low).takeIf { it > 0 } ?: 1.0
        val pad = 6.dp.toPx()
        fun x(day: Long): Float =
            if (maxDay == minDay) size.width / 2
            else pad + (day - minDay).toFloat() / (maxDay - minDay).toFloat() * (size.width - pad * 2)
        fun y(weight: Double): Float = size.height - ((weight - low) / span).toFloat() * size.height

        for (i in 0..2) {
            val gy = size.height * i / 2f
            drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
        }

        val line = Path()
        series.forEachIndexed { index, entry ->
            val px = x(entry.epochDay)
            val py = y(entry.weight)
            if (index == 0) line.moveTo(px, py) else line.lineTo(px, py)
        }
        val area = Path().apply {
            addPath(line)
            lineTo(x(series.last().epochDay), size.height)
            lineTo(x(series.first().epochDay), size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(accent.copy(alpha = 0.3f), Color.Transparent)))
        drawPath(line, accent, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val last = series.last()
        drawCircle(accent, 4.5.dp.toPx(), Offset(x(last.epochDay), y(last.weight)))
    }
}

/// Мини-график последних замеров для карточки.
@Composable
private fun Sparkline(series: List<BodyWeightEntry>, modifier: Modifier) {
    val accent = Theme.accent
    Canvas(modifier) {
        val minWeight = series.minOf { it.weight }
        val maxWeight = series.maxOf { it.weight }
        val span = (maxWeight - minWeight).takeIf { it > 0.3 } ?: 0.3
        val inset = 3.dp.toPx()
        val step = if (series.size > 1) (size.width - inset * 2) / (series.size - 1) else 0f
        val path = Path()
        series.forEachIndexed { index, entry ->
            val px = inset + index * step
            val py = inset + (1f - ((entry.weight - minWeight) / span).toFloat()) * (size.height - inset * 2)
            if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, accent, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun RatioCard(profile: ProgramProfile) {
    CardView(corner = 22.dp, spacing = 12.dp) {
        Text("Сила к своему весу", color = Theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Ratio("Присед", profile.relativeStrength(profile.squat5RM), Modifier.weight(1f))
            Ratio("Жим", profile.relativeStrength(profile.bench5RM), Modifier.weight(1f))
            Ratio("Тяга", profile.relativeStrength(profile.deadlift5RM), Modifier.weight(1f))
        }
        Text(
            "Отношение ${profile.maximumLabel} к весу тела. Держится при похудении — значит, сила осталась, а лишнее ушло.",
            color = Theme.textTertiary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun Ratio(title: String, value: Double?, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Theme.surfaceSoft)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            value?.let { String.format(java.util.Locale.ROOT, "%.2f", it).replace('.', ',') } ?: "—",
            color = Theme.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black
        )
        Text(title, color = Theme.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun HistoryCard(series: List<BodyWeightEntry>, onDelete: (BodyWeightEntry) -> Unit) {
    val items = series.asReversed()
    CardView(corner = 22.dp, spacing = 0.dp) {
        Text(
            "Все замеры",
            color = Theme.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        items.forEachIndexed { index, entry ->
            val previous = items.getOrNull(index + 1)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        RuDate.dayMonth(LocalDate.ofEpochDay(entry.epochDay)),
                        color = Theme.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (previous != null) {
                        val diff = entry.weight - previous.weight
                        Text(
                            if (abs(diff) < 0.005) "без изменений" else signedKilograms(diff),
                            color = Theme.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
                Text("${formatWeight(entry.weight)} кг", color = Theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                EntryMenu { onDelete(entry) }
            }
            if (index < items.size - 1) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Theme.hairline))
            }
        }
    }
}

@Composable
private fun EntryMenu(onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .pressable { open = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.MoreHoriz, contentDescription = "Действия с замером", tint = Theme.textTertiary, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Удалить замер", color = Theme.record) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = Theme.record) },
                onClick = {
                    open = false
                    onDelete()
                }
            )
        }
    }
}

private fun signedKilograms(value: Double): String {
    val sign = when {
        value > 0.004 -> "+"
        value < -0.004 -> "−"
        else -> ""
    }
    return "$sign${formatWeight(abs(value))} кг"
}

private fun lastMeasurementLine(series: List<BodyWeightEntry>): String {
    val last = series.lastOrNull() ?: return "Запиши первый замер — от него пойдёт график."
    val today = LocalDate.now().toEpochDay()
    return when (last.epochDay) {
        today -> "Замер сегодня"
        today - 1 -> "Последний замер вчера"
        else -> "Последний замер ${RuDate.dayMonth(LocalDate.ofEpochDay(last.epochDay))}"
    }
}
