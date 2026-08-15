import XCTest
@testable import TexasProgram

/// Подстройка весов под то, как прошёл тяжёлый день.
final class AutoregulationTests: XCTestCase {

    private func intensity(_ profile: ProgramProfile, week: Int, _ lift: MainLift) -> LoadPrescription? {
        profile.workoutPlan.weeks.first { $0.number == week }?
            .days.last?.exercises.first { $0.name == lift.rawValue }?.load
    }

    private func texas() -> ProgramProfile {
        let profile = ProgramProfile(input: .demo)
        profile.autoregulationEnabled = true
        return profile
    }

    /// Включённый переключатель сам по себе ничего не меняет: пока нет ответов,
    /// недели идут с обычной прибавкой.
    func testEnablingAloneKeepsThePlanIdentical() {
        let plain = ProgramProfile(input: .demo)
        let auto = texas()
        for week in 1...12 {
            XCTAssertEqual(intensity(auto, week: week, .squat), intensity(plain, week: week, .squat), "неделя \(week)")
        }
    }

    func testEasyAddsFiveToSquatAndTwoAndHalfToBench() {
        let profile = texas()
        XCTAssertEqual(intensity(profile, week: 1, .squat), .kilograms(90))
        XCTAssertEqual(intensity(profile, week: 1, .bench), .kilograms(90))

        profile.setReport(lift: .squat, week: 1, outcome: .easy)
        profile.setReport(lift: .bench, week: 1, outcome: .easy)

        XCTAssertEqual(intensity(profile, week: 2, .squat), .kilograms(95))
        XCTAssertEqual(intensity(profile, week: 2, .bench), .kilograms(92.5))
    }

    func testGrindHoldsTheWeight() {
        let profile = texas()
        profile.setReport(lift: .squat, week: 1, outcome: .grind)
        XCTAssertEqual(intensity(profile, week: 2, .squat), .kilograms(90))
        // Следующая неделя без ответа снова прибавляет обычные 2,5.
        XCTAssertEqual(intensity(profile, week: 3, .squat), .kilograms(92.5))
    }

    func testMissedRollsBackByPercent() {
        let profile = texas()
        profile.setReport(lift: .squat, week: 1, outcome: .missed)
        // 90 минус 7,5 % это 83,25, округление до блина даёт 82,5.
        XCTAssertEqual(intensity(profile, week: 2, .squat), .kilograms(82.5))
    }

    /// Каждое движение живёт отдельно — ради этого всё и затевалось.
    func testLiftsProgressIndependently() {
        let profile = texas()
        profile.setReport(lift: .squat, week: 1, outcome: .grind)
        profile.setReport(lift: .bench, week: 1, outcome: .easy)

        XCTAssertEqual(intensity(profile, week: 2, .squat), .kilograms(90))
        XCTAssertEqual(intensity(profile, week: 2, .bench), .kilograms(92.5))
        // Тяга без ответа идёт как обычно.
        XCTAssertEqual(intensity(profile, week: 2, .deadlift), .kilograms(92.5))
    }

    /// Лёгкие дни считаются процентами от тяжёлого, значит едут за ним.
    func testEasierDaysFollowTheIntensityDay() {
        let profile = texas()
        profile.setReport(lift: .squat, week: 1, outcome: .easy)
        let week2 = profile.workoutPlan.weeks.first { $0.number == 2 }

        // Тяжёлый 95, объём 90 % от него, лёгкий 80 % от объёма.
        XCTAssertEqual(week2?.days[2].exercises.first { $0.name == "Приседания" }?.load, .kilograms(95))
        XCTAssertEqual(week2?.days[0].exercises.first { $0.name == "Приседания" }?.load, .kilograms(85))
        XCTAssertEqual(week2?.days[1].exercises.first { $0.name == "Приседания" }?.load, .kilograms(67.5))
    }

    func testOutcomesAccumulateOverWeeks() {
        let profile = texas()
        profile.setReport(lift: .squat, week: 1, outcome: .easy)   // 90 → 95
        profile.setReport(lift: .squat, week: 2, outcome: .solid)  // 95 → 97,5
        profile.setReport(lift: .squat, week: 3, outcome: .grind)  // 97,5 → 97,5

        XCTAssertEqual(intensity(profile, week: 4, .squat), .kilograms(97.5))
    }

