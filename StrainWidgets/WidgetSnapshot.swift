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
        let english = WidgetText.isEnglish
        switch days {
        case ..<0: return english ? "Missed" : "Пропущено"
        case 0: return english ? "Today" : "Сегодня"
        case 1: return english ? "Tomorrow" : "Завтра"
        case 2: return english ? "Day after tomorrow" : "Послезавтра"
        default: return english ? "In \(days) days" : "Через \(days) дн."
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
        return (WidgetText.isEnglish ? "B" : "Ж") + "\(benchSession)"
    }
}

/// Язык виджета и Live Activity — тот, что выбран в приложении.
/// Приложение кладёт его в общую группу; подписи здесь короткие, словарь свой.
enum WidgetText {
    static var isEnglish: Bool {
        UserDefaults(suiteName: "group.com.texasprogram.app")?.string(forKey: "app_language") == "en"
    }

    private static let english: [String: String] = [
        "ГОТОВО": "DONE",
        "ОТДЫХ": "REST",
        "к подходу": "to the set",
        "Отдых между подходами": "Rest between sets",
        "Пропустить": "Skip",
        "Следующая тренировка и номер жима.": "Next workout and bench number.",
        "Открой приложение, чтобы виджет наполнился": "Open the app to fill the widget",
        "Открой приложение": "Open the app",
        "Сегодня": "Today",
        "Силовая программа": "Strength program",
        "Открой приложение для синхронизации": "Open the app to sync"
    ]

    static func t(_ text: String) -> String { isEnglish ? (english[text] ?? text) : text }
    static func bench(_ session: Int) -> String { isEnglish ? "Bench #\(session)" : "Жим №\(session)" }
    static func upTo(_ kilograms: Int) -> String { isEnglish ? "up to \(kilograms) kg" : "до \(kilograms) кг" }
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

    /// Профилей не осталось — виджет возвращается к приглашению открыть приложение.
    static func clear() {
        defaults?.removeObject(forKey: key)
        UserDefaults.standard.removeObject(forKey: key)
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
            relative: WidgetText.t("Сегодня"),
            dateText: "STRAIN",
            weekText: WidgetText.t("Силовая программа"),
            title: WidgetText.t("Открой приложение для синхронизации"),
            benchSession: nil,
            benchTopWeight: nil,
            doneDays: 0,
            totalDays: 1,
            isRestToday: false,
            updatedAt: .now
        )
    }
}
