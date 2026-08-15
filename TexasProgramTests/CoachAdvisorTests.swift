import XCTest
@testable import TexasProgram

/// Сбор данных для разбора: что уходит в запрос, а что остаётся дома.
final class CoachAdvisorTests: XCTestCase {

    private func profile() -> ProgramProfile { ProgramProfile(input: .demo) }

    func testEmptyProfileHasNothingToAnalyze() {
        XCTAssertFalse(CoachAdvisor.hasData(profile()))

        let withNote = profile()
        withNote.setNote("норм", week: 1, day: 1)
        XCTAssertTrue(CoachAdvisor.hasData(withNote))
    }

    /// Ключи вида «3-2» модель видеть не должна — только человеческий текст.
    func testNotesAreRenderedWithReadableDays() {
        let value = profile()
        value.setNote("болело плечо", week: 3, day: 2)

        let text = CoachAdvisor.context(for: value)
        XCTAssertTrue(text.contains("неделя 3, день 2: болело плечо"), text)
        XCTAssertFalse(text.contains("— 3-2:"), text)
    }

    func testContextCarriesProgrammeAndMaximums() {
        let value = profile()
        value.setNote("норм", week: 1, day: 1)

        let text = CoachAdvisor.context(for: value)
        XCTAssertTrue(text.contains("Техасский метод"), text)
        XCTAssertTrue(text.contains("5ПМ"), text)
        XCTAssertTrue(text.contains("присед 100"), text)
    }

    /// Заметки прошлого цикла остаются в хранилище, но в разбор не идут.
    func testOtherCyclesStayOutOfTheContext() {
        let value = profile()
        value.setNote("старый цикл", week: 1, day: 1)
        value.startNewCycle(squat: 110, bench: 110, deadlift: 110)
        value.setNote("новый цикл", week: 1, day: 1)

        let text = CoachAdvisor.context(for: value)
        XCTAssertTrue(text.contains("новый цикл"), text)
        XCTAssertFalse(text.contains("старый цикл"), text)
    }

    /// Заметки идут по порядку недель, а не по строковому сравнению ключей.
    func testNotesAreOrderedByWeekNotByString() throws {
        let value = profile()
        value.setNote("девятая", week: 9, day: 1)
        value.setNote("десятая", week: 10, day: 1)

        let text = CoachAdvisor.context(for: value)
        let ninth = try XCTUnwrap(text.range(of: "девятая"))
        let tenth = try XCTUnwrap(text.range(of: "десятая"))
        XCTAssertTrue(ninth.lowerBound < tenth.lowerBound, text)
    }

    /// Записанные подходы попадают в разбор сгруппированными по дню.
    func testRecordedSetsAreGroupedByDay() throws {
        let value = profile()
        let squat = try XCTUnwrap(value.workoutPlan.weeks.first?.days.first?.exercises.first)
        value.recordSet(week: 1, day: 1, exercise: squat, index: 0, reps: 5, weight: 80)
        value.recordSet(week: 1, day: 1, exercise: squat, index: 1, reps: 5, weight: 82.5)

        let text = CoachAdvisor.context(for: value)
        XCTAssertTrue(text.contains("неделя 1, день 1: 5×80, 5×82.5 кг"), text)
    }

    /// Ключ лежит кусками — проверяем, что собирается обратно целым.
    func testEmbeddedKeyDecodes() {
        let key = CoachSecrets.embeddedKey
        XCTAssertTrue(key.hasPrefix("AQ."), key)
        XCTAssertEqual(key.count, 53)
    }
}
