package com.texasprogram.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.ExercisePrescription
import com.texasprogram.app.model.LoadPrescription
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.WeeklyTonnage
import com.texasprogram.app.model.formatTonnage
import com.texasprogram.app.model.formatWeight

/// Какой подход записываем.
data class SetEntryTarget(
    val week: Int,
    val day: Int,
    val exercise: ExercisePrescription,
    val index: Int
)

/// Ввод фактического результата подхода: сколько повторов и с каким весом.
///
/// План говорит, что делать, а это — что получилось. Поля заполняются
/// предписанными значениями, чтобы в обычном случае хватило одного нажатия.
@Composable
fun SetEntryDialog(
    profile: ProgramProfile,
    target: SetEntryTarget,
    onDismiss: () -> Unit,
    onUpdate: (ProgramProfile) -> Unit
) {
    val existing = profile.setEntry(target.week, target.day, target.exercise, target.index)
    val plannedWeight = (target.exercise.load as? LoadPrescription.Kilograms)?.value
    val plannedReps = remember(target) { plannedReps(target.exercise.reps, target.index) }

    var reps by remember(target) {
        mutableStateOf(existing?.reps?.toString() ?: plannedReps?.toString() ?: "")
    }
    var weight by remember(target) {
        mutableStateOf(existing?.weight?.let { formatWeight(it) } ?: plannedWeight?.let { formatWeight(it) } ?: "")
    }

    val repsValue = reps.trim().toIntOrNull()?.takeIf { it > 0 }
    val weightValue = weight.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it >= 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подход ${target.index + 1}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(target.exercise.name, color = Theme.textPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    "По плану: " + (if (target.exercise.sets > 0) "${target.exercise.sets} × ${target.exercise.reps}" else target.exercise.reps) +
                        " · " + target.exercise.load.displayText,
                    color = Theme.textSecondary,
                    fontSize = 12.sp
                )
                TextRow(reps, "Повторов") { reps = it }
                DecimalField(value = weight, label = "Вес, кг", onValueChange = { weight = it })
                Text(
                    "Записанные подходы идут в тоннаж и во вторую линию графика — она показывает факт рядом с планом.",
                    color = Theme.textTertiary,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = repsValue != null && weightValue != null,
                onClick = {
                    if (repsValue != null && weightValue != null) {
                        onUpdate(profile.recordSet(target.week, target.day, target.exercise, target.index, repsValue, weightValue))
                    }
                    onDismiss()
                }
            ) { Text("Записать") }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(onClick = {
                        onUpdate(profile.clearSetEntry(target.week, target.day, target.exercise, target.index))
                        onDismiss()
                    }) { Text("Убрать", color = Theme.record) }
                }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
        containerColor = Theme.surfaceSoft,
        titleContentColor = Theme.textPrimary,
        textContentColor = Theme.textPrimary
    )
}

/// Предписанные повторы: из «5» берём 5, из «6 / 5 / 5 / 4 / 4» — нужный подход,
/// из «8–12» — первое число. Диапазон вписать нельзя, поэтому берём разумное.
private fun plannedReps(raw: String, index: Int): Int? {
    val parts = raw.split("/").map { it.trim() }
    if (parts.size > 1 && index < parts.size) {
        parts[index].toIntOrNull()?.let { return it }
    }
    return Regex("\\d+").find(raw)?.value?.toIntOrNull()
}

/// «Не пришёл на тренировку».
///
/// Пропуск сам по себе расписание не ломает — невыполненное едет вперёд. Ломает
/// то, что веса растут по календарю: после перерыва штанга тяжелее, хотя стимула
/// не было. Поэтому пропуск умеет задержать прогрессию до возмещения.
@Composable
fun SkipCard(
    profile: ProgramProfile,
    week: Int,
    day: Int,
    modifier: Modifier = Modifier,
    onUpdate: (ProgramProfile) -> Unit
) {
    var asking by remember { mutableStateOf(false) }
    val held = profile.holdCount(week + 1)

    CardView(modifier) {
        if (profile.isSkipped(week, day)) {
            Text("Тренировка отмечена пропущенной", color = Theme.warning, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        } else {
            SecondaryButton("Не пришёл на тренировку", icon = Icons.Filled.EventBusy, tint = Theme.warning) { asking = true }
        }
        if (held > 0) {
            Text(
                "Прогрессия задержана на $held нед.: веса не растут, пока не закроешь пропущенное.",
                color = Theme.textSecondary,
                fontSize = 11.sp
            )
        }
    }

    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("Пропустил эту тренировку?") },
            text = {
                Text(
                    "Тренировка останется в расписании, её можно закрыть позже. С задержкой следующие недели повторят текущие веса — вернёшься к той же штанге, а не к более тяжёлой.",
                    color = Theme.textSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdate(profile.markSkipped(week, day, true)); asking = false
                }) { Text("Задержать веса") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        onUpdate(profile.markSkipped(week, day, false)); asking = false
                    }) { Text("Просто пропустить") }
                    TextButton(onClick = { asking = false }) { Text("Отмена") }
                }
            },
            containerColor = Theme.surfaceSoft,
            titleContentColor = Theme.textPrimary,
            textContentColor = Theme.textPrimary
        )
    }
}

