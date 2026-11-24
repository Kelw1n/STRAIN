package com.texasprogram.app.service

import com.texasprogram.app.model.discardFreeWorkout
import com.texasprogram.app.model.replaceFreeExercise
import com.texasprogram.app.model.appendFreeExercises
import com.texasprogram.app.model.startWorkout
import com.texasprogram.app.model.setFreeCompleted
import com.texasprogram.app.model.LibraryExercise
import com.texasprogram.app.model.WorkoutTemplate
import com.texasprogram.app.model.TemplateExercise
import com.texasprogram.app.model.dayPlan
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.texasprogram.app.model.ExercisePrescription
import com.texasprogram.app.model.LoadPrescription
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.undoSwaps
import com.texasprogram.app.model.swapExercise
import com.texasprogram.app.model.isProgramLift
import com.texasprogram.app.model.SwapUndo
import com.texasprogram.app.model.SwapScope
import com.texasprogram.app.model.ExerciseSwap
import com.texasprogram.app.model.Progression
import com.texasprogram.app.model.SetEntry
import com.texasprogram.app.model.SocialLive
import com.texasprogram.app.model.WarmUp
import com.texasprogram.app.model.formatWeight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong

// MARK: - Состояние сессии

/// Подход в тренировке. Неизменяемый: правка — это новый экземпляр в списке,
/// и Compose видит её сразу. Раньше подходы лежали в обычном MutableList,
/// и отметка не перерисовывала экран, пока его не дёргало что-то ещё.
@Immutable
data class ActiveSetState(
    val id: Long = WorkoutSessionManager.nextId(),
    /// Номер подхода в записях. После удаления подхода номера не сдвигаются:
    /// по ним лежат уже записанные факты.
    val index: Int,
    /// Что было в прошлый раз в этом подходе — его можно подставить одним нажатием.
    val previous: SetEntry?,
    val weight: Double,
    val reps: Int,
    val isDone: Boolean,
    /// Подход побил лучший вес упражнения до этой тренировки.
    val isRecord: Boolean = false,
    val countOnly: Boolean = false
) {
    val previousText: String
        get() = previous?.let { "${formatWeight(it.weight)}×${it.reps}" } ?: "—"
}

@Immutable
data class ActiveExerciseState(
    /// Ключ строки списка. Не имя: одно упражнение может стоять в дне дважды,
    /// а одинаковые ключи в LazyColumn роняют приложение.
    val id: Long = WorkoutSessionManager.nextId(),
    val prescription: ExercisePrescription,
    val restSeconds: Int,
    val sets: List<ActiveSetState>,
    /// Лучший вес до этой тренировки — от него считается рекорд.
    val bestBefore: Double = 0.0,
    val completedWarmUp: Set<WarmUp.Step> = emptySet()
) {
    val key: String get() = prescription.key
    val doneCount: Int get() = sets.count { it.isDone }
    val warmUpSteps: List<WarmUp.Step>
        get() = WarmUp.steps(prescription.name, sets.maxOfOrNull { it.weight } ?: 0.0)

    fun beatsRecord(set: ActiveSetState): Boolean =
        bestBefore > 0 && set.weight > bestBefore && set.reps > 0
}

@Immutable
data class RecordNotice(val id: Long, val exercise: String, val weight: Double)

// MARK: - Сохранение на диск

@Serializable
private data class SavedSet(
    val id: Long,
    val index: Int,
    val previous: SetEntry? = null,
    val weight: Double,
    val reps: Int,
    val isDone: Boolean,
    val isRecord: Boolean = false,
    val countOnly: Boolean = false
)

@Serializable
private data class SavedExercise(
    val key: String,
    val restSeconds: Int,
    val bestBefore: Double,
    val sets: List<SavedSet>,
    val completedWarmUp: Set<WarmUp.Step> = emptySet(),
    val prescription: ExercisePrescription? = null
)

@Serializable
private data class SavedSession(
    val profileId: String,
    val week: Int,
    val day: Int,
    val benchSession: Int? = null,
    val startMillis: Long,
    val note: String,
    val exercises: List<SavedExercise>,
    val publishToFeed: Boolean = true,
    val recipients: Set<String> = emptySet(),
    val snapshotProgress: Map<String, Int> = emptyMap(),
    val snapshotLog: Map<String, SetEntry> = emptyMap(),
    /// Замены упражнений в этой тренировке: отмена тренировки их откатывает.
    val swapUndo: SwapUndo = SwapUndo()
)

// MARK: - Менеджер

object WorkoutSessionManager {
    private val ids = AtomicLong(1)
    fun nextId(): Long = ids.getAndIncrement()

