import XCTest
@testable import TexasProgram

/// Рекорды в профиле: лучший подход движения за все циклы, присед/жим/становая
/// и сумма. Те же числа считает Android.
final class PersonalRecordsTests: XCTestCase {
    func testEstimateAndLifts() {
        XCTAssertEqual(PersonalRecords.estimate(weight: 140, reps: 5), 163.5)
        XCTAssertEqual(PersonalRecords.estimate(weight: 100, reps: 1), 100)
        XCTAssertEqual(PersonalRecords.estimate(weight: 60, reps: 20), 84, "дальше 12 повторов не растёт")
        XCTAssertEqual(PersonalRecords.lift(for: "Приседания"), .squat)
        XCTAssertEqual(PersonalRecords.lift(for: "Жим лёжа"), .bench)
        XCTAssertEqual(PersonalRecords.lift(for: "Становая тяга"), .deadlift)
        XCTAssertNil(PersonalRecords.lift(for: "Фронтальные приседания"))
        XCTAssertNil(PersonalRecords.lift(for: "Жим стоя"))
        XCTAssertNil(PersonalRecords.lift(for: "Жим ногами"))
        XCTAssertNil(PersonalRecords.lift(for: "Румынская тяга"))
    }

    func testBestSetAcrossCyclesOnlyFromCompletedDays() throws {
        let first = Date(timeIntervalSince1970: 1_760_000_000)
        let second = Date(timeIntervalSince1970: 1_761_000_000)
        let log = [
            CompletionRecord(key: "1-1", date: first, week: 1, day: 1),
            CompletionRecord(key: "c2-1-1", date: second, week: 1, day: 1)
        ]
        var sets: [String: SetEntry] = [
            "1-1|Приседания|0": SetEntry(reps: 5, weight: 140),
            "1-1|Приседания|1": SetEntry(reps: 3, weight: 145),
            "c2-1-1|Приседания|0": SetEntry(reps: 2, weight: 150),
            "c2-1-1|Жим лёжа|0": SetEntry(reps: 5, weight: 100),
            "c2-1-1|custom-abc|0": SetEntry(reps: 10, weight: 20),
            "c2-1-1|own-lost|0": SetEntry(reps: 10, weight: 40),
            "2-1|Становая тяга|0": SetEntry(reps: 1, weight: 200)
        ]
        let names = ["custom-abc": "Молотки"]
        var records = PersonalRecords.records(log: log, sets: sets, names: names)
        XCTAssertEqual(records.map(\.name), ["Приседания", "Жим лёжа", "Молотки"], "незакрытый день и потерянное упражнение не в счёт")
        let squat = try XCTUnwrap(PersonalRecords.best(records, lift: .squat))
        XCTAssertEqual(squat.setText, "140 × 5")
        XCTAssertEqual(squat.estimate, 163.5)
        XCTAssertEqual(squat.date, first.timeIntervalSince1970 * 1000)
        XCTAssertNil(PersonalRecords.total(records), "без становой суммы нет")

        sets["c2-1-1|Становая тяга|0"] = SetEntry(reps: 3, weight: 180)
        records = PersonalRecords.records(log: log, sets: sets, names: names)
        XCTAssertEqual(PersonalRecords.total(records), 163.5 + 116.5 + 198)
    }

    func testRecordsTravelInProfile() throws {
        let user = try JSONDecoder().decode(SocialUser.self, from: Data("""
        {"id":"u","username":"tim","records":[{"name":"Жим лёжа","weight":100,"reps":5,"estimate":116.5,"date":1761500000000,"lift":"bench"},
                                              {"name":"Подтягивания","weight":20,"reps":8}]}
        """.utf8))
        XCTAssertEqual(user.records.count, 2)
        XCTAssertEqual(user.records[1].estimate, 25.5, "старый формат без максимума — считаем сами")
        XCTAssertEqual(PersonalRecords.best(user.records, lift: .bench)?.weight, 100)
    }
}
