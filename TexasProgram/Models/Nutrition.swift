import Foundation

// MARK: - Пищевая ценность

/// Калории и Б/Ж/У: одна запись, итог дня или норма на день.
struct Nutrients: Codable, Equatable, Hashable, Sendable {
    var kcal: Double = 0
    var protein: Double = 0
    var fat: Double = 0
    var carbs: Double = 0

    static let zero = Nutrients()

    /// Калории по Б/Ж/У: 4 ккал в грамме белка и углеводов, 9 — в грамме жира.
    static func kcal(protein: Double, fat: Double, carbs: Double) -> Double {
        protein * 4 + fat * 9 + carbs * 4
    }

    static func + (lhs: Nutrients, rhs: Nutrients) -> Nutrients {
        Nutrients(kcal: lhs.kcal + rhs.kcal, protein: lhs.protein + rhs.protein, fat: lhs.fat + rhs.fat, carbs: lhs.carbs + rhs.carbs)
    }

    func divided(by count: Int) -> Nutrients {
        guard count > 0 else { return .zero }
        let n = Double(count)
        return Nutrients(kcal: kcal / n, protein: protein / n, fat: fat / n, carbs: carbs / n)
    }

    /// Норма со сдвигом углеводов или жиров: калории меняются вместе с граммами.
    func adding(carbs deltaCarbs: Double = 0, fat deltaFat: Double = 0) -> Nutrients {
        Nutrients(
            kcal: max(0, kcal + deltaCarbs * 4 + deltaFat * 9),
            protein: protein,
            fat: max(0, fat + deltaFat),
            carbs: max(0, carbs + deltaCarbs)
        )
    }

    /// Порция другого веса: всё меняется в той же пропорции.
    func scaled(by factor: Double) -> Nutrients {
        guard factor.isFinite, factor >= 0 else { return self }
        return Nutrients(kcal: kcal * factor, protein: protein * factor, fat: fat * factor, carbs: carbs * factor)
    }

    /// Для нормы: калории до десятков, граммы до целых.
    var rounded: Nutrients {
        Nutrients(kcal: (kcal / 10).rounded() * 10, protein: protein.rounded(), fat: fat.rounded(), carbs: carbs.rounded())
    }
}

// MARK: - Дневник

/// Одна запись дневника: продукт или блюдо.
struct FoodEntry: Codable, Equatable, Hashable, Identifiable, Sendable {
    var id = UUID()
    /// Когда съедено. День записи — по местной дате.
    var date: Date
    var name: String
    /// Порция в граммах, если известна.
    var grams: Double?
    var nutrients: Nutrients
    /// Распознано по фото — видно, где цифры от ИИ, а где свои.
    var fromPhoto = false
}

enum NutritionGoal: String, Codable, CaseIterable, Identifiable, Sendable {
    case bulk, cut, hold

    var id: String { rawValue }

    var title: String {
        switch self {
        case .bulk: return "Набор"
        case .cut: return "Сушка"
        case .hold: return "Держать вес"
        }
    }

    var explanation: String {
        switch self {
        case .bulk: return "Каждую неделю +30 г углеводов, пока вес не пойдёт вверх"
        case .cut: return "Каждую неделю −13 г жиров, пока вес не пойдёт вниз"
        case .hold: return "Ешь на поддержке, вес держится на месте"
        }
    }
}

/// Как менялась норма: с какого дня и почему.
struct NormChange: Codable, Equatable, Hashable, Sendable {
    var date: Date
    var norm: Nutrients
    var note: String
}

/// Где человек в методе: неделя наблюдения, затем цель и норма на день.
struct NutritionPlan: Codable, Equatable, Sendable {
    /// Первый день недели наблюдения.
    var observationStart: Date
    /// Поддержка, найденная неделей наблюдения.
    var maintenance: Nutrients?
    var goal: NutritionGoal?
    /// Норма на день сейчас.
    var target: Nutrients?
    /// С какого дня действует текущая норма — от него считается неделя до проверки.
    var stepStart: Date?
    var history: [NormChange] = []
}

