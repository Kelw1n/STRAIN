import XCTest
@testable import TexasProgram

/// Лента: достижения, программа в профиле, разбор ответов сервера и подписи.
final class SocialTests: XCTestCase {

    private func texas() -> ProgramProfile { ProgramProfile(input: .demo) }

    private var calendar: Calendar { WorkoutScheduler.trainingCalendar() }

    private func day(_ offset: Int) -> Date {
        let monday = calendar.date(from: DateComponents(year: 2026, month: 10, day: 12, hour: 12))!
        return calendar.date(byAdding: .day, value: offset, to: monday)!
    }

    private func progress(_ profile: ProgramProfile, _ kind: AchievementKind) -> AchievementProgress {
        AchievementCalculator.progress(for: profile).first { $0.kind == kind }!
    }

    // MARK: Достижения

    func testFirstWorkoutGivesFirstPathLevel() {
        let profile = texas()
        XCTAssertEqual(progress(profile, .path).level, 0)
        profile.toggleCompleted(week: 1, day: 1, now: day(0))
        let path = progress(profile, .path)
        XCTAssertEqual(path.level, 1)
        XCTAssertEqual(path.nextThreshold, 15)
        XCTAssertEqual(path.caption, "1 из 15 · тренировок всего")
    }

    func testStrengthNeedsBodyWeight() {
        let profile = texas()
        let bench = progress(profile, .bench)
        XCTAssertTrue(bench.needsBodyWeight)
        XCTAssertEqual(bench.level, 0)

        profile.recordBodyWeight(profile.bench5RM, on: day(0))
        let measured = progress(profile, .bench)
        XCTAssertFalse(measured.needsBodyWeight)
        XCTAssertEqual(measured.value, 1, accuracy: 0.0001, "жим своего веса — ×1")
        XCTAssertEqual(measured.level, 2)
    }

    /// Рекорд — превышение прошлого лучшего веса; первая тренировка движения рекордом не считается.
    func testRecordsCountOnlyImprovements() throws {
        let profile = texas()
        let squat = try XCTUnwrap(profile.workoutPlan.weeks[0].days[0].exercises.first)
        profile.recordSet(week: 1, day: 1, exercise: squat, index: 0, reps: 5, weight: 100)
        XCTAssertEqual(AchievementCalculator.recordCount(profile), 0)
        let later = try XCTUnwrap(profile.workoutPlan.weeks[1].days[0].exercises.first { $0.name == squat.name })
        profile.recordSet(week: 2, day: 1, exercise: later, index: 0, reps: 5, weight: 105)
        XCTAssertEqual(AchievementCalculator.recordCount(profile), 1)
    }

    func testBestWeekStreak() {
        let dates = [day(-21), day(-14), day(-12), day(-7), day(7), day(14)]
        XCTAssertEqual(AchievementCalculator.bestWeekStreak(dates, calendar: calendar), 3)
        XCTAssertEqual(AchievementCalculator.bestWeekStreak([], calendar: calendar), 0)
    }

    func testBadgesAreTopThreeLevels() {
        let progress = [
            AchievementProgress(kind: .path, value: 20, needsBodyWeight: false),
            AchievementProgress(kind: .iron, value: 0, needsBodyWeight: false),
            AchievementProgress(kind: .records, value: 5, needsBodyWeight: false),
            AchievementProgress(kind: .streak, value: 8, needsBodyWeight: false),
            AchievementProgress(kind: .bench, value: 1, needsBodyWeight: false)
        ]
        let badges = AchievementCalculator.badges(from: progress)
        XCTAssertEqual(badges.map(\.id), ["streak", "path", "records"])
        XCTAssertEqual(badges.first?.level, 3)
    }

    func testProgramDaysForProfile() {
        let days = SocialProgramBuilder.days(for: texas())
        XCTAssertEqual(days.count, 3)
        XCTAssertFalse(days[0].exercises.isEmpty)
        XCTAssertLessThanOrEqual(days[0].groups.count, 2)
        XCTAssertEqual(days[0].setCount, days[0].exercises.reduce(0) { $0 + $1.sets })
    }

