import Foundation

struct UpperLowerCalculator {
    private struct BenchSet {
        let percent: Double
        let reps: String
        let type: String
    }

    static func roundToFive(_ value: Double) -> Double {
        let scaled = value / 5
        return floor(scaled + 0.5) * 5
    }

    static func generate(input: UpperLowerInput) -> WorkoutPlan {
        let weeks = (1...7).map { week in
            let firstBenchSession = (week - 1) * 2 + 1
            let secondBenchSession = firstBenchSession + 1
            return WorkoutWeekPlan(
                id: week,
                number: week,
                days: [
                    WorkoutDayPlan(id: "\(week)-1", number: 1, title: "ПОНЕДЕЛЬНИК · ВЕРХ ТЯЖЁЛЫЙ", exercises: upperHeavy(input, benchSession: firstBenchSession)),
                    WorkoutDayPlan(id: "\(week)-2", number: 2, title: "ВТОРНИК · НИЗ ТЯЖЁЛЫЙ", exercises: lowerHeavy(input)),
                    WorkoutDayPlan(id: "\(week)-3", number: 3, title: "ЧЕТВЕРГ · ВЕРХ ОБЪЁМНЫЙ", exercises: upperVolume(input, benchSession: secondBenchSession)),
                    WorkoutDayPlan(id: "\(week)-4", number: 4, title: "ПЯТНИЦА · НИЗ ОБЪЁМНЫЙ", exercises: lowerVolume(input))
                ]
            )
        }
        return WorkoutPlan(weeks: weeks, isPeaking: false)
    }

    private static func upperHeavy(_ input: UpperLowerInput, benchSession: Int) -> [ExercisePrescription] {
        [benchPrescription(oneRepMax: input.bench1RM, session: benchSession),
         rpe("Тяга штанги в наклоне", 4, "6–8", "RPE 8"),
         rpe("Жим гантелей на наклонной скамье (30°)", 3, "8–10", "RPE 8"),
         rpe("Верхний блок", 3, "6–10", "RPE 8"),
         rpe("Жим гантелей сидя", 3, "6–8", "RPE 8"),
         rpe("Махи в стороны", 3, "12–20", "RPE 9"),
         rpe("Суперсет бицепс–трицепс", 4, "12 / 10", "RPE 8–9"),
         rpe("Предплечья", 3, "12–15", "RPE 8–9")]
    }

    private static func lowerHeavy(_ input: UpperLowerInput) -> [ExercisePrescription] {
        [weighted("Приседания со штангой", 3, "4–6", input.squat1RM, 0.83, "RPE 8"),
         weighted("Становая тяга", 3, "10–12", input.deadlift1RM, 0.65, "RPE 8"),
         rpe("Сгибание ног сидя", 3, "10–15", "RPE 8–9"),
         rpe("Икры", 4, "10–15", "RPE 9"),
         rpe("Пресс", 3, "10–15", "RPE 8–9"),
         rpe("Шея", 3, "10–12", "RPE 7–8"),
         rpe("Предплечья", 3, "12–15", "RPE 8–9")]
    }

    private static func upperVolume(_ input: UpperLowerInput, benchSession: Int) -> [ExercisePrescription] {
        [benchPrescription(oneRepMax: input.bench1RM, session: benchSession),
         rpe("Подтягивания / верхний блок", 4, "6–8", "RPE 8"),
         rpe("Жим гантелей сидя", 3, "6–10", "RPE 8"),
         rpe("Тяга штанги в наклоне / горизонтальный блок", 3, "8–12", "RPE 8"),
         rpe("Махи в стороны", 3, "12–20", "RPE 9"),
         rpe("Суперсет бицепс–трицепс", 4, "12 / 10", "RPE 8–9")]
    }

    private static func lowerVolume(_ input: UpperLowerInput) -> [ExercisePrescription] {
        [weighted("Приседания со штангой", 3, "6–8", input.squat1RM, 0.77, "RPE 8"),
         weighted("Становая тяга", 3, "6–10", input.deadlift1RM, 0.75, "RPE 8"),
         rpe("Сгибание ног лёжа", 3, "10–15", "RPE 8–9"),
         rpe("Икры", 4, "10–15", "RPE 9"),
         rpe("Пресс", 3, "10–15", "RPE 8–9"),
         rpe("Шея", 3, "10–12", "RPE 7–8"),
         rpe("Предплечья", 3, "12–15", "RPE 8–9")]
    }

    private static func weighted(_ name: String, _ sets: Int, _ reps: String, _ max: Double, _ percent: Double, _ rpe: String) -> ExercisePrescription {
        ExercisePrescription(name: name, sets: sets, reps: "\(reps) · \(rpe)", load: .kilograms(roundToFive(max * percent)))
    }

    private static func rpe(_ name: String, _ sets: Int, _ reps: String, _ value: String) -> ExercisePrescription {
        ExercisePrescription(name: name, sets: sets, reps: reps, load: .rpe(value), isOptional: true)
    }

