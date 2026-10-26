package com.texasprogram.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.ExerciseSwap
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.SwapScope
import com.texasprogram.app.model.isProgramLift
import com.texasprogram.app.service.RuPlural
import com.texasprogram.app.service.ScheduledWorkout
import com.texasprogram.app.service.WorkoutSessionManager

/// «Тренажёр занят»: похожие упражнения на ту же мышцу с весом по истории.
@Composable
fun ExerciseSwapSheet(
    profile: ProgramProfile,
    workout: ScheduledWorkout,
    index: Int,
    onSwap: (ExerciseSwap.Suggestion, SwapScope) -> Unit,
    onDismiss: () -> Unit
) {
    val exercise = WorkoutSessionManager.exercises.getOrNull(index)
    if (exercise == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val currentWeight = exercise.sets.firstOrNull { !it.isDone }?.weight ?: exercise.sets.firstOrNull()?.weight ?: 0.0
    val found = remember(exercise.key) {
        ExerciseSwap.suggestions(exercise.prescription.name, currentWeight, profile, workout.week, workout.day.number)
    }
    val programLift = profile.isProgramLift(exercise.prescription)
    var scope by remember { mutableStateOf(SwapScope.TODAY) }
    var custom by remember { mutableStateOf("") }
    val trimmed = custom.trim()

    NutritionSheet(
        title = "Заменить упражнение",
        onDismiss = onDismiss,
        action = "Заменить",
        actionEnabled = trimmed.isNotEmpty(),
        onAction = { close ->
            onSwap(
                ExerciseSwap.suggestion(trimmed.take(60), exercise.prescription.name, currentWeight, profile, workout.week, workout.day.number),
                scope
            )
            close()
        }
    ) { close ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ExerciseThumb(exercise.prescription.name, 52.dp)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Вместо «${exercise.prescription.name}»", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(
                    "${RuPlural.sets(exercise.sets.size)} по ${exercise.prescription.reps} — схема остаётся",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp
                )
            }
        }

        if (found.done.isNotEmpty()) SwapSection("Уже делал", found.done) { onSwap(it, scope); close() }
        if (found.similar.isNotEmpty()) SwapSection(if (found.done.isEmpty()) "Похожие" else "Ещё похожие", found.similar) { onSwap(it, scope); close() }

        NutritionCaption("Своё упражнение")
        Box(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF212121))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (custom.isEmpty()) Text("Название — и «Заменить» сверху", color = Color.White.copy(alpha = 0.4f), fontSize = 16.sp)
            BasicTextField(
                value = custom,
                onValueChange = { custom = it.take(60) },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(Theme.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (trimmed.isNotEmpty()) {
                        onSwap(
                            ExerciseSwap.suggestion(trimmed.take(60), exercise.prescription.name, currentWeight, profile, workout.week, workout.day.number),
                            scope
                        )
                        close()
                    }
                }),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (programLift) {
            Text(
                "Это основное движение программы — его меняем только на сегодня, в следующий раз оно вернётся.",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp
            )
        } else {
            SegmentedControl(options = SwapScope.entries.map { it.title }, selectedIndex = scope.ordinal) { scope = SwapScope.entries[it] }
            Text(scope.explanation, color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
        }
        val marked = exercise.doneCount
        if (marked > 0) {
            Text(
                "Отмеченные подходы этого упражнения ($marked) снимутся — их место займёт новое.",
                color = Theme.warning,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun SwapSection(title: String, items: List<ExerciseSwap.Suggestion>, onChoose: (ExerciseSwap.Suggestion) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NutritionCaption(title)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Theme.surface)) {
            items.forEachIndexed { position, item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable { onChoose(item) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExerciseThumb(item.name, 42.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                        Text(
                            item.detail,
                            color = if (item.source == ExerciseSwap.Source.HISTORY) Theme.accent else Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            maxLines = 2
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(18.dp))
                }
                if (position < items.size - 1) HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(start = 68.dp))
            }
        }
    }
}
