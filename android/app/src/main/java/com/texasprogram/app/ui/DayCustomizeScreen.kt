package com.texasprogram.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
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
    val target: ExercisePrescription? = null,
    val existingEdit: PlanEdit? = null,
    val name: String = "",
    val sets: Int = 3,
    val reps: String = "8–12",
    val weightText: String = "",
    val loadText: String = ""
) {
    val trimmedName: String get() = name.trim()

    val kilograms: Double?
        get() = weightText.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it > 0 }

    val load: String? get() = loadText.trim().ifBlank { null }
}

/// Настройка одного дня программы: нажать на любое упражнение для правки/удаления или добавить своё.
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
                    if (target != null) {
                        onUpdate(
                            profile.replaceExercise(
                                target, value.trimmedName, value.sets, value.reps,
                                value.kilograms, value.load, week, dayNumber, scope
                            )
                        )
                    } else if (value.existingEdit != null) {
                        // Редактирование уже добавленного упражнения: заменяем старый edit на новый
                        val withoutOld = profile.removeEdit(value.existingEdit)
                        onUpdate(
                            withoutOld.addExercise(
                                value.trimmedName, value.sets, value.reps,
                                value.kilograms, value.load, week, dayNumber, scope
                            )
                        )
                    } else {
                        onUpdate(
                            profile.addExercise(
                                value.trimmedName, value.sets, value.reps,
                                value.kilograms, value.load, week, dayNumber, scope
                            )
                        )
                    }
                }
                draft = null
            },
            onDelete = {
                val target = current.target
                val edit = current.existingEdit
                if (edit != null) {
                    onUpdate(profile.removeEdit(edit))
                } else if (target != null) {
                    onUpdate(profile.hideExercise(target, week, dayNumber, scope))
                }
                draft = null
            },
            onReset = if (current.existingEdit != null) {
                {
                    onUpdate(profile.removeEdit(current.existingEdit))
                    draft = null
                }
            } else null
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "title") { ScreenTitle("Настройка упражнений") }

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
                SectionLabel("Упражнения программы (нажми для изменения)")
                generated.forEach { exercise ->
                    val currentEdit = dayEdits.firstOrNull { it.targetKey == exercise.key }
                    ProgramExerciseRow(
                        exercise = exercise,
                        edit = currentEdit,
                        onClick = {
                            draft = ExerciseDraft(
                                target = exercise,
                                existingEdit = currentEdit,
                                name = currentEdit?.name ?: exercise.name,
                                sets = currentEdit?.sets ?: exercise.sets.coerceAtLeast(1),
                                reps = currentEdit?.reps ?: exercise.reps,
                                weightText = currentEdit?.kilograms?.let { formatWeight(it) } ?: weightText(exercise),
                                loadText = currentEdit?.loadText ?: ""
                            )
                        }
                    )
                }
                Text(
                    "Нажми на любое упражнение, чтобы изменить название, число подходов, повторений, вес или удалить его из дня.",
                    color = Theme.textTertiary,
                    fontSize = 11.sp
                )
            }
        }

        item(key = "added") {
            CardView(Modifier.appearIn(2)) {
                SectionLabel("Свои упражнения")
                added.forEach { edit ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .pressable {
                                draft = ExerciseDraft(
                                    existingEdit = edit,
                                    name = edit.name,
                                    sets = edit.sets,
                                    reps = edit.reps,
                                    weightText = edit.kilograms?.let { formatWeight(it) } ?: "",
                                    loadText = edit.loadText ?: ""
                                )
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
            }
        }

        if (dayEdits.isNotEmpty()) {
            item(key = "reset") {
                CardView(Modifier.appearIn(3)) {
                    SecondaryButton("Вернуть день как было", icon = Icons.Filled.Undo, tint = Theme.record) {
                        onUpdate(profile.removeEdits(week, dayNumber))
                    }
                    Text(
                        "Уберёт все правки этого дня и вернёт оригинальные упражнения программы.",
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
    onClick: () -> Unit
) {
    val hidden = edit?.kind == PlanEditKind.HIDE
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .pressable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                edit?.name ?: exercise.name,
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
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = "Редактировать",
            tint = Theme.textTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ExerciseForm(
    draft: ExerciseDraft,
    scope: PlanEditScope,
    contentPadding: PaddingValues,
    onCancel: () -> Unit,
    onSave: (ExerciseDraft) -> Unit,
    onDelete: () -> Unit,
    onReset: (() -> Unit)? = null
) {
    var value by remember(draft) { mutableStateOf(draft) }
    var setsText by remember(draft) { mutableStateOf(draft.sets.toString()) }
    val sets = (setsText.toIntOrNull() ?: draft.sets).coerceIn(1, 12)

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "title") {
            ScreenTitle(if (draft.target != null || draft.existingEdit != null) "Настройка упражнения" else "Своё упражнение")
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
                    "Если указать вес, приложение посчитает разминку и подставит его в подходы.",
                    color = Theme.textTertiary,
                    fontSize = 11.sp
                )
            }
        }

        item(key = "save") {
            CardView(Modifier.appearIn(2)) {
                SecondaryButton(
                    if (draft.target != null || draft.existingEdit != null) "Сохранить изменения" else "Добавить упражнение",
                    icon = Icons.Filled.Check
                ) { onSave(value.copy(sets = sets)) }

                if (draft.target != null || draft.existingEdit != null) {
                    SecondaryButton(
                        "Удалить упражнение",
                        icon = Icons.Filled.Delete,
                        tint = Theme.record,
                        onClick = onDelete
                    )
                }

                if (onReset != null) {
                    SecondaryButton(
                        "Вернуть как было",
                        icon = Icons.Filled.Undo,
                        tint = Theme.warning,
                        onClick = onReset
                    )
                }

                SecondaryButton("Отмена", tint = Theme.textSecondary, onClick = onCancel)
                Text(scope.explanation, color = Theme.textTertiary, fontSize = 11.sp)
            }
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

private fun weightText(exercise: ExercisePrescription): String {
    val load = exercise.load
    return if (load is LoadPrescription.Kilograms) formatWeight(load.value) else ""
}

private fun subtitle(exercise: ExercisePrescription): String {
    val volume = if (exercise.sets > 0) "${exercise.sets} × ${exercise.reps}" else exercise.reps
    return volume + " · " + exercise.load.displayText
}
