import Foundation

/// Фулбади на три тренировки в неделю, семь недель.
///
/// Жим идёт по волне Лимара из четырнадцати тренировок — той же самой, что в
/// «Верх / Низ», без изменений: два жимовых дня в неделю на семь недель дают
/// ровно четырнадцать. Присед и тяга растут линейно, по 2,5 кг в неделю.
///
/// Средний день без жима — разгрузочный: он existует, чтобы неделя оставалась
/// трёхдневной, а не чтобы добавить объёма.
enum FullBodyCalculator {
    static let weekCount = 7
    /// Жимовые дни недели — первый и третий, как в волне.
    static let benchDays = [1, 3]

    private static let memo = PlanMemo<UpperLowerInput, WorkoutPlan>()

    static func generate(input: UpperLowerInput) -> WorkoutPlan {
        memo.resolve(input) { value in
            WorkoutPlan(weeks: (1...weekCount).map { week($0, input: value) }, isPeaking: false)
        }
    }

    /// Волна жима целиком — её показывает раздел «Жим 14».
    static func benchWave(input: UpperLowerInput) -> [BenchSessionPlan] {
        UpperLowerCalculator.benchWave(input: input)
    }

    private static func week(_ number: Int, input: UpperLowerInput) -> WorkoutWeekPlan {
        // Проценты от 1ПМ: неделя за неделей тяжелее, объём падает.
        let squatTop = ProgramCalculator.roundToPlate(input.squat1RM * squatPercent(number))
        let deadTop = ProgramCalculator.roundToPlate(input.deadlift1RM * deadliftPercent(number))

        let day1 = [
            benchSlot(week: number, dayNumber: 1),
            ExercisePrescription(name: "Приседания", sets: 4, reps: "5", load: .kilograms(squatTop)),
            ExercisePrescription(name: "Тяга в наклоне", sets: 3, reps: "8–12", load: .rpe("RPE 8"))
        ]

        let day2 = [
            ExercisePrescription(name: "Приседания", sets: 3, reps: "5", load: .kilograms(ProgramCalculator.roundToPlate(squatTop * 0.85))),
            ExercisePrescription(name: "Становая тяга", sets: 3, reps: "5", load: .kilograms(deadTop)),
            ExercisePrescription(name: "Жим штанги стоя", sets: 3, reps: "6–8", load: .rpe("RPE 8")),
            ExercisePrescription(name: "Подтягивания", sets: 3, reps: "8–12", load: .rpe("RPE 8"))
        ]

        let day3 = [
            benchSlot(week: number, dayNumber: 3),
            ExercisePrescription(name: "Приседания", sets: 2, reps: "5", load: .kilograms(ProgramCalculator.roundToPlate(squatTop * 0.75))),
            ExercisePrescription(name: "Румынская тяга", sets: 3, reps: "8", load: .rpe("RPE 8")),
            ExercisePrescription(name: "Скручивания лёжа на полу", sets: 3, reps: "12–15", load: .rpe("RPE 9"))
        ]

        return WorkoutWeekPlan(id: number, number: number, days: [
            day(number, 1, "ФУЛБАДИ · ЖИМ", day1),
            day(number, 2, "ФУЛБАДИ · НОГИ И СПИНА", day2),
            day(number, 3, "ФУЛБАДИ · ЖИМ", day3)
        ])
    }

    /// Приседания: от 70 % в первую неделю до 85 % в седьмую.
    private static func squatPercent(_ week: Int) -> Double {
        0.70 + Double(week - 1) * 0.025
    }

    /// Тяга растёт медленнее — она тяжелее восстанавливается.
    private static func deadliftPercent(_ week: Int) -> Double {
        0.70 + Double(week - 1) * 0.02
    }

    /// Место жима в дне. Реальные подходы подставляются из волны при показе,
    /// как и в «Верх / Низ», — здесь стоит только номер тренировки.
    private static func benchSlot(week: Int, dayNumber: Int) -> ExercisePrescription {
        let session = (week - 1) * 2 + (dayNumber == 1 ? 1 : 2)
        return ExercisePrescription(
            id: "bench-wave",
            name: "Жим лёжа",
            sets: 5,
            reps: "по волне",
            load: .repRange("волна " + String(session)),
            benchSession: session
        )
    }

    private static func day(_ week: Int, _ number: Int, _ title: String, _ exercises: [ExercisePrescription]) -> WorkoutDayPlan {
        WorkoutDayPlan(id: "\(week)-\(number)", number: number, title: title, exercises: exercises)
    }
}
