import Foundation
import Observation
import Security

/// Дневник питания открытого профиля.
///
/// Каждый профиль — свой файл в Application Support, а не поле в базе профилей:
/// вкладка «Питание» пока тестовая, и если её уберут, прошлая версия приложения
/// откроет базу как ни в чём не бывало.
@Observable
@MainActor
final class NutritionStore {
    static let shared = NutritionStore()

    private(set) var profileID: UUID?
    private(set) var data = NutritionData()
    private let directory: URL

    init(directory: URL = NutritionStore.defaultDirectory) {
        self.directory = directory
    }

    nonisolated static var defaultDirectory: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent("Nutrition", isDirectory: true)
    }

    /// Профиль сменился — читаем его файл. Тот же профиль второй раз не перечитываем.
    func open(_ profileID: UUID) {
        guard self.profileID != profileID else { return }
        self.profileID = profileID
        data = Self.read(profileID, in: directory) ?? NutritionData()
    }

    /// После восстановления из копии файл мог смениться под открытым профилем.
    func reload() {
        guard let profileID else { return }
        data = Self.read(profileID, in: directory) ?? NutritionData()
    }

    // MARK: Записи

    func add(_ entries: [FoodEntry]) {
        guard !entries.isEmpty else { return }
        data.entries.append(contentsOf: entries)
        save()
    }

    func update(_ entry: FoodEntry) {
        guard let index = data.entries.firstIndex(where: { $0.id == entry.id }) else { return }
        data.entries[index] = entry
        save()
    }

    func remove(_ entry: FoodEntry) {
        data.entries.removeAll { $0.id == entry.id }
        save()
    }

    /// Недавние продукты без повторов по названию — добавить ещё раз в одно касание.
    func recentFoods(limit: Int = 12) -> [FoodEntry] {
        var seen = Set<String>()
        var result: [FoodEntry] = []
        for entry in data.entries.sorted(by: { $0.date > $1.date }) {
            let key = entry.name.lowercased().trimmingCharacters(in: .whitespaces)
            guard !key.isEmpty, seen.insert(key).inserted else { continue }
            result.append(entry)
            if result.count == limit { break }
        }
        return result
    }

    // MARK: Метод

    /// Новая неделя наблюдения с сегодняшнего дня. Записи еды остаются.
    func startObservation(on day: Date = Date(), calendar: Calendar = .current) {
        data.plan = NutritionPlan(observationStart: calendar.startOfDay(for: day))
        save()
    }

    func choose(_ goal: NutritionGoal, maintenance: Nutrients, today: Date = Date(), calendar: Calendar = .current) {
        guard var plan = data.plan else { return }
        let start = calendar.startOfDay(for: today)
        let norm = LimarMethod.startingNorm(for: goal, maintenance: maintenance)
        plan.maintenance = maintenance
        plan.goal = goal
        plan.target = norm
        plan.stepStart = start
        plan.history.append(NormChange(date: start, norm: norm, note: goal.title))
        data.plan = plan
        save()
    }

    /// Новая норма после недельной проверки — или прежняя ещё на неделю.
    func apply(_ norm: Nutrients, note: String, today: Date = Date(), calendar: Calendar = .current) {
        guard var plan = data.plan else { return }
        let start = calendar.startOfDay(for: today)
        plan.target = norm
        plan.stepStart = start
        plan.history.append(NormChange(date: start, norm: norm, note: note))
        data.plan = plan
        save()
    }

    func resetPlan() {
        data.plan = nil
        save()
    }

    // MARK: Файлы

    private func save() {
        guard let profileID else { return }
        Self.write(data, for: profileID, in: directory)
    }

    nonisolated static func fileURL(_ profileID: UUID, in directory: URL) -> URL {
        directory.appendingPathComponent(profileID.uuidString + ".json")
    }

    nonisolated static func read(_ profileID: UUID, in directory: URL = defaultDirectory) -> NutritionData? {
        guard let bytes = try? Data(contentsOf: fileURL(profileID, in: directory)) else { return nil }
        return try? JSONDecoder().decode(NutritionData.self, from: bytes)
    }

    nonisolated static func write(_ data: NutritionData, for profileID: UUID, in directory: URL = defaultDirectory) {
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        guard let bytes = try? JSONEncoder().encode(data) else { return }
        try? bytes.write(to: fileURL(profileID, in: directory), options: .atomic)
    }

    nonisolated static func delete(_ profileID: UUID, in directory: URL = defaultDirectory) {
        try? FileManager.default.removeItem(at: fileURL(profileID, in: directory))
    }
}

// MARK: - Ключ Google

/// Свой ключ Google AI у каждого — в связке ключей, как код входа в ленту.
/// В коде приложения и в репозитории ключей нет.
enum GeminiKeychain {
    private static let service = "com.texasprogram.app.gemini"
    private static let account = "api-key"

    static func load() -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data,
              let key = String(data: data, encoding: .utf8), !key.isEmpty else { return nil }
        return key
    }

    static func save(_ key: String?) {
        let base: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(base as CFDictionary)
        guard let key = key?.trimmingCharacters(in: .whitespacesAndNewlines), !key.isEmpty,
              let data = key.data(using: .utf8) else { return }
        var add = base
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(add as CFDictionary, nil)
    }
}