    var activeWorkout by mutableStateOf<ScheduledWorkout?>(null)
        private set
    var isPresented by mutableStateOf(false)
    val isRunning: Boolean get() = activeWorkout != null

    /// Время старта. Длительность считается от него, а не тиками: тики
    /// отстают, когда система придерживает приложение в фоне.
    var startMillis by mutableLongStateOf(0L)
        private set
    val exercises = mutableStateListOf<ActiveExerciseState>()

    var publishToFeed by mutableStateOf(true)
        private set
    var recipients by mutableStateOf(emptySet<String>())
        private set

    fun updateSharing(publish: Boolean, selected: Set<String>) {
        publishToFeed = publish
        recipients = selected
        writeNow()
    }

    var noteText by mutableStateOf("")
    var showingZeroSetsAlert by mutableStateOf(false)
    var showingSummary by mutableStateOf(false)
    var showingNoteSheet by mutableStateOf(false)
    var customRestTargetExerciseIndex by mutableStateOf<Int?>(null)

    /// Тренировка, которую попросили начать, пока идёт другая.
    var pendingWorkout by mutableStateOf<ScheduledWorkout?>(null)
    /// Последний побитый рекорд — экран показывает плашку и гасит её сам.
    var lastRecord by mutableStateOf<RecordNotice?>(null)
    /// Упражнение, чью картинку сейчас меняют.
    var artTarget by mutableStateOf<String?>(null)
    /// Номер упражнения, которое меняют на другое: тренажёр занят.
    var swapTarget by mutableStateOf<Int?>(null)
    /// Правки дня, которые сделали замены: отмена тренировки их откатывает.
    private var swapUndo = SwapUndo()
    /// Подходы шаблона, по которому сейчас начинается тренировка: вес, повторы и отдых.
    private var templateRows: Map<String, TemplateExercise> = emptyMap()

    /// Отметки дня на момент старта: отмена возвращает к ним, а не стирает день.
    private var snapshotProgress: Map<String, Int> = emptyMap()
    private var snapshotLog: Map<String, SetEntry> = emptyMap()
    private var profileId: String? = null

    private var restTimer: RestTimer? = null
    private var storeDirectory: File? = null
    private val storageLock = Any()
    private var writeGeneration = 0L
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null

    /// Где лежит сохранённая тренировка. Вызывается один раз при запуске.
    fun attach(dir: File, timer: RestTimer? = null) {
        restTimer = timer
        cancelSave()
        storeDirectory = dir
    }

    // MARK: Время и сводка

    /// Не состояние: секундомер на экране тикает сам (`SessionClock`), и экран
    /// тренировки больше не перерисовывается целиком каждую секунду.
    fun elapsedSeconds(now: Long = System.currentTimeMillis()): Int =
        if (activeWorkout == null || startMillis <= 0) 0 else ((now - startMillis) / 1000).toInt().coerceAtLeast(0)

    val elapsedSeconds: Int get() = elapsedSeconds()

    val totalCompletedSets: Int get() = exercises.sumOf { it.doneCount }
    val totalSetsCount: Int get() = exercises.sumOf { it.sets.size }
    /// Что видят друзья: день, текущее упражнение и его подходы — «Присед · 3/5».
    val liveStatus: SocialLive?
        get() {
            val workout = activeWorkout ?: return null
            val current = exercises.firstOrNull { exercise -> exercise.sets.any { !it.isDone } } ?: exercises.lastOrNull()
            return SocialLive(
                title = workout.day.title,
                exercise = current?.prescription?.name.orEmpty(),
                done = current?.doneCount ?: 0,
                total = current?.sets?.size ?: 0,
                setsDone = totalCompletedSets,
                setsTotal = totalSetsCount,
                startedAt = startMillis.toDouble()
            )
        }
    val totalTonnage: Double
        get() = exercises.sumOf { ex -> ex.sets.filter { it.isDone && !it.countOnly }.sumOf { it.weight * it.reps } }
    val density: Double
        get() = totalTonnage / maxOf(1.0, elapsedSeconds / 60.0)

    // MARK: Старт и замена

    /// Вход для кнопок «Начать». Идёт другая тренировка — не подменяем молча, спрашиваем.
    fun requestStart(workout: ScheduledWorkout, profile: ProgramProfile) {
        val current = activeWorkout
        if (current != null && current.id != workout.id) {
            pendingWorkout = workout
            return
        }
        startWorkout(workout, profile)
    }

