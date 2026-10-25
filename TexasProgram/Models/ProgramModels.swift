import Foundation
import SwiftData

enum TrainingProgramKind: String, CaseIterable, Codable, Identifiable, Sendable {
    case texas = "Техасский метод"
    case upperLower = "Верх / Низ"
    case fullBody = "Фулбади"
    case proTexas = "Продвинутый техас"
    case euthanasia = "Эвтаназия худобы"
    case custom = "Своя программа"

    var id: String { rawValue }

    /// Готовые программы предлагаются при первом запуске, своя собирается отдельно.
    static var ready: [TrainingProgramKind] { [.texas, .upperLower, .fullBody, .proTexas, .euthanasia] }

    var subtitle: String {
        switch self {
        case .texas: return "12 недель · 3 тренировки в неделю · расчёт по 5ПМ"
        case .upperLower: return "7 недель · 4 тренировки в неделю · расчёт по 1ПМ"
        case .fullBody: return "12 недель · 3 тренировки в неделю · всё тело каждый раз"
        case .proTexas: return "20 недель · 10 двухнедельных блоков · расчёт по 5ПМ"
        case .euthanasia: return "8 недель · 3 тренировки · прогрессия по плотности"
        case .custom: return "Своя схема, свои упражнения, свои недели"
        }
    }

    /// От какого максимума считается программа.
    var usesFiveRepMax: Bool { self == .texas || self == .fullBody || self == .proTexas }

    /// Идёт ли по программе волна «Жим 14».
    var hasBenchWave: Bool { self == .upperLower }

    /// Код в файле копии. Совпадает с Android-версией, поэтому строковый,
    /// а не порядковый: перестановка вариантов не должна ломать старые файлы.
    var backupCode: String {
        switch self {
        case .texas: return "TEXAS"
        case .upperLower: return "UPPER_LOWER"
        case .fullBody: return "FULL_BODY"
        case .proTexas: return "PRO_TEXAS"
        case .euthanasia: return "EUTHANASIA"
        case .custom: return "CUSTOM"
        }
    }

