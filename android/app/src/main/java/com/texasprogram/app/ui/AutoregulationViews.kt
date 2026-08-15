package com.texasprogram.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.LiftOutcome
import com.texasprogram.app.model.LoadPrescription
import com.texasprogram.app.model.MainLift
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.WorkoutDayPlan
import com.texasprogram.app.model.formatWeight
import com.texasprogram.app.service.ProgramCalculator

/// Движения тяжёлого дня, о которых имеет смысл спросить.
///
/// Пусто — карточку не показываем. Считаем вне разметки, чтобы список
/// на экране не держал пустой элемент с отступами.
fun autoregulationLifts(profile: ProgramProfile, week: Int, day: WorkoutDayPlan): List<Pair<MainLift, Double>> {
    if (!profile.autoregulationEnabled || !profile.supportsAutoregulation) return emptyList()
    // Тяжёлый день — последний в неделе.
    val isIntensityDay = profile.workoutPlan.weeks.firstOrNull { it.number == week }
        ?.days?.lastOrNull()?.number == day.number
    if (!isIntensityDay) return emptyList()
    return day.exercises.mapNotNull { exercise ->
        val lift = MainLift.byName(exercise.name) ?: return@mapNotNull null
        val load = exercise.load as? LoadPrescription.Kilograms ?: return@mapNotNull null
        lift to load.value
    }
}

/// Вопрос после тяжёлого дня: как прошёл рабочий подход по каждому движению.
///
/// Спрашиваем только здесь. Объёмный день тоже умеет тормозить, но второй
/// вопрос за неделю превращает приложение в анкету.
@Composable
fun AutoregulationCard(
    profile: ProgramProfile,
    week: Int,
    day: WorkoutDayPlan,
    modifier: Modifier = Modifier,
    onUpdate: (ProgramProfile) -> Unit
) {
    val lifts = autoregulationLifts(profile, week, day)
    if (lifts.isEmpty()) return

    CardView(modifier) {
        SectionLabel("Как прошёл тяжёлый подход")
        lifts.forEach { (lift, weight) ->
            val current = profile.report(lift, week)?.outcome
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(lift.title, color = Theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${formatWeight(weight)} кг",
                        color = Theme.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                SegmentedControl(
                    options = LiftOutcome.entries.map { it.title },
                    selectedIndex = current?.let { LiftOutcome.entries.indexOf(it) } ?: -1
                ) { index ->
                    onUpdate(profile.setReport(lift, week, LiftOutcome.entries[index]))
                }
                if (current != null) {
                    val next = ProgramCalculator.roundToPlate(current.next(weight, lift.isUpper))
                    Text(
                        current.subtitle + if (next == weight) " · вес остаётся ${formatWeight(next)} кг"
                        else " · дальше ${formatWeight(next)} кг",
                        color = if (current.isFailure) Theme.warning else Theme.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
        Text(
            "Ответ меняет вес только этого движения на следующую неделю. Без ответа неделя идёт с обычной прибавкой.",
            color = Theme.textTertiary,
            fontSize = 11.sp
        )
    }
}

/// Предупреждение о застое: две неудачи подряд означают, что линейный рост кончился.
@Composable
fun StallNotice(profile: ProgramProfile, modifier: Modifier = Modifier) {
    val stalled = profile.stalledLifts
    if (stalled.isEmpty()) return
    CardView(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = Theme.warning, modifier = Modifier.size(18.dp))
            Text("Прогресс встал", color = Theme.warning, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            stalled.joinToString(", ") { it.title } +
                " — два раза подряд не добрал повторения. Линейная прибавка здесь кончилась: имеет смысл начать новый цикл с пересчитанных максимумов.",
            color = Theme.textSecondary,
            fontSize = 12.sp
        )
    }
}

/// Заметка к тренировке — свободный текст для себя.
@Composable
fun WorkoutNoteCard(
    profile: ProgramProfile,
    week: Int,
    day: Int,
    modifier: Modifier = Modifier,
    onUpdate: (ProgramProfile) -> Unit
) {
    // Ключ по дню: при переходе на другую тренировку поле перечитывается.
    var text by remember(week, day) { mutableStateOf(profile.note(week, day)) }

    CardView(modifier) {
        SectionLabel("Заметка")
        TextRow(text, "Как прошло, что болело, как спал") { value ->
            text = value
            onUpdate(profile.setNote(value, week, day))
        }
        Text(
            "Через месяц по этой строке будет понятно, почему неделя вышла такой.",
            color = Theme.textTertiary,
            fontSize = 11.sp
        )
    }
}
