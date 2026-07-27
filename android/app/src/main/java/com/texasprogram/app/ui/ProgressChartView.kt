package com.texasprogram.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.CompletionRecord
import com.texasprogram.app.service.RuDate
import java.time.LocalDate

/// График рабочих весов по датам выполненных тренировок.
///
/// Рисуется вручную на Canvas: тянуть графическую библиотеку ради трёх линий незачем.
@Composable
fun ProgressChartView(records: List<CompletionRecord>, modifier: Modifier = Modifier) {
    val sorted = records.sortedBy { it.epochDay }
    val days = sorted.map { it.epochDay }.distinct()

    CardView(modifier) {
        Text("Рабочие веса", color = Theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)

        if (days.size < 2) {
            Text("Пока нечего показывать", color = Theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "График появится после двух отмеченных тренировок в разные дни.",
                color = Theme.textSecondary,
                fontSize = 12.sp
            )
            return@CardView
        }

        val series = listOf(
            Triple("Присед", Theme.accent, sorted.mapNotNull { r -> r.squat?.let { r.epochDay to it } }),
            Triple("Жим", Theme.accentDeep, sorted.mapNotNull { r -> r.bench?.let { r.epochDay to it } }),
            Triple("Тяга", Theme.record, sorted.mapNotNull { r -> r.deadlift?.let { r.epochDay to it } })
        ).filter { it.third.size >= 2 }

        val allWeights = series.flatMap { it.third.map { point -> point.second } }
        val minWeight = (allWeights.minOrNull() ?: 0.0)
        val maxWeight = (allWeights.maxOrNull() ?: 1.0)
        val minDay = days.first()
        val maxDay = days.last()

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            fun x(day: Long): Float =
                if (maxDay == minDay) size.width / 2
                else ((day - minDay).toFloat() / (maxDay - minDay).toFloat()) * size.width

            fun y(weight: Double): Float {
                val span = (maxWeight - minWeight).takeIf { it > 0.5 } ?: 1.0
                val ratio = ((weight - minWeight) / span).toFloat()
                return size.height - ratio * (size.height - 12f) - 6f
            }

            // Горизонтальная сетка на четверти высоты.
            for (i in 0..3) {
                val gy = size.height * i / 3f
                drawLine(
                    Color.White.copy(alpha = 0.06f),
                    Offset(0f, gy),
                    Offset(size.width, gy),
                    strokeWidth = 1f
                )
            }

            for ((_, color, points) in series) {
                val path = Path()
                points.forEachIndexed { index, (day, weight) ->
                    val px = x(day)
                    val py = y(weight)
                    if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(path, color, style = Stroke(width = 5f))
                points.forEach { (day, weight) ->
                    drawCircle(color, radius = 5f, center = Offset(x(day), y(weight)))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            series.forEach { (name, color, _) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Spacer(Modifier.size(8.dp).clip(CircleShape).background(color))
                    Text(name, color = Theme.textSecondary, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            Text("${minWeight.toInt()}–${maxWeight.toInt()} кг", color = Theme.textTertiary, fontSize = 11.sp)
        }
    }
}

/// Список последних тренировок с датами.
@Composable
fun HistoryCard(records: List<CompletionRecord>, modifier: Modifier = Modifier) {
    val recent = records.sortedByDescending { it.epochDay }.take(8)
    if (recent.isEmpty()) return

    CardView(modifier) {
        Text("Последние тренировки", color = Theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        recent.forEach { record ->
            val date = LocalDate.ofEpochDay(record.epochDay)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    RuDate.short(RuDate.weekdayOf(date)).uppercase(),
                    color = Theme.accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.width(24.dp)
                )
                Text("Неделя ${record.week} · день ${record.day}", color = Theme.textPrimary, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(RuDate.dayMonth(date), color = Theme.textSecondary, fontSize = 10.sp)
            }
        }
    }
}
