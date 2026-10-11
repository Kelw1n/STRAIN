package com.texasprogram.app.service

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.texasprogram.app.model.ExercisePrescription
import com.texasprogram.app.model.LoadPrescription
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.formatWeight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

data class ActiveSetState(
    val id: String = UUID.randomUUID().toString(),
    val index: Int,
    val previousText: String,
    val weight: Double,
    val reps: Int,
    val isDone: Boolean
)

data class ActiveExerciseState(
    val prescription: ExercisePrescription,
    var restSeconds: Int,
    val sets: MutableList<ActiveSetState>
)

object WorkoutSessionManager {
    var activeWorkout by mutableStateOf<ScheduledWorkout?>(null)
    var isPresented by mutableStateOf(false)
    val isRunning: Boolean get() = activeWorkout != null

    var elapsedSeconds by mutableIntStateOf(0)
    val exercises = mutableStateListOf<ActiveExerciseState>()

    var noteText by mutableStateOf("")
    var showingZeroSetsAlert by mutableStateOf(false)
    var showingSummary by mutableStateOf(false)
    var showingNoteSheet by mutableStateOf(false)
    var customRestTargetExerciseIndex by mutableStateOf<Int?>(null)

    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    val totalCompletedSets: Int
        get() = exercises.sumOf { it.sets.count { s -> s.isDone } }

    val totalSetsCount: Int
        get() = exercises.sumOf { it.sets.size }

    val totalTonnage: Double
        get() = exercises.sumOf { ex ->
            ex.sets.filter { it.isDone }.sumOf { it.weight * it.reps.toDouble() }
        }

    val density: Double
        get() {
            val mins = maxOf(1.0, elapsedSeconds.toDouble() / 60.0)
            return totalTonnage / mins
        }

    fun startWorkout(workout: ScheduledWorkout, profile: ProgramProfile) {
        if (activeWorkout?.id == workout.id) {
            isPresented = true
            return
        }

        activeWorkout = workout
        elapsedSeconds = 0
        noteText = profile.note(workout.week, workout.day.number)
        showingZeroSetsAlert = false
        showingSummary = false
        showingNoteSheet = false
        customRestTargetExerciseIndex = null

        val prescs = profile.exercises(workout)
        exercises.clear()
        for (presc in prescs) {
            val defaultRest = if (presc.benchSession != null) 180 else profile.defaultRestSeconds
            val plannedWeight = extractPlannedWeight(presc, profile, workout)
            val plannedReps = extractPlannedReps(presc)
            val setsCount = maxOf(presc.sets, 3)

            val sets = (0 until setsCount).map { sIdx ->
                ActiveSetState(
                    index = sIdx,
                    previousText = previousPerformance(presc, sIdx, profile),
                    weight = plannedWeight,
                    reps = plannedReps,
                    isDone = false // ВСЕГДА начинается с 0 выполненных подходов!
                )
            }.toMutableList()

            exercises.add(
                ActiveExerciseState(
                    prescription = presc,
                    restSeconds = defaultRest,
                    sets = sets
                )
            )
        }

        startTimer()
        isPresented = true
    }

    fun minimize() {
        isPresented = false
    }

    fun expand() {
        isPresented = true
    }

    fun toggleSetDone(
        exIdx: Int,
        sIdx: Int,
        profile: ProgramProfile,
        restTimer: RestTimer,
        onUpdateProfile: (ProgramProfile) -> Unit
    ) {
        val workout = activeWorkout ?: return
        if (exIdx >= exercises.size || sIdx >= exercises[exIdx].sets.size) return
        val currentSet = exercises[exIdx].sets[sIdx]
        val newState = !currentSet.isDone

        exercises[exIdx].sets[sIdx] = currentSet.copy(isDone = newState)
        val ex = exercises[exIdx]

        if (newState) {
            val updated = profile.recordSet(
                week = workout.week,
                day = workout.day.number,
                exercise = ex.prescription,
                index = currentSet.index,
                reps = currentSet.reps,
                weight = currentSet.weight
            )
            val completedCount = exercises[exIdx].sets.count { it.isDone }
            val totalCount = exercises[exIdx].sets.size
            val details = "${formatWeight(currentSet.weight)} кг × ${currentSet.reps}"
            restTimer.start(
                seconds = ex.restSeconds.toLong(),
                exerciseName = ex.prescription.name,
                setDetails = details,
                completedSets = completedCount,
                totalSets = totalCount
            )
        } else {
            val cleared = profile.clearSetEntry(
                week = workout.week,
                day = workout.day.number,
                exercise = ex.prescription,
                index = currentSet.index
            )
            val remainingDone = exercises[exIdx].sets.count { it.isDone }
            val updated = cleared.updateCompletedSets(
                week = workout.week,
                day = workout.day.number,
                exercise = ex.prescription,
                count = remainingDone
            )
            onUpdateProfile(updated)
        }
    }

