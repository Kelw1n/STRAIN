import XCTest
@testable import TexasProgram

/// Шаг прибавки, округление плана под блины зала, застой подсобки и карточка для сторис.
final class WeightStepTests: XCTestCase {

    private func texas() -> ProgramProfile { ProgramProfile(input: .demo) }

    private func accessory(_ profile: ProgramProfile) throws -> (ExercisePrescription, week: Int, day: Int) {
        for week in profile.workoutPlan.weeks {
            for day in week.days {
                if let found = day.exercises.first(where: { $0.needsOwnWeight && $0.topReps != nil }) {
                    return (found, week.number, day.number)
                }
            }
        }
        throw XCTSkip("в демо-программе нет подсобки без веса")
    }

    /// Упражнение с тем же названием в другой неделе — у подсобки оно повторяется.
    private func same(_ exercise: ExercisePrescription, in profile: ProgramProfile, week: Int, day: Int) throws -> ExercisePrescription {
        try XCTUnwrap(profile.workoutPlan.weeks.first { $0.number == week }?
            .days.first { $0.number == day }?.exercises.first { $0.name == exercise.name })
    }

    private func bench(_ profile: ProgramProfile, week: Int = 1, day: Int = 1) throws -> Double {
        let exercise = try XCTUnwrap(profile.workoutPlan.weeks.first { $0.number == week }?
            .days.first { $0.number == day }?.exercises.first { $0.name == "Жим лёжа" })
        guard case .kilograms(let value) = exercise.load else { throw XCTSkip("у жима нет веса в плане") }
        return value
    }

    // MARK: - Шаг

    func testDefaultStepIsTwoAndAHalf() {
        let profile = texas()
        XCTAssertEqual(profile.weightStep(for: "Жим лёжа"), 2.5)
        XCTAssertFalse(profile.hasOwnWeightStep(for: "Жим лёжа"))
    }

    func testOwnStepIsKeptPerExerciseAndIgnoresCase() {
        let profile = texas()
        profile.setWeightStep(5, for: "Жим лёжа")
        XCTAssertEqual(profile.weightStep(for: "жим лёжа "), 5)
        XCTAssertEqual(profile.weightStep(for: "Жим лежа"), 5)
        XCTAssertEqual(profile.weightStep(for: "Сгибание рук"), 2.5)
    }

    /// Шаг, совпадающий с общим, не хранится: поменяешь общий — упражнение пойдёт за ним.
    func testStepEqualToDefaultIsNotStored() {
        let profile = texas()
        profile.setWeightStep(5, for: "Жим лёжа")
        profile.setWeightStep(2.5, for: "Жим лёжа")
        XCTAssertTrue(profile.weightSteps.isEmpty)
    }

    func testAccessoryStepsUpByItsOwnStep() throws {
        let profile = texas()
        let (exercise, week, day) = try accessory(profile)
        let top = try XCTUnwrap(exercise.topReps)
        profile.setWeightStep(5, for: exercise.name)
        for index in 0..<exercise.sets {
            profile.recordSet(week: week, day: day, exercise: exercise, index: index, reps: top, weight: 55)
        }
        let next = try XCTUnwrap(profile.accessoryHint(for: exercise, week: week + 1, day: day))
        XCTAssertEqual(next.weight, 60)
    }

    /// У кого есть блины по 0,625 или тренажёр с шагом 1,25 — вес не теряет четверть.
    func testSmallStepKeepsQuarters() throws {
        let profile = texas()
        let (exercise, week, day) = try accessory(profile)
        let top = try XCTUnwrap(exercise.topReps)
        profile.setWeightStep(1.25, for: exercise.name)
        for index in 0..<exercise.sets {
            profile.recordSet(week: week, day: day, exercise: exercise, index: index, reps: top, weight: 55)
        }
        let next = try XCTUnwrap(profile.accessoryHint(for: exercise, week: week + 1, day: day))
        XCTAssertEqual(next.weight, 56.25)
        XCTAssertEqual(WeightFormat.decimal(56.25), "56,25")
        XCTAssertEqual(WeightFormat.decimal(52.5), "52,5")
        XCTAssertEqual(WeightFormat.decimal(60), "60")
    }

    func testDefaultStepAppliesToEveryExerciseWithoutOwn() throws {
        let profile = texas()
        let (exercise, week, day) = try accessory(profile)
        let top = try XCTUnwrap(exercise.topReps)
        profile.defaultWeightStep = 5
        for index in 0..<exercise.sets {
            profile.recordSet(week: week, day: day, exercise: exercise, index: index, reps: top, weight: 40)
        }
        XCTAssertEqual(profile.accessoryHint(for: exercise, week: week + 1, day: day)?.weight, 45)
    }

