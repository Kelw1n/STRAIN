package com.texasprogram.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.CustomDay
import com.texasprogram.app.model.CustomExercise
import com.texasprogram.app.model.CustomProgram
import com.texasprogram.app.model.SplitTemplate
import com.texasprogram.app.model.formatWeight

/// Сборка своей программы: схема разбивки, число недель и упражнения по дням.
@Composable
fun CustomProgramBuilder(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSave: (CustomProgram) -> Unit
) {
    var program by remember {
        mutableStateOf(CustomProgram.skeleton(SplitTemplate.FULL_BODY, "Своя программа", 8))
    }
    var editing by remember { mutableStateOf<Pair<String, CustomExercise?>?>(null) }

    val draft = editing
    if (draft != null) {
        CustomExerciseForm(
            contentPadding = contentPadding,
            existing = draft.second,
            onCancel = { editing = null },
            onDelete = { exerciseId ->
                program = program.copy(days = program.days.map { day ->
                    if (day.id == draft.first) day.copy(exercises = day.exercises.filterNot { it.id == exerciseId }) else day
                })
                editing = null
            },
            onSave = { exercise ->
                program = program.copy(days = program.days.map { day ->
                    if (day.id != draft.first) day
                    else if (day.exercises.any { it.id == exercise.id }) {
                        day.copy(exercises = day.exercises.map { if (it.id == exercise.id) exercise else it })
                    } else {
                        day.copy(exercises = day.exercises + exercise)
                    }
                })
                editing = null
            }
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "title") {
            ScreenTitle("Своя программа") {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Theme.surfaceSoft)
                        .pressable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Назад", tint = Theme.textPrimary, modifier = Modifier.size(22.dp))
                }
            }
        }

        item(key = "base") {
            CardView(Modifier.appearIn(0)) {
                SectionLabel("Разбивка")
                TextRow(program.name, "Название") { program = program.copy(name = it) }
                SegmentedControl(
                    options = SplitTemplate.entries.map { it.title },
                    selectedIndex = SplitTemplate.entries.indexOf(program.template)
                ) { index ->
                    val template = SplitTemplate.entries[index]
                    // Смена схемы пересобирает дни: у неё своё число и свои названия.
                    // Вписанные упражнения при этом теряются, поэтому меняем каркас
                    // только когда он действительно другой.
                    program = if (program.days.map { it.title } == template.dayTitles) {
                        program.copy(template = template)
                    } else {
                        CustomProgram.skeleton(template, program.name, program.weeks)
                    }
                }
                Text(program.template.explanation, color = Theme.textTertiary, fontSize = 11.sp)
                WeightStepper(label = "Недель", text = program.weeks.toString(), step = 1.0) { value ->
                    program = program.copy(weeks = (value.toIntOrNull() ?: program.weeks).coerceIn(1, 24))
                }
            }
        }

        program.days.forEachIndexed { dayIndex, day ->
            item(key = "day-${day.id}") {
                CardView(Modifier.appearIn(dayIndex + 1)) {
                    SectionLabel("День ${day.number}")
                    TextRow(day.title, "Название дня") { title ->
                        program = program.copy(days = program.days.map { if (it.id == day.id) it.copy(title = title) else it })
                    }
                    day.exercises.forEachIndexed { index, exercise ->
                        ExerciseRow(
                            exercise = exercise,
                            canMoveUp = index > 0,
                            canMoveDown = index < day.exercises.size - 1,
                            onOpen = { editing = day.id to exercise },
                            onMove = { offset ->
                                program = program.copy(days = program.days.map {
                                    if (it.id == day.id) it.copy(exercises = it.exercises.moved(index, index + offset)) else it
                                })
                            }
                        )
                    }
                    SecondaryButton("Добавить упражнение", icon = Icons.Filled.Add) { editing = day.id to null }
                }
            }
        }

        item(key = "start") {
            CardView(Modifier.appearIn(program.days.size + 1)) {
                GradientButton("Запустить программу", enabled = program.isRunnable) { onSave(program) }
                Text(
                    if (program.isRunnable)
                        "Дни лягут на календарь по настройкам расписания. Программу можно будет править из «Своих упражнений»."
                    else
                        "Чтобы запустить, добавь хотя бы одно упражнение в каждый день.",
                    color = Theme.textTertiary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/// Перестановка элемента списка. Компоуз не умеет перетаскивать из коробки,
/// поэтому порядок меняется стрелками — так надёжнее, чем свой жест.
private fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to || to !in indices) return this
    val result = toMutableList()
    result.add(to, result.removeAt(from))
    return result
}

@Composable
private fun ExerciseRow(
    exercise: CustomExercise,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onMove: (Int) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .pressable(onClick = onOpen)
        ) {
            Text(exercise.name, color = Theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(exercise.detail, color = Theme.textSecondary, fontSize = 11.sp)
        }
        Spacer(Modifier.width(8.dp))
        if (canMoveUp) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).pressable { onMove(-1) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = "Выше", tint = Theme.textSecondary, modifier = Modifier.size(16.dp))
            }
        }
        if (canMoveDown) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).pressable { onMove(1) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ArrowDownward, contentDescription = "Ниже", tint = Theme.textSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun CustomExerciseForm(
    contentPadding: PaddingValues,
    existing: CustomExercise?,
    onCancel: () -> Unit,
    onDelete: (String) -> Unit,
    onSave: (CustomExercise) -> Unit
) {
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var setsText by remember(existing) { mutableStateOf((existing?.sets ?: 3).toString()) }
    var reps by remember(existing) { mutableStateOf(existing?.reps ?: "5") }
    var weight by remember(existing) { mutableStateOf(existing?.kilograms?.let { formatWeight(it) } ?: "") }
    var loadText by remember(existing) { mutableStateOf(existing?.loadText ?: "") }
    var increment by remember(existing) {
        mutableStateOf(existing?.weeklyIncrement?.takeIf { it != 0.0 }?.let { formatWeight(it) } ?: "")
    }

    fun number(text: String) = text.replace(',', '.').trim().toDoubleOrNull()
    val sets = (setsText.toIntOrNull() ?: 3).coerceIn(1, 12)

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "title") { ScreenTitle(if (existing == null) "Новое упражнение" else "Упражнение") }

        item(key = "fields") {
            CardView(Modifier.appearIn(0)) {
                SectionLabel("Упражнение")
                TextRow(name, "Название") { name = it }
                WeightStepper(label = "Подходов", text = setsText, step = 1.0) { setsText = it }
                TextRow(reps, "Повторения, например 5 или 8–12") { reps = it }
            }
        }

        item(key = "load") {
            CardView(Modifier.appearIn(1)) {
                SectionLabel("Нагрузка")
                DecimalField(value = weight, label = "Вес, кг", onValueChange = { weight = it })
                TextRow(loadText, "Или подпись: RPE 8, свой вес") { loadText = it }
                DecimalField(value = increment, label = "Прибавка за неделю, кг", onValueChange = { increment = it })
                Text(
                    "Прибавка работает только вместе с весом: каждую следующую неделю к нему добавится указанное и округлится до блина.",
                    color = Theme.textTertiary,
                    fontSize = 11.sp
                )
            }
        }

        item(key = "actions") {
            CardView(Modifier.appearIn(2)) {
                GradientButton(if (existing == null) "Добавить" else "Сохранить", enabled = name.isNotBlank()) {
                    onSave(
                        CustomExercise(
                            id = existing?.id ?: ("own-" + java.util.UUID.randomUUID().toString()),
                            name = name.trim(),
                            sets = sets,
                            reps = reps,
                            kilograms = number(weight),
                            loadText = loadText.trim().ifBlank { null },
                            weeklyIncrement = number(increment) ?: 0.0
                        )
                    )
                }
                if (existing != null) {
                    SecondaryButton("Убрать из дня", icon = Icons.Filled.Delete, tint = Theme.record) { onDelete(existing.id) }
                }
                SecondaryButton("Отмена", tint = Theme.textSecondary, onClick = onCancel)
            }
        }
    }
}
