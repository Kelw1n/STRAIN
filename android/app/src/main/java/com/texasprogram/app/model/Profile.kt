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
    /// Когда какая тренировка была отмечена и с какими весами — основа графика.
    val completionLog: List<CompletionRecord> = emptyList(),
    /// Сколько подходов закрыто: ключ «деньПлана|названиеУпражнения».
    val setProgress: Map<String, Int> = emptyMap(),
    /// Длительность отдыха, который запускается сам после отметки подхода.
    val defaultRestSeconds: Int = 180,
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
    val peakDeadlift5RM: Double? = null,
    /// Свои упражнения и правки дней. Поле новое: у сохранённых профилей
    /// подставится пустой список, и план останется прежним.
    val planEdits: List<PlanEdit> = emptyList()
) {

    // MARK: - Входные данные и план

    val input: ProgramInput
        get() = ProgramInput(squat5RM, bench5RM, deadlift5RM, level, pull, arms, core, back, press)

    val upperLowerInput: UpperLowerInput
        get() = UpperLowerInput(squat5RM, bench5RM, deadlift5RM)

    /// Идёт ли пиковый цикл. Только «Техас» — у «Верх / Низ» пикирования нет.
    val isPeaking: Boolean
        get() = programKind == TrainingProgramKind.TEXAS && peakingActive

    /// План программы с наложенными пользовательскими правками.
    /// Правки входят в ключ кэша: иначе добавленное упражнение не появилось бы,
    /// пока не поменяются максимумы.
    val workoutPlan: WorkoutPlan
        get() {
            val key = listOf(programKind, squat5RM, bench5RM, deadlift5RM, level, pull, arms, core, back, press, isPeaking, peakSquat5RM, peakBench5RM, peakDeadlift5RM, planEdits)
            return PlanCache.resolve(key) { applyEdits(generatedPlan) }
        }

    /// Чистый расчёт без правок — нужен экрану настройки, чтобы показать,
    /// что именно программа предлагала до вмешательства.
    val generatedPlan: WorkoutPlan
        get() = when (programKind) {
            TrainingProgramKind.TEXAS ->
                if (isPeaking) ProgramCalculator.generatePeaking(peakingInput)
                else ProgramCalculator.generate(input)
            TrainingProgramKind.UPPER_LOWER -> UpperLowerCalculator.generate(upperLowerInput)
        }

    private fun applyEdits(plan: WorkoutPlan): WorkoutPlan {
        // Быстрый выход: у профиля без правок расчёт остаётся прежним.
        if (planEdits.isEmpty()) return plan
        val peaking = isPeaking
        return plan.copy(weeks = plan.weeks.map { week ->
            week.copy(days = week.days.map { day ->
                val edits = planEdits.filter { it.matches(week.number, day.number, peaking) }
                if (edits.isEmpty()) return@map day
                val list = day.exercises.toMutableList()
                // Порядок важен: сначала убираем и заменяем упражнения программы,
                // и только потом дописываем свои — иначе замена могла бы попасть
                // на только что добавленное упражнение.
                edits.filter { it.kind == PlanEditKind.HIDE }.forEach { edit ->
                    list.removeAll { it.key == edit.targetKey }
                }
                edits.filter { it.kind == PlanEditKind.REPLACE }.forEach { edit ->
                    val index = list.indexOfFirst { it.key == edit.targetKey }
                    if (index >= 0) list[index] = edit.prescription
                }
                edits.filter { it.kind == PlanEditKind.ADD }.forEach { list.add(it.prescription) }
                day.copy(exercises = list)
            })
        })
    }

    // MARK: - Свои упражнения

    /// Правки, которые действуют на конкретную тренировку.
    fun edits(week: Int, day: Int): List<PlanEdit> =
        planEdits.filter { it.matches(week, day, isPeaking) }

    /// Упражнения дня, как их предлагает сама программа, без правок.
    fun generatedExercises(week: Int, day: Int): List<ExercisePrescription> =
        generatedPlan.weeks.firstOrNull { it.number == week }
            ?.days?.firstOrNull { it.number == day }?.exercises.orEmpty()

    fun addExercise(
        name: String,
        sets: Int,
        reps: String,
        kilograms: Double?,
        loadText: String?,
        week: Int,
        day: Int,
        scope: PlanEditScope
    ): ProgramProfile = copy(
        planEdits = planEdits + PlanEdit(
            kind = PlanEditKind.ADD,
            day = day,
            week = if (scope == PlanEditScope.SINGLE) week else null,
            isPeaking = isPeaking,
            name = name,
            sets = sets,
            reps = reps,
            kilograms = kilograms,
            loadText = loadText
        )
    )

    fun replaceExercise(
        target: ExercisePrescription,
        name: String,
        sets: Int,
        reps: String,
        kilograms: Double?,
        loadText: String?,
        week: Int,
        day: Int,
        scope: PlanEditScope
    ): ProgramProfile = dropEdits(target.key, day, week, scope).let { cleaned ->
        cleaned.copy(
            planEdits = cleaned.planEdits + PlanEdit(
                kind = PlanEditKind.REPLACE,
                day = day,
                week = if (scope == PlanEditScope.SINGLE) week else null,
                isPeaking = isPeaking,
                targetKey = target.key,
                name = name,
                sets = sets,
                reps = reps,
                kilograms = kilograms,
                loadText = loadText
            )
        )
    }

    fun hideExercise(
        target: ExercisePrescription,
        week: Int,
        day: Int,
        scope: PlanEditScope
    ): ProgramProfile = dropEdits(target.key, day, week, scope).let { cleaned ->
        cleaned.copy(
            planEdits = cleaned.planEdits + PlanEdit(
                kind = PlanEditKind.HIDE,
                day = day,
                week = if (scope == PlanEditScope.SINGLE) week else null,
                isPeaking = isPeaking,
                targetKey = target.key
            )
        )
    }

    /// Убирает правку и вместе с ней осиротевшие отметки подходов:
    /// упражнения больше нет, а его закрытые подходы остались бы в хранилище.
    fun removeEdit(edit: PlanEdit): ProgramProfile {
        val suffix = "|" + edit.id
        return copy(
            planEdits = planEdits.filterNot { it.id == edit.id },
            setProgress = setProgress.filterNot { it.key.endsWith(suffix) }
        )
    }

    fun removeEdits(week: Int, day: Int): ProgramProfile =
        edits(week, day).fold(this) { profile, edit -> profile.removeEdit(edit) }

    /// Одно упражнение — одна правка. Иначе замена поверх замены накопила бы
    /// цепочку, из которой не выбраться кнопкой «вернуть как было».
    private fun dropEdits(targetKey: String, day: Int, week: Int, scope: PlanEditScope): ProgramProfile {
        val peaking = isPeaking
        val stale = planEdits.filter {
            it.targetKey == targetKey && it.day == day && it.isPeaking == peaking &&
                (scope == PlanEditScope.EVERY_WEEK || it.week == null || it.week == week)
        }
        return stale.fold(this) { profile, edit -> profile.removeEdit(edit) }
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

    /// Ключ отметки. У пикового цикла своё пространство имён: недели там тоже
    /// нумеруются с единицы и иначе накладывались бы на основную программу.
    fun dayKey(week: Int, day: Int): String = if (isPeaking) "peak-$week-$day" else "$week-$day"

    fun isCompleted(week: Int, day: Int): Boolean = completedDayKeys.contains(dayKey(week, day))

    /// Считаем только текущий цикл.
    val completedDayCount: Int
        get() = completedDayKeys
            .filter { if (isPeaking) it.startsWith("peak-") else !it.startsWith("peak-") }
            .toSet().size

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
        next = next.syncSets(week, day, done)
        next = if (done) {
            next.copy(lastCompletionEpochDay = today.toEpochDay()).recordCompletion(week, day, today)
        } else {
            val key = dayKey(week, day)
            next.copy(completionLog = next.completionLog.filterNot { it.key == key })
        }
        return next
    }

    /// Кладёт в историю дату и рабочие веса основных движений этого дня.
    /// Веса сохраняем на момент отметки: правка максимумов не должна менять прошлое.
    private fun recordCompletion(week: Int, day: Int, today: LocalDate): ProgramProfile {
        val key = dayKey(week, day)
        val exercises = workoutPlan.weeks.firstOrNull { it.number == week }
            ?.days?.firstOrNull { it.number == day }?.exercises.orEmpty()

        fun weight(vararg names: String): Double? = names.firstNotNullOfOrNull { name ->
            (exercises.firstOrNull { it.name == name }?.load as? LoadPrescription.Kilograms)?.value
        }

        val record = CompletionRecord(
            key = key,
            epochDay = today.toEpochDay(),
            week = week,
            day = day,
            squat = weight("Приседания", "Приседания со штангой"),
            bench = weight("Жим лёжа"),
            deadlift = weight("Становая тяга")
        )
        return copy(completionLog = completionLog.filterNot { it.key == key } + record)
    }

    // MARK: - Отметка по подходам

    private fun setKey(week: Int, day: Int, exercise: ExercisePrescription): String =
        dayKey(week, day) + "|" + exercise.key

    fun completedSets(week: Int, day: Int, exercise: ExercisePrescription): Int =
        setProgress[setKey(week, day, exercise)] ?: 0

    /// Добавит ли тап подход — по этому признаку запускается отдых.
    fun willAddSet(week: Int, day: Int, exercise: ExercisePrescription, index: Int): Boolean {
        val current = completedSets(week, day, exercise)
        val target = if (current == index + 1) index else index + 1
        return target > current
    }

    /// Тап доводит счётчик до точки; повторный тап по последней закрытой — снимает её.
    fun toggleSet(week: Int, day: Int, exercise: ExercisePrescription, index: Int): ProgramProfile {
        val key = setKey(week, day, exercise)
        val current = setProgress[key] ?: 0
        val target = if (current == index + 1) index else index + 1
        val updated = setProgress.toMutableMap()
        if (target > 0) updated[key] = target else updated.remove(key)
        return copy(setProgress = updated)
    }

    /// Упражнения без подходов (тест 1ПМ) не считаются — там нечего отмечать.
    fun allSetsDone(workout: ScheduledWorkout): Boolean {
        val list = exercises(workout).filter { it.sets > 0 }
        if (list.isEmpty()) return false
        return list.all { completedSets(workout.week, workout.day.number, it) >= it.sets }
    }

    /// Точки и отметка дня не расходятся: закрыли день — загорелись все точки.
    private fun syncSets(week: Int, day: Int, filled: Boolean): ProgramProfile {
        val exercises = workoutPlan.weeks.firstOrNull { it.number == week }
            ?.days?.firstOrNull { it.number == day }?.exercises.orEmpty()
        val updated = setProgress.toMutableMap()
        for (exercise in exercises) {
            val key = setKey(week, day, exercise)
            if (filled && exercise.sets > 0) updated[key] = exercise.sets else updated.remove(key)
        }
        return copy(setProgress = updated)
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
        val key = dayKey(week, day)
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
