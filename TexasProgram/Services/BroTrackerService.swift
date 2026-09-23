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
    var recentChatMessages: [BroChatMessage]

    enum CodingKeys: String, CodingKey {
        case broId, name, programKind, programTitle, currentWeek, currentDay
        case lastActiveEpoch, squat5RM, bench5RM, deadlift5RM, recentLifts, programDays, rawProgramJson
        case recentChatMessages
    }

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
        rawProgramJson: String? = nil,
        recentChatMessages: [BroChatMessage] = []
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
        self.recentChatMessages = recentChatMessages
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        broId = try container.decode(String.self, forKey: .broId)
        name = try container.decode(String.self, forKey: .name)
        programKind = try container.decode(String.self, forKey: .programKind)
        programTitle = try container.decode(String.self, forKey: .programTitle)
        currentWeek = try container.decode(Int.self, forKey: .currentWeek)
        currentDay = try container.decode(Int.self, forKey: .currentDay)
        lastActiveEpoch = try container.decode(Int64.self, forKey: .lastActiveEpoch)
        squat5RM = try container.decode(Double.self, forKey: .squat5RM)
        bench5RM = try container.decode(Double.self, forKey: .bench5RM)
        deadlift5RM = try container.decode(Double.self, forKey: .deadlift5RM)
        recentLifts = try container.decodeIfPresent([BroLiftEntry].self, forKey: .recentLifts) ?? []
        programDays = try container.decodeIfPresent([BroWorkoutDay].self, forKey: .programDays) ?? []
        rawProgramJson = try container.decodeIfPresent(String.self, forKey: .rawProgramJson)
        recentChatMessages = try container.decodeIfPresent([BroChatMessage].self, forKey: .recentChatMessages) ?? []
    }

    func withBroId(_ newBroId: String) -> BroProfileData {
        BroProfileData(
            broId: newBroId,
            name: name,
            programKind: programKind,
            programTitle: programTitle,
            currentWeek: currentWeek,
            currentDay: currentDay,
            lastActiveEpoch: lastActiveEpoch,
            squat5RM: squat5RM,
            bench5RM: bench5RM,
            deadlift5RM: deadlift5RM,
            recentLifts: recentLifts,
            programDays: programDays,
            rawProgramJson: rawProgramJson,
            recentChatMessages: recentChatMessages
        )
    }

    var statusDescription: String {
        let diffSec = max(0, (Int64(Date().timeIntervalSince1970 * 1000) - lastActiveEpoch) / 1000)
        if diffSec < 300 {
            return "В сети / Только что тренировался"
        } else if diffSec < 3600 {
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
        let diffSec = max(0, (Int64(Date().timeIntervalSince1970 * 1000) - lastActiveEpoch) / 1000)
        return diffSec < 86400 * 2
    }

    var isOnline: Bool {
        let diffSec = max(0, (Int64(Date().timeIntervalSince1970 * 1000) - lastActiveEpoch) / 1000)
        return diffSec < 300
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

    /// Проверяет, является ли идентификатор подтверждённым серверным объектом, а не локальной временной заглушкой
    var isConfirmedServerId: Bool {
        !myBroId.isEmpty && !myBroId.hasPrefix("bro_")
    }

    // MARK: - Backend Server URL
    var backendBaseUrl: String {
        defaults.string(forKey: "strain_backend_url") ?? "https://strain-backend.onrender.com"
    }

    func setBackendUrl(_ url: String) {
        defaults.set(url, forKey: "strain_backend_url")
    }

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
        let curWeekPlan = profile.workoutPlan.weeks.first { $0.number == week }
        let nextDay = curWeekPlan?.days.first { !profile.isCompleted(week: week, day: $0.number) }
        let day = nextDay?.number ?? 1
        let sq = Int(profile.squat5RM)
        let bp = Int(profile.bench5RM)
        let dl = Int(profile.deadlift5RM)

        var link = "strain://bro?id=\(myBroId)&name=\(encodedName)&kind=\(kind)&w=\(week)&d=\(day)&sq=\(sq)&bp=\(bp)&dl=\(dl)"
        if let back = profile.back?.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed), !back.isEmpty {
            link += "&back=\(back)"
        }
        if let press = profile.press?.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed), !press.isEmpty {
            link += "&press=\(press)"
        }
        if let pull = profile.pull?.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed), !pull.isEmpty {
            link += "&pull=\(pull)"
        }
        if let arms = profile.arms?.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed), !arms.isEmpty {
            link += "&arms=\(arms)"
        }
        if let core = profile.core?.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed), !core.isEmpty {
            link += "&core=\(core)"
        }
        return link
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
        let schedule = profile.schedule
        let focus = schedule.focus
        let curWeek = focus?.week ?? profile.currentWeek
        let curDay = focus?.day.number ?? (plan.weeks.first { $0.number == curWeek }?.days.first { !profile.isCompleted(week: curWeek, day: $0.number) }?.number ?? 1)

        // Собираем все запланированные упражнения активного дня с учётом волны жима и правок
        var lifts: [BroLiftEntry] = []
        if let focus {
            let focusExercises = profile.exercises(for: focus)
            for ex in focusExercises {
                let load = ex.load.displayText
                let presc = ex.sets > 0 ? "\(ex.sets)×\(ex.reps) · \(load)" : "\(ex.reps) · \(load)"
                lifts.append(BroLiftEntry(name: ex.name, prescription: presc))
            }
        } else if let day = plan.weeks.first(where: { $0.number == curWeek })?.days.first {
            let exs = profile.exercises(day: day, benchSession: nil)
            for ex in exs {
                let load = ex.load.displayText
                let presc = ex.sets > 0 ? "\(ex.sets)×\(ex.reps) · \(load)" : "\(ex.reps) · \(load)"
                lifts.append(BroLiftEntry(name: ex.name, prescription: presc))
            }
        }

        // Сопоставление сессий жимовой волны для каждого дня плана
        let benchMap = Dictionary(
            schedule.allPending.compactMap { sw in sw.benchSession.map { ("\(sw.week)-\(sw.day.number)", $0) } },
            uniquingKeysWith: { first, _ in first }
        )

        // Собираем все дни плана для просмотра другом (все недели цикла без усечения)
        var programDays: [BroWorkoutDay] = []
        for week in plan.weeks {
            for day in week.days {
                let benchSession = benchMap["\(week.number)-\(day.number)"]
                let resolvedExercises = profile.exercises(day: day, benchSession: benchSession)
                let exs = resolvedExercises.map {
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
            currentWeek: curWeek,
            currentDay: curDay,
            lastActiveEpoch: Int64(Date().timeIntervalSince1970 * 1000),
            squat5RM: profile.squat5RM,
            bench5RM: profile.bench5RM,
            deadlift5RM: profile.deadlift5RM,
            recentLifts: lifts,
            programDays: programDays,
            recentChatMessages: loadOutbox()
        )

        // 1. Синхронизация с выделенным бэкендом STRAIN (без лимитов размера JSON)
        var backendSynced = false
        if let backendUrl = URL(string: "\(backendBaseUrl)/api/profile/\(myBroId)") {
            var req = URLRequest(url: backendUrl)
            req.httpMethod = "PUT"
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            req.timeoutInterval = 10
            if let bodyData = try? JSONEncoder().encode(myData) {
                req.httpBody = bodyData
                if let (_, response) = try? await URLSession.shared.data(for: req),
                   let httpRes = response as? HTTPURLResponse, (200...299).contains(httpRes.statusCode) {
                    backendSynced = true
                    self.lastError = nil
                }
            }
        }

        // 2. Fallback на api.restful-api.dev
        let endpoint = "https://api.restful-api.dev/objects"
        do {
            if isConfirmedServerId {
                // Серверный ID уже есть: пытаемся обновить существующий объект через PUT
                let apiObject = RestfulApiObject(id: myBroId, name: "STRAIN_BRO", data: myData)
                let bodyData = try JSONEncoder().encode(apiObject)

                var request = URLRequest(url: URL(string: "\(endpoint)/\(myBroId)")!)
                request.httpMethod = "PUT"
                request.setValue("application/json", forHTTPHeaderField: "Content-Type")
                request.httpBody = bodyData

                let (data, response) = try await URLSession.shared.data(for: request)
                if let httpRes = response as? HTTPURLResponse {
                    if (200...299).contains(httpRes.statusCode) {
                        if let decoded = try? JSONDecoder().decode(CreateResponse.self, from: data),
                           let newId = decoded.id, !newId.isEmpty {
                            self.myBroId = newId
                        }
                        self.lastError = nil
                    } else if httpRes.statusCode == 404 {
                        // Сервер удалил/сбросил объект (404 Not Found) — немедленный откат к созданию через POST
                        try await createRemoteProfile(myData: myData, endpoint: endpoint)
                    } else if !backendSynced {
                        self.lastError = "Ошибка сервера: HTTP \(httpRes.statusCode)"
                    }
                }
            } else {
                // Локальный временный ID (начинается с bro_) — выполняем первичное создание через POST
                try await createRemoteProfile(myData: myData, endpoint: endpoint)
            }
        } catch {
            if !backendSynced {
                self.lastError = error.localizedDescription
            }
        }
    }

    private func createRemoteProfile(myData: BroProfileData, endpoint: String) async throws {
        var createRequest = URLRequest(url: URL(string: endpoint)!)
        createRequest.httpMethod = "POST"
        createRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let createObject = RestfulApiObject(id: nil, name: "STRAIN_BRO", data: myData)
        createRequest.httpBody = try JSONEncoder().encode(createObject)

        let (data, response) = try await URLSession.shared.data(for: createRequest)
        if let httpRes = response as? HTTPURLResponse, (200...299).contains(httpRes.statusCode) {
            if let decoded = try? JSONDecoder().decode(CreateResponse.self, from: data),
               let newId = decoded.id, !newId.isEmpty {
                self.myBroId = newId

                // Обновляем remote object с новым server ID внутри data.broId, чтобы друзья получали верный ID
                let updatedData = myData.withBroId(newId)
                let updateObject = RestfulApiObject(id: newId, name: "STRAIN_BRO", data: updatedData)
                if let updateBody = try? JSONEncoder().encode(updateObject) {
                    var putReq = URLRequest(url: URL(string: "\(endpoint)/\(newId)")!)
                    putReq.httpMethod = "PUT"
                    putReq.setValue("application/json", forHTTPHeaderField: "Content-Type")
                    putReq.httpBody = updateBody
                    _ = try? await URLSession.shared.data(for: putReq)
                }
            }
            self.lastError = nil
        } else if let httpRes = response as? HTTPURLResponse {
            self.lastError = "Ошибка сервера: HTTP \(httpRes.statusCode)"
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

                let back = dict["back"]
                let press = dict["press"]
                let pull = dict["pull"]
                let arms = dict["arms"]
                let core = dict["core"]

                let days = generatePreviewDays(
                    kind: kind,
                    squat: sq,
                    bench: bp,
                    deadlift: dl,
                    back: back,
                    press: press,
                    pull: pull,
                    arms: arms,
                    core: core
                )
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

                // Сразу пытаемся обновить данные с сервера (полная программа, подсобные, актуальные веса)
                await refreshBuddies()

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

    private func generatePreviewDays(
        kind: String,
        squat: Double,
        bench: Double,
        deadlift: Double,
        back: String? = nil,
        press: String? = nil,
        pull: String? = nil,
        arms: String? = nil,
        core: String? = nil
    ) -> [BroWorkoutDay] {
        let progKind = TrainingProgramKind.allCases.first { $0.backupCode == kind || $0.rawValue == kind } ?? .texas
        let sq = squat > 0 ? squat : 100
        let bp = bench > 0 ? bench : 100
        let dl = deadlift > 0 ? deadlift : 100
        let dummy: ProgramProfile
        switch progKind {
        case .upperLower:
            dummy = ProgramProfile(upperLowerInput: UpperLowerInput(squat1RM: sq, bench1RM: bp, deadlift1RM: dl), name: "Preview")
        case .fullBody:
            dummy = ProgramProfile(fullBodyInput: ProgramInput(squat5RM: sq, bench5RM: bp, deadlift5RM: dl, level: .beginner, pull: pull, arms: arms, core: core, back: back, press: press), level: .aboutYear, name: "Preview")
        case .proTexas:
            dummy = ProgramProfile(proTexasInput: ProgramInput(squat5RM: sq, bench5RM: bp, deadlift5RM: dl, level: .beginner, pull: pull, arms: arms, core: core, back: back, press: press), name: "Preview")
        default:
            dummy = ProgramProfile(input: ProgramInput(squat5RM: sq, bench5RM: bp, deadlift5RM: dl, level: .beginner, pull: pull, arms: arms, core: core, back: back, press: press), name: "Preview")
        }
        var days: [BroWorkoutDay] = []
        for week in dummy.workoutPlan.weeks {
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
        // 1. Попытка загрузить с выделенного бэкенда STRAIN
        if let url = URL(string: "\(backendBaseUrl)/api/profile/\(id)") {
            var req = URLRequest(url: url)
            req.timeoutInterval = 6
            if let (data, response) = try? await URLSession.shared.data(for: req),
               let httpRes = response as? HTTPURLResponse, (200...299).contains(httpRes.statusCode),
               let buddy = try? JSONDecoder().decode(BroProfileData.self, from: data) {
                return buddy.broId.isEmpty ? buddy.withBroId(id) : buddy
            }
        }

        // 2. Fallback на restful-api.dev
        guard let url = URL(string: "https://api.restful-api.dev/objects/\(id)") else { return nil }
        do {
            let (data, response) = try await URLSession.shared.data(from: url)
            guard let httpRes = response as? HTTPURLResponse, (200...299).contains(httpRes.statusCode) else {
                return nil
            }
            let obj = try JSONDecoder().decode(RestfulApiObject.self, from: data)
            let serverId = obj.id ?? id
            return obj.data.broId == serverId ? obj.data : obj.data.withBroId(serverId)
        } catch {
            return nil
        }
    }

    // MARK: - Chat Management

    private let keyOutbox = "strain_my_outbox"

    func channelId(for buddyId: String) -> String {
        let ids = [myBroId, buddyId].sorted()
        return "chat_\(ids[0])_\(ids[1])"
    }

    func loadLocalMessages(channelId: String) -> [BroChatMessage] {
        guard let data = defaults.data(forKey: "strain_chat_\(channelId)"),
              let msgs = try? JSONDecoder().decode([BroChatMessage].self, from: data) else {
            return []
        }
        return msgs
    }

    func saveLocalMessages(_ messages: [BroChatMessage], channelId: String) {
        if let data = try? JSONEncoder().encode(messages) {
            defaults.set(data, forKey: "strain_chat_\(channelId)")
        }
    }

    func loadOutbox() -> [BroChatMessage] {
        guard let data = defaults.data(forKey: keyOutbox),
              let msgs = try? JSONDecoder().decode([BroChatMessage].self, from: data) else {
            return []
        }
        return msgs
    }

    func saveOutbox(_ msgs: [BroChatMessage]) {
        if let data = try? JSONEncoder().encode(msgs) {
            defaults.set(data, forKey: keyOutbox)
        }
    }

    /// Загружает свежие сообщения из облачного канала выделенного бэкенда
    func fetchRemoteMessages(for buddyId: String) async {
        let chId = channelId(for: buddyId)
        guard let url = URL(string: "\(backendBaseUrl)/api/chat/\(chId)/messages") else { return }
        var req = URLRequest(url: url)
        req.timeoutInterval = 5
        guard let (data, response) = try? await URLSession.shared.data(for: req),
              let httpRes = response as? HTTPURLResponse, (200...299).contains(httpRes.statusCode),
              let serverMsgs = try? JSONDecoder().decode([BroChatMessage].self, from: data) else {
            return
        }

        var local = loadLocalMessages(channelId: chId)
        let existingIds = Set(local.map { $0.id })
        var hasNew = false
        for m in serverMsgs where !existingIds.contains(m.id) {
            local.append(m)
            hasNew = true
        }
        if hasNew {
            local.sort { $0.timestamp < $1.timestamp }
            saveLocalMessages(local, channelId: chId)
        }
    }

    func getMessages(buddyId: String) -> [BroChatMessage] {
        let chId = channelId(for: buddyId)
        var local = loadLocalMessages(channelId: chId)

        if let buddy = buddies.first(where: { $0.broId == buddyId }) {
            let buddyMsgs = buddy.recentChatMessages.filter { $0.channelId == chId }
            var existingIds = Set(local.map { $0.id })
            for msg in buddyMsgs where !existingIds.contains(msg.id) {
                local.append(msg)
                existingIds.insert(msg.id)
            }
            local.sort { $0.timestamp < $1.timestamp }
            saveLocalMessages(local, channelId: chId)
        }
        return local
    }

    @discardableResult
    func sendMessage(
        to buddyId: String,
        text: String,
        type: BroMessageType = .text,
        workoutPayload: WorkoutSharePayload? = nil,
        photoBase64: String? = nil,
        profile: ProgramProfile? = nil
    ) async -> BroChatMessage {
        let chId = channelId(for: buddyId)
        let senderName = (profile?.name.isEmpty == false) ? profile!.name : "Бро"
        let msg = BroChatMessage(
            channelId: chId,
            senderId: myBroId,
            senderName: senderName,
            timestamp: Int64(Date().timeIntervalSince1970 * 1000),
            text: text,
            type: type,
            workoutPayload: workoutPayload,
            photoBase64: photoBase64
        )

        var current = loadLocalMessages(channelId: chId)
        current.append(msg)
        saveLocalMessages(current, channelId: chId)

        var outbox = loadOutbox()
        outbox.append(msg)
        if outbox.count > 25 {
            outbox.removeFirst(outbox.count - 25)
        }
        saveOutbox(outbox)

        // 1. Прямая отправка в выделенный канал бэкенда
        if let chatUrl = URL(string: "\(backendBaseUrl)/api/chat/\(chId)/messages") {
            var chatReq = URLRequest(url: chatUrl)
            chatReq.httpMethod = "POST"
            chatReq.setValue("application/json", forHTTPHeaderField: "Content-Type")
            chatReq.timeoutInterval = 8
            if let body = try? JSONEncoder().encode(msg) {
                chatReq.httpBody = body
                Task {
                    _ = try? await URLSession.shared.data(for: chatReq)
                }
            }
        }

        // 2. Обновление профиля
        if let profile = profile {
            await syncMyProfile(profile: profile)
        }
        return msg
    }
}

