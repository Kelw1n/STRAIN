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

/// Запись о выполненной тренировке: когда и с какими весами основных движений.
///
/// Веса сохраняем в момент отметки, а не считаем потом заново: максимумы можно
/// поменять в настройках, и тогда история задним числом стала бы неверной.
struct CompletionRecord: Codable, Equatable, Hashable, Sendable, Identifiable {
    let key: String
    let date: Date
    let week: Int
    let day: Int
    /// Рабочие веса приседа, жима и тяги в этот день; нет упражнения — нет и веса.
    var squat: Double?
    var bench: Double?
    var deadlift: Double?
    /// Что реально поднято в этот день, если подходы записывались.
    /// Необязательные: у дней без записей факта просто нет.
    var actualSquat: Double? = nil
    var actualBench: Double? = nil
    var actualDeadlift: Double? = nil

    var id: String { key + "-" + String(Int(date.timeIntervalSince1970)) }
}

/// Как невыполненные тренировки раскладываются по календарю.
enum ScheduleMode: String, CaseIterable, Codable, Identifiable, Sendable {
    /// День недели задаёт тип тренировки: в понедельник — понедельничная,
    /// просто ближайшей невыполненной недели.
    case weekday = "По дням недели"
    /// Невыполненные идут строго подряд по ближайшим тренировочным дням,
    /// даже если тип дня не совпадает с днём недели.
    case queue = "Очередь"

    var id: String { rawValue }