    init(backupCode: String) {
        switch backupCode {
        case "UPPER_LOWER": self = .upperLower
        case "FULL_BODY": self = .fullBody
        case "PRO_TEXAS": self = .proTexas
        case "EUTHANASIA": self = .euthanasia
        case "CUSTOM": self = .custom
        default: self = .texas
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
    var durationSeconds: Int? = nil
    var effort: String? = nil

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

    /// Число повторов из плана. У диапазона «8-10» берём нижнюю границу:
    /// записывать за человека верхнюю — приписывать ему работу, которой не было.
    var plannedReps: Int? {
        let digits = reps.split(whereSeparator: { !$0.isNumber })
        return digits.first.flatMap { Int($0) }
    }

    /// Верх диапазона повторов: из «8–12» это 12, из «5» — 5.
    /// Именно его надо закрыть во всех подходах, чтобы прибавить вес.
    var topReps: Int? {
        reps.split(whereSeparator: { !$0.isNumber }).compactMap { Int($0) }.max()
    }

    /// Упражнение без назначенного веса — подсобка, которую программа не считает.
    var needsOwnWeight: Bool {
        switch load {
        case .kilograms, .testOneRepMax: return false
        case .rpe, .repRange: return true
        }
    }
}

/// Подсказка по рабочему весу подсобки.
struct AccessoryHint: Equatable, Sendable {
    /// Шаг прибавки. Два блина по 1,25 — самая мелкая пара, которая есть
    /// в обычном зале; меньше прибавить всё равно нечем.
    static let step: Double = 2.5

    /// Нет веса — упражнение делается впервые, число брать неоткуда.
    let weight: Double?
    /// Вес вырос по сравнению с прошлым разом.
    let isStepUp: Bool
    let text: String
    /// Застой: три тренировки на одном весе без роста — предлагаем сбросить.
    var isDeload: Bool = false
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
    /// Пропущенные тренировки.
    var skipped: [SkippedWorkout] = []
    /// Не гасить экран, пока открыта тренировка.
    var keepScreenOn: Bool = true
    /// Какой проход программы идёт. Первый цикл ключи не меняет.
    var cycleNumber: Int = 1
    /// Своя программа, если выбран этот вид. У остальных не используется.
    var customProgram: CustomProgram?
    /// Стаж для фулбади: от него зависит схема прогрессии.
    var fullBodyLevelRaw: String = FullBodyLevel.aboutYear.rawValue
    /// Вход «Эвтаназии»: три движения с тестами. У остальных программ не используется.
    var euthanasiaInput: EuthanasiaInput?
    /// Подстраивать ли веса под то, как прошёл тяжёлый день.
    /// По умолчанию выключено: классический техас линеен по замыслу.
    var autoregulationEnabled: Bool = false
    /// Оценки тяжёлых дней по движениям.
    var liftReports: [LiftReport] = []
    /// Заметка к тренировке: ключ дня плана.
    var workoutNotes: [String: String] = [:]
    /// Замеры веса тела, по одному на дату.
    var bodyWeightLog: [BodyWeightEntry] = []
    /// Опыт за места в законченных челленджах: один раз за каждый челлендж.
    var challengeAwards: [ChallengeAward] = []
    /// Вести ли рабочий вес подсобки по записям прошлых тренировок.
    var accessoryProgressionEnabled: Bool = true
    /// Шаг прибавки по упражнениям: ключ — нормализованное название.
    /// Нет записи — обычные 2,5 кг. Нужен там, где мелких блинов нет
    /// (жим лёжа, тренажёр с плитками по 5 кг) или, наоборот, шаг мельче.
    var weightSteps: [String: Double] = [:]
    /// Шаг для всех упражнений без своего: зависит от самых мелких блинов в зале.
    /// По умолчанию 2,5 кг — пара блинов по 1,25.
    var defaultWeightStep: Double = AccessoryHint.step
    /// Застой, который человек решил не сбрасывать: упражнение → вес.
    /// На другом весе предложение вернётся.
    var stallDismissed: [String: Double] = [:]
    /// Напоминание о тренировке в дни расписания.
    var reminderEnabled: Bool = false
    var reminderHour: Int = 18
    var reminderMinute: Int = 0
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

    /// Фулбади считается от 5ПМ, как и техас: прогрессия там та же линейная.
    convenience init(fullBodyInput: ProgramInput, level: FullBodyLevel, name: String = "Профиль") {
        self.init(input: fullBodyInput, name: name)
        programKindRaw = TrainingProgramKind.fullBody.rawValue
        fullBodyLevelRaw = level.rawValue
    }

    /// Продвинутый техас считается от того же 5ПМ, что и классический.
    convenience init(proTexasInput: ProgramInput, name: String = "Профиль") {
        self.init(input: proTexasInput, name: name)
        programKindRaw = TrainingProgramKind.proTexas.rawValue
    }

    /// «Эвтаназия»: максимумы каждого движения лежат внутри её собственного входа.
    convenience init(euthanasia: EuthanasiaInput, name: String = "Профиль") {
        self.init(input: .demo, name: name)
        programKindRaw = TrainingProgramKind.euthanasia.rawValue
        euthanasiaInput = euthanasia
        squat5RM = euthanasia.squat.oneRepMax
        bench5RM = euthanasia.press.oneRepMax
        deadlift5RM = euthanasia.pull.oneRepMax
    }

    /// Своя программа: максимумы ей не нужны, веса заданы прямо в упражнениях.
    convenience init(customProgram program: CustomProgram, name: String = "Профиль") {
        self.init(input: .demo, name: program.name.isEmpty ? name : program.name)
        programKindRaw = TrainingProgramKind.custom.rawValue
        customProgram = program
    }

    var programKind: TrainingProgramKind {
        get { TrainingProgramKind(rawValue: programKindRaw) ?? .texas }
        set { programKindRaw = newValue.rawValue }
    }

    var level: TrainingLevel {
        get { TrainingLevel(rawValue: levelRaw) ?? .beginner }
        set { levelRaw = newValue.rawValue }
    }

    var fullBodyLevel: FullBodyLevel {
        get { FullBodyLevel(rawValue: fullBodyLevelRaw) ?? .aboutYear }
        set { fullBodyLevelRaw = newValue.rawValue }
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
    /// Порядок обязателен: сначала задержка подменяет неделю расчёта, потом
    /// правки подставляют свои упражнения. Наоборот задержка стёрла бы то,
    /// что пользователь вписал руками.
    ///
    /// Расчёт всех недель дорогой, а план читают десятки раз за одну
    /// перерисовку: медали, история упражнений, расписание. Поэтому готовый
    /// план берём из кэша, пока не поменялось ничего из того, от чего он зависит.
    var workoutPlan: WorkoutPlan {
        PlanCache.plan(for: planInputs, kind: .full) {
            applySteps(to: applyEdits(to: applyAutoregulation(to: applyHolds(to: generatedPlan))))
        }
    }

    /// Всё, от чего зависит план. Сравниваются сами значения, а не хэш:
    /// устаревший план из кэша невозможен.
    fileprivate var planInputs: PlanInputs {
        PlanInputs(
            kind: programKindRaw,
            peaking: isPeaking,
            input: input,
            peakingInput: peakingInput,
            fullBodyLevel: fullBodyLevelRaw,
            euthanasia: euthanasiaInput,
            custom: customProgram,
            edits: planEdits,
            skipped: skipped,
            reports: liftReports,
            autoregulation: autoregulationEnabled,
            cycle: cycleNumber,
            steps: weightSteps,
            defaultStep: defaultWeightStep
        )
    }

    /// Чистый расчёт без правок — нужен экрану настройки, чтобы показать,
    /// что именно программа предлагала до вмешательства.
    var generatedPlan: WorkoutPlan {
        PlanCache.plan(for: planInputs, kind: .generated) { calculatedPlan }
    }

    private var calculatedPlan: WorkoutPlan {
        switch programKind {
        case .texas:
            return isPeaking
                ? ProgramCalculator.generatePeaking(input: peakingInput)
                : ProgramCalculator.generate(input: input)
        case .upperLower:
            return UpperLowerCalculator.generate(input: upperLowerInput)
        case .fullBody:
            return FullBodyCalculator.generate(input: input, level: fullBodyLevel)
        case .proTexas:
            return ProTexasCalculator.generate(input: input)
        case .euthanasia:
            return euthanasiaInput.map(EuthanasiaCalculator.generate) ?? WorkoutPlan(weeks: [], isPeaking: false)
        case .custom:
            return customProgram?.plan ?? WorkoutPlan(weeks: [], isPeaking: false)
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

    // MARK: - Новый цикл

    /// Начинает программу заново с новыми максимумами.
    ///
    /// Отметки не стираются: они остаются под префиксом прошлого цикла и просто
    /// перестают быть видимыми. История и графики от этого сквозные — видно, как
    /// ты рос из цикла в цикл, а не только внутри одного.
    ///
    /// Максимумы приходят снаружи: основная программа считается от свежего
    /// пятиповторного теста, а не от пересчёта прошлого результата.
    func startNewCycle(squat: Double, bench: Double, deadlift: Double, now: Date = Date()) {
        cycleNumber += 1
        peakingActive = false
        peakSquat5RM = nil
        peakBench5RM = nil
        peakDeadlift5RM = nil
        squat5RM = squat
        bench5RM = bench
        deadlift5RM = deadlift
        // Волна жима считается от нуля: её номера не привязаны к пространству имён.
        completedBenchSessions = []
        lastCompletionDate = nil
        cycleStartedAt = now
        scheduleAnchorWeek = 0
        scheduleAnchorDate = nil
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

    // MARK: - Серия и цель недели по календарю

    struct CalendarStreak: Equatable {
        let weeks: Int
        let workouts: Int
    }

    /// Серия как у REBRN: неделя (пн–вс) засчитана, если в ней была хотя бы одна
    /// тренировка. Начатая неделя серию не обрывает — до воскресенья есть время,
    /// поэтому пустую текущую неделю пропускаем и считаем от прошлой.
    func calendarStreak(now: Date = Date(), calendar base: Calendar = .current) -> CalendarStreak {
        let calendar = WorkoutScheduler.trainingCalendar(base)
        var perWeek: [Date: Int] = [:]
        for record in completionLog {
            guard let start = calendar.dateInterval(of: .weekOfYear, for: record.date)?.start else { continue }
            perWeek[start, default: 0] += 1
        }
        guard var cursor = calendar.dateInterval(of: .weekOfYear, for: now)?.start else {
            return CalendarStreak(weeks: 0, workouts: 0)
        }
        if perWeek[cursor] == nil, let previous = calendar.date(byAdding: .weekOfYear, value: -1, to: cursor) {
            cursor = previous
        }
        var weeks = 0
        var workouts = 0
        while let count = perWeek[cursor] {
            weeks += 1
            workouts += count
            guard let previous = calendar.date(byAdding: .weekOfYear, value: -1, to: cursor) else { break }
            cursor = previous
        }
        return CalendarStreak(weeks: weeks, workouts: workouts)
    }

    /// Цель недели: сколько тренировок сделано на этой календарной неделе
    /// и сколько задумано — по числу дней в расписании. С понедельника снова с нуля.
    func weekGoal(now: Date = Date(), calendar base: Calendar = .current) -> (done: Int, target: Int) {
        let calendar = WorkoutScheduler.trainingCalendar(base)
        let target = max(weekdays.count, 1)
        guard let week = calendar.dateInterval(of: .weekOfYear, for: now) else { return (0, target) }
        return (completionLog.filter { week.contains($0.date) }.count, target)
    }

    // MARK: - Авторегуляция

    /// Умеет ли программа подстраиваться. Механизм завязан на тяжёлый день,
    /// поэтому работает там, где он есть: у обоих техасов.
    var supportsAutoregulation: Bool {
        programKind == .texas || programKind == .proTexas
    }

    private var autoregulationActive: Bool { autoregulationEnabled && supportsAutoregulation }

    /// Ключ оценки: то же пространство имён, что у отметок, плюс номер недели.
    func reportKey(week: Int) -> String { keyPrefix + "\(week)" }

    func report(lift: MainLift, week: Int) -> LiftReport? {
        let key = reportKey(week: week)
        return liftReports.first { $0.key == key && $0.lift == lift }
    }

    /// Оценки недели по всем движениям.
    func reports(week: Int) -> [LiftReport] {
        let key = reportKey(week: week)
        return liftReports.filter { $0.key == key }
    }

    func setReport(lift: MainLift, week: Int, outcome: LiftOutcome, now: Date = Date()) {
        let key = reportKey(week: week)
        liftReports.removeAll { $0.key == key && $0.lift == lift }
        liftReports.append(LiftReport(key: key, lift: lift, outcome: outcome, date: now))
    }

    func clearReport(lift: MainLift, week: Int) {
        let key = reportKey(week: week)
        liftReports.removeAll { $0.key == key && $0.lift == lift }
    }

    /// Две неудачи подряд по движению — линейный рост кончился, пора сбрасывать цикл.
    func isStalled(_ lift: MainLift, upTo week: Int) -> Bool {
        let recent = (1..<max(week, 1)).compactMap { report(lift: lift, week: $0) }
        guard recent.count >= 2 else { return false }
        return recent.suffix(2).allSatisfy(\.outcome.isFailure)
    }

    var stalledLifts: [MainLift] {
        guard autoregulationActive else { return [] }
        let weeks = generatedPlan.weeks.count + 1
        return MainLift.allCases.filter { isStalled($0, upTo: weeks) }
    }

    /// Вес тяжёлого дня, каким его запланировал расчёт.
    private func plannedIntensity(_ lift: MainLift, week: WorkoutWeekPlan) -> Double? {
        guard let day = week.days.last else { return nil }
        guard let exercise = day.exercises.first(where: { $0.name == lift.rawValue }),
              case .kilograms(let value) = exercise.load else { return nil }
        return value
    }

    /// Вес тяжёлого дня с учётом того, как прошли прошлые недели.
    ///
    /// Недели без оценки идут с обычной прибавкой — поэтому включённый
    /// переключатель сам по себе план не меняет, пока не начнёшь отвечать.
    func autoregulatedIntensity(_ lift: MainLift, week: Int, in plan: WorkoutPlan) -> Double? {
        guard let first = plan.weeks.first, var value = plannedIntensity(lift, week: first) else { return nil }
        for earlier in 1..<max(week, 1) {
            if let outcome = report(lift: lift, week: earlier)?.outcome {
                value = outcome.next(after: value, isUpper: lift.isUpper)
            } else {
                value += 2.5
            }
        }
        return ProgramCalculator.roundToPlate(value)
    }

    /// Масштабирует веса недели так, чтобы тяжёлый день попал в нужное число.
    ///
    /// Остальные дни считаются процентами от тяжёлого, поэтому достаточно
    /// умножить их на то же отношение — арифметика остаётся в калькуляторе.
    private func applyAutoregulation(to plan: WorkoutPlan) -> WorkoutPlan {
        guard autoregulationActive, !liftReports.isEmpty else { return plan }
        return WorkoutPlan(weeks: plan.weeks.map { week in
            var factors: [String: Double] = [:]
            for lift in MainLift.allCases {
                guard let planned = plannedIntensity(lift, week: week), planned > 0,
                      let target = autoregulatedIntensity(lift, week: week.number, in: plan),
                      target != planned else { continue }
                factors[lift.rawValue] = target / planned
            }
            guard !factors.isEmpty else { return week }
            return WorkoutWeekPlan(id: week.id, number: week.number, days: week.days.map { day in
                WorkoutDayPlan(id: day.id, number: day.number, title: day.title, exercises: day.exercises.map { exercise in
                    guard let factor = factors[exercise.name],
                          case .kilograms(let value) = exercise.load else { return exercise }
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

    // MARK: - Заметки

    func note(week: Int, day: Int) -> String {
        workoutNotes[dayKey(week: week, day: day)] ?? ""
    }

    func setNote(_ text: String, week: Int, day: Int) {
        let key = dayKey(week: week, day: day)
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        workoutNotes[key] = trimmed.isEmpty ? nil : trimmed
    }

    /// Неделя, на которой человек сейчас: первая, где остались незакрытые дни.
    ///
    /// Считаем по отметкам, а не по календарю. Пропущенная пятница не должна
    /// перекидывать на следующую неделю только потому, что её день прошёл, —
    /// незакрытая неделя остаётся текущей, пока в ней есть что делать.
    /// Отмеченные пропуски в счёт не идут: их закрыли сознательно.
    var currentWeek: Int {
        let maxCompleted = workoutPlan.weeks
            .filter { week in week.days.contains { isCompleted(week: week.number, day: $0.number) } }
            .map(\.number)
            .max() ?? 1
        let open = workoutPlan.weeks.first { week in
            week.days.contains { day in
                !isCompleted(week: week.number, day: day.number) && !isSkipped(week: week.number, day: day.number)
            }
        }
        let openWeek = open?.number ?? workoutPlan.weeks.last?.number ?? 1
        return max(openWeek, maxCompleted)
    }

    // MARK: - Пропущенные тренировки

    func isSkipped(week: Int, day: Int) -> Bool {
        skipped.contains { $0.week == week && $0.day == day && $0.isPeaking == isPeaking }
    }

    /// Сколько недель прогрессия стоит на месте к началу указанной недели.
    ///
    /// Считаем пропуски строго раньше этой недели: неделя, которую пропустили,
    /// должна остаться со своими весами, а замереть должна следующая.
    func holdCount(before week: Int) -> Int {
        Set(skipped.filter { $0.isPeaking == isPeaking && $0.holdsProgression && $0.week < week }
            .map(\.week)).count
    }

    /// Какая неделя расчёта достаётся календарной неделе.
    func effectiveWeek(for week: Int) -> Int {
        max(week - holdCount(before: week), 1)
    }

    /// Задержка прогрессии: неделя берёт веса более ранней.
    ///
    /// Веса не пересчитываем формулой — подставляем упражнения нужной недели
    /// целиком. Так арифметика остаётся в одном месте, в самом калькуляторе.
    private func applyHolds(to plan: WorkoutPlan) -> WorkoutPlan {
        guard skipped.contains(where: { $0.isPeaking == isPeaking && $0.holdsProgression }) else { return plan }
        let byNumber = Dictionary(uniqueKeysWithValues: plan.weeks.map { ($0.number, $0) })
        return WorkoutPlan(weeks: plan.weeks.map { week in
            let source = effectiveWeek(for: week.number)
            guard source != week.number, let donor = byNumber[source] else { return week }
            // Номер и идентификаторы дней остаются своими: по ним считаются отметки.
            return WorkoutWeekPlan(id: week.id, number: week.number, days: zip(week.days, donor.days).map { own, borrowed in
                WorkoutDayPlan(id: own.id, number: own.number, title: own.title, exercises: borrowed.exercises)
            })
        }, isPeaking: plan.isPeaking)
    }

    /// «Не пришёл на тренировку». Пропуск не отмечает день выполненным —
    /// тренировка остаётся в расписании, её ещё можно закрыть.
    func markSkipped(week: Int, day: Int, holdsProgression: Bool, now: Date = Date()) {
        guard !isSkipped(week: week, day: day) else { return }
        skipped.append(SkippedWorkout(
            week: week, day: day, isPeaking: isPeaking, date: now, holdsProgression: holdsProgression
        ))
    }

    func removeSkip(_ item: SkippedWorkout) {
        skipped.removeAll { $0.id == item.id }
    }

    /// Пропуски текущего пространства имён — их показывают настройки.
    var activeSkips: [SkippedWorkout] {
        skipped.filter { $0.isPeaking == isPeaking }.sorted { ($0.week, $0.day) < ($1.week, $1.day) }
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

    /// Сколько подходов сверх плана ищем в записях. В тренировке можно
    /// добавить подход кнопкой — без этого запаса он записывался, но в историю,
    /// графики и выгрузку не попадал.
    static let extraSetAllowance = 12

    /// Записанные подходы упражнения по порядку номеров, включая добавленные сверх плана.
    func entries(week: Int, day: Int, exercise: ExercisePrescription) -> [(index: Int, entry: SetEntry)] {
        (0..<(max(exercise.sets, 0) + Self.extraSetAllowance)).compactMap { index in
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

    /// Тоннаж дня по записанным подходам: ключ дня из истории отметок.
    func tonnage(forDayKey key: String) -> Double {
        let prefix = key + "|"
        return setLog.reduce(0) { sum, item in
            item.key.hasPrefix(prefix) ? sum + item.value.tonnage : sum
        }
    }

    // MARK: - История упражнения

    /// Названия упражнений, по которым есть хоть один записанный подход.
    ///
    /// Берём из плана, а не из ключей: в ключе лежит идентификатор, а показать
    /// нужно человеческое имя, и порядок должен совпадать с порядком в программе.
    var loggedExerciseNames: [String] {
        var names: [String] = []
        for week in workoutPlan.weeks {
            for day in week.days {
                for exercise in day.exercises where !names.contains(exercise.name) {
                    if !entries(week: week.number, day: day.number, exercise: exercise).isEmpty {
                        names.append(exercise.name)
                    }
                }
            }
        }
        return names
    }

    /// Весь путь одного движения по текущему циклу.
    ///
    /// Ключ отметки завязан на идентификатор упражнения, а он совпадает с именем,
    /// поэтому замена упражнения в одном дне не смешивает историю разных движений.
    func history(forExerciseNamed name: String) -> [ExerciseHistoryPoint] {
        var points: [ExerciseHistoryPoint] = []
        for week in workoutPlan.weeks {
            for day in week.days {
                for exercise in day.exercises where exercise.name == name {
                    let logged = entries(week: week.number, day: day.number, exercise: exercise).map(\.entry)
                    guard !logged.isEmpty else { continue }
                    var planned: Double?
                    if case .kilograms(let value) = exercise.load { planned = value }
                    points.append(ExerciseHistoryPoint(week: week.number, day: day.number, planned: planned, entries: logged))
                }
            }
        }
        return points
    }

    // MARK: - Вес тела

    var bodyWeightSeries: [BodyWeightEntry] {
        bodyWeightLog.sorted { $0.date < $1.date }
    }

    var latestBodyWeight: Double? { bodyWeightSeries.last?.weight }

    /// Записывает замер. День — единица измерения: второй замер за сутки заменяет первый.
    func recordBodyWeight(_ value: Double, on date: Date = Date(), calendar: Calendar = .current) {
        let day = calendar.startOfDay(for: date)
        bodyWeightLog.removeAll { calendar.isDate($0.date, inSameDayAs: day) }
        guard value > 0 else { return }
        bodyWeightLog.append(BodyWeightEntry(date: day, weight: value))
    }

    func removeBodyWeight(_ entry: BodyWeightEntry) {
        bodyWeightLog.removeAll { $0.date == entry.date }
    }

    /// Отношение максимума к своему весу: на сушке штанга стоит,
    /// а это число растёт — и видно, что работа не впустую.
    func relativeStrength(_ maximum: Double) -> Double? {
        guard let weight = latestBodyWeight, weight > 0 else { return nil }
        return maximum / weight
    }

    // MARK: - Закрыть день как по плану

    /// Записывает все подходы дня плановыми весами.
    ///
    /// Для упражнений без конкретного веса (РПЕ, диапазон повторов, тест)
    /// просто закрываем точки: придумывать за человека числа неправильно.
    /// Упражнения дня ровно в том виде, в каком они были на экране:
    /// с правками, задержками и жимом из волны.
    func plannedExercises(week: Int, day: Int) -> [ExercisePrescription] {
        guard let plan = workoutPlan.weeks.first(where: { $0.number == week })?
            .days.first(where: { $0.number == day }) else { return [] }
        return exercises(day: plan, benchSession: benchSession(week: week, day: day))
    }

    func completeAsPlanned(week: Int, day: Int, now: Date = Date()) {
        for exercise in plannedExercises(week: week, day: day) {
            guard exercise.sets > 0 else { continue }
            // У подсобки своего веса в плане нет — берём тот, что ведёт прогрессия.
            var weight: Double?
            if case .kilograms(let value) = exercise.load { weight = value }
            else { weight = accessoryHint(for: exercise, week: week, day: day)?.weight }

            if let weight, let reps = exercise.plannedReps {
                for index in 0..<exercise.sets {
                    recordSet(week: week, day: day, exercise: exercise, index: index, reps: reps, weight: weight, now: now)
                }
            } else {
                setProgress[setKey(week: week, day: day, exercise: exercise)] = exercise.sets
            }
        }

        if !isCompleted(week: week, day: day) {
            toggleCompleted(week: week, day: day, now: now)
        }
    }

    // MARK: - Шаг прибавки

    /// Варианты шага: от тренажёров с шагом 1 кг до плиток по 10 кг.
    static let weightStepOptions: [Double] = [1, 1.25, 2.5, 5, 10]

    /// Ключ упражнения для шага: одно и то же движение во всех днях и неделях.
    static func weightStepKey(_ name: String) -> String {
        name.lowercased()
            .replacingOccurrences(of: "ё", with: "е")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Подпись варианта: что это значит в зале.
    static func weightStepTitle(_ step: Double) -> String {
        switch step {
        case 1: return "1 кг — тренажёр"
        case 1.25: return "1,25 кг"
        case 2.5: return "2,5 кг — есть блины 1,25"
        case 5: return "5 кг — мельче 2,5 нет"
        case 10: return "10 кг — плитки по 10"
        default: return WeightFormat.kilogramsPrecise(step)
        }
    }

    static func weightStepHint(_ step: Double) -> String {
        switch step {
        case 5: return "Самые мелкие блины — 2,5 кг: вес растёт на 5 кг, а веса программы округляются до того, что можно повесить."
        case 2.5: return "Есть блины по 1,25 кг: вес растёт на 2,5 кг — обычная прибавка."
        default: return "Вес растёт на \(WeightFormat.kilogramsPrecise(step)), веса программы округляются под этот шаг."
        }
    }

    func weightStep(for name: String) -> Double {
        weightSteps[Self.weightStepKey(name)] ?? defaultWeightStep
    }

    /// Свой ли шаг у упражнения — меню показывает «как в зале» или выбранный.
    func hasOwnWeightStep(for name: String) -> Bool {
        weightSteps[Self.weightStepKey(name)] != nil
    }

    func setWeightStep(_ step: Double, for name: String) {
        let key = Self.weightStepKey(name)
        // Шаг, совпадающий с общим, не храним: поменяешь общий — упражнение пойдёт за ним.
        if step == defaultWeightStep || step <= 0 {
            weightSteps.removeValue(forKey: key)
        } else {
            weightSteps[key] = step
        }
    }

    /// Округляет вес до того, что реально можно повесить при этом шаге.
    /// Ровно посередине — вниз: лишний вес на штанге хуже недостающего.
    static func roundWeight(_ value: Double, step: Double) -> Double {
        guard value.isFinite, step > 0 else { return value }
        let units = value / step
        let lower = units.rounded(.down)
        let rounded = units - lower > 0.5 + 1e-9 ? lower + 1 : lower
        return (rounded * step * 100).rounded() / 100
    }

    /// Веса программы для упражнений со своим шагом: 82,5 в жиме при шаге 5 кг
    /// не повесить, поэтому план показывает 80.
    private func applySteps(to plan: WorkoutPlan) -> WorkoutPlan {
        // Обычный зал и ни одного своего шага — план ровно тот, что посчитала программа.
        guard !weightSteps.isEmpty || defaultWeightStep != AccessoryHint.step else { return plan }
        return WorkoutPlan(weeks: plan.weeks.map { week in
            WorkoutWeekPlan(id: week.id, number: week.number, days: week.days.map { day in
                WorkoutDayPlan(id: day.id, number: day.number, title: day.title, exercises: day.exercises.map { exercise in
                    guard case .kilograms(let value) = exercise.load else { return exercise }
                    let step = weightStep(for: exercise.name)
                    // Программа считает с шагом 2,5 — такие веса и так вешаются.
                    guard step != AccessoryHint.step else { return exercise }
                    let rounded = Self.roundWeight(value, step: step)
                    guard rounded != value else { return exercise }
                    return ExercisePrescription(
                        id: exercise.id,
                        name: exercise.name,
                        sets: exercise.sets,
                        reps: exercise.reps,
                        load: .kilograms(rounded),
                        isOptional: exercise.isOptional,
                        benchSession: exercise.benchSession
                    )
                })
            })
        }, isPeaking: plan.isPeaking)
    }

    // MARK: - Прогрессия подсобки

    /// Что делать с весом подсобки на этой тренировке.
    ///
    /// Двойная прогрессия: сначала растут повторы внутри диапазона, и только
    /// когда верх диапазона закрыт во всех подходах — растёт вес. Так подсобка
    /// не встаёт на одном весе годами и не скачет через силу.
    func accessoryHint(for exercise: ExercisePrescription, week: Int, day: Int) -> AccessoryHint? {
        guard accessoryProgressionEnabled, exercise.needsOwnWeight, let top = exercise.topReps else { return nil }

        // Прошлые разы этого движения — строго раньше текущего дня.
        let previous = history(forExerciseNamed: exercise.name)
            .filter { ($0.week, $0.day) < (week, day) }

        guard let last = previous.last, let working = last.entries.map(\.weight).max(), working > 0 else {
            return AccessoryHint(
                weight: nil,
                isStepUp: false,
                text: "Запиши вес, с которым \(top) повторений идут впритык — дальше прибавку ведёт приложение."
            )
        }

        // Верх диапазона должен быть закрыт во всех подходах, а не в одном.
        let allSets = last.entries.count >= exercise.sets
        let allTop = last.entries.allSatisfy { $0.reps >= top }

        if allSets && allTop {
            // Шаг — свой у упражнения: на блоке 2,5, на жиме без мелких блинов 5.
            // От рабочего веса, без округления к сетке: он уже реально висел на штанге.
            let step = weightStep(for: exercise.name)
            let next = ((working + step) * 100).rounded() / 100
            return AccessoryHint(
                weight: next,
                isStepUp: next > working,
                text: "В прошлый раз \(top) во всех подходах — прибавь до \(WeightFormat.kilogramsPrecise(next))."
            )
        }

        if let deload = stallDeload(for: exercise, top: top, previous: previous, working: working) {
            return AccessoryHint(
                weight: deload,
                isStepUp: false,
                text: "Три тренировки подряд на \(WeightFormat.kilogramsPrecise(working)) без роста. Сбрось до \(WeightFormat.kilogramsPrecise(deload)) и набери \(top) заново — так вес снова пойдёт вверх.",
                isDeload: true
            )
        }

        return AccessoryHint(
            weight: working,
            isStepUp: false,
            text: "Держи \(WeightFormat.kilogramsPrecise(working)) и добери до \(top) в каждом подходе — тогда вес вырастет."
        )
    }

    /// Сколько тренировок подряд на одном весе без роста считаем застоем.
    static let stallSessions = 3

    /// Вес разгрузки, если движение встало: последние три раза один и тот же
    /// рабочий вес, ни разу не закрыт верх диапазона и повторов не прибавилось. Минус 10% под шаг —
    /// достаточно, чтобы снова пойти вверх, и не так много, чтобы откатиться.
    private func stallDeload(for exercise: ExercisePrescription, top: Int, previous: [ExerciseHistoryPoint], working: Double) -> Double? {
        let key = Self.weightStepKey(exercise.name)
        if stallDismissed[key] == working { return nil }
        let recent = previous.filter { !$0.entries.isEmpty }.suffix(Self.stallSessions)
        guard recent.count == Self.stallSessions else { return nil }
        let stuck = recent.allSatisfy { point in
            point.entries.map(\.weight).max() == working
                && !(point.entries.count >= exercise.sets && point.entries.allSatisfy { $0.reps >= top })
        }
        // Повторы растут на том же весе — двойная прогрессия идёт, это не застой.
        guard stuck, let first = recent.first, let last = recent.last, last.totalReps <= first.totalReps else { return nil }
        let step = weightStep(for: exercise.name)
        var deload = Self.roundWeight(working * 0.9, step: step)
        if deload >= working { deload = working - step }
        return deload > 0 ? deload : nil
    }

    /// «Не сбрасывать»: застой на этом весе больше не предлагаем.
    func dismissStall(for name: String, weight: Double) {
        stallDismissed[Self.weightStepKey(name)] = weight
    }

    // MARK: - История по старым отметкам

    /// Сколько уже отмеченных тренировок можно достроить до записей.
    ///
    /// Отметка говорит «подход сделан», но не говорит с каким весом. Раз вес
    /// был плановым — а отмечают обычно именно так, — эти записи можно
    /// восстановить, и история перестанет начинаться с сегодняшнего дня.
    func backfillTargets() -> [(week: Int, day: Int)] {
        var result: [(week: Int, day: Int)] = []
        for week in workoutPlan.weeks {
            for day in week.days {
                let marked = isCompleted(week: week.number, day: day.number)
                let list = plannedExercises(week: week.number, day: day.number)
                let hasSomething = list.contains { completedSets(week: week.number, day: day.number, exercise: $0) > 0 }
                guard marked || hasSomething else { continue }

                let fillable = list.contains { exercise in
                    guard case .kilograms = exercise.load, exercise.plannedReps != nil else { return false }
                    let done = marked ? exercise.sets : completedSets(week: week.number, day: day.number, exercise: exercise)
                    guard done > 0 else { return false }
                    return entries(week: week.number, day: day.number, exercise: exercise).isEmpty
                }
                if fillable { result.append((week.number, day.number)) }
            }
        }
        return result
    }

    var backfillableWorkouts: Int { backfillTargets().count }

    /// Достраивает записи по отметкам. Возвращает число заполненных тренировок.
    ///
    /// Трогаем только пустое: если по упражнению уже есть хоть один записанный
    /// подход, значит человек вводил настоящие числа, и подменять их плановыми
    /// нельзя. Упражнения без веса в плане (РПЕ, «до отказа») пропускаем —
    /// придумывать за человека килограммы неоткуда.
    @discardableResult
    func backfillHistoryFromMarks(now: Date = Date()) -> Int {
        var filled = 0
        for target in backfillTargets() {
            let marked = isCompleted(week: target.week, day: target.day)
            for exercise in plannedExercises(week: target.week, day: target.day) {
                guard case .kilograms(let weight) = exercise.load, let reps = exercise.plannedReps else { continue }
                guard entries(week: target.week, day: target.day, exercise: exercise).isEmpty else { continue }

                let done = marked ? exercise.sets : completedSets(week: target.week, day: target.day, exercise: exercise)
                guard done > 0 else { continue }
                for index in 0..<done {
                    recordSet(week: target.week, day: target.day, exercise: exercise,
                              index: index, reps: reps, weight: weight, now: now)
                }
            }
            filled += 1
        }
        return filled
    }

    /// Тоннаж по неделям: только записанные подходы, без догадок по плану.
    var weeklyTonnage: [WeeklyTonnage] {
        var totals: [Int: (sum: Double, count: Int)] = [:]
        for (key, entry) in setLog {
            guard let day = SetKeyParts.dayKey(from: key), isOwnKey(day),
                  let week = SetKeyParts.week(fromDayKey: day, prefix: keyPrefix) else { continue }
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

    var maximumLabel: String { programKind.usesFiveRepMax ? "5ПМ" : "1ПМ" }
    var totalDays: Int { workoutPlan.weeks.reduce(0) { $0 + $1.days.count } }

    /// Ключ отметки. У пикового цикла своё пространство имён, иначе его дни
    /// накладывались бы на дни основной программы — недели там нумеруются с единицы.
    func dayKey(week: Int, day: Int) -> String { keyPrefix + "\(week)-\(day)" }

    /// Пространство имён отметок: номер цикла и пиковый режим.
    ///
    /// Первый цикл не добавляет ничего — ключи сохранённых профилей остаются
    /// прежними, миграция не нужна.
    var keyPrefix: String {
        let cycle = cycleNumber > 1 ? "c\(cycleNumber)-" : ""
        return isPeaking ? cycle + "peak-" : cycle
    }

    /// Принадлежит ли ключ текущему пространству.
    ///
    /// Одной проверки префикса мало: у пустого префикса «peak-1-1» тоже
    /// начинается с него, а у «c2-» внутри может лежать пиковый ключ.
    func isOwnKey(_ key: String) -> Bool {
        guard key.hasPrefix(keyPrefix) else { return false }
        let rest = key.dropFirst(keyPrefix.count)
        return !rest.contains("peak-") && !rest.hasPrefix("c")
    }

    func isCompleted(week: Int, day: Int) -> Bool { completedDayKeys.contains(dayKey(week: week, day: day)) }

    /// Отметка дня. Верхний день двигает волну жима на одну тренировку:
    /// какая именно это тренировка, определяет счётчик волны, а не день недели.
    func toggleCompleted(week: Int, day: Int, now: Date = Date(), durationSeconds: Int? = nil, effort: String? = nil) {
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
        // Возместил пропущенное — задержка снимается сама.
        if done { skipped.removeAll { $0.week == week && $0.day == day && $0.isPeaking == isPeaking } }
        if done {
            lastCompletionDate = now
            recordCompletion(week: week, day: day, now: now, durationSeconds: durationSeconds, effort: effort)
            let dur = Double(durationSeconds ?? 3600)
            Task {
                await HealthKitService.shared.saveWorkout(
                    startDate: now.addingTimeInterval(-dur),
                    endDate: now,
                    title: "\(self.name) · Неделя \(week), день \(day)"
                )
            }
        } else {
            let key = dayKey(week: week, day: day)
            completionLog.removeAll { $0.key == key }
        }
    }

    /// Кладёт в историю дату и рабочие веса основных движений этого дня.
    private func recordCompletion(week: Int, day: Int, now: Date, durationSeconds: Int? = nil, effort: String? = nil) {
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
            actualDeadlift: actualWeight(week: week, day: day, exerciseNames: ["Становая тяга"]),
            durationSeconds: durationSeconds,
            effort: effort
        ))
    }

    /// Обновляет запись истории, если день уже отмечен: факт мог появиться позже.
    /// Дату оставляем исходную — тренировка была тогда, а не в момент правки.
    /// Время и усилие уже отмеченной тренировки. День мог закрыться раньше —
    /// отметкой на «Сегодня» или последним подходом, — а итог пришёл с экрана
    /// завершения; без этого он терялся, и в истории не было ни времени, ни усилия.
    func setWorkoutSummary(week: Int, day: Int, durationSeconds: Int?, effort: String?) {
        let key = dayKey(week: week, day: day)
        guard let index = completionLog.firstIndex(where: { $0.key == key }) else { return }
        if let durationSeconds { completionLog[index].durationSeconds = durationSeconds }
        if let effort { completionLog[index].effort = effort }
    }

    private func refreshCompletion(week: Int, day: Int, now: Date) {
        guard isCompleted(week: week, day: day) else { return }
        let key = dayKey(week: week, day: day)
        let original = completionLog.first { $0.key == key }?.date ?? now
        let prevDur = completionLog.first { $0.key == key }?.durationSeconds
        let prevEffort = completionLog.first { $0.key == key }?.effort
        recordCompletion(week: week, day: day, now: original, durationSeconds: prevDur, effort: prevEffort)
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

    /// Обновляет количество выполненных подходов в упражнении.
    func updateCompletedSets(week: Int, day: Int, exercise: ExercisePrescription, count: Int) {
        let key = setKey(week: week, day: day, exercise: exercise)
        if count <= 0 {
            setProgress[key] = nil
        } else {
            setProgress[key] = count
        }
    }

    /// Слепок отметок одного дня: им отмена тренировки возвращает всё как было.
    struct DaySnapshot: Codable {
        let progress: [String: Int]
        let log: [String: SetEntry]
    }

    func snapshotDay(week: Int, day: Int) -> DaySnapshot {
        let prefix = dayKey(week: week, day: day) + "|"
        return DaySnapshot(
            progress: setProgress.filter { $0.key.hasPrefix(prefix) },
            log: setLog.filter { $0.key.hasPrefix(prefix) }
        )
    }

    /// Возвращает отметки дня к слепку. Выполненность дня и история не трогаются:
    /// тренировка, закрытая до начала сессии, после отмены сессии остаётся закрытой.
    func restoreDay(_ snapshot: DaySnapshot, week: Int, day: Int, now: Date = Date()) {
        let prefix = dayKey(week: week, day: day) + "|"
        // Собираем словари целиком и присваиваем один раз: каждое присваивание
        // свойства модели — это запись в хранилище.
        setProgress = setProgress.filter { !$0.key.hasPrefix(prefix) }
            .merging(snapshot.progress) { _, restored in restored }
        setLog = setLog.filter { !$0.key.hasPrefix(prefix) }
            .merging(snapshot.log) { _, restored in restored }
        refreshCompletion(week: week, day: day, now: now)
    }

    /// Полный сброс подходов и записей дня (при отмене/удалении тренировки).
    func resetDaySets(week: Int, day: Int) {
        let prefix = dayKey(week: week, day: day) + "|"
        let progressKeys = setProgress.keys.filter { $0.hasPrefix(prefix) }
        for k in progressKeys {
            setProgress[k] = nil
        }
        let logKeys = setLog.keys.filter { $0.hasPrefix(prefix) }
        for k in logKeys {
            setLog[k] = nil
        }
        let dKey = dayKey(week: week, day: day)
        completionLog.removeAll { $0.key == dKey }
        setDayCompleted(week: week, day: day, false)
    }

    /// Дни, которые несут волну жима: в «Верх / Низ» это верхние дни — первый и третий.
    func carriesBenchWave(day: Int) -> Bool {
        programKind.hasBenchWave && (day == 1 || day == 3)
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

    var trainingDayCount: Int {
        switch programKind {
        case .texas, .fullBody, .proTexas, .euthanasia: return 3
        case .upperLower: return 4
        case .custom: return max(customProgram?.days.count ?? 3, 1)
        }
    }

    /// «Верх / Низ» — понедельник, вторник, четверг, пятница. Техас — понедельник, среда, пятница.
    var defaultWeekdays: [Int] {
        switch trainingDayCount {
        case 1: return [2]
        case 2: return [2, 5]
        case 3: return [2, 4, 6]
        case 4: return [2, 3, 5, 6]
        case 5: return [2, 3, 4, 5, 6]
        case 6: return [2, 3, 4, 5, 6, 7]
        default: return Array(RuDate.weekOrder.prefix(max(trainingDayCount, 1)))
        }
    }

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
        completedDayKeys = completedDayKeys.filter { !isOwnKey($0) }
        // История отмеченного задним числом недостоверна — у тех дней нет реальных дат.
        completionLog.removeAll { isOwnKey($0.key) }
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
        guard programKind.hasBenchWave else { return [] }
        return UpperLowerCalculator.benchWave(input: upperLowerInput)
    }

    /// Номер жимовой тренировки волны для дня программы «Верх / Низ».
    /// Понедельник (день 1) — нечётные номера, четверг (день 3) — чётные.
    func benchSession(week: Int, day: Int) -> Int? {
        guard programKind.hasBenchWave else { return nil }
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
        Set(completedDayKeys.filter { isOwnKey($0) }).count
    }
}


// MARK: - Кэш плана

/// Входные данные плана: одинаковые — значит, и план тот же самый.
fileprivate struct PlanInputs: Equatable {
    let kind: String
    let peaking: Bool
    let input: ProgramInput
    let peakingInput: ProgramInput
    let fullBodyLevel: String
    let euthanasia: EuthanasiaInput?
    let custom: CustomProgram?
    let edits: [PlanEdit]
    let skipped: [SkippedWorkout]
    let reports: [LiftReport]
    let autoregulation: Bool
    let cycle: Int
    let steps: [String: Double]
    let defaultStep: Double
}

/// Несколько последних посчитанных планов. Ключ — сами входные данные,
/// поэтому кэш общий для всех профилей и не может отдать чужой или старый план.
fileprivate enum PlanCache {
    enum Kind { case full, generated }

    private static let lock = NSLock()
    private static var entries: [(kind: Kind, inputs: PlanInputs, plan: WorkoutPlan)] = []
    private static let limit = 6

    static func plan(for inputs: PlanInputs, kind: Kind, build: () -> WorkoutPlan) -> WorkoutPlan {
        lock.lock()
        if let hit = entries.first(where: { $0.kind == kind && $0.inputs == inputs }) {
            lock.unlock()
            return hit.plan
        }
        lock.unlock()
        // Считаем без замка: внутри расчёт может сам спросить другой план.
        let plan = build()
        lock.lock()
        entries.removeAll { $0.kind == kind && $0.inputs == inputs }
        entries.insert((kind, inputs, plan), at: 0)
        if entries.count > limit { entries.removeLast(entries.count - limit) }
        lock.unlock()
        return plan
    }
}