/// Всё питание профиля. Лежит отдельным файлом, а не в базе профилей:
/// так вкладку можно убрать, и прошлая версия приложения откроется как была.
struct NutritionData: Codable, Equatable, Sendable {
    var entries: [FoodEntry] = []
    var plan: NutritionPlan?
}

// MARK: - Метод Лимара

/// Метод Лимара (@freedom_limar): неделю ешь как обычно и записывай еду и вес
/// натощак. Средние калории при стоящем весе — поддержка. Дальше на наборе
/// каждую неделю +30 г углеводов, на сушке — минус жиры, и смотришь на вес.
///
/// Свои правила поверх ролика: сравниваем средний вес недели, а не дни — по
/// дням вес прыгает; неделя считается, если записано хотя бы 5 дней из 7.
enum LimarMethod {
    static let weekLength = 7
    static let minimumDays = 5
    /// Средний вес сдвинулся меньше чем на столько к прошлой неделе — «стоит на месте».
    static let stableChange = 0.2
    /// Внутри недели наблюдения вес прыгает сильнее — начало с концом сравниваем с запасом.
    static let observationShift = 0.5
    static let carbStep = 30.0
    /// Те же ~120 ккал, что и 30 г углеводов на наборе.
    static let fatStep = 13.0
    /// Ниже этого жиры лучше не опускать: предупреждаем, но не запрещаем.
    static let fatFloorPerKg = 0.6

    static func entries(_ entries: [FoodEntry], on day: Date, calendar: Calendar = .current) -> [FoodEntry] {
        entries.filter { calendar.isDate($0.date, inSameDayAs: day) }.sorted { $0.date < $1.date }
    }

    static func total(_ entries: [FoodEntry]) -> Nutrients {
        entries.reduce(.zero) { $0 + $1.nutrients }
    }

    /// Семь дней начиная с `start`: сколько дней записано и что в среднем.
    struct Week: Equatable {
        let start: Date
        let foodDays: Int
        let weightDays: Int
        /// Среднее за день — по дням, где еда записана.
        let intake: Nutrients?
        let weight: Double?
        /// Последние три замера против первых трёх: насколько вес сдвинулся внутри недели.
        let shift: Double?

        var isComplete: Bool { foodDays >= LimarMethod.minimumDays && weightDays >= LimarMethod.minimumDays }
    }

    static func week(starting start: Date, entries: [FoodEntry], weights: [BodyWeightEntry], calendar: Calendar = .current) -> Week {
        let first = calendar.startOfDay(for: start)
        let end = calendar.date(byAdding: .day, value: weekLength, to: first) ?? first
        let food = entries.filter { $0.date >= first && $0.date < end }
        let days = Dictionary(grouping: food) { calendar.startOfDay(for: $0.date) }.values.map(total)
        let measured = weights.filter { $0.date >= first && $0.date < end }.sorted { $0.date < $1.date }.map(\.weight)
        var shift: Double?
        if measured.count >= minimumDays {
            shift = average(measured.suffix(3)) - average(measured.prefix(3))
        }
        return Week(
            start: first,
            foodDays: days.count,
            weightDays: measured.count,
            intake: days.isEmpty ? nil : days.reduce(Nutrients.zero, +).divided(by: days.count),
            weight: measured.isEmpty ? nil : average(measured),
            shift: shift
        )
    }

    /// Неделя наблюдения: идёт, мало записей или готово.
    enum Observation: Equatable {
        /// Какой сейчас день недели наблюдения, 1…7.
        case collecting(day: Int, week: Week)
        case notEnough(Week)
        /// Средние калории — поддержка. `shift` есть, если вес внутри недели заметно сдвинулся.
        case done(Week, maintenance: Nutrients, shift: Double?)
    }

