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

private struct RestfulApiObject: Codable {
    let id: String?
    let name: String
    let data: BroProfileData
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
        self.myBroId = defaults.string(forKey: keyMyBroId) ?? ""
        self.buddyIds = defaults.stringArray(forKey: keyBuddyIds) ?? []
        loadCachedBuddies()
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
                if let decoded = try? JSONDecoder().decode(RestfulApiObject.self, from: data), let newId = decoded.id {
                    self.myBroId = newId
                }
                self.lastError = nil
            }
        } catch {
            self.lastError = error.localizedDescription
        }
    }

    /// Добавляет друга по ссылке strain://bro/<id> или чистому ID
    func addBuddy(from rawCode: String) async -> Bool {
        var cleanId = rawCode.trimmingCharacters(in: .whitespacesAndNewlines)
        if cleanId.contains("/bro/") {
            cleanId = cleanId.components(separatedBy: "/bro/").last ?? cleanId
        } else if cleanId.contains("bro=") {
            cleanId = cleanId.components(separatedBy: "bro=").last ?? cleanId
        }
        cleanId = cleanId.replacingOccurrences(of: "strain://", with: "")

        guard !cleanId.isEmpty, cleanId != myBroId else { return false }

        if let fetched = await fetchBuddy(id: cleanId) {
            if !buddyIds.contains(cleanId) {
                buddyIds.append(cleanId)
            }
            buddies.removeAll { $0.broId == cleanId }
            buddies.insert(fetched, at: 0)
            saveCachedBuddies()
            return true
        }
        return false
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
