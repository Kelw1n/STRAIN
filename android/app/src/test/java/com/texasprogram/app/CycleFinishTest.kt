package com.texasprogram.app

import com.texasprogram.app.model.CustomDay
import com.texasprogram.app.model.CustomExercise
import com.texasprogram.app.model.CustomProgram
import com.texasprogram.app.model.CycleFeel
import com.texasprogram.app.model.CycleFinish
import com.texasprogram.app.model.CycleGrowth
import com.texasprogram.app.model.NextCycleStart
import com.texasprogram.app.model.ProgramInput
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.SplitTemplate
import com.texasprogram.app.model.cycleRecap
import com.texasprogram.app.model.isProgramFinished
import com.texasprogram.app.model.nextCycleStart
import com.texasprogram.app.model.startNewCustomCycle
import com.texasprogram.app.service.EuthanasiaInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/// Конец программы: вопрос о весах и старт следующего цикла.
/// Те же случаи — в CycleFinishTests на iOS.
class CycleFinishTest {

    private fun program(weeks: Int = 8) = CustomProgram(
        name = "Моя",
        template = SplitTemplate.CUSTOM,
        weeks = weeks,
        days = listOf(
            CustomDay(
                number = 1, title = "ДЕНЬ 1", exercises = listOf(
                    CustomExercise(id = "own-squat", name = "Присед", sets = 3, reps = "5", kilograms = 100.0, weeklyIncrement = 2.5),
                    CustomExercise(id = "own-curl", name = "Бицепс", sets = 3, reps = "10–12", kilograms = 20.0, weeklyIncrement = 0.0),
                    CustomExercise(id = "own-pull", name = "Подтягивания", sets = 3, reps = "8", loadText = "свой вес")
                )
            )
        )
    )

    @Test
    fun minimumReps() {
        assertEquals(5, CycleFinish.minimumReps("5"))
        assertEquals(8, CycleFinish.minimumReps("8–10"))
        assertEquals(8, CycleFinish.minimumReps("8/10"))
        assertNull(CycleFinish.minimumReps("до отказа"))
    }

    @Test
    fun suggestionFromRecords() {
        assertNull(CycleFinish.suggestion(null, 0, 0))
        assertEquals(CycleFeel.HARD, CycleFinish.suggestion(9.5, 0, 10))
        assertEquals(CycleFeel.HARD, CycleFinish.suggestion(7.0, 3, 12))
        assertEquals(CycleFeel.EASY, CycleFinish.suggestion(4.0, 0, 12))
        assertEquals(CycleFeel.NORMAL, CycleFinish.suggestion(7.5, 1, 20))
        assertEquals(CycleFeel.NORMAL, CycleFinish.suggestion(null, 0, 9))
    }

    @Test
    fun nextCustomWeights() {
        val base = program()
        val easy = CycleFinish.nextCustom(base, CycleFeel.EASY)
        val normal = CycleFinish.nextCustom(base, CycleFeel.NORMAL)
        val hard = CycleFinish.nextCustom(base, CycleFeel.HARD)
        assertEquals(120.0, easy.first.days[0].exercises[0].kilograms!!, 0.001)
        assertEquals(117.5, normal.first.days[0].exercises[0].kilograms!!, 0.001)
        assertEquals(105.0, hard.first.days[0].exercises[0].kilograms!!, 0.001)
        assertEquals(20.0, hard.first.days[0].exercises[1].kilograms!!, 0.001)
        assertNull(hard.first.days[0].exercises[2].kilograms)
        assertEquals(listOf(CycleGrowth("Присед", 100.0, 117.5)), normal.second)
    }

    @Test
    fun nextMax() {
        assertEquals(110.0, CycleFinish.nextMax(100.0, 1.1, CycleFeel.NORMAL), 0.001)
        assertEquals(112.5, CycleFinish.nextMax(100.0, 1.1, CycleFeel.EASY), 0.001)
        assertEquals(100.0, CycleFinish.nextMax(100.0, 1.1, CycleFeel.HARD), 0.001)
        assertEquals(100.0, CycleFinish.nextMax(100.0, 0.8, CycleFeel.NORMAL), 0.001)
        assertEquals(130.0, CycleFinish.nextMax(100.0, 2.0, CycleFeel.NORMAL), 0.001)
    }

    @Test
    fun customProgramFinishesAndStartsNextCycle() {
        var profile = ProgramProfile.custom(program(weeks = 2))
        assertFalse(profile.isProgramFinished)
        profile = profile.toggleCompleted(1, 1)
        assertFalse(profile.isProgramFinished)
        profile = profile.toggleCompleted(2, 1)
        assertTrue(profile.isProgramFinished)
        assertEquals(2, profile.cycleRecap.workouts)

        val next = profile.nextCycleStart(CycleFeel.NORMAL) as NextCycleStart.Custom
        profile = profile.startNewCustomCycle(next.program)
        assertEquals(2, profile.cycleNumber)
        assertFalse(profile.isProgramFinished)
        assertEquals(102.5, profile.customProgram!!.days[0].exercises[0].kilograms!!, 0.001)
        assertEquals(0, profile.completedDayCount)
    }

    @Test
    fun readyProgramSuggestsMaxes() {
        val profile = ProgramProfile.texas(ProgramInput.demo)
        val next = profile.nextCycleStart(CycleFeel.HARD) as NextCycleStart.Maxes
        assertEquals(3, next.changes.size)
        assertTrue(next.squat > 0 && next.squat < profile.squat5RM * 1.3)
    }

    @Test
    fun euthanasiaNewCycleUpdatesItsMaxes() {
        val profile = ProgramProfile.euthanasia(EuthanasiaInput.demo).startNewCycle(150.0, 105.0, 110.0)
        assertEquals(150.0, profile.euthanasiaInput!!.squat.oneRepMax, 0.001)
        assertEquals(105.0, profile.euthanasiaInput!!.press.oneRepMax, 0.001)
        assertEquals(110.0, profile.euthanasiaInput!!.pull.oneRepMax, 0.001)
    }
}