    static func observation(_ plan: NutritionPlan, entries: [FoodEntry], weights: [BodyWeightEntry],
                            today: Date = Date(), calendar: Calendar = .current) -> Observation {
        let summary = week(starting: plan.observationStart, entries: entries, weights: weights, calendar: calendar)
        let passed = calendar.dateComponents([.day], from: summary.start, to: calendar.startOfDay(for: today)).day ?? 0
        if passed < weekLength { return .collecting(day: max(passed, 0) + 1, week: summary) }
        guard summary.isComplete, let intake = summary.intake else { return .notEnough(summary) }
        let shift = summary.shift.flatMap { abs($0) >= observationShift ? $0 : nil }
        return .done(summary, maintenance: intake.rounded, shift: shift)
    }

    /// С чего начать цель: на наборе сразу +30 г углеводов, на сушке −13 г жиров.
    static func startingNorm(for goal: NutritionGoal, maintenance: Nutrients) -> Nutrients {
        switch goal {
        case .bulk: return maintenance.adding(carbs: carbStep)
        case .cut: return maintenance.adding(fat: -fatStep)
        case .hold: return maintenance
        }
    }

    enum Advice: Equatable {
        case keep, addCarbs, removeCarbs, cutFat
    }

    /// Проверка раз в неделю: что стало с весом при текущей норме и что делать дальше.
    struct Check: Equatable {
        /// Средний вес этой недели против прошлой; `nil` — замеров мало.
        let change: Double?
        let advice: Advice
        /// Норма, если принять совет.
        let proposed: Nutrients
        /// Жиры по совету опустятся ниже 0,6 г на кило веса.
        let lowFat: Bool
    }

    /// `nil`, пока с начала текущей нормы не прошло семи дней.
    static func check(_ plan: NutritionPlan, weights: [BodyWeightEntry], today: Date = Date(), calendar: Calendar = .current) -> Check? {
        guard let goal = plan.goal, let target = plan.target, let stepStart = plan.stepStart else { return nil }
        let start = calendar.startOfDay(for: stepStart)
        let passed = calendar.dateComponents([.day], from: start, to: calendar.startOfDay(for: today)).day ?? 0
        guard passed >= weekLength, let previousStart = calendar.date(byAdding: .day, value: -weekLength, to: start) else { return nil }
        let current = averageWeight(weights, from: start, calendar: calendar)
        let previous = averageWeight(weights, from: previousStart, calendar: calendar)
        var change: Double?
        if let current, let previous { change = current - previous }

        var advice = Advice.keep
        if let change {
            switch goal {
            case .bulk: if change < stableChange { advice = .addCarbs }
            case .cut: if change > -stableChange { advice = .cutFat }
            case .hold:
                if change >= stableChange { advice = .removeCarbs } else if change <= -stableChange { advice = .addCarbs }
            }
        }
        let proposed: Nutrients
        switch advice {
        case .keep: proposed = target
        case .addCarbs: proposed = target.adding(carbs: carbStep)
        case .removeCarbs: proposed = target.adding(carbs: -carbStep)
        case .cutFat: proposed = target.adding(fat: -fatStep)
        }
        let bodyWeight = current ?? previous ?? weights.max { $0.date < $1.date }?.weight
        let lowFat = advice == .cutFat && bodyWeight.map { proposed.fat < $0 * fatFloorPerKg } == true
        return Check(change: change, advice: advice, proposed: proposed, lowFat: lowFat)
    }

    /// Средний вес семи дней от `start`; меньше пяти замеров — не считаем.
    static func averageWeight(_ weights: [BodyWeightEntry], from start: Date, calendar: Calendar = .current) -> Double? {
        let first = calendar.startOfDay(for: start)
        guard let end = calendar.date(byAdding: .day, value: weekLength, to: first) else { return nil }
        let values = weights.filter { $0.date >= first && $0.date < end }.map(\.weight)
        return values.count >= minimumDays ? average(values) : nil
    }

    private static func average<C: Collection>(_ values: C) -> Double where C.Element == Double {
        values.isEmpty ? 0 : values.reduce(0, +) / Double(values.count)
    }
}
