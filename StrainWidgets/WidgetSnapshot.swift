import Foundation

/// Слепок следующей тренировки для виджетов.
///
/// Расширение не может читать хранилище приложения напрямую — у них разные песочницы.
/// Приложение кладёт сюда уже готовые строки, чтобы виджету не пришлось знать
/// ни про модель, ни про расчёт плана.
struct WidgetSnapshot: Codable, Equatable {
    var relative: String        // «Сегодня», «Завтра», «Через 3 дн.»
    var dateText: String        // «27 июля»
    var weekText: String        // «Неделя 3»
    var title: String           // «ПОНЕДЕЛЬНИК · ВЕРХ ТЯЖЁЛЫЙ»
    var benchSession: Int?
    var benchTopWeight: Int?
    var doneDays: Int
    var totalDays: Int
    var isRestToday: Bool
    var updatedAt: Date

    var progress: Double {
        guard totalDays > 0 else { return 0 }
        return min(max(Double(doneDays) / Double(totalDays), 0), 1)
    }

    /// Короткая строка для круглого виджета на экране блокировки.
    var compactLine: String {
        guard let benchSession else { return weekText }
        return "Ж\(benchSession)"
    }
}

/// Общий контейнер приложения и расширения.
///
/// `UserDefaults(suiteName:)` вернёт `nil`, если App Group не выдана при подписи —
/// тогда виджет просто покажет заглушку, а приложение продолжит работать как обычно.
enum WidgetStore {
    static let appGroup = "group.com.texasprogram.app"
    private static let key = "widget.snapshot"

    private static var defaults: UserDefaults? { UserDefaults(suiteName: appGroup) }

    static func save(_ snapshot: WidgetSnapshot) {
        guard let defaults, let data = try? JSONEncoder().encode(snapshot) else { return }
        defaults.set(data, forKey: key)
    }

    static func load() -> WidgetSnapshot? {
        guard let defaults, let data = defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(WidgetSnapshot.self, from: data)
    }
}
