import UserNotifications
import XCTest
@testable import TexasProgram

/// «Цель»: своё время у каждого дня, несколько напоминаний и срок.
/// Те же случаи — в TrainingGoalTest на Android.
@MainActor
final class TrainingGoalTests: XCTestCase {
    private var calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/Moscow")!
        return calendar
    }()

    private func profile() -> ProgramProfile {
        let profile = ProgramProfile(input: .demo)
        profile.reminderEnabled = true
        profile.reminderHour = 18
        profile.reminderMinute = 0
        profile.scheduleWeekdays = [2, 4, 6]
        return profile
    }

    private func components(_ request: UNNotificationRequest) -> DateComponents? {
        (request.trigger as? UNCalendarNotificationTrigger)?.dateComponents
    }

    func testOwnTimePerDay() {
        let profile = profile()
        profile.trainingTimes = ["4": 7 * 60 + 30]
        XCTAssertEqual(profile.trainingMinutes(weekday: 2), 18 * 60)
        XCTAssertEqual(profile.trainingMinutes(weekday: 4), 7 * 60 + 30)
        let requests = ReminderScheduler.plan(for: profile)
        let wednesday = requests.first { $0.identifier == "strain-workout-4" }
        XCTAssertEqual(components(wednesday!)?.hour, 7)
        XCTAssertEqual(components(wednesday!)?.minute, 30)
        XCTAssertEqual(requests.count, 3)
    }

    func testSeveralRemindersBeforeTraining() {
        let profile = profile()
        profile.reminderOffsets = [60, 0, 15, 30]
        // Не больше трёх, по порядку.
        XCTAssertEqual(profile.activeReminderOffsets, [0, 15, 30])
        let requests = ReminderScheduler.plan(for: profile).filter { $0.identifier.hasPrefix("strain-workout-2") }
        XCTAssertEqual(requests.map(\.identifier).sorted(), ["strain-workout-2", "strain-workout-2-m15", "strain-workout-2-m30"])
        let early = requests.first { $0.identifier == "strain-workout-2-m30" }!
        XCTAssertEqual(components(early)?.hour, 17)
        XCTAssertEqual(components(early)?.minute, 30)
        XCTAssertEqual(early.content.title, "Тренировка через 30 минут")
        XCTAssertEqual(requests.first { $0.identifier == "strain-workout-2" }?.content.title, "Сегодня тренировка")
    }

    func testReminderBeforeMidnightIsSkipped() {
        let profile = profile()
        profile.trainingTimes = ["2": 30]
        profile.reminderOffsets = [0, 60]
        let monday = ReminderScheduler.plan(for: profile).filter { $0.identifier.hasPrefix("strain-workout-2") }
        XCTAssertEqual(monday.map(\.identifier), ["strain-workout-2"])
    }

    func testEmptyOffsetsMeanAtStart() {
        let profile = profile()
        profile.reminderOffsets = []
        XCTAssertEqual(profile.activeReminderOffsets, [0])
    }

    func testGoalTerm() throws {
        let profile = profile()
        XCTAssertNil(profile.goalWeek(calendar: calendar))
        let start = try XCTUnwrap(calendar.date(from: DateComponents(year: 2026, month: 10, day: 11)))
        profile.goalStart = start
        profile.goalMonths = 3
        let end = try XCTUnwrap(profile.goalEnd(calendar: calendar))
        XCTAssertEqual(calendar.dateComponents([.month, .day], from: end), DateComponents(month: 1, day: 11))
        let now = try XCTUnwrap(calendar.date(from: DateComponents(year: 2026, month: 10, day: 26)))
        let term = try XCTUnwrap(profile.goalWeek(now: now, calendar: calendar))
        XCTAssertEqual(term.week, 3)
        XCTAssertEqual(term.total, 14)
        let before = try XCTUnwrap(calendar.date(from: DateComponents(year: 2026, month: 10, day: 1)))
        XCTAssertNil(profile.goalWeek(now: before, calendar: calendar))
        let after = try XCTUnwrap(calendar.date(from: DateComponents(year: 2027, month: 6, day: 1)))
        XCTAssertEqual(profile.goalWeek(now: after, calendar: calendar)?.week, 14)
    }

    func testTitles() {
        XCTAssertEqual(TrainingGoal.offsetTitle(0), "В начале тренировки")
        XCTAssertEqual(TrainingGoal.offsetTitle(60), "За 1 час")
        XCTAssertEqual(TrainingGoal.monthsTitle(0), "Без срока")
        XCTAssertEqual(TrainingGoal.monthsTitle(3), "3 месяца")
        XCTAssertEqual(TrainingGoal.monthsTitle(6), "6 месяцев")
    }
}
