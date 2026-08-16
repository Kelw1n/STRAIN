package com.texasprogram.app

import com.texasprogram.app.model.ProgramInput
import com.texasprogram.app.model.ProgramProfile
import org.junit.Assert.assertEquals
import org.junit.Test

/// Неделя, на которой открывается план.
class CurrentWeekTest {

    private fun texas() = ProgramProfile.texas(ProgramInput.demo)

    /// Закрывает всю неделю целиком.
    private fun ProgramProfile.closeWeek(week: Int): ProgramProfile {
        val days = workoutPlan.weeks.first { it.number == week }.days
        return days.fold(this) { profile, day -> profile.toggleCompleted(week, day.number) }
    }

    @Test
    fun freshProfileStartsOnTheFirstWeek() {
        assertEquals(1, texas().currentWeek)
    }

    /// Тот самый случай: понедельник и среда отмечены, пятница нет.
    /// Неделя не закрыта — значит она и текущая, какой бы день ни был на календаре.
    @Test
    fun weekWithAnUnmarkedDayStaysCurrent() {
        val profile = texas().toggleCompleted(1, 1).toggleCompleted(1, 2)
        assertEquals(1, profile.currentWeek)
    }

    @Test
    fun closedWeekMovesToTheNextOne() {
        assertEquals(2, texas().closeWeek(1).currentWeek)
        assertEquals(3, texas().closeWeek(1).closeWeek(2).currentWeek)
    }

    /// Пропуск отмечен сознательно — он неделю не держит.
    @Test
    fun deliberateSkipDoesNotHoldTheWeek() {
        val profile = texas()
            .toggleCompleted(1, 1)
            .toggleCompleted(1, 2)
            .markSkipped(1, 3, holdsProgression = true)

        assertEquals(2, profile.currentWeek)
    }

    /// Дыра в середине важнее свежих отметок: возвращаемся к незакрытой неделе.
    @Test
    fun anOlderUnfinishedWeekWinsOverALaterOne() {
        val profile = texas().closeWeek(1).closeWeek(2).toggleCompleted(2, 1)
        assertEquals(2, profile.currentWeek)
    }

    @Test
    fun finishedProgrammeStaysOnTheLastWeek() {
        val plan = texas().workoutPlan
        val profile = plan.weeks.fold(texas()) { value, week -> value.closeWeek(week.number) }
        assertEquals(plan.weeks.last().number, profile.currentWeek)
    }
}
