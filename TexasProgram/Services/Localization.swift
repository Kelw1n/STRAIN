import SwiftUI

// MARK: - Язык приложения

/// Язык интерфейса на выбор в настройках. «Как в системе» — русский, если
/// телефон на русском (или близком языке), иначе английский.
enum AppLanguage: String, CaseIterable, Identifiable, Sendable {
    case system, ru, en

    static let storageKey = "app_language"
    /// Виджет и Live Activity читают язык из общей группы приложения.
    static let appGroup = "group.com.texasprogram.app"

    var id: String { rawValue }

    /// Подписи переключателя — каждая на своём языке, чтобы найти родной в любом режиме.
    var title: String {
        switch self {
        case .system: return L10n.isEnglish ? "System" : "Как в системе"
        case .ru: return "Русский"
        case .en: return "English"
        }
    }

    static var current: AppLanguage {
        AppLanguage(rawValue: UserDefaults.standard.string(forKey: storageKey) ?? "") ?? .system
    }

    /// Итоговый язык: «как в системе» раскрывается в русский или английский.
    var resolved: AppLanguage {
        switch self {
        case .ru, .en: return self
        case .system:
            let preferred = Locale.preferredLanguages.first?.lowercased() ?? "ru"
            let russianLike = ["ru", "uk", "be", "kk", "ky", "uz", "hy", "az", "ka", "tg"]
            return russianLike.contains { preferred.hasPrefix($0) } ? .ru : .en
        }
    }

    var locale: Locale { Locale(identifier: resolved == .en ? "en_US" : "ru_RU") }

    static func save(_ language: AppLanguage) {
        UserDefaults.standard.set(language.rawValue, forKey: storageKey)
        L10n.reload()
        shareWithWidgets()
    }

    /// Виджету и Live Activity — уже раскрытый язык: «как в системе» они не узнают.
    static func shareWithWidgets() {
        UserDefaults(suiteName: appGroup)?.set(current.resolved.rawValue, forKey: storageKey)
    }
}

// MARK: - Перевод

/// Перевод с русского на английский по общему с Android словарю `i18n-en.json`.
///
/// Ключ — русская фраза как она есть в коде; подстановки (числа, имена)
/// помечены `{}`. Фраза, собранная во время работы («Неделя 3 из 12»),
/// узнаётся по шаблону «Неделя {} из {}», а подставленные куски переводятся
/// отдельно — так «Заменено на «Приседания»» становится «Replaced with “Squats”».
enum L10n {
    private struct Template {
        let regex: NSRegularExpression
        let target: String
    }

    private struct Table {
        let exact: [String: String]
        let templates: [Template]
    }

    private static let lock = NSLock()
    private static var cache: [String: String] = [:]
    private static var english: Bool = AppLanguage.current.resolved == .en

