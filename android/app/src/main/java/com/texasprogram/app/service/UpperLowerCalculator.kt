package com.texasprogram.app.service

import com.texasprogram.app.model.BenchSessionPlan
import com.texasprogram.app.model.BenchSetKind
import com.texasprogram.app.model.BenchSetPlan
import com.texasprogram.app.model.ExercisePrescription
import com.texasprogram.app.model.LoadPrescription
import com.texasprogram.app.model.UpperLowerInput
import com.texasprogram.app.model.WorkoutDayPlan
import com.texasprogram.app.model.WorkoutPlan
import com.texasprogram.app.model.WorkoutWeekPlan
import kotlin.math.floor

object UpperLowerCalculator {

    private data class BenchSet(val percent: Double, val reps: String, val kind: BenchSetKind)

    /// День недели не зашит в заголовок: он берётся из расписания профиля.
    val dayTitles = listOf("ВЕРХ ТЯЖЁЛЫЙ", "НИЗ ТЯЖЁЛЫЙ", "ВЕРХ ОБЪЁМНЫЙ", "НИЗ ОБЪЁМНЫЙ")

    val benchSessionCount: Int get() = benchSessions.size

    fun roundToFive(value: Double): Double = floor(value / 5 + 0.5) * 5

    /// Полная волна из 14 жимовых тренировок с посчитанными весами.
    fun benchWave(input: UpperLowerInput): List<BenchSessionPlan> =
        benchSessions.mapIndexed { index, sets ->
            val number = index + 1
            val isFirstOfWeek = number % 2 == 1
            BenchSessionPlan(
                id = number,
                week = (number + 1) / 2,
                dayNumber = if (isFirstOfWeek) 1 else 3,
                dayTitle = dayTitles[if (isFirstOfWeek) 0 else 2],
                sets = sets.mapIndexed { setIndex, set ->
                    BenchSetPlan(
                        id = setIndex + 1,
                        percent = set.percent,
                        weight = roundToFive(input.bench1RM * set.percent),
                        reps = set.reps,
                        kind = set.kind
                    )
                }
            )
        }

    fun generate(input: UpperLowerInput): WorkoutPlan {
        val wave = benchWave(input)
        val weeks = (1..7).map { week ->
            val firstBench = (week - 1) * 2 + 1
            val secondBench = firstBench + 1
            WorkoutWeekPlan(
                number = week,
                days = listOf(
                    WorkoutDayPlan("$week-1", 1, dayTitles[0], upperHeavy(wave[firstBench - 1])),
                    WorkoutDayPlan("$week-2", 2, dayTitles[1], lowerHeavy(input)),
                    WorkoutDayPlan("$week-3", 3, dayTitles[2], upperVolume(wave[secondBench - 1])),
                    WorkoutDayPlan("$week-4", 4, dayTitles[3], lowerVolume(input))
                )
            )
        }
        return WorkoutPlan(weeks, isPeaking = false)
    }

    private fun upperHeavy(bench: BenchSessionPlan) = listOf(
        benchPrescription(bench),
        rpe("Тяга штанги в наклоне", 4, "6–8", "RPE 8"),
        rpe("Жим гантелей на наклонной скамье (30°)", 3, "8–10", "RPE 8"),
        rpe("Верхний блок", 3, "6–10", "RPE 8"),
        rpe("Жим гантелей сидя", 3, "6–8", "RPE 8"),
        rpe("Махи в стороны", 3, "12–20", "RPE 9"),
        rpe("Суперсет бицепс–трицепс", 4, "12 / 10", "RPE 8–9"),
        rpe("Предплечья", 3, "12–15", "RPE 8–9")
    )

    private fun lowerHeavy(input: UpperLowerInput) = listOf(
        weighted("Приседания со штангой", 3, "4–6", input.squat1RM, 0.83, "RPE 8"),
        weighted("Становая тяга", 3, "10–12", input.deadlift1RM, 0.65, "RPE 8"),
        rpe("Сгибание ног сидя", 3, "10–15", "RPE 8–9"),
        rpe("Икры", 4, "10–15", "RPE 9"),
        rpe("Пресс", 3, "10–15", "RPE 8–9"),
        rpe("Шея", 3, "10–12", "RPE 7–8"),
        rpe("Предплечья", 3, "12–15", "RPE 8–9")
    )

    private fun upperVolume(bench: BenchSessionPlan) = listOf(
        benchPrescription(bench),
        rpe("Подтягивания / верхний блок", 4, "6–8", "RPE 8"),
        rpe("Жим гантелей сидя", 3, "6–10", "RPE 8"),
        rpe("Тяга штанги в наклоне / горизонтальный блок", 3, "8–12", "RPE 8"),
        rpe("Махи в стороны", 3, "12–20", "RPE 9"),
        rpe("Суперсет бицепс–трицепс", 4, "12 / 10", "RPE 8–9")
    )

    private fun lowerVolume(input: UpperLowerInput) = listOf(
        weighted("Приседания со штангой", 3, "6–8", input.squat1RM, 0.77, "RPE 8"),
        weighted("Становая тяга", 3, "6–10", input.deadlift1RM, 0.75, "RPE 8"),
        rpe("Сгибание ног лёжа", 3, "10–15", "RPE 8–9"),
        rpe("Икры", 4, "10–15", "RPE 9"),
        rpe("Пресс", 3, "10–15", "RPE 8–9"),
        rpe("Шея", 3, "10–12", "RPE 7–8"),
        rpe("Предплечья", 3, "12–15", "RPE 8–9")
    )

