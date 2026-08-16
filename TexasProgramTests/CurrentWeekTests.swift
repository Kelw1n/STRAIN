import XCTest
@testable import TexasProgram

/// Неделя, на которой открывается план.
final class CurrentWeekTests: XCTestCase {

    private func texas() -> ProgramProfile { ProgramProfile(input: .demo) }

    /// Закрывает всю неделю целиком.
    private func closeWeek(_ week: Int, in profile: ProgramProfile) {
        let days = profile.workoutPlan.weeks.first { $0.number == week }?.days ?? []
        for day in days {
            profile.toggleCompleted(week: week, day: day.number)
        }
    }

    func testFreshProfileStartsOnTheFirstWeek() {
        XCTAssertEqual(texas().currentWeek, 1)
    }

    /// Тот самый случай: понедельник и среда отмечены, пятница нет.
    /// Неделя не закрыта — значит она и текущая, какой бы день ни был на календаре.
    func testWeekWithAnUnmarkedDayStaysCurrent() {
        let profile = texas()
        profile.toggleCompleted(week: 1, day: 1)
        profile.toggleCompleted(week: 1, day: 2)

        XCTAssertEqual(profile.currentWeek, 1)
    }

    func testClosedWeekMovesToTheNextOne() {
        let profile = texas()
        closeWeek(1, in: profile)
        XCTAssertEqual(profile.currentWeek, 2)

        closeWeek(2, in: profile)
        XCTAssertEqual(profile.currentWeek, 3)
    }

    /// Пропуск отмечен сознательно — он неделю не держит.
    func testDeliberateSkipDoesNotHoldTheWeek() {
        let profile = texas()
        profile.toggleCompleted(week: 1, day: 1)
        profile.toggleCompleted(week: 1, day: 2)
        profile.markSkipped(week: 1, day: 3, holdsProgression: true)

        XCTAssertEqual(profile.currentWeek, 2)
    }

    /// Дыра в середине важнее свежих отметок: возвращаемся к незакрытой неделе.
    func testAnOlderUnfinishedWeekWinsOverALaterOne() {
        let profile = texas()
        closeWeek(1, in: profile)
        closeWeek(2, in: profile)
        profile.toggleCompleted(week: 2, day: 1)

        XCTAssertEqual(profile.currentWeek, 2)
    }

    func testFinishedProgrammeStaysOnTheLastWeek() throws {
        let profile = texas()
        for week in profile.workoutPlan.weeks {
            closeWeek(week.number, in: profile)
        }
        let last = try XCTUnwrap(profile.workoutPlan.weeks.last?.number)

        XCTAssertEqual(profile.currentWeek, last)
    }
}
