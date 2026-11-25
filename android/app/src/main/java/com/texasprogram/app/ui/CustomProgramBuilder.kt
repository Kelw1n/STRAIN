package com.texasprogram.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
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
import com.texasprogram.app.model.Superset
import com.texasprogram.app.model.formatWeight
import com.texasprogram.app.service.L

/// Сборка своей программы: схема разбивки, число недель и упражнения по дням.
@Composable
fun CustomProgramBuilder(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    /// Готовая программа, например скопированная у друга из ленты.
    initial: CustomProgram? = null,
    title: String = "Своя программа",
    saveTitle: String = "Запустить программу",
    onSave: (CustomProgram) -> Unit
) {
    var program by remember {
        mutableStateOf(initial ?: CustomProgram.skeleton(SplitTemplate.FULL_BODY, "Своя программа", 8))
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
                    if (day.id == draft.first) day.copy(
                        exercises = day.exercises.filterNot { it.id == exerciseId },
                        supersets = day.supersets.filterNot { it.contains(exerciseId) }
                    ) else day
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
            ScreenTitle(title) {
                Box(
                    Modifier
                        .size(40.dp)
                        .pressable(onClick = onBack)
                        .clip(CircleShape)
                        .background(Theme.surfaceSoft),
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

        items(program.days, key = { it.number }) { day ->
            CardView {
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
                if (day.exercises.size >= 2) {
                    SupersetEditor(day) { pairs ->
                        program = program.copy(days = program.days.map {
                            if (it.id == day.id) it.copy(supersets = pairs) else it
                        })
                    }
                }
                SecondaryButton("Добавить упражнение", icon = Icons.Filled.Add) { editing = day.id to null }
            }
        }

        item(key = "start") {
            CardView(Modifier.appearIn(program.days.size + 1)) {
                GradientButton(saveTitle, enabled = program.isRunnable) { onSave(program) }
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

@Composable
private fun SupersetEditor(day: CustomDay, update: (List<Superset>) -> Unit) {
    val pairs = day.validSupersets
    val available = day.exercises.filter { exercise -> pairs.none { it.contains(exercise.id) } }
    var adding by remember { mutableStateOf(false) }
    var firstId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<String?>(null) }

    pairs.forEach { pair ->
        val first = day.exercises.first { it.id == pair.first }
        val second = day.exercises.first { it.id == pair.second }
        Column(Modifier.fillMaxWidth().pressable { editing = pair.id }) {
            Text(first.name + " → " + second.name, color = Theme.textPrimary)
            Text(
                if (pair.restAfterEach == true) "Отдых после каждого подхода" else "Отдых после пары",
                color = Theme.textSecondary,
                fontSize = 11.sp
            )
        }
    }
    if (available.size >= 2) {
        SecondaryButton("Добавить суперсет", icon = Icons.Filled.Add) {
            firstId = null
            adding = true
        }
    }
    if (adding && available.size >= 2) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { androidx.compose.material3.Text(L(if (firstId == null) "Первое упражнение" else "Второе упражнение")) },
            text = {
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(available.filter { it.id != firstId }, key = { it.id }) { exercise ->
                        TextButton(onClick = {
                            val first = firstId
                            if (first == null) firstId = exercise.id
                            else {
                                update(pairs + Superset(first, exercise.id))
                                adding = false
                            }
                        }) { Text(exercise.name) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { adding = false }) { Text("Отмена") } }
        )
    }
    pairs.firstOrNull { it.id == editing }?.let { pair ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Суперсет") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Отдых после каждого подхода", modifier = Modifier.weight(1f))
                    Switch(checked = pair.restAfterEach == true, onCheckedChange = { checked ->
                        update(pairs.map { if (it.id == pair.id) it.copy(restAfterEach = checked) else it })
                    })
                }
            },
            confirmButton = { TextButton(onClick = { editing = null }) { Text("Готово") } },
            dismissButton = {
                TextButton(onClick = {
                    update(pairs.filterNot { it.id == pair.id })
                    editing = null
                }) { Text("Разъединить") }
            }
        )
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
                Modifier.size(30.dp).pressable { onMove(-1) }.clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = "Выше", tint = Theme.textSecondary, modifier = Modifier.size(16.dp))
            }
        }
        if (canMoveDown) {
            Box(
                Modifier.size(30.dp).pressable { onMove(1) }.clip(CircleShape),
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

    var advanceAfterTarget by remember(existing) { mutableStateOf(existing?.advanceAfterTarget == true) }
    fun number(text: String) = text.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it.isFinite() }
    val sets = (setsText.toIntOrNull() ?: 3).coerceIn(1, 12)
    val validProgression = !advanceAfterTarget || (
        (number(weight) ?: -1.0) in 0.0..1000.0 &&
            (number(increment) ?: 0.0) in 0.001..1000.0 && reps.any(Char::isDigit)
        )

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Прибавлять после всех повторов", modifier = Modifier.weight(1f), color = Theme.textPrimary)
                    Switch(checked = advanceAfterTarget, onCheckedChange = { advanceAfterTarget = it })
                }
                DecimalField(
                    value = increment,
                    label = if (advanceAfterTarget) "Шаг прибавки, кг" else "Прибавка за неделю, кг",
                    onValueChange = { increment = it }
                )
                Text(
                    if (advanceAfterTarget)
                        "Прибавка сработает, когда ты выполнишь все подходы с верхней границей повторов на целевом весе."
                    else "Прибавка работает только вместе с весом: каждую следующую неделю к нему добавится указанное и округлится до блина.",
                    color = Theme.textTertiary,
                    fontSize = 11.sp
                )
            }
        }

        item(key = "actions") {
            CardView(Modifier.appearIn(2)) {
                GradientButton(if (existing == null) "Добавить" else "Сохранить", enabled = name.isNotBlank() && validProgression) {
                    onSave(
                        CustomExercise(
                            id = existing?.id ?: ("own-" + java.util.UUID.randomUUID().toString()),
                            name = name.trim(),
                            sets = sets,
                            reps = reps,
                            kilograms = number(weight),
                            loadText = loadText.trim().ifBlank { null },
                            weeklyIncrement = number(increment) ?: 0.0,
                            advanceAfterTarget = true.takeIf { advanceAfterTarget }
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
