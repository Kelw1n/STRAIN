package com.texasprogram.app.service

import com.texasprogram.app.model.ExercisePrescription
import com.texasprogram.app.model.LoadPrescription
import com.texasprogram.app.model.ProgramInput
import com.texasprogram.app.model.TrainingLevel
import com.texasprogram.app.model.WorkoutDayPlan
import com.texasprogram.app.model.WorkoutPlan
import com.texasprogram.app.model.WorkoutWeekPlan
import kotlin.math.ceil
import kotlin.math.floor

object ProgramCalculator {

    /// Округление к ближайшим 2,5 кг — как MROUND в исходной таблице.
    fun roundToPlate(value: Double): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        val scaled = value / 2.5
        val rounded = if (scaled >= 0) floor(scaled + 0.5) else ceil(scaled - 0.5)
        return rounded * 2.5
    }

    fun generate(input: ProgramInput): WorkoutPlan = generateWeeks(input, peaking = false)

    fun generatePeaking(input: ProgramInput): WorkoutPlan = generateWeeks(input, peaking = true)

    private fun generateWeeks(input: ProgramInput, peaking: Boolean): WorkoutPlan {
        val count = if (peaking) (if (input.level == TrainingLevel.BEGINNER) 3 else 4) else 12
        val weeks = (1..count).map { week ->
            if (peaking) peakWeek(week, input) else baseWeek(week, input)
        }
        return WorkoutPlan(weeks, peaking)
    }

    private fun baseWeek(week: Int, input: ProgramInput): WorkoutWeekPlan {
        val increment = (week - 1) * 2.5 - 10
        val squatDay3 = roundToPlate(input.squat5RM + increment)
        val benchDay3 = roundToPlate(input.bench5RM + increment)
        val deadDay3 = roundToPlate(input.deadlift5RM + increment)
        val squatDay1 = roundToPlate(squatDay3 * 0.9)
        val benchDay1 = roundToPlate(benchDay3 * 0.9)
        val squatDay2 = roundToPlate(squatDay1 * 0.8)
        val benchDay2 = roundToPlate(benchDay1 * 0.8)

        val d1 = mutableListOf(
            ExercisePrescription("Приседания", 5, "5", LoadPrescription.Kilograms(squatDay1)),
            ExercisePrescription("Жим лёжа", 5, "5", LoadPrescription.Kilograms(benchDay1))
        )
        d1.addOptional(input.pull, 2, "5", LoadPrescription.Rpe("RPE 8"))
        d1.addOptional(input.arms, 5, "8–12", LoadPrescription.Rpe("RPE 8–9"))
        d1.addOptional(input.core, 3, "5–8", LoadPrescription.Rpe("RPE 9"))

        val d2 = mutableListOf(
            ExercisePrescription("Приседания", 2, "5", LoadPrescription.Kilograms(squatDay2)),
            ExercisePrescription("Жим лёжа", 3, "5", LoadPrescription.Kilograms(benchDay2))
        )
        d2.addOptional(input.back, 3, "8–12", LoadPrescription.Rpe("RPE 8–9"))
        d2.addOptional(input.press, 2, "5–8", LoadPrescription.Rpe("RPE 8"))

        val d3 = mutableListOf(
            ExercisePrescription("Приседания", 1, "5", LoadPrescription.Kilograms(squatDay3)),
            ExercisePrescription("Жим лёжа", 1, "5", LoadPrescription.Kilograms(benchDay3)),
            ExercisePrescription("Становая тяга", 1, "5", LoadPrescription.Kilograms(deadDay3))
        )
        d3.addOptional(input.core, 3, "9–12", LoadPrescription.Rpe("RPE 9"))

        return WorkoutWeekPlan(week, listOf(day(week, 1, d1), day(week, 2, d2), day(week, 3, d3)))
    }

    private fun peakWeek(week: Int, input: ProgramInput): WorkoutWeekPlan {
        val medium = input.level == TrainingLevel.INTERMEDIATE
        val increment = week * 2.5
        val squatTop = roundToPlate(input.squat5RM + increment)
        val benchTop = roundToPlate(input.bench5RM + increment)
        val deadTop = roundToPlate(input.deadlift5RM + increment)

        val d1: List<ExercisePrescription>
        val d2: List<ExercisePrescription>
        val d3: List<ExercisePrescription>

        when (week) {
            1 -> {
                val first = mutableListOf(
                    ExercisePrescription("Приседания", if (medium) 4 else 3, if (medium) "4" else "3", LoadPrescription.Kilograms(roundToPlate(squatTop * 0.9))),
                    ExercisePrescription("Жим лёжа", if (medium) 4 else 3, if (medium) "4" else "3", LoadPrescription.Kilograms(roundToPlate(benchTop * 0.9)))
                )
                first.addOptional(input.pull, if (medium) 3 else 2, if (medium) "4" else "3", LoadPrescription.Rpe("RPE 8"))
                first.addOptional(input.arms, 5, if (medium) "8–12" else "5–8", LoadPrescription.Rpe("RPE 8–9"))
                first.addOptional(input.core, 3, "5–8", LoadPrescription.Rpe("RPE 9"))
                d1 = first
                d2 = peakDayTwo(input, roundToPlate(squatTop * 0.9), roundToPlate(benchTop * 0.9), week)
                d3 = peakMainDay(input, 2, "3", LoadPrescription.Kilograms(squatTop), LoadPrescription.Kilograms(benchTop), LoadPrescription.Kilograms(deadTop), includeCore = true)
            }
            2 -> {
                val first = mutableListOf(
                    ExercisePrescription("Приседания", 3, if (medium) "3" else "2", LoadPrescription.Kilograms(roundToPlate(squatTop * 0.9))),
                    ExercisePrescription("Жим лёжа", 3, if (medium) "3" else "2", LoadPrescription.Kilograms(roundToPlate(benchTop * 0.9)))
                )
                first.addOptional(input.pull, if (medium) 3 else 2, if (medium) "3" else "2", LoadPrescription.Rpe("RPE 7"))
                if (medium) {
                    first.addOptional(input.arms, 5, "5–8", LoadPrescription.Rpe("RPE 8–9"))
                    first.addOptional(input.core, 3, "5–8", LoadPrescription.Rpe("RPE 9"))
                }
                d1 = first
                d2 = peakDayTwo(input, roundToPlate(squatTop * 0.9), roundToPlate(benchTop * 0.9), week)
                d3 = peakMainDay(input, 3, if (medium) "2" else "1", LoadPrescription.Kilograms(squatTop), LoadPrescription.Kilograms(benchTop), LoadPrescription.Kilograms(deadTop), includeCore = medium)
            }
            3 -> {
                val first = mutableListOf(
                    ExercisePrescription("Приседания", if (medium) 3 else 2, if (medium) "2" else "1", LoadPrescription.Kilograms(if (medium) roundToPlate(squatTop * 0.9) else squatTop)),
                    ExercisePrescription("Жим лёжа", if (medium) 3 else 2, if (medium) "2" else "1", LoadPrescription.Kilograms(if (medium) roundToPlate(benchTop * 0.9) else benchTop))
                )
                if (medium) {
                    first.addOptional(input.pull, 3, "2", LoadPrescription.Rpe("RPE 7"))
                } else {
                    first.add(ExercisePrescription("Становая тяга", 2, "1", LoadPrescription.Kilograms(deadTop)))
                }
                d1 = first
                val squatBase = if (medium) roundToPlate(squatTop * 0.9) else squatTop
                val benchBase = if (medium) roundToPlate(benchTop * 0.9) else benchTop
                d2 = listOf(
                    ExercisePrescription("Приседания", 2, if (medium) "3" else "1", LoadPrescription.Kilograms(roundToPlate(squatBase * 0.8))),
                    ExercisePrescription("Жим лёжа", if (medium) 3 else 2, if (medium) "3" else "1", LoadPrescription.Kilograms(roundToPlate(benchBase * 0.8)))
                )
                d3 = if (medium) {
                    peakMainDay(input, 3, "2", LoadPrescription.Kilograms(squatTop), LoadPrescription.Kilograms(benchTop), LoadPrescription.Kilograms(deadTop), includeCore = false)
                } else {
                    peakMainDay(input, 0, "", LoadPrescription.TestOneRepMax, LoadPrescription.TestOneRepMax, LoadPrescription.TestOneRepMax, includeCore = false)
                }
            }
            else -> {
                d1 = peakMainDay(input, 2, "1", LoadPrescription.Kilograms(squatTop), LoadPrescription.Kilograms(benchTop), LoadPrescription.Kilograms(deadTop), includeCore = false)
                d2 = listOf(
                    ExercisePrescription("Приседания", 3, "1", LoadPrescription.Kilograms(roundToPlate(squatTop * 0.8))),
                    ExercisePrescription("Жим лёжа", 3, "1", LoadPrescription.Kilograms(roundToPlate(benchTop * 0.8)))
                )
                d3 = peakMainDay(input, 0, "", LoadPrescription.TestOneRepMax, LoadPrescription.TestOneRepMax, LoadPrescription.TestOneRepMax, includeCore = false)
            }
        }
        return WorkoutWeekPlan(week, listOf(day(week, 1, d1), day(week, 2, d2), day(week, 3, d3)))
    }

    private fun peakDayTwo(input: ProgramInput, squatDayOne: Double, benchDayOne: Double, week: Int): List<ExercisePrescription> {
        val medium = input.level == TrainingLevel.INTERMEDIATE
        val reps = if (week == 2 && medium) "4" else "5"
        val result = mutableListOf(
            ExercisePrescription("Приседания", 2, reps, LoadPrescription.Kilograms(roundToPlate(squatDayOne * 0.8))),
            ExercisePrescription("Жим лёжа", if (medium) 3 else 2, reps, LoadPrescription.Kilograms(roundToPlate(benchDayOne * 0.8)))
        )
        if (week == 1) {
            result.addOptional(input.back, 3, if (medium) "8–12" else "5–8", LoadPrescription.Rpe("RPE 8–9"))
        }
        if (week == 1 || medium) {
            result.addOptional(
                input.press,
                if (week == 2 && medium) 3 else 2,
                "5–8",
                LoadPrescription.Rpe(if (week == 2) "RPE 7" else "RPE 8")
            )
        }
        return result
    }

    private fun peakMainDay(
        input: ProgramInput,
        sets: Int,
        reps: String,
        squat: LoadPrescription,
        bench: LoadPrescription,
        deadlift: LoadPrescription,
        includeCore: Boolean
    ): List<ExercisePrescription> {
        val result = mutableListOf(
            ExercisePrescription("Приседания", sets, reps, squat),
            ExercisePrescription("Жим лёжа", sets, reps, bench),
            ExercisePrescription("Становая тяга", sets, reps, deadlift)
        )
        if (includeCore) result.addOptional(input.core, 3, "9–12", LoadPrescription.Rpe("RPE 9"))
        return result
    }

    private fun day(week: Int, number: Int, exercises: List<ExercisePrescription>) =
        WorkoutDayPlan("$week-$number", number, "ДЕНЬ $number", exercises)

    private fun MutableList<ExercisePrescription>.addOptional(
        name: String?,
        sets: Int,
        reps: String,
        load: LoadPrescription
    ) {
        if (name.isNullOrBlank()) return
        add(ExercisePrescription(name, sets, reps, load, isOptional = true))
    }
}
