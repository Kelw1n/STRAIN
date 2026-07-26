import Foundation

struct UpperLowerCalculator {
    private struct BenchSet {
        let percent: Double
        let reps: String
        let kind: BenchSetKind
    }

    /// День недели больше не зашит в заголовок: он берётся из расписания профиля.
    static let dayTitles = [
        "ВЕРХ ТЯЖЁЛЫЙ",
        "НИЗ ТЯЖЁЛЫЙ",
        "ВЕРХ ОБЪЁМНЫЙ",
        "НИЗ ОБЪЁМНЫЙ"
    ]

    /// Количество жимовых тренировок в волновом цикле.
    static var benchSessionCount: Int { benchSessions.count }

    static func roundToFive(_ value: Double) -> Double {
        let scaled = value / 5
        return floor(scaled + 0.5) * 5
    }

    private static let planMemo = PlanMemo<UpperLowerInput, WorkoutPlan>()
    private static let waveMemo = PlanMemo<UpperLowerInput, [BenchSessionPlan]>()

    /// Полная волна из 14 жимовых тренировок с посчитанными весами.
    static func benchWave(input: UpperLowerInput) -> [BenchSessionPlan] {
        waveMemo.resolve(input) { buildWave(input: $0) }
    }

    private static func buildWave(input: UpperLowerInput) -> [BenchSessionPlan] {
        benchSessions.enumerated().map { index, sets in
            let number = index + 1
            let isFirstOfWeek = number % 2 == 1
            return BenchSessionPlan(
                id: number,
                week: (number + 1) / 2,
                dayNumber: isFirstOfWeek ? 1 : 3,
                dayTitle: dayTitles[isFirstOfWeek ? 0 : 2],
                sets: sets.enumerated().map { setIndex, set in
                    BenchSetPlan(
                        id: setIndex + 1,
                        percent: set.percent,
                        weight: roundToFive(input.bench1RM * set.percent),
                        reps: set.reps,
                        kind: set.kind
                    )
                }
            )
        }
    }

    static func generate(input: UpperLowerInput) -> WorkoutPlan {
        planMemo.resolve(input) { buildPlan(input: $0) }
    }

    private static func buildPlan(input: UpperLowerInput) -> WorkoutPlan {
        let wave = benchWave(input: input)
        let weeks = (1...7).map { week in
            let firstBenchSession = (week - 1) * 2 + 1
            let secondBenchSession = firstBenchSession + 1
            return WorkoutWeekPlan(
                id: week,
                number: week,
                days: [
                    WorkoutDayPlan(id: "\(week)-1", number: 1, title: dayTitles[0], exercises: upperHeavy(input, bench: wave[firstBenchSession - 1])),
                    WorkoutDayPlan(id: "\(week)-2", number: 2, title: dayTitles[1], exercises: lowerHeavy(input)),
                    WorkoutDayPlan(id: "\(week)-3", number: 3, title: dayTitles[2], exercises: upperVolume(input, bench: wave[secondBenchSession - 1])),
                    WorkoutDayPlan(id: "\(week)-4", number: 4, title: dayTitles[3], exercises: lowerVolume(input))
                ]
            )
        }
        return WorkoutPlan(weeks: weeks, isPeaking: false)
    }

