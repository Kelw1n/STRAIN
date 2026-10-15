import Foundation

// MARK: - Достижения
//
// Восемь медалей по пять уровней. Всё считается на телефоне из того, что
// уже записано: отметок, подходов, замеров веса. На сервер уходят только
// итоговые уровни — их видят другие в профиле.

enum AchievementGroup: String, CaseIterable, Identifiable, Sendable {
    case volume = "ОБЪЁМ И СЧЁТ"
    case records = "РЕКОРДЫ И ПОСТОЯНСТВО"
    case strength = "ОТНОСИТЕЛЬНАЯ СИЛА"

    var id: String { rawValue }
}

enum AchievementKind: String, CaseIterable, Identifiable, Sendable {
    case path, iron, records, streak, bench, squat, deadlift, total

    var id: String { rawValue }

    var title: String {
        switch self {
        case .path: return "Путь"
        case .iron: return "Гора железа"
        case .records: return "Охота за рекордами"
        case .streak: return "Без пропусков"
        case .bench: return "Жим"
        case .squat: return "Присед"
        case .deadlift: return "Тяга"
        case .total: return "Троеборец"
        }
    }

    var symbol: String {
        switch self {
        case .path: return "mountain.2.fill"
        case .iron: return "scalemass.fill"
        case .records: return "trophy.fill"
        case .streak: return "flame.fill"
        case .bench: return "dumbbell.fill"
        case .squat: return "figure.strengthtraining.functional"
        case .deadlift: return "figure.strengthtraining.traditional"
        case .total: return "crown.fill"
        }
    }

    var group: AchievementGroup {
        switch self {
        case .path, .iron: return .volume
        case .records, .streak: return .records
        case .bench, .squat, .deadlift, .total: return .strength
        }
    }

    /// Пороги пяти уровней.
    var thresholds: [Double] {
        switch self {
        case .path: return [1, 15, 50, 100, 250]
        case .iron: return [1_000, 30_000, 100_000, 500_000, 1_000_000]
        case .records: return [1, 5, 20, 50, 100]
        case .streak: return [2, 4, 8, 16, 52]
        case .bench: return [0.75, 1, 1.25, 1.5, 2]
        case .squat: return [1, 1.25, 1.5, 2, 2.5]
        case .deadlift: return [1.25, 1.5, 2, 2.5, 3]
        case .total: return [3, 4, 5, 6, 7]
        }
    }

    /// Что считаем — подпись под шкалой.
    var caption: String {
        switch self {
        case .path: return "тренировок всего"
        case .iron: return "тоннаж за всё время"
        case .records: return "личных рекордов всего"
        case .streak: return "недель подряд, лучшая серия"
        case .bench: return "жим лёжа ÷ свой вес"
        case .squat: return "присед ÷ свой вес"
        case .deadlift: return "становая ÷ свой вес"
        case .total: return "сумма трёх ÷ свой вес"
        }
    }

    var isRatio: Bool { group == .strength }

    func format(_ value: Double) -> String {
        switch self {
        case .iron:
            return value < 1000 ? "\(Int(value.rounded())) кг" : WeightFormat.decimal((value / 100).rounded() / 10) + " т"
        case .bench, .squat, .deadlift, .total:
            return "×" + WeightFormat.decimal((value * 100).rounded() / 100)
        default:
            return "\(Int(value.rounded()))"
        }
    }
}

struct AchievementProgress: Identifiable, Hashable, Sendable {
    let kind: AchievementKind
    let value: Double
    /// Для силы к весу без замера веса считать не от чего.
    let needsBodyWeight: Bool

    var id: String { kind.rawValue }

    /// Взятые уровни, 0…5.
    var level: Int { kind.thresholds.filter { value >= $0 - 1e-9 }.count }
    var isMaxed: Bool { level >= kind.thresholds.count }
    var nextThreshold: Double? { isMaxed ? nil : kind.thresholds[level] }

    /// «0 из 15 · тренировок всего».
    var caption: String {
        if needsBodyWeight { return "Запиши вес тела · \(kind.caption)" }
        if let next = nextThreshold {
            return "\(kind.format(value)) из \(kind.format(next)) · \(kind.caption)"
        }
        return "\(kind.format(value)) · все уровни взяты"
    }

    /// Доля пути до следующего уровня внутри текущего отрезка шкалы, 0…1.
    var segmentFraction: Double {
        guard let next = nextThreshold else { return 1 }
        let start = level == 0 ? 0 : kind.thresholds[level - 1]
        guard next > start else { return 0 }
        return min(1, max(0, (value - start) / (next - start)))
    }
}

enum AchievementCalculator {
    static let squatNames = ["Приседания", "Приседания со штангой"]
    static let benchNames = ["Жим лёжа"]
    static let deadliftNames = ["Становая тяга"]