    /// Новую тренировку не начали. Свободная уже заведена в профиле — убираем её.
    fun cancelReplace(profile: ProgramProfile? = null, onUpdateProfile: ((ProgramProfile) -> Unit)? = null) {
        val pending = pendingWorkout
        if (pending != null && pending.week == ProgramProfile.FREE_WEEK && profile != null && onUpdateProfile != null) {
            onUpdateProfile(profile.discardFreeWorkout(pending.day.number))
        }
        pendingWorkout = null
        templateRows = emptyMap()
        expand()
    }

    /// Тренировка по шаблону — своему, из каталога или сообщества. Программа не двигается.
    fun startTemplate(template: WorkoutTemplate, profile: ProgramProfile, onUpdateProfile: (ProgramProfile) -> Unit) {
        val (next, workout) = profile.startWorkout(template)
        onUpdateProfile(next)
        templateRows = template.exercises.associateBy { it.id }
        requestStart(workout, next)
    }

    /// Упражнения из библиотеки — в идущую свободную тренировку.
    fun addExercises(items: List<LibraryExercise>, profile: ProgramProfile, onUpdateProfile: (ProgramProfile) -> Unit) {
        val workout = activeWorkout ?: return
        if (workout.week != ProgramProfile.FREE_WEEK || items.isEmpty()) return
        val added = items.map { TemplateExercise(name = it.name, restSeconds = profile.defaultRestSeconds) }
        val next = profile.appendFreeExercises(workout.day.number, added.map { it.prescription })
        onUpdateProfile(next)
        added.forEach { row -> exercises.add(apply(row, buildExercise(row.prescription, next, workout))) }
        save()
    }

    /// Строки шаблона поверх расчёта: свой вес и повторы в каждом подходе, свой отдых.
    /// Пустой вес — из прошлого раза, а если упражнение делается впервые — ноль.
    private fun apply(row: TemplateExercise, state: ActiveExerciseState): ActiveExerciseState {
        val fresh = state.sets.all { it.previous == null } && state.bestBefore == 0.0
        val sets = state.sets.mapIndexed { index, set ->
            val planned = row.sets.getOrNull(index)
            if (set.isDone || planned == null) set
            else set.copy(
                weight = when {
                    planned.weight > 0 -> planned.weight
                    fresh -> 0.0
                    else -> set.weight
                },
                reps = planned.reps
            )
        }
        return state.copy(restSeconds = row.restSeconds, sets = sets)
    }

    fun confirmReplace(profile: ProgramProfile, onUpdateProfile: (ProgramProfile) -> Unit) {
        val next = pendingWorkout ?: return
        pendingWorkout = null
        val restored = discardWorkout(profile, onUpdateProfile)
        startWorkout(next, restored)
    }

    fun startWorkout(workout: ScheduledWorkout, profile: ProgramProfile, now: Long = System.currentTimeMillis()) {
        if (activeWorkout?.id == workout.id) {
            expand()
            return
        }
        // Тренируешься сегодня — в заголовке сегодняшний день недели, даже если по
        // расписанию тренировка стояла на другой: «ПЯТНИЦА» в среду выглядела ошибкой.
        val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        activeWorkout = workout.copy(date = today, weekday = RuDate.weekdayOf(today))
        profileId = profile.id
        startMillis = now
        publishToFeed = true
        recipients = emptySet()
        noteText = profile.note(workout.week, workout.day.number)
        showingZeroSetsAlert = false
        showingSummary = false
        showingNoteSheet = false
        customRestTargetExerciseIndex = null
        lastRecord = null
        swapTarget = null
        swapUndo = SwapUndo()

        val prefix = profile.dayKey(workout.week, workout.day.number) + "|"
        snapshotProgress = profile.setProgress.filterKeys { it.startsWith(prefix) }
        snapshotLog = profile.setLog.filterKeys { it.startsWith(prefix) }

        exercises.clear()
        exercises.addAll(profile.exercises(workout).map { presc ->
            val state = buildExercise(presc, profile, workout)
            templateRows[presc.key]?.let { apply(it, state) } ?: state
        })
        templateRows = emptyMap()
        save()
        isPresented = true
    }

