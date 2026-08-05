package com.texasprogram.app.service

import com.texasprogram.app.model.AdditionalExerciseCategory
import com.texasprogram.app.model.ExercisePrescription
import com.texasprogram.app.model.LoadPrescription
import com.texasprogram.app.model.ProgramInput
import com.texasprogram.app.model.WorkoutDayPlan
import com.texasprogram.app.model.WorkoutPlan
import com.texasprogram.app.model.WorkoutWeekPlan

/// Объём и состав тренировки для конкретного стажа.
///
/// Различия между уровнями держим числами в одном месте, а не ветками по коду:
/// так видно всю таблицу разом и её можно проверить тестом.
data class FullBodyVolume(
    val volumeSets: Int,
    val lightSets: Int,
    val topSets: Int,
    /// Добивочные подходы после тяжёлого — 90 % от рабочего.
    val backoffSets: Int,
    val deadliftSets: Int,
    /// Подсобка. Ноль означает, что слот на этом стаже не используется.
    val pullSets: Int,
    val extraPullSets: Int,
    val verticalPressSets: Int,
    val armSets: Int,
    val coreSets: Int
)

/// Сколько человек тренируется — от этого зависит и объём, и набор упражнений.
enum class FullBodyLevel(val title: String) {
    UNDER_YEAR("Меньше года"),
    ABOUT_YEAR("Около года"),
    OVER_YEAR("Больше года"),
    TWO_YEARS("Два года и больше");

    /// Растёт ли вес от тренировки к тренировке, а не от недели к неделе.
    val isLinear: Boolean get() = this == UNDER_YEAR

    val subtitle: String
        get() = when (this) {
            UNDER_YEAR -> "Минимум объёма, вес растёт каждую тренировку"
            ABOUT_YEAR -> "Недельная волна, средний объём"
            OVER_YEAR -> "Больше подходов, добивка и вертикальный жим"
            TWO_YEARS -> "Максимальный объём, прибавка через неделю"
        }

    val explanation: String
        get() = when (this) {
            UNDER_YEAR -> "Три подхода в основных движениях, из подсобки только тяга, руки и кор по два подхода. Присед прибавляет 2,5 кг каждую тренировку, жим — раз в неделю. Пока растёт так, усложнять нечего."
            ABOUT_YEAR -> "Техасская волна внутри недели: пять подходов в день объёма, лёгкий день, один тяжёлый подход. Подсобка по три подхода. Прибавка 2,5 кг в неделю."
            OVER_YEAR -> "Тот же каркас, но объёма больше: четыре подхода в тяге, добивка после тяжёлого подхода, добавляются вертикальный жим и дополнительная тяга."
            TWO_YEARS -> "Шесть подходов в день объёма, две добивки, подсобка по четыре подхода. Прибавка 2,5 кг раз в две недели: линейно каждую неделю на этом стаже уже не растёт."
        }

    /// Прибавка к тяжёлому подходу на этой неделе.
    fun increment(week: Int): Double = when (this) {
        UNDER_YEAR, ABOUT_YEAR, OVER_YEAR -> (week - 1) * 2.5
        // Полблина в неделю не набрать: прибавляем 2,5 кг раз в две недели.
        TWO_YEARS -> ((week - 1) / 2) * 2.5
    }

    val volume: FullBodyVolume
        get() = when (this) {
            UNDER_YEAR -> FullBodyVolume(3, 3, 3, 0, 1, 2, 0, 0, 2, 2)
            ABOUT_YEAR -> FullBodyVolume(5, 2, 1, 0, 1, 3, 0, 0, 3, 3)
            OVER_YEAR -> FullBodyVolume(5, 3, 1, 1, 1, 4, 3, 3, 3, 3)
            TWO_YEARS -> FullBodyVolume(6, 3, 1, 2, 2, 4, 3, 4, 4, 3)
        }

    /// Какие слоты подсобки предлагать при настройке. Остальные на этом стаже
    /// в план не попадут, и спрашивать их незачем.
    val accessorySlots: List<AdditionalExerciseCategory>
        get() {
            val result = mutableListOf(AdditionalExerciseCategory.BACK)
            if (volume.extraPullSets > 0) result.add(AdditionalExerciseCategory.PULL)
            if (volume.verticalPressSets > 0) result.add(AdditionalExerciseCategory.PRESS)
            result.add(AdditionalExerciseCategory.ARMS)
            result.add(AdditionalExerciseCategory.CORE)
            return result
        }
}

