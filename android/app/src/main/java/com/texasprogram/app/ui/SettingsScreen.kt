package com.texasprogram.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.AdditionalExerciseCategory
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.ScheduleMode
import com.texasprogram.app.model.TrainingLevel
import com.texasprogram.app.model.TrainingProgramKind
import com.texasprogram.app.model.formatWeight
import com.texasprogram.app.service.RuDate
import com.texasprogram.app.service.UpperLowerCalculator

@Composable
fun SettingsScreen(
    profile: ProgramProfile,
    profiles: List<ProgramProfile>,
    onUpdate: (ProgramProfile) -> Unit,
    onSwitchProfile: (String) -> Unit,
    onAddProfile: () -> Unit,
    onDeleteProfile: () -> Unit,
    onClose: () -> Unit,
    contentPadding: PaddingValues
) {
    var showDelete by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "title") {
            ScreenTitle("Настройки") {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Theme.surfaceSoft)
                        .pressable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Готово", tint = Theme.textPrimary, modifier = Modifier.size(20.dp))
                }
            }
        }

        item(key = "profiles") {
            CardView(Modifier.appearIn(0)) {
                SectionLabel("Профили")
                TextRow(
                    value = profile.name,
                    label = "Имя профиля",
                    onValueChange = { onUpdate(profile.copy(name = it)) }
                )
                profiles.forEach { item ->
                    val isActive = item.id == profile.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isActive) Theme.accent.copy(alpha = 0.10f) else Color.Transparent)
                            .pressable(enabled = !isActive) { onSwitchProfile(item.id) }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (isActive) Theme.accentGradient else SolidColor(Theme.surfaceSoft)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, color = Theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(item.programKind.title, color = Theme.textSecondary, fontSize = 11.sp)
                        }
                        if (isActive) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                SecondaryButton("Добавить профиль", icon = Icons.Filled.PersonAdd, onClick = onAddProfile)
                Text(
                    "У каждого профиля своя программа, максимумы, расписание и отметки.",
                    color = Theme.textTertiary,
                    fontSize = 11.sp
                )
            }
        }

        item(key = "maxes") {
            CardView(Modifier.appearIn(1)) {
                SectionLabel(profile.maximumLabel)
                NumberRow("Приседания", profile.squat5RM) { onUpdate(profile.copy(squat5RM = it)) }
                NumberRow("Жим лёжа", profile.bench5RM) { onUpdate(profile.copy(bench5RM = it)) }
                NumberRow("Становая тяга", profile.deadlift5RM) { onUpdate(profile.copy(deadlift5RM = it)) }
            }
        }

        item(key = "order") {
            CardView(Modifier.appearIn(2)) {
                SectionLabel("Порядок тренировок")
                SegmentedControl(
                    options = ScheduleMode.entries.map { it.title },
                    selectedIndex = ScheduleMode.entries.indexOf(profile.scheduleMode)
                ) { onUpdate(profile.copy(scheduleMode = ScheduleMode.entries[it])) }
                Text(profile.scheduleMode.explanation, color = Theme.textTertiary, fontSize = 11.sp)
            }
        }

        item(key = "weekdays") {
            CardView(Modifier.appearIn(3)) {
                SectionLabel("Дни недели")
                (1..profile.trainingDayCount).forEach { day ->
                    WeekdayRow(
                        label = dayLabel(profile, day),
                        weekday = profile.weekday(day),
                        onSelect = { onUpdate(profile.setWeekday(it, day)) }
                    )
                }
                Text(
                    if (profile.scheduleMode == ScheduleMode.QUEUE)
                        "Дни, в которые ты ходишь в зал. Очередь раздаёт тренировки именно по ним."
                    else
                        "День недели задаёт тип тренировки. Поменяешь здесь — поменяется и то, что приложение предложит в этот день.",
                    color = Theme.textTertiary,
                    fontSize = 11.sp
                )
            }
        }

        if (profile.programKind == TrainingProgramKind.TEXAS) {
            item(key = "level") {
                CardView(Modifier.appearIn(4)) {
                    SectionLabel("Уровень пикирования")
                    SegmentedControl(
                        options = TrainingLevel.entries.map { it.title },
                        selectedIndex = TrainingLevel.entries.indexOf(profile.level)
                    ) { onUpdate(profile.copy(level = TrainingLevel.entries[it])) }
                }
            }

            item(key = "extra") {
                CardView(Modifier.appearIn(5)) {
                    SectionLabel("Дополнительные упражнения")
                    AdditionalExerciseCategory.entries.forEach { category ->
                        val current = when (category) {
                            AdditionalExerciseCategory.PULL -> profile.pull
                            AdditionalExerciseCategory.ARMS -> profile.arms
                            AdditionalExerciseCategory.CORE -> profile.core
                            AdditionalExerciseCategory.BACK -> profile.back
                            AdditionalExerciseCategory.PRESS -> profile.press
                        }
                        ExercisePicker(category, current) { value ->
                            onUpdate(
                                when (category) {
                                    AdditionalExerciseCategory.PULL -> profile.copy(pull = value)
                                    AdditionalExerciseCategory.ARMS -> profile.copy(arms = value)
                                    AdditionalExerciseCategory.CORE -> profile.copy(core = value)
                                    AdditionalExerciseCategory.BACK -> profile.copy(back = value)
                                    AdditionalExerciseCategory.PRESS -> profile.copy(press = value)
                                }
                            )
                        }
                    }
                }
            }
        }

        if (profile.programKind == TrainingProgramKind.UPPER_LOWER) {
            item(key = "bench") {
                CardView(Modifier.appearIn(6)) {
                    SectionLabel("Жим 14")
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Отмечено тренировок", color = Theme.textPrimary, fontSize = 14.sp)
                        Spacer(Modifier.weight(1f))
                        GradientText(
                            "${profile.completedBenchCount} / ${UpperLowerCalculator.benchSessionCount}",
                            fontSize = 15.sp,
                            weight = FontWeight.Bold,
                            gradient = Theme.recordGradient
                        )
                    }
                    SecondaryButton("Сбросить отметки жима", tint = Theme.warning) {
                        onUpdate(profile.copy(completedBenchSessions = emptyList()))
                    }
                }
            }
        }

        item(key = "delete") {
            CardView(Modifier.appearIn(7)) {
                SecondaryButton(
                    if (profiles.size > 1) "Удалить этот профиль" else "Сбросить профиль",
                    icon = Icons.Filled.Delete,
                    tint = Theme.record
                ) { showDelete = true }
                Text(
                    if (profiles.size > 1)
                        "Удалит максимумы, программу и отметки этого профиля. Остальные профили останутся."
                    else
                        "Удалит максимумы, выбранную программу и все отметки, затем вернёт на стартовый выбор.",
                    color = Theme.textTertiary,
                    fontSize = 11.sp
                )
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(if (profiles.size > 1) "Удалить профиль «${profile.name}»?" else "Полностью сбросить профиль?") },
            text = { Text("Действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    onDeleteProfile()
                }) { Text("Удалить", color = Theme.record) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Отмена") } },
            containerColor = Theme.surface,
            titleContentColor = Theme.textPrimary,
            textContentColor = Theme.textSecondary
        )
    }
}

