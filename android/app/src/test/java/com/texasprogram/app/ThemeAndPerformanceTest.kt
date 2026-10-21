package com.texasprogram.app

import androidx.compose.ui.graphics.Color
import com.texasprogram.app.model.ProgramInput
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.ui.Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ThemeAndPerformanceTest {

    /// Тема одна — REBRN: чёрный OLED-фон, графитовые карточки, оранжевый акцент.
    @Test
    fun testRebrnPalette() {
        assertEquals("Theme.card must match Theme.surface", Theme.surface, Theme.card)
        assertEquals("Theme.base must be OLED black", Color(0xFF000000), Theme.base)
        assertEquals("Theme.surface must be dark graphite", Color(0xFF161617), Theme.surface)
        assertEquals("Theme.accent must be REBRN orange", Color(0xFFFF6029), Theme.accent)
        assertEquals("Theme.textPrimary must be pure white", Color(0xFFFFFFFF), Theme.textPrimary)
    }

    @Test
    fun testScheduleDeterminismForMemoization() {
        val profile = ProgramProfile.texas(ProgramInput.demo)
        val today = LocalDate.of(2026, 9, 23)

        val schedule1 = profile.schedule(today)
        val schedule2 = profile.schedule(today)

        assertNotNull(schedule1)
        assertEquals("Schedule focus must be deterministic", schedule1.focus?.week, schedule2.focus?.week)
        assertEquals("Schedule focus day must be deterministic", schedule1.focus?.day?.number, schedule2.focus?.day?.number)
    }

    @Test
    fun testExerciseHistoryMapPrecomputation() {
        var profile = ProgramProfile.texas(ProgramInput.demo)
        val squat = profile.workoutPlan.weeks.first().days.first().exercises.first { it.name == "Приседания" }
        profile = profile.recordSet(
            week = 1,
            day = 1,
            exercise = squat,
            index = 0,
            reps = 5,
            weight = 100.0
        )

        val names = profile.loggedExerciseNames
        assertTrue("Logged exercises must contain Приседания", names.contains("Приседания"))

        val historyMap = names.associateWith { profile.history(it) }
        assertTrue("History map must contain points for Приседания", historyMap.containsKey("Приседания"))
        val squatPoints = historyMap["Приседания"].orEmpty()
        assertEquals(1, squatPoints.size)
        assertEquals(100.0, squatPoints.first().best, 0.001)
    }

    @Test
    fun testBodyWeightMemoizationMetrics() {
        var profile = ProgramProfile.texas(ProgramInput.demo)
        // Один замер на день: второй за те же сутки заменил бы первый.
        profile = profile.recordBodyWeight(80.0, java.time.LocalDate.of(2026, 10, 1))
        profile = profile.recordBodyWeight(82.5, java.time.LocalDate.of(2026, 10, 8))

        val series = profile.bodyWeightSeries
        assertEquals(2, series.size)

        val minWeight = series.minOf { it.weight }
        val maxWeight = series.maxOf { it.weight }
        val delta = series.last().weight - series.first().weight

        assertEquals(80.0, minWeight, 0.001)
        assertEquals(82.5, maxWeight, 0.001)
        assertEquals(2.5, delta, 0.001)

        val squatRatio = profile.relativeStrength(profile.squat5RM)
        assertTrue("Relative strength must be non-null when body weight is recorded", squatRatio != null)
    }

    @Test
    fun testNotesParsingAndSortingLogic() {
        var profile = ProgramProfile.texas(ProgramInput.demo)
        profile = profile.setNote("Week 1 Day 1 note", week = 1, day = 1)
        profile = profile.setNote("Week 2 Day 3 note", week = 2, day = 3)

        val prefix = profile.keyPrefix
        val notes = profile.workoutNotes
            .filterKeys { profile.isOwnKey(it) }
            .mapNotNull { (key, text) ->
                val parts = key.removePrefix(prefix).split("-")
                val week = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val day = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                Triple(week, day, text)
            }
            .sortedWith(compareByDescending<Triple<Int, Int, String>> { it.first }.thenByDescending { it.second })

        assertEquals(2, notes.size)
        // Week 2 Day 3 must come first (descending sort)
        assertEquals(2, notes[0].first)
        assertEquals(3, notes[0].second)
        assertEquals("Week 2 Day 3 note", notes[0].third)

        assertEquals(1, notes[1].first)
        assertEquals(1, notes[1].second)
        assertEquals("Week 1 Day 1 note", notes[1].third)
    }
}
