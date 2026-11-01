import Foundation

// MARK: - Добавки

/// Когда пьётся добавка. «С тренировкой» — протеин, предтрен: в дни без
/// тренировки такую не ждём, и серия из-за неё не рвётся.
enum SupplementSlot: String, Codable, CaseIterable, Identifiable, Sendable {
    case morning, day, evening, workout

    var id: String { rawValue }

    var title: String {
        switch self {
        case .morning: return "Утром"
        case .day: return "Днём"
        case .evening: return "Вечером"
        case .workout: return "С тренировкой"
        }
    }

    var systemImage: String {
        switch self {
        case .morning: return "sunrise.fill"
        case .day: return "sun.max.fill"
        case .evening: return "moon.fill"
        case .workout: return "dumbbell.fill"
        }
    }
}

/// Время напоминания: часы и минуты по местному времени.
struct SupplementReminder: Codable, Equatable, Hashable, Sendable {
    var hour: Int
    var minute: Int

    var text: String { String(format: "%d:%02d", hour, minute) }
}

/// Одна добавка из списка: креатин, омега-3, витамин D.
struct Supplement: Codable, Equatable, Hashable, Identifiable, Sendable {
    var id = UUID()
    var name: String
    /// Доза как её говорят: «5 г», «2 капсулы».
    var dose: String
    var slot: SupplementSlot
    var reminder: SupplementReminder?
    /// С какого дня в списке: дни раньше не считаются пропущенными.
    var created: Date

    init(id: UUID = UUID(), name: String, dose: String = "", slot: SupplementSlot = .morning, reminder: SupplementReminder? = nil, created: Date = Date()) {
        self.id = id
        self.name = name
        self.dose = dose
        self.slot = slot
        self.reminder = reminder
        self.created = created
    }
}

/// Отметка «выпил»: какая добавка и когда.
struct SupplementIntake: Codable, Equatable, Hashable, Sendable {
    var supplementID: UUID
    var date: Date
}

/// Добавки профиля и отметки. Лежат в файле питания — и в резервной копии вместе с ним.
struct SupplementData: Codable, Equatable, Sendable {
    var items: [Supplement] = []
    var taken: [SupplementIntake] = []
}

/// Частые добавки — добавить в одно касание.
struct SupplementPreset: Identifiable, Hashable, Sendable {
    let name: String
    let dose: String
    let slot: SupplementSlot

    var id: String { name }

    static let all: [SupplementPreset] = [
        SupplementPreset(name: "Креатин", dose: "5 г", slot: .morning),
        SupplementPreset(name: "Омега-3", dose: "2 капсулы", slot: .morning),
        SupplementPreset(name: "Витамин D3", dose: "2000 МЕ", slot: .morning),
        SupplementPreset(name: "Магний", dose: "400 мг", slot: .evening),
        SupplementPreset(name: "Протеин", dose: "30 г", slot: .workout),
        SupplementPreset(name: "Мультивитамины", dose: "1 таблетка", slot: .morning),
        SupplementPreset(name: "Цинк", dose: "25 мг", slot: .evening),
        SupplementPreset(name: "Кофеин", dose: "200 мг", slot: .workout)
    ]
}

// MARK: - Расчёты

/// Что выпито за день, серия дней и последние дни. Один расчёт на iOS и
/// Android — `Supplements.kt`.
enum SupplementTracker {
    /// Как выглядит день: сколько нужно было выпить и сколько выпито.
    struct DayState: Equatable, Sendable {
        let day: Date
        let required: Int
        let taken: Int

        var isComplete: Bool { required > 0 && taken >= required }
        var share: Double { required > 0 ? min(Double(taken) / Double(required), 1) : 0 }
    }

    /// Выпита ли добавка в этот день.
    static func isTaken(_ item: Supplement, on day: Date, in data: SupplementData, calendar: Calendar = .current) -> Bool {
        data.taken.contains { $0.supplementID == item.id && calendar.isDate($0.date, inSameDayAs: day) }
    }

    /// Когда отмечена — для подписи «в 9:12».
    static func intake(_ item: Supplement, on day: Date, in data: SupplementData, calendar: Calendar = .current) -> SupplementIntake? {
        data.taken.first { $0.supplementID == item.id && calendar.isDate($0.date, inSameDayAs: day) }
    }

