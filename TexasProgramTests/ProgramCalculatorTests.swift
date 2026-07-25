import XCTest
@testable import TexasProgram

final class ProgramCalculatorTests: XCTestCase {
    func testRoundToPlateMatchesExcelMROUND() {
        XCTAssertEqual(ProgramCalculator.roundToPlate(88), 87.5)
        XCTAssertEqual(ProgramCalculator.roundToPlate(89), 90)
        XCTAssertEqual(ProgramCalculator.roundToPlate(91.25), 90)
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
        XCTAssertTrue(day.exercises.contains { $0.name == input.pull })
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
}
