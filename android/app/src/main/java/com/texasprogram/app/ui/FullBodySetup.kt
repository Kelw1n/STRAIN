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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.AdditionalExerciseCategory
import com.texasprogram.app.model.ProgramInput
import com.texasprogram.app.model.TrainingLevel
import com.texasprogram.app.service.FullBodyLevel

/// Настройка фулбади: стаж и пятиповторные максимумы.
///
/// Стаж спрашиваем первым: от него зависит вся схема, а не только числа.
@Composable
fun FullBodySetup(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSave: (ProgramInput, FullBodyLevel) -> Unit
) {
    var level by remember { mutableStateOf(FullBodyLevel.ABOUT_YEAR) }
    var squat by remember { mutableStateOf("100") }
    var bench by remember { mutableStateOf("100") }
    var deadlift by remember { mutableStateOf("100") }
    val chosen = remember { mutableStateMapOf<AdditionalExerciseCategory, String>() }

    fun number(text: String) = text.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it > 0 }
    val isReady = number(squat) != null && number(bench) != null && number(deadlift) != null

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "title") {
            ScreenTitle("Фулбади") {
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

        item(key = "level") {
            CardView(Modifier.appearIn(0)) {
                SectionLabel("Сколько ходишь в зал")
                FullBodyLevel.entries.forEach { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .pressable { level = item }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (level == item) Theme.accent else Theme.surfaceSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            if (level == item) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f, fill = false)) {
                            Text(item.title, color = Theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(item.subtitle, color = Theme.textSecondary, fontSize = 11.sp)
                        }
                    }
                }
                Text(level.explanation, color = Theme.textTertiary, fontSize = 11.sp)
            }
        }

        item(key = "maxes") {
            CardView(Modifier.appearIn(1)) {
                SectionLabel("Пятиповторный максимум")
                DecimalField(value = squat, label = "Приседания", onValueChange = { squat = it })
                DecimalField(value = bench, label = "Жим лёжа", onValueChange = { bench = it })
                DecimalField(value = deadlift, label = "Становая тяга", onValueChange = { deadlift = it })
                Text(
                    "Вес, который поднимаешь на пять повторений с запасом в один. Протестируй перед началом — от этих чисел считается вся программа.",
                    color = Theme.textTertiary,
                    fontSize = 11.sp
                )
            }
        }

        item(key = "accessories") {
            CardView(Modifier.appearIn(2)) {
                SectionLabel("Подсобка")
                // Набор слотов задаёт стаж: на меньшем объёме вертикального жима
                // и дополнительной тяги в плане нет, спрашивать их незачем.
                level.accessorySlots.forEach { category ->
                    ExercisePicker(category = category, value = chosen[category]) { value ->
                        if (value == null) chosen.remove(category) else chosen[category] = value
                    }
                }
                Text(
                    "Идёт в каждой тренировке. Не выберешь — подставятся стандартные. На другом стаже набор слотов другой.",
                    color = Theme.textTertiary,
                    fontSize = 11.sp
                )
            }
        }

        item(key = "start") {
            CardView(Modifier.appearIn(3)) {
                GradientButton("Начать программу", enabled = isReady) {
                    val squatValue = number(squat) ?: return@GradientButton
                    val benchValue = number(bench) ?: return@GradientButton
                    val deadliftValue = number(deadlift) ?: return@GradientButton
                    onSave(
                        ProgramInput(
                            squat5RM = squatValue,
                            bench5RM = benchValue,
                            deadlift5RM = deadliftValue,
                            level = TrainingLevel.BEGINNER,
                            pull = chosen[AdditionalExerciseCategory.PULL],
                            arms = chosen[AdditionalExerciseCategory.ARMS],
                            core = chosen[AdditionalExerciseCategory.CORE],
                            back = chosen[AdditionalExerciseCategory.BACK],
                            press = chosen[AdditionalExerciseCategory.PRESS]
                        ),
                        level
                    )
                }
            }
        }
    }
}
