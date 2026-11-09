package com.texasprogram.app.model

import com.texasprogram.app.service.ProgramCalculator
import java.time.LocalDate
import java.util.Locale

/// Как шли веса последние две недели программы — от ответа зависит,
/// будет ли разгрузка в начале следующего цикла. Те же правила — в CycleFinish.swift.
enum class CycleFeel(val title: String, val caption: String, val outcome: String) {
    EASY(
        "Легко",
        "Оставался запас, можно было больше",
        "Без разгрузки: следующий цикл начнётся сразу со следующего шага прибавки."
    ),
    NORMAL(
        "Тяжело, но всё сделал",
        "Последние подходы шли на пределе",
        "Без разгрузки: следующий цикл начнётся с весов, на которых ты закончил."
    ),
    HARD(
        "Не добирал повторы",
        "Срывал подходы или стоял на одном весе",
        "Разгрузка: старт на 10 % легче, дальше веса растут с той же прибавкой и быстро догоняют прошлый максимум."
    );

    companion object {
        const val DELOAD = 0.9
        const val EASY_BONUS = 1.025
    }
}

/// Рост одного упражнения за цикл.
data class CycleGrowth(val name: String, val from: Double, val to: Double) {
    val delta: Double get() = to - from
}

data class CycleRecap(val weeks: Int, val workouts: Int, val tonnage: Double, val growth: List<CycleGrowth>)

/// Подсказка по записям последних двух недель.
data class CycleFeelHint(val averageEffort: Double?, val missedSets: Int, val loggedSets: Int, val suggestion: CycleFeel) {
    val text: String
        get() {
            val parts = mutableListOf<String>()
            averageEffort?.let { parts += "среднее усилие ${String.format(Locale.US, "%.1f", it).replace('.', ',')} из 10" }
            if (loggedSets > 0) {
                parts += if (missedSets == 0) "все записанные подходы выполнены"
                else "недобрано подходов: $missedSets из $loggedSets"
            }
            return "По записям: " + parts.joinToString(", ") + ". Похоже на «${suggestion.title}»."
        }
}

/// Старт следующего цикла.
sealed class NextCycleStart {
    abstract val changes: List<CycleGrowth>

    data class Custom(val program: CustomProgram, override val changes: List<CycleGrowth>) : NextCycleStart()
    data class Maxes(val squat: Double, val bench: Double, val deadlift: Double, override val changes: List<CycleGrowth>) : NextCycleStart()
}

object CycleFinish {
    /// Первое число в записи повторов: «8–10» → 8, «5» → 5.
    fun minimumReps(text: String): Int? {
        val digits = StringBuilder()
        for (c in text) {
            if (c.isDigit()) digits.append(c) else if (digits.isNotEmpty()) break
        }
        return digits.toString().toIntOrNull()
    }

    fun suggestion(averageEffort: Double?, missedSets: Int, loggedSets: Int): CycleFeel? {
        if (averageEffort == null && loggedSets == 0) return null
        val missedShare = if (loggedSets > 0) missedSets.toDouble() / loggedSets else 0.0
        if (missedShare >= 0.15 || (averageEffort ?: 0.0) >= 9) return CycleFeel.HARD
        if (missedSets == 0 && averageEffort != null && averageEffort <= 5) return CycleFeel.EASY
        return CycleFeel.NORMAL
    }

    /// Новые стартовые веса своей программы. Упражнения без прибавки не трогаем.
    fun nextCustom(program: CustomProgram, feel: CycleFeel): Pair<CustomProgram, List<CycleGrowth>> {
        val changes = mutableListOf<CycleGrowth>()
        val lastWeek = maxOf(program.weeks, 1)
        val days = program.days.map { day ->
            day.copy(exercises = day.exercises.map { exercise ->
                val start = exercise.kilograms
                if (start == null || exercise.weeklyIncrement == 0.0) return@map exercise
                val finish = ProgramCalculator.roundToPlate(start + (lastWeek - 1) * exercise.weeklyIncrement)
                val value = when (feel) {
                    CycleFeel.EASY -> finish + exercise.weeklyIncrement
                    CycleFeel.NORMAL -> finish
                    CycleFeel.HARD -> finish * CycleFeel.DELOAD
                }
                val rounded = maxOf(ProgramCalculator.roundToPlate(value), 0.0)
                if (changes.none { it.name == exercise.name }) changes += CycleGrowth(exercise.name, start, rounded)
                exercise.copy(kilograms = rounded)
            })
        }
        return program.copy(days = days) to changes
    }

    /// Новый максимум готовой программы: `ratio` — во сколько раз вырос тяжёлый вес за цикл.
    fun nextMax(max: Double, ratio: Double, feel: CycleFeel): Double {
        val carried = max * ratio.coerceIn(1.0, 1.3)
        val value = when (feel) {
            CycleFeel.EASY -> carried * CycleFeel.EASY_BONUS
            CycleFeel.NORMAL -> carried
            CycleFeel.HARD -> carried * CycleFeel.DELOAD
        }
        return ProgramCalculator.roundToPlate(value)
    }
}

