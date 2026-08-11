import Foundation

/// Продвинутый техасский метод Лимара: десять двухнедельных блоков.
///
/// Всё считается от веса интенсивного дня второй недели блока. Первые два блока
/// идут ниже твоего 5ПМ — это разгон, а не занижение: программу начинают после
/// нескольких месяцев классического техаса, и вход делается заведомо посильным.
enum ProTexasCalculator {
    static let blockCount = 10
    static let weekCount = blockCount * 2

    /// Сдвиг веса интенсивного дня по блокам: −20, −10, свой 5ПМ, дальше по 2,5 кг.
    static let blockOffsets: [Double] = [-20, -10, 0, 2.5, 5, 7.5, 10, 12.5, 15, 17.5]

    private static let memo = PlanMemo<ProgramInput, WorkoutPlan>()

    static func generate(input: ProgramInput) -> WorkoutPlan {
        memo.resolve(input) { value in
            WorkoutPlan(weeks: (1...weekCount).map { week($0, input: value) }, isPeaking: false)
        }
    }

    /// Номер двухнедельного блока для календарной недели.
    static func block(forWeek week: Int) -> Int { (week + 1) / 2 }

    /// Вес интенсивного дня блока — точка отсчёта для всех остальных дней.
    static func intensityWeight(_ base: Double, block: Int) -> Double {
        let offset = blockOffsets.indices.contains(block - 1) ? blockOffsets[block - 1] : blockOffsets.last ?? 0
        return ProgramCalculator.roundToPlate(base + offset)
    }

    private static func week(_ number: Int, input: ProgramInput) -> WorkoutWeekPlan {
        let blockNumber = block(forWeek: number)
        let squat = intensityWeight(input.squat5RM, block: blockNumber)
        let bench = intensityWeight(input.bench5RM, block: blockNumber)
        let deadlift = intensityWeight(input.deadlift5RM, block: blockNumber)
        // Нечётная неделя — первая в блоке, подготовительная; чётная — классическая.
        let isFirstWeek = number % 2 == 1

        let days = isFirstWeek
            ? firstWeek(input: input, squat: squat, bench: bench, deadlift: deadlift)
            : secondWeek(input: input, squat: squat, bench: bench, deadlift: deadlift)
        return WorkoutWeekPlan(id: number, number: number, days: days.enumerated().map { index, exercises in
            day(number, index + 1, dayTitle(isFirstWeek: isFirstWeek, dayNumber: index + 1), exercises)
        })
    }

    private static func dayTitle(isFirstWeek: Bool, dayNumber: Int) -> String {
        let phase = isFirstWeek ? "ПОДГОТОВКА" : "КЛАССИКА"
        switch dayNumber {
        case 1: return phase + " · ОБЪЁМ"
        case 2: return phase + " · ВОССТАНОВЛЕНИЕ"
        default: return phase + " · ИНТЕНСИВНОСТЬ"
        }
    }

    // MARK: - Первая неделя блока: субмаксимальная

    private static func firstWeek(input: ProgramInput, squat: Double, bench: Double, deadlift: Double) -> [[ExercisePrescription]] {
        var volume = [
            main("Приседания", sets: 5, reps: "7", weight: squat, percent: 0.8),
            main("Жим лёжа", sets: 5, reps: "7", weight: bench, percent: 0.8)
        ]
        append(&volume, optional(input.pull, sets: 4, reps: "7–10", rpe: "RPE 7"))
        append(&volume, optional(input.core, sets: 4, reps: "5–8", rpe: "RPE 8"))

        var recovery = [
            main("Приседания", sets: 2, reps: "5", weight: squat, percent: 0.7),
            main("Жим лёжа", sets: 2, reps: "5", weight: bench, percent: 0.7)
        ]
        append(&recovery, optional(input.back, sets: 3, reps: "8–12", rpe: "RPE 8"))
        append(&recovery, optional(input.press, sets: 3, reps: "7–10", rpe: "RPE 7"))
        append(&recovery, optional(input.arms, sets: 4, reps: "9–12", rpe: "RPE 8"))

        var intensity = [
            main("Приседания", sets: 3, reps: "3", weight: squat, percent: 0.9),
            main("Жим лёжа", sets: 3, reps: "3", weight: bench, percent: 0.9),
            main("Становая тяга", sets: 3, reps: "3", weight: deadlift, percent: 0.9)
        ]
        append(&intensity, optional(input.back, sets: 3, reps: "8–12", rpe: "RPE 8"))
        append(&intensity, optional(input.core, sets: 4, reps: "13–16", rpe: "RPE 8"))

        return [volume, recovery, intensity]
    }

    // MARK: - Вторая неделя блока: классический техас

    private static func secondWeek(input: ProgramInput, squat: Double, bench: Double, deadlift: Double) -> [[ExercisePrescription]] {
        var volume = [
            main("Приседания", sets: 5, reps: "5", weight: squat, percent: 0.9),
            main("Жим лёжа", sets: 5, reps: "5", weight: bench, percent: 0.9)
        ]
        append(&volume, optional(input.pull, sets: 2, reps: "5", rpe: "RPE 7"))
        append(&volume, optional(input.core, sets: 3, reps: "5–8", rpe: "RPE 8"))

        var recovery = [
            main("Приседания", sets: 2, reps: "5", weight: squat, percent: 0.7),
            main("Жим лёжа", sets: 2, reps: "5", weight: bench, percent: 0.7)
        ]
        append(&recovery, optional(input.back, sets: 3, reps: "5–8", rpe: "RPE 8"))
        append(&recovery, optional(input.press, sets: 3, reps: "5", rpe: "RPE 7"))
        append(&recovery, optional(input.arms, sets: 3, reps: "9–12", rpe: "RPE 8"))

        // Кульминация блока: истинный пятиповторный максимум.
        var intensity = [
            main("Приседания", sets: 1, reps: "5", weight: squat, percent: 1),
            main("Жим лёжа", sets: 1, reps: "5", weight: bench, percent: 1),
            main("Становая тяга", sets: 1, reps: "5", weight: deadlift, percent: 1)
        ]
        append(&intensity, optional(input.back, sets: 3, reps: "8–12", rpe: "RPE 8"))
        append(&intensity, optional(input.core, sets: 3, reps: "13–16", rpe: "RPE 8"))

        return [volume, recovery, intensity]
    }

    // MARK: - Сборка

    private static func main(_ name: String, sets: Int, reps: String, weight: Double, percent: Double) -> ExercisePrescription {
        ExercisePrescription(
            name: name,
            sets: sets,
            reps: reps,
            load: .kilograms(ProgramCalculator.roundToPlate(weight * percent))
        )
    }

    private static func optional(_ name: String?, sets: Int, reps: String, rpe: String) -> ExercisePrescription? {
        guard let name, !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        return ExercisePrescription(name: name, sets: sets, reps: reps, load: .rpe(rpe), isOptional: true)
    }

    private static func append(_ array: inout [ExercisePrescription], _ value: ExercisePrescription?) {
        if let value { array.append(value) }
    }

    private static func day(_ week: Int, _ number: Int, _ title: String, _ exercises: [ExercisePrescription]) -> WorkoutDayPlan {
        WorkoutDayPlan(id: "\(week)-\(number)", number: number, title: title, exercises: exercises)
    }
}
