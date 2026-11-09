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

    /// Кто уже пользовался приложением до английского, остаётся на русском,
    /// даже если iPhone на английском: язык не должен смениться сам после обновления.
    /// Новая установка выбора не имеет — берётся язык телефона.
    static func keepRussianForExistingUsers() {
        let defaults = UserDefaults.standard
        guard defaults.string(forKey: storageKey) == nil else { return }
        let store = URL.applicationSupportDirectory.appending(path: "default.store")
        guard FileManager.default.fileExists(atPath: store.path) else { return }
        defaults.set(AppLanguage.ru.rawValue, forKey: storageKey)
        L10n.reload()
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
    /// `weak` — шаблон с короткой неизменной частью («{} и {}»): годится, только если перевёл всё.
    private struct Template {
        let regex: NSRegularExpression
        let target: String
        let weak: Bool
    }

    private struct Table {
        let exact: [String: String]
        /// Те же фразы капсом: «ЖИМ ЛЁЖА» в заголовках, собранных через `uppercased()`.
        let upper: [String: String]
        /// И строчными: «жим лёжа» внутри фразы, собранной через `lowercased()`.
        let lower: [String: String]
        let templates: [Template]
        /// Куски фраз, к которым в коде приклеено число или имя: «был(а) » + «5 минут назад»,
        /// «Жим лёжа» + « · тест 1ПМ». Длинные проверяются первыми.
        let prefixes: [(String, String)]
        let suffixes: [(String, String)]
    }

    /// Разделители, по которым дробится собранная из кусков строка: «Жим лёжа · тест 1ПМ».
    private static let number = try! NSRegularExpression(pattern: "^([+−-]?\\d[\\d .,]*?) (\\D.*)$", options: [.dotMatchesLineSeparators])

    private static let tailNumber = try! NSRegularExpression(pattern: "^(\\D.*?) (\\d[\\d .,]*)$", options: [.dotMatchesLineSeparators])

    private static let separators = [" · ", "\n", "; ", ", "]

    private static let lock = NSLock()
    private static var cache: [String: String] = [:]
    private static var english: Bool = AppLanguage.current.resolved == .en

    private static let table: Table = {
        guard let url = Bundle.main.url(forResource: "i18n-en", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let raw = try? JSONDecoder().decode([String: String].self, from: data)
        else { return Table(exact: [:], upper: [:], lower: [:], templates: [], prefixes: [], suffixes: []) }
        var exact: [String: String] = [:]
        var templates: [(Template, Int, String)] = []
        for (key, value) in raw {
            guard key.contains("{}") else { exact[key] = value; continue }
            let literal = key.replacingOccurrences(of: "{}", with: "")
            // Шаблон без русских букв вне подстановок поймал бы что угодно.
            guard literal.unicodeScalars.contains(where: isCyrillic) else { continue }
            let weak = literal.filter(\.isLetter).count <= 3
            if let regex = try? NSRegularExpression(pattern: pattern(key), options: [.dotMatchesLineSeparators]) {
                templates.append((Template(regex: regex, target: value, weak: weak), literal.count, key))
            }
            // Тот же шаблон капсом: «ДЕНЬ 3» в заголовках через `uppercased()`.
            let upperKey = key.uppercased()
            if upperKey != key, let regex = try? NSRegularExpression(pattern: pattern(upperKey), options: [.dotMatchesLineSeparators]) {
                templates.append((Template(regex: regex, target: value.uppercased(), weak: weak), literal.count, upperKey))
            }
        }
        let pieces = exact.filter { key, _ in
            key.trimmingCharacters(in: .whitespacesAndNewlines).count >= 2 && key.unicodeScalars.contains(where: isCyrillic)
        }.sorted { $0.key < $1.key }
        let longestFirst: ((key: String, value: String), (key: String, value: String)) -> Bool = {
            $0.key.count != $1.key.count ? $0.key.count > $1.key.count : $0.key < $1.key
        }
        let prefixes = pieces.filter { $0.key.hasSuffix(" ") }.sorted(by: longestFirst).map { ($0.key, $0.value) }
        let suffixes = pieces.filter { key, _ in
            guard let first = key.first else { return false }
            return !(first.isLetter || first.isNumber || "«\"(".contains(first))
        }.sorted(by: longestFirst).map { ($0.key, $0.value) }
        // Кусок «Отдых » годится и как отдельное слово «Отдых».
        for (key, value) in exact.sorted(by: { $0.key < $1.key }) {
            let trimmed = key.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed != key, !trimmed.isEmpty, exact[trimmed] == nil {
                exact[trimmed] = value.trimmingCharacters(in: .whitespacesAndNewlines)
            }
        }
        var upper: [String: String] = [:]
        var lower: [String: String] = [:]
        for (key, value) in exact {
            upper[key.uppercased()] = value.uppercased()
            lower[key.lowercased()] = value
        }
        // Чем длиннее неизменная часть, тем точнее шаблон: такие проверяем первыми.
        let ordered = templates.sorted { $0.1 != $1.1 ? $0.1 > $1.1 : $0.2 < $1.2 }.map(\.0)
        return Table(exact: exact, upper: upper, lower: lower, templates: ordered, prefixes: prefixes, suffixes: suffixes)
    }()

    /// Регулярка шаблона. Подстановка вплотную к русской букве («Ж{}», «{}м») — число, а не слово:
    /// иначе «Ж{}» съел бы любую фразу на «Ж».
    private static func pattern(_ key: String) -> String {
        let parts = key.components(separatedBy: "{}")
        var out = "^" + NSRegularExpression.escapedPattern(for: parts[0])
        for index in 1..<parts.count {
            let before = parts[index - 1].unicodeScalars.last
            let after = parts[index].unicodeScalars.first
            let tight = (before.map(isCyrillic) ?? false) || (after.map(isCyrillic) ?? false)
            out += (tight ? "([^\\u0400-\\u04FF]+?)" : "(.+?)") + NSRegularExpression.escapedPattern(for: parts[index])
        }
        return out + "$"
    }

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
        // Неразрывные пробелы ставит типографика экранов — в словаре их нет.
        let plain = text.replacingOccurrences(of: "\u{00A0}", with: " ").replacingOccurrences(of: "\u{202F}", with: " ")
        let result = translate(plain, depth: 0)
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
        if trimmed == trimmed.uppercased(), let hit = table.upper[trimmed] {
            return text.replacingOccurrences(of: trimmed, with: hit)
        }
        if trimmed == trimmed.lowercased(), let hit = table.lower[trimmed] {
            return text.replacingOccurrences(of: trimmed, with: lowerFirst(hit))
        }
        guard depth < 3 else { return text }
        let range = NSRange(text.startIndex..., in: text)
        for template in table.templates {
            guard let match = template.regex.firstMatch(in: text, range: range) else { continue }
            var values: [String] = []
            for index in 1..<match.numberOfRanges {
                guard let part = Range(match.range(at: index), in: text) else { values.append(""); continue }
                values.append(self.part(String(text[part]), depth: depth + 1))
            }
            let result = fill(template.target, values)
            // Короткий шаблон («{} и {}») с недопереведённым остатком дал бы кашу из двух языков.
            if template.weak, result.unicodeScalars.contains(where: isCyrillic) { continue }
            return result
        }
        // «3 тренировки» → «3 workouts»: число и слово, переведённое целиком.
        if let match = number.firstMatch(in: text, range: range),
           let count = Range(match.range(at: 1), in: text), let word = Range(match.range(at: 2), in: text) {
            let rest = part(String(text[word]), depth: depth + 1)
            if !rest.unicodeScalars.contains(where: isCyrillic) { return String(text[count]) + " " + rest }
        }
        // «Ноябрь 2026» → «November 2026»: слово, переведённое целиком, и число.
        if let match = tailNumber.firstMatch(in: text, range: range),
           let words = Range(match.range(at: 1), in: text), let count = Range(match.range(at: 2), in: text) {
            let head = part(String(text[words]), depth: depth + 1)
            if !head.unicodeScalars.contains(where: isCyrillic) { return head + " " + String(text[count]) }
        }
        for (key, value) in table.prefixes where text.count > key.count && text.hasPrefix(key) {
            return value + part(String(text.dropFirst(key.count)), depth: depth + 1)
        }
        for (key, value) in table.suffixes where text.count > key.count && text.hasSuffix(key) {
            return part(String(text.dropLast(key.count)), depth: depth + 1) + value
        }
        // Целиком не нашлась — переводим куски по отдельности.
        for separator in separators where text.contains(separator) {
            return text.components(separatedBy: separator).map { part($0, depth: depth + 1) }.joined(separator: separator)
        }
        return text
    }

    /// «Bench press» → «bench press», но «RPE» остаётся «RPE».
    private static func lowerFirst(_ text: String) -> String {
        let characters = Array(text)
        guard characters.count > 1, characters[1].isLowercase else { return text }
        return characters[0].lowercased() + String(characters.dropFirst())
    }

    private static func part(_ text: String, depth: Int) -> String {
        text.unicodeScalars.contains(where: isCyrillic) ? translate(text, depth: depth) : text
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

    /// Дробная часть числа: «2,5» по-русски, «2.5» по-английски. На входе — с точкой.
    static func decimal(_ text: String) -> String {
        isEnglish ? text : text.replacingOccurrences(of: ".", with: ",")
    }
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
