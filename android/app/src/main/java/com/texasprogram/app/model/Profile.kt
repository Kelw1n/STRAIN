package com.texasprogram.app.model

import com.texasprogram.app.service.FullBodyCalculator
import com.texasprogram.app.service.EuthanasiaCalculator
import com.texasprogram.app.service.EuthanasiaInput
import com.texasprogram.app.service.FullBodyLevel
import com.texasprogram.app.service.ProTexasCalculator
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
    val planEdits: List<PlanEdit> = emptyList(),
    /// Что реально сделано в подходе: ключ «деньПлана|упражнение|номерПодхода».
    val setLog: Map<String, SetEntry> = emptyMap(),
    /// Пропущенные тренировки.
    val skipped: List<SkippedWorkout> = emptyList(),
    /// Не гасить экран, пока открыта тренировка.
    val keepScreenOn: Boolean = true,
    /// Какой проход программы идёт. Первый цикл ключи не меняет.
    val cycleNumber: Int = 1,
    /// Своя программа, если выбран этот вид. У остальных не используется.
    val customProgram: CustomProgram? = null,
    /// Стаж для фулбади: от него зависит схема прогрессии.
    val fullBodyLevel: FullBodyLevel = FullBodyLevel.ABOUT_YEAR,
    /// Вход «Эвтаназии»: три движения с тестами. У остальных программ не используется.
    val euthanasiaInput: EuthanasiaInput? = null,
    /// Подстраивать ли веса под то, как прошёл тяжёлый день.
    /// По умолчанию выключено: классический техас линеен по замыслу.
    val autoregulationEnabled: Boolean = false,
    /// Оценки тяжёлых дней по движениям.
    val liftReports: List<LiftReport> = emptyList(),
    /// Заметка к тренировке: ключ дня плана.
    val workoutNotes: Map<String, String> = emptyMap()
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
            val key = listOf(programKind, squat5RM, bench5RM, deadlift5RM, level, pull, arms, core, back, press, isPeaking, peakSquat5RM, peakBench5RM, peakDeadlift5RM, planEdits, skipped, customProgram, fullBodyLevel, euthanasiaInput, autoregulationEnabled, liftReports)
            // Порядок обязателен: сначала задержка подменяет неделю расчёта, потом
            // правки подставляют свои упражнения. Наоборот задержка стёрла бы
            // то, что пользователь вписал руками.
            return PlanCache.resolve(key) { applyEdits(applyAutoregulation(applyHolds(generatedPlan))) }
        }

    /// Чистый расчёт без правок — нужен экрану настройки, чтобы показать,
    /// что именно программа предлагала до вмешательства.
    val generatedPlan: WorkoutPlan
        get() = when (programKind) {
            TrainingProgramKind.TEXAS ->
                if (isPeaking) ProgramCalculator.generatePeaking(peakingInput)
                else ProgramCalculator.generate(input)
            TrainingProgramKind.UPPER_LOWER -> UpperLowerCalculator.generate(upperLowerInput)
            TrainingProgramKind.FULL_BODY -> FullBodyCalculator.generate(input, fullBodyLevel)
            TrainingProgramKind.PRO_TEXAS -> ProTexasCalculator.generate(input)
            TrainingProgramKind.EUTHANASIA ->
                euthanasiaInput?.let { EuthanasiaCalculator.generate(it) } ?: WorkoutPlan(emptyList(), false)
            TrainingProgramKind.CUSTOM -> customProgram?.plan ?: WorkoutPlan(emptyList(), false)
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
                // Порядок применяем последним: к этому моменту состав дня уже
                // окончательный. Упражнения вне списка идут в конец — их могли
                // добавить после перестановки. sortedBy устойчива, поэтому свой
                // ряд они сохранят.
                val order = edits.lastOrNull { it.kind == PlanEditKind.ORDER }?.order
                if (order != null) {
                    val rank = order.withIndex().associate { (index, key) -> key to index }
                    return@map day.copy(exercises = list.sortedBy { rank[it.key] ?: Int.MAX_VALUE })
                }
                day.copy(exercises = list)
            })
        })
    }

    /// Обновляет запись истории, если день уже отмечен: факт мог появиться позже.
    /// Дату оставляем исходную — тренировка была тогда, а не в момент правки.
    private fun refreshCompletion(week: Int, day: Int, today: LocalDate): ProgramProfile {
        if (!isCompleted(week, day)) return this
        val key = dayKey(week, day)
        val original = completionLog.firstOrNull { it.key == key }?.epochDay ?: today.toEpochDay()
        return recordCompletion(week, day, LocalDate.ofEpochDay(original))
    }

    // MARK: - Пространство имён отметок

    /// Номер цикла и пиковый режим. Первый цикл ничего не добавляет —
    /// ключи сохранённых профилей остаются прежними, миграция не нужна.
    val keyPrefix: String
        get() {
            val cycle = if (cycleNumber > 1) "c$cycleNumber-" else ""
            return if (isPeaking) cycle + "peak-" else cycle
        }

    /// Принадлежит ли ключ текущему пространству.
    ///
    /// Одной проверки префикса мало: у пустого префикса «peak-1-1» тоже
    /// начинается с него, а у «c2-» внутри может лежать пиковый ключ.
    fun isOwnKey(key: String): Boolean {
        if (!key.startsWith(keyPrefix)) return false
        val rest = key.removePrefix(keyPrefix)
        return !rest.contains("peak-") && !rest.startsWith("c")
    }

    // MARK: - Авторегуляция

    /// Умеет ли программа подстраиваться. Механизм завязан на тяжёлый день,
    /// поэтому работает там, где он есть: у обоих техасов.
    val supportsAutoregulation: Boolean
        get() = programKind == TrainingProgramKind.TEXAS || programKind == TrainingProgramKind.PRO_TEXAS

    private val autoregulationActive: Boolean get() = autoregulationEnabled && supportsAutoregulation

    /// Ключ оценки: то же пространство имён, что у отметок, плюс номер недели.
    fun reportKey(week: Int): String = keyPrefix + week

    fun report(lift: MainLift, week: Int): LiftReport? =
        liftReports.firstOrNull { it.key == reportKey(week) && it.lift == lift }

    fun reports(week: Int): List<LiftReport> = liftReports.filter { it.key == reportKey(week) }

    fun setReport(lift: MainLift, week: Int, outcome: LiftOutcome, today: LocalDate = LocalDate.now()): ProgramProfile {
        val key = reportKey(week)
        return copy(
            liftReports = liftReports.filterNot { it.key == key && it.lift == lift } +
                LiftReport(key, lift, outcome, today.toEpochDay())
        )
    }

    fun clearReport(lift: MainLift, week: Int): ProgramProfile {
        val key = reportKey(week)
        return copy(liftReports = liftReports.filterNot { it.key == key && it.lift == lift })
    }

    /// Две неудачи подряд по движению — линейный рост кончился, пора сбрасывать цикл.
    fun isStalled(lift: MainLift, upTo: Int): Boolean {
        val recent = (1 until maxOf(upTo, 1)).mapNotNull { report(lift, it) }
        if (recent.size < 2) return false
        return recent.takeLast(2).all { it.outcome.isFailure }
    }

    val stalledLifts: List<MainLift>
        get() {
            if (!autoregulationActive) return emptyList()
            val weeks = generatedPlan.weeks.size + 1
            return MainLift.entries.filter { isStalled(it, weeks) }
        }

    /// Вес тяжёлого дня, каким его запланировал расчёт.
    private fun plannedIntensity(lift: MainLift, week: WorkoutWeekPlan): Double? {
        val day = week.days.lastOrNull() ?: return null
        val load = day.exercises.firstOrNull { it.name == lift.title }?.load
        return (load as? LoadPrescription.Kilograms)?.value
    }

    /// Вес тяжёлого дня с учётом того, как прошли прошлые недели.
    ///
    /// Недели без оценки идут с обычной прибавкой — поэтому включённый
    /// переключатель сам по себе план не меняет, пока не начнёшь отвечать.
    fun autoregulatedIntensity(lift: MainLift, week: Int, plan: WorkoutPlan): Double? {
        val first = plan.weeks.firstOrNull() ?: return null
        var value = plannedIntensity(lift, first) ?: return null
        for (earlier in 1 until maxOf(week, 1)) {
            val outcome = report(lift, earlier)?.outcome
            value = outcome?.next(value, lift.isUpper) ?: (value + 2.5)
        }
        return ProgramCalculator.roundToPlate(value)
    }

    /// Масштабирует веса недели так, чтобы тяжёлый день попал в нужное число.
    ///
    /// Остальные дни считаются процентами от тяжёлого, поэтому достаточно
    /// умножить их на то же отношение — арифметика остаётся в калькуляторе.
    private fun applyAutoregulation(plan: WorkoutPlan): WorkoutPlan {
        if (!autoregulationActive || liftReports.isEmpty()) return plan
        return plan.copy(weeks = plan.weeks.map { week ->
            val factors = mutableMapOf<String, Double>()
            for (lift in MainLift.entries) {
                val planned = plannedIntensity(lift, week) ?: continue
                if (planned <= 0) continue
                val target = autoregulatedIntensity(lift, week.number, plan) ?: continue
                if (target != planned) factors[lift.title] = target / planned
            }
            if (factors.isEmpty()) return@map week
            week.copy(days = week.days.map { day ->
                day.copy(exercises = day.exercises.map { exercise ->
                    val factor = factors[exercise.name]
                    val load = exercise.load
                    if (factor == null || load !is LoadPrescription.Kilograms) exercise
                    else exercise.copy(load = LoadPrescription.Kilograms(ProgramCalculator.roundToPlate(load.value * factor)))
                })
            })
        })
    }

    // MARK: - Заметки

    fun note(week: Int, day: Int): String = workoutNotes[dayKey(week, day)] ?: ""

    fun setNote(text: String, week: Int, day: Int): ProgramProfile {
        val key = dayKey(week, day)
        val trimmed = text.trim()
        return copy(workoutNotes = if (trimmed.isEmpty()) workoutNotes - key else workoutNotes + (key to trimmed))
    }

    /// Неделя, на которой человек сейчас: первая, где остались незакрытые дни.
    ///
    /// Считаем по отметкам, а не по календарю. Пропущенная пятница не должна
    /// перекидывать на следующую неделю только потому, что её день прошёл, —
    /// незакрытая неделя остаётся текущей, пока в ней есть что делать.
    /// Отмеченные пропуски в счёт не идут: их закрыли сознательно.
    val currentWeek: Int
        get() {
            val plan = workoutPlan
            val open = plan.weeks.firstOrNull { week ->
                week.days.any { !isCompleted(week.number, it.number) && !isSkipped(week.number, it.number) }
            }
            return open?.number ?: plan.weeks.lastOrNull()?.number ?: 1
        }

    // MARK: - Пропущенные тренировки

    fun isSkipped(week: Int, day: Int): Boolean =
        skipped.any { it.week == week && it.day == day && it.isPeaking == isPeaking }

    /// Сколько недель прогрессия стоит на месте к началу указанной недели.
    ///
    /// Считаем пропуски строго раньше этой недели: неделя, которую пропустили,
    /// должна остаться со своими весами, а замереть должна следующая.
    fun holdCount(before: Int): Int =
        skipped.filter { it.isPeaking == isPeaking && it.holdsProgression && it.week < before }
            .map { it.week }.toSet().size

    /// Какая неделя расчёта достаётся календарной неделе.
    fun effectiveWeek(week: Int): Int = maxOf(week - holdCount(week), 1)

    /// Задержка прогрессии: неделя берёт веса более ранней.
    ///
    /// Веса не пересчитываем формулой — подставляем упражнения нужной недели
    /// целиком. Так арифметика остаётся в одном месте, в самом калькуляторе.
    private fun applyHolds(plan: WorkoutPlan): WorkoutPlan {
        if (skipped.none { it.isPeaking == isPeaking && it.holdsProgression }) return plan
        val byNumber = plan.weeks.associateBy { it.number }
        return plan.copy(weeks = plan.weeks.map { week ->
            val source = effectiveWeek(week.number)
            val donor = byNumber[source]
            if (source == week.number || donor == null) return@map week
            // Номера и идентификаторы дней остаются своими: по ним считаются отметки.
            week.copy(days = week.days.zip(donor.days).map { (own, borrowed) ->
                own.copy(exercises = borrowed.exercises)
            })
        })
    }

    /// «Не пришёл на тренировку». Пропуск не отмечает день выполненным —
    /// тренировка остаётся в расписании, её ещё можно закрыть.
    fun markSkipped(week: Int, day: Int, holdsProgression: Boolean, today: LocalDate = LocalDate.now()): ProgramProfile {
        if (isSkipped(week, day)) return this
        return copy(skipped = skipped + SkippedWorkout(
            week = week, day = day, isPeaking = isPeaking,
            epochDay = today.toEpochDay(), holdsProgression = holdsProgression
        ))
    }

    fun removeSkip(item: SkippedWorkout): ProgramProfile =
        copy(skipped = skipped.filterNot { it.id == item.id })

    /// Пропуски текущего пространства имён — их показывают настройки.
    val activeSkips: List<SkippedWorkout>
        get() = skipped.filter { it.isPeaking == isPeaking }.sortedWith(compareBy({ it.week }, { it.day }))

    // MARK: - Фактически выполненные подходы

    private fun logKey(week: Int, day: Int, exercise: ExercisePrescription, index: Int): String =
        setKey(week, day, exercise) + "|" + index

    fun setEntry(week: Int, day: Int, exercise: ExercisePrescription, index: Int): SetEntry? =
        setLog[logKey(week, day, exercise, index)]

    /// Записывает факт подхода и заодно закрывает точку: отмечать дважды незачем.
    fun recordSet(
        week: Int,
        day: Int,
        exercise: ExercisePrescription,
        index: Int,
        reps: Int,
        weight: Double,
        today: LocalDate = LocalDate.now()
    ): ProgramProfile {
        val key = setKey(week, day, exercise)
        val progress = setProgress.toMutableMap()
        if ((progress[key] ?: 0) < index + 1) progress[key] = index + 1
        return copy(
            setLog = setLog + (logKey(week, day, exercise, index) to SetEntry(reps, weight)),
            setProgress = progress
        ).refreshCompletion(week, day, today)
    }

    fun clearSetEntry(week: Int, day: Int, exercise: ExercisePrescription, index: Int, today: LocalDate = LocalDate.now()): ProgramProfile =
        copy(setLog = setLog - logKey(week, day, exercise, index)).refreshCompletion(week, day, today)

    /// Номера подходов, у которых факт записан.
    fun loggedSets(week: Int, day: Int, exercise: ExercisePrescription): Set<Int> =
        (0 until maxOf(exercise.sets, 0)).filter { setEntry(week, day, exercise, it) != null }.toSet()

    /// Наибольший записанный вес движения за день — им факт попадает в график.
    fun actualWeight(week: Int, day: Int, names: List<String>): Double? {
        val exercises = workoutPlan.weeks.firstOrNull { it.number == week }
            ?.days?.firstOrNull { it.number == day }?.exercises.orEmpty()
        return exercises.filter { names.contains(it.name) }
            .flatMap { exercise -> loggedSets(week, day, exercise).mapNotNull { setEntry(week, day, exercise, it)?.weight } }
            .maxOrNull()
    }

    /// Тоннаж по неделям: только записанные подходы, без догадок по плану.
    val weeklyTonnage: List<WeeklyTonnage>
        get() {
            val totals = mutableMapOf<Int, Pair<Double, Int>>()
            for ((key, entry) in setLog) {
                val day = SetKeyParts.dayKey(key)
                if (!isOwnKey(day)) continue
                val week = SetKeyParts.week(day, keyPrefix) ?: continue
                val current = totals[week] ?: (0.0 to 0)
                totals[week] = (current.first + entry.tonnage) to (current.second + 1)
            }
            return totals.map { WeeklyTonnage(it.key, it.value.first, it.value.second) }.sortedBy { it.week }
        }

    // MARK: - Серия без пропусков

    /// Недели подряд, закрытые полностью.
    ///
    /// Считаем по отметкам, а не по календарю: тренировка, пропущенная и позже
    /// наверстанная, серию не рвёт. Незакрытая последняя неделя тоже не рвёт —
    /// она просто ещё идёт.
    val weekStreak: Pair<Int, Int>
        get() {
            val closed = workoutPlan.weeks.map { week -> week.days.all { isCompleted(week.number, it.number) } }
            if (closed.isEmpty()) return 0 to 0

            var best = 0
            var run = 0
            for (done in closed) {
                run = if (done) run + 1 else 0
                best = maxOf(best, run)
            }

            var index = closed.size - 1
            while (index >= 0 && !closed[index]) index--
            var current = 0
            while (index >= 0 && closed[index]) {
                current++
                index--
            }
            return current to best
        }

    // MARK: - Новый цикл

    /// Начинает программу заново с новыми максимумами.
    ///
    /// Отметки не стираются: они остаются под префиксом прошлого цикла и просто
    /// перестают быть видимыми. История и графики от этого сквозные.
    fun startNewCycle(squat: Double, bench: Double, deadlift: Double, today: LocalDate = LocalDate.now()): ProgramProfile =
        copy(
            cycleNumber = cycleNumber + 1,
            peakingActive = false,
            peakSquat5RM = null,
            peakBench5RM = null,
            peakDeadlift5RM = null,
            squat5RM = squat,
            bench5RM = bench,
            deadlift5RM = deadlift,
            completedBenchSessions = emptyList(),
            lastCompletionEpochDay = null,
            cycleStartedEpochDay = today.toEpochDay(),
            scheduleAnchorWeek = 0,
            scheduleAnchorEpochDay = null
        )

    // MARK: - Свои упражнения

    /// Порядок упражнений дня. Пустой список убирает свою перестановку.
    fun setOrder(keys: List<String>, week: Int, day: Int, scope: PlanEditScope): ProgramProfile {
        val peaking = isPeaking
        val cleaned = planEdits.filterNot {
            it.kind == PlanEditKind.ORDER && it.day == day && it.isPeaking == peaking &&
                (scope == PlanEditScope.EVERY_WEEK || it.week == null || it.week == week)
        }
        if (keys.isEmpty()) return copy(planEdits = cleaned)
        return copy(
            planEdits = cleaned + PlanEdit(
                kind = PlanEditKind.ORDER,
                day = day,
                week = if (scope == PlanEditScope.SINGLE) week else null,
                isPeaking = peaking,
                order = keys
            )
        )
    }

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

    val maximumLabel: String get() = if (programKind.usesFiveRepMax) "5ПМ" else "1ПМ"

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
    fun dayKey(week: Int, day: Int): String = keyPrefix + "$week-$day"

    fun isCompleted(week: Int, day: Int): Boolean = completedDayKeys.contains(dayKey(week, day))

    /// Считаем только текущий цикл.
    val completedDayCount: Int
        get() = completedDayKeys.filter { isOwnKey(it) }.toSet().size

    /// Отметка дня. Верхний день двигает волну жима на одну тренировку:
    /// какая именно это тренировка, определяет счётчик волны, а не день недели.
    fun toggleCompleted(week: Int, day: Int, today: LocalDate = LocalDate.now()): ProgramProfile {
        val done = !isCompleted(week, day)
        var next = setDayCompleted(week, day, done)
        // Возместил пропущенное — задержка снимается сама.
        if (done) next = next.copy(skipped = next.skipped.filterNot {
            it.week == week && it.day == day && it.isPeaking == isPeaking
        })
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
            deadlift = weight("Становая тяга"),
            actualSquat = actualWeight(week, day, listOf("Приседания", "Приседания со штангой")),
            actualBench = actualWeight(week, day, listOf("Жим лёжа")),
            actualDeadlift = actualWeight(week, day, listOf("Становая тяга"))
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
        programKind.hasBenchWave && (day == 1 || day == 3)

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

    val trainingDayCount: Int
        get() = when (programKind) {
            TrainingProgramKind.TEXAS, TrainingProgramKind.FULL_BODY,
            TrainingProgramKind.PRO_TEXAS, TrainingProgramKind.EUTHANASIA -> 3
            TrainingProgramKind.UPPER_LOWER -> 4
            TrainingProgramKind.CUSTOM -> maxOf(customProgram?.days?.size ?: 3, 1)
        }

    /// «Верх / Низ» — понедельник, вторник, четверг, пятница. Техас — понедельник, среда, пятница.
    val defaultWeekdays: List<Int>
        get() = when (trainingDayCount) {
            1 -> listOf(2)
            2 -> listOf(2, 5)
            3 -> listOf(2, 4, 6)
            4 -> listOf(2, 3, 5, 6)
            5 -> listOf(2, 3, 4, 5, 6)
            6 -> listOf(2, 3, 4, 5, 6, 7)
            else -> RuDate.weekOrder.take(maxOf(trainingDayCount, 1))
        }

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

        /// Фулбади считается от 5ПМ, как и техас: прогрессия там та же линейная.
        fun fullBody(input: ProgramInput, level: FullBodyLevel, name: String = "Профиль") =
            texas(input, name).copy(programKind = TrainingProgramKind.FULL_BODY, fullBodyLevel = level)

        /// Продвинутый техас считается от того же 5ПМ, что и классический.
        fun proTexas(input: ProgramInput, name: String = "Профиль") =
            texas(input, name).copy(programKind = TrainingProgramKind.PRO_TEXAS)

        /// «Эвтаназия»: максимумы каждого движения лежат внутри её собственного входа.
        fun euthanasia(input: EuthanasiaInput, name: String = "Профиль") = ProgramProfile(
            name = name,
            programKind = TrainingProgramKind.EUTHANASIA,
            squat5RM = input.squat.oneRepMax,
            bench5RM = input.press.oneRepMax,
            deadlift5RM = input.pull.oneRepMax,
            euthanasiaInput = input
        )

        /// Своя программа: максимумы ей не нужны, веса заданы прямо в упражнениях.
        fun custom(program: CustomProgram) = ProgramProfile(
            name = program.name.ifBlank { "Профиль" },
            programKind = TrainingProgramKind.CUSTOM,
            customProgram = program
        )
    }
}