    private fun buildExercise(
        presc: ExercisePrescription,
        profile: ProgramProfile,
        workout: ScheduledWorkout,
        weight: Double? = null,
        repsOverride: Int? = null,
        countOverride: Int? = null,
        rest: Int? = null
    ): ActiveExerciseState {
        // История — один проход на упражнение: раньше её пересобирали по всему
        // плану для каждого подхода и ещё раз для веса.
        val history = profile.history(presc.name)
            .filterNot { it.week == workout.week && it.day == workout.day.number }
        val last = history.lastOrNull { it.entries.isNotEmpty() }?.entries.orEmpty()
        val best = history.maxOfOrNull { it.best } ?: 0.0
        val planned = weight ?: plannedWeight(presc, profile, workout, last)
        val reps = repsOverride ?: presc.plannedReps ?: 10
        val waveSets = presc.benchSession?.let { session ->
            profile.benchWave.firstOrNull { it.id == session }?.sets
        }.orEmpty()
        // Подходы этого дня, уже записанные до старта (точками на «Главном»
        // или в прошлой сессии), приходят отмеченными. Раньше они показывались
        // пустыми, и казалось, что сделанное пропало.
        val logged = profile.indexedEntries(workout.week, workout.day.number, presc)
        val plannedIndices = 0 until maxOf(countOverride ?: presc.sets, 1)
        val indices = (plannedIndices.toSet() + logged.keys).sorted()
        val progress = profile.completedSets(workout.week, workout.day.number, presc)
        val dots = plannedIndices.filterNot { it in logged }.take((progress - logged.size).coerceAtLeast(0)).toSet()
        val sets = indices.map { index ->
            val entry = logged[index]
            val waveSet = waveSets.getOrNull(index)
            // В тесте на максимум число повторов заранее неизвестно.
            val waveReps = waveSet?.let { it.reps.toIntOrNull() ?: 0 }
            ActiveSetState(
                index = index,
                previous = last.getOrNull(index) ?: last.firstOrNull(),
                weight = entry?.weight ?: weight ?: waveSet?.weight ?: planned,
                reps = entry?.reps ?: repsOverride ?: waveReps ?: reps,
                isDone = entry != null || index in dots,
                countOnly = index in dots
            )
        }
        return ActiveExerciseState(
            prescription = presc,
            restSeconds = rest ?: if (presc.benchSession != null) 180 else profile.defaultRestSeconds,
            sets = sets,
            bestBefore = best
        )
    }

    /// Тренажёр занят — меняем упражнение на похожее прямо в тренировке.
    /// Отмеченные подходы старого упражнения снимаются: их место займёт новое.
    fun swapExercise(
        exIdx: Int,
        suggestion: ExerciseSwap.Suggestion,
        scope: SwapScope,
        profile: ProgramProfile,
        onUpdateProfile: (ProgramProfile) -> Unit
    ) {
        val workout = activeWorkout ?: return
        val old = exercises.getOrNull(exIdx) ?: return
        val name = suggestion.name.trim()
        if (name.isEmpty() || ProgramProfile.weightStepKey(name) == ProgramProfile.weightStepKey(old.prescription.name)) return
        var next = profile
        for (set in old.sets) {
            if (set.isDone) next = next.clearSetEntry(workout.week, workout.day.number, old.prescription, set.index)
        }
        next = next.updateCompletedSets(workout.week, workout.day.number, old.prescription, 0)
        // В свободной тренировке меняем прямо в её дне: правок программы нет.
        if (workout.week == ProgramProfile.FREE_WEEK) {
            val (updated, fresh) = next.replaceFreeExercise(workout.day.number, old.prescription, name)
            onUpdateProfile(updated)
            exercises[exIdx] = buildExercise(
                fresh,
                updated,
                workout,
                weight = suggestion.weight ?: old.sets.firstOrNull { !it.isDone }?.weight ?: old.sets.firstOrNull()?.weight,
                repsOverride = suggestion.reps ?: old.sets.firstOrNull()?.reps,
                countOverride = old.sets.size,
                rest = old.restSeconds
            )
            swapTarget = null
            save()
            return
        }
        val result = next.swapExercise(
            old.prescription,
            name,
            workout.week,
            workout.day.number,
            if (next.isProgramLift(old.prescription)) SwapScope.TODAY else scope,
            swapUndo
        )
        swapUndo = result.undo
        onUpdateProfile(result.profile)
        exercises[exIdx] = buildExercise(
            result.prescription,
            result.profile,
            workout,
            weight = suggestion.weight ?: old.sets.firstOrNull { !it.isDone }?.weight ?: old.sets.firstOrNull()?.weight,
            repsOverride = suggestion.reps ?: old.sets.firstOrNull()?.reps,
            countOverride = old.sets.size,
            rest = old.restSeconds
        )
        swapTarget = null
        save()
    }

    private fun plannedWeight(
        presc: ExercisePrescription,
        profile: ProgramProfile,
        workout: ScheduledWorkout,
        last: List<SetEntry>? = null
    ): Double {
        (presc.load as? LoadPrescription.Kilograms)?.let { return it.value }
        profile.accessoryHint(presc, workout.week, workout.day.number)?.weight?.let { return it }
        val previous = last ?: profile.history(presc.name)
            .filterNot { it.week == workout.week && it.day == workout.day.number }
            .lastOrNull { it.entries.isNotEmpty() }?.entries.orEmpty()
        return previous.maxOfOrNull { it.weight } ?: 50.0
    }