    fun addSet(exIdx: Int, profile: ProgramProfile) {
        if (exIdx >= exercises.size || activeWorkout == null) return
        val existingSets = exercises[exIdx].sets
        val lastWeight = existingSets.lastOrNull()?.weight ?: 50.0
        val lastReps = existingSets.lastOrNull()?.reps ?: 10
        val newIndex = existingSets.size

        val newSet = ActiveSetState(
            index = newIndex,
            previousText = previousPerformance(exercises[exIdx].prescription, newIndex, profile),
            weight = lastWeight,
            reps = lastReps,
            isDone = false
        )
        exercises[exIdx].sets.add(newSet)
    }

    fun adjustWeight(exIdx: Int, sIdx: Int, delta: Double) {
        if (exIdx >= exercises.size || sIdx >= exercises[exIdx].sets.size) return
        val set = exercises[exIdx].sets[sIdx]
        exercises[exIdx].sets[sIdx] = set.copy(weight = maxOf(0.0, set.weight + delta))
    }

    fun adjustReps(exIdx: Int, sIdx: Int, delta: Int) {
        if (exIdx >= exercises.size || sIdx >= exercises[exIdx].sets.size) return
        val set = exercises[exIdx].sets[sIdx]
        exercises[exIdx].sets[sIdx] = set.copy(reps = maxOf(1, set.reps + delta))
    }

    fun setRestDuration(seconds: Int) {
        val idx = customRestTargetExerciseIndex ?: return
        if (idx < exercises.size) {
            exercises[idx].restSeconds = seconds
        }
        customRestTargetExerciseIndex = null
    }

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
                    next = next.recordSet(
                        week = workout.week,
                        day = workout.day.number,
                        exercise = ex.prescription,
                        index = set.index,
                        reps = set.reps,
                        weight = set.weight
                    )
                }
            }
        }

        val finalDuration = customDuration ?: elapsedSeconds

        if (!next.isCompleted(workout.week, workout.day.number)) {
            next = next.toggleCompleted(
                week = workout.week,
                day = workout.day.number,
                durationSeconds = finalDuration,
                effort = effort
            )
        }

        onUpdateProfile(next)
        stopTimer()
        activeWorkout = null
        isPresented = false
        exercises.clear()
    }

    fun discardWorkout(profile: ProgramProfile, onUpdateProfile: (ProgramProfile) -> Unit) {
        val workout = activeWorkout ?: return
        val updated = profile.resetDaySets(workout.week, workout.day.number)
        onUpdateProfile(updated)

        stopTimer()
        activeWorkout = null
        isPresented = false
        exercises.clear()
    }

    private fun startTimer() {
        stopTimer()
        timerJob = scope.launch {
            while (isActive) {
                delay(1000)
                elapsedSeconds++
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun extractPlannedWeight(presc: ExercisePrescription, profile: ProgramProfile, workout: ScheduledWorkout): Double {
        (presc.load as? LoadPrescription.Kilograms)?.let { return it.value }
        profile.accessoryHint(presc, workout.week, workout.day.number)?.weight?.let { return it }
        val hist = profile.history(presc.name)
        val best = hist.lastOrNull()?.entries?.maxOfOrNull { it.weight }
        return best ?: 50.0
    }

    private fun extractPlannedReps(presc: ExercisePrescription): Int {
        return presc.plannedReps ?: 10
    }

    private fun previousPerformance(presc: ExercisePrescription, setIndex: Int, profile: ProgramProfile): String {
        val hist = profile.history(presc.name)
        val lastPoint = hist.lastOrNull() ?: return "—"
        if (lastPoint.entries.isEmpty()) return "—"
        if (setIndex < lastPoint.entries.size) {
            val entry = lastPoint.entries[setIndex]
            return "${formatWeight(entry.weight)} × ${entry.reps}"
        } else {
            val best = lastPoint.entries.first()
            return "${formatWeight(best.weight)} × ${best.reps}"
        }
    }
}
