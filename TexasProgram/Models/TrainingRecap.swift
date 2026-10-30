import Foundation

/// Итоги месяца или года, как «Spotify Wrapped»: сколько тренировок и времени,
/// сколько поднято, рекорды, любимое упражнение, самый тяжёлый подход, серия
/// недель и сравнение с прошлым таким же периодом.
///
/// Считается на телефоне из истории отметок и записанных подходов — все
/// тренировки, а не только выложенные в ленту.
struct TrainingRecap: Equatable {
    enum Period: Hashable, Identifiable {
        case month(year: Int, month: Int)
        case year(Int)

        var id: String {
            switch self {
            case .month(let year, let month): return String(format: "%04d-%02d", year, month)
            case .year(let year): return "\(year)"
            }
        }

        var isYear: Bool { if case .year = self { return true } else { return false } }

        /// «Октябрь 2026», «2026 год».
        var title: String {
            switch self {
            case .month(let year, let month): return "\(TrainingRecap.monthNames[month].capitalized) \(year)"
            case .year(let year): return "\(year) год"
            }
        }

        /// «октябрь», «2026» — в подписях вроде «Твой октябрь».
        var name: String {
            switch self {
            case .month(_, let month): return TrainingRecap.monthNames[month]
            case .year(let year): return "\(year)"
            }
        }

        /// «в октябре», «за 2026 год».
        var inPhrase: String {
            switch self {
            case .month(_, let month): return "в " + TrainingRecap.monthPrepositional[month]
            case .year(let year): return "за \(year) год"
            }
        }

        var previous: Period {
            switch self {
            case .month(let year, let month): return month == 1 ? .month(year: year - 1, month: 12) : .month(year: year, month: month - 1)
            case .year(let year): return .year(year - 1)
            }
        }

        func interval(_ calendar: Calendar) -> DateInterval {
            var parts = DateComponents()
            switch self {
            case .month(let year, let month):
                parts.year = year
                parts.month = month
                parts.day = 1
                let start = calendar.date(from: parts) ?? Date()
                let end = calendar.date(byAdding: .month, value: 1, to: start) ?? start
                return DateInterval(start: start, end: end)
            case .year(let year):
                parts.year = year
                parts.month = 1
                parts.day = 1
                let start = calendar.date(from: parts) ?? Date()
                let end = calendar.date(byAdding: .year, value: 1, to: start) ?? start
                return DateInterval(start: start, end: end)
            }
        }
    }

    /// Упражнение в итогах: сколько раз делали, сколько подняли и лучший подход.
    struct ExerciseStat: Equatable {
        let name: String
        var sessions: Int
        var volumeKg: Double
        var bestWeight: Double
        var bestReps: Int
    }

    let period: Period
    let workouts: Int
    let seconds: Int
    let volumeKg: Double
    let sets: Int
    let reps: Int
    /// Сколько раз лучший вес движения стал выше всех прошлых.
    let records: Int
    /// Недель с тренировкой и самая длинная серия таких недель подряд.
    let activeWeeks: Int
    let longestWeekStreak: Int
    /// Чаще всего делали.
    let favorite: ExerciseStat?
    /// Самый тяжёлый подход периода.
    let heaviest: ExerciseStat?
    /// Больше всего подняли — до трёх упражнений.
    let topByVolume: [ExerciseStat]
    /// День недели, в который тренировались чаще всего (`Calendar`: 1 — воскресенье).
    let busiestWeekday: Int?
    /// Тренировки по неделям месяца или по месяцам года — для столбиков.
    let buckets: [Int]
    let previousWorkouts: Int
    let previousVolumeKg: Double

    var isEmpty: Bool { workouts == 0 }
    var hours: Int { seconds / 3600 }
    var minutes: Int { (seconds % 3600) / 60 }

    /// Насколько больше (или меньше) поднято, чем в прошлый такой же период, в процентах.
    var volumeChange: Int? {
        guard previousVolumeKg > 0 else { return nil }
        return Int(((volumeKg - previousVolumeKg) / previousVolumeKg * 100).rounded())
    }