    /// Меняет шаг прибавки упражнения прямо в тренировке. Неотмеченные подходы
    /// с плановым весом сразу получают вес под новый шаг; то, что человек
    /// вписал руками или уже сделал, не трогаем.
    fun changeWeightStep(step: Double, exIdx: Int, profile: ProgramProfile, onUpdateProfile: (ProgramProfile) -> Unit) {
        val workout = activeWorkout ?: return
        val target = exercises.getOrNull(exIdx) ?: return
        val key = ProgramProfile.weightStepKey(target.prescription.name)
        // Одно движение может стоять в дне дважды — шаг у него один.
        val affected = exercises.indices.filter { ProgramProfile.weightStepKey(exercises[it].prescription.name) == key }
        val before = affected.map { plannedWeight(exercises[it].prescription, profile, workout) }

        val updated = profile.setWeightStep(step, target.prescription.name)
        onUpdateProfile(updated)

        val fresh = updated.exercises(workout)
        affected.forEachIndexed { offset, idx ->
            val ex = exercises[idx]
            val presc = fresh.firstOrNull { it.key == ex.key } ?: ex.prescription
            val after = plannedWeight(presc, updated, workout)
            exercises[idx] = ex.copy(
                prescription = presc,
                sets = if (after == before[offset]) ex.sets else ex.sets.map {
                    if (!it.isDone && it.weight == before[offset]) it.copy(weight = after) else it
                }
            )
        }
        save()
    }

    /// Сменили профиль посреди тренировки: она принадлежит прежнему, и подходы
    /// не должны писаться в чужой. Кладём её на диск и убираем из памяти —
    /// вернёшься в тот профиль, и она поднимется.
    fun detachIfOtherProfile(profile: ProgramProfile) {
        val owner = profileId ?: return
        if (activeWorkout == null || owner == profile.id) return
        writeNow()
        restTimer?.takeIf { it.isRunning }?.stop()
        saveJob?.cancel()
        activeWorkout = null
        isPresented = false
        showingSummary = false
        showingZeroSetsAlert = false
        pendingWorkout = null
        this.profileId = null
        exercises.clear()
    }

    fun minimize() { isPresented = false }
    fun expand() { if (activeWorkout != null) isPresented = true }

    // MARK: Завершение и отмена

    fun finishWorkout(
        profile: ProgramProfile,
        customDuration: Int? = null,
        effort: String? = null,
        onUpdateProfile: (ProgramProfile) -> Unit
    ) {
        val workout = activeWorkout ?: return
        val xpBefore = Progression.xp(profile)
        var next = profile.setNote(noteText, workout.week, workout.day.number)
        for (ex in exercises) {
            for (set in ex.sets) {
                if (set.isDone && !set.countOnly) {
                    next = next.recordSet(workout.week, workout.day.number, ex.prescription, set.index, set.reps, set.weight, completedCount = ex.doneCount)
                }
            }
        }
        val duration = customDuration ?: elapsedSeconds
        if (next.isCompleted(workout.week, workout.day.number)) {
            next = next.withWorkoutSummary(workout.week, workout.day.number, duration, effort)
        } else if (workout.week == ProgramProfile.FREE_WEEK) {
            next = next.setFreeCompleted(workout.day.number, true, Instant.ofEpochMilli(startMillis), duration, effort)
        } else {
            next = next.toggleCompleted(
                week = workout.week,
                day = workout.day.number,
                today = workout.date,
                scheduledBench = workout.benchSession,
                durationSeconds = duration,
                effort = effort
            )
        }
        onUpdateProfile(next)
        reset()
        ProgressionEvents.celebrate(xpBefore, Progression.xp(next))
    }

    /// Отмена возвращает день к отметкам на момент старта. Возвращает профиль
    /// после отмены — замена тренировки начинает новую уже от него.
    fun discardWorkout(profile: ProgramProfile, onUpdateProfile: (ProgramProfile) -> Unit): ProgramProfile {
        val workout = activeWorkout ?: return profile
        // Замены упражнений — тоже назад: тренировки не было.
        val base = profile.undoSwaps(swapUndo)
        val snapshot = base.copy(setProgress = snapshotProgress, setLog = snapshotLog)
        var restored = base.restoreDay(snapshot, workout.week, workout.day.number)
        // Незаконченная свободная тренировка исчезает целиком.
        if (workout.week == ProgramProfile.FREE_WEEK) restored = restored.discardFreeWorkout(workout.day.number)
        onUpdateProfile(restored)
        reset()
        return restored
    }

