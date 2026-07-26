import Foundation
import SwiftData

enum TrainingProgramKind: String, CaseIterable, Codable, Identifiable, Sendable {
    case texas = "Техасский метод"
    case upperLower = "Верх / Низ"

    var id: String { rawValue }
    var subtitle: String {
        switch self {
        case .texas: return "12 недель · 3 тренировки в неделю · расчёт по 5ПМ"
        case .upperLower: return "7 недель · 4 тренировки в неделю · расчёт по 1ПМ"
        }
    }
}

enum TrainingLevel: String, CaseIterable, Codable, Identifiable, Sendable {
    case beginner = "Начальный"
    case intermediate = "Средний"

    var id: String { rawValue }
}

enum AdditionalExerciseCategory: String, CaseIterable, Identifiable, Hashable {
    case pull = "Дополнительная тяга"
    case arms = "Руки"
    case core = "Кор"
    case back = "Спина"
    case press = "Вертикальный жим"

    var id: String { rawValue }

    var options: [String] {
        switch self {
        case .pull:
            return ["Становая тяга с плинтов (ниже колена)", "Тяга трэп-грифа", "Румынская тяга", "Становая тяга рывковым хватом на прямых ногах", "Обратная гиперэкстензия", "Glute Ham Raise (GHR)"]
        case .arms:
            return ["Растянутый суперсет: бицепс + трицепс"]
        case .core:
            return ["Копенгагенская планка", "Молитва в блоке", "Скручивания лёжа на полу", "Подъём ног в висе", "Скручивания на фитболе"]
        case .back:
            return ["Подтягивания", "Тяга верхнего блока узким/широким хватом", "Вертикальная тяга в хамере", "Тяга Пендли", "Тяга в наклоне", "Горизонтальная тяга блока узким/широким хватом"]
        case .press:
            return ["Жим штанги стоя", "Жим гантелей сидя", "Армейский жим гирь", "Отжимания в стойке на руках", "Z-жим"]
        }
    }
}

struct ProgramInput: Equatable, Codable, Sendable {
    var squat5RM: Double
    var bench5RM: Double
    var deadlift5RM: Double
    var level: TrainingLevel
    var pull: String?
    var arms: String?
    var core: String?
    var back: String?
    var press: String?

    static let demo = ProgramInput(squat5RM: 100, bench5RM: 100, deadlift5RM: 100, level: .intermediate, pull: nil, arms: nil, core: "Копенгагенская планка", back: nil, press: nil)
}

struct UpperLowerInput: Equatable, Codable, Sendable {
    var squat1RM: Double
    var bench1RM: Double
    var deadlift1RM: Double

    static let demo = UpperLowerInput(squat1RM: 100, bench1RM: 100, deadlift1RM: 130)
}

enum LoadPrescription: Equatable, Hashable, Codable, Sendable {
    case kilograms(Double)
    case rpe(String)
    case repRange(String)
    case testOneRepMax

    private enum CodingKeys: String, CodingKey { case kind, value }
    private enum Kind: String, Codable { case kilograms, rpe, repRange, testOneRepMax }

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        let kind = try values.decode(Kind.self, forKey: .kind)
        switch kind {
        case .kilograms: self = .kilograms(try values.decode(Double.self, forKey: .value))
        case .rpe: self = .rpe(try values.decode(String.self, forKey: .value))
        case .repRange: self = .repRange(try values.decode(String.self, forKey: .value))
        case .testOneRepMax: self = .testOneRepMax
        }
    }

    func encode(to encoder: Encoder) throws {
        var values = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case .kilograms(let value):
            try values.encode(Kind.kilograms, forKey: .kind); try values.encode(value, forKey: .value)
        case .rpe(let value):
            try values.encode(Kind.rpe, forKey: .kind); try values.encode(value, forKey: .value)
        case .repRange(let value):
            try values.encode(Kind.repRange, forKey: .kind); try values.encode(value, forKey: .value)
        case .testOneRepMax:
            try values.encode(Kind.testOneRepMax, forKey: .kind)
        }
    }

    var displayText: String {
        switch self {
        case .kilograms(let value):
            return WeightFormat.kilogramsPrecise(value)
        case .rpe(let value): return value
        case .repRange(let value): return value
        case .testOneRepMax: return "Тест 1ПМ"
        }
    }
}

