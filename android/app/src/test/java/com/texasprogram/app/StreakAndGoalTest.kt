package com.texasprogram.app

import com.texasprogram.app.data.BackupService
import com.texasprogram.app.model.ProgramInput
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.ui.calendarWeeks
import com.texasprogram.app.ui.monthTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/// Серия по календарю, цель недели, объём дня, замеры веса и перенос итогов тренировки в копии.
class StreakAndGoalTest {

    private fun texas() = ProgramProfile.texas(ProgramInput.demo)

    /// Понедельник 12 октября 2026 плюс смещение в днях.
    private fun day(offset: Long): LocalDate = LocalDate.of(2026, 10, 12).plusDays(offset)

    /// Отмечает тренировки плана подряд, каждую в свою дату.
    private fun complete(profile: ProgramProfile, dates: List<LocalDate>): ProgramProfile {
        val slots = profile.workoutPlan.weeks.flatMap { week -> week.days.map { week.number to it.number } }
        var result = profile
        dates.forEachIndexed { index, date ->
            val (week, dayNumber) = slots[index]
            result = result.toggleCompleted(week, dayNumber, date)
        }
        return result
    }

    @Test
    fun noWorkoutsNoStreak() {
        val streak = texas().calendarStreak(day(2))
        assertEquals(0, streak.weeks)
        assertEquals(0, streak.workouts)
    }

    /// Неделя с одной тренировкой засчитана; тренировки суммируются по всем неделям серии.
    @Test
    fun consecutiveWeeksCount() {
        val profile = complete(texas(), listOf(day(-14), day(-12), day(-7), day(0)))
        val streak = profile.calendarStreak(day(1))
        assertEquals(3, streak.weeks)
        assertEquals(4, streak.workouts)
    }

    /// Начатая неделя без тренировки серию не обрывает — считаем от прошлой.
    @Test
    fun emptyCurrentWeekKeepsStreak() {
        val profile = complete(texas(), listOf(day(-14), day(-7)))
        assertEquals(2, profile.calendarStreak(day(3)).weeks)
    }

    /// Пропущенная целая неделя обрывает серию.
    @Test
    fun gapBreaksStreak() {
        val profile = complete(texas(), listOf(day(-21), day(-7)))
        assertEquals(1, profile.calendarStreak(day(2)).weeks)
    }

    @Test
    fun weekGoalCountsOnlyThisWeek() {
        val profile = complete(texas(), listOf(day(-3), day(0), day(2)))
        val (done, target) = profile.weekGoal(day(4))
        assertEquals(2, done)
        assertEquals(profile.weekdays.size, target)
    }

    /// С понедельника цель снова с нуля.
    @Test
    fun weekGoalResetsOnMonday() {
        val profile = complete(texas(), listOf(day(0), day(2), day(4)))
        assertEquals(0, profile.weekGoal(day(7)).first)
    }

    @Test
    fun dayTonnageComesFromLoggedSets() {
        val base = texas()
        val squat = base.workoutPlan.weeks.first().days.first().exercises.first()
        val profile = base
            .recordSet(1, 1, squat, 0, reps = 5, weight = 100.0)
            .recordSet(1, 1, squat, 1, reps = 3, weight = 110.0)
        assertEquals(830.0, profile.tonnage(profile.dayKey(1, 1)), 0.001)
        assertEquals(0.0, profile.tonnage(profile.dayKey(1, 2)), 0.001)
    }

    @Test
    fun streakCalendarCoversWholeWeeks() {
        val weeks = calendarWeeks(day(0))
        assertTrue(weeks.all { it.size == 7 })
        // Октябрь 2026: с понедельника 28 сентября по воскресенье 1 ноября.
        assertEquals(LocalDate.of(2026, 9, 28), weeks.first().first())
        assertEquals(LocalDate.of(2026, 11, 1), weeks.last().last())
        assertTrue(weeks.all { it.first().dayOfWeek == DayOfWeek.MONDAY })
        assertEquals("Октябрь 2026", monthTitle(day(0)))
    }

    /// Изменение за 30 дней — от замера на начало окна; удаление убирает замер этого дня.
    @Test
    fun bodyWeightChangeAndRemoval() {
        var profile = texas()
            .recordBodyWeight(84.0, day(-40))
            .recordBodyWeight(83.0, day(-30))
            .recordBodyWeight(81.5, day(0))
        assertEquals(-1.5, profile.bodyWeightChange(30)!!, 0.001)

        profile = profile.removeBodyWeight(day(0).toEpochDay())
        assertEquals(83.0, profile.latestBodyWeight!!, 0.001)
        assertNull(texas().recordBodyWeight(80.0, day(0)).bodyWeightChange(30))
    }

    /// Копия переносит время и усилие тренировки — раньше они терялись при восстановлении.
    @Test
    fun backupKeepsWorkoutSummary() {
        val profile = texas().toggleCompleted(1, 1, day(0), durationSeconds = 3_900, effort = "8")
        val restored = BackupService.profile(BackupService.parse(BackupService.export(listOf(profile))).profiles.first())
        val record = restored.completionLog.first()
        assertEquals(3_900, record.durationSeconds)
        assertEquals("8", record.effort)
        assertEquals(day(0).toEpochDay(), record.epochDay)
    }

    /// Исправленный после тренировки подход не стирает её время и усилие.
    @Test
    fun editingSetKeepsWorkoutSummary() {
        val base = texas().toggleCompleted(1, 1, day(0), durationSeconds = 3_600, effort = "7")
        val squat = base.workoutPlan.weeks.first().days.first().exercises.first()
        val edited = base.recordSet(1, 1, squat, 0, reps = 5, weight = 100.0, today = day(2))
        val record = edited.completionLog.first { it.key == edited.dayKey(1, 1) }
        assertEquals(3_600, record.durationSeconds)
        assertEquals("7", record.effort)
        assertEquals(day(0).toEpochDay(), record.epochDay)
    }

    /// День закрыт раньше экрана завершения — время и усилие всё равно попадают в историю.
    @Test
    fun summaryReachesAlreadyCompletedDay() {
        val profile = texas().toggleCompleted(1, 1, day(0)).withWorkoutSummary(1, 1, 2_700, "6")
        val record = profile.completionLog.first { it.key == profile.dayKey(1, 1) }
        assertEquals(2_700, record.durationSeconds)
        assertEquals("6", record.effort)
    }

    /// Расписание из листа «Цель» — с понедельника: воскресенье последнее, а не первое.
    @Test
    fun goalScheduleIsMondayFirst() {
        val profile = texas()
        val chosen = setOf(1, 3, 5)
        val ordered = com.texasprogram.app.service.RuDate.weekOrder.filter { it in chosen }
        assertEquals(listOf(3, 5, 1), ordered)
        assertEquals(listOf(3, 5, 1), profile.copy(scheduleWeekdays = ordered).weekdays)
    }
}