/// Фулбади на три тренировки в неделю: каждый день присед, жим, тяга, руки и кор.
///
/// Прогрессия линейная, как в техасском методе: расчёт от пятиповторного максимума,
/// тяжёлый подход прибавляет 2,5 кг, лёгкие дни — проценты от него. Стаж меняет
/// объём и набор упражнений, а не только шаг прибавки.
object FullBodyCalculator {
    const val WEEK_COUNT = 12

    private var cacheKey: Any? = null
    private var cacheValue: WorkoutPlan? = null

    fun generate(input: ProgramInput, level: FullBodyLevel): WorkoutPlan {
        val key = listOf(input, level)
        cacheValue?.let { if (cacheKey == key) return it }
        val plan = WorkoutPlan(
            weeks = (1..WEEK_COUNT).map { week(it, input, level) },
            isPeaking = false
        )
        cacheKey = key
        cacheValue = plan
        return plan
    }

    private fun week(number: Int, input: ProgramInput, level: FullBodyLevel): WorkoutWeekPlan {
        val days = if (level.isLinear) linearWeek(number, input, level) else waveWeek(number, input, level)
        return WorkoutWeekPlan(number, days)
    }

    // MARK: - Меньше года: вес растёт каждую тренировку

    private fun linearWeek(week: Int, input: ProgramInput, level: FullBodyLevel): List<WorkoutDayPlan> {
        val volume = level.volume
        // Присед прибавляет каждую тренировку, жим и тяга — раз в неделю:
        // верх тела так быстро не растёт, и упереться в потолок можно за месяц.
        val benchWeight = ProgramCalculator.roundToPlate(input.bench5RM * 0.85 + (week - 1) * 2.5)
        val deadWeight = ProgramCalculator.roundToPlate(input.deadlift5RM * 0.85 + (week - 1) * 5.0)

        return (1..3).map { dayNumber ->
            val session = (week - 1) * 3 + dayNumber
            val squatWeight = ProgramCalculator.roundToPlate(input.squat5RM * 0.8 + (session - 1) * 2.5)

            val exercises = mutableListOf(
                ExercisePrescription("Приседания", volume.volumeSets, "5", LoadPrescription.Kilograms(squatWeight)),
                ExercisePrescription("Жим лёжа", volume.volumeSets, "5", LoadPrescription.Kilograms(benchWeight))
            )
            // Становая тяжело восстанавливается — один раз в неделю.
            if (dayNumber == 2) {
                exercises.add(ExercisePrescription("Становая тяга", volume.deadliftSets, "5", LoadPrescription.Kilograms(deadWeight)))
            }
            exercises.addAll(accessories(input, volume, dayNumber))
            day(week, dayNumber, "ФУЛБАДИ " + letter(dayNumber), exercises)
        }
    }

    // MARK: - Год и дальше: недельная волна техасского метода