struct ExercisePrescription: Identifiable, Equatable, Hashable, Codable, Sendable {
    /// Стабильный идентификатор: имя упражнения уникально внутри дня.
    /// Случайный UUID здесь ломал бы identity во ForEach при каждом пересчёте плана.
    let id: String
    let name: String
    let sets: Int
    let reps: String
    let load: LoadPrescription
    let isOptional: Bool
    /// Номер тренировки в волне «Жим 14», если упражнение — часть жимовой волны.
    let benchSession: Int?

    init(id: String? = nil, name: String, sets: Int, reps: String, load: LoadPrescription, isOptional: Bool = false, benchSession: Int? = nil) {
        self.id = id ?? name; self.name = name; self.sets = sets; self.reps = reps; self.load = load; self.isOptional = isOptional; self.benchSession = benchSession
    }
}

struct WorkoutDayPlan: Identifiable, Equatable, Hashable, Codable, Sendable {
    let id: String
    let number: Int
    let title: String
    let exercises: [ExercisePrescription]
}

struct WorkoutWeekPlan: Identifiable, Equatable, Hashable, Codable, Sendable {
    let id: Int
    let number: Int
    let days: [WorkoutDayPlan]
}

struct WorkoutPlan: Equatable, Codable, Sendable {
    let weeks: [WorkoutWeekPlan]
    let isPeaking: Bool
}

@Model
final class ProgramProfile {
    var programKindRaw: String = TrainingProgramKind.texas.rawValue
    var squat5RM: Double
    var bench5RM: Double
    var deadlift5RM: Double
    var levelRaw: String
    var pull: String?
    var arms: String?
    var core: String?
    var back: String?
    var press: String?
    var completedDayKeys: [String]
    var completedBenchSessions: [Int] = []
    /// Дни недели тренировок в нумерации `Calendar` (1 — воскресенье). Пусто — значения по умолчанию.
    var scheduleWeekdays: [Int] = []
    var cycleStartedAt: Date
    var peakingActive: Bool
    var peakSquat5RM: Double?
    var peakBench5RM: Double?
    var peakDeadlift5RM: Double?

    init(input: ProgramInput = .demo) {
        programKindRaw = TrainingProgramKind.texas.rawValue
        squat5RM = input.squat5RM; bench5RM = input.bench5RM; deadlift5RM = input.deadlift5RM
        levelRaw = input.level.rawValue; pull = input.pull; arms = input.arms; core = input.core; back = input.back; press = input.press
        completedDayKeys = []; cycleStartedAt = .now; peakingActive = false
    }

    init(upperLowerInput: UpperLowerInput) {
        programKindRaw = TrainingProgramKind.upperLower.rawValue
        squat5RM = upperLowerInput.squat1RM
        bench5RM = upperLowerInput.bench1RM
        deadlift5RM = upperLowerInput.deadlift1RM
        levelRaw = TrainingLevel.beginner.rawValue
        pull = nil; arms = nil; core = nil; back = nil; press = nil
        completedDayKeys = []; cycleStartedAt = .now; peakingActive = false
    }

    var programKind: TrainingProgramKind {
        get { TrainingProgramKind(rawValue: programKindRaw) ?? .texas }
        set { programKindRaw = newValue.rawValue }
    }

    var level: TrainingLevel {
        get { TrainingLevel(rawValue: levelRaw) ?? .beginner }
        set { levelRaw = newValue.rawValue }
    }

    var input: ProgramInput {
        ProgramInput(squat5RM: squat5RM, bench5RM: bench5RM, deadlift5RM: deadlift5RM, level: level, pull: pull, arms: arms, core: core, back: back, press: press)
    }

    var upperLowerInput: UpperLowerInput {
        UpperLowerInput(squat1RM: squat5RM, bench1RM: bench5RM, deadlift1RM: deadlift5RM)
    }

    var workoutPlan: WorkoutPlan {
        switch programKind {
        case .texas: return ProgramCalculator.generate(input: input)
        case .upperLower: return UpperLowerCalculator.generate(input: upperLowerInput)
        }
    }

    var maximumLabel: String { programKind == .texas ? "5ПМ" : "1ПМ" }
    var totalDays: Int { workoutPlan.weeks.reduce(0) { $0 + $1.days.count } }

    func isCompleted(week: Int, day: Int) -> Bool { completedDayKeys.contains("\(week)-\(day)") }

