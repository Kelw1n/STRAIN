import XCTest
@testable import TexasProgram

/// Кэш плана: готовый план берётся повторно, но любая правка входных данных
/// даёт новый — устаревший план из кэша недопустим.
final class PerformanceTests: XCTestCase {

    private func texas() -> ProgramProfile { ProgramProfile(input: .demo) }

    private func firstLoad(_ profile: ProgramProfile) -> LoadPrescription? {
        profile.workoutPlan.weeks.first?.days.first?.exercises.first?.load
    }

    func testPlanFollowsMaxChanges() {
        let profile = texas()
        let before = firstLoad(profile)
        profile.squat5RM += 20
        XCTAssertNotEqual(firstLoad(profile), before, "новый 5ПМ — новые веса")
        profile.squat5RM -= 20
        XCTAssertEqual(firstLoad(profile), before)
    }

    func testPlanFollowsProgramAndSteps() {
        let profile = texas()
        let texasPlan = profile.workoutPlan
        profile.programKind = .fullBody
        XCTAssertNotEqual(profile.workoutPlan, texasPlan)
        profile.programKind = .texas
        XCTAssertEqual(profile.workoutPlan, texasPlan)

        let before = profile.workoutPlan
        profile.defaultWeightStep = 5
        XCTAssertNotEqual(profile.workoutPlan, before, "другой шаг — другие веса")
    }

    func testTwoProfilesDoNotShareStalePlans() {
        let light = texas()
        let heavy = texas()
        heavy.bench5RM = 140
        XCTAssertNotEqual(light.workoutPlan, heavy.workoutPlan)
    }

    /// Медали читают план десятки раз — с кэшем это доли миллисекунды.
    func testAchievementsAreCheap() {
        let profile = texas()
        _ = AchievementCalculator.progress(for: profile)
        measure {
            for _ in 0..<20 { _ = AchievementCalculator.progress(for: profile) }
        }
    }
}
