package com.texasprogram.app

import com.texasprogram.app.model.ProgramInput
import com.texasprogram.app.model.ProgramProfile
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

    private fun workout(p: ProgramProfile = profile): ScheduledWorkout = p.schedule().allPending.first()

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
        assertFalse(File(dir, "active-workout.json").exists())
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
}
