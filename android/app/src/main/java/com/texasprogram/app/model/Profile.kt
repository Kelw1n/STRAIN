package com.texasprogram.app.model

import com.texasprogram.app.service.ProgramCalculator
import com.texasprogram.app.service.RuDate
import com.texasprogram.app.service.ScheduledWorkout
import com.texasprogram.app.service.UpperLowerCalculator
import com.texasprogram.app.service.WorkoutSchedule
import com.texasprogram.app.service.WorkoutScheduler
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

/// Кэш последнего плана: он пересобирался бы на каждое обращение из UI.
private object PlanCache {
    private var key: Any? = null
    private var value: WorkoutPlan? = null

    fun resolve(newKey: Any, build: () -> WorkoutPlan): WorkoutPlan {
        val cached = value
        if (cached != null && key == newKey) return cached
        val built = build()
        key = newKey
        value = built
        return built
    }
}

@Serializable
data class ProgramProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Профиль",
    val createdAtEpochDay: Long = LocalDate.now().toEpochDay(),
    val programKind: TrainingProgramKind = TrainingProgramKind.TEXAS,
    val squat5RM: Double = 100.0,
    val bench5RM: Double = 100.0,
    val deadlift5RM: Double = 100.0,
    val level: TrainingLevel = TrainingLevel.BEGINNER,
    val pull: String? = null,
    val arms: String? = null,
    val core: String? = null,
    val back: String? = null,
    val press: String? = null,
    val completedDayKeys: List<String> = emptyList(),
    val completedBenchSessions: List<Int> = emptyList(),
    /// Дни недели тренировок в нумерации Calendar (1 — воскресенье). Пусто — значения по умолчанию.
    val scheduleWeekdays: List<Int> = emptyList(),
    /// Привязка недели плана к календарю больше не используется: оба режима считают
    /// даты от сегодняшнего дня. Поля оставлены, чтобы не ломать сохранённые профили.
    val scheduleAnchorWeek: Int = 0,
    val scheduleAnchorEpochDay: Long? = null,
    /// Как план ложится на календарь. Поле новое: у сохранённых профилей подставится
    /// значение по умолчанию, то есть режим по дням недели.
    val scheduleMode: ScheduleMode = ScheduleMode.WEEKDAY,
    /// Дата последней отметки: по ней видно, что сегодня уже тренировались.
    val lastCompletionEpochDay: Long? = null,
    val cycleStartedEpochDay: Long = LocalDate.now().toEpochDay(),
    val peakingActive: Boolean = false,
    val peakSquat5RM: Double? = null,
    val peakBench5RM: Double? = null,
    val peakDeadlift5RM: Double? = null
) {

    // MARK: - Входные данные и план

    val input: ProgramInput
        get() = ProgramInput(squat5RM, bench5RM, deadlift5RM, level, pull, arms, core, back, press)

    val upperLowerInput: UpperLowerInput
        get() = UpperLowerInput(squat5RM, bench5RM, deadlift5RM)

    val workoutPlan: WorkoutPlan
        get() {
            val key = listOf(programKind, squat5RM, bench5RM, deadlift5RM, level, pull, arms, core, back, press)
            return PlanCache.resolve(key) {
                when (programKind) {
                    TrainingProgramKind.TEXAS -> ProgramCalculator.generate(input)
                    TrainingProgramKind.UPPER_LOWER -> UpperLowerCalculator.generate(upperLowerInput)
                }
            }
        }

    val maximumLabel: String get() = if (programKind == TrainingProgramKind.TEXAS) "5ПМ" else "1ПМ"

    val totalDays: Int get() = workoutPlan.weeks.sumOf { it.days.size }

    val peakingInput: ProgramInput
        get() = input.copy(
            squat5RM = peakSquat5RM ?: squat5RM,
            bench5RM = peakBench5RM ?: bench5RM,
            deadlift5RM = peakDeadlift5RM ?: deadlift5RM
        )

    // MARK: - Отметки дней

    fun isCompleted(week: Int, day: Int): Boolean = completedDayKeys.contains("$week-$day")

    val completedDayCount: Int get() = completedDayKeys.toSet().size

    /// Отметка дня. Верхний день двигает волну жима на одну тренировку:
    /// какая именно это тренировка, определяет счётчик волны, а не день недели.
    fun toggleCompleted(week: Int, day: Int, today: LocalDate = LocalDate.now()): ProgramProfile {
        val done = !isCompleted(week, day)
        var next = setDayCompleted(week, day, done)
        if (carriesBenchWave(day)) {
            next = if (done) {
                nextBenchSession?.let { next.setBenchCompleted(it, true) } ?: next
            } else {
                completedBenchSessions.maxOrNull()?.let { next.setBenchCompleted(it, false) } ?: next
            }
        }
        if (done) next = next.copy(lastCompletionEpochDay = today.toEpochDay())
        return next
    }

    /// Дни, которые несут волну жима: в «Верх / Низ» это верхние дни — первый и третий.
    fun carriesBenchWave(day: Int): Boolean =
        programKind == TrainingProgramKind.UPPER_LOWER && (day == 1 || day == 3)

    /// Упражнения тренировки: вспомогательные — из дня программы, жим — из волны.
    fun exercises(workout: ScheduledWorkout): List<ExercisePrescription> {
        val session = workout.benchSession ?: return workout.day.exercises
        val wave = benchWave.firstOrNull { it.id == session } ?: return workout.day.exercises
        return workout.day.exercises.map { exercise ->
            if (exercise.benchSession == null) exercise
            else ExercisePrescription(
                name = wave.exerciseName,
                sets = wave.sets.size,
                reps = wave.repsText,
                load = LoadPrescription.RepRange(wave.weightsText),
                benchSession = wave.id
            )
        }
    }

    private fun setDayCompleted(week: Int, day: Int, done: Boolean): ProgramProfile {
        val key = "$week-$day"
        val keys = if (done) {
            if (completedDayKeys.contains(key)) completedDayKeys else completedDayKeys + key
        } else {
            completedDayKeys.filterNot { it == key }
        }
        return copy(completedDayKeys = keys)
    }

    // MARK: - Волна «Жим 14»

    val benchWave: List<BenchSessionPlan>
        get() = if (programKind == TrainingProgramKind.UPPER_LOWER) {
            UpperLowerCalculator.benchWave(upperLowerInput)
        } else {
            emptyList()
        }

    /// Номер тренировки волны для дня программы: понедельник и четверг — жимовые.
    fun benchSession(week: Int, day: Int): Int? {
        if (programKind != TrainingProgramKind.UPPER_LOWER) return null
        return when (day) {
            1 -> (week - 1) * 2 + 1
            3 -> (week - 1) * 2 + 2
            else -> null
        }
    }

    /// Обратное сопоставление: неделя и день программы для тренировки волны.
    fun dayForBenchSession(session: Int): Pair<Int, Int> =
        Pair((session + 1) / 2, if (session % 2 == 1) 1 else 3)

    fun isBenchCompleted(session: Int): Boolean = completedBenchSessions.contains(session)

    val completedBenchCount: Int get() = completedBenchSessions.toSet().size

    val nextBenchSession: Int?
        get() = (1..UpperLowerCalculator.benchSessionCount).firstOrNull { !isBenchCompleted(it) }

    /// Отметка жима синхронно двигает день программы.
    fun toggleBenchCompleted(session: Int, today: LocalDate = LocalDate.now()): ProgramProfile {
        val done = !isBenchCompleted(session)
        val (week, day) = dayForBenchSession(session)
        val next = setBenchCompleted(session, done).setDayCompleted(week, day, done)
        return if (done) next.copy(lastCompletionEpochDay = today.toEpochDay()) else next
    }

    private fun setBenchCompleted(session: Int, done: Boolean): ProgramProfile {
        val list = if (done) {
            if (completedBenchSessions.contains(session)) completedBenchSessions else completedBenchSessions + session
        } else {
            completedBenchSessions.filterNot { it == session }
        }
        return copy(completedBenchSessions = list)
    }

    // MARK: - Расписание по дням недели

    val trainingDayCount: Int get() = if (programKind == TrainingProgramKind.TEXAS) 3 else 4

    /// «Верх / Низ» — понедельник, вторник, четверг, пятница. Техас — понедельник, среда, пятница.
    val defaultWeekdays: List<Int>
        get() = if (programKind == TrainingProgramKind.TEXAS) listOf(2, 4, 6) else listOf(2, 3, 5, 6)

    val weekdays: List<Int>
        get() = if (scheduleWeekdays.size == trainingDayCount) scheduleWeekdays else defaultWeekdays

    fun weekday(day: Int): Int {
        val list = weekdays
        if (day < 1 || day > list.size) return list.firstOrNull() ?: 2
        return list[day - 1]
    }

    fun setWeekday(weekday: Int, day: Int): ProgramProfile {
        val list = weekdays.toMutableList()
        if (day < 1 || day > list.size) return this
        list[day - 1] = weekday
        return copy(scheduleWeekdays = list)
    }

    fun weekdayName(day: Int): String = RuDate.full(weekday(day))

    fun shortWeekdayName(day: Int): String = RuDate.short(weekday(day))

    /// Заголовок дня с реальным днём недели: «ПОНЕДЕЛЬНИК · ВЕРХ ТЯЖЁЛЫЙ».
    fun fullTitle(day: WorkoutDayPlan): String =
        weekdayName(day.number).uppercase() + " · " + day.title

    fun cycleStartedAtDate(): LocalDate = LocalDate.ofEpochDay(cycleStartedEpochDay)

    fun scheduleAnchorDate(): LocalDate? = scheduleAnchorEpochDay?.let { LocalDate.ofEpochDay(it) }

    fun lastCompletionDate(): LocalDate? = lastCompletionEpochDay?.let { LocalDate.ofEpochDay(it) }

    fun schedule(today: LocalDate = LocalDate.now()): WorkoutSchedule =
        WorkoutScheduler.build(this, workoutPlan, today)

    /// Ближайшая дата, на которую попадёт тренировочный день с его днём недели.
    fun nextOccurrence(day: Int, today: LocalDate = LocalDate.now()): LocalDate {
        val candidate = RuDate.mondayOf(today).plusDays(RuDate.offsetFromMonday(weekday(day)))
        return if (candidate < today) candidate.plusWeeks(1) else candidate
    }

    /// Дата, на которую встанет день плана, если сделать его текущим.
    /// В режиме очереди это ближайший свободный тренировочный день, иначе — свой день недели.
    fun plannedStartDate(day: Int, today: LocalDate = LocalDate.now()): LocalDate =
        when (scheduleMode) {
            ScheduleMode.QUEUE -> WorkoutScheduler.nextTrainingSlot(this, today)
            ScheduleMode.WEEKDAY -> nextOccurrence(day, today)
        }

    /// «Я сейчас здесь»: всё до выбранного дня отмечается выполненным, выбранный день
    /// становится следующим.
    fun setCurrentWorkout(week: Int, day: Int, today: LocalDate = LocalDate.now()): ProgramProfile {
        val keys = mutableListOf<String>()
        // Волна остаётся непрерывной: сколько верхних дней закрыли, столько жимов и сделано.
        var benchDone = 0
        for (planWeek in workoutPlan.weeks) {
            for (planDay in planWeek.days) {
                val isBefore = planWeek.number < week || (planWeek.number == week && planDay.number < day)
                if (!isBefore) continue
                keys.add("${planWeek.number}-${planDay.number}")
                if (carriesBenchWave(planDay.number)) benchDone++
            }
        }
        val benches = if (benchDone > 0) {
            (1..minOf(benchDone, UpperLowerCalculator.benchSessionCount)).toList()
        } else {
            emptyList()
        }
        // Отметки проставлены задним числом — сегодняшней тренировки среди них нет.
        // Привязка к календарной неделе не нужна: оба режима считают даты
        // от сегодняшнего дня и от того, что осталось невыполненным.
        return copy(
            completedDayKeys = keys,
            completedBenchSessions = benches,
            lastCompletionEpochDay = null,
            scheduleAnchorWeek = 0,
            scheduleAnchorEpochDay = null
        )
    }

    /// То же самое, но по номеру тренировки волны жима.
    fun setCurrentBenchSession(session: Int, today: LocalDate = LocalDate.now()): ProgramProfile {
        val (week, day) = dayForBenchSession(session)
        return setCurrentWorkout(week, day, today)
    }

    companion object {
        fun texas(input: ProgramInput, name: String = "Профиль") = ProgramProfile(
            name = name,
            programKind = TrainingProgramKind.TEXAS,
            squat5RM = input.squat5RM,
            bench5RM = input.bench5RM,
            deadlift5RM = input.deadlift5RM,
            level = input.level,
            pull = input.pull,
            arms = input.arms,
            core = input.core,
            back = input.back,
            press = input.press
        )

        fun upperLower(input: UpperLowerInput, name: String = "Профиль") = ProgramProfile(
            name = name,
            programKind = TrainingProgramKind.UPPER_LOWER,
            squat5RM = input.squat1RM,
            bench5RM = input.bench1RM,
            deadlift5RM = input.deadlift1RM,
            level = TrainingLevel.BEGINNER
        )
    }
}