    private static let table: Table = {
        guard let url = Bundle.main.url(forResource: "i18n-en", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let raw = try? JSONDecoder().decode([String: String].self, from: data)
        else { return Table(exact: [:], templates: []) }
        var exact: [String: String] = [:]
        var templates: [(Template, Int)] = []
        for (key, value) in raw {
            guard key.contains("{}") else { exact[key] = value; continue }
            let literal = key.replacingOccurrences(of: "{}", with: "")
            // Шаблон без русских букв вне подстановок поймал бы что угодно.
            guard literal.unicodeScalars.contains(where: isCyrillic) else { continue }
            let parts = key.components(separatedBy: "{}")
            let pattern = "^" + parts.map(NSRegularExpression.escapedPattern(for:)).joined(separator: "(.+?)") + "$"
            guard let regex = try? NSRegularExpression(pattern: pattern, options: [.dotMatchesLineSeparators]) else { continue }
            templates.append((Template(regex: regex, target: value), literal.count))
        }
        // Чем длиннее неизменная часть, тем точнее шаблон: такие проверяем первыми.
        return Table(exact: exact, templates: templates.sorted { $0.1 > $1.1 }.map(\.0))
    }()

    static var isEnglish: Bool {
        lock.lock(); defer { lock.unlock() }
        return english
    }

    /// После смены языка: заново читаем выбор и забываем готовые переводы.
    static func reload() {
        lock.lock()
        english = AppLanguage.current.resolved == .en
        cache.removeAll()
        lock.unlock()
    }

    static func isCyrillic(_ scalar: Unicode.Scalar) -> Bool {
        (0x0400...0x04FF).contains(scalar.value)
    }

    static func tr(_ text: String) -> String {
        guard isEnglish, text.unicodeScalars.contains(where: isCyrillic) else { return text }
        lock.lock()
        if let hit = cache[text] { lock.unlock(); return hit }
        lock.unlock()
        let result = translate(text, depth: 0)
        lock.lock()
        if cache.count > 5000 { cache.removeAll() }
        cache[text] = result
        lock.unlock()
        return result
    }

    private static func translate(_ text: String, depth: Int) -> String {
        if let hit = table.exact[text] { return hit }
        // Хвостовые пробелы и переводы строк — частая разница между фразой в коде и на экране.
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed != text, let hit = table.exact[trimmed] {
            return text.replacingOccurrences(of: trimmed, with: hit)
        }
        guard depth < 2 else { return text }
        let range = NSRange(text.startIndex..., in: text)
        for template in table.templates {
            guard let match = template.regex.firstMatch(in: text, range: range) else { continue }
            var values: [String] = []
            for index in 1..<match.numberOfRanges {
                guard let part = Range(match.range(at: index), in: text) else { values.append(""); continue }
                let value = String(text[part])
                values.append(value.unicodeScalars.contains(where: isCyrillic) ? translate(value, depth: depth + 1) : value)
            }
            return fill(template.target, values)
        }
        return text
    }

    /// `{}` — по порядку, `{2}` — по номеру: в английском слова иногда меняются местами.
    private static func fill(_ target: String, _ values: [String]) -> String {
        var result = ""
        var next = 0
        var index = target.startIndex
        while index < target.endIndex {
            let character = target[index]
            if character == "{", let close = target[index...].firstIndex(of: "}") {
                let inner = target[target.index(after: index)..<close]
                if inner.isEmpty {
                    result += next < values.count ? values[next] : ""
                    next += 1
                    index = target.index(after: close)
                    continue
                }
                if let number = Int(inner), number >= 1 {
                    result += number - 1 < values.count ? values[number - 1] : ""
                    index = target.index(after: close)
                    continue
                }
            }
            result.append(character)
            index = target.index(after: index)
        }
        return result
    }
}

/// Перевод фразы интерфейса на выбранный язык.
func L(_ text: String) -> String { L10n.tr(text) }

extension L10n {
    /// Язык для дат и чисел: «12 марта» или «March 12».
    static var locale: Locale { Locale(identifier: isEnglish ? "en_US" : "ru_RU") }
}

/// Формат даты на текущем языке. Форматтер пересоздаётся только после смены языка.
final class LocalizedDateFormat {
    private let ru: String
    private let en: String
    private var cached: (english: Bool, formatter: DateFormatter)?

    /// `ru` и `en` — шаблоны DateFormatter: «d MMM yyyy» и «MMM d, yyyy».
    init(ru: String, en: String) {
        self.ru = ru
        self.en = en
    }

    func string(from date: Date) -> String {
        let english = L10n.isEnglish
        if let cached, cached.english == english { return cached.formatter.string(from: date) }
        let formatter = DateFormatter()
        formatter.locale = L10n.locale
        formatter.dateFormat = english ? en : ru
        cached = (english, formatter)
        return formatter.string(from: date)
    }
}

// MARK: - Строки в SwiftUI

// Подписи из переменных (`Text(option.title)`) идут через перевод сами.
// Литералы в коде обёрнуты в `L(...)`. `@_disfavoredOverload` оставляет
// литералам их обычный путь, а переменным даёт этот, а не общий `StringProtocol`.

extension Text {
    @_disfavoredOverload
    init(_ content: String) {
        self.init(verbatim: L10n.tr(content))
    }
}

extension Button where Label == Text {
    @_disfavoredOverload
    init(_ title: String, action: @escaping () -> Void) {
        self.init(action: action) { Text(verbatim: L10n.tr(title)) }
    }

    @_disfavoredOverload
    init(_ title: String, role: ButtonRole?, action: @escaping () -> Void) {
        self.init(role: role, action: action) { Text(verbatim: L10n.tr(title)) }
    }
}

extension SwiftUI.Label where Title == Text, Icon == Image {
    @_disfavoredOverload
    init(_ title: String, systemImage: String) {
        self.init { Text(verbatim: L10n.tr(title)) } icon: { Image(systemName: systemImage) }
    }
}

extension View {
    @_disfavoredOverload
    func navigationTitle(_ title: String) -> some View {
        navigationTitle(Text(verbatim: L10n.tr(title)))
    }
}
