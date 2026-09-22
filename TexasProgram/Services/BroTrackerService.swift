import SwiftUI
import Foundation

struct BroLiftEntry: Codable, Identifiable {
    var id: String { name }
    let name: String
    let prescription: String

    init(name: String, prescription: String) {
        self.name = name
        self.prescription = prescription
    }
}

struct BroExercise: Codable, Identifiable {
    var id: String { name }
    let name: String
    let sets: Int
    let reps: String
    let weight: String

    init(name: String, sets: Int, reps: String, weight: String) {
        self.name = name
        self.sets = sets
        self.reps = reps
        self.weight = weight
    }
}

struct BroWorkoutDay: Codable, Identifiable {
    var id: String { "\(week)-\(day)" }
    let week: Int
    let day: Int
    let title: String
    let exercises: [BroExercise]

    init(week: Int, day: Int, title: String, exercises: [BroExercise]) {
        self.week = week
        self.day = day
        self.title = title
        self.exercises = exercises
    }
}

struct BroProfileData: Codable, Identifiable {
    var id: String { broId }
    let broId: String
    let name: String
    let programKind: String
    let programTitle: String
    let currentWeek: Int
    let currentDay: Int
    let lastActiveEpoch: Int64
    let squat5RM: Double
    let bench5RM: Double
    let deadlift5RM: Double
    let recentLifts: [BroLiftEntry]
    let programDays: [BroWorkoutDay]
    let rawProgramJson: String?

    init(
        broId: String,
        name: String,
        programKind: String,
        programTitle: String,
        currentWeek: Int,
        currentDay: Int,
        lastActiveEpoch: Int64,
        squat5RM: Double,
        bench5RM: Double,
        deadlift5RM: Double,
        recentLifts: [BroLiftEntry],
        programDays: [BroWorkoutDay],
        rawProgramJson: String? = nil
    ) {
        self.broId = broId
        self.name = name
        self.programKind = programKind
        self.programTitle = programTitle
        self.currentWeek = currentWeek
        self.currentDay = currentDay
        self.lastActiveEpoch = lastActiveEpoch
        self.squat5RM = squat5RM
        self.bench5RM = bench5RM
        self.deadlift5RM = deadlift5RM
        self.recentLifts = recentLifts
        self.programDays = programDays
        self.rawProgramJson = rawProgramJson
    }

    var statusDescription: String {
        let diffSec = (Int64(Date().timeIntervalSince1970 * 1000) - lastActiveEpoch) / 1000
        if diffSec < 3600 {
            return "Только что тренировался"
        } else if diffSec < 86400 {
            let hours = max(1, diffSec / 3600)
            return "Тренировался \(hours) ч. назад"
        } else {
            let days = diffSec / 86400
            return "Был \(days) дн. назад"
        }
    }

    var isRecentlyActive: Bool {
        let diffSec = (Int64(Date().timeIntervalSince1970 * 1000) - lastActiveEpoch) / 1000
        return diffSec < 86400 * 2
    }
}

struct AddBuddyResult {
    let success: Bool
    let buddyName: String
    let message: String
}

private struct RestfulApiObject: Codable {
    let id: String?
    let name: String
    let data: BroProfileData
}

private struct CreateResponse: Codable {
    let id: String?
}

@Observable
final class BroTrackerService {
    static let shared = BroTrackerService()

    private let defaults = UserDefaults.standard
    private let keyMyBroId = "strain_my_bro_id"
    private let keyBuddyIds = "strain_buddy_ids"
    private let keyCachedBuddies = "strain_cached_buddies"

    var myBroId: String {
        didSet { defaults.set(myBroId, forKey: keyMyBroId) }
    }

    var buddyIds: [String] {
        didSet { defaults.set(buddyIds, forKey: keyBuddyIds) }
    }

    var buddies: [BroProfileData] = []
    var isSyncing: Bool = false
    var lastError: String? = nil

