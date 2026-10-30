import XCTest
@testable import TexasProgram

/// Карта тренировок за год: тяжесть дня 0–4 по тоннажу, сдвиг старой карты
/// друга на сегодня. Те же числа считает Android.
final class TrainingHeatmapTests: XCTestCase {
    private var calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar
    }()

    /// Среда, 28 октября 2026.
    private var today: Date { calendar.date(from: DateComponents(year: 2026, month: 10, day: 28, hour: 12))! }

    private func day(_ back: Int) -> Date { calendar.date(byAdding: .day, value: -back, to: today)! }

    func testLevelsFollowTonnage() {
        // Тоннаж 100, 200, 300, 400 и 1000 кг; эталон — 400 (девять десятых).
        let tonnages = [4: 100.0, 3: 200.0, 2: 300.0, 1: 400.0, 0: 1000.0]
        var log = tonnages.keys.map { CompletionRecord(key: "k\($0)", date: day($0), week: 1, day: 1) }
        var sets: [String: SetEntry] = [:]
        for (back, tonnage) in tonnages { sets["k\(back)|Приседания|0"] = SetEntry(reps: 10, weight: tonnage / 10) }
        log.append(CompletionRecord(key: "empty", date: day(6), week: 1, day: 2))
        log.append(CompletionRecord(key: "old", date: day(400), week: 1, day: 3))
        sets["open|Жим лёжа|0"] = SetEntry(reps: 5, weight: 100)

        let heatmap = TrainingHeatmap.build(log: log, sets: sets, today: today, calendar: calendar)
        XCTAssertEqual(heatmap.end, "2026-10-28")
        XCTAssertEqual(heatmap.days.count, TrainingHeatmap.length)
        XCTAssertEqual(String(heatmap.days.suffix(7)), "1012344", "без подходов — бледно, тяжелее эталона — ярче всего")
        XCTAssertEqual(heatmap.days.filter { $0 != "0" }.count, 6, "год назад и незакрытый день не в счёт")
        XCTAssertEqual(TrainingHeatmap.workouts(TrainingHeatmap.levels(heatmap, today: today, calendar: calendar)), 6)
    }

    func testFriendsOldMapShiftsToToday() {
        let old = SocialHeatmap(end: "2026-10-25", days: String(repeating: "0", count: 370) + "4")
        let levels = TrainingHeatmap.levels(old, today: today, calendar: calendar)
        XCTAssertEqual(levels.count, TrainingHeatmap.length)
        XCTAssertEqual(Array(levels.suffix(4)), [4, 0, 0, 0], "тренировка три дня назад")
        let short = SocialHeatmap(end: "2026-10-28", days: "21")
        XCTAssertEqual(Array(TrainingHeatmap.levels(short, today: today, calendar: calendar).suffix(3)), [0, 2, 1])
    }

    func testVisibleWeeksStartOnMonday() {
        XCTAssertEqual(TrainingHeatmap.visibleCount(today: today, calendar: calendar), 367, "среда: 52 недели и три дня")
        XCTAssertEqual(TrainingHeatmap.visibleCount(today: day(2), calendar: calendar), 365, "понедельник")
        XCTAssertEqual(TrainingHeatmap.visibleCount(today: day(3), calendar: calendar), 371, "воскресенье")
    }
}