    // MARK: - Округление плана

    func testRoundingTiesGoDown() {
        XCTAssertEqual(ProgramProfile.roundWeight(82.5, step: 5), 80)
        XCTAssertEqual(ProgramProfile.roundWeight(83, step: 5), 85)
        XCTAssertEqual(ProgramProfile.roundWeight(57.5, step: 1.25), 57.5)
        XCTAssertEqual(ProgramProfile.roundWeight(82.5, step: 1), 82)
    }

    /// Нет мелких блинов — в плане только те веса, что можно повесить.
    func testPlanRoundsMainLiftToTheStep() throws {
        let profile = texas()
        let before = try bench(profile)
        profile.setWeightStep(5, for: "Жим лёжа")
        let after = try bench(profile)
        XCTAssertEqual(after.truncatingRemainder(dividingBy: 5), 0)
        XCTAssertLessThanOrEqual(abs(after - before), 2.5)
    }

    /// Обычный зал — план ровно тот, что посчитала программа.
    func testDefaultGymLeavesPlanUntouched() throws {
        let profile = texas()
        let planned = try bench(profile)
        profile.setWeightStep(2.5, for: "Жим лёжа")
        XCTAssertEqual(try bench(profile), planned)
    }

    // MARK: - Застой

    /// Три тренировки на одном весе, верх не закрыт, повторов не прибавилось.
    private func stall(_ profile: ProgramProfile) throws -> (ExercisePrescription, week: Int, day: Int) {
        let (exercise, week, day) = try accessory(profile)
        let top = try XCTUnwrap(exercise.topReps)
        try XCTSkipIf(top < 3)
        for offset in 0..<3 {
            let current = try same(exercise, in: profile, week: week + offset, day: day)
            for index in 0..<current.sets {
                profile.recordSet(week: week + offset, day: day, exercise: current, index: index, reps: top - 2, weight: 50)
            }
        }
        return (try same(exercise, in: profile, week: week + 3, day: day), week + 3, day)
    }

    func testThreeFlatSessionsSuggestADeload() throws {
        let profile = texas()
        let (exercise, week, day) = try stall(profile)
        let hint = try XCTUnwrap(profile.accessoryHint(for: exercise, week: week, day: day))
        XCTAssertTrue(hint.isDeload)
        XCTAssertEqual(hint.weight, 45)
    }

    func testDeloadFollowsTheStep() throws {
        let profile = texas()
        let (exercise, week, day) = try stall(profile)
        profile.setWeightStep(5, for: exercise.name)
        XCTAssertEqual(profile.accessoryHint(for: exercise, week: week, day: day)?.weight, 45)
        profile.setWeightStep(10, for: exercise.name)
        XCTAssertEqual(profile.accessoryHint(for: exercise, week: week, day: day)?.weight, 40)
    }

    func testDismissedStallIsNotSuggestedAgain() throws {
        let profile = texas()
        let (exercise, week, day) = try stall(profile)
        profile.dismissStall(for: exercise.name, weight: 50)
        let hint = try XCTUnwrap(profile.accessoryHint(for: exercise, week: week, day: day))
        XCTAssertFalse(hint.isDeload)
        XCTAssertEqual(hint.weight, 50)
    }

    /// Повторы растут на том же весе — двойная прогрессия работает, это не застой.
    func testGrowingRepsAreNotAStall() throws {
        let profile = texas()
        let (exercise, week, day) = try accessory(profile)
        let top = try XCTUnwrap(exercise.topReps)
        try XCTSkipIf(top < 4)
        for offset in 0..<3 {
            let current = try same(exercise, in: profile, week: week + offset, day: day)
            for index in 0..<current.sets {
                profile.recordSet(week: week + offset, day: day, exercise: current, index: index, reps: top - 3 + offset, weight: 50)
            }
        }
        let next = try same(exercise, in: profile, week: week + 3, day: day)
        let hint = try XCTUnwrap(profile.accessoryHint(for: next, week: week + 3, day: day))
        XCTAssertFalse(hint.isDeload)
    }

    // MARK: - Копия

    func testBackupKeepsSteps() throws {
        let profile = texas()
        profile.defaultWeightStep = 5
        profile.setWeightStep(1.25, for: "Сгибание рук")
        profile.dismissStall(for: "Тяга", weight: 60)
        let data = try BackupArchive.export([profile])
        let archive = try BackupArchive.archive(from: data)
        let snapshot = try XCTUnwrap(archive.profiles.first)
        XCTAssertEqual(snapshot.defaultWeightStep, 5)
        XCTAssertEqual(snapshot.weightSteps?["сгибание рук"], 1.25)
        XCTAssertEqual(snapshot.stallDismissed?["тяга"], 60)
    }
}
