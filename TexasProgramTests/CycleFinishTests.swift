import XCTest
@testable import TexasProgram

/// Конец программы: вопрос о весах и старт следующего цикла.
/// Те же случаи — в CycleFinishTest на Android.
@MainActor
final class CycleFinishTests: XCTestCase {
    private func program(weeks: Int = 8) -> CustomProgram {
        CustomProgram(name: "Моя", template: .custom, weeks: weeks, days: [
            CustomDay(number: 1, title: "ДЕНЬ 1", exercises: [
                CustomExercise(id: "own-squat", name: "Присед", sets: 3, reps: "5", kilograms: 100, loadText: nil, weeklyIncrement: 2.5),
                CustomExercise(id: "own-curl", name: "Бицепс", sets: 3, reps: "10–12", kilograms: 20, loadText: nil, weeklyIncrement: 0),
                CustomExercise(id: "own-pull", name: "Подтягивания", sets: 3, reps: "8", kilograms: nil, loadText: "свой вес", weeklyIncrement: 0)
            ])
        ])
    }

    func testMinimumReps() {
        XCTAssertEqual(CycleFinish.minimumReps("5"), 5)
        XCTAssertEqual(CycleFinish.minimumReps("8–10"), 8)
        XCTAssertEqual(CycleFinish.minimumReps("8/10"), 8)
        XCTAssertNil(CycleFinish.minimumReps("до отказа"))
    }

    func testSuggestionFromRecords() {
        XCTAssertNil(CycleFinish.suggestion(averageEffort: nil, missedSets: 0, loggedSets: 0))
        XCTAssertEqual(CycleFinish.suggestion(averageEffort: 9.5, missedSets: 0, loggedSets: 10), .hard)
        XCTAssertEqual(CycleFinish.suggestion(averageEffort: 7, missedSets: 3, loggedSets: 12), .hard)
        XCTAssertEqual(CycleFinish.suggestion(averageEffort: 4, missedSets: 0, loggedSets: 12), .easy)
        XCTAssertEqual(CycleFinish.suggestion(averageEffort: 7.5, missedSets: 1, loggedSets: 20), .normal)
        XCTAssertEqual(CycleFinish.suggestion(averageEffort: nil, missedSets: 0, loggedSets: 9), .normal)
    }

    func testNextCustomWeights() {
        let base = program()
        // 100 кг + 7 прибавок по 2,5 = 117,5 на восьмой неделе.
        let easy = CycleFinish.nextCustom(base, feel: .easy)
        let normal = CycleFinish.nextCustom(base, feel: .normal)
        let hard = CycleFinish.nextCustom(base, feel: .hard)
        XCTAssertEqual(easy.0.days[0].exercises[0].kilograms, 120)
        XCTAssertEqual(normal.0.days[0].exercises[0].kilograms, 117.5)
        XCTAssertEqual(hard.0.days[0].exercises[0].kilograms, 105)
        // Без прибавки и без веса — не трогаем.
        XCTAssertEqual(hard.0.days[0].exercises[1].kilograms, 20)
        XCTAssertNil(hard.0.days[0].exercises[2].kilograms)
        XCTAssertEqual(normal.1, [CycleGrowth(name: "Присед", from: 100, to: 117.5)])
    }

    func testNextMax() {
        XCTAssertEqual(CycleFinish.nextMax(100, ratio: 1.1, feel: .normal), 110)
        XCTAssertEqual(CycleFinish.nextMax(100, ratio: 1.1, feel: .easy), 112.5)
        XCTAssertEqual(CycleFinish.nextMax(100, ratio: 1.1, feel: .hard), 100)
        // Падение за цикл максимум не снижает, рост ограничен 30 %.
        XCTAssertEqual(CycleFinish.nextMax(100, ratio: 0.8, feel: .normal), 100)
        XCTAssertEqual(CycleFinish.nextMax(100, ratio: 2, feel: .normal), 130)
    }

    func testCustomProgramFinishesAndStartsNextCycle() {
        let profile = ProgramProfile(customProgram: program(weeks: 2))
        XCTAssertFalse(profile.isProgramFinished)
        profile.toggleCompleted(week: 1, day: 1)
        XCTAssertFalse(profile.isProgramFinished)
        profile.toggleCompleted(week: 2, day: 1)
        XCTAssertTrue(profile.isProgramFinished)
        XCTAssertEqual(profile.cycleRecap.workouts, 2)

        guard case .custom(let next, _)? = profile.nextCycleStart(feel: .normal) else {
            return XCTFail("ожидалась своя программа")
        }
        profile.startNewCustomCycle(next)
        XCTAssertEqual(profile.cycleNumber, 2)
        XCTAssertFalse(profile.isProgramFinished)
        XCTAssertEqual(profile.customProgram?.days[0].exercises[0].kilograms, 102.5)
        XCTAssertEqual(profile.completedDayCount, 0)
    }

    func testReadyProgramSuggestsMaxes() {
        let profile = ProgramProfile(input: .demo)
        guard case .maxes(let squat, _, _, let changes)? = profile.nextCycleStart(feel: .hard) else {
            return XCTFail("ожидались максимумы")
        }
        XCTAssertEqual(changes.count, 3)
        XCTAssertLessThan(squat, profile.squat5RM * 1.3)
        XCTAssertGreaterThan(squat, 0)
    }

    func testEuthanasiaNewCycleUpdatesItsMaxes() {
        let profile = ProgramProfile(euthanasia: .demo)
        profile.startNewCycle(squat: 150, bench: 105, deadlift: 110)
        XCTAssertEqual(profile.euthanasiaInput?.squat.oneRepMax, 150)
        XCTAssertEqual(profile.euthanasiaInput?.press.oneRepMax, 105)
        XCTAssertEqual(profile.euthanasiaInput?.pull.oneRepMax, 110)
    }
}
