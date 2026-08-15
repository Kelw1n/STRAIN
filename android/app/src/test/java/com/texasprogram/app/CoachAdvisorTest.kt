package com.texasprogram.app

import com.texasprogram.app.model.ProgramInput
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.service.CoachAdvisor
import com.texasprogram.app.service.CoachSecrets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/// Сбор данных для разбора: что уходит в запрос, а что остаётся дома.
class CoachAdvisorTest {

    private fun profile() = ProgramProfile.texas(ProgramInput.demo)

    @Test
    fun emptyProfileHasNothingToAnalyze() {
        assertFalse(CoachAdvisor.hasData(profile()))
        assertTrue(CoachAdvisor.hasData(profile().setNote("норм", 1, 1)))
    }

    /// Ключи вида «3-2» модель видеть не должна — только человеческий текст.
    @Test
    fun notesAreRenderedWithReadableDays() {
        val text = CoachAdvisor.context(profile().setNote("болело плечо", 3, 2))
        assertTrue(text, text.contains("неделя 3, день 2: болело плечо"))
        assertFalse(text, text.contains("— 3-2:"))
    }

    @Test
    fun contextCarriesProgrammeAndMaximums() {
        val text = CoachAdvisor.context(profile().setNote("норм", 1, 1))
        assertTrue(text, text.contains("Техасский метод"))
        assertTrue(text, text.contains("5ПМ"))
        assertTrue(text, text.contains("присед 100"))
    }

    /// Заметки прошлого цикла остаются в хранилище, но в разбор не идут.
    @Test
    fun otherCyclesStayOutOfTheContext() {
        val old = profile().setNote("старый цикл", 1, 1)
        val fresh = old.startNewCycle(110.0, 110.0, 110.0).setNote("новый цикл", 1, 1)

        val text = CoachAdvisor.context(fresh)
        assertTrue(text, text.contains("новый цикл"))
        assertFalse(text, text.contains("старый цикл"))
    }

    /// Заметки идут по порядку недель, а не по строковому сравнению ключей.
    @Test
    fun notesAreOrderedByWeekNotByString()  {
        val text = CoachAdvisor.context(
            profile().setNote("девятая", 9, 1).setNote("десятая", 10, 1)
        )
        assertTrue(text, text.indexOf("девятая") < text.indexOf("десятая"))
    }

    /// Записанные подходы попадают в разбор сгруппированными по дню.
    @Test
    fun recordedSetsAreGroupedByDay() {
        var value = profile()
        val squat = value.workoutPlan.weeks.first().days.first().exercises.first()
        value = value.recordSet(1, 1, squat, 0, 5, 80.0).recordSet(1, 1, squat, 1, 5, 82.5)

        val text = CoachAdvisor.context(value)
        assertTrue(text, text.contains("неделя 1, день 1: 5×80, 5×82.5 кг"))
    }

    /// Ключ лежит кусками — проверяем, что собирается обратно целым.
    @Test
    fun embeddedKeyDecodes() {
        val key = CoachSecrets.embeddedKey
        assertTrue(key, key.startsWith("AQ."))
        assertEquals(53, key.length)
    }
}