    var explanation: String {
        switch self {
        case .weekday:
            return "В понедельник — понедельничная тренировка, в четверг — четверговая. Пропущенная не теряется: она достанется следующему такому же дню недели."
        case .queue:
            return "Невыполненные тренировки идут строго подряд по ближайшим тренировочным дням. Пропустил четверг — сделаешь его в ближайший тренировочный день, каким бы он ни был."
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
    var profileID: UUID = UUID()
    var name: String = "Профиль"
    var createdAt: Date = Date()
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
    /// Когда какая тренировка была отмечена. Ключей в `completedDayKeys` мало
    /// для графиков: там нет дат, а без них прогресс во времени не построить.
    var completionLog: [CompletionRecord] = []
    /// Сколько подходов закрыто в каждом упражнении: ключ «деньПлана|идентификаторУпражнения».
    var setProgress: [String: Int] = [:]
    /// Длительность отдыха, который запускается сам после отметки подхода.
    var defaultRestSeconds: Int = 180
    /// Свои упражнения и правки дней. Свойство новое: у существующих профилей
    /// подставится пустой список, и план останется прежним.
    var planEdits: [PlanEdit] = []
    /// Что реально сделано в подходе: ключ «деньПлана|упражнение|номерПодхода».
    var setLog: [String: SetEntry] = [:]
    /// Откаты после несданных весов.
    var deloads: [DeloadEvent] = []
    /// Не гасить экран, пока открыта тренировка.
    var keepScreenOn: Bool = true
    var completedBenchSessions: [Int] = []
    /// Дни недели тренировок в нумерации `Calendar` (1 — воскресенье). Пусто — значения по умолчанию.
    var scheduleWeekdays: [Int] = []
    /// Явная привязка недели плана к календарю: номер недели и понедельник её календарной недели.
    /// Используется только в режиме «по дням недели».
    var scheduleAnchorWeek: Int = 0
    var scheduleAnchorDate: Date?
    /// Как план ложится на календарь. Свойство новое: у существующих профилей
    /// подставится значение по умолчанию, то есть режим по дням недели.
    var scheduleModeRaw: String = ScheduleMode.weekday.rawValue
    /// Дата последней отметки. В режиме очереди по ней видно, что сегодня уже тренировались.
    var lastCompletionDate: Date?
    var cycleStartedAt: Date
    var peakingActive: Bool
    var peakSquat5RM: Double?
    var peakBench5RM: Double?
    var peakDeadlift5RM: Double?

    init(input: ProgramInput = .demo, name: String = "Профиль") {
        profileID = UUID(); self.name = name; createdAt = .now
        programKindRaw = TrainingProgramKind.texas.rawValue
        squat5RM = input.squat5RM; bench5RM = input.bench5RM; deadlift5RM = input.deadlift5RM
        levelRaw = input.level.rawValue; pull = input.pull; arms = input.arms; core = input.core; back = input.back; press = input.press
        completedDayKeys = []; cycleStartedAt = .now; peakingActive = false
    }

    init(upperLowerInput: UpperLowerInput, name: String = "Профиль") {
        profileID = UUID(); self.name = name; createdAt = .now
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

    var scheduleMode: ScheduleMode {
        get { ScheduleMode(rawValue: scheduleModeRaw) ?? .weekday }
        set { scheduleModeRaw = newValue.rawValue }
    }

    var input: ProgramInput {
        ProgramInput(squat5RM: squat5RM, bench5RM: bench5RM, deadlift5RM: deadlift5RM, level: level, pull: pull, arms: arms, core: core, back: back, press: press)
    }

    var upperLowerInput: UpperLowerInput {
        UpperLowerInput(squat1RM: squat5RM, bench1RM: bench5RM, deadlift1RM: deadlift5RM)
    }

    /// Идёт ли сейчас пиковый цикл. Только «Техас» — у «Верх / Низ» пикирования нет.
    var isPeaking: Bool { programKind == .texas && peakingActive }

    /// План программы с откатами и пользовательскими правками.
    ///
    /// Порядок обязателен: сначала откаты режут веса расчёта, потом правки
    /// подставляют свои упражнения. Наоборот откат срезал бы вес, который
    /// пользователь вписал руками.
    var workoutPlan: WorkoutPlan { applyEdits(to: applyDeloads(to: generatedPlan)) }

    /// Чистый расчёт без правок — нужен экрану настройки, чтобы показать,
    /// что именно программа предлагала до вмешательства.
    var generatedPlan: WorkoutPlan {
        switch programKind {
        case .texas:
            return isPeaking
                ? ProgramCalculator.generatePeaking(input: peakingInput)
                : ProgramCalculator.generate(input: input)
        case .upperLower:
            return UpperLowerCalculator.generate(input: upperLowerInput)
        }
    }

    private func applyEdits(to plan: WorkoutPlan) -> WorkoutPlan {
        // Быстрый выход: у профиля без правок расчёт остаётся прежним.
        guard !planEdits.isEmpty else { return plan }
        let peaking = isPeaking
        let weeks = plan.weeks.map { week in
            WorkoutWeekPlan(id: week.id, number: week.number, days: week.days.map { day in
                let edits = planEdits.filter { $0.matches(week: week.number, day: day.number, isPeaking: peaking) }
                guard !edits.isEmpty else { return day }
                var list = day.exercises
                // Порядок важен: сначала убираем и заменяем упражнения программы,
                // и только потом дописываем свои — иначе замена могла бы попасть
                // на только что добавленное упражнение.
                for edit in edits where edit.kind == .hide {
                    list.removeAll { $0.id == edit.targetID }
                }
                for edit in edits where edit.kind == .replace {
                    guard let index = list.firstIndex(where: { $0.id == edit.targetID }) else { continue }
                    list[index] = edit.prescription
                }
                for edit in edits where edit.kind == .add {
                    list.append(edit.prescription)
                }
                // Порядок применяем последним: к этому моменту состав дня уже
                // окончательный. Упражнения, которых нет в списке, идут в конец —
                // их могли добавить после перестановки.
                if let order = edits.last(where: { $0.kind == .order })?.order {
                    let rank = Dictionary(uniqueKeysWithValues: order.enumerated().map { ($0.element, $0.offset) })
                    // Сортировка в Swift не обещает устойчивости, поэтому вторым
                    // ключом идёт исходная позиция: упражнения вне списка сохранят ряд.
                    list = list.enumerated()
                        .sorted { left, right in
                            (rank[left.element.id] ?? Int.max, left.offset)
                                < (rank[right.element.id] ?? Int.max, right.offset)
                        }
                        .map(\.element)
                }
                return WorkoutDayPlan(id: day.id, number: day.number, title: day.title, exercises: list)
            })
        }
        return WorkoutPlan(weeks: weeks, isPeaking: plan.isPeaking)
    }

    // MARK: - Серия без пропусков

    /// Недели подряд, закрытые полностью.
    ///
    /// Считаем по отметкам, а не по календарю: тренировка, пропущенная и позже
    /// наверстанная, серию не рвёт — важно, что неделя в итоге закрыта.
    /// Незаконченная последняя неделя серию тоже не обрывает, она просто ещё идёт.
    var weekStreak: (current: Int, best: Int) {
        let weeks = workoutPlan.weeks
        guard !weeks.isEmpty else { return (0, 0) }

        let closed = weeks.map { week in
            week.days.allSatisfy { isCompleted(week: week.number, day: $0.number) }
        }

        var best = 0
        var run = 0
        for done in closed {
            run = done ? run + 1 : 0
            best = max(best, run)
        }

        // Текущая серия — та, что упирается в последнюю закрытую неделю.
        var current = 0
        var index = closed.count - 1
        while index >= 0, !closed[index] { index -= 1 }
        while index >= 0, closed[index] {
            current += 1
            index -= 1
        }
        return (current, best)
    }

    // MARK: - Откаты после несданного веса

    /// Откаты, действующие на неделю: срабатывают все, объявленные не позже неё.
    func activeDeloads(upTo week: Int) -> [DeloadEvent] {
        deloads.filter { $0.isPeaking == isPeaking && $0.week <= week }
    }

    /// Во сколько раз урезаны веса этой недели. Несколько откатов перемножаются.
    func deloadFactor(week: Int) -> Double {
        activeDeloads(upTo: week).reduce(1.0) { $0 * $1.factor }
    }

    private func applyDeloads(to plan: WorkoutPlan) -> WorkoutPlan {
        let active = deloads.filter { $0.isPeaking == isPeaking }
        guard !active.isEmpty else { return plan }
        return WorkoutPlan(weeks: plan.weeks.map { week in
            let factor = deloadFactor(week: week.number)
            guard factor < 1 else { return week }
            return WorkoutWeekPlan(id: week.id, number: week.number, days: week.days.map { day in
                WorkoutDayPlan(id: day.id, number: day.number, title: day.title, exercises: day.exercises.map { exercise in
                    // Режем только конкретные килограммы: RPE и тест 1ПМ процентам не подчиняются.
                    guard case .kilograms(let value) = exercise.load else { return exercise }
                    return ExercisePrescription(
                        id: exercise.id,
                        name: exercise.name,
                        sets: exercise.sets,
                        reps: exercise.reps,
                        load: .kilograms(ProgramCalculator.roundToPlate(value * factor)),
                        isOptional: exercise.isOptional,
                        benchSession: exercise.benchSession
                    )
                })
            })
        }, isPeaking: plan.isPeaking)
    }

    /// «Не взял вес»: с этой недели расчёт идёт от сниженных процентов.
    func addDeload(fromWeek week: Int, percent: Double, now: Date = Date()) {
        deloads.append(DeloadEvent(week: week, percent: percent, isPeaking: isPeaking, date: now))
    }

    func removeDeload(_ event: DeloadEvent) {
        deloads.removeAll { $0.id == event.id }
    }

    // MARK: - Фактически выполненные подходы

    private func logKey(week: Int, day: Int, exercise: ExercisePrescription, index: Int) -> String {
        setKey(week: week, day: day, exercise: exercise) + "|\(index)"
    }

    func setEntry(week: Int, day: Int, exercise: ExercisePrescription, index: Int) -> SetEntry? {
        setLog[logKey(week: week, day: day, exercise: exercise, index: index)]
    }

    /// Записывает факт подхода и заодно закрывает точку: отмечать дважды незачем.
    func recordSet(week: Int, day: Int, exercise: ExercisePrescription, index: Int, reps: Int, weight: Double, now: Date = Date()) {
        setLog[logKey(week: week, day: day, exercise: exercise, index: index)] = SetEntry(reps: reps, weight: weight)
        let key = setKey(week: week, day: day, exercise: exercise)
        if (setProgress[key] ?? 0) < index + 1 { setProgress[key] = index + 1 }
        refreshCompletion(week: week, day: day, now: now)
    }

    func clearSetEntry(week: Int, day: Int, exercise: ExercisePrescription, index: Int, now: Date = Date()) {
        setLog[logKey(week: week, day: day, exercise: exercise, index: index)] = nil
        refreshCompletion(week: week, day: day, now: now)
    }

    /// Записанные подходы упражнения по порядку номеров.
    func entries(week: Int, day: Int, exercise: ExercisePrescription) -> [(index: Int, entry: SetEntry)] {
        (0..<max(exercise.sets, 0)).compactMap { index in
            setEntry(week: week, day: day, exercise: exercise, index: index).map { (index, $0) }
        }
    }

    /// Наибольший записанный вес движения за день — им факт попадает в график.
    func actualWeight(week: Int, day: Int, exerciseNames: [String]) -> Double? {
        let exercises = workoutPlan.weeks.first { $0.number == week }?
            .days.first { $0.number == day }?.exercises ?? []
        let matched = exercises.filter { exerciseNames.contains($0.name) }
        let weights = matched.flatMap { entries(week: week, day: day, exercise: $0).map(\.entry.weight) }
        return weights.max()
    }

    /// Тоннаж по неделям: только записанные подходы, без догадок по плану.
    var weeklyTonnage: [WeeklyTonnage] {
        var totals: [Int: (sum: Double, count: Int)] = [:]
        for (key, entry) in setLog {
            guard let week = SetKeyParts.week(from: key, isPeaking: isPeaking) else { continue }
            let current = totals[week] ?? (0, 0)
            totals[week] = (current.sum + entry.tonnage, current.count + 1)
        }
        return totals
            .map { WeeklyTonnage(week: $0.key, total: $0.value.sum, setCount: $0.value.count) }
            .sorted { $0.week < $1.week }
    }

    // MARK: - Свои упражнения

    /// Порядок упражнений дня. Пустой список убирает свою перестановку.
    func setOrder(_ ids: [String], week: Int, day: Int, scope: PlanEditScope) {
        let peaking = isPeaking
        planEdits.removeAll {
            $0.kind == .order && $0.day == day && $0.isPeaking == peaking
                && (scope == .everyWeek || $0.week == nil || $0.week == week)
        }
        guard !ids.isEmpty else { return }
        planEdits.append(PlanEdit(
            kind: .order,
            day: day,
            week: scope == .single ? week : nil,
            isPeaking: peaking,
            order: ids
        ))
    }

    /// Правки, которые действуют на конкретную тренировку.
    func edits(week: Int, day: Int) -> [PlanEdit] {
        planEdits.filter { $0.matches(week: week, day: day, isPeaking: isPeaking) }
    }

    /// Упражнения дня, как их предлагает сама программа, без правок.
    func generatedExercises(week: Int, day: Int) -> [ExercisePrescription] {
        generatedPlan.weeks.first { $0.number == week }?
            .days.first { $0.number == day }?.exercises ?? []
    }

    @discardableResult
    func addExercise(
        name: String,
        sets: Int,
        reps: String,
        kilograms: Double?,
        loadText: String?,
        week: Int,
        day: Int,
        scope: PlanEditScope
    ) -> PlanEdit {
        let edit = PlanEdit(
            kind: .add,
            day: day,
            week: scope == .single ? week : nil,
            isPeaking: isPeaking,
            name: name,
            sets: sets,
            reps: reps,
            kilograms: kilograms,
            loadText: loadText
        )
        planEdits.append(edit)
        return edit
    }

    @discardableResult
    func replaceExercise(
        _ target: ExercisePrescription,
        name: String,
        sets: Int,
        reps: String,
        kilograms: Double?,
        loadText: String?,
        week: Int,
        day: Int,
        scope: PlanEditScope
    ) -> PlanEdit {
        dropEdits(targeting: target.id, day: day, week: week, scope: scope)
        let edit = PlanEdit(
            kind: .replace,
            day: day,
            week: scope == .single ? week : nil,
            isPeaking: isPeaking,
            targetID: target.id,
            name: name,
            sets: sets,
            reps: reps,
            kilograms: kilograms,
            loadText: loadText
        )
        planEdits.append(edit)
        return edit
    }

    @discardableResult
    func hideExercise(_ target: ExercisePrescription, week: Int, day: Int, scope: PlanEditScope) -> PlanEdit {
        dropEdits(targeting: target.id, day: day, week: week, scope: scope)
        let edit = PlanEdit(
            kind: .hide,
            day: day,
            week: scope == .single ? week : nil,
            isPeaking: isPeaking,
            targetID: target.id
        )
        planEdits.append(edit)
        return edit
    }

    /// Убирает правку и вместе с ней осиротевшие отметки подходов:
    /// упражнения больше нет, а его закрытые подходы остались бы в хранилище.
    func removeEdit(_ edit: PlanEdit) {
        planEdits.removeAll { $0.id == edit.id }
        // Ключи снимаем заранее: словарь нельзя править, обходя его же ключи.
        let suffix = "|" + edit.id
        let orphaned = setProgress.keys.filter { $0.hasSuffix(suffix) }
        for key in orphaned { setProgress[key] = nil }
        // У журнала факта ключ длиннее на номер подхода, поэтому ищем вхождение.
        let inner = "|" + edit.id + "|"
        let orphanedLog = setLog.keys.filter { $0.contains(inner) }
        for key in orphanedLog { setLog[key] = nil }
    }

    func removeEdits(week: Int, day: Int) {
        for edit in edits(week: week, day: day) { removeEdit(edit) }
    }

    /// Одно упражнение — одна правка. Иначе замена поверх замены накопила бы
    /// цепочку, из которой не выбраться кнопкой «вернуть как было».
    private func dropEdits(targeting targetID: String, day: Int, week: Int, scope: PlanEditScope) {
        let peaking = isPeaking
        let stale = planEdits.filter {
            $0.targetID == targetID && $0.day == day && $0.isPeaking == peaking
                && (scope == .everyWeek || $0.week == nil || $0.week == week)
        }
        for edit in stale { removeEdit(edit) }
    }

    var maximumLabel: String { programKind == .texas ? "5ПМ" : "1ПМ" }
    var totalDays: Int { workoutPlan.weeks.reduce(0) { $0 + $1.days.count } }

    /// Ключ отметки. У пикового цикла своё пространство имён, иначе его дни
    /// накладывались бы на дни основной программы — недели там нумеруются с единицы.
    func dayKey(week: Int, day: Int) -> String {
        isPeaking ? "peak-\(week)-\(day)" : "\(week)-\(day)"
    }

    func isCompleted(week: Int, day: Int) -> Bool { completedDayKeys.contains(dayKey(week: week, day: day)) }

    /// Отметка дня. Верхний день двигает волну жима на одну тренировку:
    /// какая именно это тренировка, определяет счётчик волны, а не день недели.
    func toggleCompleted(week: Int, day: Int, now: Date = Date()) {
        let done = !isCompleted(week: week, day: day)
        setDayCompleted(week: week, day: day, done)
        if carriesBenchWave(day: day) {
            if done {
                if let next = nextBenchSession { setBenchCompleted(next, true) }
            } else if let last = completedBenchSessions.max() {
                setBenchCompleted(last, false)
            }
        }
        syncSets(week: week, day: day, filled: done)
        if done {
            lastCompletionDate = now
            recordCompletion(week: week, day: day, now: now)
        } else {
            let key = dayKey(week: week, day: day)
            completionLog.removeAll { $0.key == key }
        }
    }

    /// Кладёт в историю дату и рабочие веса основных движений этого дня.
    private func recordCompletion(week: Int, day: Int, now: Date) {
        let key = dayKey(week: week, day: day)
        completionLog.removeAll { $0.key == key }

        let exercises = workoutPlan.weeks.first { $0.number == week }?
            .days.first { $0.number == day }?.exercises ?? []

        func weight(_ name: String) -> Double? {
            guard let load = exercises.first(where: { $0.name == name })?.load,
                  case .kilograms(let value) = load else { return nil }
            return value
        }

        completionLog.append(CompletionRecord(
            key: key,
            date: now,
            week: week,
            day: day,
            squat: weight("Приседания") ?? weight("Приседания со штангой"),
            bench: weight("Жим лёжа"),
            deadlift: weight("Становая тяга"),
            actualSquat: actualWeight(week: week, day: day, exerciseNames: ["Приседания", "Приседания со штангой"]),
            actualBench: actualWeight(week: week, day: day, exerciseNames: ["Жим лёжа"]),
            actualDeadlift: actualWeight(week: week, day: day, exerciseNames: ["Становая тяга"])
        ))
    }

    /// Обновляет запись истории, если день уже отмечен: факт мог появиться позже.
    /// Дату оставляем исходную — тренировка была тогда, а не в момент правки.
    private func refreshCompletion(week: Int, day: Int, now: Date) {
        guard isCompleted(week: week, day: day) else { return }
        let key = dayKey(week: week, day: day)
        let original = completionLog.first { $0.key == key }?.date ?? now
        recordCompletion(week: week, day: day, now: original)
    }

    // MARK: - Отметка по подходам

    private func setKey(week: Int, day: Int, exercise: ExercisePrescription) -> String {
        dayKey(week: week, day: day) + "|" + exercise.id
    }

    func completedSets(week: Int, day: Int, exercise: ExercisePrescription) -> Int {
        setProgress[setKey(week: week, day: day, exercise: exercise)] ?? 0
    }

    /// Тап по точке доводит счётчик до неё; повторный тап по последней закрытой — снимает её.
    /// Возвращает `true`, если подход добавлен, — по этому признаку запускается отдых.
    @discardableResult
    func toggleSet(week: Int, day: Int, exercise: ExercisePrescription, index: Int) -> Bool {
        let key = setKey(week: week, day: day, exercise: exercise)
        let current = setProgress[key] ?? 0
        let target = current == index + 1 ? index : index + 1
        setProgress[key] = target > 0 ? target : nil
        return target > current
    }

    /// Упражнения без подходов (тест 1ПМ) в расчёт не идут — там нечего отмечать.
    func allSetsDone(for workout: ScheduledWorkout) -> Bool {
        let list = exercises(for: workout).filter { $0.sets > 0 }
        guard !list.isEmpty else { return false }
        return list.allSatisfy {
            completedSets(week: workout.week, day: workout.day.number, exercise: $0) >= $0.sets
        }
    }

    /// Точки и отметка дня не должны расходиться: закрыли день — гасим все точки.
    private func syncSets(week: Int, day: Int, filled: Bool) {
        let exercises = workoutPlan.weeks.first { $0.number == week }?
            .days.first { $0.number == day }?.exercises ?? []
        for exercise in exercises {
            let key = setKey(week: week, day: day, exercise: exercise)
            setProgress[key] = filled && exercise.sets > 0 ? exercise.sets : nil
        }
    }

    /// Дни, которые несут волну жима: в «Верх / Низ» это верхние дни — первый и третий.
    func carriesBenchWave(day: Int) -> Bool {
        programKind == .upperLower && (day == 1 || day == 3)
    }

    /// Упражнения тренировки: вспомогательные — из дня программы, жим — из волны.
    func exercises(for workout: ScheduledWorkout) -> [ExercisePrescription] {
        exercises(day: workout.day, benchSession: workout.benchSession)
    }

    /// То же самое, но по дню напрямую. Экран деталей берёт день из свежего плана,
    /// а не из расписания, собранного до правки, — иначе добавленное упражнение
    /// появлялось бы только после выхода и повторного захода.
    func exercises(day: WorkoutDayPlan, benchSession: Int?) -> [ExercisePrescription] {
        guard let session = benchSession,
              let wave = benchWave.first(where: { $0.id == session })
        else { return day.exercises }

        return day.exercises.map { exercise in
            guard exercise.benchSession != nil else { return exercise }
            return ExercisePrescription(
                id: exercise.id,
                name: wave.exerciseName,
                sets: wave.sets.count,
                reps: wave.repsText,
                load: .repRange(wave.weightsText),
                benchSession: wave.id
            )
        }
    }

    private func setDayCompleted(week: Int, day: Int, _ done: Bool) {
        let key = dayKey(week: week, day: day)
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

    /// Ближайшая дата, на которую попадёт тренировочный день с его днём недели.
    func nextOccurrence(ofDay day: Int, now: Date = Date(), calendar base: Calendar = .current) -> Date {
        let calendar = WorkoutScheduler.trainingCalendar(base)
        let startOfToday = calendar.startOfDay(for: now)
        let weekStart = calendar.dateInterval(of: .weekOfYear, for: startOfToday)?.start ?? startOfToday
        let offset = (weekday(forDay: day) - 2 + 7) % 7
        let candidate = calendar.date(byAdding: .day, value: offset, to: weekStart) ?? startOfToday
        guard candidate < startOfToday else { return candidate }
        return calendar.date(byAdding: .weekOfYear, value: 1, to: candidate) ?? candidate
    }

    /// Дата, на которую встанет день плана, если сделать его текущим.
    /// В очереди это ближайший свободный тренировочный день, иначе — свой день недели.
    func plannedStartDate(forDay day: Int, now: Date = Date(), calendar: Calendar = .current) -> Date {
        switch scheduleMode {
        case .queue: return WorkoutScheduler.nextTrainingSlot(profile: self, now: now, calendar: calendar)
        case .weekday: return nextOccurrence(ofDay: day, now: now, calendar: calendar)
        }
    }

    /// «Я сейчас здесь»: всё до выбранного дня отмечается выполненным, выбранный день
    /// становится следующим. Нужно после переустановки, когда история отметок потеряна.
    func setCurrentWorkout(week: Int, day: Int, now: Date = Date(), calendar: Calendar = .current) {
        // Чужое пространство имён не трогаем: сброс внутри пикирования
        // не должен стирать историю основного цикла и наоборот.
        completedDayKeys = completedDayKeys.filter { isPeaking ? !$0.hasPrefix("peak-") : $0.hasPrefix("peak-") }
        // История отмеченного задним числом недостоверна — у тех дней нет реальных дат.
        completionLog.removeAll { isPeaking ? $0.key.hasPrefix("peak-") : !$0.key.hasPrefix("peak-") }
        completedBenchSessions = []
        // Волна остаётся непрерывной: сколько верхних дней закрыли, столько жимов и сделано.
        var benchDone = 0
        for planWeek in workoutPlan.weeks {
            for planDay in planWeek.days where planWeek.number < week || (planWeek.number == week && planDay.number < day) {
                setDayCompleted(week: planWeek.number, day: planDay.number, true)
                if carriesBenchWave(day: planDay.number) { benchDone += 1 }
            }
        }
        if benchDone > 0 { completedBenchSessions = Array(1...min(benchDone, UpperLowerCalculator.benchSessionCount)) }
        // Отметки проставлены задним числом — сегодняшней тренировки среди них нет.
        lastCompletionDate = nil
        // Привязка к календарной неделе больше не используется: оба режима считают
        // даты от сегодняшнего дня и от того, что осталось невыполненным.
        scheduleAnchorWeek = 0
        scheduleAnchorDate = nil
    }

    /// То же самое, но по номеру тренировки волны жима.
    func setCurrentBenchSession(_ session: Int, now: Date = Date(), calendar: Calendar = .current) {
        let target = day(forBenchSession: session)
        setCurrentWorkout(week: target.week, day: target.day, now: now, calendar: calendar)
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
    func toggleBenchCompleted(_ session: Int, now: Date = Date()) {
        let done = !isBenchCompleted(session)
        setBenchCompleted(session, done)
        let target = day(forBenchSession: session)
        setDayCompleted(week: target.week, day: target.day, done)
        if done { lastCompletionDate = now }
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

    /// Считаем только текущий цикл: отметки пикирования лежат в своём пространстве имён.
    var completedDayCount: Int {
        let peakKeys = completedDayKeys.filter { $0.hasPrefix("peak-") }
        return Set(isPeaking ? peakKeys : completedDayKeys.filter { !$0.hasPrefix("peak-") }).count
    }
}
