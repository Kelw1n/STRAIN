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
    /// День следующей тренировки. Подпись «Сегодня / Завтра» считается от него
    /// в момент показа: раньше виджет хранил готовую строку, и без запуска
    /// приложения «Через 2 дн.» висело и назавтра. В старых слепках его нет.
    var focusDate: Date? = nil

    /// «Сегодня», «Завтра», «Через 3 дн.» на день показа — теми же словами,
    /// что и в приложении.
    func relative(on day: Date, calendar: Calendar = .current) -> String {
        guard let focusDate else { return relative }
        let days = calendar.dateComponents([.day], from: calendar.startOfDay(for: day), to: calendar.startOfDay(for: focusDate)).day ?? 0
        switch days {
        case ..<0: return "Пропущено"
        case 0: return "Сегодня"
        case 1: return "Завтра"
        case 2: return "Послезавтра"
        default: return "Через \(days) дн."
        }
    }

    /// Отдых — пока до тренировки ещё есть дни.
    func isRest(on day: Date, calendar: Calendar = .current) -> Bool {
        guard let focusDate else { return isRestToday }
        return calendar.startOfDay(for: focusDate) > calendar.startOfDay(for: day)
    }

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
        if let defaults, let data = try? JSONEncoder().encode(snapshot) {
            defaults.set(data, forKey: key)
        }
        if let data = try? JSONEncoder().encode(snapshot) {
            UserDefaults.standard.set(data, forKey: key)
        }
    }

    static func load() -> WidgetSnapshot? {
        if let defaults, let data = defaults.data(forKey: key),
           let snapshot = try? JSONDecoder().decode(WidgetSnapshot.self, from: data) {
            return snapshot
        }
        if let data = UserDefaults.standard.data(forKey: key),
           let snapshot = try? JSONDecoder().decode(WidgetSnapshot.self, from: data) {
            return snapshot
        }
        return WidgetSnapshot(
            relative: "Сегодня",
            dateText: "STRAIN",
            weekText: "Силовая программа",
            title: "Открой приложение для синхронизации",
            benchSession: nil,
            benchTopWeight: nil,
            doneDays: 0,
            totalDays: 1,
            isRestToday: false,
            updatedAt: .now
        )
    }
}