/// Тоннаж по неделям: сумма «повторы × вес» записанных подходов.
///
/// Считается только по факту. Догадываться по плану нельзя: повторы там бывают
/// диапазоном и списком, и любая догадка дала бы выдуманное число.
@Composable
fun TonnageCard(weeks: List<WeeklyTonnage>, modifier: Modifier = Modifier) {
    CardView(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Тоннаж", color = Theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (weeks.isNotEmpty()) {
                Text(
                    formatTonnage(weeks.sumOf { it.total }),
                    color = Theme.accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (weeks.isEmpty()) {
            Text(
                "Записывай подходы долгим нажатием на кружок — тоннаж считается по ним.",
                color = Theme.textSecondary,
                fontSize = 12.sp
            )
        } else {
            val top = weeks.maxOf { it.total }.coerceAtLeast(1.0)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(top = 6.dp)
            ) {
                val gap = size.width / (weeks.size * 2f)
                val barWidth = gap
                weeks.forEachIndexed { index, item ->
                    val height = (item.total / top * size.height).toFloat()
                    drawRect(
                        color = Theme.accent,
                        topLeft = Offset(index * (barWidth + gap) + gap / 2, size.height - height),
                        size = Size(barWidth, height)
                    )
                }
            }
            Text(
                "${weeks.sumOf { it.setCount }} записанных подходов за ${weeks.size} нед.",
                color = Theme.textSecondary,
                fontSize = 11.sp
            )
        }
    }
}

/// Серия закрытых недель. Показываем только когда есть что показать:
/// нули на пустом профиле выглядят как упрёк.
@Composable
fun StreakCard(current: Int, best: Int, modifier: Modifier = Modifier) {
    if (best <= 0) return
    CardView(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("СЕРИЯ", color = Theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("$current", color = Theme.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(weekWord(current), color = Theme.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text("подряд без пропусков", color = Theme.textSecondary, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("ЛУЧШАЯ", color = Theme.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(
                    "$best",
                    color = if (current >= best) Theme.success else Theme.textSecondary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/// «1 неделя», «2 недели», «5 недель» — без этого счётчик читается коряво.
private fun weekWord(count: Int): String {
    val tens = count % 100
    if (tens in 11..14) return "недель"
    return when (count % 10) {
        1 -> "неделя"
        2, 3, 4 -> "недели"
        else -> "недель"
    }
}

/// Новый заход по программе с новыми максимумами.
///
/// Числа вводятся заново, а не пересчитываются из теста 1ПМ: техасская программа
/// считается от пятиповторного максимума, и в самой таблице сказано его протестировать.
@Composable
fun NewCycleDialog(
    profile: ProgramProfile,
    onDismiss: () -> Unit,
    onUpdate: (ProgramProfile) -> Unit
) {
    var squat by remember { mutableStateOf(formatWeight(profile.squat5RM)) }
    var bench by remember { mutableStateOf(formatWeight(profile.bench5RM)) }
    var deadlift by remember { mutableStateOf(formatWeight(profile.deadlift5RM)) }

    fun number(text: String) = text.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it > 0 }
    val isReady = number(squat) != null && number(bench) != null && number(deadlift) != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый цикл") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Цикл ${profile.cycleNumber} закрыт. Отметки начнутся заново, история и графики останутся сквозными.",
                    color = Theme.textSecondary,
                    fontSize = 12.sp
                )
                DecimalField(value = squat, label = "Приседания, ${profile.maximumLabel}", onValueChange = { squat = it })
                DecimalField(value = bench, label = "Жим лёжа, ${profile.maximumLabel}", onValueChange = { bench = it })
                DecimalField(value = deadlift, label = "Становая тяга, ${profile.maximumLabel}", onValueChange = { deadlift = it })
                if (profile.programKind.usesFiveRepMax) {
                    Text(
                        "Пятиповторный максимум и результат теста 1ПМ — разные числа. Ориентировочный 5ПМ около 86–89 % от одноповторного, но это прикидка: проверь на первой тяжёлой тренировке.",
                        color = Theme.textTertiary,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isReady,
                onClick = {
                    val s = number(squat); val b = number(bench); val d = number(deadlift)
                    if (s != null && b != null && d != null) onUpdate(profile.startNewCycle(s, b, d))
                    onDismiss()
                }
            ) { Text("Начать цикл ${profile.cycleNumber + 1}") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        containerColor = Theme.surfaceSoft,
        titleContentColor = Theme.textPrimary,
        textContentColor = Theme.textPrimary
    )
}