    /// Отметка дня. Для «Верх / Низ» синхронно двигает связанную тренировку волны жима.
    func toggleCompleted(week: Int, day: Int) {
        let done = !isCompleted(week: week, day: day)
        setDayCompleted(week: week, day: day, done)
        if let session = benchSession(week: week, day: day) { setBenchCompleted(session, done) }
    }

    private func setDayCompleted(week: Int, day: Int, _ done: Bool) {
        let key = "\(week)-\(day)"
        if done {
            if !completedDayKeys.contains(key) { completedDayKeys.append(key) }
        } else {
            completedDayKeys.removeAll { $0 == key }
        }
    }

    // MARK: - Расписание по дням недели

    var trainingDayCount: Int { programKind == .texas ? 3 : 4 }

    /// «Верх / Низ» — понедельник, вторник, четверг, пятница. Техас — понедельник, среда, пятница.
    var defaultWeekdays: [Int] { programKind == .texas ? [2, 4, 6] : [2, 3, 5, 6] }

    var weekdays: [Int] {
        scheduleWeekdays.count == trainingDayCount ? scheduleWeekdays : defaultWeekdays
    }

    func weekday(forDay day: Int) -> Int {
        let list = weekdays
        guard day >= 1, day <= list.count else { return list.first ?? 2 }
        return list[day - 1]
    }

    func setWeekday(_ weekday: Int, forDay day: Int) {
        var list = weekdays
        guard day >= 1, day <= list.count else { return }
        list[day - 1] = weekday
        scheduleWeekdays = list
    }

    func weekdayName(forDay day: Int) -> String { RuDate.full(weekday: weekday(forDay: day)) }
    func shortWeekdayName(forDay day: Int) -> String { RuDate.short(weekday: weekday(forDay: day)) }

    /// Заголовок дня с реальным днём недели: «ПОНЕДЕЛЬНИК · ВЕРХ ТЯЖЁЛЫЙ».
    func fullTitle(forDay day: WorkoutDayPlan) -> String {
        weekdayName(forDay: day.number).uppercased() + " · " + day.title
    }

    var schedule: WorkoutSchedule {
        WorkoutScheduler.build(profile: self, plan: workoutPlan)
    }

    // MARK: - Волна «Жим 14»

    var benchWave: [BenchSessionPlan] {
        guard programKind == .upperLower else { return [] }
        return UpperLowerCalculator.benchWave(input: upperLowerInput)
    }

    /// Номер жимовой тренировки волны для дня программы «Верх / Низ».
    /// Понедельник (день 1) — нечётные номера, четверг (день 3) — чётные.
    func benchSession(week: Int, day: Int) -> Int? {
        guard programKind == .upperLower else { return nil }
        switch day {
        case 1: return (week - 1) * 2 + 1
        case 3: return (week - 1) * 2 + 2
        default: return nil
        }
    }

    /// День программы, которому принадлежит тренировка волны жима.
    func day(forBenchSession session: Int) -> (week: Int, day: Int) {
        ((session + 1) / 2, session % 2 == 1 ? 1 : 3)
    }

    func isBenchCompleted(_ session: Int) -> Bool { completedBenchSessions.contains(session) }

    /// Отметка жимовой тренировки синхронно отмечает соответствующий день программы.
    func toggleBenchCompleted(_ session: Int) {
        let done = !isBenchCompleted(session)
        setBenchCompleted(session, done)
        let target = day(forBenchSession: session)
        setDayCompleted(week: target.week, day: target.day, done)
    }

    private func setBenchCompleted(_ session: Int, _ done: Bool) {
        if done {
            if !completedBenchSessions.contains(session) { completedBenchSessions.append(session) }
        } else {
            completedBenchSessions.removeAll { $0 == session }
        }
    }

    var completedBenchCount: Int { Set(completedBenchSessions).count }

    /// Первая неотмеченная жимовая тренировка волны.
    var nextBenchSession: Int? {
        (1...UpperLowerCalculator.benchSessionCount).first { !isBenchCompleted($0) }
    }

    var peakingInput: ProgramInput {
        var value = input
        value.squat5RM = peakSquat5RM ?? squat5RM
        value.bench5RM = peakBench5RM ?? bench5RM
        value.deadlift5RM = peakDeadlift5RM ?? deadlift5RM
        return value
    }

    var completedDayCount: Int { Set(completedDayKeys).count }
}
