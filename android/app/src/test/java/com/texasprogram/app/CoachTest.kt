package com.texasprogram.app

import com.texasprogram.app.model.Coach
import com.texasprogram.app.model.CoachAction
import com.texasprogram.app.model.CoachMessage
import com.texasprogram.app.model.CompletionRecord
import com.texasprogram.app.model.ProgramInput
import com.texasprogram.app.model.ProgramProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/// ИИ-тренер: разбор ответа, проверка и применение действий, данные для тренера.
class CoachTest {
    private fun profile(): ProgramProfile {
        val base = ProgramProfile.texas(ProgramInput.demo)
        val squat = base.workoutPlan.weeks[0].days[0].exercises[0]
        val logged = base.recordSet(1, 1, squat, 0, reps = 5, weight = 100.0)
        return logged.copy(
            completionLog = listOf(CompletionRecord(logged.dayKey(1, 1), LocalDate.now().minusDays(1).toEpochDay(), 1, 1, effort = "Тяжело"))
        )
    }

    @Test
    fun parsesJsonAndPlainText() {
        val reply = Coach.parse("```json {\"reply\": \"Сбрось жим.\", \"actions\": [{\"type\": \"stall_reset\", \"lift\": \"bench\"}, {\"type\": \"weight_step\", \"exercise\": \"Приседания\", \"step\": \"1,25\"}]} ```")
        assertEquals("Сбрось жим.", reply.text)
        assertEquals(2, reply.actions.size)
        assertEquals(1.25, reply.actions[1].step!!, 0.0)
        assertEquals("Просто текст", Coach.parse("Просто текст").text)
        assertTrue(Coach.parse("Просто текст").actions.isEmpty())
    }

    @Test
    fun onlySupportedActionsResolveAndApply() {
        var profile = profile()
        val bench = profile.bench5RM
        val reset = CoachAction("stall_reset", lift = "bench")
        assertNotNull(Coach.resolve(reset, profile))
        profile = Coach.apply(reset, profile)
        assertTrue(profile.bench5RM < bench)
        assertTrue(profile.bench5RM >= bench * 0.85)

        val name = profile.loggedExerciseNames.first()
        val step = CoachAction("weight_step", exercise = name.uppercase(), step = 1.25)
        assertNotNull("название сверяется без учёта регистра", Coach.resolve(step, profile))
        profile = Coach.apply(step, profile)
        assertEquals(1.25, profile.weightStep(name), 0.0)
        assertNull("тот же шаг второй раз не предлагается", Coach.resolve(step, profile))

        assertNull("шаг не из списка", Coach.resolve(CoachAction("weight_step", exercise = name, step = 3.0), profile))
        assertNull(Coach.resolve(CoachAction("weight_step", exercise = "Выдуманное", step = 5.0), profile))
        assertNull(Coach.resolve(CoachAction("delete_program"), profile))
        assertNull(Coach.resolve(CoachAction("stall_reset", lift = "curl"), profile))
    }

    @Test
    fun contextHasProgramAndSets() {
        val text = Coach.context(profile(), null)
        assertTrue(text, text.contains("Программа: Техасский метод"))
        assertTrue(text, text.contains("100×5"))
        assertTrue(text, text.contains("тяжесть: Тяжело"))
        assertTrue(text, text.contains("Вес тела не записан"))
    }

    @Test
    fun weeklyReviewOnMondayOnce() {
        val monday = LocalDate.of(2026, 10, 26)
        val profile = ProgramProfile.texas(ProgramInput.demo).copy(
            completionLog = listOf(CompletionRecord("1-1", monday.minusDays(3).toEpochDay(), 1, 1))
        )
        assertTrue(Coach.needsReview(emptyList(), profile, monday))
        val done = CoachMessage(role = "coach", text = "Разбор", reviewWeek = Coach.reviewWeek(monday))
        assertFalse(Coach.needsReview(listOf(done), profile, monday))
        assertFalse("в четверг не предлагаем", Coach.needsReview(emptyList(), profile, monday.plusDays(3)))
    }
}