    /// Добавки, которые ждём в этот день: уже были в списке, а «с тренировкой» —
    /// только в день тренировки.
    static func required(on day: Date, items: [Supplement], trainedDays: Set<Date>, calendar: Calendar = .current) -> [Supplement] {
        let start = calendar.startOfDay(for: day)
        let trained = trainedDays.contains(start)
        return items.filter { item in
            calendar.startOfDay(for: item.created) <= start && (item.slot != .workout || trained)
        }
    }

    static func state(on day: Date, data: SupplementData, trainedDays: Set<Date>, calendar: Calendar = .current) -> DayState {
        let start = calendar.startOfDay(for: day)
        let takenIDs = Set(data.taken.filter { calendar.isDate($0.date, inSameDayAs: start) }.map(\.supplementID))
        let required = required(on: start, items: data.items, trainedDays: trainedDays, calendar: calendar)
        return DayState(day: start, required: required.count, taken: required.filter { takenIDs.contains($0.id) }.count)
    }

    /// Дни подряд, когда выпито всё. Сегодня ещё идёт: не допил — серия
    /// считается со вчера и не обнуляется до завтра. День, в который ничего
    /// не ждали (только «с тренировкой», а тренировки не было), серию не рвёт.
    static func streak(data: SupplementData, trainedDays: Set<Date>, today: Date = Date(), calendar: Calendar = .current) -> Int {
        guard let first = data.items.map({ calendar.startOfDay(for: $0.created) }).min() else { return 0 }
        var day = calendar.startOfDay(for: today)
        var count = 0
        if !state(on: day, data: data, trainedDays: trainedDays, calendar: calendar).isComplete {
            guard let yesterday = calendar.date(byAdding: .day, value: -1, to: day) else { return 0 }
            day = yesterday
        }
        // Год назад и раньше не смотрим: серию длиннее вряд ли кто-то соберёт,
        // а проход по дням не должен расти без конца.
        for _ in 0..<400 {
            guard day >= first else { break }
            let state = state(on: day, data: data, trainedDays: trainedDays, calendar: calendar)
            if state.required > 0 {
                guard state.isComplete else { break }
                count += 1
            }
            guard let previous = calendar.date(byAdding: .day, value: -1, to: day) else { break }
            day = previous
        }
        return count
    }

    /// Последние `count` дней по порядку, сегодня последним.
    static func recent(_ count: Int = 7, data: SupplementData, trainedDays: Set<Date>, today: Date = Date(), calendar: Calendar = .current) -> [DayState] {
        let start = calendar.startOfDay(for: today)
        return (0..<count).reversed().compactMap { offset in
            calendar.date(byAdding: .day, value: -offset, to: start).map {
                state(on: $0, data: data, trainedDays: trainedDays, calendar: calendar)
            }
        }
    }

    /// Отметить или снять отметку. Отметка за прошлый день — на полдень того
    /// дня, за сегодня — на сейчас.
    static func toggle(_ item: Supplement, on day: Date, in data: inout SupplementData, now: Date = Date(), calendar: Calendar = .current) {
        if isTaken(item, on: day, in: data, calendar: calendar) {
            data.taken.removeAll { $0.supplementID == item.id && calendar.isDate($0.date, inSameDayAs: day) }
        } else {
            let moment = calendar.isDate(day, inSameDayAs: now)
                ? now
                : calendar.date(bySettingHour: 12, minute: 0, second: 0, of: day) ?? day
            data.taken.append(SupplementIntake(supplementID: item.id, date: moment))
        }
    }

    /// Порядок в списке: утро, день, вечер, с тренировкой; внутри — как добавлены.
    static func ordered(_ items: [Supplement]) -> [Supplement] {
        let order = Dictionary(uniqueKeysWithValues: SupplementSlot.allCases.enumerated().map { ($1, $0) })
        return items.enumerated()
            .sorted { (order[$0.element.slot] ?? 0, $0.offset) < (order[$1.element.slot] ?? 0, $1.offset) }
            .map(\.element)
    }

    /// Отметки старше года больше ни на что не влияют — чистим, чтобы файл не рос.
    static func pruned(_ data: SupplementData, now: Date = Date(), calendar: Calendar = .current) -> SupplementData {
        guard let limit = calendar.date(byAdding: .day, value: -400, to: calendar.startOfDay(for: now)) else { return data }
        var result = data
        result.taken.removeAll { $0.date < limit }
        return result
    }
}
