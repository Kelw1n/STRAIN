import XCTest
@testable import TexasProgram

/// Эталонная распечатка планов всех программ. Та же распечатка есть в тестах
/// Android (GoldenPlanTest): одни и те же максимумы должны давать одни и те же
/// веса, подходы и повторы на обеих платформах — построчно.
final class GoldenPlanTests: XCTestCase {
    /// Неудобные максимумы: на круглых 100 кг ошибки округления не видны.
    static let odd = ProgramInput(
        squat5RM: 142.5, bench5RM: 97.5, deadlift5RM: 187.5, level: .beginner,
        pull: "Подтягивания", arms: "Растянутый суперсет: бицепс + трицепс", core: "Скручивания на фитболе",
        back: "Румынская тяга", press: "Z-жим"
    )
    static let light = ProgramInput(
        squat5RM: 61.25, bench5RM: 43.75, deadlift5RM: 81.25, level: .intermediate,
        pull: nil, arms: nil, core: nil, back: nil, press: nil
    )

    static func euthanasia(_ level: EuthanasiaLevel) -> EuthanasiaInput {
        EuthanasiaInput(
            pull: EuthanasiaMovement(exercise: "Тяга в наклоне", oneRepMax: 87.5, testMinutes: 23),
            squat: EuthanasiaMovement(exercise: "Присед классический", oneRepMax: 132.5, testMinutes: 27.5),
            press: EuthanasiaMovement(exercise: "Жим классический", oneRepMax: 72.5, testMinutes: 31),
            level: level
        )
    }

    static var custom: CustomProgram {
        CustomProgram(name: "Тест", template: .custom, weeks: 3, days: [
            CustomDay(id: "d1", number: 1, title: "ДЕНЬ 1", exercises: [
                CustomExercise(id: "own-a", name: "Жим", sets: 3, reps: "5", kilograms: 61.3, loadText: nil, weeklyIncrement: 2.5),
                CustomExercise(id: "own-b", name: "Планка", sets: 3, reps: "60 с", kilograms: nil, loadText: "свой вес", weeklyIncrement: 0),
                CustomExercise(id: "own-c", name: "Тяга", sets: 4, reps: "8", kilograms: nil, loadText: nil, weeklyIncrement: 0)
            ])
        ])
    }

    static var scenarios: [(String, WorkoutPlan)] {
        var list: [(String, WorkoutPlan)] = [
            ("texas-demo", ProgramCalculator.generate(input: .demo)),
            ("texas-odd", ProgramCalculator.generate(input: odd)),
            ("texas-light", ProgramCalculator.generate(input: light)),
            ("peaking-odd", ProgramCalculator.generatePeaking(input: odd)),
            ("peaking-light", ProgramCalculator.generatePeaking(input: light)),
            ("ul-demo", UpperLowerCalculator.generate(input: .demo)),
            ("ul-odd", UpperLowerCalculator.generate(input: UpperLowerInput(squat1RM: 152.5, bench1RM: 107.5, deadlift1RM: 192.5))),
            ("pt-odd", ProTexasCalculator.generate(input: odd)),
            ("pt-light", ProTexasCalculator.generate(input: light)),
            ("eu-demo", EuthanasiaCalculator.generate(input: .demo)),
            ("custom", custom.plan)
        ]
        for level in FullBodyLevel.allCases {
            list.append(("fb-\(BackupEnum.androidCode(String(describing: level)))-odd", FullBodyCalculator.generate(input: odd, level: level)))
        }
        list.append(("fb-TWO_YEARS-light", FullBodyCalculator.generate(input: light, level: .twoYears)))
        for level in EuthanasiaLevel.allCases {
            list.append(("eu-\(BackupEnum.androidCode(String(describing: level)))", EuthanasiaCalculator.generate(input: euthanasia(level))))
        }
        return list
    }

    static func dump(_ plan: WorkoutPlan) -> [String] {
        var lines: [String] = []
        for week in plan.weeks {
            for day in week.days {
                lines.append("W\(week.number) D\(day.number) \(day.id) \(day.title)")
                for exercise in day.exercises {
                    let bench = exercise.benchSession.map(String.init) ?? "-"
                    lines.append("  \(exercise.id)|\(exercise.name)|\(exercise.sets)|\(exercise.reps)|\(load(exercise.load))|\(exercise.isOptional ? 1 : 0)|\(bench)")
                }
            }
        }
        return lines
    }

    static func load(_ load: LoadPrescription) -> String {
        switch load {
        case .kilograms(let value): return String(format: "kg %.3f", value)
        case .rpe(let text): return "rpe " + text
        case .repRange(let text): return "range " + text
        case .testOneRepMax: return "test1rm"
        }
    }

    /// Печатает распечатку в лог CI строками «GOLDEN|…» — её сверяют с Android.
    func testPrintGoldenPlans() {
        var count = 0
        for (name, plan) in Self.scenarios {
            print("GOLDEN|# \(name)")
            for line in Self.dump(plan) {
                print("GOLDEN|" + line)
                count += 1
            }
        }
        XCTAssertGreaterThan(count, 1000)
    }
}
