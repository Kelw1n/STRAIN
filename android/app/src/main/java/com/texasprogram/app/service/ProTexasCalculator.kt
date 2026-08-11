package com.texasprogram.app.service

import com.texasprogram.app.model.ExercisePrescription
import com.texasprogram.app.model.LoadPrescription
import com.texasprogram.app.model.ProgramInput
import com.texasprogram.app.model.WorkoutDayPlan
import com.texasprogram.app.model.WorkoutPlan
import com.texasprogram.app.model.WorkoutWeekPlan

/// Продвинутый техасский метод Лимара: десять двухнедельных блоков.
///
/// Всё считается от веса интенсивного дня второй недели блока. Первые два блока
/// идут ниже твоего 5ПМ — это разгон, а не занижение: программу начинают после
/// нескольких месяцев классического техаса, и вход делается заведомо посильным.
object ProTexasCalculator {
    const val BLOCK_COUNT = 10
    const val WEEK_COUNT = BLOCK_COUNT * 2

    /// Сдвиг веса интенсивного дня по блокам: −20, −10, свой 5ПМ, дальше по 2,5 кг.
    val blockOffsets = listOf(-20.0, -10.0, 0.0, 2.5, 5.0, 7.5, 10.0, 12.5, 15.0, 17.5)

    private var cacheKey: Any? = null
    private var cacheValue: WorkoutPlan? = null

    fun generate(input: ProgramInput): WorkoutPlan {
        cacheValue?.let { if (cacheKey == input) return it }
        val plan = WorkoutPlan(weeks = (1..WEEK_COUNT).map { week(it, input) }, isPeaking = false)
        cacheKey = input
        cacheValue = plan
        return plan
    }

    /// Номер двухнедельного блока для календарной недели.
    fun block(week: Int): Int = (week + 1) / 2

    /// Вес интенсивного дня блока — точка отсчёта для всех остальных дней.
    fun intensityWeight(base: Double, block: Int): Double {
        val offset = blockOffsets.getOrElse(block - 1) { blockOffsets.last() }
        return ProgramCalculator.roundToPlate(base + offset)
    }

    private fun week(number: Int, input: ProgramInput): WorkoutWeekPlan {
        val blockNumber = block(number)
        val squat = intensityWeight(input.squat5RM, blockNumber)
        val bench = intensityWeight(input.bench5RM, blockNumber)
        val deadlift = intensityWeight(input.deadlift5RM, blockNumber)
        // Нечётная неделя — первая в блоке, подготовительная; чётная — классическая.
        val isFirstWeek = number % 2 == 1

        val days = if (isFirstWeek) firstWeek(input, squat, bench, deadlift)
        else secondWeek(input, squat, bench, deadlift)

        return WorkoutWeekPlan(
            number = number,
            days = days.mapIndexed { index, exercises ->
                WorkoutDayPlan("$number-${index + 1}", index + 1, dayTitle(isFirstWeek, index + 1), exercises)
            }
        )
    }

    private fun dayTitle(isFirstWeek: Boolean, dayNumber: Int): String {
        val phase = if (isFirstWeek) "ПОДГОТОВКА" else "КЛАССИКА"
        return phase + when (dayNumber) {
            1 -> " · ОБЪЁМ"
            2 -> " · ВОССТАНОВЛЕНИЕ"
            else -> " · ИНТЕНСИВНОСТЬ"
        }
    }

    // MARK: - Первая неделя блока: субмаксимальная

    private fun firstWeek(input: ProgramInput, squat: Double, bench: Double, deadlift: Double): List<List<ExercisePrescription>> {
        val volume = mutableListOf(
            main("Приседания", 5, "7", squat, 0.8),
            main("Жим лёжа", 5, "7", bench, 0.8)
        )
        add(volume, optional(input.pull, 4, "7–10", "RPE 7"))
        add(volume, optional(input.core, 4, "5–8", "RPE 8"))

        val recovery = mutableListOf(
            main("Приседания", 2, "5", squat, 0.7),
            main("Жим лёжа", 2, "5", bench, 0.7)
        )
        add(recovery, optional(input.back, 3, "8–12", "RPE 8"))
        add(recovery, optional(input.press, 3, "7–10", "RPE 7"))
        add(recovery, optional(input.arms, 4, "9–12", "RPE 8"))

        val intensity = mutableListOf(
            main("Приседания", 3, "3", squat, 0.9),
            main("Жим лёжа", 3, "3", bench, 0.9),
            main("Становая тяга", 3, "3", deadlift, 0.9)
        )
        add(intensity, optional(input.back, 3, "8–12", "RPE 8"))
        add(intensity, optional(input.core, 4, "13–16", "RPE 8"))

        return listOf(volume, recovery, intensity)
    }

    // MARK: - Вторая неделя блока: классический техас

    private fun secondWeek(input: ProgramInput, squat: Double, bench: Double, deadlift: Double): List<List<ExercisePrescription>> {
        val volume = mutableListOf(
            main("Приседания", 5, "5", squat, 0.9),
            main("Жим лёжа", 5, "5", bench, 0.9)
        )
        add(volume, optional(input.pull, 2, "5", "RPE 7"))
        add(volume, optional(input.core, 3, "5–8", "RPE 8"))

        val recovery = mutableListOf(
            main("Приседания", 2, "5", squat, 0.7),
            main("Жим лёжа", 2, "5", bench, 0.7)
        )
        add(recovery, optional(input.back, 3, "5–8", "RPE 8"))
        add(recovery, optional(input.press, 3, "5", "RPE 7"))
        add(recovery, optional(input.arms, 3, "9–12", "RPE 8"))

        // Кульминация блока: истинный пятиповторный максимум.
        val intensity = mutableListOf(
            main("Приседания", 1, "5", squat, 1.0),
            main("Жим лёжа", 1, "5", bench, 1.0),
            main("Становая тяга", 1, "5", deadlift, 1.0)
        )
        add(intensity, optional(input.back, 3, "8–12", "RPE 8"))
        add(intensity, optional(input.core, 3, "13–16", "RPE 8"))

        return listOf(volume, recovery, intensity)
    }

    private fun main(name: String, sets: Int, reps: String, weight: Double, percent: Double) =
        ExercisePrescription(name, sets, reps, LoadPrescription.Kilograms(ProgramCalculator.roundToPlate(weight * percent)))

    private fun optional(name: String?, sets: Int, reps: String, rpe: String): ExercisePrescription? {
        if (name.isNullOrBlank()) return null
        return ExercisePrescription(name, sets, reps, LoadPrescription.Rpe(rpe), isOptional = true)
    }

    private fun add(list: MutableList<ExercisePrescription>, value: ExercisePrescription?) {
        if (value != null) list.add(value)
    }
}