/// Все тренировки плана закрыты: выполнены или сознательно пропущены.
val ProgramProfile.isProgramFinished: Boolean
    get() {
        val weeks = workoutPlan.weeks
        if (weeks.isEmpty()) return false
        return weeks.all { week -> week.days.all { isCompleted(week.number, it.number) || isSkipped(week.number, it.number) } }
    }

private val ProgramProfile.cycleRecords: List<CompletionRecord>
    get() = completionLog.filter { isOwnKey(it.key) }.sortedBy { it.epochDay }

val ProgramProfile.cycleRecap: CycleRecap
    get() {
        val tonnage = setLog.entries.sumOf { (key, entry) ->
            val day = key.substringBefore('|')
            if (isOwnKey(day)) entry.tonnage else 0.0
        }
        val program = customProgram
        val growth = if (programKind == TrainingProgramKind.CUSTOM && program != null) {
            CycleFinish.nextCustom(program, CycleFeel.NORMAL).second
        } else {
            val records = cycleRecords
            MainLift.values().mapNotNull { lift ->
                val values = records.mapNotNull { r ->
                    when (lift) {
                        MainLift.SQUAT -> r.actualSquat ?: r.squat
                        MainLift.BENCH -> r.actualBench ?: r.bench
                        MainLift.DEADLIFT -> r.actualDeadlift ?: r.deadlift
                    }
                }
                if (values.isEmpty()) null else CycleGrowth(lift.title, values.first(), values.last())
            }
        }
        return CycleRecap(workoutPlan.weeks.size, completedDayCount, tonnage, growth)
    }

val ProgramProfile.cycleFeelHint: CycleFeelHint?
    get() {
        val weeks = workoutPlan.weeks.takeLast(2)
        val numbers = weeks.map { it.number }.toSet()
        val efforts = cycleRecords
            .filter { it.week in numbers }
            .mapNotNull { r -> r.effort?.let { CycleFinish.minimumReps(it) } }
            .filter { it in 1..10 }
        var missed = 0
        var logged = 0
        for (week in weeks) for (day in week.days) for (exercise in day.exercises) {
            if (exercise.sets <= 0) continue
            val target = CycleFinish.minimumReps(exercise.reps) ?: continue
            for (index in 0 until exercise.sets) {
                val entry = setEntry(week.number, day.number, exercise, index) ?: continue
                logged++
                if (entry.reps < target) missed++
            }
        }
        val average = if (efforts.isEmpty()) null else efforts.sum().toDouble() / efforts.size
        val suggestion = CycleFinish.suggestion(average, missed, logged) ?: return null
        return CycleFeelHint(average, missed, logged, suggestion)
    }

private fun ProgramProfile.cycleRatio(lift: MainLift): Double {
    fun heaviest(week: WorkoutWeekPlan?): Double? = week?.days?.flatMap { it.exercises }
        ?.filter { it.name == lift.title }
        ?.mapNotNull { (it.load as? LoadPrescription.Kilograms)?.value }
        ?.maxOrNull()
    val plan = workoutPlan
    val first = heaviest(plan.weeks.firstOrNull()) ?: return 1.0
    val last = heaviest(plan.weeks.lastOrNull()) ?: return 1.0
    return if (first > 0) last / first else 1.0
}

/// Каким будет следующий цикл при этом ответе.
fun ProgramProfile.nextCycleStart(feel: CycleFeel): NextCycleStart? {
    if (programKind == TrainingProgramKind.CUSTOM) {
        val program = customProgram ?: return null
        val (next, changes) = CycleFinish.nextCustom(program, feel)
        return NextCycleStart.Custom(next, changes)
    }
    val squatNow = peakSquat5RM ?: squat5RM
    val benchNow = peakBench5RM ?: bench5RM
    val deadliftNow = peakDeadlift5RM ?: deadlift5RM
    val squat = CycleFinish.nextMax(squatNow, cycleRatio(MainLift.SQUAT), feel)
    val bench = CycleFinish.nextMax(benchNow, cycleRatio(MainLift.BENCH), feel)
    val deadlift = CycleFinish.nextMax(deadliftNow, cycleRatio(MainLift.DEADLIFT), feel)
    val label = maximumLabel
    return NextCycleStart.Maxes(
        squat, bench, deadlift,
        listOf(
            CycleGrowth("${MainLift.SQUAT.title} · $label", squatNow, squat),
            CycleGrowth("${MainLift.BENCH.title} · $label", benchNow, bench),
            CycleGrowth("${MainLift.DEADLIFT.title} · $label", deadliftNow, deadlift)
        )
    )
}

/// Следующий цикл своей программы с новыми весами.
fun ProgramProfile.startNewCustomCycle(program: CustomProgram, today: LocalDate = LocalDate.now()): ProgramProfile =
    copy(customProgram = program).startNewCycle(squat5RM, bench5RM, deadlift5RM, today)

/// Повтор цикла с теми же весами.
fun ProgramProfile.repeatCycle(today: LocalDate = LocalDate.now()): ProgramProfile =
    startNewCycle(squat5RM, bench5RM, deadlift5RM, today)
