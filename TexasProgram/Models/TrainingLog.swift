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

/// Откат после несданного веса.
///
/// Техасский метод на этот случай имеет своё правило: не взял тяжёлый день —
/// сбрасываешь проценты и идёшь заново. Событие хранит, с какой недели действует
/// сброс, чтобы исходные максимумы остались нетронутыми.
struct DeloadEvent: Codable, Equatable, Hashable, Identifiable, Sendable {
    var id: String = UUID().uuidString
    /// Первая неделя, которой касается откат. Всё до неё считается как раньше.
    var week: Int
    /// На сколько процентов срезаются веса: 5 или 10.
    var percent: Double
    /// Пикирование считает недели с единицы — его откаты хранятся отдельно.
    var isPeaking: Bool
    var date: Date

    /// Множитель весов: 10 % отката — это 0,9 от расчёта.
    var factor: Double { 1 - percent / 100 }

    var title: String { "−\(Int(percent))% с недели \(week)" }
}

/// Тоннаж одной недели: сумма повторов на вес по всем записанным подходам.
struct WeeklyTonnage: Identifiable, Equatable, Sendable {
    let week: Int
    let total: Double
    let setCount: Int

    var id: Int { week }
}

/// Разбор ключа отметки подхода: «неделя-день|упражнение|номер».
///
/// Ключи собирались как строки задолго до тоннажа; разбирать их обратно надёжнее,
/// чем держать рядом второй индекс, который может разъехаться с первым.
enum SetKeyParts {
    static func dayKey(from key: String) -> String? {
        key.split(separator: "|", maxSplits: 1, omittingEmptySubsequences: false).first.map(String.init)
    }

    /// Номер недели. У пикового цикла свой префикс, его сюда не пускаем.
    static func week(from key: String, isPeaking: Bool) -> Int? {
        guard let day = dayKey(from: key) else { return nil }
        let peak = day.hasPrefix("peak-")
        guard peak == isPeaking else { return nil }
        let trimmed = peak ? String(day.dropFirst("peak-".count)) : day
        return trimmed.split(separator: "-").first.flatMap { Int($0) }
    }
}
