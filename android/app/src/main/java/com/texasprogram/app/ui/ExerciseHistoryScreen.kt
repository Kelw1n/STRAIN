package com.texasprogram.app.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.ExerciseHistoryPoint
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.formatWeight

/// Весь путь одного движения по текущему циклу.
///
/// Записанные подходы копились ради этого экрана: без него они лежат по дням
/// и увидеть, что присед за десять недель прибавил двадцать кило, нельзя.
@Composable
fun ExerciseHistoryScreen(
    profile: ProgramProfile,
    name: String,
    contentPadding: PaddingValues
) {
    val points = remember(profile, name) { profile.history(name) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "title") { ScreenTitle(name) }

        if (points.isEmpty()) {
            item(key = "empty") {
                CardView(Modifier.appearIn(0)) {
                    Text("Здесь пока пусто", color = Theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Нажми на счётчик подходов в карточке упражнения и впиши, сколько получилось. Ещё быстрее: «Всё прошло по плану» под таймером отдыха.",
                        color = Theme.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            return@LazyColumn
        }

        item(key = "summary") { SummaryCard(points, Modifier.appearIn(0)) }
        item(key = "chart") { HistoryChart(points, Modifier.appearIn(1)) }

        items(points, key = { "${it.week}-${it.day}" }) { point ->
            PointRow(point)
        }
    }
}

/// Первый и последний рабочий вес: главный ответ этого экрана.
@Composable
private fun SummaryCard(points: List<ExerciseHistoryPoint>, modifier: Modifier = Modifier) {
    val first = points.first().best
    val last = points.last().best
    val delta = last - first

    HighlightCard(modifier) {
        Text(
            "ЛУЧШИЙ ПОДХОД",
            color = Theme.accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.3.sp
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "${formatWeight(last)} кг",
                color = Theme.textPrimary,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            )
            if (delta != 0.0) {
                Text(
                    (if (delta > 0) "+" else "") + "${formatWeight(delta)} кг",
                    color = if (delta > 0) Theme.success else Theme.record,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
        Text(
            if (delta > 0) "было ${formatWeight(first)} кг — за ${points.size} ${workoutWord(points.size)}"
            else "записано ${points.size} ${workoutWord(points.size)}",
            color = Theme.textSecondary,
            fontSize = 12.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Stat("Всего подходов", "${points.sumOf { it.entries.size }}")
            Stat("Повторов", "${points.sumOf { it.totalReps }}")
        }
    }
}

@Composable
private fun Stat(title: String, value: String) {
    Column {
        Text(value, color = Theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(title, color = Theme.textSecondary, fontSize = 11.sp)
    }
}

/// План пунктиром, факт точками: видно и то, что назначено, и то, что вышло.
@Composable
private fun HistoryChart(points: List<ExerciseHistoryPoint>, modifier: Modifier = Modifier) {
    CardView(modifier) {
        Text("Вес по неделям", color = Theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)

        val facts = points.map { it.week to it.best }
        val plans = points.mapNotNull { point -> point.planned?.let { point.week to it } }
        val all = facts.map { it.second } + plans.map { it.second }
        val minWeight = all.minOrNull() ?: 0.0
        val maxWeight = all.maxOrNull() ?: 1.0
        val minWeek = points.minOf { it.week }
        val maxWeek = points.maxOf { it.week }

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
        ) {
            fun x(week: Int): Float =
                if (maxWeek == minWeek) size.width / 2
                else ((week - minWeek).toFloat() / (maxWeek - minWeek).toFloat()) * size.width

            fun y(weight: Double): Float {
                val span = (maxWeight - minWeight).takeIf { it > 0.5 } ?: 1.0
                val ratio = ((weight - minWeight) / span).toFloat()
                return size.height - ratio * (size.height - 14f) - 7f
            }

            for (i in 0..3) {
                val gy = size.height * i / 3f
                drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
            }

            if (plans.size >= 2) {
                val path = Path()
                plans.forEachIndexed { index, (week, weight) ->
                    if (index == 0) path.moveTo(x(week), y(weight)) else path.lineTo(x(week), y(weight))
                }
                drawPath(
                    path,
                    Theme.textTertiary,
                    style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                )
            }

            facts.forEach { (week, weight) -> drawCircle(Theme.accent, radius = 7f, center = Offset(x(week), y(weight))) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(Theme.accent, "факт")
            Legend(Theme.textTertiary, "план")
            Spacer(Modifier.weight(1f))
            Text(
                "${formatWeight(minWeight)}–${formatWeight(maxWeight)} кг",
                color = Theme.textTertiary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Spacer(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text, color = Theme.textSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun PointRow(point: ExerciseHistoryPoint) {
    CardView(padding = 14.dp, spacing = 7.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Неделя ${point.week}, день ${point.day}",
                color = Theme.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${formatWeight(point.best)} кг",
                color = Theme.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            point.entries.joinToString("   ") { "${it.reps}×${formatWeight(it.weight)}" },
            color = Theme.textSecondary,
            fontSize = 12.sp
        )
        if (point.planned != null && point.planned != point.best) {
            Text(
                "по плану было ${formatWeight(point.planned)} кг",
                color = if (point.best < point.planned) Theme.warning else Theme.success,
                fontSize = 11.sp
            )
        }
    }
}

/// Список движений, по которым есть записи, — вход в историю с экрана прогресса.
@Composable
fun ExerciseHistoryListScreen(
    profile: ProgramProfile,
    onOpen: (String) -> Unit,
    onUpdate: (ProgramProfile) -> Unit,
    contentPadding: PaddingValues
) {
    val names = profile.loggedExerciseNames
    val pending = profile.backfillableWorkouts
    var confirming by remember { mutableStateOf(false) }
    var filledMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "title") { ScreenTitle("История") }

        if (pending > 0) {
            item(key = "backfill") {
                HighlightCard(Modifier.appearIn(0)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.History, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(17.dp))
                        Text("Есть отмеченные тренировки", color = Theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "$pending ${workoutWord(pending)} отмечено, но веса в них не записаны. Можно проставить плановые — история посчитается задним числом.",
                        color = Theme.textSecondary,
                        fontSize = 12.sp
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(13.dp))
                            .background(Theme.accentGradient)
                            .pressable { confirming = true }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Заполнить по отметкам", color = Theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        filledMessage?.let { message ->
            item(key = "filled") {
                Text(message, color = Theme.success, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }

        if (names.isEmpty()) {
            item(key = "empty") {
                CardView(Modifier.appearIn(1)) {
                    Text("Записей пока нет", color = Theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Нажми на счётчик подходов в карточке упражнения и впиши, сколько получилось. Как появятся записи, движения соберутся сюда.",
                        color = Theme.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        items(names, key = { it }) { name ->
            val points = profile.history(name)
            CardView(
                Modifier.pressable { onOpen(name) },
                padding = 15.dp,
                spacing = 4.dp
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, color = Theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${points.size} трен. · лучший ${formatWeight(points.maxOfOrNull { it.best } ?: 0.0)} кг",
                            color = Theme.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("›", color = Theme.textSecondary, fontSize = 20.sp)
                }
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Заполнить историю плановыми весами?") },
            text = {
                Text(
                    "Возьмутся веса из плана этих недель. Там, где ты уже вводил свои числа, ничего не изменится. Упражнения без веса в плане — по РПЕ и на количество — пропустятся."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val (updated, filled) = profile.backfillHistoryFromMarks()
                    onUpdate(updated)
                    filledMessage = "Заполнено $filled ${workoutWord(filled)}. Что помнишь иначе — поправь через счётчик подходов."
                    confirming = false
                }) { Text("Заполнить", color = Theme.accent) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Отмена") } },
            containerColor = Theme.dialog,
            titleContentColor = Theme.textPrimary,
            textContentColor = Theme.textSecondary
        )
    }
}

private fun workoutWord(count: Int): String {
    val last = count % 10
    val hundred = count % 100
    return when {
        hundred in 11..14 -> "тренировок"
        last == 1 -> "тренировка"
        last in 2..4 -> "тренировки"
        else -> "тренировок"
    }
}
