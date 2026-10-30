import Foundation

// MARK: - Рекорды для профиля

/// Лучший подход движения за всё время: вес, повторы, расчётный максимум и дата.
/// Считается на телефоне по всем циклам и уходит в профиль — друзья видят витрину.
struct SocialRecord: Codable, Hashable, Identifiable, Sendable {
    var id: String { name }
    let name: String
    let weight: Double
    let reps: Int
    /// Расчётный разовый максимум по Эпли, до 0,5 кг.
    let estimate: Double
    /// Когда поставлен: миллисекунды, как остальные даты сервера.
    let date: Double
    /// squat / bench / deadlift — для витрины и суммы троеборья.
    var lift: String?

    init(name: String, weight: Double, reps: Int, estimate: Double, date: Double, lift: String?) {
        self.name = name
        self.weight = weight
        self.reps = reps
        self.estimate = estimate
        self.date = date
        self.lift = lift
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        name = try c.decode(String.self, forKey: .name)
        weight = try c.decode(Double.self, forKey: .weight)
        reps = try c.decode(Int.self, forKey: .reps)
        estimate = try c.decodeIfPresent(Double.self, forKey: .estimate) ?? PersonalRecords.estimate(weight: weight, reps: reps)
        date = try c.decodeIfPresent(Double.self, forKey: .date) ?? 0
        lift = try? c.decodeIfPresent(String.self, forKey: .lift)
    }

    var setText: String { "\(WeightFormat.plain(weight)) × \(reps)" }
}

enum PersonalRecords {
    enum Lift: String, CaseIterable, Sendable {
        case squat, bench, deadlift

        var title: String {
            switch self {
            case .squat: return "Присед"
            case .bench: return "Жим лёжа"
            case .deadlift: return "Становая"
            }
        }
    }

    /// Сколько движений уходит в профиль: витрине хватает, сервер не распухает.
    static let limit = 30

    /// Присед, жим лёжа или становая — по названию, тем же разбором, что и картинки.
    /// Жим должен быть лёжа: жим стоя и жим ногами в сумму не идут.
    static func lift(for name: String) -> Lift? {
        switch ExerciseArt.key(for: name) {
        case "squat": return .squat
        case "deadlift": return .deadlift
        case "bench": return ProgramProfile.weightStepKey(name).contains("леж") ? .bench : nil
        default: return nil
        }
    }

    /// Эпли: 140 × 5 ≈ 163. Дальше двенадцати повторов формула врёт — их не считаем.
    static func estimate(weight: Double, reps: Int) -> Double {
        guard weight > 0, reps > 0 else { return 0 }
        let raw = reps == 1 ? weight : weight * (1 + Double(min(reps, 12)) / 30)
        return (raw * 2).rounded() / 2
    }

    static func records(for profile: ProgramProfile) -> [SocialRecord] {
        records(log: profile.completionLog, sets: profile.setLog, names: names(for: profile))
    }

    /// Лучший подход каждого движения по всем циклам: только из отмеченных
    /// тренировок, дата — день, когда тренировку закрыли.
    static func records(log: [CompletionRecord], sets: [String: SetEntry], names: [String: String]) -> [SocialRecord] {
        var dates: [String: Date] = [:]
        for record in log { dates[record.key] = max(dates[record.key] ?? record.date, record.date) }
        var best: [String: SocialRecord] = [:]
        for (key, entry) in sets where entry.weight > 0 && entry.reps > 0 {
            let parts = key.split(separator: "|", omittingEmptySubsequences: false)
            guard parts.count >= 3, let date = dates[String(parts[0])] else { continue }
            let id = String(parts[1])
            guard let name = names[id] ?? (isGenerated(id) ? nil : id) else { continue }
            let candidate = SocialRecord(
                name: name,
                weight: entry.weight,
                reps: entry.reps,
                estimate: estimate(weight: entry.weight, reps: entry.reps),
                date: (date.timeIntervalSince1970 * 1000).rounded(),
                lift: lift(for: name)?.rawValue
            )
            let group = ProgramProfile.weightStepKey(name)
            if let current = best[group], !isBetter(candidate, than: current) { continue }
            best[group] = candidate
        }
        return Array(best.values.sorted(by: isBetter).prefix(limit))
    }

    /// Лучший в движении для витрины: у приседа это может быть и вариант приседа.
    static func best(_ records: [SocialRecord], lift: Lift) -> SocialRecord? {
        records.filter { $0.lift == lift.rawValue }.min(by: isBetter)
    }

    /// Сумма троеборья по расчётным максимумам — только когда есть все три движения.
    static func total(_ records: [SocialRecord]) -> Double? {
        let bests = Lift.allCases.compactMap { best(records, lift: $0) }
        guard bests.count == Lift.allCases.count else { return nil }
        return bests.reduce(0) { $0 + $1.estimate }
    }

    /// Больше расчётный максимум; при равном — тяжелее вес, потом раньше поставлен.
    static func isBetter(_ lhs: SocialRecord, than rhs: SocialRecord) -> Bool {
        if lhs.estimate != rhs.estimate { return lhs.estimate > rhs.estimate }
        if lhs.weight != rhs.weight { return lhs.weight > rhs.weight }
        if lhs.date != rhs.date { return lhs.date < rhs.date }
        return lhs.name < rhs.name
    }

    /// Название по идентификатору упражнения: у программы он и есть название,
    /// у своих и добавленных — случайный, его ищем в правках и своей программе.
    static func names(for profile: ProgramProfile) -> [String: String] {
        var map: [String: String] = [:]
        for edit in profile.planEdits where edit.kind == .add || edit.kind == .replace {
            map[edit.id] = edit.name
        }
        for day in profile.customProgram?.days ?? [] {
            for exercise in day.exercises { map[exercise.id] = exercise.name }
        }
        for week in profile.workoutPlan.weeks {
            for day in week.days {
                for exercise in day.exercises { map[exercise.id] = exercise.name }
            }
        }
        return map
    }

    private static func isGenerated(_ id: String) -> Bool {
        id.hasPrefix("custom-") || id.hasPrefix("own-")
    }
}