    private init() {
        var id = defaults.string(forKey: keyMyBroId) ?? ""
        if id.isEmpty {
            id = "bro_" + UUID().uuidString.prefix(8).lowercased()
            defaults.set(id, forKey: keyMyBroId)
        }
        self.myBroId = id
        self.buddyIds = defaults.stringArray(forKey: keyBuddyIds) ?? []
        loadCachedBuddies()
    }

    /// Генерирует полную QR-ссылку с данными профиля для мгновенного добавления офлайн
    func buildQRLink(profile: ProgramProfile) -> String {
        let name = profile.name.isEmpty ? "Бро" : profile.name
        let encodedName = name.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? name
        let kind = profile.programKind.backupCode
        let week = profile.currentWeek
        let day = 1
        let sq = Int(profile.squat5RM)
        let bp = Int(profile.bench5RM)
        let dl = Int(profile.deadlift5RM)

        return "strain://bro?id=\(myBroId)&name=\(encodedName)&kind=\(kind)&w=\(week)&d=\(day)&sq=\(sq)&bp=\(bp)&dl=\(dl)"
    }

    private func loadCachedBuddies() {
        guard let data = defaults.data(forKey: keyCachedBuddies),
              let list = try? JSONDecoder().decode([BroProfileData].self, from: data) else {
            return
        }
        self.buddies = list
    }

    private func saveCachedBuddies() {
        if let data = try? JSONEncoder().encode(buddies) {
            defaults.set(data, forKey: keyCachedBuddies)
        }
    }

    /// Публикует / обновляет свой профиль в облаке
    func syncMyProfile(profile: ProgramProfile) async {
        isSyncing = true
        defer { isSyncing = false }

        let plan = profile.workoutPlan
        let curWeek = profile.currentWeek
        let curWeekPlan = plan.weeks.first { $0.number == curWeek }
        let nextDay = curWeekPlan?.days.first { !profile.isCompleted(week: curWeek, day: $0.number) }

        // Собираем ключевые упражнения текущего дня
        var lifts: [BroLiftEntry] = []
        if let day = nextDay ?? curWeekPlan?.days.first {
            for ex in day.exercises.prefix(3) {
                let load = ex.load.displayText
                let presc = ex.sets > 0 ? "\(ex.sets)×\(ex.reps) · \(load)" : "\(ex.reps) · \(load)"
                lifts.append(BroLiftEntry(name: ex.name, prescription: presc))
            }
        }

        // Собираем дни плана для просмотра другом
        var programDays: [BroWorkoutDay] = []
        for week in plan.weeks.prefix(4) {
            for day in week.days {
                let exs = day.exercises.map {
                    BroExercise(name: $0.name, sets: $0.sets, reps: $0.reps, weight: $0.load.displayText)
                }
                programDays.append(BroWorkoutDay(week: week.number, day: day.number, title: day.title, exercises: exs))
            }
        }

        let myData = BroProfileData(
            broId: myBroId.isEmpty ? UUID().uuidString.prefix(8).lowercased() + String(Int.random(in: 1000...9999)) : myBroId,
            name: profile.name.isEmpty ? "Бро" : profile.name,
            programKind: profile.programKind.backupCode,
            programTitle: profile.programKind.rawValue,
            currentWeek: profile.currentWeek,
            currentDay: nextDay?.number ?? 1,
            lastActiveEpoch: Int64(Date().timeIntervalSince1970 * 1000),
            squat5RM: profile.squat5RM,
            bench5RM: profile.bench5RM,
            deadlift5RM: profile.deadlift5RM,
            recentLifts: lifts,
            programDays: programDays
        )

        let endpoint = "https://api.restful-api.dev/objects"
        do {
            let apiObject = RestfulApiObject(id: myBroId.isEmpty ? nil : myBroId, name: "STRAIN_BRO", data: myData)
            let bodyData = try JSONEncoder().encode(apiObject)

            var request: URLRequest
            if myBroId.isEmpty {
                request = URLRequest(url: URL(string: endpoint)!)
                request.httpMethod = "POST"
            } else {
                request = URLRequest(url: URL(string: "\(endpoint)/\(myBroId)")!)
                request.httpMethod = "PUT"
            }
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = bodyData

            let (data, response) = try await URLSession.shared.data(for: request)
            if let httpRes = response as? HTTPURLResponse, (200...299).contains(httpRes.statusCode) {
                if let decoded = try? JSONDecoder().decode(CreateResponse.self, from: data), let newId = decoded.id {
                    self.myBroId = newId
                }
                self.lastError = nil
            }
        } catch {
            self.lastError = error.localizedDescription
        }
    }

