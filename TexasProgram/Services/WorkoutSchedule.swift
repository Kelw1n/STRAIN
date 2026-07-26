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

    /// Заголовок по реальной дате: в режиме очереди день недели берётся отсюда,
    /// а не из номера дня в программе.
    var fullTitle: String { weekdayName.uppercased() + " · " + day.title }
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
    /// Все невыполненные тренировки с назначенными датами — для экрана «План».
    let allPending: [ScheduledWorkout]
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

    static func build(profile: ProgramProfile, plan: WorkoutPlan, now: Date = Date(), calendar base: Calendar = .current) -> WorkoutSchedule {
        profile.useQueueSchedule
            ? buildQueue(profile: profile, plan: plan, now: now, calendar: base)
            : buildByWeekday(profile: profile, plan: plan, now: now, calendar: base)
    }

    // MARK: - Очередь

    /// Невыполненные тренировки раздаются подряд по ближайшим тренировочным дням.
    ///
    /// День плана здесь не привязан к своему дню недели: пропустил четверг — тренировка
    /// не ждёт следующего четверга, а просто становится первой в очереди. Ничего не
    /// теряется и не копится в «пропущено», сдвигается только календарь.
    private static func buildQueue(profile: ProgramProfile, plan: WorkoutPlan, now: Date, calendar base: Calendar) -> WorkoutSchedule {
        let calendar = trainingCalendar(base)
        let startOfToday = calendar.startOfDay(for: now)
        let weekdays = Set(profile.weekdays)

        var pending: [(week: Int, day: WorkoutDayPlan)] = []
        for week in plan.weeks {
            for day in week.days where !profile.isCompleted(week: week.number, day: day.number) {
                pending.append((week.number, day))
            }
        }

        let trainedToday = profile.lastCompletionDate.map { calendar.isDate($0, inSameDayAs: startOfToday) } ?? false
        let todayIsTrainingDay = weekdays.contains(calendar.component(.weekday, from: startOfToday))

        var entries: [ScheduledWorkout] = []
        entries.reserveCapacity(pending.count)

        if !weekdays.isEmpty {
            // Если сегодня уже отметились, очередь начинается с завтра.
            var cursor = trainedToday ? (calendar.date(byAdding: .day, value: 1, to: startOfToday) ?? startOfToday) : startOfToday
            var index = 0
            var guardCounter = 0
            let limit = pending.count * 7 + 14

            while index < pending.count, guardCounter < limit {
                guardCounter += 1
                let weekday = calendar.component(.weekday, from: cursor)
                if weekdays.contains(weekday) {
                    let item = pending[index]
                    entries.append(ScheduledWorkout(
                        week: item.week,
                        day: item.day,
                        date: cursor,
                        weekday: weekday,
                        isCompleted: false
                    ))
                    index += 1
                }
                cursor = calendar.date(byAdding: .day, value: 1, to: cursor) ?? cursor
            }
        }

        let todayEntry = entries.first { calendar.isDate($0.date, inSameDayAs: startOfToday) }

        return WorkoutSchedule(
            today: todayEntry,
            upcoming: entries.first { $0.date > startOfToday },
            overdue: [],
            todayAlreadyDone: trainedToday && todayIsTrainingDay,
            allPending: entries,
            referenceDate: now
        )
    }

    /// Ближайший тренировочный день, на который встанет первая тренировка очереди.
    static func nextTrainingSlot(profile: ProgramProfile, now: Date = Date(), calendar base: Calendar = .current) -> Date {
        let calendar = trainingCalendar(base)
        let startOfToday = calendar.startOfDay(for: now)
        let weekdays = Set(profile.weekdays)
        guard !weekdays.isEmpty else { return startOfToday }

        let trainedToday = profile.lastCompletionDate.map { calendar.isDate($0, inSameDayAs: startOfToday) } ?? false
        var cursor = trainedToday ? (calendar.date(byAdding: .day, value: 1, to: startOfToday) ?? startOfToday) : startOfToday
        for _ in 0..<14 {
            if weekdays.contains(calendar.component(.weekday, from: cursor)) { return cursor }
            cursor = calendar.date(byAdding: .day, value: 1, to: cursor) ?? cursor
        }
        return cursor
    }

    // MARK: - По дням недели

    /// Раскладывает план по календарю.
    ///
    /// Текущая неделя плана — первая, в которой остались невыполненные дни. Она ложится
    /// на календарную неделю из явной привязки профиля, а если её нет — на текущую.
    /// Неделя, которая целиком в прошлом, переносится вперёд: иначе новый пользователь
    /// сразу видел бы «пропущено» за дни, когда приложения у него ещё не было.
    private static func buildByWeekday(profile: ProgramProfile, plan: WorkoutPlan, now: Date, calendar base: Calendar) -> WorkoutSchedule {
        let calendar = trainingCalendar(base)
        let startOfToday = calendar.startOfDay(for: now)
        let thisWeekStart = calendar.dateInterval(of: .weekOfYear, for: startOfToday)?.start ?? startOfToday

        let anchorWeek = plan.weeks.first { week in
            week.days.contains { !profile.isCompleted(week: week.number, day: $0.number) }
        }?.number ?? plan.weeks.first?.number ?? 1

        let weekdays = plan.weeks.first?.days.map { profile.weekday(forDay: $0.number) } ?? []
        var weekStart = anchoredWeekStart(profile: profile, anchorWeek: anchorWeek, fallback: thisWeekStart, calendar: calendar)

        // Неделя ещё не начата, но её дни уже позади (свежая установка в конце недели) —
        // переносим её вперёд, чтобы не показывать пропуски за дни без приложения.
        // Начатую неделю не двигаем: её невыполненные дни — настоящие пропуски.
        let weekStarted = plan.weeks.first { $0.number == anchorWeek }?.days
            .contains { profile.isCompleted(week: anchorWeek, day: $0.number) } ?? false
        if !weekStarted, lastTrainingDate(weekStart: weekStart, weekdays: weekdays, calendar: calendar) < startOfToday {
            weekStart = thisWeekStart
            if lastTrainingDate(weekStart: weekStart, weekdays: weekdays, calendar: calendar) < startOfToday {
                weekStart = calendar.date(byAdding: .weekOfYear, value: 1, to: weekStart) ?? weekStart
            }
        }

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
        // Дни до начала цикла пропущенными не считаются.
        let cycleStart = calendar.startOfDay(for: profile.cycleStartedAt)

        return WorkoutSchedule(
            today: todayEntry.flatMap { $0.isCompleted ? nil : $0 },
            upcoming: pending.filter { $0.date > startOfToday }.min { $0.date < $1.date },
            overdue: pending.filter { $0.date < startOfToday && $0.date >= cycleStart }.sorted { $0.date < $1.date },
            todayAlreadyDone: todayEntry?.isCompleted ?? false,
            allPending: pending.sorted { $0.date < $1.date },
            referenceDate: now
        )
    }

    private static func anchoredWeekStart(profile: ProgramProfile, anchorWeek: Int, fallback: Date, calendar: Calendar) -> Date {
        guard profile.scheduleAnchorWeek > 0, let stored = profile.scheduleAnchorDate else { return fallback }
        return calendar.date(byAdding: .weekOfYear, value: anchorWeek - profile.scheduleAnchorWeek, to: stored) ?? fallback
    }

    private static func lastTrainingDate(weekStart: Date, weekdays: [Int], calendar: Calendar) -> Date {
        let offsets = weekdays.map { ($0 - 2 + 7) % 7 }
        let last = offsets.max() ?? 0
        return calendar.date(byAdding: .day, value: last, to: weekStart) ?? weekStart
    }

    private static func date(week: Int, weekday: Int, anchorWeek: Int, weekStart: Date, calendar: Calendar) -> Date? {
        guard let base = calendar.date(byAdding: .weekOfYear, value: week - anchorWeek, to: weekStart) else { return nil }
        // firstWeekday = 2, поэтому понедельник — нулевое смещение от начала недели.
        let offset = (weekday - 2 + 7) % 7
        return calendar.date(byAdding: .day, value: offset, to: base)
    }
}
