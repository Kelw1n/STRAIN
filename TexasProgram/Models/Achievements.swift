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
        // История всех движений — одним проходом: дальше медали только читают её.
        let history = profile.historyByName()
        let squat = best(history.points, names: squatNames, floor: profile.squat5RM)
        let bench = best(history.points, names: benchNames, floor: profile.bench5RM)
        let deadlift = best(history.points, names: deadliftNames, floor: profile.deadlift5RM)

        func ratio(_ lift: Double) -> Double {
            guard let bodyWeight, bodyWeight > 0 else { return 0 }
            return lift / bodyWeight
        }

        return AchievementKind.allCases.map { kind in
            let value: Double
            switch kind {
            case .path: value = Double(profile.completionLog.count)
            case .iron: value = profile.setLog.values.reduce(0) { $0 + $1.tonnage }
            case .records: value = Double(recordCount(names: history.names, points: history.points))
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
        best(profile.historyByName().points, names: names, floor: floor)
    }

    /// То же по готовой истории всех движений.
    static func best(_ history: [String: [ExerciseHistoryPoint]], names: [String], floor: Double) -> Double {
        var best = floor
        for name in names {
            for point in history[name] ?? [] {
                best = max(best, point.best)
            }
        }
        return best
    }

    /// Рекорд — тренировка, где лучший вес движения выше всех прошлых.
    /// Первая тренировка движения рекордом не считается: побить ещё нечего.
    static func recordCount(_ profile: ProgramProfile) -> Int {
        let history = profile.historyByName()
        return recordCount(names: history.names, points: history.points)
    }

    /// То же по готовой истории: имена — в порядке программы.
    static func recordCount(names: [String], points history: [String: [ExerciseHistoryPoint]]) -> Int {
        var count = 0
        for name in names {
            var top: Double?
            for point in history[name] ?? [] where point.best > 0 {
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

// MARK: - Сводка недели для рейтинга

/// Неделя — с понедельника. Сводку считает телефон: в ней все тренировки,
/// а не только выложенные в ленту.
enum WeeklyStatsBuilder {
    static var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.firstWeekday = 2
        calendar.minimumDaysInFirstWeek = 4
        return calendar
    }

    /// «2026-W42» — по нему видно, что сводка этой недели, а не прошлой.
    static func weekKey(for date: Date, calendar: Calendar = WeeklyStatsBuilder.calendar) -> String {
        let parts = calendar.dateComponents([.yearForWeekOfYear, .weekOfYear], from: date)
        return String(format: "%04d-W%02d", parts.yearForWeekOfYear ?? 0, parts.weekOfYear ?? 0)
    }

    static func stats(for profile: ProgramProfile, now: Date = Date(), calendar: Calendar = WeeklyStatsBuilder.calendar) -> SocialWeekStats {
        guard let week = calendar.dateInterval(of: .weekOfYear, for: now) else {
            return SocialWeekStats(week: weekKey(for: now, calendar: calendar), workouts: 0, volumeKg: 0, records: 0, streakWeeks: 0)
        }
        let log = profile.completionLog
        let thisWeek = log.filter { week.contains($0.date) }
        let volume = thisWeek.reduce(0.0) { $0 + profile.tonnage(forDayKey: $1.key) }

        // Рекорд недели — тренировка этой недели, где лучший вес движения выше всех прошлых.
        let dates = Dictionary(log.map { ($0.key, $0.date) }, uniquingKeysWith: { max($0, $1) })
        var records = 0
        let history = profile.historyByName()
        for name in history.names {
            var top: Double?
            for point in history.points[name] ?? [] where point.best > 0 {
                if let current = top {
                    if point.best > current + 1e-9 {
                        top = point.best
                        if let date = dates[profile.dayKey(week: point.week, day: point.day)], week.contains(date) { records += 1 }
                    }
                } else {
                    top = point.best
                }
            }
        }

        let experience = Progression.xp(for: profile)
        return SocialWeekStats(
            week: weekKey(for: now, calendar: calendar),
            workouts: thisWeek.count,
            volumeKg: (volume * 10).rounded() / 10,
            records: records,
            streakWeeks: currentStreak(log.map(\.date), now: now, calendar: calendar),
            level: Progression.level(forXP: experience).level,
            xp: experience
        )
    }

    /// Недели подряд с тренировкой, считая эту. Эта ещё пустая — серия
    /// не прервана, пока не кончилась: считаем с прошлой.
    static func currentStreak(_ dates: [Date], now: Date = Date(), calendar: Calendar = WeeklyStatsBuilder.calendar) -> Int {
        let weeks = Set(dates.compactMap { calendar.dateInterval(of: .weekOfYear, for: $0)?.start })
        guard var cursor = calendar.dateInterval(of: .weekOfYear, for: now)?.start else { return 0 }
        if !weeks.contains(cursor) {
            guard let previous = calendar.date(byAdding: .weekOfYear, value: -1, to: cursor) else { return 0 }
            cursor = previous
        }
        var count = 0
        while weeks.contains(cursor) {
            count += 1
            guard let previous = calendar.date(byAdding: .weekOfYear, value: -1, to: cursor) else { break }
            cursor = previous
        }
        return count
    }

    /// Сводка из рейтинга на эту неделю: прошлонедельная — нули, а серия
    /// живёт, только если прошлая неделя была последней с тренировкой.
    static func current(_ stats: SocialWeekStats, now: Date = Date(), calendar: Calendar = WeeklyStatsBuilder.calendar) -> SocialWeekStats {
        let key = weekKey(for: now, calendar: calendar)
        if stats.week == key { return stats }
        let previous = calendar.date(byAdding: .weekOfYear, value: -1, to: now).map { weekKey(for: $0, calendar: calendar) }
        let streak = stats.week == previous && stats.workouts > 0 ? stats.streakWeeks : 0
        // Уровень не обнуляется с неделей — его копят за всю историю.
        return SocialWeekStats(week: key, workouts: 0, volumeKg: 0, records: 0, streakWeeks: streak, level: stats.level, xp: stats.xp)
    }
}

// MARK: - Опыт и уровни

/// Опыт за то, что человек и так делает, и уровни 1–50 со званиями.
///
/// Считается заново по всей истории — по завершённым тренировкам и записанным
/// подходам всех циклов, поэтому старые тренировки тоже приносят опыт, а
/// уровень после переноса копии на другой телефон тот же. Правила совпадают
/// с Android до единицы: одна и та же история — один и тот же уровень.
enum Progression {
    static let workoutXP = 100
    static let setXP = 5
    static let recordXP = 50
    static let weekGoalXP = 150
    /// Серия недель с тренировкой: каждая следующая неделя +10% к опыту недели, до +50%.
    static let maxStreakSteps = 5
    static let maxLevel = 50

    struct Level: Equatable, Sendable {
        let level: Int
        let xp: Int
        /// Опыт, с которого начинается этот уровень.
        let floor: Int
        /// С которого начнётся следующий; на пятидесятом — нет.
        let next: Int?

        var title: String { Progression.title(for: level) }

        /// Доля пути до следующего уровня, 0…1.
        var fraction: Double {
            guard let next, next > floor else { return 1 }
            return min(1, max(0, Double(xp - floor) / Double(next - floor)))
        }

        var toNext: Int? { next.map { max(0, $0 - xp) } }
    }

    /// Опыт, с которого начинается уровень: 60 × (уровень − 1)².
    /// Пятый — после пары недель, двадцатый — месяцев через пять, пятидесятый — через годы.
    static func threshold(for level: Int) -> Int {
        let step = max(0, min(level, maxLevel) - 1)
        return 60 * step * step
    }

    static func level(forXP xp: Int) -> Level {
        var level = 1
        while level < maxLevel && xp >= threshold(for: level + 1) { level += 1 }
        return Level(level: level, xp: xp, floor: threshold(for: level), next: level < maxLevel ? threshold(for: level + 1) : nil)
    }

    static func title(for level: Int) -> String {
        switch level {
        case ..<5: return "Новичок"
        case ..<10: return "Любитель"
        case ..<15: return "Атлет"
        case ..<20: return "Силач"
        case ..<25: return "Ветеран зала"
        case ..<30: return "Мастер"
        case ..<35: return "Железный"
        case ..<40: return "Титан"
        case ..<45: return "Чемпион"
        case ..<50: return "Икона зала"
        default: return "Легенда"
        }
    }

    /// Цель недели для бонуса: тренировок в неделе программы, но не больше трёх —
    /// иначе смена программы на четырёхдневную отнимала бы опыт за прошлые недели.
    static func weeklyGoal(for profile: ProgramProfile) -> Int { min(3, max(1, profile.trainingDayCount)) }

    static func level(for profile: ProgramProfile) -> Level { level(forXP: xp(for: profile)) }

    static func xp(for profile: ProgramProfile, excluding key: String? = nil) -> Int {
        let log = key.map { excluded in profile.completionLog.filter { $0.key != excluded } } ?? profile.completionLog
        return xp(log: log, sets: profile.setLog, weeklyGoal: weeklyGoal(for: profile))
            + profile.challengeAwards.reduce(0) { $0 + $1.xp }
    }

    /// Опыт по истории. Порядок тренировок — по дню, при равенстве — по ключу,
    /// чтобы рекорды считались одинаково на любом телефоне.
    static func xp(log: [CompletionRecord], sets: [String: SetEntry], weeklyGoal: Int, calendar: Calendar = WeeklyStatsBuilder.calendar) -> Int {
        let workouts = orderedWorkouts(log, calendar: calendar)
        guard !workouts.isEmpty else { return 0 }

        // Подходы по дню тренировки: ключ подхода — «день|упражнение|номер».
        var byDay: [String: [(exercise: String, entry: SetEntry)]] = [:]
        for (key, entry) in sets where entry.reps > 0 {
            let parts = key.split(separator: "|", omittingEmptySubsequences: false)
            guard parts.count >= 3 else { continue }
            byDay[String(parts[0]), default: []].append((String(parts[1]), entry))
        }

        var top: [String: Double] = [:]
        var weekXP: [Date: Int] = [:]
        var weekCount: [Date: Int] = [:]
        for workout in workouts {
            let daySets = byDay[workout.key] ?? []
            var best: [String: Double] = [:]
            for item in daySets where item.entry.weight > 0 {
                best[item.exercise] = max(best[item.exercise] ?? 0, item.entry.weight)
            }
            var records = 0
            for exercise in best.keys.sorted() {
                guard let weight = best[exercise] else { continue }
                if let current = top[exercise] {
                    if weight > current + 1e-9 {
                        records += 1
                        top[exercise] = weight
                    }
                } else {
                    top[exercise] = weight
                }
            }
            let gained = workoutXP + setXP * daySets.count + recordXP * records
            weekXP[workout.week, default: 0] += gained
            weekCount[workout.week, default: 0] += 1
        }

        let goal = min(3, max(1, weeklyGoal))
        var total = 0
        var run = 0
        var previous: Date?
        for week in weekXP.keys.sorted() {
            if let previous, let expected = calendar.date(byAdding: .weekOfYear, value: 1, to: previous),
               calendar.isDate(expected, inSameDayAs: week) {
                run += 1
            } else {
                run = 1
            }
            previous = week
            var points = weekXP[week] ?? 0
            if (weekCount[week] ?? 0) >= goal { points += weekGoalXP }
            // Целочисленно: одинаковое округление на iPhone и Android.
            total += points * (10 + min(run - 1, maxStreakSteps)) / 10
        }
        return total
    }

    private struct Workout {
        let key: String
        let day: Date
        let week: Date
    }

    private static func orderedWorkouts(_ log: [CompletionRecord], calendar: Calendar) -> [Workout] {
        // Повтор ключа — та же тренировка: берём последнюю дату.
        var latest: [String: Date] = [:]
        for record in log { latest[record.key] = max(latest[record.key] ?? record.date, record.date) }
        return latest.compactMap { key, date in
            let day = calendar.startOfDay(for: date)
            guard let week = calendar.dateInterval(of: .weekOfYear, for: day)?.start else { return nil }
            return Workout(key: key, day: day, week: week)
        }
        .sorted { $0.day == $1.day ? $0.key < $1.key : $0.day < $1.day }
    }

    // MARK: Награды месяца

    enum MonthTier: Int, Comparable, Sendable {
        case bronze = 1, silver, gold

        static func < (lhs: MonthTier, rhs: MonthTier) -> Bool { lhs.rawValue < rhs.rawValue }

        var title: String {
            switch self {
            case .bronze: return "Бронза"
            case .silver: return "Серебро"
            case .gold: return "Золото"
            }
        }

        /// Сколько тренировок за месяц нужно.
        var workouts: Int {
            switch self {
            case .bronze: return 8
            case .silver: return 12
            case .gold: return 16
            }
        }
    }

    /// Награда за месяц: получить её можно только в этом месяце, потом она остаётся.
    struct MonthAward: Identifiable, Equatable, Sendable {
        let year: Int
        let month: Int
        let workouts: Int
        let tier: MonthTier

        var id: String { "\(year)-\(month)" }
        var title: String { "\(Progression.monthNames[month]) \(year)" }
    }

    static let monthNames = ["", "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
                             "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"]

    static func tier(forWorkouts count: Int) -> MonthTier? {
        if count >= MonthTier.gold.workouts { return .gold }
        if count >= MonthTier.silver.workouts { return .silver }
        if count >= MonthTier.bronze.workouts { return .bronze }
        return nil
    }

    /// Награды по месяцам — новые первыми.
    static func monthAwards(log: [CompletionRecord], calendar: Calendar = WeeklyStatsBuilder.calendar) -> [MonthAward] {
        var counts: [String: (year: Int, month: Int, count: Int)] = [:]
        for workout in orderedWorkouts(log, calendar: calendar) {
            let parts = calendar.dateComponents([.year, .month], from: workout.day)
            guard let year = parts.year, let month = parts.month else { continue }
            let id = "\(year)-\(month)"
            counts[id] = (year, month, (counts[id]?.count ?? 0) + 1)
        }
        return counts.values
            .compactMap { item in tier(forWorkouts: item.count).map { MonthAward(year: item.year, month: item.month, workouts: item.count, tier: $0) } }
            .sorted { ($0.year, $0.month) > ($1.year, $1.month) }
    }

    /// Этот месяц: сколько уже есть и до какой награды осталось.
    static func currentMonth(log: [CompletionRecord], now: Date = Date(), calendar: Calendar = WeeklyStatsBuilder.calendar) -> (workouts: Int, next: MonthTier?) {
        let parts = calendar.dateComponents([.year, .month], from: now)
        let count = orderedWorkouts(log, calendar: calendar).filter {
            let day = calendar.dateComponents([.year, .month], from: $0.day)
            return day.year == parts.year && day.month == parts.month
        }.count
        let next = [MonthTier.bronze, .silver, .gold].first { count < $0.workouts }
        return (count, next)
    }
}