    private fun weighted(name: String, sets: Int, reps: String, max: Double, percent: Double, rpe: String) =
        ExercisePrescription(name, sets, "$reps · $rpe", LoadPrescription.Kilograms(roundToFive(max * percent)))

    private fun rpe(name: String, sets: Int, reps: String, value: String) =
        ExercisePrescription(name, sets, reps, LoadPrescription.Rpe(value), isOptional = true)

    private fun benchPrescription(session: BenchSessionPlan) = ExercisePrescription(
        name = session.exerciseName,
        sets = session.sets.size,
        reps = session.repsText,
        load = LoadPrescription.RepRange(session.weightsText),
        benchSession = session.id
    )

    private val benchSessions: List<List<BenchSet>> = listOf(
        listOf(BenchSet(0.80, "6", BenchSetKind.NORMAL), BenchSet(0.825, "5", BenchSetKind.NORMAL), BenchSet(0.825, "5", BenchSetKind.NORMAL), BenchSet(0.85, "4", BenchSetKind.NORMAL), BenchSet(0.85, "4", BenchSetKind.NORMAL)),
        listOf(BenchSet(0.85, "3", BenchSetKind.NORMAL), BenchSet(0.85, "3", BenchSetKind.NORMAL), BenchSet(0.925, "2", BenchSetKind.NORMAL), BenchSet(0.925, "2", BenchSetKind.NORMAL), BenchSet(1.025, "1", BenchSetKind.NEGATIVE)),
        listOf(BenchSet(0.80, "6", BenchSetKind.NORMAL), BenchSet(0.85, "5", BenchSetKind.NORMAL), BenchSet(0.85, "5", BenchSetKind.NORMAL), BenchSet(0.875, "4", BenchSetKind.NORMAL), BenchSet(0.875, "4", BenchSetKind.NORMAL)),
        listOf(BenchSet(0.875, "3", BenchSetKind.NORMAL), BenchSet(0.875, "3", BenchSetKind.NORMAL), BenchSet(0.95, "2", BenchSetKind.NORMAL), BenchSet(0.95, "2", BenchSetKind.NORMAL), BenchSet(1.05, "1", BenchSetKind.NEGATIVE)),
        listOf(BenchSet(0.80, "6", BenchSetKind.NORMAL), BenchSet(0.85, "5", BenchSetKind.NORMAL), BenchSet(0.85, "5", BenchSetKind.NORMAL), BenchSet(0.90, "макс.", BenchSetKind.TEST)),
        listOf(BenchSet(0.90, "3", BenchSetKind.NORMAL), BenchSet(0.90, "3", BenchSetKind.NORMAL), BenchSet(0.95, "2", BenchSetKind.NORMAL), BenchSet(0.95, "2", BenchSetKind.NORMAL), BenchSet(1.05, "1", BenchSetKind.NEGATIVE)),
        listOf(BenchSet(0.85, "5", BenchSetKind.NORMAL), BenchSet(0.85, "5", BenchSetKind.NORMAL), BenchSet(0.90, "3", BenchSetKind.NORMAL), BenchSet(0.90, "3", BenchSetKind.NORMAL), BenchSet(0.925, "макс.", BenchSetKind.TEST)),
        listOf(BenchSet(0.925, "3", BenchSetKind.NORMAL), BenchSet(0.925, "3", BenchSetKind.NORMAL), BenchSet(1.0, "1", BenchSetKind.NORMAL), BenchSet(1.0, "1", BenchSetKind.NORMAL), BenchSet(1.10, "1", BenchSetKind.NEGATIVE)),
        listOf(BenchSet(0.85, "5", BenchSetKind.NORMAL), BenchSet(0.85, "5", BenchSetKind.NORMAL), BenchSet(0.95, "3", BenchSetKind.NORMAL), BenchSet(0.95, "3", BenchSetKind.NORMAL), BenchSet(0.95, "макс.", BenchSetKind.TEST)),
        listOf(BenchSet(0.95, "3", BenchSetKind.NORMAL), BenchSet(0.95, "3", BenchSetKind.NORMAL), BenchSet(1.025, "2", BenchSetKind.NORMAL), BenchSet(1.025, "2", BenchSetKind.NORMAL), BenchSet(1.05, "1", BenchSetKind.NORMAL)),
        listOf(BenchSet(0.875, "5", BenchSetKind.NORMAL), BenchSet(0.875, "5", BenchSetKind.NORMAL), BenchSet(0.95, "3", BenchSetKind.NORMAL), BenchSet(0.95, "3", BenchSetKind.NORMAL), BenchSet(0.95, "макс.", BenchSetKind.TEST)),
        listOf(BenchSet(0.95, "3", BenchSetKind.NORMAL), BenchSet(0.95, "3", BenchSetKind.NORMAL), BenchSet(1.025, "2", BenchSetKind.NORMAL), BenchSet(1.025, "2", BenchSetKind.NORMAL), BenchSet(1.05, "1", BenchSetKind.NORMAL)),
        listOf(BenchSet(0.90, "5", BenchSetKind.NORMAL), BenchSet(1.0, "3", BenchSetKind.NORMAL), BenchSet(1.0, "3", BenchSetKind.NORMAL), BenchSet(1.05, "2", BenchSetKind.NORMAL), BenchSet(1.05, "2", BenchSetKind.NORMAL)),
        listOf(BenchSet(0.95, "3", BenchSetKind.NORMAL), BenchSet(1.05, "2", BenchSetKind.NORMAL), BenchSet(1.075, "1", BenchSetKind.RECORD))
    )
}
