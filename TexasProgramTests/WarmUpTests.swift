import XCTest
@testable import TexasProgram

/// Разминка перед рабочим весом: те же числа, что в WarmUpTest на Android.
final class WarmUpTests: XCTestCase {
    private func text(_ steps: [WarmUp.Step]) -> String {
        steps.map { "\(WeightFormat.plain($0.weight))×\($0.reps)" }.joined(separator: " ")
    }

    func testStepsFromBar() {
        XCTAssertEqual(text(WarmUp.steps(working: 100)), "20×10 40×5 60×3 80×2")
        XCTAssertEqual(text(WarmUp.steps(working: 142.5)), "20×10 55×5 85×3 112,5×2 127,5×1")
        XCTAssertEqual(text(WarmUp.steps(working: 50)), "20×10 30×3 40×2")
        XCTAssertEqual(text(WarmUp.steps(working: 34)), "")
    }

    func testDeadliftStartsWithPlates() {
        XCTAssertEqual(text(WarmUp.steps(for: "Становая тяга", working: 180)), "70×5 107,5×3 142,5×2 160×1")
    }

    func testOnlyBarbell() {
        XCTAssertFalse(WarmUp.steps(for: "Приседания", working: 120).isEmpty)
        XCTAssertFalse(WarmUp.steps(for: "Жим лёжа", working: 80).isEmpty)
        XCTAssertTrue(WarmUp.steps(for: "Жим гантелей лёжа", working: 80).isEmpty)
        XCTAssertTrue(WarmUp.steps(for: "Сгибание рук со штангой", working: 40).isEmpty)
        XCTAssertTrue(WarmUp.steps(for: "Жим ногами", working: 200).isEmpty)
    }
}
