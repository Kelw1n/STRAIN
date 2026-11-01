import Foundation

/// «Цель» как в REBRN: дни, срок, время каждой тренировки и несколько
/// напоминаний до неё. Расчёт тот же, что на Android, — `TrainingGoal.kt`.
enum TrainingGoal {
    /// За сколько минут до тренировки можно напомнить.
    static let offsetOptions = [0, 15, 30, 60, 120]
    /// Не больше трёх напоминаний на тренировку: у iPhone лимит на число
    /// запланированных уведомлений, а ещё есть добавки.
    static let maxOffsets = 3
    static let monthOptions = [0, 1, 2, 3, 6, 12]

    static func offsetTitle(_ minutes: Int) -> String {
        switch minutes {
        case 0: return "В начале тренировки"
        case 60: return "За 1 час"
        case 120: return "За 2 часа"
        default: return "За \(minutes) минут"
        }
    }

    /// «через 30 минут», «через 1 час» — в заголовке уведомления.
    static func inText(_ minutes: Int) -> String {
        switch minutes {
        case 60: return "через час"
        case 120: return "через 2 часа"
        default: return "через \(minutes) минут"
        }
    }

    static func monthsTitle(_ months: Int) -> String {
        months == 0 ? "Без срока" : "\(months) " + RuPlural.word(months, "месяц", "месяца", "месяцев")
    }

    static func timeText(_ minutes: Int) -> String {
        String(format: "%d:%02d", minutes / 60, minutes % 60)
    }
}

extension ProgramProfile {
    /// Минуты от полуночи, когда тренировка в этот день недели.
    func trainingMinutes(weekday: Int) -> Int {
        trainingTimes[String(weekday)] ?? (reminderHour * 60 + reminderMinute)
    }

    /// Напоминания по порядку, без повторов и не больше трёх. Пустой список
    /// при включённых напоминаниях — значит «в начале тренировки».
    var activeReminderOffsets: [Int] {
        let cleaned = Array(Set(reminderOffsets.filter { TrainingGoal.offsetOptions.contains($0) })).sorted()
        return cleaned.isEmpty ? [0] : Array(cleaned.prefix(TrainingGoal.maxOffsets))
    }

    /// Последний день срока, если он задан.
    func goalEnd(calendar: Calendar = .current) -> Date? {
        guard goalMonths > 0, let goalStart else { return nil }
        return calendar.date(byAdding: .month, value: goalMonths, to: calendar.startOfDay(for: goalStart))
    }

    /// Какая идёт неделя срока: «неделя 3 из 13». Срока нет или он ещё не начался — nil.
    func goalWeek(now: Date = Date(), calendar: Calendar = .current) -> (week: Int, total: Int)? {
        guard let goalStart, let end = goalEnd(calendar: calendar) else { return nil }
        let start = calendar.startOfDay(for: goalStart)
        let today = calendar.startOfDay(for: now)
        guard today >= start else { return nil }
        let total = max(1, Int(ceil(Double(calendar.dateComponents([.day], from: start, to: end).day ?? 0) / 7)))
        let week = (calendar.dateComponents([.day], from: start, to: today).day ?? 0) / 7 + 1
        return (min(week, total), total)
    }
}