    private static func upperHeavy(_ input: UpperLowerInput, bench: BenchSessionPlan) -> [ExercisePrescription] {
        [benchPrescription(bench),
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

    private static func upperVolume(_ input: UpperLowerInput, bench: BenchSessionPlan) -> [ExercisePrescription] {
        [benchPrescription(bench),
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

    private static func benchPrescription(_ session: BenchSessionPlan) -> ExercisePrescription {
        ExercisePrescription(
            name: session.exerciseName,
            sets: session.sets.count,
            reps: session.repsText,
            load: .repRange(session.weightsText),
            benchSession: session.id
        )
    }

    private static let benchSessions: [[BenchSet]] = [
        [.init(percent: 0.80, reps: "6", kind: .normal), .init(percent: 0.825, reps: "5", kind: .normal), .init(percent: 0.825, reps: "5", kind: .normal), .init(percent: 0.85, reps: "4", kind: .normal), .init(percent: 0.85, reps: "4", kind: .normal)],
        [.init(percent: 0.85, reps: "3", kind: .normal), .init(percent: 0.85, reps: "3", kind: .normal), .init(percent: 0.925, reps: "2", kind: .normal), .init(percent: 0.925, reps: "2", kind: .normal), .init(percent: 1.025, reps: "1", kind: .negative)],
        [.init(percent: 0.80, reps: "6", kind: .normal), .init(percent: 0.85, reps: "5", kind: .normal), .init(percent: 0.85, reps: "5", kind: .normal), .init(percent: 0.875, reps: "4", kind: .normal), .init(percent: 0.875, reps: "4", kind: .normal)],
        [.init(percent: 0.875, reps: "3", kind: .normal), .init(percent: 0.875, reps: "3", kind: .normal), .init(percent: 0.95, reps: "2", kind: .normal), .init(percent: 0.95, reps: "2", kind: .normal), .init(percent: 1.05, reps: "1", kind: .negative)],
        [.init(percent: 0.80, reps: "6", kind: .normal), .init(percent: 0.85, reps: "5", kind: .normal), .init(percent: 0.85, reps: "5", kind: .normal), .init(percent: 0.90, reps: "макс.", kind: .test)],
        [.init(percent: 0.90, reps: "3", kind: .normal), .init(percent: 0.90, reps: "3", kind: .normal), .init(percent: 0.95, reps: "2", kind: .normal), .init(percent: 0.95, reps: "2", kind: .normal), .init(percent: 1.05, reps: "1", kind: .negative)],
        [.init(percent: 0.85, reps: "5", kind: .normal), .init(percent: 0.85, reps: "5", kind: .normal), .init(percent: 0.90, reps: "3", kind: .normal), .init(percent: 0.90, reps: "3", kind: .normal), .init(percent: 0.925, reps: "макс.", kind: .test)],
        [.init(percent: 0.925, reps: "3", kind: .normal), .init(percent: 0.925, reps: "3", kind: .normal), .init(percent: 1.0, reps: "1", kind: .normal), .init(percent: 1.0, reps: "1", kind: .normal), .init(percent: 1.10, reps: "1", kind: .negative)],
        [.init(percent: 0.85, reps: "5", kind: .normal), .init(percent: 0.85, reps: "5", kind: .normal), .init(percent: 0.95, reps: "3", kind: .normal), .init(percent: 0.95, reps: "3", kind: .normal), .init(percent: 0.95, reps: "макс.", kind: .test)],
        [.init(percent: 0.95, reps: "3", kind: .normal), .init(percent: 0.95, reps: "3", kind: .normal), .init(percent: 1.025, reps: "2", kind: .normal), .init(percent: 1.025, reps: "2", kind: .normal), .init(percent: 1.05, reps: "1", kind: .normal)],
        [.init(percent: 0.875, reps: "5", kind: .normal), .init(percent: 0.875, reps: "5", kind: .normal), .init(percent: 0.95, reps: "3", kind: .normal), .init(percent: 0.95, reps: "3", kind: .normal), .init(percent: 0.95, reps: "макс.", kind: .test)],
        [.init(percent: 0.95, reps: "3", kind: .normal), .init(percent: 0.95, reps: "3", kind: .normal), .init(percent: 1.025, reps: "2", kind: .normal), .init(percent: 1.025, reps: "2", kind: .normal), .init(percent: 1.05, reps: "1", kind: .normal)],
        [.init(percent: 0.90, reps: "5", kind: .normal), .init(percent: 1.0, reps: "3", kind: .normal), .init(percent: 1.0, reps: "3", kind: .normal), .init(percent: 1.05, reps: "2", kind: .normal), .init(percent: 1.05, reps: "2", kind: .normal)],
        [.init(percent: 0.95, reps: "3", kind: .normal), .init(percent: 1.05, reps: "2", kind: .normal), .init(percent: 1.075, reps: "1", kind: .record)]
    ]
}
