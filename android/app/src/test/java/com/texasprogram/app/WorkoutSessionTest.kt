package com.texasprogram.app

import com.texasprogram.app.model.ProgramInput
import com.texasprogram.app.model.PlanEditScope
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.UpperLowerInput
import com.texasprogram.app.model.WorkoutTemplate
import com.texasprogram.app.service.ExerciseArt
import com.texasprogram.app.service.RuPlural
import com.texasprogram.app.service.ScheduledWorkout
import com.texasprogram.app.service.WorkoutSessionManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId

/// Тренировка на экране: подходы, перестановка, отмена и сохранение на диск.
class WorkoutSessionTest {

    private lateinit var dir: File
    private lateinit var profile: ProgramProfile
    private val manager = WorkoutSessionManager

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("session").toFile()
        manager.attach(dir)
        manager.forgetForTesting()
        manager.clearSaved()
        profile = ProgramProfile.texas(ProgramInput.demo)
    }

    @After
    fun tearDown() {
        manager.forgetForTesting()
        manager.clearSaved()
        dir.deleteRecursively()
    }

    /// Тренировка, где у первого упражнения несколько подходов. Ближайшая по дате
    /// зависит от дня недели, а в силовой день там один подход — удалять нечего.
    private fun workout(p: ProgramProfile = profile): ScheduledWorkout =
        p.schedule().allPending.first { (p.exercises(it).firstOrNull()?.sets ?: 0) >= 2 }

    private fun start(): ScheduledWorkout {
        val w = workout()
        manager.startWorkout(w, profile)
        return w
    }

    private val update: (ProgramProfile) -> Unit = { profile = it }

    @Test
    fun startBuildsAsManyRowsAsThePlan() {
        val w = start()
        val planned = profile.exercises(w)
        assertEquals(planned.size, manager.exercises.size)
        planned.zip(manager.exercises).forEach { (presc, ex) ->
            assertEquals(maxOf(presc.sets, 1), ex.sets.size)
            assertEquals(presc.key, ex.key)
        }
        assertTrue(manager.isPresented)
        // Ключи строк не повторяются, даже если упражнение стоит в дне дважды.
        assertEquals(manager.exercises.size, manager.exercises.map { it.id }.toSet().size)
    }

    @Test
    fun toggleRecordsSetAndUntoggleClearsIt() {
        val w = start()
        val ex = manager.exercises[0]
        manager.toggleSetDone(0, 0, profile, null, update)
        assertTrue(manager.exercises[0].sets[0].isDone)
        assertNotNull(profile.setEntry(w.week, w.day.number, ex.prescription, 0))

        manager.toggleSetDone(0, 0, profile, null, update)
        assertFalse(manager.exercises[0].sets[0].isDone)
        assertNull(profile.setEntry(w.week, w.day.number, ex.prescription, 0))
    }

    @Test
    fun editingWeightOfDoneSetRewritesTheRecord() {
        val w = start()
        val ex = manager.exercises[0]
        manager.toggleSetDone(0, 0, profile, null, update)
        manager.setWeight(123.5, 0, 0, profile, update)
        assertEquals(123.5, profile.setEntry(w.week, w.day.number, ex.prescription, 0)!!.weight, 0.001)
        manager.setReps(7, 0, 0, profile, update)
        assertEquals(7, profile.setEntry(w.week, w.day.number, ex.prescription, 0)!!.reps)
    }

    @Test
    fun addAfterDeleteDoesNotReuseALiveIndex() {
        start()
        val before = manager.exercises[0].sets.map { it.index }
        assertTrue(before.size >= 2)
        manager.deleteSet(0, 0, profile, update)
        manager.addSet(0)
        val after = manager.exercises[0].sets.map { it.index }
        assertEquals(after.size, after.toSet().size)
        assertEquals(before.max() + 1, after.last())
    }

    @Test
    fun deletingDoneSetClearsItsRecord() {
        val w = start()
        val ex = manager.exercises[0]
        manager.toggleSetDone(0, 0, profile, null, update)
        manager.deleteSet(0, 0, profile, update)
        assertNull(profile.setEntry(w.week, w.day.number, ex.prescription, 0))
    }

    @Test
    fun lastSetCannotBeDeleted() {
        start()
        while (manager.exercises[0].sets.size > 1) manager.deleteSet(0, 0, profile, update)
        manager.deleteSet(0, 0, profile, update)
        assertEquals(1, manager.exercises[0].sets.size)
    }

    @Test
    fun applyPreviousCopiesLastTime() {
        // Прошлый раз: та же тренировка неделей раньше записана целиком.
        val w = workout()
        val presc = profile.exercises(w)[0]
        val earlier = profile.workoutPlan.weeks.firstOrNull { it.number == w.week - 1 }
        if (earlier == null) {
            // Демо начинается с первой недели — прошлого раза нет, подставлять нечего.
            start()
            manager.applyPrevious(0, 0, profile, update)
            assertNull(manager.exercises[0].sets[0].previous)
            return
        }
        val day = earlier.days.first { it.number == w.day.number }
        val old = day.exercises.first { it.name == presc.name }
        profile = profile.recordSet(earlier.number, day.number, old, 0, 4, 77.5)
        manager.startWorkout(w, profile)
        manager.setWeight(10.0, 0, 0, profile, update)
        manager.applyPrevious(0, 0, profile, update)
        assertEquals(77.5, manager.exercises[0].sets[0].weight, 0.001)
        assertEquals(4, manager.exercises[0].sets[0].reps)
    }

    @Test
    fun moveExerciseReorders() {
        start()
        if (manager.exercises.size < 2) return
        val first = manager.exercises[0].id
        val second = manager.exercises[1].id
        manager.moveExercise(0, 1)
        assertEquals(second, manager.exercises[0].id)
        assertEquals(first, manager.exercises[1].id)
        // Выход за границы ничего не ломает.
        manager.moveExercise(0, 99)
        assertEquals(second, manager.exercises[0].id)
    }

    @Test
    fun discardRestoresDayToSnapshot() {
        val w = start()
        val ex = manager.exercises[0]
        manager.toggleSetDone(0, 0, profile, null, update)
        manager.discardWorkout(profile, update)
        assertFalse(manager.isRunning)
        assertNull(profile.setEntry(w.week, w.day.number, ex.prescription, 0))
    }

    /// Тренировку по расписанию на другой день начали сегодня — в заголовке сегодняшний
    /// день недели («СРЕДА · ДЕНЬ 3»), а не плановый («ПЯТНИЦА · ДЕНЬ 3»).
    @Test
    fun earlyWorkoutShowsTodaysWeekday() {
        val pending = profile.schedule().allPending
        val today = java.time.LocalDate.now()
        val future = pending.firstOrNull { it.date.isAfter(today) } ?: return
        manager.startWorkout(future, profile)
        val active = manager.activeWorkout!!
        assertEquals(future.id, active.id)
        assertEquals(com.texasprogram.app.service.RuDate.weekdayOf(today), active.weekday)
        assertEquals(today, active.date)
        assertTrue(active.fullTitle.startsWith(com.texasprogram.app.service.RuDate.full(active.weekday).uppercase()))
    }

    @Test
    fun requestStartAsksBeforeReplacing() {
        val pending = profile.schedule().allPending
        if (pending.size < 2) return
        manager.startWorkout(pending[0], profile)
        manager.requestStart(pending[1], profile)
        assertEquals(pending[1].id, manager.pendingWorkout?.id)
        assertEquals(pending[0].id, manager.activeWorkout?.id)
        manager.confirmReplace(profile, update)
        assertNull(manager.pendingWorkout)
        assertEquals(pending[1].id, manager.activeWorkout?.id)
    }

    /// Шаблон, начатый поверх идущей тренировки, и отказ в вопросе: заготовка
    /// свободной тренировки не должна остаться висеть в профиле.
    @Test
    fun cancellingTemplateReplaceDropsFreeWorkout() {
        val current = profile.schedule().allPending.first()
        manager.startWorkout(current, profile)
        manager.startTemplate(com.texasprogram.app.model.TemplateCatalog.all[0], profile, update)
        assertEquals(ProgramProfile.FREE_WEEK, manager.pendingWorkout?.week)
        assertEquals(1, profile.freeWorkouts.size)

        manager.cancelReplace(profile, update)
        assertNull(manager.pendingWorkout)
        assertTrue(profile.freeWorkouts.isEmpty())
        assertEquals(current.id, manager.activeWorkout?.id)
    }

    @Test
    fun sessionSurvivesProcessDeath() {
        val w = start()
        manager.toggleSetDone(0, 0, profile, null, update)
        manager.setWeight(101.0, 0, 1, profile, update)
        manager.updateNote("тяжело")
        val ids = manager.exercises.map { it.key }
        manager.writeNow()

        manager.forgetForTesting()
        assertFalse(manager.isRunning)
        manager.restoreIfNeeded(profile)

        assertTrue(manager.isRunning)
        assertFalse(manager.isPresented)
        assertEquals(w.id, manager.activeWorkout?.id)
        assertEquals(ids, manager.exercises.map { it.key })
        assertTrue(manager.exercises[0].sets[0].isDone)
        assertEquals(101.0, manager.exercises[0].sets[1].weight, 0.001)
        assertEquals("тяжело", manager.noteText)
    }

    @Test
    fun partialWarmUpSurvivesReorderingAndProcessDeath() {
        val w = start()
        val index = manager.exercises.indexOfFirst { it.warmUpSteps.isNotEmpty() }
        val exercise = manager.exercises[index]
        val step = exercise.warmUpSteps.first()
        assertTrue(exercise.warmUpSteps.size > 1)

        manager.toggleWarmUp(step, exercise.id)
        manager.moveExercise(index, if (index == 0) 1 else 0)
        assertEquals(setOf(step), manager.exercises.single { it.id == exercise.id }.completedWarmUp)
        manager.minimize()
        manager.forgetForTesting()
        manager.restoreIfNeeded(profile)

        val restored = manager.exercises.single { it.key == exercise.key }
        assertEquals(setOf(step), restored.completedWarmUp)
        assertEquals(0, manager.totalCompletedSets)
        assertEquals(0.0, manager.totalTonnage, 0.001)
        val restoredIndex = manager.exercises.indexOfFirst { it.id == restored.id }
        manager.toggleSetDone(restoredIndex, 0, profile, null, update)
        manager.toggleSetDone(restoredIndex, 0, profile, null, update)
        assertEquals(setOf(step), manager.exercises[restoredIndex].completedWarmUp)
        manager.toggleWarmUp(step, restored.id)
        assertTrue(manager.exercises.single { it.key == exercise.key }.completedWarmUp.isEmpty())
        manager.discardWorkout(profile, update)
        manager.startWorkout(w, profile)
        assertTrue(manager.exercises.single { it.key == exercise.key }.completedWarmUp.isEmpty())
    }

    @Test
    fun benchWaveUsesPlannedWeightAndRepsForEachSet() {
        profile = ProgramProfile.upperLower(UpperLowerInput.demo)
        val w = profile.schedule().allPending.first { it.benchSession == 1 }
        val wave = profile.benchWave.first { it.id == 1 }
        manager.startWorkout(w, profile)

        val bench = manager.exercises.single { it.prescription.benchSession == 1 }
        assertEquals(wave.sets.map { it.weight }, bench.sets.map { it.weight })
        assertEquals(wave.sets.map { it.reps.toInt() }, bench.sets.map { it.reps })

        // Пока сессия была закрыта, для того же дня расписание назначило другой жим.
        manager.writeNow()
        manager.forgetForTesting()
        manager.restoreIfNeeded(profile.setCurrentBenchSession(2))
        assertEquals(1, manager.activeWorkout?.benchSession)
        assertEquals(bench.prescription, manager.exercises.single { it.prescription.benchSession == 1 }.prescription)
    }

    @Test
    fun restoreKeepsExerciseRemovedFromPlanAfterStart() {
        val w = start()
        val exercise = manager.exercises.last()
        manager.setWeight(91.5, manager.exercises.lastIndex, 0, profile, update)
        manager.writeNow()
        manager.forgetForTesting()

        profile = profile.hideExercise(exercise.prescription, w.week, w.day.number, PlanEditScope.SINGLE)
        val updatedDay = profile.schedule().allPending.first { it.id == w.id }
        assertFalse(profile.exercises(updatedDay).any { it.key == exercise.key })
        manager.restoreIfNeeded(profile)

        val restored = manager.exercises.last()
        assertEquals(exercise.prescription, restored.prescription)
        assertEquals(91.5, restored.sets.first().weight, 0.001)
    }

    @Test
    fun editingOutOfOrderSetUpdatesRecordAndCount() {
        val older = workout()
        val first = profile.exercises(older).first()
        profile = profile.recordSet(older.week, older.day.number, first, 0, 5, 80.0)
        val w = profile.schedule().allPending.first { candidate ->
            candidate.id != older.id && profile.exercises(candidate).any { it.name == first.name && it.sets > 1 }
        }
        manager.startWorkout(w, profile)
        val index = manager.exercises.indexOfFirst { it.prescription.name == first.name }
        val exercise = manager.exercises[index].prescription

        manager.setWeight(90.0, index, 1, profile, update)
        manager.toggleSetDone(index, 1, profile, null, update)
        assertTrue(manager.exercises[index].sets[1].isRecord)
        assertEquals(1, profile.completedSets(w.week, w.day.number, exercise))
        manager.setWeight(75.0, index, 1, profile, update)
        assertFalse(manager.exercises[index].sets[1].isRecord)
        assertEquals(1, profile.completedSets(w.week, w.day.number, exercise))
    }

    @Test
    fun noteDraftSurvivesProcessDeathAndClearsOldNoteOnFinish() {
        val w = workout()
        profile = profile.setNote("старая заметка", w.week, w.day.number)
        manager.startWorkout(w, profile)
        manager.updateNote("")
        manager.writeNow()
        manager.forgetForTesting()
        manager.restoreIfNeeded(profile)

        assertEquals("", manager.noteText)
        assertEquals("старая заметка", profile.note(w.week, w.day.number))
        manager.finishWorkout(profile, onUpdateProfile = update)
        assertEquals("", profile.note(w.week, w.day.number))
    }

    @Test
    fun finishingAfterMidnightRecordsDayWorkoutStarted() {
        val w = workout()
        val zone = ZoneId.systemDefault()
        val started = LocalDate.now(zone).atStartOfDay(zone).minusMinutes(1)
        manager.startWorkout(w, profile, now = started.toInstant().toEpochMilli())
        manager.finishWorkout(profile, customDuration = 20 * 60, onUpdateProfile = update)

        val record = profile.completionLog.single { it.week == w.week && it.day == w.day.number }
        assertEquals(started.toLocalDate().toEpochDay(), record.epochDay)
        assertEquals(20 * 60, record.durationSeconds)
    }

    @Test
    fun savedSessionOfAnotherProfileIsIgnored() {
        start()
        manager.writeNow()
        manager.forgetForTesting()
        manager.restoreIfNeeded(ProgramProfile.texas(ProgramInput.demo))
        assertFalse(manager.isRunning)
    }

    @Test
    fun staleSessionIsDropped() {
        start()
        manager.writeNow()
        manager.forgetForTesting()
        manager.restoreIfNeeded(profile, now = System.currentTimeMillis() + 25 * 3600 * 1000L)
        assertFalse(manager.isRunning)
        assertTrue(File(dir, "active-workouts").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun eachProfileKeepsItsOwnActiveSession() {
        val second = ProgramProfile.texas(ProgramInput.demo)
        start()
        manager.updateNote("Первый профиль")
        manager.detachIfOtherProfile(second)
        manager.startWorkout(workout(second), second)
        manager.updateNote("Второй профиль")

        manager.detachIfOtherProfile(profile)
        manager.restoreIfNeeded(profile)
        assertTrue(manager.isRunning)
        assertEquals("Первый профиль", manager.noteText)
        manager.discardWorkout(profile, update)

        manager.restoreIfNeeded(second)
        assertTrue(manager.isRunning)
        assertEquals("Второй профиль", manager.noteText)
    }

    @Test
    fun emptyFreeWorkoutSurvivesProcessDeath() {
        manager.startTemplate(WorkoutTemplate(name = "Тренировка"), profile, update)
        val id = manager.activeWorkout?.id
        assertNotNull(id)
        manager.writeNow()

        manager.forgetForTesting()
        manager.restoreIfNeeded(profile)

        assertEquals(id, manager.activeWorkout?.id)
        assertTrue(manager.exercises.isEmpty())
        assertFalse(manager.isPresented)
    }

    @Test
    fun exerciseArtMatchesNamesLikeIos() {
        assertEquals("squat", ExerciseArt.key("Приседания"))
        assertEquals("front_squat", ExerciseArt.key("Фронтальные приседания"))
        assertEquals("bench", ExerciseArt.key("Жим лёжа"))
        assertEquals("incline", ExerciseArt.key("Жим на наклонной скамье"))
        assertEquals("row", ExerciseArt.key("Тяга штанги в наклоне"))
        assertEquals("deadlift", ExerciseArt.key("Становая тяга"))
        assertEquals("pullup", ExerciseArt.key("Подтягивания"))
        assertEquals("generic", ExerciseArt.key("Что-то своё"))
    }

    @Test
    fun russianPlurals() {
        assertEquals("1 подход", RuPlural.sets(1))
        assertEquals("3 подхода", RuPlural.sets(3))
        assertEquals("5 подходов", RuPlural.sets(5))
        assertEquals("11 подходов", RuPlural.sets(11))
        assertEquals("21 подход", RuPlural.sets(21))
        assertEquals("22 упражнения", RuPlural.exercises(22))
    }

    @Test
    fun monthCalendarStartsOnMonday() {
        // Октябрь 2026 начинается в четверг: три пустые клетки перед первым числом.
        val rows = com.texasprogram.app.ui.monthRows(java.time.LocalDate.of(2026, 10, 12))
        assertTrue(rows.all { it.size == 7 })
        assertEquals(listOf(null, null, null), rows[0].take(3))
        assertEquals(1, rows[0][3]?.dayOfMonth)
        assertEquals(31, rows.flatten().filterNotNull().size)
    }
}
