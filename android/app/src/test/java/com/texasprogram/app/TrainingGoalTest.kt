package com.texasprogram.app

import com.texasprogram.app.data.BackupService
import com.texasprogram.app.model.ProgramInput
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.TrainingGoal
import com.texasprogram.app.model.activeReminderOffsets
import com.texasprogram.app.model.goalEnd
import com.texasprogram.app.model.goalWeek
import com.texasprogram.app.model.trainingMinutes
import com.texasprogram.app.service.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/// «Цель»: своё время у каждого дня, несколько напоминаний и срок.
/// Те же случаи — в TrainingGoalTests на iOS.
class TrainingGoalTest {

    private fun profile(): ProgramProfile = ProgramProfile.texas(ProgramInput.demo).copy(
        reminderEnabled = true,
        reminderHour = 18,
        reminderMinute = 0,
        scheduleWeekdays = listOf(2, 4, 6)
    )

    @Test
    fun ownTimePerDay() {
        val profile = profile().copy(trainingTimes = mapOf("4" to 7 * 60 + 30))
        assertEquals(18 * 60, profile.trainingMinutes(2))
        assertEquals(7 * 60 + 30, profile.trainingMinutes(4))
        val plan = ReminderScheduler.plan(profile)
        assertEquals(3, plan.size)
        val wednesday = plan.first { it.weekday == 4 }
        assertEquals(7 * 60 + 30, wednesday.minutes)
        assertEquals(2, wednesday.day)
    }

    @Test
    fun severalRemindersBeforeTraining() {
        val profile = profile().copy(reminderOffsets = listOf(60, 0, 15, 30))
        // Не больше трёх, по порядку.
        assertEquals(listOf(0, 15, 30), profile.activeReminderOffsets)
        val monday = ReminderScheduler.plan(profile).filter { it.weekday == 2 }
        assertEquals(listOf(0, 15, 30), monday.map { it.offset })
        assertEquals(17 * 60 + 30, monday.first { it.offset == 30 }.minutes)
        assertEquals("Тренировка через 30 минут", ReminderScheduler.title(30))
        assertEquals("Тренировка через час", ReminderScheduler.title(60))
        assertEquals("Сегодня тренировка", ReminderScheduler.title(0))
    }

    @Test
    fun reminderBeforeMidnightIsSkipped() {
        val profile = profile().copy(trainingTimes = mapOf("2" to 30), reminderOffsets = listOf(0, 60))
        val monday = ReminderScheduler.plan(profile).filter { it.weekday == 2 }
        assertEquals(listOf(0), monday.map { it.offset })
    }

    @Test
    fun emptyOffsetsMeanAtStart() {
        assertEquals(listOf(0), profile().copy(reminderOffsets = emptyList()).activeReminderOffsets)
    }

    @Test
    fun noRemindersWhenDisabled() {
        assertEquals(emptyList<ReminderScheduler.Planned>(), ReminderScheduler.plan(profile().copy(reminderEnabled = false)))
    }

    @Test
    fun goalTerm() {
        assertNull(profile().goalWeek(LocalDate.of(2026, 10, 26)))
        val profile = profile().copy(goalStartEpochDay = LocalDate.of(2026, 10, 11).toEpochDay(), goalMonths = 3)
        assertEquals(LocalDate.of(2027, 1, 11), profile.goalEnd())
        assertEquals(3 to 14, profile.goalWeek(LocalDate.of(2026, 10, 26)))
        assertNull(profile.goalWeek(LocalDate.of(2026, 10, 1)))
        assertEquals(14, profile.goalWeek(LocalDate.of(2027, 6, 1))?.first)
        // Без длительности срока нет.
        assertNull(profile.copy(goalMonths = 0).goalWeek(LocalDate.of(2026, 10, 26)))
    }

    @Test
    fun titles() {
        assertEquals("В начале тренировки", TrainingGoal.offsetTitle(0))
        assertEquals("За 1 час", TrainingGoal.offsetTitle(60))
        assertEquals("Без срока", TrainingGoal.monthsTitle(0))
        assertEquals("3 месяца", TrainingGoal.monthsTitle(3))
        assertEquals("6 месяцев", TrainingGoal.monthsTitle(6))
    }

    @Test
    fun goalSurvivesBackup() {
        val profile = profile().copy(
            trainingTimes = mapOf("2" to 6 * 60, "6" to 20 * 60 + 15),
            goalStartEpochDay = LocalDate.of(2026, 10, 11).toEpochDay(),
            goalMonths = 6
        )
        val restored = BackupService.profile(BackupService.parse(BackupService.export(listOf(profile))).profiles.first())
        assertEquals(profile.trainingTimes, restored.trainingTimes)
        assertEquals(profile.goalStartEpochDay, restored.goalStartEpochDay)
        assertEquals(6, restored.goalMonths)
    }
}
