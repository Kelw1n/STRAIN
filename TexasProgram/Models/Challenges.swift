import Foundation

// MARK: - Челленджи с друзьями
//
// Сервер хранит челлендж и счёт участников, а сам счёт считает приложение:
// вся история тренировок живёт на телефоне. Правила счёта совпадают с Android
// до единицы — одна и та же история даёт один и тот же счёт.

enum ChallengeKind: String, Codable, CaseIterable, Identifiable, Sendable {
    case workouts, volume, streak, strength

    var id: String { rawValue }

    var title: String {
        switch self {
        case .workouts: return "Больше тренировок"
        case .volume: return "Больше тоннаж"
        case .streak: return "Без пропусков"
        case .strength: return "Рост силы"
        }
    }

    var subtitle: String {
        switch self {
        case .workouts: return "Кто чаще придёт в зал"
        case .volume: return "Кто поднимет больше килограммов за всё время"
        case .streak: return "Цель недели каждую неделю — кто дольше продержится"
        case .strength: return "Прибавка в силе к весу тела — честно для лёгких и тяжёлых"
        }
    }

    var symbol: String {
        switch self {
        case .workouts: return "flame.fill"
        case .volume: return "scalemass.fill"
        case .streak: return "calendar.badge.checkmark"
        case .strength: return "chart.line.uptrend.xyaxis"
        }
    }
}

struct SocialChallenge: Codable, Hashable, Identifiable, Sendable {
    struct Member: Codable, Hashable, Sendable {
        let user: SocialUserShort
        var status: String
        var score: Double
        var detail: String
        var place: Int?

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            user = try c.decode(SocialUserShort.self, forKey: .user)
            status = try c.decodeIfPresent(String.self, forKey: .status) ?? "invited"
            score = (try? c.decodeIfPresent(Double.self, forKey: .score)) ?? 0
            detail = (try? c.decodeIfPresent(String.self, forKey: .detail)) ?? ""
            place = try? c.decodeIfPresent(Int.self, forKey: .place)
        }
    }

    let id: String
    /// Вид строкой: челлендж нового вида из будущей версии не ломает список.
    let kindRaw: String
    let weeks: Int
    let startsAt: Double
    let endsAt: Double
    var status: String
    var owner: SocialUserShort?
    var myStatus: String?
    var members: [Member]

    enum CodingKeys: String, CodingKey {
        case id, kindRaw = "kind", weeks, startsAt, endsAt, status, owner, myStatus, members
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        kindRaw = try c.decodeIfPresent(String.self, forKey: .kindRaw) ?? ""
        weeks = try c.decodeIfPresent(Int.self, forKey: .weeks) ?? 1
        startsAt = try c.decodeIfPresent(Double.self, forKey: .startsAt) ?? 0
        endsAt = try c.decodeIfPresent(Double.self, forKey: .endsAt) ?? 0
        status = try c.decodeIfPresent(String.self, forKey: .status) ?? "active"
        owner = try? c.decodeIfPresent(SocialUserShort.self, forKey: .owner)
        myStatus = try? c.decodeIfPresent(String.self, forKey: .myStatus)
        members = (try? c.decodeIfPresent([Member].self, forKey: .members)) ?? []
    }

    var kind: ChallengeKind? { ChallengeKind(rawValue: kindRaw) }
    var title: String { kind?.title ?? "Челлендж" }
    var start: Date { Date(timeIntervalSince1970: startsAt / 1000) }
    var end: Date { Date(timeIntervalSince1970: endsAt / 1000) }
    var joined: [Member] { members.filter { $0.status == "joined" } }
    var invited: [Member] { members.filter { $0.status == "invited" } }
    var me: Member? { members.first { $0.user.isMe } }
    var isInvite: Bool { myStatus == "invited" && status != "finished" }
    var isFinished: Bool { status == "finished" }

    /// Сутки после конца сервер ещё принимает счёт — потом места окончательные.
    func isFinal(now: Date = Date()) -> Bool { now.timeIntervalSince1970 * 1000 > endsAt + ChallengeScoring.graceMs }

    /// «Осталось 5 дней», «Начнётся завтра», «Закончился 3 окт.».
    func timeLine(now: Date = Date(), calendar: Calendar = WeeklyStatsBuilder.calendar) -> String {
        if isFinished { return "Закончился \(RuDate.shortDayMonth(end.addingTimeInterval(-1), calendar: calendar))" }
        let today = calendar.startOfDay(for: now)
        if now < start {
            let days = calendar.dateComponents([.day], from: today, to: calendar.startOfDay(for: start)).day ?? 0
            return days <= 1 ? "Начнётся завтра" : "Начнётся через \(days) \(RuPlural.word(days, "день", "дня", "дней"))"
        }
        let left = max(1, calendar.dateComponents([.day], from: today, to: calendar.startOfDay(for: end)).day ?? 1)
        return "Осталось \(left) \(RuPlural.word(left, "день", "дня", "дней"))"
    }
}

/// Счёт для сервера: один запрос сразу по всем своим челленджам.
struct ChallengeScoreUpdate: Codable, Hashable, Sendable {
    let id: String
    let score: Double
    let detail: String
}

/// Победитель прошлой недели в рейтинге.
struct SocialWeekWinner: Codable, Hashable, Identifiable, Sendable {
    let week: String
    let user: SocialUserShort
    var workouts: Int
    var volumeKg: Double

