import XCTest
@testable import TexasProgram

/// Две программы из Лимарина: продвинутый техас и «Эвтаназия худобы».
///
/// Числа сверены с таблицами: проценты дней, сдвиги блоков и множители
/// уплотнения взяты из формул, а не выведены на глаз.
final class NewProgramsTests: XCTestCase {

    private func day(_ plan: WorkoutPlan, week: Int, day: Int) -> [ExercisePrescription] {
        plan.weeks.first { $0.number == week }?.days.first { $0.number == day }?.exercises ?? []
    }

    private func weight(_ list: [ExercisePrescription], _ name: String) -> LoadPrescription? {
        list.first { $0.name == name }?.load
    }

    // MARK: - Продвинутый техас

    func testProTexasIsTenBlocksOfTwoWeeks() {
        let plan = ProTexasCalculator.generate(input: .demo)
        XCTAssertEqual(plan.weeks.count, 20)
        XCTAssertTrue(plan.weeks.allSatisfy { $0.days.count == 3 })
        XCTAssertEqual(ProTexasCalculator.block(forWeek: 1), 1)
        XCTAssertEqual(ProTexasCalculator.block(forWeek: 2), 1)
        XCTAssertEqual(ProTexasCalculator.block(forWeek: 3), 2)
        XCTAssertEqual(ProTexasCalculator.block(forWeek: 20), 10)
    }

    /// Первые два блока идут ниже 5ПМ — это разгон, дальше по 2,5 кг за блок.
    func testProTexasBlockRamp() {
        XCTAssertEqual(ProTexasCalculator.intensityWeight(100, block: 1), 80)
        XCTAssertEqual(ProTexasCalculator.intensityWeight(100, block: 2), 90)
        XCTAssertEqual(ProTexasCalculator.intensityWeight(100, block: 3), 100)
        XCTAssertEqual(ProTexasCalculator.intensityWeight(100, block: 4), 102.5)
        XCTAssertEqual(ProTexasCalculator.intensityWeight(100, block: 10), 117.5)
    }

    /// Проценты дней внутри блока: 80 / 70 / 90, затем 90 / 70 / 100.
    func testProTexasDayPercentages() {
        let plan = ProTexasCalculator.generate(input: .demo)
        // Третий блок идёт ровно от 5ПМ, на нём проценты читаются проще всего:
        // недели 5 и 6, интенсивность 100 кг.
        XCTAssertEqual(weight(day(plan, week: 5, day: 1), "Приседания"), .kilograms(80))
        XCTAssertEqual(weight(day(plan, week: 5, day: 2), "Приседания"), .kilograms(70))
        XCTAssertEqual(weight(day(plan, week: 5, day: 3), "Приседания"), .kilograms(90))
        XCTAssertEqual(weight(day(plan, week: 6, day: 1), "Приседания"), .kilograms(90))
        XCTAssertEqual(weight(day(plan, week: 6, day: 2), "Приседания"), .kilograms(70))
        XCTAssertEqual(weight(day(plan, week: 6, day: 3), "Приседания"), .kilograms(100))
    }

    func testProTexasSchemes() {
        let plan = ProTexasCalculator.generate(input: .demo)
        let volumeFirst = day(plan, week: 5, day: 1).first { $0.name == "Приседания" }
        XCTAssertEqual(volumeFirst?.sets, 5)
        XCTAssertEqual(volumeFirst?.reps, "7")

        let intensityFirst = day(plan, week: 5, day: 3).first { $0.name == "Приседания" }
        XCTAssertEqual(intensityFirst?.sets, 3)
        XCTAssertEqual(intensityFirst?.reps, "3")

        let volumeSecond = day(plan, week: 6, day: 1).first { $0.name == "Приседания" }
        XCTAssertEqual(volumeSecond?.sets, 5)
        XCTAssertEqual(volumeSecond?.reps, "5")

        let peak = day(plan, week: 6, day: 3).first { $0.name == "Приседания" }
        XCTAssertEqual(peak?.sets, 1)
        XCTAssertEqual(peak?.reps, "5")
    }

