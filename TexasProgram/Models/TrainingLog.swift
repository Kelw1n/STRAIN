import Foundation

/// Фактически выполненный подход: сколько повторов и с каким весом.
///
/// План говорит, что делать, а это — что получилось. Одно другое не заменяет:
/// без факта график показывает расчёт, а не тренировки.
struct SetEntry: Codable, Equatable, Hashable, Sendable {
    var reps: Int
    var weight: Double

    /// Тоннаж подхода — повторы на вес.
    var tonnage: Double { Double(reps) * weight }
}

/// Пропущенная тренировка.
///
/// Пропуск сам по себе расписание не ломает — невыполненное просто едет вперёд.
/// Ломает другое: веса растут по календарю, и после перерыва штанга оказывается
/// тяжелее, хотя стимула не было. Поэтому пропуск умеет задержать прогрессию.
struct SkippedWorkout: Codable, Equatable, Hashable, Identifiable, Sendable {
    var id: String = UUID().uuidString
    var week: Int
    var day: Int
    /// Пикирование считает недели с единицы — его пропуски хранятся отдельно.
    var isPeaking: Bool
    var date: Date
    /// Держит ли пропуск прогрессию: неделя повторяет веса предыдущей,
    /// пока пропущенное не закрыто.
    var holdsProgression: Bool

    var title: String { "Неделя \(week), день \(day)" }
}

/// Замер веса тела.
///
/// Вводится руками. «Здоровье» сюда не подключаем сознательно: сторонний
/// доступ к нему требует прав, а с ними подпись сборки, которую ставят вручную,
/// перестаёт устанавливаться.
struct BodyWeightEntry: Codable, Equatable, Hashable, Identifiable, Sendable {
    var date: Date
    var weight: Double

    var id: Date { date }
}

/// Одна тренировка в истории упражнения.
struct ExerciseHistoryPoint: Identifiable, Equatable, Sendable {
    let week: Int
    let day: Int
    let planned: Double?
    let entries: [SetEntry]

    var id: String { "\(week)-\(day)" }
    /// Лучший подход дня: по нему и видно, растёт ли движение.
    var best: Double { entries.map(\.weight).max() ?? 0 }
    var tonnage: Double { entries.reduce(0) { $0 + $1.tonnage } }
    var totalReps: Int { entries.reduce(0) { $0 + $1.reps } }
}

/// Тоннаж одной недели: сумма повторов на вес по всем записанным подходам.
struct WeeklyTonnage: Identifiable, Equatable, Sendable {
    let week: Int
    let total: Double
    let setCount: Int

    var id: Int { week }
}

/// Разбор ключа отметки подхода: «префикс + неделя-день|упражнение|номер».
///
/// Ключи собирались как строки задолго до тоннажа; разбирать их обратно надёжнее,
/// чем держать рядом второй индекс, который может разъехаться с первым.
enum SetKeyParts {
    static func dayKey(from key: String) -> String? {
        key.split(separator: "|", maxSplits: 1, omittingEmptySubsequences: false).first.map(String.init)
    }

    /// Номер недели из ключа дня. Префикс задаёт цикл и пиковый режим,
    /// поэтому чужие ключи сюда не попадают.
    static func week(fromDayKey day: String, prefix: String) -> Int? {
        guard day.hasPrefix(prefix) else { return nil }
        return day.dropFirst(prefix.count).split(separator: "-").first.flatMap { Int($0) }
    }
}