    private fun waveWeek(week: Int, input: ProgramInput, level: FullBodyLevel): List<WorkoutDayPlan> {
        val volume = level.volume
        val increment = level.increment(week)
        val squatTop = ProgramCalculator.roundToPlate(input.squat5RM + increment)
        val benchTop = ProgramCalculator.roundToPlate(input.bench5RM + increment)
        val deadTop = ProgramCalculator.roundToPlate(input.deadlift5RM + increment)

        // День объёма — 90 % от тяжёлого, лёгкий день — 80 % от дня объёма.
        val squatVolume = ProgramCalculator.roundToPlate(squatTop * 0.9)
        val benchVolume = ProgramCalculator.roundToPlate(benchTop * 0.9)
        val squatLight = ProgramCalculator.roundToPlate(squatVolume * 0.8)
        val benchLight = ProgramCalculator.roundToPlate(benchVolume * 0.8)

        val day1 = mutableListOf(
            ExercisePrescription("Приседания", volume.volumeSets, "5", LoadPrescription.Kilograms(squatVolume)),
            ExercisePrescription("Жим лёжа", volume.volumeSets, "5", LoadPrescription.Kilograms(benchVolume))
        )
        day1.addAll(accessories(input, volume, 1))

        val day2 = mutableListOf(
            ExercisePrescription("Приседания", volume.lightSets, "5", LoadPrescription.Kilograms(squatLight)),
            ExercisePrescription("Жим лёжа", volume.lightSets, "5", LoadPrescription.Kilograms(benchLight)),
            ExercisePrescription("Становая тяга", volume.deadliftSets, "5", LoadPrescription.Kilograms(deadTop))
        )
        day2.addAll(accessories(input, volume, 2))

        val day3 = mutableListOf(
            ExercisePrescription("Приседания", volume.topSets, "5", LoadPrescription.Kilograms(squatTop)),
            ExercisePrescription("Жим лёжа", volume.topSets, "5", LoadPrescription.Kilograms(benchTop))
        )
        // Добивка идёт отдельным упражнением: у неё своё имя и свой идентификатор,
        // иначе она слилась бы с рабочим подходом в отметках и в истории.
        if (volume.backoffSets > 0) {
            day3.add(
                ExercisePrescription(
                    name = "Приседания · добивка",
                    sets = volume.backoffSets,
                    reps = "5",
                    load = LoadPrescription.Kilograms(ProgramCalculator.roundToPlate(squatTop * 0.9)),
                    id = "squat-backoff"
                )
            )
            day3.add(
                ExercisePrescription(
                    name = "Жим лёжа · добивка",
                    sets = volume.backoffSets,
                    reps = "5",
                    load = LoadPrescription.Kilograms(ProgramCalculator.roundToPlate(benchTop * 0.9)),
                    id = "bench-backoff"
                )
            )
        }
        day3.addAll(accessories(input, volume, 3))

        return listOf(
            day(week, 1, "ФУЛБАДИ · ОБЪЁМ", day1),
            day(week, 2, "ФУЛБАДИ · ЛЁГКИЙ", day2),
            day(week, 3, "ФУЛБАДИ · ТЯЖЁЛЫЙ", day3)
        )
    }

    // MARK: - Подсобка

    /// Тяга, руки и кор есть в каждой тренировке — иначе это не фулбади, а сплит.
    /// Число подходов и набор слотов задаёт стаж, названия — выбор в настройках.
    private fun accessories(input: ProgramInput, volume: FullBodyVolume, dayNumber: Int): List<ExercisePrescription> {
        val result = mutableListOf<ExercisePrescription>()

        if (volume.pullSets > 0) {
            val name = input.back ?: if (dayNumber == 2) "Тяга в наклоне" else "Подтягивания"
            result.add(ExercisePrescription(name, volume.pullSets, "8–12", LoadPrescription.Rpe("RPE 8"), isOptional = true))
        }
        // Дополнительная тяга — только в лёгкий день: в остальные уже есть становая
        // или тяжёлый присед, и спина не вывезет.
        if (volume.extraPullSets > 0 && dayNumber == 2) {
            val name = input.pull ?: "Румынская тяга"
            result.add(ExercisePrescription(name, volume.extraPullSets, "5–8", LoadPrescription.Rpe("RPE 8"), isOptional = true))
        }
        if (volume.verticalPressSets > 0) {
            val name = input.press ?: "Жим штанги стоя"
            result.add(ExercisePrescription(name, volume.verticalPressSets, "6–8", LoadPrescription.Rpe("RPE 8"), isOptional = true))
        }
        if (volume.armSets > 0) {
            val name = input.arms ?: "Растянутый суперсет: бицепс + трицепс"
            result.add(ExercisePrescription(name, volume.armSets, "8–12", LoadPrescription.Rpe("RPE 8–9"), isOptional = true))
        }
        if (volume.coreSets > 0) {
            val name = input.core ?: "Скручивания лёжа на полу"
            result.add(ExercisePrescription(name, volume.coreSets, "10–15", LoadPrescription.Rpe("RPE 9"), isOptional = true))
        }
        return result
    }

    private fun letter(dayNumber: Int): String = when (dayNumber) {
        1 -> "A"
        2 -> "B"
        else -> "C"
    }

    private fun day(week: Int, number: Int, title: String, exercises: List<ExercisePrescription>) =
        WorkoutDayPlan("$week-$number", number, title, exercises)
}