    private static func benchPrescription(oneRepMax: Double, session: Int) -> ExercisePrescription {
        let sets = benchSessions[session - 1]
        let weights = sets.map { roundToFive(oneRepMax * $0.percent) }
        let weightText = weights.map(formatWeight).joined(separator: " / ") + " кг"
        let repsText = sets.map(\.reps).joined(separator: " / ")
        let special = sets.first(where: { $0.type != "обычный" })?.type
        let name = special.map { "Жим лёжа · \($0)" } ?? "Жим лёжа"
        return ExercisePrescription(name: name, sets: sets.count, reps: repsText, load: .repRange(weightText))
    }

    private static func formatWeight(_ value: Double) -> String {
        value.formatted(.number.precision(.fractionLength(0)))
    }

    private static let benchSessions: [[BenchSet]] = [
        [.init(percent: 0.80, reps: "6", type: "обычный"), .init(percent: 0.825, reps: "5", type: "обычный"), .init(percent: 0.825, reps: "5", type: "обычный"), .init(percent: 0.85, reps: "4", type: "обычный"), .init(percent: 0.85, reps: "4", type: "обычный")],
        [.init(percent: 0.85, reps: "3", type: "обычный"), .init(percent: 0.85, reps: "3", type: "обычный"), .init(percent: 0.925, reps: "2", type: "обычный"), .init(percent: 0.925, reps: "2", type: "обычный"), .init(percent: 1.025, reps: "1", type: "негатив")],
        [.init(percent: 0.80, reps: "6", type: "обычный"), .init(percent: 0.85, reps: "5", type: "обычный"), .init(percent: 0.85, reps: "5", type: "обычный"), .init(percent: 0.875, reps: "4", type: "обычный"), .init(percent: 0.875, reps: "4", type: "обычный")],
        [.init(percent: 0.875, reps: "3", type: "обычный"), .init(percent: 0.875, reps: "3", type: "обычный"), .init(percent: 0.95, reps: "2", type: "обычный"), .init(percent: 0.95, reps: "2", type: "обычный"), .init(percent: 1.05, reps: "1", type: "негатив")],
        [.init(percent: 0.80, reps: "6", type: "обычный"), .init(percent: 0.85, reps: "5", type: "обычный"), .init(percent: 0.85, reps: "5", type: "обычный"), .init(percent: 0.90, reps: "макс.", type: "тест")],
        [.init(percent: 0.90, reps: "3", type: "обычный"), .init(percent: 0.90, reps: "3", type: "обычный"), .init(percent: 0.95, reps: "2", type: "обычный"), .init(percent: 0.95, reps: "2", type: "обычный"), .init(percent: 1.05, reps: "1", type: "негатив")],
        [.init(percent: 0.85, reps: "5", type: "обычный"), .init(percent: 0.85, reps: "5", type: "обычный"), .init(percent: 0.90, reps: "3", type: "обычный"), .init(percent: 0.90, reps: "3", type: "обычный"), .init(percent: 0.925, reps: "макс.", type: "тест")],
        [.init(percent: 0.925, reps: "3", type: "обычный"), .init(percent: 0.925, reps: "3", type: "обычный"), .init(percent: 1.0, reps: "1", type: "обычный"), .init(percent: 1.0, reps: "1", type: "обычный"), .init(percent: 1.10, reps: "1", type: "негатив")],
        [.init(percent: 0.85, reps: "5", type: "обычный"), .init(percent: 0.85, reps: "5", type: "обычный"), .init(percent: 0.95, reps: "3", type: "обычный"), .init(percent: 0.95, reps: "3", type: "обычный"), .init(percent: 0.95, reps: "макс.", type: "тест")],
        [.init(percent: 0.95, reps: "3", type: "обычный"), .init(percent: 0.95, reps: "3", type: "обычный"), .init(percent: 1.025, reps: "2", type: "обычный"), .init(percent: 1.025, reps: "2", type: "обычный"), .init(percent: 1.05, reps: "1", type: "обычный")],
        [.init(percent: 0.875, reps: "5", type: "обычный"), .init(percent: 0.875, reps: "5", type: "обычный"), .init(percent: 0.95, reps: "3", type: "обычный"), .init(percent: 0.95, reps: "3", type: "обычный"), .init(percent: 0.95, reps: "макс.", type: "тест")],
        [.init(percent: 0.95, reps: "3", type: "обычный"), .init(percent: 0.95, reps: "3", type: "обычный"), .init(percent: 1.025, reps: "2", type: "обычный"), .init(percent: 1.025, reps: "2", type: "обычный"), .init(percent: 1.05, reps: "1", type: "обычный")],
        [.init(percent: 0.90, reps: "5", type: "обычный"), .init(percent: 1.0, reps: "3", type: "обычный"), .init(percent: 1.0, reps: "3", type: "обычный"), .init(percent: 1.05, reps: "2", type: "обычный"), .init(percent: 1.05, reps: "2", type: "обычный")],
        [.init(percent: 0.95, reps: "3", type: "обычный"), .init(percent: 1.05, reps: "2", type: "обычный"), .init(percent: 1.075, reps: "1", type: "рекорд")]
    ]
}
