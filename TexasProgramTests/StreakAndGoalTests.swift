import XCTest
@testable import TexasProgram

/// Серия по календарю, цель недели и объём дня из записанных подходов.
final class StreakAndGoalTests: XCTestCase {

    private func texas() -> ProgramProfile { ProgramProfile(input: .demo) }

    private var calendar: Calendar { WorkoutScheduler.trainingCalendar() }

    /// Дата: понедельник 12 октября 2026 плюс смещение в днях, полдень — без краёв суток.
    private func day(_ offset: Int) -> Date {
        let monday = calendar.date(from: DateComponents(year: 2026, month: 10, day: 12, hour: 12))!
        return calendar.date(byAdding: .day, value: offset, to: monday)!
    }

    /// Отмечает тренировки плана подряд, каждую в свою дату.
    private func complete(_ profile: ProgramProfile, on dates: [Date]) {
        var slots: [(Int, Int)] = []
        for week in profile.workoutPlan.weeks {
            for plan in week.days { slots.append((week.number, plan.number)) }
        }
        for (index, date) in dates.enumerated() {
            let (week, dayNumber) = slots[index]
            profile.toggleCompleted(week: week, day: dayNumber, now: date)
        }
    }

    func testNoWorkoutsNoStreak() {
        let streak = texas().calendarStreak(now: day(2))
        XCTAssertEqual(streak.weeks, 0)
        XCTAssertEqual(streak.workouts, 0)
    }

    /// Неделя с одной тренировкой засчитана; тренировки суммируются по всем неделям серии.
    func testConsecutiveWeeksCount() {
        let profile = texas()
        complete(profile, on: [day(-14), day(-12), day(-7), day(0)])
        let streak = profile.calendarStreak(now: day(1))
        XCTAssertEqual(streak.weeks, 3)
        XCTAssertEqual(streak.workouts, 4)
    }

    /// Начатая неделя без тренировки серию не обрывает — считаем от прошлой.
    func testEmptyCurrentWeekKeepsStreak() {
        let profile = texas()
        complete(profile, on: [day(-14), day(-7)])
        XCTAssertEqual(profile.calendarStreak(now: day(3)).weeks, 2)
    }

    /// Пропущенная целая неделя обрывает серию.
    func testGapBreaksStreak() {
        let profile = texas()
        complete(profile, on: [day(-21), day(-7)])
        XCTAssertEqual(profile.calendarStreak(now: day(2)).weeks, 1)
    }

    func testWeekGoalCountsOnlyThisWeek() {
        let profile = texas()
        complete(profile, on: [day(-3), day(0), day(2)])
        let goal = profile.weekGoal(now: day(4))
        XCTAssertEqual(goal.done, 2)
        XCTAssertEqual(goal.target, profile.weekdays.count)
    }

    /// С понедельника цель снова с нуля.
    func testWeekGoalResetsOnMonday() {
        let profile = texas()
        complete(profile, on: [day(0), day(2), day(4)])
        XCTAssertEqual(profile.weekGoal(now: day(7)).done, 0)
    }

    func testDayTonnageComesFromLoggedSets() throws {
        let profile = texas()
        let squat = try XCTUnwrap(profile.workoutPlan.weeks.first?.days.first?.exercises.first)
        profile.recordSet(week: 1, day: 1, exercise: squat, index: 0, reps: 5, weight: 100)
        profile.recordSet(week: 1, day: 1, exercise: squat, index: 1, reps: 3, weight: 110)
        XCTAssertEqual(profile.tonnage(forDayKey: profile.dayKey(week: 1, day: 1)), 830, accuracy: 0.001)
        XCTAssertEqual(profile.tonnage(forDayKey: profile.dayKey(week: 1, day: 2)), 0)
    }

    func testStreakCalendarCoversWholeWeeks() {
        let weeks = StreakCalendarSheet.weeks(of: day(0), calendar: calendar)
        XCTAssertTrue(weeks.allSatisfy { $0.count == 7 })
        // Октябрь 2026: с понедельника 28 сентября по воскресенье 1 ноября.
        XCTAssertEqual(calendar.component(.day, from: weeks.first!.first!), 28)
        XCTAssertEqual(calendar.component(.day, from: weeks.last!.last!), 1)
        XCTAssertEqual(StreakCalendarSheet.monthTitle(day(0), calendar: calendar), "Октябрь 2026")
    }
}