    /// Становая есть только в интенсивные дни — в объёмных и лёгких её нет.
    func testProTexasDeadliftOnlyOnIntensityDays() {
        let plan = ProTexasCalculator.generate(input: .demo)
        XCTAssertNil(weight(day(plan, week: 5, day: 1), "Становая тяга"))
        XCTAssertNil(weight(day(plan, week: 5, day: 2), "Становая тяга"))
        XCTAssertNotNil(weight(day(plan, week: 5, day: 3), "Становая тяга"))
        XCTAssertNotNil(weight(day(plan, week: 6, day: 3), "Становая тяга"))
    }

    func testProTexasProfileUsesFiveRepMax() {
        let profile = ProgramProfile(proTexasInput: .demo)
        XCTAssertEqual(profile.programKind, .proTexas)
        XCTAssertEqual(profile.maximumLabel, "5ПМ")
        XCTAssertEqual(profile.trainingDayCount, 3)
        XCTAssertEqual(profile.totalDays, 60)
        XCTAssertTrue(profile.benchWave.isEmpty)
    }

    // MARK: - Эвтаназия худобы

    func testEuthanasiaIsEightWeeksOfThreeDays() {
        let plan = EuthanasiaCalculator.generate(input: .demo)
        XCTAssertEqual(plan.weeks.count, 8)
        XCTAssertTrue(plan.weeks.allSatisfy { $0.days.count == 3 })
    }

    /// Первая неделя — тестовая: максимум и замер времени, без рабочих весов.
    func testEuthanasiaFirstWeekIsTesting() {
        let plan = EuthanasiaCalculator.generate(input: .demo)
        let names = day(plan, week: 1, day: 1).map(\.name)
        XCTAssertTrue(names.contains { $0.contains("тест 1ПМ") })
        XCTAssertTrue(names.contains { $0.contains("тест на время") })
        XCTAssertFalse(names.contains { $0.contains("плотность") })
    }

    /// Силовая часть циклится 3×5 → 3×3 → волна 5/3/1.
    func testEuthanasiaStrengthCycles() {
        let plan = EuthanasiaCalculator.generate(input: .demo)
        // Неделя 2 — первая инверсия.
        let first = day(plan, week: 2, day: 1).first { $0.name == "Тяга в наклоне" }
        XCTAssertEqual(first?.sets, 3)
        XCTAssertEqual(first?.reps, "5")
        XCTAssertEqual(first?.load, .kilograms(65))

        let second = day(plan, week: 3, day: 1).first { $0.name == "Тяга в наклоне" }
        XCTAssertEqual(second?.sets, 3)
        XCTAssertEqual(second?.reps, "3")
        XCTAssertEqual(second?.load, .kilograms(70))

        // Третья инверсия — волна из трёх подходов с разным весом.
        let wave = day(plan, week: 4, day: 1).filter { $0.name == "Тяга в наклоне" }
        XCTAssertEqual(wave.count, 3)
        XCTAssertEqual(wave.map(\.reps), ["5", "3", "1"])
        XCTAssertEqual(wave.map(\.load), [.kilograms(65), .kilograms(75), .kilograms(85)])
    }

    /// Волна даёт три упражнения с одним именем — у них должны быть разные ключи,
    /// иначе отметки подходов слились бы в одну.
    func testEuthanasiaWaveHasDistinctIdentifiers() {
        let plan = EuthanasiaCalculator.generate(input: .demo)
        let wave = day(plan, week: 4, day: 1).filter { $0.name == "Тяга в наклоне" }
        XCTAssertEqual(Set(wave.map(\.id)).count, 3)
    }

