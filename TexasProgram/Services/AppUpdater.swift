import Foundation
import Observation

/// Обновление iPhone-версии без App Store.
///
/// CI кладёт в релиз `ios-latest` рядом со `STRAIN.ipa` файл `version.json` с
/// номером сборки и «что нового» за последние версии. Приложение сверяет его со
/// своим номером и показывает плашку. Поставить IPA само приложение не может —
/// iOS этого не разрешает, — поэтому «Скачать» отдаёт файл, а ставится он через
/// Sideloadly, как и раньше.
@MainActor
@Observable
final class AppUpdater {
    static let shared = AppUpdater()

    static let base = URL(string: "https://github.com/Kelw1n/STRAIN/releases/download/ios-latest/")!
    static let versionURL = base.appendingPathComponent("version.json")
    static let ipaURL = base.appendingPathComponent("STRAIN.ipa")
    /// Свёрнутое и снова открытое приложение проверяет не чаще раза в шесть часов.
    private static let recheck: TimeInterval = 6 * 60 * 60
    private static let seenKey = "app_seen_version"

    /// Одна версия в истории: номер, когда вышла и что в ней нового.
    struct Change: Codable, Equatable, Identifiable, Sendable {
        var versionCode: Int
        var versionName: String
        var notes: String
        var date: String?

        var id: Int { versionCode }

        /// Строки описания — каждая пунктом списка, без своих маркеров.
        var points: [String] {
            notes.split(whereSeparator: \.isNewline)
                .map { $0.trimmingCharacters(in: .whitespaces).trimmingCharacters(in: CharacterSet(charactersIn: "•-*—")).trimmingCharacters(in: .whitespaces) }
                .filter { !$0.isEmpty }
        }

        init(versionCode: Int, versionName: String = "", notes: String = "", date: String? = nil) {
            self.versionCode = versionCode
            self.versionName = versionName
            self.notes = notes
            self.date = date
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            versionCode = try c.decode(Int.self, forKey: .versionCode)
            versionName = try c.decodeIfPresent(String.self, forKey: .versionName) ?? ""
            notes = try c.decodeIfPresent(String.self, forKey: .notes) ?? ""
            date = try c.decodeIfPresent(String.self, forKey: .date)
        }
    }

    struct Release: Codable, Equatable, Sendable {
        var versionCode: Int
        var versionName: String
        var notes: String
        var date: String?
        /// Последние версии, новые сверху.
        var history: [Change]

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            versionCode = try c.decode(Int.self, forKey: .versionCode)
            versionName = try c.decodeIfPresent(String.self, forKey: .versionName) ?? ""
            notes = try c.decodeIfPresent(String.self, forKey: .notes) ?? ""
            date = try c.decodeIfPresent(String.self, forKey: .date)
            history = try c.decodeIfPresent([Change].self, forKey: .history) ?? []
        }

        /// Что вышло после версии `since` и до `upTo` включительно — новые сверху.
        func changes(since: Int, upTo: Int = .max) -> [Change] {
            let all = history.isEmpty ? [Change(versionCode: versionCode, versionName: versionName, notes: notes, date: date)] : history
            var seen = Set<Int>()
            return all
                .filter { $0.versionCode > since && $0.versionCode <= upTo && seen.insert($0.versionCode).inserted }
                .sorted { $0.versionCode > $1.versionCode }
        }
    }

    /// Версия новее установленной; `nil` — обновлений нет.
    private(set) var available: Release?
    /// Что нового в предлагаемой версии и во всех между ней и установленной.
    private(set) var pending: [Change] = []
    /// Только что обновились: что нового, показываем один раз.
    private(set) var whatsNew: [Change] = []
    /// «×» на плашке — до следующего запуска приложения не показываем.
    private(set) var dismissed = false
    /// Хоть раз получили ответ с номером последней версии.
    private(set) var hasChecked = false

    @ObservationIgnored private var lastCheck: Date?
    @ObservationIgnored private var checking = false

    /// Номер установленной сборки. У сборок из Xcode на компьютере он 1 —
    /// у них плашка тоже будет, её можно закрыть.
    static var installedCode: Int {
        Int(Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "") ?? 0
    }

    static var installedName: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
    }

    /// Ошибки молча пропускаем: проверка — не повод мешать тренироваться.
    func check(force: Bool = false) async {
        if checking { return }
        if !force, let lastCheck, Date().timeIntervalSince(lastCheck) < Self.recheck { return }
        checking = true
        defer { checking = false }
        var request = URLRequest(url: Self.versionURL, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 20)
        request.setValue("no-cache", forHTTPHeaderField: "Cache-Control")
        guard let (data, response) = try? await URLSession.shared.data(for: request),
              (response as? HTTPURLResponse)?.statusCode == 200,
              let release = try? JSONDecoder().decode(Release.self, from: data) else { return }
        lastCheck = Date()
        hasChecked = true
        apply(release, installed: Self.installedCode)
    }

    /// Разбор ответа отдельно от сети — его проверяют тесты.
    func apply(_ release: Release, installed: Int, defaults: UserDefaults = .standard) {
        rememberSeen(release, installed: installed, defaults: defaults)
        if release.versionCode > installed {
            available = release
            pending = release.changes(since: installed)
        } else {
            available = nil
            pending = []
        }
    }

    private func rememberSeen(_ release: Release, installed: Int, defaults: UserDefaults) {
        let seen = defaults.object(forKey: Self.seenKey) as? Int
        guard let seen, seen <= installed else {
            // Первый запуск с проверкой или откат на старую сборку: запоминаем, что стоит.
            defaults.set(installed, forKey: Self.seenKey)
            return
        }
        guard seen < installed else { return }
        let changes = release.changes(since: seen, upTo: installed)
        if changes.isEmpty {
            defaults.set(installed, forKey: Self.seenKey)
        } else {
            whatsNew = changes
        }
    }

    func dismiss() {
        dismissed = true
    }

    /// «Понятно» под «что нового»: больше не показываем для этой версии.
    func dismissWhatsNew(defaults: UserDefaults = .standard) {
        defaults.set(Self.installedCode, forKey: Self.seenKey)
        whatsNew = []
    }

    /// Строка под заголовком: первая новость, а если версий несколько — сколько их.
    /// Так же, как на Android.
    static func summary(_ changes: [Change], fallback: String = "") -> String {
        let first = changes.first?.points.first
            ?? fallback.split(whereSeparator: \.isNewline).first.map { $0.trimmingCharacters(in: .whitespaces) } ?? ""
        if changes.count > 1 {
            let word = RuPlural.word(changes.count, "обновление", "обновления", "обновлений")
            return "\(changes.count) \(word) с твоей версии · \(first)"
        }
        return first.isEmpty ? "Исправления и улучшения" : first
    }

    /// «30 октября» из «2026-10-30».
    static func dayText(_ date: String?) -> String? {
        guard let date, let day = TrainingHeatmap.date(date) else { return nil }
        return day.formatted(.dateTime.day().month(.wide).locale(Locale(identifier: "ru_RU")))
    }
}
