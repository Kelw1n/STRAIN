import XCTest
@testable import TexasProgram

final class ProgramCalculatorTests: XCTestCase {
    func testRoundToPlateMatchesExcelMROUND() {
        XCTAssertEqual(ProgramCalculator.roundToPlate(88), 87.5)
        XCTAssertEqual(ProgramCalculator.roundToPlate(89), 90)
        XCTAssertEqual(ProgramCalculator.roundToPlate(91.25), 92.5)
    }

    func testBasePlanHasTwelveWeeksAndThreeDays() {
        let plan = ProgramCalculator.generate(input: .demo)
        XCTAssertEqual(plan.weeks.count, 12)
        XCTAssertTrue(plan.weeks.allSatisfy { $0.days.count == 3 })
        XCTAssertEqual(plan.weeks[0].days[0].exercises[0].load, .kilograms(80))
        XCTAssertEqual(plan.weeks[0].days[0].exercises[1].load, .kilograms(80))
        XCTAssertEqual(plan.weeks[0].days[2].exercises[0].load, .kilograms(90))
    }

    func testAllExcelWeekThreeLoads() {
        let plan = ProgramCalculator.generate(input: .demo)
        let expectedDayThree = stride(from: 90.0, through: 117.5, by: 2.5).map { $0 }
        for (index, week) in plan.weeks.enumerated() {
            XCTAssertEqual(week.days[2].exercises[0].load, .kilograms(expectedDayThree[index]))
            XCTAssertEqual(week.days[2].exercises[1].load, .kilograms(expectedDayThree[index]))
            XCTAssertEqual(week.days[2].exercises[2].load, .kilograms(expectedDayThree[index]))
            XCTAssertEqual(week.days[0].exercises[0].load, .kilograms(ProgramCalculator.roundToPlate(expectedDayThree[index] * 0.9)))
        }
    }

    func testOptionalExercisesArePropagated() {
        var input = ProgramInput.demo
        input.pull = AdditionalExerciseCategory.pull.options[1]
        input.back = AdditionalExerciseCategory.back.options[0]
        let day = ProgramCalculator.generate(input: input).weeks[0].days[0]
        XCTAssertTrue(day.exercises.contains { $0.name == input.pull! })
        XCTAssertFalse(day.exercises.contains { $0.name == input.back })
    }

    func testPeakingEndsWithOneRepMaxTest() {
        let plan = ProgramCalculator.generatePeaking(input: .demo)
        XCTAssertEqual(plan.weeks.count, 4)
        XCTAssertTrue(plan.weeks.last!.days[2].exercises.prefix(3).allSatisfy { $0.load == .testOneRepMax })
    }

    func testBeginnerPeakingMatchesThreeWeekExcelOption() {
        var input = ProgramInput.demo
        input.level = .beginner
        let plan = ProgramCalculator.generatePeaking(input: input)
        XCTAssertEqual(plan.weeks.count, 3)
        XCTAssertTrue(plan.weeks.last!.days[2].exercises.prefix(3).allSatisfy { $0.load == .testOneRepMax })
    }

    func testUpperLowerPlanHasSevenWeeksAndFourDays() {
        let plan = UpperLowerCalculator.generate(input: .demo)
        XCTAssertEqual(plan.weeks.count, 7)
        XCTAssertTrue(plan.weeks.allSatisfy { $0.days.count == 4 })
        XCTAssertEqual(plan.weeks[0].days[1].exercises[0].load, .kilograms(85))
        XCTAssertEqual(plan.weeks[0].days[1].exercises[1].load, .kilograms(85))
        XCTAssertEqual(plan.weeks[0].days[3].exercises[0].load, .kilograms(75))
        XCTAssertEqual(plan.weeks[0].days[3].exercises[1].load, .kilograms(100))
    }

    func testBenchWaveHasFourteenSessionsMappedToUpperDays() {
        let wave = UpperLowerCalculator.benchWave(input: .demo)
        XCTAssertEqual(wave.count, 14)
        XCTAssertEqual(wave.map(\.id), Array(1...14))
        XCTAssertEqual(wave.map(\.week), [1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7])
        XCTAssertEqual(wave[0].dayNumber, 1)
        XCTAssertEqual(wave[1].dayNumber, 3)
    }

    func testBenchWaveWeightsAreRoundedToFive() {
        let wave = UpperLowerCalculator.benchWave(input: .demo)
        XCTAssertEqual(wave[0].sets.map(\.weight), [80, 85, 85, 85, 85])
        XCTAssertEqual(wave[1].highlight, .negative)
        XCTAssertEqual(wave[13].highlight, .record)
        XCTAssertEqual(wave[13].topWeight, 110)
    }

    func testBenchWaveIsLinkedToDayPrescriptions() {
        let wave = UpperLowerCalculator.benchWave(input: .demo)
        let plan = UpperLowerCalculator.generate(input: .demo)
        XCTAssertEqual(plan.weeks[0].days[0].exercises[0].benchSession, 1)
        XCTAssertEqual(plan.weeks[0].days[2].exercises[0].benchSession, 2)
        XCTAssertEqual(plan.weeks[6].days[2].exercises[0].benchSession, 14)
        XCTAssertEqual(plan.weeks[0].days[0].exercises[0].load, .repRange(wave[0].weightsText))
        XCTAssertNil(plan.weeks[0].days[1].exercises[0].benchSession)
    }

    func testBenchSessionCompletionTracking() {
        let profile = ProgramProfile(upperLowerInput: .demo)
        XCTAssertEqual(profile.nextBenchSession, 1)
        profile.toggleBenchCompleted(1)
        XCTAssertTrue(profile.isBenchCompleted(1))
        XCTAssertEqual(profile.completedBenchCount, 1)
        XCTAssertEqual(profile.nextBenchSession, 2)
        profile.toggleBenchCompleted(1)
        XCTAssertEqual(profile.completedBenchCount, 0)
        XCTAssertEqual(profile.nextBenchSession, 1)
    }

    func testUpperLowerBenchWaveComesFromFourteenSessionSheet() {
        let plan = UpperLowerCalculator.generate(input: .demo)
        let firstBench = plan.weeks[0].days[0].exercises[0]
        let lastBench = plan.weeks[6].days[2].exercises[0]
        XCTAssertEqual(firstBench.sets, 5)
        XCTAssertEqual(firstBench.reps, "6 / 5 / 5 / 4 / 4")
        XCTAssertEqual(lastBench.sets, 3)
        XCTAssertTrue(lastBench.name.contains("рекорд"))
    }
}
