package com.texasprogram.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.TrainingHeatmap
import com.texasprogram.app.service.RuPlural
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// MARK: - Карта тренировок за год

private val Cell = 12.dp
private val Gap = 3.dp
private val MonthsHeight = 16.dp
private val monthNames = listOf("янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
private val heatmapDay = DateTimeFormatter.ofPattern("EE, d MMMM", Locale.forLanguageTag("ru"))

internal fun heatmapColor(level: Int): Color = when (level) {
    1 -> Theme.accent.copy(alpha = 0.3f)
    2 -> Theme.accent.copy(alpha = 0.52f)
    3 -> Theme.accent.copy(alpha = 0.76f)
    4 -> Theme.accent
    else -> Theme.tone(0xFF292929)
}

private fun heatmapWord(level: Int): String = when (level) {
    1 -> "лёгкая тренировка"
    2 -> "тренировка"
    3 -> "тяжёлая тренировка"
    4 -> "очень тяжёлая тренировка"
    else -> "отдых"
}

/// Год тренировок клеточками, как на GitHub: колонка — неделя с понедельника,
/// свежие справа. Чем тяжелее день, тем ярче клеточка. Нажатие — какой это день.
@Composable
fun ProfileHeatmapCard(levels: List<Int>, today: LocalDate = LocalDate.now()) {
    val count = TrainingHeatmap.visibleCount(today)
    val cells = remember(levels, count) { levels.takeLast(count) }
    val columns = (cells.size + 6) / 7
    val workouts = TrainingHeatmap.workouts(levels)
    val label = "$workouts ${RuPlural.word(workouts, "тренировка", "тренировки", "тренировок")}"
    var selected by remember(cells) { mutableStateOf<Int?>(null) }
    val haptics = rememberHapticEngine()
    // Подпись месяца над колонкой, с которой он начинается.
    val months = remember(count, today) {
        val labels = mutableListOf<Pair<Int, String>>()
        var previous: Int? = null
        for (column in 0 until columns) {
            val monday = today.plusDays((column * 7 - (count - 1)).toLong())
            if (monday.monthValue != previous) {
                // Первая колонка с обрезком месяца — подпись налезла бы на следующую.
                if (previous != null || monday.dayOfMonth <= 7) labels += column to monthNames[monday.monthValue - 1]
                previous = monday.monthValue
            }
        }
        labels
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Theme.tone(0xFF161617))
            .padding(18.dp)
            .semantics { contentDescription = "Год тренировок: $label" },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ГОД ТРЕНИРОВОК", color = Theme.ink.copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, modifier = Modifier.weight(1f))
            Text(label, color = Theme.ink.copy(alpha = 0.75f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        // Свежие недели справа — прокрутка начинается с конца.
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState(), reverseScrolling = true)) {
            val width = Cell * columns + Gap * (columns - 1)
            Box(Modifier.width(width).height(MonthsHeight + Cell * 7 + Gap * 6)) {
                months.forEach { (column, text) ->
                    Text(
                        text,
                        color = Theme.ink.copy(alpha = 0.45f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.offset(x = (Cell + Gap) * column)
                    )
                }
                Canvas(
                    Modifier
                        .offset(y = MonthsHeight)
                        .width(width)
                        .height(Cell * 7 + Gap * 6)
                        .pointerInput(cells) {
                            detectTapGestures { position ->
                                val step = (Cell + Gap).toPx()
                                val column = (position.x / step).toInt()
                                val row = (position.y / step).toInt()
                                val index = column * 7 + row
                                if (row in 0..6 && index in cells.indices) {
                                    haptics.light()
                                    selected = if (selected == index) null else index
                                }
                            }
                        }
                ) {
                    val cell = Cell.toPx()
                    val step = (Cell + Gap).toPx()
                    val radius = CornerRadius(3.dp.toPx())
                    cells.forEachIndexed { index, level ->
                        val topLeft = Offset((index / 7) * step, (index % 7) * step)
                        drawRoundRect(heatmapColor(level), topLeft, Size(cell, cell), radius)
                        if (index == selected) {
                            val inset = 1.5.dp.toPx()
                            drawRoundRect(
                                Theme.ink,
                                Offset(topLeft.x - inset, topLeft.y - inset),
                                Size(cell + inset * 2, cell + inset * 2),
                                CornerRadius(4.dp.toPx()),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                    }
                }
            }
        }
        val chosen = selected
        if (chosen != null && chosen in cells.indices) {
            val day = today.minusDays((cells.size - 1 - chosen).toLong())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(heatmapColor(cells[chosen])))
                Text("${day.format(heatmapDay)} — ${heatmapWord(cells[chosen])}", color = Theme.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Меньше", color = Theme.ink.copy(alpha = 0.45f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                (0..4).forEach { level -> Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(heatmapColor(level))) }
                Text("Больше", color = Theme.ink.copy(alpha = 0.45f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