    private fun reset() {
        restTimer?.takeIf { it.isRunning }?.stop()
        cancelSave()
        profileId?.let(::clearSavedFor)
        activeWorkout = null
        isPresented = false
        showingZeroSetsAlert = false
        showingSummary = false
        lastRecord = null
        profileId = null
        snapshotProgress = emptyMap()
        snapshotLog = emptyMap()
        swapUndo = SwapUndo()
        swapTarget = null
        exercises.clear()
    }

    // MARK: Подходы

    fun toggleWarmUp(step: WarmUp.Step, exerciseId: Long): Boolean {
        if (activeWorkout == null) return false
        val index = exercises.indexOfFirst { it.id == exerciseId }
        val exercise = exercises.getOrNull(index) ?: return false
        if (step !in exercise.warmUpSteps) return false
        val done = exercise.completedWarmUp
        exercises[index] = exercise.copy(completedWarmUp = if (step in done) done - step else done + step)
        if (writeNow()) return true
        exercises[index] = exercise
        save()
        return false
    }

    private inline fun updateSet(exIdx: Int, sIdx: Int, change: (ActiveSetState) -> ActiveSetState): ActiveSetState? {
        val ex = exercises.getOrNull(exIdx) ?: return null
        val set = ex.sets.getOrNull(sIdx) ?: return null
        val updated = change(set)
        exercises[exIdx] = ex.copy(sets = ex.sets.toMutableList().also { it[sIdx] = updated })
        return updated
    }

    fun toggleSetDone(
        exIdx: Int,
        sIdx: Int,
        profile: ProgramProfile,
        restTimer: RestTimer?,
        onUpdateProfile: (ProgramProfile) -> Unit
    ) {
        val workout = activeWorkout ?: return
        val ex = exercises.getOrNull(exIdx) ?: return
        val set = ex.sets.getOrNull(sIdx) ?: return

        if (!set.isDone) {
            // Рекорд — только против прошлых тренировок: первый раз в жизни
            // сделанное упражнение рекордом не считается.
            val isRecord = ex.beatsRecord(set)
            val done = updateSet(exIdx, sIdx) { it.copy(isDone = true, countOnly = false, isRecord = isRecord) } ?: return
            val updatedEx = exercises[exIdx]
            // Отмеченный подход сохраняем сразу: закрытое приложение не теряет тренировку.
            onUpdateProfile(profile.recordSet(
                workout.week, workout.day.number, ex.prescription, done.index, done.reps, done.weight,
                completedCount = updatedEx.doneCount
            ))
            if (isRecord) lastRecord = RecordNotice(nextId(), ex.prescription.name, done.weight)
            restTimer?.start(
                seconds = ex.restSeconds.toLong(),
                exerciseName = ex.prescription.name,
                setDetails = "${formatWeight(done.weight)} кг × ${done.reps}",
                completedSets = updatedEx.doneCount,
                totalSets = updatedEx.sets.size
            )
        } else {
            updateSet(exIdx, sIdx) { it.copy(isDone = false, isRecord = false) }
            val cleared = profile.clearSetEntry(workout.week, workout.day.number, ex.prescription, set.index)
            onUpdateProfile(cleared.updateCompletedSets(workout.week, workout.day.number, ex.prescription, exercises[exIdx].doneCount))
        }
        save()
    }

    fun addSet(exIdx: Int) {
        if (activeWorkout == null) return
        val ex = exercises.getOrNull(exIdx) ?: return
        // Следующий номер — после самого большого: после удаления подхода
        // «количество» совпало бы с номером ещё живого подхода.
        val highest = ex.sets.maxOfOrNull { it.index } ?: -1
        val index = if (highest < Int.MAX_VALUE) highest + 1 else (0..ex.sets.size).first { candidate -> ex.sets.none { it.index == candidate } }
        val last = ex.sets.lastOrNull()
        exercises[exIdx] = ex.copy(sets = ex.sets + ActiveSetState(
            index = index,
            previous = last?.previous,
            weight = last?.weight ?: 50.0,
            reps = last?.reps ?: 10,
            isDone = false
        ))
        save()
    }

    /// Удаляет подход. Последний оставшийся не удаляется — у упражнения должна быть строка.
    fun deleteSet(exIdx: Int, sIdx: Int, profile: ProgramProfile, onUpdateProfile: (ProgramProfile) -> Unit) {
        val workout = activeWorkout ?: return
        val ex = exercises.getOrNull(exIdx) ?: return
        if (ex.sets.size <= 1 || sIdx !in ex.sets.indices) return
        val removed = ex.sets[sIdx]
        val rest = ex.sets.filterIndexed { i, _ -> i != sIdx }
        exercises[exIdx] = ex.copy(sets = rest)
        if (removed.isDone) {
            val cleared = profile.clearSetEntry(workout.week, workout.day.number, ex.prescription, removed.index)
            onUpdateProfile(cleared.updateCompletedSets(workout.week, workout.day.number, ex.prescription, rest.count { it.isDone }))
        }
        save()
    }

