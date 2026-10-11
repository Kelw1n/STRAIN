package com.texasprogram.app

import com.texasprogram.app.model.ProgramInput
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.service.MuscleCategory
import com.texasprogram.app.service.MuscleGroupAnalytics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MuscleGroupAnalyticsTest {

    @Test
    fun testCategorization() {
        assertEquals(MuscleCategory.LEGS, MuscleCategory.categorize("Приседания со штангой"))
        assertEquals(MuscleCategory.CHEST, MuscleCategory.categorize("Жим штанги лёжа"))
        assertEquals(MuscleCategory.BACK, MuscleCategory.categorize("Становая тяга"))
        assertEquals(MuscleCategory.SHOULDERS, MuscleCategory.categorize("Армейский жим стоя"))
        assertEquals(MuscleCategory.ARMS, MuscleCategory.categorize("Сгибания на бицепс"))
    }

    @Test
    fun testAnalyticsOnNewProfile() {
        val profile = ProgramProfile.texas(ProgramInput.demo)
        val result = MuscleGroupAnalytics.calculate(profile)

        assertFalse(result.items.isEmpty())
        assertEquals(5, result.radarValues.size)
        assertTrue(result.items.sumOf { it.sets } > 0)
    }

    @Test
    fun testResetDaySets() {
        var profile = ProgramProfile.texas(ProgramInput.demo)
        val workout = profile.schedule().allPending.first()
        val ex = profile.exercises(workout).first()

        profile = profile.recordSet(workout.week, workout.day.number, ex, 0, 5, 100.0)
        profile = profile.recordSet(workout.week, workout.day.number, ex, 1, 5, 100.0)

        assertTrue(profile.setLog.keys.any { it.startsWith("${workout.week}-${workout.day.number}") })

        profile = profile.resetDaySets(workout.week, workout.day.number)
        assertFalse(profile.setLog.keys.any { it.startsWith("${workout.week}-${workout.day.number}") })
    }
}