    /// Добавляет друга по ссылке strain://bro?... (офлайн/онлайн) или чистому ID
    func addBuddy(from rawCode: String) async -> AddBuddyResult {
        let trimmed = rawCode.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            return AddBuddyResult(success: false, buddyName: "", message: "Код пуст")
        }

        // 1. Проверяем QR-ссылку с параметрами (офлайн / прямой обмен)
        if trimmed.contains("?") && (trimmed.contains("name=") || trimmed.contains("kind=")) {
            let urlToParse = trimmed.hasPrefix("strain://") ? trimmed.replacingOccurrences(of: "strain://", with: "https://strain.app/") : trimmed
            if let url = URL(string: urlToParse),
               let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
               let items = components.queryItems {
                let dict = Dictionary(uniqueKeysWithValues: items.compactMap { item in
                    item.value.map { (item.name, $0) }
                })
                let id = dict["id"] ?? ("bro_" + UUID().uuidString.prefix(8).lowercased())
                if id == myBroId {
                    return AddBuddyResult(success: false, buddyName: "", message: "Это твой собственный QR-код!")
                }
                let name = dict["name"] ?? "Бро"
                let kind = dict["kind"] ?? "TEXAS"
                let week = Int(dict["w"] ?? "") ?? 1
                let day = Int(dict["d"] ?? "") ?? 1
                let sq = Double(dict["sq"] ?? "") ?? 100.0
                let bp = Double(dict["bp"] ?? "") ?? 100.0
                let dl = Double(dict["dl"] ?? "") ?? 100.0

                let programTitle = TrainingProgramKind.allCases.first { $0.backupCode == kind || $0.rawValue == kind }?.rawValue ?? kind

                let days = generatePreviewDays(kind: kind, squat: sq, bench: bp, deadlift: dl)
                let lifts = generatePreviewLifts(squat: sq, bench: bp, deadlift: dl)

                let buddy = BroProfileData(
                    broId: id,
                    name: name,
                    programKind: kind,
                    programTitle: programTitle,
                    currentWeek: week,
                    currentDay: day,
                    lastActiveEpoch: Int64(Date().timeIntervalSince1970 * 1000),
                    squat5RM: sq,
                    bench5RM: bp,
                    deadlift5RM: dl,
                    recentLifts: lifts,
                    programDays: days
                )

                if !buddyIds.contains(id) {
                    buddyIds.append(id)
                }
                buddies.removeAll { $0.broId == id }
                buddies.insert(buddy, at: 0)
                saveCachedBuddies()

                return AddBuddyResult(success: true, buddyName: name, message: "Бро «\(name)» успешно добавлен в банду! 🤝")
            }
        }

        // 2. Если передан ID или strain://bro/<id>
        var cleanId = trimmed
        if cleanId.contains("/bro/") {
            cleanId = cleanId.components(separatedBy: "/bro/").last ?? cleanId
        } else if cleanId.contains("bro=") {
            cleanId = cleanId.components(separatedBy: "bro=").last ?? cleanId
        }
        cleanId = cleanId.replacingOccurrences(of: "strain://", with: "").trimmingCharacters(in: .whitespacesAndNewlines)

        guard !cleanId.isEmpty else {
            return AddBuddyResult(success: false, buddyName: "", message: "Неверный формат ссылки или кода")
        }
        if cleanId == myBroId {
            return AddBuddyResult(success: false, buddyName: "", message: "Это твой собственный код бро!")
        }