    /// Подставляет в подход то, что было в прошлый раз.
    fun applyPrevious(exIdx: Int, sIdx: Int, profile: ProgramProfile, onUpdateProfile: (ProgramProfile) -> Unit) {
        val previous = exercises.getOrNull(exIdx)?.sets?.getOrNull(sIdx)?.previous ?: return
        val set = updateSet(exIdx, sIdx) { it.copy(weight = previous.weight, reps = previous.reps) } ?: return
        rerecordIfDone(exIdx, sIdx, set, profile, onUpdateProfile)
        save()
    }

    /// Вес из поля ввода. Отмеченный подход переписываем сразу,
    /// иначе до «Завершить» в истории висел бы старый вес.
    fun setWeight(value: Double, exIdx: Int, sIdx: Int, profile: ProgramProfile, onUpdateProfile: (ProgramProfile) -> Unit) {
        val set = updateSet(exIdx, sIdx) { it.copy(weight = value.coerceIn(0.0, 1000.0)) } ?: return
        rerecordIfDone(exIdx, sIdx, set, profile, onUpdateProfile)
        save()
    }

    fun setReps(value: Int, exIdx: Int, sIdx: Int, profile: ProgramProfile, onUpdateProfile: (ProgramProfile) -> Unit) {
        val set = updateSet(exIdx, sIdx) { it.copy(reps = value.coerceIn(0, 999)) } ?: return
        rerecordIfDone(exIdx, sIdx, set, profile, onUpdateProfile)
        save()
    }

    private fun rerecordIfDone(exIdx: Int, sIdx: Int, set: ActiveSetState, profile: ProgramProfile, onUpdateProfile: (ProgramProfile) -> Unit) {
        val workout = activeWorkout ?: return
        if (!set.isDone) return
        val ex = exercises[exIdx]
        updateSet(exIdx, sIdx) { it.copy(countOnly = false, isRecord = ex.beatsRecord(it)) }
        onUpdateProfile(profile.recordSet(
            workout.week, workout.day.number, ex.prescription, set.index, set.reps, set.weight,
            completedCount = ex.doneCount
        ))
    }

    /// Переносит упражнение на другое место. Порядок меняется только в этой тренировке.
    fun moveExercise(from: Int, to: Int) {
        if (from !in exercises.indices || to !in exercises.indices || from == to) return
        val item = exercises.removeAt(from)
        exercises.add(to, item)
        save()
    }

    fun setRestDuration(seconds: Int) {
        val idx = customRestTargetExerciseIndex ?: return
        exercises.getOrNull(idx)?.let { exercises[idx] = it.copy(restSeconds = seconds) }
        customRestTargetExerciseIndex = null
        save()
    }

    fun updateNote(text: String) {
        noteText = text
        save()
    }

    // MARK: Сохранение

    /// Запись откладывается на мгновение и идёт в фоне: ввод веса меняет
    /// состояние на каждую цифру, писать файл на каждую незачем.
    private fun save() {
        val session = currentSession() ?: return
        val file = sessionFile(session.profileId) ?: return
        val generation = cancelSave()
        saveJob = ioScope.launch {
            delay(200)
            write(file, session, generation)
        }
    }

    /// Немедленная запись — при уходе приложения в фон.
    fun writeNow(): Boolean {
        val session = currentSession() ?: return false
        val file = sessionFile(session.profileId) ?: return false
        return write(file, session, cancelSave())
    }

    private fun cancelSave(): Long {
        saveJob?.cancel()
        return synchronized(storageLock) { ++writeGeneration }
    }

    private fun sessionsDirectory(): File? = storeDirectory?.let { File(it, "active-workouts") }

    private fun sessionFile(id: String): File? {
        // Профиль из резервной копии может иметь произвольный строковый ID.
        val filename = Base64.getUrlEncoder().withoutPadding().encodeToString(id.toByteArray(Charsets.UTF_8))
        return sessionsDirectory()?.let { File(it, "$filename.json") }
    }

    private fun legacyFile(): File? = storeDirectory?.let { File(it, "active-workout.json") }

    private fun readSession(file: File?): SavedSession? = file?.let {
        runCatching { json.decodeFromString(SavedSession.serializer(), it.readText()) }.getOrNull()
    }

