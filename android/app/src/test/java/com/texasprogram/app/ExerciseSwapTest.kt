package com.texasprogram.app

import com.texasprogram.app.model.ExerciseSwap
import com.texasprogram.app.model.PlanEditScope
import com.texasprogram.app.model.ProgramInput
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.SwapScope
import com.texasprogram.app.model.SwapUndo
import com.texasprogram.app.model.isProgramLift
import com.texasprogram.app.model.swapExercise
import com.texasprogram.app.model.undoSwaps
import com.texasprogram.app.service.WorkoutSessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/// Замена упражнения на ходу: похожие по движению, вес по истории или примерно,
/// «только сегодня» и «до конца программы», откат при отмене тренировки.
class ExerciseSwapTest {
    private fun texas() = ProgramProfile.texas(ProgramInput.demo)

    @Test
    fun familiesAndEstimates() {
        assertEquals("Присед", ExerciseSwap.family("Приседания")?.title)
        assertEquals("Горизонтальная тяга", ExerciseSwap.family("Тяга штанги в наклоне")?.title)
        assertEquals(80.0, ExerciseSwap.estimate("Приседания", 100.0, "Фронтальные приседания", 2.5)!!, 0.0)
        assertEquals("с весом тела", 0.0, ExerciseSwap.estimate("Жим лёжа", 100.0, "Отжимания на брусьях", 2.5)!!, 0.0)
        assertNull("другое движение", ExerciseSwap.estimate("Жим лёжа", 100.0, "Приседания", 2.5))
        assertNull(ExerciseSwap.estimate("Что-то своё", 100.0, "Фронтальные приседания", 2.5))
    }

    @Test
    fun historyComesFirstWithLastWeight() {
        var profile = texas()
        val day = profile.workoutPlan.weeks.first().days.first()
        val squat = day.exercises.first { it.name == "Приседания" }
        // Неделю назад вместо приседа делали фронтальный.
        val swapped = profile.swapExercise(squat, "Фронтальные приседания", 1, day.number, SwapScope.TODAY, SwapUndo())
        profile = swapped.profile.recordSet(1, day.number, swapped.prescription, 0, 5, 90.0)

        val found = ExerciseSwap.suggestions("Приседания", 120.0, profile, 2, day.number)
        assertEquals("Фронтальные приседания", found.done.first().name)
        assertEquals(90.0, found.done.first().weight!!, 0.0)
        assertEquals(5, found.done.first().reps)
        assertFalse("уже в «делал»", found.similar.any { it.name == "Фронтальные приседания" })
        assertFalse("само себя не предлагаем", found.similar.any { it.name == "Приседания" })
        assertEquals(90.0, found.similar.first { it.name == "Присед Зерчера" }.weight!!, 0.0)
    }

    @Test
    fun todayOnlyAndUndo() {
        val profile = texas()
        val squat = profile.workoutPlan.weeks[0].days[0].exercises.first { it.name == "Приседания" }
        val result = profile.swapExercise(squat, "Жим ногами", 1, 1, SwapScope.TODAY, SwapUndo())
        assertTrue(result.profile.workoutPlan.weeks[0].days[0].exercises.any { it.name == "Жим ногами" })
        assertTrue("следующая неделя по программе", result.profile.workoutPlan.weeks[1].days[0].exercises.any { it.name == "Приседания" })
        val undone = result.profile.undoSwaps(result.undo)
        assertTrue(undone.planEdits.isEmpty())
        assertTrue(undone.workoutPlan.weeks[0].days[0].exercises.any { it.name == "Приседания" })
    }