private fun dayLabel(profile: ProgramProfile, day: Int): String {
    val title = profile.workoutPlan.weeks.firstOrNull()?.days?.firstOrNull { it.number == day }?.title
    if (title == null || profile.programKind != TrainingProgramKind.UPPER_LOWER) return "День $day"
    return "День $day · " + title.take(1) + title.drop(1).lowercase()
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun ModeChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Theme.accentGradient else SolidColor(Theme.surfaceSoft))
            .pressable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) Color.White else Theme.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WeekdayRow(label: String, weekday: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Theme.surfaceSoft)
                .pressable { open = true }
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                color = Theme.textPrimary,
                fontSize = 13.sp,
                maxLines = 2,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.weight(1f))
            Text(
                RuDate.full(weekday).replaceFirstChar { it.uppercase() },
                color = Theme.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = Theme.textSecondary, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            RuDate.weekOrder.forEach { value ->
                DropdownMenuItem(
                    text = { Text(RuDate.full(value).replaceFirstChar { it.uppercase() }) },
                    onClick = { onSelect(value); open = false }
                )
            }
        }
    }
}

@Composable
fun ExercisePicker(category: AdditionalExerciseCategory, value: String?, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Theme.surfaceSoft)
                .pressable { open = true }
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Заголовок сжимается первым: без weight длинные названия
            // выдавливали значение и шеврон за край карточки.
            Text(
                category.title,
                color = Theme.accent,
                fontSize = 14.sp,
                maxLines = 2,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.weight(1f))
            Text(
                value ?: "Не выбрано",
                color = Theme.textSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 150.dp)
            )
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.UnfoldMore, contentDescription = null, tint = Theme.textSecondary, modifier = Modifier.size(15.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Не выбрано") }, onClick = { onSelect(null); open = false })
            category.options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); open = false })
            }
        }
    }
}

@Composable
fun NumberRow(title: String, value: Double, onChange: (Double) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Theme.textPrimary, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        StepButton("−") { onChange(maxOf(0.0, value - 2.5)) }
        Spacer(Modifier.width(8.dp))
        Text(
            "${formatWeight(value)} кг",
            color = Theme.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Theme.surfaceSoft)
                .padding(horizontal = 12.dp, vertical = 7.dp)
        )
        Spacer(Modifier.width(8.dp))
        StepButton("+") { onChange(value + 2.5) }
    }
}

@Composable
private fun StepButton(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Theme.accent.copy(alpha = 0.14f))
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, color = Theme.accent, fontSize = 15.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun TextRow(value: String, label: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Theme.textPrimary,
            unfocusedTextColor = Theme.textPrimary,
            focusedBorderColor = Theme.accent,
            unfocusedBorderColor = Theme.hairline,
            focusedLabelColor = Theme.accent,
            unfocusedLabelColor = Theme.textSecondary,
            cursorColor = Theme.accent
        )
    )
}

@Composable
fun DecimalField(value: String, label: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Theme.textPrimary,
            unfocusedTextColor = Theme.textPrimary,
            focusedBorderColor = Theme.accent,
            unfocusedBorderColor = Theme.hairline,
            focusedLabelColor = Theme.accent,
            unfocusedLabelColor = Theme.textSecondary,
            cursorColor = Theme.accent
        )
    )
}
