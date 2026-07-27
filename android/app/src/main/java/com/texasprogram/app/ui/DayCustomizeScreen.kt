package com.texasprogram.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.ExercisePrescription
import com.texasprogram.app.model.LoadPrescription
import com.texasprogram.app.model.PlanEdit
import com.texasprogram.app.model.PlanEditKind
import com.texasprogram.app.model.PlanEditScope
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.formatWeight

/// Черновик своего упражнения: то, что пользователь набирает в форме.
data class ExerciseDraft(
    /// Что заменяем. `null` — добавляем новое упражнение.
    val target: ExercisePrescription? = null,
    val name: String = "",
    val sets: Int = 3,
    val reps: String = "8–12",
    val weightText: String = "",
    val loadText: String = ""
) {
    val trimmedName: String get() = name.trim()

    /// Запятая как разделитель: на русской раскладке она под рукой.
    val kilograms: Double?
        get() = weightText.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it > 0 }

    val load: String? get() = loadText.trim().ifBlank { null }
}

/// Настройка одного дня программы: заменить, убрать или дописать своё упражнение.
@Composable
fun DayCustomizeScreen(
    profile: ProgramProfile,
    week: Int,
    dayNumber: Int,
    onUpdate: (ProgramProfile) -> Unit,
    contentPadding: PaddingValues
) {
    var scope by remember { mutableStateOf(PlanEditScope.EVERY_WEEK) }
    var draft by remember { mutableStateOf<ExerciseDraft?>(null) }

    val generated = profile.generatedExercises(week, dayNumber)
    val dayEdits = profile.edits(week, dayNumber)
    val added = dayEdits.filter { it.kind == PlanEditKind.ADD }

    val current = draft
    if (current != null) {
        ExerciseForm(
            draft = current,
            scope = scope,
            contentPadding = contentPadding,
            onCancel = { draft = null },
            onSave = { value ->
                if (value.trimmedName.isNotEmpty()) {
                    val target = value.target
                    onUpdate(
                        if (target != null) {
                            profile.replaceExercise(
                                target, value.trimmedName, value.sets, value.reps,
                                value.kilograms, value.load, week, dayNumber, scope
                            )
                        } else {
                            profile.addExercise(
                                value.trimmedName, value.sets, value.reps,
                                value.kilograms, value.load, week, dayNumber, scope
                            )
                        }
                    )
                }
                draft = null
            }
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "title") { ScreenTitle("Свои упражнения") }

        item(key = "scope") {
            CardView(Modifier.appearIn(0)) {
                SectionLabel("Куда применить")
                SegmentedControl(
                    options = PlanEditScope.entries.map { it.title },
                    selectedIndex = PlanEditScope.entries.indexOf(scope)
                ) { scope = PlanEditScope.entries[it] }
                Text(scope.explanation, color = Theme.textTertiary, fontSize = 11.sp)
            }
        }

        item(key = "program") {
            CardView(Modifier.appearIn(1)) {
                SectionLabel("Упражнения программы")
                generated.forEach { exercise ->
                    ProgramExerciseRow(
                        exercise = exercise,
                        edit = dayEdits.firstOrNull { it.targetKey == exercise.key },
                        onReplace = {
                            draft = ExerciseDraft(
                                target = exercise,
                                name = exercise.name,
                                sets = exercise.sets.coerceAtLeast(1),
                                reps = exercise.reps,
                                weightText = weightText(exercise)
                            )
                        },
                        onHide = { onUpdate(profile.hideExercise(exercise, week, dayNumber, scope)) },
                        onRestore = { edit -> onUpdate(profile.removeEdit(edit)) }
                    )
                }
                Text(
                    "Приседания, жим и становая кормят график прогресса. Если заменить или убрать их, в графике за эти дни будет пусто.",
                    color = Theme.textTertiary,
                    fontSize = 11.sp
                )
            }
        }

        item(key = "added") {
            CardView(Modifier.appearIn(2)) {
                SectionLabel("Свои упражнения")
                added.forEach { edit ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f, fill = false)) {
                            Text(edit.name, color = Theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                edit.detail + " · " + edit.scope.title.lowercase(),
                                color = Theme.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        IconAction(Icons.Filled.Delete, "Удалить", Theme.record) {
                            onUpdate(profile.removeEdit(edit))
                        }
                    }
                }
                SecondaryButton("Добавить своё упражнение", icon = Icons.Filled.Add) {
                    draft = ExerciseDraft()
                }
                if (added.isEmpty()) {
                    Text(
                        "Упражнение встанет в конец дня и будет отмечаться по подходам наравне с остальными.",
                        color = Theme.textTertiary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        if (dayEdits.isNotEmpty()) {
            item(key = "reset") {
                CardView(Modifier.appearIn(3)) {
                    SecondaryButton("Вернуть день как было", icon = Icons.Filled.Undo, tint = Theme.record) {
                        onUpdate(profile.removeEdits(week, dayNumber))
                    }
                    Text(
                        "Уберёт все правки этого дня и вернёт упражнения программы.",
                        color = Theme.textTertiary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgramExerciseRow(
    exercise: ExercisePrescription,
    edit: PlanEdit?,
    onReplace: () -> Unit,
    onHide: () -> Unit,
    onRestore: (PlanEdit) -> Unit
) {
    val hidden = edit?.kind == PlanEditKind.HIDE
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                exercise.name,
                color = if (hidden) Theme.textTertiary else Theme.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (hidden) TextDecoration.LineThrough else null,
                maxLines = 2
            )
            Text(
                if (edit != null) edit.summary + " · " + edit.scope.title.lowercase() else subtitle(exercise),
                color = if (edit != null) Theme.warning else Theme.textSecondary,
                fontSize = 11.sp,
                maxLines = 2
            )
        }
        Spacer(Modifier.width(8.dp))
        if (edit != null) {
            IconAction(Icons.Filled.Undo, "Вернуть как было", Theme.accent) { onRestore(edit) }
        } else {
            IconAction(Icons.Filled.Refresh, "Заменить", Theme.accent, onClick = onReplace)
            Spacer(Modifier.width(4.dp))
            IconAction(Icons.Filled.VisibilityOff, "Убрать из дня", Theme.record, onClick = onHide)
        }
    }
}

@Composable
private fun IconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ExerciseForm(
    draft: ExerciseDraft,
    scope: PlanEditScope,
    contentPadding: PaddingValues,
    onCancel: () -> Unit,
    onSave: (ExerciseDraft) -> Unit
) {
    var value by remember(draft) { mutableStateOf(draft) }
    var setsText by remember(draft) { mutableStateOf(draft.sets.toString()) }
    // Подходов не бывает ноль и не бывает двадцать: поле свободное, но при
    // сохранении число зажимается в разумные границы.
    val sets = (setsText.toIntOrNull() ?: draft.sets).coerceIn(1, 12)

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "title") {
            ScreenTitle(if (draft.target != null) "Замена" else "Своё упражнение")
        }

        item(key = "fields") {
            CardView(Modifier.appearIn(0)) {
                SectionLabel("Упражнение")
                TextRow(value.name, "Название") { value = value.copy(name = it) }
                WeightStepper(label = "Подходов", text = setsText, step = 1.0) { setsText = it }
                TextRow(value.reps, "Повторения, например 8–12") { value = value.copy(reps = it) }
            }
        }

        item(key = "load") {
            CardView(Modifier.appearIn(1)) {
                SectionLabel("Нагрузка")
                DecimalField(
                    value = value.weightText,
                    label = "Вес, кг",
                    onValueChange = { value = value.copy(weightText = it) }
                )
                TextRow(value.loadText, "Или подпись: RPE 8, до отказа") { value = value.copy(loadText = it) }
                Text(
                    "Если указать вес, приложение посчитает блины и разминку. Оставишь пусто — покажет подпись.",
                    color = Theme.textTertiary,
                    fontSize = 11.sp
                )
            }
        }

        item(key = "save") {
            CardView(Modifier.appearIn(2)) {
                SecondaryButton(
                    if (draft.target != null) "Заменить" else "Добавить",
                    icon = Icons.Filled.Check
                ) { onSave(value.copy(sets = sets)) }
                SecondaryButton("Отмена", tint = Theme.textSecondary, onClick = onCancel)
                Text(scope.explanation, color = Theme.textTertiary, fontSize = 11.sp)
            }
        }
    }
}

private fun weightText(exercise: ExercisePrescription): String {
    val load = exercise.load
    return if (load is LoadPrescription.Kilograms) formatWeight(load.value) else ""
}

private fun subtitle(exercise: ExercisePrescription): String {
    val volume = if (exercise.sets > 0) "${exercise.sets} × ${exercise.reps}" else exercise.reps
    return volume + " · " + exercise.load.displayText
}