    @Test
    fun restOfProgramKeepsPastWeeks() {
        val profile = texas()
        val accessory = profile.workoutPlan.weeks[1].days[0].exercises.first { !profile.isProgramLift(it) }
        val result = profile.swapExercise(accessory, "Своё упражнение", 2, 1, SwapScope.REST, SwapUndo())
        val weeks = result.profile.workoutPlan.weeks
        assertTrue("прошлая неделя не меняется", weeks[0].days[0].exercises.any { it.key == accessory.key })
        for (week in weeks.drop(1)) {
            if (week.days[0].exercises.none { it.name == "Своё упражнение" }) {
                assertFalse("неделя ${week.number} осталась со старым", week.days[0].exercises.any { it.key == accessory.key })
            }
        }
        assertTrue(weeks[1].days[0].exercises.any { it.name == "Своё упражнение" })
        assertTrue(weeks.last().days[0].exercises.any { it.name == "Своё упражнение" })
    }

    @Test
    fun ownEveryWeekExerciseSwapsOnlyWhereAsked() {
        var profile = texas().addExercise("Сгибания на бицепс", 3, "10", 20.0, null, 1, 1, PlanEditScope.EVERY_WEEK)
        val added = profile.planEdits.single()
        profile = profile.recordSet(1, 1, added.prescription, 0, 10, 20.0)
        val own = profile.workoutPlan.weeks[1].days[0].exercises.first { it.key == added.id }
        val first = profile.swapExercise(own, "Молотки", 2, 1, SwapScope.TODAY, SwapUndo())
        profile = first.profile
        var weeks = profile.workoutPlan.weeks
        assertTrue(weeks[1].days[0].exercises.any { it.key == first.prescription.key && it.name == "Молотки" })
        assertFalse(weeks[1].days[0].exercises.any { it.key == added.id })
        assertTrue("прошлая неделя как была", weeks[0].days[0].exercises.any { it.key == added.id && it.name == "Сгибания на бицепс" })
        assertTrue("следующая неделя по-старому", weeks[2].days[0].exercises.any { it.key == added.id })
        assertEquals("история не переехала", 1, profile.history("Сгибания на бицепс").first { it.entries.isNotEmpty() }.week)

        val second = profile.swapExercise(first.prescription, "Сгибания со штангой", 2, 1, SwapScope.REST, first.undo)
        profile = second.profile
        weeks = profile.workoutPlan.weeks
        assertTrue(weeks[1].days[0].exercises.any { it.name == "Сгибания со штангой" })
        assertTrue(weeks.last().days[0].exercises.any { it.name == "Сгибания со штангой" })
        assertTrue(weeks[0].days[0].exercises.any { it.key == added.id })

        assertEquals(listOf(added), profile.undoSwaps(second.undo).planEdits)
    }

    @Test
    fun sessionSwapClearsMarkedSetsAndDiscardRestores() {
        WorkoutSessionManager.forgetForTesting()
        var profile = texas()
        val workout = profile.schedule().allPending.first()
        WorkoutSessionManager.startWorkout(workout, profile)
        val index = WorkoutSessionManager.exercises.indexOfFirst { it.prescription.name == "Приседания" }
        val old = WorkoutSessionManager.exercises[index].prescription
        WorkoutSessionManager.toggleSetDone(index, 0, profile, null) { profile = it }
        assertNotNull(profile.setEntry(workout.week, workout.day.number, old, 0))

        val suggestion = ExerciseSwap.suggestion("Фронтальные приседания", old.name, 100.0, profile, workout.week, workout.day.number)
        WorkoutSessionManager.swapExercise(index, suggestion, SwapScope.REST, profile) { profile = it }
        val swapped = WorkoutSessionManager.exercises[index]
        assertEquals("Фронтальные приседания", swapped.prescription.name)
        assertEquals(80.0, swapped.sets.first().weight, 0.0)
        assertFalse(swapped.sets.any { it.isDone })
        assertNull("старый подход снят", profile.setEntry(workout.week, workout.day.number, old, 0))
        // Основное движение меняется только на сегодня, даже если просили дальше.
        assertEquals(1, profile.planEdits.size)

        WorkoutSessionManager.discardWorkout(profile) { profile = it }
        assertTrue("отмена тренировки возвращает упражнение", profile.planEdits.isEmpty())
    }
}