    /// Поднятое — в понятных предметах: «как 3 легковые машины».
    var volumeComparison: String? {
        guard volumeKg >= 50 else { return nil }
        if volumeKg >= 6000 {
            let count = Int((volumeKg / 6000).rounded())
            return "как \(count) \(RuPlural.word(count, "слон", "слона", "слонов"))"
        }
        if volumeKg >= 1500 {
            let count = Int((volumeKg / 1500).rounded())
            return "как \(count) \(RuPlural.word(count, "легковая машина", "легковые машины", "легковых машин"))"
        }
        let count = Int((volumeKg / 50).rounded())
        return "как \(count) \(RuPlural.word(count, "мешок", "мешка", "мешков")) цемента"
    }

    static let monthNames = ["", "январь", "февраль", "март", "апрель", "май", "июнь",
                             "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь"]
    static let monthPrepositional = ["", "январе", "феврале", "марте", "апреле", "мае", "июне",
                                     "июле", "августе", "сентябре", "октябре", "ноябре", "декабре"]

    // MARK: - Расчёт

    /// Итоги профиля. Подходы берутся прямо из записей: в ключе — день и
    /// упражнение, поэтому прошлые циклы программы тоже попадают в итоги.
    static func build(_ profile: ProgramProfile, period: Period, calendar: Calendar = WorkoutScheduler.trainingCalendar()) -> TrainingRecap {
        var names: [String: String] = [:]
        for week in profile.workoutPlan.weeks {
            for day in week.days {
                for exercise in day.exercises { names[exercise.id] = exercise.name }
            }
        }
        return build(log: profile.completionLog, setLog: profile.setLog, names: names, period: period, calendar: calendar)
    }

    static func build(
        log: [CompletionRecord],
        setLog: [String: SetEntry],
        names: [String: String] = [:],
        period: Period,
        calendar: Calendar = WorkoutScheduler.trainingCalendar()
    ) -> TrainingRecap {
        let days = setsByDay(setLog)
        let interval = period.interval(calendar)
        let sorted = log.sorted { $0.date < $1.date }

        var workouts = 0, seconds = 0, sets = 0, reps = 0, records = 0
        var volume = 0.0
        var exercises: [String: ExerciseStat] = [:]
        var bestSoFar: [String: Double] = [:]
        var weekdays: [Int: Int] = [:]
        var weeks = Set<Date>()
        var buckets = Array(repeating: 0, count: bucketCount(period, interval: interval, calendar: calendar))

        for record in sorted {
            let inPeriod = interval.contains(record.date)
            let day = days[record.key] ?? [:]
            for (id, entries) in day {
                let name = names[id] ?? id
                let top = entries.map(\.weight).max() ?? 0
                if inPeriod {
                    var stat = exercises[name] ?? ExerciseStat(name: name, sessions: 0, volumeKg: 0, bestWeight: 0, bestReps: 0)
                    stat.sessions += 1
                    stat.volumeKg += entries.reduce(0) { $0 + $1.tonnage }
                    for entry in entries where entry.weight > stat.bestWeight || (entry.weight == stat.bestWeight && entry.reps > stat.bestReps) {
                        stat.bestWeight = entry.weight
                        stat.bestReps = entry.reps
                    }
                    exercises[name] = stat
                    sets += entries.count
                    reps += entries.reduce(0) { $0 + $1.reps }
                    volume += entries.reduce(0) { $0 + $1.tonnage }
                }
                // Рекорд — лучший вес выше всех прошлых; первый раз рекордом не считаем.
                guard top > 0 else { continue }
                if let previous = bestSoFar[name], top > previous + 1e-9, inPeriod { records += 1 }
                bestSoFar[name] = max(bestSoFar[name] ?? 0, top)
            }
            guard inPeriod else { continue }
            workouts += 1
            seconds += record.durationSeconds ?? 0
            weekdays[calendar.component(.weekday, from: record.date), default: 0] += 1
            if let week = calendar.dateInterval(of: .weekOfYear, for: record.date)?.start { weeks.insert(week) }
            let index = bucketIndex(record.date, period: period, interval: interval, calendar: calendar)
            if buckets.indices.contains(index) { buckets[index] += 1 }
        }

        let previous = period.previous.interval(calendar)
        let earlier = sorted.filter { previous.contains($0.date) }
        let previousVolume = earlier.reduce(0.0) { sum, record in
            sum + (days[record.key] ?? [:]).values.reduce(0.0) { $0 + $1.reduce(0) { $0 + $1.tonnage } }
        }

        let stats = Array(exercises.values)
        let busiest = weekdays.max { $0.value == $1.value ? $0.key > $1.key : $0.value < $1.value }
        return TrainingRecap(
            period: period,
            workouts: workouts,
            seconds: seconds,
            volumeKg: (volume * 10).rounded() / 10,
            sets: sets,
            reps: reps,
            records: records,
            activeWeeks: weeks.count,
            longestWeekStreak: longestStreak(weeks, calendar: calendar),
            favorite: stats.max { ($0.sessions, $0.volumeKg) < ($1.sessions, $1.volumeKg) },
            heaviest: stats.filter { $0.bestWeight > 0 }.max { ($0.bestWeight, $0.bestReps) < ($1.bestWeight, $1.bestReps) },
            topByVolume: Array(stats.filter { $0.volumeKg > 0 }.sorted { $0.volumeKg > $1.volumeKg }.prefix(3)),
            busiestWeekday: busiest?.key,
            buckets: buckets,
            previousWorkouts: earlier.count,
            previousVolumeKg: (previousVolume * 10).rounded() / 10
        )
    }

