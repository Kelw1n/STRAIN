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
    /// Номер тренировки волны жима, выданный этому дню. Волна идёт своим порядком,
    /// не привязанным к дню недели, поэтому номер приходит из расписания.
    var benchSession: Int?

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
        switch profile.scheduleMode {
        case .queue: return buildQueue(profile: profile, plan: plan, now: now, calendar: base)
        case .weekday: return buildByWeekday(profile: profile, plan: plan, now: now, calendar: base)
        }
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

        assignBenchSessions(&entries, profile: profile)
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

    /// У каждого дня недели свой счётчик.
    ///
    /// Понедельник всегда отдаёт понедельничную тренировку — самую раннюю невыполненную.
    /// Сделал понедельники первой и второй недели — в следующий понедельник будет третья.
    /// Пропущенный четверг не теряется и не переезжает на понедельник: он достанется
    /// ближайшему четвергу.
    private static func buildByWeekday(profile: ProgramProfile, plan: WorkoutPlan, now: Date, calendar base: Calendar) -> WorkoutSchedule {
        let calendar = trainingCalendar(base)
        let startOfToday = calendar.startOfDay(for: now)
        let todayWeekday = calendar.component(.weekday, from: startOfToday)
        let trainedToday = profile.lastCompletionDate.map { calendar.isDate($0, inSameDayAs: startOfToday) } ?? false

        // Номера тренировочных дней программы: 1...3 для «Техаса», 1...4 для «Верх / Низ».
        let dayNumbers = plan.weeks.first?.days.map(\.number) ?? []

        var entries: [ScheduledWorkout] = []
        entries.reserveCapacity(plan.weeks.count * dayNumbers.count)

        for dayNumber in dayNumbers {
            let weekday = profile.weekday(forDay: dayNumber)
            var cursor = nextOccurrence(of: weekday, from: startOfToday, calendar: calendar)
            // Сегодня уже отметились — этот слот занят, следующий такой день через неделю.
            if trainedToday, weekday == todayWeekday {
                cursor = calendar.date(byAdding: .weekOfYear, value: 1, to: cursor) ?? cursor
            }

            for week in plan.weeks {
                guard let day = week.days.first(where: { $0.number == dayNumber }) else { continue }
                guard !profile.isCompleted(week: week.number, day: dayNumber) else { continue }
                entries.append(ScheduledWorkout(
                    week: week.number,
                    day: day,
                    date: cursor,
                    weekday: weekday,
                    isCompleted: false
                ))
                cursor = calendar.date(byAdding: .weekOfYear, value: 1, to: cursor) ?? cursor
            }
        }

        entries.sort { $0.date < $1.date }
        assignBenchSessions(&entries, profile: profile)

        return WorkoutSchedule(
            today: entries.first { calendar.isDate($0.date, inSameDayAs: startOfToday) },
            upcoming: entries.first { $0.date > startOfToday },
            overdue: [],
            todayAlreadyDone: trainedToday && dayNumbers.contains { profile.weekday(forDay: $0) == todayWeekday },
            allPending: entries,
            referenceDate: now
        )
    }

    /// Раздаёт тренировки волны жима по порядку тем дням, которые её несут.
    ///
    /// Волна не смотрит на день недели: следующая невыполненная достаётся ближайшему
    /// верхнему дню, каким бы он ни был. Так в понедельник получаются понедельничные
    /// вспомогательные упражнения и при этом правильный по счёту жим.
    private static func assignBenchSessions(_ entries: inout [ScheduledWorkout], profile: ProgramProfile) {
        guard profile.programKind == .upperLower, var next = profile.nextBenchSession else { return }
        let total = UpperLowerCalculator.benchSessionCount
        for index in entries.indices where profile.carriesBenchWave(day: entries[index].day.number) {
            guard next <= total else { return }
            entries[index].benchSession = next
            next += 1
        }
    }

    /// Ближайшая дата с нужным днём недели, начиная с указанной.
    static func nextOccurrence(of weekday: Int, from start: Date, calendar: Calendar) -> Date {
        let current = calendar.component(.weekday, from: start)
        let delta = (weekday - current + 7) % 7
        return calendar.date(byAdding: .day, value: delta, to: start) ?? start
    }
}