    var id: String { week }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        week = try c.decode(String.self, forKey: .week)
        user = try c.decode(SocialUserShort.self, forKey: .user)
        workouts = (try? c.decodeIfPresent(Int.self, forKey: .workouts)) ?? 0
        volumeKg = (try? c.decodeIfPresent(Double.self, forKey: .volumeKg)) ?? 0
    }
}

/// Опыт за место в законченном челлендже — лежит в профиле и едет в копии.
struct ChallengeAward: Codable, Equatable, Hashable, Sendable {
    var id: String
    var title: String
    var place: Int
    var xp: Int
    var epochDay: Int
}

// MARK: - Счёт

enum ChallengeScoring {
    static let graceMs: Double = 24 * 60 * 60 * 1000

    struct Result: Equatable, Sendable {
        let score: Double
        let detail: String
    }

    /// Опыт за место: первое +300, второе +200, третье +100, остальным за участие +50.
    /// Нужны хотя бы двое участников и ненулевой счёт.
    static func xp(place: Int?, participants: Int, score: Double) -> Int {
        guard participants >= 2, score > 0, let place else { return 0 }
        switch place {
        case 1: return 300
        case 2: return 200
        case 3: return 100
        default: return 50
        }
    }

    static func result(for challenge: SocialChallenge, profile: ProgramProfile, now: Date = Date(),
                       calendar: Calendar = WeeklyStatsBuilder.calendar) -> Result? {
        guard let kind = challenge.kind else { return nil }
        return result(
            kind: kind,
            log: profile.completionLog,
            sets: profile.setLog,
            tonnage: { profile.tonnage(forDayKey: $0) },
            bodyWeight: profile.latestBodyWeight,
            weeklyGoal: Progression.weeklyGoal(for: profile),
            start: challenge.start,
            weeks: challenge.weeks,
            now: now,
            calendar: calendar
        )
    }

    /// Счёт за окно челленджа: с полуночи дня начала на `weeks` недель.
    static func result(
        kind: ChallengeKind,
        log: [CompletionRecord],
        sets: [String: SetEntry],
        tonnage: (String) -> Double,
        bodyWeight: Double?,
        weeklyGoal: Int,
        start: Date,
        weeks: Int,
        now: Date = Date(),
        calendar: Calendar = WeeklyStatsBuilder.calendar
    ) -> Result {
        let startDay = calendar.startOfDay(for: start)
        let length = max(1, weeks) * 7
        func index(_ date: Date) -> Int {
            calendar.dateComponents([.day], from: startDay, to: calendar.startOfDay(for: date)).day ?? -1
        }
        // Повтор ключа — та же тренировка: берём последнюю дату.
        var latest: [String: Date] = [:]
        for record in log { latest[record.key] = max(latest[record.key] ?? record.date, record.date) }
        let days = latest.mapValues(index)
        let inWindow = days.filter { $0.value >= 0 && $0.value < length }
        let count = inWindow.count

        switch kind {
        case .workouts:
            return Result(score: Double(count), detail: "\(count) \(RuPlural.word(count, "тренировка", "тренировки", "тренировок"))")

        case .volume:
            let volume = inWindow.keys.sorted().reduce(0.0) { $0 + tonnage($1) }
            let score = (volume * 10).rounded() / 10
            return Result(score: score, detail: score >= 1000 ? "\(decimal(score / 1000)) т" : "\(Int(score.rounded())) кг")

        case .streak:
            let goal = min(3, max(1, weeklyGoal))
            let todayIndex = index(now)
            var run = 0
            for week in 0..<max(1, weeks) where week * 7 <= todayIndex {
                let done = inWindow.values.filter { $0 >= week * 7 && $0 < week * 7 + 7 }.count
                guard done >= goal else { break }
                run += 1
            }
            return Result(score: Double(run * 100 + count), detail: "\(run) из \(max(1, weeks)) нед.")

        case .strength:
            // Лучший расчётный разовый максимум в каждом движении: до начала и за окно.
            var before: [String: Double] = [:]
            var during: [String: Double] = [:]
            for (key, entry) in sets where entry.reps > 0 && entry.reps <= 12 && entry.weight > 0 {
                let parts = key.split(separator: "|", omittingEmptySubsequences: false)
                guard parts.count >= 3, let day = days[String(parts[0])], day < length else { continue }
                let exercise = String(parts[1])
                let max1 = entry.reps == 1 ? entry.weight : entry.weight * (1 + Double(entry.reps) / 30)
                if day < 0 { before[exercise] = max(before[exercise] ?? 0, max1) }
                else { during[exercise] = max(during[exercise] ?? 0, max1) }
            }
            var gain = 0.0
            for exercise in during.keys.sorted() {
                guard let base = before[exercise], let best = during[exercise] else { continue }
                gain += max(0, best - base)
            }
            guard let bodyWeight, bodyWeight > 0 else {
                return Result(score: 0, detail: "+\(decimal(gain)) кг · нет веса тела")
            }
            let score = (gain / bodyWeight * 1000).rounded() / 10
            return Result(score: score, detail: "+\(decimal(gain)) кг · \(decimal(score))% веса")
        }
    }

    /// Одна цифра после запятой, без «,0»: одинаково на iPhone и Android.
    static func decimal(_ value: Double) -> String {
        let tenths = Int((value * 10).rounded())
        let whole = tenths / 10
        let fraction = abs(tenths % 10)
        return fraction == 0 ? "\(whole)" : "\(whole),\(fraction)"
    }
}