    /// Периоды, за которые есть что показать: этот и прошлый месяц, этот и прошлый год.
    static func periods(_ log: [CompletionRecord], now: Date = Date(), calendar: Calendar = WorkoutScheduler.trainingCalendar()) -> [Period] {
        let parts = calendar.dateComponents([.year, .month], from: now)
        let year = parts.year ?? 2026
        let month = parts.month ?? 1
        let current = Period.month(year: year, month: month)
        let candidates: [Period] = [current, current.previous, .year(year), .year(year - 1)]
        return candidates.filter { period in
            let interval = period.interval(calendar)
            return log.contains { interval.contains($0.date) }
        }
    }

    /// День → упражнение → подходы. Ключ подхода: «день|упражнение|номер».
    static func setsByDay(_ setLog: [String: SetEntry]) -> [String: [String: [SetEntry]]] {
        var result: [String: [String: [SetEntry]]] = [:]
        for (key, entry) in setLog {
            let parts = key.split(separator: "|", omittingEmptySubsequences: false)
            guard parts.count >= 3 else { continue }
            result[String(parts[0]), default: [:]][String(parts[1]), default: []].append(entry)
        }
        return result
    }

    private static func bucketCount(_ period: Period, interval: DateInterval, calendar: Calendar) -> Int {
        if period.isYear { return 12 }
        let last = interval.end.addingTimeInterval(-1)
        return bucketIndex(last, period: period, interval: interval, calendar: calendar) + 1
    }

    /// Номер недели внутри месяца (с понедельника) или номер месяца в году, с нуля.
    private static func bucketIndex(_ date: Date, period: Period, interval: DateInterval, calendar: Calendar) -> Int {
        if period.isYear { return (calendar.component(.month, from: date) - 1) }
        guard let first = calendar.dateInterval(of: .weekOfYear, for: interval.start)?.start,
              let week = calendar.dateInterval(of: .weekOfYear, for: date)?.start else { return 0 }
        return max(0, calendar.dateComponents([.weekOfYear], from: first, to: week).weekOfYear ?? 0)
    }

    private static func longestStreak(_ weeks: Set<Date>, calendar: Calendar) -> Int {
        var longest = 0
        for start in weeks {
            // Серию считаем только от её первой недели.
            if let before = calendar.date(byAdding: .weekOfYear, value: -1, to: start), weeks.contains(before) { continue }
            var length = 0
            var cursor = start
            while weeks.contains(cursor) {
                length += 1
                guard let next = calendar.date(byAdding: .weekOfYear, value: 1, to: cursor) else { break }
                cursor = next
            }
            longest = max(longest, length)
        }
        return longest
    }
}
