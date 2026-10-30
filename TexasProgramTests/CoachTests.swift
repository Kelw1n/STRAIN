import XCTest
@testable import TexasProgram

/// ИИ-тренер: разбор ответа, проверка и применение действий, данные для тренера.
final class CoachTests: XCTestCase {
    private func profile() -> ProgramProfile {
        let profile = ProgramProfile(input: .demo)
        let squat = profile.workoutPlan.weeks[0].days[0].exercises[0]
        profile.recordSet(week: 1, day: 1, exercise: squat, index: 0, reps: 5, weight: 100)
        profile.completionLog = [CompletionRecord(key: profile.dayKey(week: 1, day: 1), date: Date().addingTimeInterval(-86_400), week: 1, day: 1, effort: "Тяжело")]
        return profile
    }

    func testParsesJsonAndPlainText() {
        let reply = Coach.parse(#"```json {"reply": "Сбрось жим.", "actions": [{"type": "stall_reset", "lift": "bench"}, {"type": "weight_step", "exercise": "Приседания", "step": "1,25"}]} ```"#)
        XCTAssertEqual(reply.text, "Сбрось жим.")
        XCTAssertEqual(reply.actions.count, 2)
        XCTAssertEqual(reply.actions[1].step, 1.25)
        XCTAssertEqual(Coach.parse("Просто текст").text, "Просто текст")
        XCTAssertTrue(Coach.parse("Просто текст").actions.isEmpty)
    }

    func testOnlySupportedActionsResolveAndApply() throws {
        let profile = profile()
        let bench = profile.bench5RM
        let reset = CoachAction(type: "stall_reset", lift: "bench")
        XCTAssertNotNil(Coach.resolve(reset, profile: profile))
        Coach.apply(reset, to: profile)
        XCTAssertLessThan(profile.bench5RM, bench)
        XCTAssertGreaterThanOrEqual(profile.bench5RM, bench * 0.85)

        let name = try XCTUnwrap(profile.loggedExerciseNames.first)
        let step = CoachAction(type: "weight_step", exercise: name.uppercased(), step: 1.25)
        XCTAssertNotNil(Coach.resolve(step, profile: profile), "название сверяется без учёта регистра")
        Coach.apply(step, to: profile)
        XCTAssertEqual(profile.weightStep(for: name), 1.25)
        XCTAssertNil(Coach.resolve(step, profile: profile), "тот же шаг второй раз не предлагается")

        XCTAssertNil(Coach.resolve(CoachAction(type: "weight_step", exercise: name, step: 3), profile: profile), "шаг не из списка")
        XCTAssertNil(Coach.resolve(CoachAction(type: "weight_step", exercise: "Выдуманное", step: 5), profile: profile))
        XCTAssertNil(Coach.resolve(CoachAction(type: "delete_program"), profile: profile))
        XCTAssertNil(Coach.resolve(CoachAction(type: "stall_reset", lift: "curl"), profile: profile))
    }

    func testContextHasProgramAndSets() {
        let text = Coach.context(for: profile(), nutrition: nil)
        XCTAssertTrue(text.contains("Программа: Техасский метод"))
        XCTAssertTrue(text.contains("100×5"))
        XCTAssertTrue(text.contains("тяжесть: Тяжело"))
        XCTAssertTrue(text.contains("Вес тела не записан"))
    }

    func testWeeklyReviewOnMondayOnce() {
        let calendar = WeeklyStatsBuilder.calendar
        let monday = calendar.date(from: DateComponents(year: 2026, month: 10, day: 26, hour: 10))!
        let profile = ProgramProfile(input: .demo)
        profile.completionLog = [CompletionRecord(key: "1-1", date: monday.addingTimeInterval(-3 * 86_400), week: 1, day: 1)]
        XCTAssertTrue(Coach.needsReview([], profile: profile, now: monday, calendar: calendar))
        let done = CoachMessage(role: .coach, text: "Разбор", reviewWeek: Coach.reviewWeek(now: monday))
        XCTAssertFalse(Coach.needsReview([done], profile: profile, now: monday, calendar: calendar))
        XCTAssertFalse(Coach.needsReview([], profile: profile, now: monday.addingTimeInterval(3 * 86_400), calendar: calendar), "в четверг не предлагаем")
    }
}