        if let fetched = await fetchBuddy(id: cleanId) {
            if !buddyIds.contains(cleanId) {
                buddyIds.append(cleanId)
            }
            buddies.removeAll { $0.broId == cleanId }
            buddies.insert(fetched, at: 0)
            saveCachedBuddies()
            return AddBuddyResult(success: true, buddyName: fetched.name, message: "Бро «\(fetched.name)» успешно добавлен в банду! 🤝")
        } else {
            return AddBuddyResult(success: false, buddyName: "", message: "Не удалось найти бро с ID «\(cleanId)». Проверь подключение к интернету.")
        }
    }

    private func generatePreviewDays(kind: String, squat: Double, bench: Double, deadlift: Double) -> [BroWorkoutDay] {
        let progKind = TrainingProgramKind.allCases.first { $0.backupCode == kind || $0.rawValue == kind } ?? .texas
        let sq = squat > 0 ? squat : 100
        let bp = bench > 0 ? bench : 100
        let dl = deadlift > 0 ? deadlift : 100
        let dummy: ProgramProfile
        switch progKind {
        case .upperLower:
            dummy = ProgramProfile(upperLowerInput: UpperLowerInput(squat1RM: sq, bench1RM: bp, deadlift1RM: dl), name: "Preview")
        case .fullBody:
            dummy = ProgramProfile(fullBodyInput: ProgramInput(squat5RM: sq, bench5RM: bp, deadlift5RM: dl, level: .beginner), level: .aboutYear, name: "Preview")
        case .proTexas:
            dummy = ProgramProfile(proTexasInput: ProgramInput(squat5RM: sq, bench5RM: bp, deadlift5RM: dl, level: .beginner), name: "Preview")
        default:
            dummy = ProgramProfile(input: ProgramInput(squat5RM: sq, bench5RM: bp, deadlift5RM: dl, level: .beginner), name: "Preview")
        }
        var days: [BroWorkoutDay] = []
        for week in dummy.workoutPlan.weeks.prefix(4) {
            for day in week.days {
                let exs = day.exercises.map {
                    BroExercise(name: $0.name, sets: $0.sets, reps: $0.reps, weight: $0.load.displayText)
                }
                days.append(BroWorkoutDay(week: week.number, day: day.number, title: day.title, exercises: exs))
            }
        }
        return days
    }

    private func generatePreviewLifts(squat: Double, bench: Double, deadlift: Double) -> [BroLiftEntry] {
        return [
            BroLiftEntry(name: "Присед 5ПМ", prescription: "\(Int(squat)) кг"),
            BroLiftEntry(name: "Жим 5ПМ", prescription: "\(Int(bench)) кг"),
            BroLiftEntry(name: "Тяга 5ПМ", prescription: "\(Int(deadlift)) кг")
        ]
    }

    func removeBuddy(id: String) {
        buddyIds.removeAll { $0 == id }
        buddies.removeAll { $0.broId == id }
        saveCachedBuddies()
    }

    func refreshBuddies() async {
        guard !buddyIds.isEmpty else { return }
        isSyncing = true
        defer { isSyncing = false }

        var updated: [BroProfileData] = []
        for id in buddyIds {
            if let buddy = await fetchBuddy(id: id) {
                updated.append(buddy)
            } else if let cached = buddies.first(where: { $0.broId == id }) {
                updated.append(cached)
            }
        }
        self.buddies = updated
        saveCachedBuddies()
    }

    private func fetchBuddy(id: String) async -> BroProfileData? {
        guard let url = URL(string: "https://api.restful-api.dev/objects/\(id)") else { return nil }
        do {
            let (data, response) = try await URLSession.shared.data(from: url)
            guard let httpRes = response as? HTTPURLResponse, (200...299).contains(httpRes.statusCode) else {
                return nil
            }
            let obj = try JSONDecoder().decode(RestfulApiObject.self, from: data)
            return obj.data
        } catch {
            return nil
        }
    }
}
