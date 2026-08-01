import Foundation

/// Готовые схемы разбивки для своей программы.
///
/// Схема задаёт только каркас — сколько дней в неделю и как они называются.
/// Упражнения в них вписывает пользователь.
enum SplitTemplate: String, CaseIterable, Identifiable, Codable, Sendable {
    case fullBody = "Фулбади"
    case fullBodyEOD = "Фулбади через день"
    case upperLower = "Верх / Низ"
    case pushPullLegs = "Жим / Тяга / Ноги"
    case custom = "Свой"

    var id: String { rawValue }

    /// Названия дней. Их количество и есть число тренировок в неделю.
    var dayTitles: [String] {
        switch self {
        case .fullBody: return ["ФУЛБАДИ А", "ФУЛБАДИ B", "ФУЛБАДИ C"]
        case .fullBodyEOD: return ["ФУЛБАДИ А", "ФУЛБАДИ B", "ФУЛБАДИ C", "ФУЛБАДИ D"]
        case .upperLower: return ["ВЕРХ ТЯЖЁЛЫЙ", "НИЗ ТЯЖЁЛЫЙ", "ВЕРХ ЛЁГКИЙ", "НИЗ ЛЁГКИЙ"]
        case .pushPullLegs: return ["ЖИМ", "ТЯГА", "НОГИ"]
        case .custom: return ["ДЕНЬ 1", "ДЕНЬ 2", "ДЕНЬ 3"]
        }
    }

    var explanation: String {
        switch self {
        case .fullBody:
            return "Всё тело за тренировку, три раза в неделю. Классика для новичка и для возвращения после перерыва."
        case .fullBodyEOD:
            return "Всё тело через день: четыре тренировки, дни недели плавают. Ставь режим «Очередь» в настройках."
        case .upperLower:
            return "Два верхних и два нижних дня. Больше объёма на движение, чем в фулбади."
        case .pushPullLegs:
            return "Жимовые, тяговые и ноги по отдельности. Нужен опыт: восстановление размазано тоньше."
        case .custom:
            return "Пустой каркас. Сам решаешь, сколько дней и что в них."
        }
    }
}

/// Одно упражнение своей программы.
struct CustomExercise: Codable, Equatable, Hashable, Identifiable, Sendable {
    var id: String = "own-" + UUID().uuidString
    var name: String
    var sets: Int
    var reps: String
    /// Вес в килограммах, если задан числом.
    var kilograms: Double?
    /// Свободная подпись нагрузки: «RPE 8», «свой вес».
    var loadText: String?
    /// Прибавка к весу за неделю. Ноль — вес не растёт.
    var weeklyIncrement: Double

    var load: LoadPrescription {
        if let kilograms { return .kilograms(kilograms) }
        if let loadText, !loadText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return .repRange(loadText)
        }
        return .rpe("RPE 8")
    }

    /// Упражнение для конкретной недели: вес растёт от номера недели.
    func prescription(week: Int) -> ExercisePrescription {
        var value = load
        if let kilograms, weeklyIncrement != 0 {
            value = .kilograms(ProgramCalculator.roundToPlate(kilograms + Double(week - 1) * weeklyIncrement))
        }
        return ExercisePrescription(id: id, name: name, sets: sets, reps: reps, load: value)
    }

    var detail: String {
        let volume = sets > 0 ? "\(sets) × \(reps)" : reps
        let growth = weeklyIncrement != 0 ? " · +\(WeightFormat.plain(weeklyIncrement)) кг/нед" : ""
        return volume + " · " + load.displayText + growth
    }
}

/// День своей программы.
struct CustomDay: Codable, Equatable, Hashable, Identifiable, Sendable {
    var id: String = UUID().uuidString
    var number: Int
    var title: String
    var exercises: [CustomExercise] = []
}

/// Своя программа целиком: сколько недель, какие дни, что в них.
struct CustomProgram: Codable, Equatable, Hashable, Sendable {
    var name: String = "Своя программа"
    var template: SplitTemplate = .fullBody
    var weeks: Int = 8
    var days: [CustomDay] = []

    /// Каркас по выбранной схеме — дни без упражнений.
    static func skeleton(template: SplitTemplate, name: String, weeks: Int) -> CustomProgram {
        CustomProgram(
            name: name,
            template: template,
            weeks: weeks,
            days: template.dayTitles.enumerated().map { CustomDay(number: $0.offset + 1, title: $0.element) }
        )
    }

    /// Готова ли программа к запуску: пустые дни тренировать нечем.
    var isRunnable: Bool {
        weeks > 0 && !days.isEmpty && days.allSatisfy { !$0.exercises.isEmpty }
    }

    var plan: WorkoutPlan {
        WorkoutPlan(weeks: (1...max(weeks, 1)).map { week in
            WorkoutWeekPlan(id: week, number: week, days: days.map { day in
                WorkoutDayPlan(
                    id: "\(week)-\(day.number)",
                    number: day.number,
                    title: day.title,
                    exercises: day.exercises.map { $0.prescription(week: week) }
                )
            })
        }, isPeaking: false)
    }
}
