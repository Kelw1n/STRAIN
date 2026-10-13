package com.texasprogram.app.service

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.texasprogram.app.model.ExercisePrescription
import com.texasprogram.app.model.LoadPrescription
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.SetEntry
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
import java.time.LocalDate
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
    val isRecord: Boolean = false
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
    val bestBefore: Double = 0.0
) {
    val key: String get() = prescription.key
    val doneCount: Int get() = sets.count { it.isDone }
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
    val isRecord: Boolean = false
)

@Serializable
private data class SavedExercise(
    val key: String,
    val restSeconds: Int,
    val bestBefore: Double,
    val sets: List<SavedSet>
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
    val snapshotProgress: Map<String, Int> = emptyMap(),
    val snapshotLog: Map<String, SetEntry> = emptyMap()
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

    /// Отметки дня на момент старта: отмена возвращает к ним, а не стирает день.
    private var snapshotProgress: Map<String, Int> = emptyMap()
    private var snapshotLog: Map<String, SetEntry> = emptyMap()
    private var profileId: String? = null

    private var storeFile: File? = null
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null

    /// Где лежит сохранённая тренировка. Вызывается один раз при запуске.
    fun attach(dir: File) {
        storeFile = File(dir, "active-workout.json")
    }

    // MARK: Время и сводка

    /// Не состояние: секундомер на экране тикает сам (`SessionClock`), и экран
    /// тренировки больше не перерисовывается целиком каждую секунду.
    fun elapsedSeconds(now: Long = System.currentTimeMillis()): Int =
        if (activeWorkout == null || startMillis <= 0) 0 else ((now - startMillis) / 1000).toInt().coerceAtLeast(0)

    val elapsedSeconds: Int get() = elapsedSeconds()

    val totalCompletedSets: Int get() = exercises.sumOf { it.doneCount }
    val totalSetsCount: Int get() = exercises.sumOf { it.sets.size }
    val totalTonnage: Double
        get() = exercises.sumOf { ex -> ex.sets.filter { it.isDone }.sumOf { it.weight * it.reps } }
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

    fun cancelReplace() {
        pendingWorkout = null
        expand()
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
        activeWorkout = workout
        profileId = profile.id
        startMillis = now
        noteText = profile.note(workout.week, workout.day.number)
        showingZeroSetsAlert = false
        showingSummary = false
        showingNoteSheet = false
        customRestTargetExerciseIndex = null
        lastRecord = null

        val prefix = profile.dayKey(workout.week, workout.day.number) + "|"
        snapshotProgress = profile.setProgress.filterKeys { it.startsWith(prefix) }
        snapshotLog = profile.setLog.filterKeys { it.startsWith(prefix) }

        exercises.clear()
        exercises.addAll(profile.exercises(workout).map { presc -> buildExercise(presc, profile, workout) })
        save()
        isPresented = true
    }

    private fun buildExercise(presc: ExercisePrescription, profile: ProgramProfile, workout: ScheduledWorkout): ActiveExerciseState {
        // История — один проход на упражнение: раньше её пересобирали по всему
        // плану для каждого подхода и ещё раз для веса.
        val history = profile.history(presc.name)
            .filterNot { it.week == workout.week && it.day == workout.day.number }
        val last = history.lastOrNull { it.entries.isNotEmpty() }?.entries.orEmpty()
        val best = history.maxOfOrNull { it.best } ?: 0.0
        val planned = plannedWeight(presc, profile, workout, last)
        val reps = presc.plannedReps ?: 10
        // Подходы этого дня, уже записанные до старта (точками на «Главном»
        // или в прошлой сессии), приходят отмеченными. Раньше они показывались
        // пустыми, и казалось, что сделанное пропало.
        val logged = (0 until maxOf(presc.sets, 0) + ProgramProfile.EXTRA_SET_ALLOWANCE)
            .mapNotNull { index -> profile.setEntry(workout.week, workout.day.number, presc, index)?.let { index to it } }
            .toMap()
        val count = maxOf(presc.sets, 1, (logged.keys.maxOrNull() ?: -1) + 1)
        // Сколько подходов в плане, столько и строк: два — значит два.
        val sets = (0 until count).map { index ->
            val entry = logged[index]
            ActiveSetState(
                index = index,
                previous = last.getOrNull(index) ?: last.firstOrNull(),
                weight = entry?.weight ?: planned,
                reps = entry?.reps ?: reps,
                isDone = entry != null
            )
        }
        return ActiveExerciseState(
            prescription = presc,
            restSeconds = if (presc.benchSession != null) 180 else profile.defaultRestSeconds,
            sets = sets,
            bestBefore = best
        )
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
        var next = profile
        for (ex in exercises) {
            for (set in ex.sets) {
                if (set.isDone) {
                    next = next.recordSet(workout.week, workout.day.number, ex.prescription, set.index, set.reps, set.weight)
                }
            }
        }
        if (!next.isCompleted(workout.week, workout.day.number)) {
            next = next.toggleCompleted(
                week = workout.week,
                day = workout.day.number,
                durationSeconds = customDuration ?: elapsedSeconds,
                effort = effort
            )
        }
        onUpdateProfile(next)
        reset()
    }

    /// Отмена возвращает день к отметкам на момент старта. Возвращает профиль
    /// после отмены — замена тренировки начинает новую уже от него.
    fun discardWorkout(profile: ProgramProfile, onUpdateProfile: (ProgramProfile) -> Unit): ProgramProfile {
        val workout = activeWorkout ?: return profile
        val snapshot = profile.copy(setProgress = snapshotProgress, setLog = snapshotLog)
        val restored = profile.restoreDay(snapshot, workout.week, workout.day.number)
        onUpdateProfile(restored)
        reset()
        return restored
    }

    private fun reset() {
        activeWorkout = null
        isPresented = false
        showingZeroSetsAlert = false
        showingSummary = false
        lastRecord = null
        profileId = null
        snapshotProgress = emptyMap()
        snapshotLog = emptyMap()
        exercises.clear()
        clearSaved()
    }

    // MARK: Подходы

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
            val isRecord = ex.bestBefore > 0 && set.weight > ex.bestBefore && set.reps > 0
            val done = updateSet(exIdx, sIdx) { it.copy(isDone = true, isRecord = isRecord) } ?: return
            // Отмеченный подход сохраняем сразу: закрытое приложение не теряет тренировку.
            onUpdateProfile(profile.recordSet(workout.week, workout.day.number, ex.prescription, done.index, done.reps, done.weight))
            if (isRecord) lastRecord = RecordNotice(nextId(), ex.prescription.name, done.weight)
            val updatedEx = exercises[exIdx]
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
        val index = (ex.sets.maxOfOrNull { it.index } ?: -1) + 1
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
        rerecordIfDone(exIdx, set, profile, onUpdateProfile)
        save()
    }

    /// Вес из поля ввода. Отмеченный подход переписываем сразу,
    /// иначе до «Завершить» в истории висел бы старый вес.
    fun setWeight(value: Double, exIdx: Int, sIdx: Int, profile: ProgramProfile, onUpdateProfile: (ProgramProfile) -> Unit) {
        val set = updateSet(exIdx, sIdx) { it.copy(weight = value.coerceIn(0.0, 1000.0)) } ?: return
        rerecordIfDone(exIdx, set, profile, onUpdateProfile)
        save()
    }

    fun setReps(value: Int, exIdx: Int, sIdx: Int, profile: ProgramProfile, onUpdateProfile: (ProgramProfile) -> Unit) {
        val set = updateSet(exIdx, sIdx) { it.copy(reps = value.coerceIn(0, 999)) } ?: return
        rerecordIfDone(exIdx, set, profile, onUpdateProfile)
        save()
    }

    private fun rerecordIfDone(exIdx: Int, set: ActiveSetState, profile: ProgramProfile, onUpdateProfile: (ProgramProfile) -> Unit) {
        val workout = activeWorkout ?: return
        if (!set.isDone) return
        val ex = exercises[exIdx]
        onUpdateProfile(profile.recordSet(workout.week, workout.day.number, ex.prescription, set.index, set.reps, set.weight))
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
        val file = storeFile ?: return
        saveJob?.cancel()
        saveJob = ioScope.launch {
            delay(200)
            write(file, session)
        }
    }

    /// Немедленная запись — при уходе приложения в фон.
    fun writeNow() {
        val session = currentSession() ?: return
        val file = storeFile ?: return
        saveJob?.cancel()
        write(file, session)
    }

    private fun write(file: File, session: SavedSession) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(SavedSession.serializer(), session))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
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
            exercises = exercises.map { ex ->
                SavedExercise(ex.key, ex.restSeconds, ex.bestBefore, ex.sets.map {
                    SavedSet(it.id, it.index, it.previous, it.weight, it.reps, it.isDone, it.isRecord)
                })
            },
            snapshotProgress = snapshotProgress,
            snapshotLog = snapshotLog
        )
    }

    fun clearSaved() {
        saveJob?.cancel()
        storeFile?.let { runCatching { it.delete() } }
    }

    /// Для тестов: забыть тренировку в памяти, не трогая файл, — как после выгрузки.
    fun forgetForTesting() {
        saveJob?.cancel()
        activeWorkout = null
        isPresented = false
        profileId = null
        exercises.clear()
    }

    /// Поднимает сохранённую тренировку этого профиля — свёрнутой, с плашкой
    /// «продолжить». Начатую больше суток назад не поднимаем: её забыли.
    fun restoreIfNeeded(profile: ProgramProfile, now: Long = System.currentTimeMillis()) {
        if (activeWorkout != null) return
        val file = storeFile ?: return
        if (!file.exists()) return
        val session = runCatching {
            json.decodeFromString(SavedSession.serializer(), file.readText())
        }.getOrNull() ?: return
        if (session.profileId != profile.id) return
        if (now - session.startMillis > 24 * 3600 * 1000L) {
            clearSaved()
            return
        }
        val workout = profile.schedule().allPending
            .firstOrNull { it.week == session.week && it.day.number == session.day }
            ?: run {
                val day = profile.workoutPlan.weeks.firstOrNull { it.number == session.week }
                    ?.days?.firstOrNull { it.number == session.day } ?: return
                ScheduledWorkout(
                    week = session.week,
                    day = day,
                    date = LocalDate.now(),
                    weekday = profile.weekday(session.day),
                    isCompleted = profile.isCompleted(session.week, session.day),
                    benchSession = session.benchSession
                )
            }
        val byKey = profile.exercises(workout).associateBy { it.key }
        val restored = session.exercises.mapNotNull { saved ->
            val presc = byKey[saved.key] ?: return@mapNotNull null
            ActiveExerciseState(
                prescription = presc,
                restSeconds = saved.restSeconds,
                sets = saved.sets.map { ActiveSetState(nextId(), it.index, it.previous, it.weight, it.reps, it.isDone, it.isRecord) },
                bestBefore = saved.bestBefore
            )
        }
        if (restored.isEmpty()) return
        activeWorkout = workout
        profileId = session.profileId
        startMillis = session.startMillis
        noteText = session.note
        snapshotProgress = session.snapshotProgress
        snapshotLog = session.snapshotLog
        exercises.clear()
        exercises.addAll(restored)
        isPresented = false
    }
}
