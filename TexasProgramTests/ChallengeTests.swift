import XCTest
@testable import TexasProgram

/// Счёт челленджей: та же история, что в тесте Android, — счёт обязан совпасть.
final class ChallengeTests: XCTestCase {
    private let calendar = WeeklyStatsBuilder.calendar

    private func date(_ month: Int, _ day: Int, hour: Int = 18) -> Date {
        calendar.date(from: DateComponents(year: 2026, month: month, day: day, hour: hour))!
    }

    private var log: [CompletionRecord] {
        [
            CompletionRecord(key: "1-1", date: date(10, 5), week: 1, day: 1),
            CompletionRecord(key: "1-2", date: date(10, 7), week: 1, day: 2),
            // Повтор той же тренировки: считается один раз, по последней дате.
            CompletionRecord(key: "1-3", date: date(10, 8), week: 1, day: 3),
            CompletionRecord(key: "1-3", date: date(10, 9), week: 1, day: 3),
            CompletionRecord(key: "2-1", date: date(10, 12), week: 2, day: 1),
            CompletionRecord(key: "c2-1-1", date: date(10, 26), week: 1, day: 1)
        ]
    }

    private let sets: [String: SetEntry] = [
        "1-1|Приседания|0": SetEntry(reps: 5, weight: 100),
        "1-1|Приседания|1": SetEntry(reps: 5, weight: 100),
        "1-1|Жим лёжа|0": SetEntry(reps: 5, weight: 80),
        "1-2|Приседания|0": SetEntry(reps: 5, weight: 102.5),
        "2-1|Жим лёжа|0": SetEntry(reps: 3, weight: 85),
        "2-1|Жим лёжа|1": SetEntry(reps: 0, weight: 85),
        "c2-1-1|Приседания|0": SetEntry(reps: 5, weight: 100),
        "3-1|Приседания|0": SetEntry(reps: 5, weight: 200)
    ]

    private func tonnage(_ key: String) -> Double {
        sets.filter { $0.key.hasPrefix(key + "|") }.values.reduce(0) { $0 + Double($1.reps) * $1.weight }
    }

    private func result(_ kind: ChallengeKind, start: Date, weeks: Int, bodyWeight: Double? = 80) -> ChallengeScoring.Result {
        ChallengeScoring.result(
            kind: kind, log: log, sets: sets, tonnage: tonnage, bodyWeight: bodyWeight,
            weeklyGoal: 3, start: start, weeks: weeks, now: date(10, 20, hour: 12), calendar: calendar
        )
    }

    func testScoresMatchAndroid() {
        let start = date(10, 5, hour: 0)
        XCTAssertEqual(result(.workouts, start: start, weeks: 2), .init(score: 4, detail: "4 тренировки"))
        XCTAssertEqual(result(.volume, start: start, weeks: 2), .init(score: 2167.5, detail: "2,2 т"))
        // Первая неделя — три тренировки из трёх, вторая — одна: серия остановилась.
        XCTAssertEqual(result(.streak, start: start, weeks: 2), .init(score: 104, detail: "1 из 2 нед."))
        // Жим: 85 × 3 после 80 × 5 — расчётный максимум вырос на 0,17 кг.
        let week2 = date(10, 12, hour: 0)
        XCTAssertEqual(result(.strength, start: week2, weeks: 1), .init(score: 0.2, detail: "+0,2 кг · 0,2% веса"))
        XCTAssertEqual(result(.strength, start: week2, weeks: 1, bodyWeight: nil), .init(score: 0, detail: "+0,2 кг · нет веса тела"))
        XCTAssertEqual(result(.workouts, start: date(10, 26, hour: 0), weeks: 1).score, 1)
    }

    func testPlaceExperience() {
        XCTAssertEqual(ChallengeScoring.xp(place: 1, participants: 2, score: 5), 300)
        XCTAssertEqual(ChallengeScoring.xp(place: 2, participants: 3, score: 5), 200)
        XCTAssertEqual(ChallengeScoring.xp(place: 3, participants: 3, score: 5), 100)
        XCTAssertEqual(ChallengeScoring.xp(place: 4, participants: 5, score: 1), 50)
        XCTAssertEqual(ChallengeScoring.xp(place: 1, participants: 1, score: 5), 0, "в одиночку не считается")
        XCTAssertEqual(ChallengeScoring.xp(place: 1, participants: 3, score: 0), 0, "без единой тренировки — без опыта")
        XCTAssertEqual(ChallengeScoring.decimal(2), "2")
        XCTAssertEqual(ChallengeScoring.decimal(12.34), "12,3")
    }

    func testChallengeDecodesWithUnknownKind() throws {
        let list = try JSONDecoder().decode([SocialChallenge].self, from: Data(#"""
        [{"id":"c1","kind":"workouts","weeks":2,"startsAt":1000,"endsAt":2000,"status":"active","myStatus":"joined",
          "owner":{"id":"u1","username":"tim"},
          "members":[{"user":{"id":"u2","username":"sem","level":7},"status":"joined","score":3,"detail":"3 тренировки","place":1},
                     {"user":{"id":"u1","username":"tim","isMe":true},"status":"joined","score":2,"detail":"2 тренировки","place":2},
                     {"user":{"id":"u3","username":"new"},"status":"invited"}]},
         {"id":"c2","kind":"future","weeks":1,"startsAt":1000,"endsAt":2000,"status":"finished","members":[]}]
        """#.utf8))
        XCTAssertEqual(list.count, 2)
        XCTAssertEqual(list[0].kind, .workouts)
        XCTAssertEqual(list[0].me?.place, 2)
        XCTAssertEqual(list[0].joined.count, 2)
        XCTAssertEqual(list[0].invited.map(\.user.username), ["new"])
        XCTAssertEqual(list[0].members[0].user.level, 7)
        XCTAssertNil(list[1].kind, "челлендж нового вида не ломает список")
        XCTAssertEqual(list[1].title, "Челлендж")
    }

    func testAwardsAddExperienceAndTravelInBackup() throws {
        let profile = ProgramProfile(input: .demo)
        let base = Progression.xp(for: profile)
        profile.challengeAwards = [ChallengeAward(id: "c1", title: "Больше тренировок", place: 1, xp: 300, epochDay: 20_400)]
        XCTAssertEqual(Progression.xp(for: profile), base + 300)
        let archive = try BackupService.archive(from: try BackupService.export([profile], socialToken: nil))
        XCTAssertEqual(archive.profiles.first?.challengeAwards?.first?.xp, 300)
    }
}
