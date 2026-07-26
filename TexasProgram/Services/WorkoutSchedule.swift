import Foundation

/// Русские названия без `DateFormatter`: он дорогой, а набор строк фиксированный.
enum RuDate {
    static let weekdayFull = ["", "воскресенье", "понедельник", "вторник", "среда", "четверг", "пятница", "суббота"]
    static let weekdayShort = ["", "вс", "пн", "вт", "ср", "чт", "пт", "сб"]
    static let monthGenitive = ["", "января", "февраля", "марта", "апреля", "мая", "июня",
                                "июля", "августа", "сентября", "октября", "ноября", "декабря"]

    /// Номера дней недели в порядке «понедельник → воскресенье» (нумерация `Calendar`: 1 — воскресенье).
    static let weekOrder = [2, 3, 4, 5, 6, 7, 1]

    static func full(weekday: Int) -> String {
        weekdayFull.indices.contains(weekday) ? weekdayFull[weekday] : ""
    }

    static func short(weekday: Int) -> String {
        weekdayShort.indices.contains(weekday) ? weekdayShort[weekday] : ""
    }

    static func dayMonth(_ date: Date, calendar: Calendar = .current) -> String {
        let parts = calendar.dateComponents([.day, .month], from: date)
        let month = monthGenitive.indices.contains(parts.month ?? 0) ? monthGenitive[parts.month ?? 0] : ""
        return "\(parts.day ?? 1) \(month)"
    }
}

/// Тренировка плана, привязанная к календарной дате.
struct ScheduledWorkout: Identifiable, Equatable {
    let week: Int
    let day: WorkoutDayPlan
    let date: Date
    let weekday: Int
    let isCompleted: Bool

    var id: String { "\(week)-\(day.number)" }
    var weekdayName: String { RuDate.full(weekday: weekday) }
    var shortWeekday: String { RuDate.short(weekday: weekday) }
}

struct WorkoutSchedule {
    /// Невыполненная тренировка, назначенная на сегодня.
    let today: ScheduledWorkout?
    /// Ближайшая невыполненная тренировка после сегодняшнего дня.
    let upcoming: ScheduledWorkout?
    /// Невыполненные тренировки текущей недели, чей день уже прошёл.
    let overdue: [ScheduledWorkout]
    /// Сегодня по расписанию тренировка есть, и она уже отмечена.
    let todayAlreadyDone: Bool
    let referenceDate: Date

    /// Что показывать на экране «Сегодня».
    var focus: ScheduledWorkout? { today ?? upcoming }
    var isRestDay: Bool { today == nil && !todayAlreadyDone }

    /// «Сегодня» / «Завтра» / «Через 3 дня» / «Просрочено».
    func relativeTitle(for workout: ScheduledWorkout, calendar: Calendar = .current) -> String {
        let days = calendar.dateComponents([.day], from: calendar.startOfDay(for: referenceDate), to: workout.date).day ?? 0
        switch days {
        case ..<0: return "Пропущено"
        case 0: return "Сегодня"
        case 1: return "Завтра"
        case 2: return "Послезавтра"
        default: return "Через \(days) дн."
        }
    }
}

enum WorkoutScheduler {
    /// Календарь с неделей, начинающейся с понедельника.
    static func trainingCalendar(_ base: Calendar = .current) -> Calendar {
        var calendar = base
        calendar.firstWeekday = 2
        return calendar
    }

    /// Раскладывает план по календарю.
    ///
    /// Текущая календарная неделя — это первая неделя плана, в которой остались
    /// невыполненные дни. Если пользователь отстал, расписание едет вместе с ним,
    /// а пропущенные дни этой недели показываются отдельно.
    static func build(profile: ProgramProfile, plan: WorkoutPlan, now: Date = Date(), calendar base: Calendar = .current) -> WorkoutSchedule {
        let calendar = trainingCalendar(base)
        let startOfToday = calendar.startOfDay(for: now)
        let weekStart = calendar.dateInterval(of: .weekOfYear, for: startOfToday)?.start ?? startOfToday

        let anchorWeek = plan.weeks.first { week in
            week.days.contains { !profile.isCompleted(week: week.number, day: $0.number) }
        }?.number ?? plan.weeks.first?.number ?? 1

        var entries: [ScheduledWorkout] = []
        entries.reserveCapacity(plan.weeks.count * 4)

        for week in plan.weeks {
            for day in week.days {
                let weekday = profile.weekday(forDay: day.number)
                guard let date = date(week: week.number, weekday: weekday, anchorWeek: anchorWeek, weekStart: weekStart, calendar: calendar) else { continue }
                entries.append(ScheduledWorkout(
                    week: week.number,
                    day: day,
                    date: date,
                    weekday: weekday,
                    isCompleted: profile.isCompleted(week: week.number, day: day.number)
                ))
            }
        }

        let todayEntry = entries.first { calendar.isDate($0.date, inSameDayAs: startOfToday) }
        let pending = entries.filter { !$0.isCompleted }

        return WorkoutSchedule(
            today: todayEntry.flatMap { $0.isCompleted ? nil : $0 },
            upcoming: pending.filter { $0.date > startOfToday }.min { $0.date < $1.date },
            overdue: pending.filter { $0.date < startOfToday }.sorted { $0.date < $1.date },
            todayAlreadyDone: todayEntry?.isCompleted ?? false,
            referenceDate: now
        )
    }

    private static func date(week: Int, weekday: Int, anchorWeek: Int, weekStart: Date, calendar: Calendar) -> Date? {
        guard let base = calendar.date(byAdding: .weekOfYear, value: week - anchorWeek, to: weekStart) else { return nil }
        // firstWeekday = 2, поэтому понедельник — нулевое смещение от начала недели.
        let offset = (weekday - 2 + 7) % 7
        return calendar.date(byAdding: .day, value: offset, to: base)
    }
}
