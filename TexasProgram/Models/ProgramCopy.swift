import Foundation

/// Копия чужой тренировки или программы — как «Своя программа»: её можно
/// поправить в конструкторе и запустить отдельным профилем.
enum ProgramCopy {
    /// Вся программа друга или один её день — дни из вкладки «Программы».
    static func program(days: [SocialProgramDay], name: String) -> CustomProgram {
        CustomProgram(
            name: name,
            template: .custom,
            weeks: 8,
            days: days.enumerated().map { index, day in
                CustomDay(number: index + 1, title: day.title, exercises: day.exercises.map(exercise))
            }
        )
    }

    /// Тренировка из поста — так, как её сделали: подходы, повторы и вес.
    static func program(workout: SocialWorkout, title: String, name: String) -> CustomProgram {
        CustomProgram(
            name: name,
            template: .custom,
            weeks: 8,
            days: [CustomDay(number: 1, title: title, exercises: workout.exercises.map(exercise))]
        )
    }

    static func exercise(_ item: SocialProgramDay.Exercise) -> CustomExercise {
        let kilograms = Self.kilograms(item.load)
        return CustomExercise(
            name: item.name,
            sets: max(item.sets, 1),
            reps: item.reps.isEmpty ? "5" : item.reps,
            kilograms: kilograms,
            loadText: kilograms == nil && !item.load.isEmpty ? item.load : nil,
            weeklyIncrement: 0
        )
    }

    /// Рабочий вес — самый тяжёлый подход, повторы — самые частые в подходах с ним.
    static func exercise(_ item: SocialWorkout.Exercise) -> CustomExercise {
        let heaviest = item.sets.map(\.weight).max() ?? 0
        let working = item.sets.filter { $0.weight == heaviest }
        let counts = Dictionary(grouping: working.map(\.reps), by: { $0 }).mapValues(\.count)
        let reps = counts.max { $0.value == $1.value ? $0.key < $1.key : $0.value < $1.value }?.key ?? 5
        return CustomExercise(
            name: item.name,
            sets: max(item.sets.count, 1),
            reps: "\(reps)",
            kilograms: heaviest > 0 ? heaviest : nil,
            loadText: heaviest > 0 ? nil : "свой вес",
            weeklyIncrement: 0
        )
    }

    /// «100 кг» → 100, «82,5 кг» → 82,5, «220 lbs» → в килограммы. Остальное — подпись.
    static func kilograms(_ load: String) -> Double? {
        let text = load.lowercased().replacingOccurrences(of: ",", with: ".").trimmingCharacters(in: .whitespaces)
        guard text.range(of: #"^[0-9]+(\.[0-9]+)?\s*(кг|kg|lbs)$"#, options: .regularExpression) != nil else { return nil }
        let digits = text.prefix { $0.isNumber || $0 == "." }
        guard let value = Double(digits), value > 0 else { return nil }
        // Фунты — в килограммы с шагом 0,5.
        return text.hasSuffix("lbs") ? (value / 2.20462 * 2).rounded() / 2 : value
    }
}
