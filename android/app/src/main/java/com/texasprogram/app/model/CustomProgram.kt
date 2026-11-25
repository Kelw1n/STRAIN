package com.texasprogram.app.model

import com.texasprogram.app.service.ProgramCalculator
import kotlinx.serialization.Serializable
import java.util.UUID

/// Готовые схемы разбивки для своей программы.
///
/// Схема задаёт только каркас — сколько дней в неделю и как они называются.
/// Упражнения в них вписывает пользователь.
@Serializable(with = SplitTemplateSerializer::class)
enum class SplitTemplate(val title: String) {
    FULL_BODY("Фулбади"),
    FULL_BODY_EOD("Фулбади через день"),
    UPPER_LOWER("Верх / Низ"),
    PUSH_PULL_LEGS("Жим / Тяга / Ноги"),
    CUSTOM("Свой");

    /// Названия дней. Их количество и есть число тренировок в неделю.
    val dayTitles: List<String>
        get() = when (this) {
            FULL_BODY -> listOf("ФУЛБАДИ А", "ФУЛБАДИ B", "ФУЛБАДИ C")
            FULL_BODY_EOD -> listOf("ФУЛБАДИ А", "ФУЛБАДИ B", "ФУЛБАДИ C", "ФУЛБАДИ D")
            UPPER_LOWER -> listOf("ВЕРХ ТЯЖЁЛЫЙ", "НИЗ ТЯЖЁЛЫЙ", "ВЕРХ ЛЁГКИЙ", "НИЗ ЛЁГКИЙ")
            PUSH_PULL_LEGS -> listOf("ЖИМ", "ТЯГА", "НОГИ")
            CUSTOM -> listOf("ДЕНЬ 1", "ДЕНЬ 2", "ДЕНЬ 3")
        }

    val explanation: String
        get() = when (this) {
            FULL_BODY -> "Всё тело за тренировку, три раза в неделю. Классика для новичка и для возвращения после перерыва."
            FULL_BODY_EOD -> "Всё тело через день: четыре тренировки, дни недели плавают. Ставь режим «Очередь» в настройках."
            UPPER_LOWER -> "Два верхних и два нижних дня. Больше объёма на движение, чем в фулбади."
            PUSH_PULL_LEGS -> "Жимовые, тяговые и ноги по отдельности. Нужен опыт: восстановление размазано тоньше."
            CUSTOM -> "Пустой каркас. Сам решаешь, сколько дней и что в них."
        }
}

/// Одно упражнение своей программы.
@Serializable
data class CustomExercise(
    val id: String = "own-" + UUID.randomUUID().toString(),
    val name: String,
    val sets: Int,
    val reps: String,
    /// Вес в килограммах, если задан числом.
    val kilograms: Double? = null,
    /// Свободная подпись нагрузки: «RPE 8», «свой вес».
    val loadText: String? = null,
    /// Прибавка к весу за неделю. Ноль — вес не растёт.
    val weeklyIncrement: Double = 0.0,
    val advanceAfterTarget: Boolean? = null
) {
    val load: LoadPrescription
        get() {
            if (kilograms != null) return LoadPrescription.Kilograms(kilograms)
            if (!loadText.isNullOrBlank()) return LoadPrescription.RepRange(loadText)
            return LoadPrescription.Rpe("RPE 8")
        }

    /// Упражнение для конкретной недели: вес растёт от номера недели.
    fun prescription(week: Int, superset: Superset? = null): ExercisePrescription {
        val value = if (kilograms != null && weeklyIncrement != 0.0 && advanceAfterTarget != true) {
            LoadPrescription.Kilograms(ProgramCalculator.roundToPlate(kilograms + (week - 1) * weeklyIncrement))
        } else {
            load
        }
        return ExercisePrescription(name = name, sets = sets, reps = reps, load = value, id = id, superset = superset)
    }

    val detail: String
        get() {
            val volume = if (sets > 0) "$sets × $reps" else reps
            val growth = when {
                advanceAfterTarget == true -> " · +${formatWeight(weeklyIncrement)} кг"
                weeklyIncrement != 0.0 -> " · +${formatWeight(weeklyIncrement)} кг/нед"
                else -> ""
            }
            return volume + " · " + load.displayText + growth
        }
}

@Serializable
data class Superset(
    val first: String,
    val second: String,
    val id: String = UUID.randomUUID().toString(),
    val restAfterEach: Boolean? = null
) {
    fun contains(exerciseId: String) = first == exerciseId || second == exerciseId
}

/// День своей программы.
@Serializable
data class CustomDay(
    val id: String = UUID.randomUUID().toString(),
    val number: Int,
    val title: String,
    val exercises: List<CustomExercise> = emptyList(),
    val supersets: List<Superset> = emptyList()
) {
    val validSupersets: List<Superset>
        get() {
            val ids = exercises.map { it.id }.toSet()
            val paired = mutableSetOf<String>()
            return supersets.filter { pair ->
                if (pair.first == pair.second || pair.first !in ids || pair.second !in ids ||
                    pair.first in paired || pair.second in paired
                ) {
                    false
                } else {
                    paired.addAll(listOf(pair.first, pair.second))
                    true
                }
            }
        }
}

/// Своя программа целиком: сколько недель, какие дни, что в них.
@Serializable
data class CustomProgram(
    val name: String = "Своя программа",
    val template: SplitTemplate = SplitTemplate.FULL_BODY,
    val weeks: Int = 8,
    val days: List<CustomDay> = emptyList()
) {
    /// Готова ли программа к запуску: пустые дни тренировать нечем.
    val isRunnable: Boolean
        get() = weeks > 0 && days.isNotEmpty() && days.all { it.exercises.isNotEmpty() }

    val plan: WorkoutPlan
        get() = WorkoutPlan(
            weeks = (1..maxOf(weeks, 1)).map { week ->
                WorkoutWeekPlan(
                    number = week,
                    days = days.map { day ->
                        val pairs = day.validSupersets
                        WorkoutDayPlan(
                            id = "$week-${day.number}",
                            number = day.number,
                            title = day.title,
                            exercises = day.exercises.map { exercise ->
                                exercise.prescription(week, pairs.firstOrNull { it.contains(exercise.id) })
                            }
                        )
                    }
                )
            },
            isPeaking = false
        )

    companion object {
        /// Каркас по выбранной схеме — дни без упражнений.
        fun skeleton(template: SplitTemplate, name: String, weeks: Int) = CustomProgram(
            name = name,
            template = template,
            weeks = weeks,
            days = template.dayTitles.mapIndexed { index, title -> CustomDay(number = index + 1, title = title) }
        )
    }
}
