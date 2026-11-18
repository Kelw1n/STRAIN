package com.texasprogram.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.texasprogram.app.model.ProgramProfile
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreWorkoutDatesSheet(
    workouts: List<ProgramProfile.UndatedWorkout>,
    onRestore: (Map<String, LocalDate>) -> Unit,
    onDismiss: () -> Unit
) {
    val today = LocalDate.now()
    var latest by remember { mutableStateOf(today) }
    var dates by remember(workouts) { mutableStateOf(suggestWorkoutDates(workouts, latest)) }
    var excluded by remember { mutableStateOf(emptySet<String>()) }
    var pickAnchor by remember { mutableStateOf(false) }
    var pickDay by remember { mutableStateOf<String?>(null) }
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    NutritionSheet(
        title = "Восстановить историю",
        onDismiss = onDismiss,
        action = "Сохранить",
        actionEnabled = dates.keys.any { it !in excluded },
        onAction = { close ->
            onRestore(dates.filterKeys { it !in excluded })
            close()
        }
    ) {
        Text(
            "Выбери дату последней тренировки из списка. Остальные даты STRAIN предложит по расписанию. Проверь их и убери дни, когда не тренировался.",
            color = Theme.textSecondary
        )
        Text("Последняя тренировка", color = Theme.ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        NutritionButton(latest.format(formatter), primary = false) { pickAnchor = true }
        Text("Проверь даты тренировок", color = Theme.ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        for (workout in workouts) {
            val included = workout.id !in excluded
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Неделя ${workout.week} · День ${workout.day}", color = Theme.ink)
                Checkbox(
                    checked = included,
                    onCheckedChange = { value ->
                        excluded = if (value) excluded - workout.id else excluded + workout.id
                    }
                )
            }
            if (included) {
                NutritionButton(dates.getValue(workout.id).format(formatter), primary = false) { pickDay = workout.id }
            }
        }
    }

    val selectedDay = pickDay
    if (pickAnchor || selectedDay != null) {
        val initial = selectedDay?.let { dates[it] } ?: latest
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial.toEpochDay() * MILLIS_PER_DAY,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis / MILLIS_PER_DAY <= today.toEpochDay()
            }
        )
        val close = { pickAnchor = false; pickDay = null }
        DatePickerDialog(
            onDismissRequest = close,
            confirmButton = {
                Text("Готово", color = Theme.accent, modifier = Modifier.padding(12.dp).pressable {
                    state.selectedDateMillis?.let { millis ->
                        val date = LocalDate.ofEpochDay(millis / MILLIS_PER_DAY)
                        if (selectedDay != null) dates = dates + (selectedDay to date)
                        else {
                            latest = date
                            dates = suggestWorkoutDates(workouts, latest)
                        }
                    }
                    close()
                })
            },
            dismissButton = {
                Text("Отмена", color = Theme.textSecondary, modifier = Modifier.padding(12.dp).pressable { close() })
            }
        ) { DatePicker(state = state) }
    }
}

private const val MILLIS_PER_DAY = 86_400_000L

private fun suggestWorkoutDates(workouts: List<ProgramProfile.UndatedWorkout>, latest: LocalDate): Map<String, LocalDate> {
    val last = workouts.lastOrNull() ?: return emptyMap()
    return workouts.associate { it.id to latest.plusDays(it.scheduleOffset - last.scheduleOffset) }
}