    static func progress(for profile: ProgramProfile, now: Date = Date()) -> [AchievementProgress] {
        let bodyWeight = profile.latestBodyWeight
        let squat = best(profile, names: squatNames, floor: profile.squat5RM)
        let bench = best(profile, names: benchNames, floor: profile.bench5RM)
        let deadlift = best(profile, names: deadliftNames, floor: profile.deadlift5RM)

        func ratio(_ lift: Double) -> Double {
            guard let bodyWeight, bodyWeight > 0 else { return 0 }
            return lift / bodyWeight
        }

        return AchievementKind.allCases.map { kind in
            let value: Double
            switch kind {
            case .path: value = Double(profile.completionLog.count)
            case .iron: value = profile.setLog.values.reduce(0) { $0 + $1.tonnage }
            case .records: value = Double(recordCount(profile))
            case .streak: value = Double(bestWeekStreak(profile.completionLog.map(\.date)))
            case .bench: value = ratio(bench)
            case .squat: value = ratio(squat)
            case .deadlift: value = ratio(deadlift)
            case .total: value = ratio(squat + bench + deadlift)
            }
            return AchievementProgress(kind: kind, value: value, needsBodyWeight: kind.isRatio && bodyWeight == nil)
        }
    }

    /// Медали на показ: три самых высоких взятых уровня.
    static func badges(from progress: [AchievementProgress], limit: Int = 3) -> [SocialBadge] {
        let order = Dictionary(uniqueKeysWithValues: AchievementKind.allCases.enumerated().map { ($1, $0) })
        return progress
            .filter { $0.level > 0 }
            .sorted { $0.level == $1.level ? (order[$0.kind] ?? 0) < (order[$1.kind] ?? 0) : $0.level > $1.level }
            .prefix(limit)
            .map { SocialBadge(id: $0.kind.rawValue, level: $0.level) }
    }

    /// Лучший поднятый вес в движении: из записанных подходов или из максимума,
    /// с которого начата программа, — он тоже был поднят по-настоящему.
    static func best(_ profile: ProgramProfile, names: [String], floor: Double) -> Double {
        var best = floor
        for name in names {
            for point in profile.history(forExerciseNamed: name) {
                best = max(best, point.best)
            }
        }
        return best
    }

    /// Рекорд — тренировка, где лучший вес движения выше всех прошлых.
    /// Первая тренировка движения рекордом не считается: побить ещё нечего.
    static func recordCount(_ profile: ProgramProfile) -> Int {
        var count = 0
        for name in profile.loggedExerciseNames {
            var top: Double?
            for point in profile.history(forExerciseNamed: name) where point.best > 0 {
                if let current = top {
                    if point.best > current + 1e-9 {
                        count += 1
                        top = point.best
                    }
                } else {
                    top = point.best
                }
            }
        }
        return count
    }

    /// Самая длинная серия календарных недель (пн–вс), в каждой из которых была тренировка.
    static func bestWeekStreak(_ dates: [Date], calendar base: Calendar = .current) -> Int {
        var calendar = base
        calendar.firstWeekday = 2
        calendar.minimumDaysInFirstWeek = 4
        let weeks = Set(dates.compactMap { calendar.dateInterval(of: .weekOfYear, for: $0)?.start })
        let sorted = weeks.sorted()
        var best = 0
        var run = 0
        var previous: Date?
        for week in sorted {
            if let previous, let expected = calendar.date(byAdding: .weekOfYear, value: 1, to: previous),
               calendar.isDate(expected, inSameDayAs: week) {
                run += 1
            } else {
                run = 1
            }
            best = max(best, run)
            previous = week
        }
        return best
    }
}

// MARK: - Программа для профиля

enum SocialProgramBuilder {
    /// Дни текущей недели программы: название, две главные группы мышц и упражнения.
    static func days(for profile: ProgramProfile) -> [SocialProgramDay] {
        let plan = profile.workoutPlan
        let week = plan.weeks.first { $0.number == profile.currentWeek } ?? plan.weeks.first
        guard let week else { return [] }
        return week.days.map { day in
            var sets: [String: Int] = [:]
            for exercise in day.exercises {
                let group = shortGroup(MuscleCategory.categorize(exerciseName: exercise.name))
                sets[group, default: 0] += exercise.sets
            }
            let groups = sets.sorted { $0.value == $1.value ? $0.key < $1.key : $0.value > $1.value }.prefix(2).map(\.key)
            return SocialProgramDay(
                title: day.title,
                groups: groups,
                exercises: day.exercises.map {
                    SocialProgramDay.Exercise(name: $0.name, sets: $0.sets, reps: $0.reps, load: $0.load.displayText)
                }
            )
        }
    }

    static func shortGroup(_ category: MuscleCategory) -> String {
        switch category {
        case .chest: return "Грудь"
        case .back: return "Спина"
        case .legs: return "Ноги"
        case .shoulders: return "Плечи"
        case .arms: return "Руки"
        }
    }
}