    // MARK: Ответы сервера

    func testPostDecodesWithMissingFields() throws {
        let json = """
        {"id":"p1","author":{"id":"u1","username":"tim"},"createdAt":1792000000000,
         "title":"Верх","volumeKg":1470,"durationSeconds":6480,
         "workout":{"exercises":[{"name":"Жим лёжа","sets":[{"reps":5,"weight":80},{"reps":5,"weight":85,"record":true}]}]}}
        """
        let post = try JSONDecoder().decode(SocialPost.self, from: Data(json.utf8))
        XCTAssertEqual(post.description, "")
        XCTAssertEqual(post.photos, [])
        XCTAssertEqual(post.likes, 0)
        XCTAssertFalse(post.liked)
        XCTAssertEqual(post.workout.exercises.first?.best?.weight, 85)
        XCTAssertTrue(post.workout.exercises.first?.hasRecord ?? false)
        XCTAssertEqual(post.density, 1470 / 108, accuracy: 0.01)
    }

    func testBrokenWorkoutDoesNotDropPost() throws {
        let json = """
        {"id":"p2","author":{"id":"u1","username":"tim"},"createdAt":0,"workout":"не тренировка"}
        """
        let post = try JSONDecoder().decode(SocialPost.self, from: Data(json.utf8))
        XCTAssertTrue(post.workout.exercises.isEmpty)
        XCTAssertEqual(post.title, "Тренировка")
    }

    func testUserHidesBodyLineWhenEmpty() throws {
        let hidden = try JSONDecoder().decode(SocialUser.self, from: Data(#"{"id":"u","username":"a"}"#.utf8))
        XCTAssertNil(hidden.bodyLine)
        let shown = try JSONDecoder().decode(SocialUser.self, from: Data(#"{"id":"u","username":"a","heightCm":178,"weightKg":75}"#.utf8))
        XCTAssertEqual(shown.bodyLine, "178 см · 75 кг")
    }

    // MARK: Подписи

    func testUsernameRules() {
        XCTAssertEqual(SocialFormat.normalizedUsername(" @Kelw1n "), "kelw1n")
        XCTAssertTrue(SocialFormat.isValidUsername("kelw1n"))
        XCTAssertTrue(SocialFormat.isValidUsername("tim_g.2008"))
        XCTAssertFalse(SocialFormat.isValidUsername("ab"))
        XCTAssertFalse(SocialFormat.isValidUsername("тимур"))
        XCTAssertFalse(SocialFormat.isValidUsername("with space"))
    }

    func testAgoAndDuration() {
        let now = day(0)
        XCTAssertEqual(SocialFormat.ago(now.addingTimeInterval(-20), now: now, calendar: calendar), "только что")
        XCTAssertEqual(SocialFormat.ago(now.addingTimeInterval(-600), now: now, calendar: calendar), "10 минут назад")
        XCTAssertEqual(SocialFormat.ago(now.addingTimeInterval(-3 * 3600), now: now, calendar: calendar), "3 часа назад")
        XCTAssertEqual(SocialFormat.ago(day(-1), now: now, calendar: calendar), "вчера")
        XCTAssertEqual(SocialFormat.duration(6480), "1ч 48м")
        XCTAssertEqual(SocialFormat.duration(45 * 60), "45м")
    }

    // MARK: Копия

    func testBackupCarriesSocialToken() throws {
        let token = String(repeating: "a", count: 64)
        let archive = try BackupService.archive(from: try BackupService.export([texas()], socialToken: token))
        XCTAssertEqual(archive.socialToken, token)
        let without = try BackupService.archive(from: try BackupService.export([texas()], socialToken: nil))
        XCTAssertNil(without.socialToken)
    }
}