    func testAnswerCanBeChangedAndCleared() {
        let profile = texas()
        profile.setReport(lift: .squat, week: 1, outcome: .missed)
        XCTAssertEqual(intensity(profile, week: 2, .squat), .kilograms(82.5))

        profile.setReport(lift: .squat, week: 1, outcome: .easy)
        XCTAssertEqual(profile.reports(week: 1).count, 1)
        XCTAssertEqual(intensity(profile, week: 2, .squat), .kilograms(95))

        profile.clearReport(lift: .squat, week: 1)
        XCTAssertEqual(intensity(profile, week: 2, .squat), .kilograms(92.5))
    }

    func testDisabledSwitchIgnoresAnswers() {
        let profile = texas()
        profile.setReport(lift: .squat, week: 1, outcome: .missed)
        XCTAssertEqual(intensity(profile, week: 2, .squat), .kilograms(82.5))

        profile.autoregulationEnabled = false
        XCTAssertEqual(intensity(profile, week: 2, .squat), .kilograms(92.5))
    }

    // MARK: - Застой

    func testTwoMissesInARowMarkTheLiftAsStalled() {
        let profile = texas()
        profile.setReport(lift: .squat, week: 1, outcome: .missed)
        XCTAssertTrue(profile.stalledLifts.isEmpty)

        profile.setReport(lift: .squat, week: 2, outcome: .missed)
        XCTAssertEqual(profile.stalledLifts, [.squat])
    }

    /// Удачная неделя между двумя провалами застоем не считается.
    func testSuccessBetweenMissesResetsTheCounter() {
        let profile = texas()
        profile.setReport(lift: .squat, week: 1, outcome: .missed)
        profile.setReport(lift: .squat, week: 2, outcome: .solid)
        profile.setReport(lift: .squat, week: 3, outcome: .missed)
        XCTAssertTrue(profile.stalledLifts.isEmpty)
    }

    // MARK: - Пространство имён

    /// У пикирования и нового цикла свои недели: ответы не должны смешиваться.
    func testReportsAreNamespaced() {
        let profile = texas()
        profile.setReport(lift: .squat, week: 1, outcome: .easy)
        XCTAssertNotNil(profile.report(lift: .squat, week: 1))

        profile.peakingActive = true
        XCTAssertNil(profile.report(lift: .squat, week: 1))

        profile.peakingActive = false
        profile.startNewCycle(squat: 100, bench: 100, deadlift: 100)
        XCTAssertNil(profile.report(lift: .squat, week: 1))
        XCTAssertEqual(profile.reportKey(week: 1), "c2-1")
    }

    /// Механизм завязан на тяжёлый день — там, где его нет, он выключен.
    func testOnlyTexasProgrammesSupportIt() {
        XCTAssertTrue(ProgramProfile(input: .demo).supportsAutoregulation)
        XCTAssertTrue(ProgramProfile(proTexasInput: .demo).supportsAutoregulation)
        XCTAssertFalse(ProgramProfile(upperLowerInput: .demo).supportsAutoregulation)
        XCTAssertFalse(ProgramProfile(euthanasia: .demo).supportsAutoregulation)
    }

    // MARK: - Заметки

    func testNotesAreKeptPerWorkout() {
        let profile = ProgramProfile(input: .demo)
        profile.setNote("болело плечо", week: 2, day: 1)

        XCTAssertEqual(profile.note(week: 2, day: 1), "болело плечо")
        XCTAssertEqual(profile.note(week: 2, day: 2), "")

        // Пустая заметка не занимает место в хранилище.
        profile.setNote("   ", week: 2, day: 1)
        XCTAssertEqual(profile.note(week: 2, day: 1), "")
        XCTAssertTrue(profile.workoutNotes.isEmpty)
    }

    // MARK: - Копия

    func testBackupCarriesReportsAndNotes() throws {
        let profile = texas()
        profile.setReport(lift: .bench, week: 2, outcome: .grind)
        profile.setNote("спал пять часов", week: 2, day: 3)

        let archive = try BackupService.archive(from: try BackupService.export([profile]))
        let snapshot = try XCTUnwrap(archive.profiles.first)

        XCTAssertEqual(snapshot.autoregulationEnabled, true)
        XCTAssertEqual(snapshot.liftReports?.count, 1)
        XCTAssertEqual(snapshot.liftReports?.first?.outcome, .grind)
        XCTAssertEqual(snapshot.workoutNotes?["2-3"], "спал пять часов")
    }
}