    private fun write(file: File, session: SavedSession, generation: Long): Boolean {
        val data = runCatching { json.encodeToString(SavedSession.serializer(), session) }.getOrNull() ?: return false
        return synchronized(storageLock) {
            if (generation != writeGeneration) return false
            val tmp = File(file.parentFile, file.name + ".tmp")
            try {
                file.parentFile?.mkdirs()
                tmp.writeText(data)
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                true
            } catch (_: java.io.IOException) {
                tmp.delete()
                false
            }
        }
    }

    private fun currentSession(): SavedSession? {
        val workout = activeWorkout ?: return null
        val owner = profileId ?: return null
        return SavedSession(
            profileId = owner,
            week = workout.week,
            day = workout.day.number,
            benchSession = workout.benchSession,
            startMillis = startMillis,
            note = noteText,
            publishToFeed = publishToFeed,
            recipients = recipients,
            exercises = exercises.map { ex ->
                SavedExercise(
                    key = ex.key,
                    restSeconds = ex.restSeconds,
                    bestBefore = ex.bestBefore,
                    sets = ex.sets.map { SavedSet(it.id, it.index, it.previous, it.weight, it.reps, it.isDone, it.isRecord, it.countOnly) },
                    completedWarmUp = ex.completedWarmUp,
                    prescription = ex.prescription
                )
            },
            snapshotProgress = snapshotProgress,
            snapshotLog = snapshotLog,
            swapUndo = swapUndo
        )
    }

    private fun clearSavedFor(id: String) = synchronized(storageLock) {
        sessionFile(id)?.delete()
        val legacy = legacyFile()
        if (readSession(legacy)?.profileId == id) legacy?.delete()
    }

    /// Очистка хранилища между тестами.
    fun clearSaved() {
        cancelSave()
        synchronized(storageLock) {
            sessionsDirectory()?.deleteRecursively()
            legacyFile()?.delete()
        }
    }

    /// Профиль удалён: его тренировка — и идущая, и отложенная при смене
    /// профиля — больше никогда не продолжится.
    fun forgetProfile(id: String) {
        if (activeWorkout != null && profileId == id) {
            reset()
            return
        }
        clearSavedFor(id)
    }

    /// Для тестов: забыть тренировку в памяти, не трогая файл, — как после выгрузки.
    fun forgetForTesting() {
        cancelSave()
        activeWorkout = null
        isPresented = false
        profileId = null
        exercises.clear()
    }

    /// Поднимает сохранённую тренировку этого профиля — свёрнутой, с плашкой
    /// «продолжить». Начатую больше суток назад не поднимаем: её забыли.
    fun restoreIfNeeded(profile: ProgramProfile, now: Long = System.currentTimeMillis()) {
        if (activeWorkout != null) return
        val session = synchronized(storageLock) {
            readSession(sessionFile(profile.id)) ?: readSession(legacyFile())
        } ?: return
        if (session.profileId != profile.id) return
        if (now - session.startMillis > 24 * 3600 * 1000L) {
            clearSavedFor(profile.id)
            return
        }
        // День недели — того дня, когда тренировку начали, а не дня по расписанию.
        val startedOn = Instant.ofEpochMilli(session.startMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val workout = profile.schedule().allPending
            .firstOrNull { it.week == session.week && it.day.number == session.day }
            ?.copy(date = startedOn, weekday = RuDate.weekdayOf(startedOn), benchSession = session.benchSession)
            ?: run {
                val day = profile.dayPlan(session.week, session.day) ?: return
                ScheduledWorkout(
                    week = session.week,
                    day = day,
                    date = startedOn,
                    weekday = RuDate.weekdayOf(startedOn),
                    isCompleted = profile.isCompleted(session.week, session.day),
                    benchSession = session.benchSession
                )
            }
        val byKey = profile.exercises(workout).associateBy { it.key }
        val restored = session.exercises.mapNotNull { saved ->
            val presc = saved.prescription ?: byKey[saved.key] ?: return@mapNotNull null
            ActiveExerciseState(
                prescription = presc,
                restSeconds = saved.restSeconds,
                sets = saved.sets.map { ActiveSetState(nextId(), it.index, it.previous, it.weight, it.reps, it.isDone, it.isRecord, it.countOnly) },
                bestBefore = saved.bestBefore,
                completedWarmUp = saved.completedWarmUp
            )
        }
        if (restored.isEmpty() && session.exercises.isNotEmpty()) return
        activeWorkout = workout
        profileId = session.profileId
        startMillis = session.startMillis
        publishToFeed = session.publishToFeed
        recipients = session.recipients
        noteText = session.note
        snapshotProgress = session.snapshotProgress
        snapshotLog = session.snapshotLog
        swapUndo = session.swapUndo
        exercises.clear()
        exercises.addAll(restored)
        isPresented = false
    }
}
