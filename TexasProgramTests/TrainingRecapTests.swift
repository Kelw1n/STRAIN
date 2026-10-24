import XCTest
@testable import TexasProgram

/// Итоги месяца и года: счёт тренировок, объёма, рекордов и серий.
final class TrainingRecapTests: XCTestCase {
    private var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        calendar.firstWeekday = 2
        return calendar
    }

    private func date(_ year: Int, _ month: Int, _ day: Int) -> Date {
        calendar.date(from: DateComponents(year: year, month: month, day: day, hour: 18))!
    }

    private func record(_ key: String, _ date: Date, minutes: Int = 60) -> CompletionRecord {
        CompletionRecord(key: key, date: date, week: 1, day: 1, durationSeconds: minutes * 60)
    }

    func testMonthTotalsRecordsAndFavorites() {
        let log = [
            record("1-1", date(2026, 9, 28)),          // сентябрь: прошлый период
            record("1-2", date(2026, 10, 6), minutes: 70),
            record("1-3", date(2026, 10, 8), minutes: 50),
            record("2-1", date(2026, 10, 20), minutes: 65)
        ]
        let sets: [String: SetEntry] = [
            "1-1|squat|0": SetEntry(reps: 5, weight: 100),
            "1-2|squat|0": SetEntry(reps: 5, weight: 100),   // не выше прошлого — не рекорд
            "1-2|squat|1": SetEntry(reps: 5, weight: 100),
            "1-3|bench|0": SetEntry(reps: 5, weight: 80),    // первый раз — не рекорд
            "2-1|squat|0": SetEntry(reps: 3, weight: 110),   // выше всех прошлых — рекорд
            "2-1|bench|0": SetEntry(reps: 5, weight: 82.5)   // и это рекорд
        ]
        let recap = TrainingRecap.build(
            log: log, setLog: sets, names: ["squat": "Присед", "bench": "Жим лёжа"],
            period: .month(year: 2026, month: 10), calendar: calendar
        )
        XCTAssertEqual(recap.workouts, 3)
        XCTAssertEqual(recap.seconds, (70 + 50 + 65) * 60)
        XCTAssertEqual(recap.sets, 5)
        XCTAssertEqual(recap.reps, 23)
        XCTAssertEqual(recap.volumeKg, 500 + 500 + 400 + 330 + 412.5, accuracy: 0.01)
        XCTAssertEqual(recap.records, 2)
        XCTAssertEqual(recap.favorite?.name, "Присед", "присед — две тренировки, жим — две, но присед тяжелее по объёму")
        XCTAssertEqual(recap.heaviest?.name, "Присед")
        XCTAssertEqual(recap.heaviest?.bestWeight, 110)
        XCTAssertEqual(recap.topByVolume.first?.name, "Присед")
        XCTAssertEqual(recap.previousWorkouts, 1)
        XCTAssertEqual(recap.previousVolumeKg, 500)
        XCTAssertEqual(recap.volumeChange, Int(((2142.5 - 500) / 500 * 100).rounded()))
        // 6 и 8 октября — одна неделя, 20-е — через неделю: две недели, серии нет.
        XCTAssertEqual(recap.activeWeeks, 2)
        XCTAssertEqual(recap.longestWeekStreak, 1)
        XCTAssertEqual(recap.buckets.reduce(0, +), 3)
        XCTAssertEqual(recap.busiestWeekday, 3, "6-е и 20-е — вторники")
    }

    func testYearBucketsAndStreak() {
        let log = [
            record("a", date(2026, 1, 5)), record("b", date(2026, 1, 12)), record("c", date(2026, 1, 19)),
            record("d", date(2026, 3, 2))
        ]
        let recap = TrainingRecap.build(log: log, setLog: [:], period: .year(2026), calendar: calendar)
        XCTAssertEqual(recap.buckets.count, 12)
        XCTAssertEqual(recap.buckets[0], 3)
        XCTAssertEqual(recap.buckets[2], 1)
        XCTAssertEqual(recap.longestWeekStreak, 3, "три недели января подряд")
        XCTAssertNil(recap.favorite)
        XCTAssertEqual(recap.volumeKg, 0)
    }

    func testPeriodsOnlyWithWorkouts() {
        let now = date(2026, 10, 20)
        let log = [record("x", date(2026, 10, 2)), record("y", date(2025, 6, 1))]
        let periods = TrainingRecap.periods(log, now: now, calendar: calendar)
        XCTAssertEqual(periods, [.month(year: 2026, month: 10), .year(2026), .year(2025)], "сентябрь пустой — его нет")
        XCTAssertEqual(TrainingRecap.Period.month(year: 2026, month: 1).previous, .month(year: 2025, month: 12))
        XCTAssertEqual(TrainingRecap.Period.month(year: 2026, month: 10).title, "Октябрь 2026")
        XCTAssertEqual(TrainingRecap.Period.month(year: 2026, month: 10).inPhrase, "в октябре")
    }

    func testVolumeComparison() {
        func recap(_ kg: Double) -> TrainingRecap {
            TrainingRecap.build(log: [record("1-1", date(2026, 10, 1))], setLog: ["1-1|x|0": SetEntry(reps: 1, weight: kg)],
                                period: .month(year: 2026, month: 10), calendar: calendar)
        }
        XCTAssertEqual(recap(12_000).volumeComparison, "как 2 слона")
        XCTAssertEqual(recap(4_500).volumeComparison, "как 3 легковые машины")
        XCTAssertEqual(recap(250).volumeComparison, "как 5 мешков цемента")
        XCTAssertNil(recap(20).volumeComparison)
    }
}