    /// Плотность: повторов столько же, времени меньше с каждой инверсией.
    func testEuthanasiaDensityShrinksTime() {
        let plan = EuthanasiaCalculator.generate(input: .demo)
        func density(_ week: Int) -> String? {
            day(plan, week: week, day: 1).first { $0.name.contains("плотность") }?.load.displayText
        }
        // Тест тяги — 20 минут, средний уровень: первая инверсия 92,9 %.
        XCTAssertEqual(density(2), "55 кг · за 18:34")
        XCTAssertEqual(density(8), "75 кг · за 10:00")

        // Количество повторений не меняется — меняется только время.
        let reps = (2...8).map { week in
            day(plan, week: week, day: 1).first { $0.name.contains("плотность") }?.reps
        }
        XCTAssertTrue(reps.allSatisfy { $0 == "45 повторений" })
    }

    func testEuthanasiaLevelsDifferOnlyInStep() {
        XCTAssertEqual(EuthanasiaLevel.beginner.timeFactor(inversion: 7), 0.6, accuracy: 0.002)
        XCTAssertEqual(EuthanasiaLevel.medium.timeFactor(inversion: 7), 0.5, accuracy: 0.002)
        XCTAssertEqual(EuthanasiaLevel.pro.timeFactor(inversion: 7), 0.4, accuracy: 0.002)
        // Первая инверсия — почти исходное время у всех.
        XCTAssertEqual(EuthanasiaLevel.medium.timeFactor(inversion: 1), 0.929, accuracy: 0.002)
    }

    /// Каждое движение считается от своего максимума и своего КПШ.
    func testEuthanasiaMovementsAreIndependent() {
        let plan = EuthanasiaCalculator.generate(input: .demo)
        let pull = day(plan, week: 2, day: 1).first { $0.name.contains("плотность") }
        let squat = day(plan, week: 2, day: 2).first { $0.name.contains("плотность") }
        let press = day(plan, week: 2, day: 3).first { $0.name.contains("плотность") }

        XCTAssertEqual(pull?.reps, "45 повторений")
        XCTAssertEqual(squat?.reps, "40 повторений")
        XCTAssertEqual(press?.reps, "50 повторений")
        // Присед в демо — 140 кг, 55 % от него это 77,5.
        XCTAssertEqual(squat?.load.displayText.hasPrefix("77,5 кг"), true)
    }

    func testEuthanasiaKeepsFourAccessoriesEveryDay() {
        let plan = EuthanasiaCalculator.generate(input: .demo)
        for week in plan.weeks {
            for item in week.days {
                XCTAssertEqual(item.exercises.filter(\.isOptional).count, 4, "неделя \(week.number), день \(item.number)")
            }
        }
    }

    func testEuthanasiaProfileBuildsPlan() {
        let profile = ProgramProfile(euthanasia: .demo)
        XCTAssertEqual(profile.programKind, .euthanasia)
        XCTAssertEqual(profile.trainingDayCount, 3)
        XCTAssertEqual(profile.totalDays, 24)
        XCTAssertFalse(profile.programKind.usesFiveRepMax)
    }

    func testTimeTextReadsLikeAStopwatch() {
        XCTAssertEqual(EuthanasiaCalculator.timeText(20), "20:00")
        XCTAssertEqual(EuthanasiaCalculator.timeText(18.5667), "18:34")
        XCTAssertEqual(EuthanasiaCalculator.timeText(5.25), "5:15")
    }

    // MARK: - Копия

    func testBackupCarriesBothNewPrograms() throws {
        let pro = ProgramProfile(proTexasInput: .demo)
        let evt = ProgramProfile(euthanasia: .demo)

        let archive = try BackupService.archive(from: try BackupService.export([pro, evt]))
        XCTAssertEqual(archive.profiles[0].programKind, "PRO_TEXAS")
        XCTAssertEqual(archive.profiles[1].programKind, "EUTHANASIA")
        XCTAssertEqual(archive.profiles[1].euthanasiaInput?.pull.exercise, "Тяга в наклоне")
        XCTAssertEqual(archive.profiles[1].euthanasiaInput?.level, .medium)
    }
}
