package com.texasprogram.app

import androidx.compose.ui.graphics.Color
import com.texasprogram.app.model.ProgramInput
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.ui.AppThemeStyle
import com.texasprogram.app.ui.Theme
import com.texasprogram.app.ui.ThemeManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ThemeAndPerformanceTest {

    @Test
    fun testClaudeLightThemePalette() {
        ThemeManager.style = AppThemeStyle.CLAUDE_LIGHT

        assertFalse("Claude Light theme should not be dark", Theme.isDark)
        assertTrue("Claude Light theme should be Claude style", Theme.isClaude)

        assertEquals("Theme.card must match Theme.surface", Theme.surface, Theme.card)
        assertEquals("Theme.surface must be white in light theme", Color(0xFFFFFFFF), Theme.surface)
        assertEquals("Theme.base must be cream in light theme", Color(0xFFFBF8F3), Theme.base)
        assertEquals("Theme.surfaceSoft must be soft beige", Color(0xFFF3EEE7), Theme.surfaceSoft)
        assertEquals("Theme.dialog must be white", Color(0xFFFFFFFF), Theme.dialog)
        assertEquals("Theme.hairline must have dark alpha", Color(0x14000000), Theme.hairline)
        assertEquals("Theme.textPrimary must be high-contrast dark brown", Color(0xFF1E1D1B), Theme.textPrimary)
    }

    @Test
    fun testClaudeDarkThemePalette() {
        ThemeManager.style = AppThemeStyle.CLAUDE_DARK

        assertTrue("Claude Dark theme must be dark", Theme.isDark)
        assertTrue("Claude Dark theme must be Claude style", Theme.isClaude)

        assertEquals("Theme.card must match Theme.surface", Theme.surface, Theme.card)
        assertEquals("Theme.base must be dark graphite", Color(0xFF1B1917), Theme.base)
        assertEquals("Theme.surface must be dark surface", Color(0xFF262422), Theme.surface)
        assertEquals("Theme.surfaceSoft must be dark slate", Color(0xFF32302D), Theme.surfaceSoft)
        assertEquals("Theme.hairline must have white alpha", Color(0x28FFFFFF), Theme.hairline)
    }

    @Test
    fun testStrainDarkThemePalette() {
        ThemeManager.style = AppThemeStyle.STRAIN_DARK

        assertTrue("Strain Dark theme must be dark", Theme.isDark)
        assertFalse("Strain Dark theme must not be Claude style", Theme.isClaude)

        assertEquals("Theme.card must match Theme.surface", Theme.surface, Theme.card)
        assertEquals("Theme.base must be deep black/navy", Color(0xFF0B0E13), Theme.base)
        assertEquals("Theme.accent must be cyan neon", Color(0xFF29D6C2), Theme.accent)
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
        profile = profile.recordSet(
            week = 1,
            day = 1,
            exerciseName = "Присед",
            setIndex = 0,
            weight = 100.0,
            reps = 5,
            rpe = null
        )

        val names = profile.loggedExerciseNames
        assertTrue("Logged exercises must contain Присед", names.contains("Присед"))

        val historyMap = names.associateWith { profile.history(it) }
        assertTrue("History map must contain points for Присед", historyMap.containsKey("Присед"))
        val squatPoints = historyMap["Присед"].orEmpty()
        assertEquals(1, squatPoints.size)
        assertEquals(100.0, squatPoints.first().best, 0.001)
    }

    @Test
    fun testBodyWeightMemoizationMetrics() {
        var profile = ProgramProfile.texas(ProgramInput.demo)
        profile = profile.recordBodyWeight(80.0)
        profile = profile.recordBodyWeight(82.5)

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
        profile = profile.saveNote(week = 1, day = 1, text = "Week 1 Day 1 note")
        profile = profile.saveNote(week = 2, day = 3, text = "Week 2 Day 3 note")

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
