import Foundation

// MARK: - Библиотека упражнений

/// Упражнение библиотеки: из общего файла приложения или своё.
/// Файл библиотеки один на iOS и Android — `exercise-library.json`.
struct LibraryExercise: Codable, Equatable, Hashable, Identifiable, Sendable {
    var id: String
    var name: String
    /// Группа мышц — ключ из `ExerciseLibrary.groups`.
    var group: String
    var secondary: [String] = []
    /// Инвентарь — ключ из `ExerciseLibrary.equipment`.
    var equipment: String
    var steps: [String] = []

    /// Свои упражнения — с приставкой, чтобы не спутать с библиотечными.
    static let customPrefix = "my-"
    var isCustom: Bool { id.hasPrefix(Self.customPrefix) }

    static func custom(name: String, group: String, equipment: String) -> LibraryExercise {
        LibraryExercise(id: customPrefix + UUID().uuidString, name: name, group: group, equipment: equipment)
    }

    init(id: String, name: String, group: String, secondary: [String] = [], equipment: String, steps: [String] = []) {
        self.id = id
        self.name = name
        self.group = group
        self.secondary = secondary
        self.equipment = equipment
        self.steps = steps
    }

    /// В копии с Android и в старых своих упражнениях полей может не быть.
    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        id = try values.decode(String.self, forKey: .id)
        name = try values.decode(String.self, forKey: .name)
        group = try values.decodeIfPresent(String.self, forKey: .group) ?? "other"
        secondary = try values.decodeIfPresent([String].self, forKey: .secondary) ?? []
        equipment = try values.decodeIfPresent(String.self, forKey: .equipment) ?? "other"
        steps = try values.decodeIfPresent([String].self, forKey: .steps) ?? []
    }
}

enum ExerciseLibrary {
    struct Option: Codable, Hashable, Identifiable, Sendable {
        let id: String
        let title: String
    }

    private struct File: Decodable {
        let groups: [Option]
        let equipment: [Option]
        let exercises: [LibraryExercise]
    }

    private static let file: File = {
        guard let url = Bundle.main.url(forResource: "exercise-library", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let file = try? JSONDecoder().decode(File.self, from: data)
        else { return File(groups: [], equipment: [], exercises: []) }
        return file
    }()

    static var groups: [Option] { file.groups }
    static var equipment: [Option] { file.equipment }
    static var builtIn: [LibraryExercise] { file.exercises }

    private static let groupTitles = Dictionary(uniqueKeysWithValues: file.groups.map { ($0.id, $0.title) })
    private static let equipmentTitles = Dictionary(uniqueKeysWithValues: file.equipment.map { ($0.id, $0.title) })
    private static let byName: [String: LibraryExercise] = Dictionary(
        file.exercises.map { (normalized($0.name), $0) }, uniquingKeysWith: { first, _ in first }
    )

    static func groupTitle(_ id: String) -> String { groupTitles[id] ?? "Другое" }
    static func equipmentTitle(_ id: String) -> String { equipmentTitles[id] ?? "Другое" }

    static func normalized(_ name: String) -> String {
        name.lowercased().replacingOccurrences(of: "ё", with: "е").trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Библиотека вместе со своими: свои — первыми, они нужнее.
    static func all(custom: [LibraryExercise]) -> [LibraryExercise] { custom + builtIn }

    /// Упражнение по названию — для «i» у упражнений программы и шаблонов.
    static func find(named name: String, custom: [LibraryExercise] = []) -> LibraryExercise? {
        let key = normalized(name)
        return custom.first { normalized($0.name) == key } ?? byName[key]
    }

    /// Поиск по словам в любом порядке: «жим гант» найдёт «Жим гантелей лёжа».
    static func filter(_ items: [LibraryExercise], query: String, group: String?, equipment: String?) -> [LibraryExercise] {
        let words = normalized(query).split(separator: " ").map(String.init)
        return items.filter { item in
            if let group, item.group != group && !item.secondary.contains(group) { return false }
            if let equipment, item.equipment != equipment { return false }
            guard !words.isEmpty else { return true }
            let name = normalized(item.name)
            return words.allSatisfy { name.contains($0) }
        }
    }

    /// Разделы по группам мышц в порядке файла: «Грудь», «Спина»…
    /// При фильтре по мышце упражнение со второстепенной мышцей идёт в её раздел.
    static func sections(_ items: [LibraryExercise], group: String? = nil) -> [(group: Option, items: [LibraryExercise])] {
        let order = groups + [Option(id: "other", title: "Другое")]
        let grouped = Dictionary(grouping: items) { item -> String in
            if let group { return group }
            return order.contains { $0.id == item.group } ? item.group : "other"
        }
        return order.compactMap { option in
            guard let list = grouped[option.id], !list.isEmpty else { return nil }
            return (option, list)
        }
    }
}

// MARK: - Шаблоны тренировок

/// Подход шаблона: сколько килограммов и повторов. Ноль килограммов — вес
/// возьмётся из прошлого раза.
struct TemplateSet: Codable, Equatable, Hashable, Sendable {
    var weight: Double
    var reps: Int
}

struct TemplateExercise: Codable, Equatable, Hashable, Identifiable, Sendable {
    var id: String = "tx-" + UUID().uuidString
    var name: String
    var restSeconds: Int = 120
    var sets: [TemplateSet] = [TemplateSet(weight: 0, reps: 10), TemplateSet(weight: 0, reps: 10), TemplateSet(weight: 0, reps: 10)]

    /// Упражнение для тренировки: подходов столько, сколько строк; вес — из
    /// первой строки, если он вписан, иначе из истории.
    var prescription: ExercisePrescription {
        let reps = sets.first?.reps ?? 10
        let weight = sets.first(where: { $0.weight > 0 })?.weight
        return ExercisePrescription(
            id: id,
            name: name,
            sets: max(sets.count, 1),
            reps: "\(reps)",
            load: weight.map { .kilograms($0) } ?? .repRange("\(reps)")
        )
    }
}

/// Своя тренировка, как в REBRN: упражнения, отдых и подходы с весом и повторами.
/// Запускается в любой день и не трогает программу: подходы идут в историю,
/// рекорды и серию, а программа ведёт свои веса как раньше.
struct WorkoutTemplate: Codable, Equatable, Hashable, Identifiable, Sendable {
    var id: String = UUID().uuidString
    var name: String
    var exercises: [TemplateExercise] = []
    /// Показывать в профиле — тогда шаблон видят в «Сообществе».
    var isShared: Bool = false
    var createdAt: Date = Date()

    var setCount: Int { exercises.reduce(0) { $0 + $1.sets.count } }

    /// «7 упр · 20 подх» — как в списке REBRN.
    var summary: String { "\(exercises.count) упр · \(setCount) подх" }

    /// Группы мышц по упражнениям — подпись в каталоге.
    func groups(custom: [LibraryExercise] = []) -> [String] {
        var result: [String] = []
        for exercise in exercises {
            guard let group = ExerciseLibrary.find(named: exercise.name, custom: custom)?.group else { continue }
            let title = ExerciseLibrary.groupTitle(group)
            if !result.contains(title) { result.append(title) }
        }
        return result
    }

    init(id: String = UUID().uuidString, name: String, exercises: [TemplateExercise] = [], isShared: Bool = false, createdAt: Date = Date()) {
        self.id = id
        self.name = name
        self.exercises = exercises
        self.isShared = isShared
        self.createdAt = createdAt
    }

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        id = try values.decode(String.self, forKey: .id)
        name = try values.decode(String.self, forKey: .name)
        exercises = try values.decodeIfPresent([TemplateExercise].self, forKey: .exercises) ?? []
        isShared = try values.decodeIfPresent(Bool.self, forKey: .isShared) ?? false
        createdAt = try values.decodeIfPresent(Date.self, forKey: .createdAt) ?? Date()
    }

    /// Копия под своим именем — из каталога, сообщества или «Дублировать».
    func copy(named newName: String? = nil) -> WorkoutTemplate {
        WorkoutTemplate(
            name: newName ?? name,
            exercises: exercises.map { TemplateExercise(name: $0.name, restSeconds: $0.restSeconds, sets: $0.sets) }
        )
    }

    /// Программа из чужого профиля: «4 × 8», «80 кг» — в подходы шаблона.
    static func from(title: String, exercises: [(name: String, sets: Int, reps: String, load: String)]) -> WorkoutTemplate {
        WorkoutTemplate(name: title, exercises: exercises.map { item in
            let reps = item.reps.split(whereSeparator: { !$0.isNumber }).first.flatMap { Int($0) } ?? 10
            let weight = Double(item.load.replacingOccurrences(of: ",", with: ".")
                .split(whereSeparator: { !$0.isNumber && $0 != "." }).first ?? "") ?? 0
            return TemplateExercise(
                name: item.name,
                sets: Array(repeating: TemplateSet(weight: weight, reps: reps), count: min(max(item.sets, 1), 10))
            )
        })
    }
}

/// Готовые тренировки — вкладка «Каталог». Веса пустые: возьмутся из
/// прошлых раз или впишешь сам.
enum TemplateCatalog {
    private static func ex(_ name: String, _ sets: Int, _ reps: Int, rest: Int = 120) -> TemplateExercise {
        TemplateExercise(id: "cat-" + name, name: name, restSeconds: rest, sets: Array(repeating: TemplateSet(weight: 0, reps: reps), count: sets))
    }

    static let all: [WorkoutTemplate] = [
        WorkoutTemplate(id: "catalog-fullbody-a", name: "Фулбади A", exercises: [
            ex("Приседания со штангой", 3, 8, rest: 180), ex("Жим штанги лёжа", 3, 8, rest: 180),
            ex("Тяга штанги в наклоне", 3, 10), ex("Жим гантелей сидя", 3, 10),
            ex("Сгибание ног лёжа", 3, 12, rest: 90), ex("Подъём гантелей на бицепс", 2, 12, rest: 90),
            ex("Планка", 3, 45, rest: 60)
        ]),
        WorkoutTemplate(id: "catalog-fullbody-b", name: "Фулбади B", exercises: [
            ex("Становая тяга", 3, 5, rest: 180), ex("Жим штанги стоя", 3, 8, rest: 150),
            ex("Тяга верхнего блока", 3, 10), ex("Жим ногами", 3, 12),
            ex("Разведение гантелей лёжа", 3, 12, rest: 90), ex("Разгибания на блоке с канатом", 3, 12, rest: 90),
            ex("Подъём ног в висе", 3, 12, rest: 60)
        ]),
        WorkoutTemplate(id: "catalog-fullbody-c", name: "Фулбади C", exercises: [
            ex("Фронтальные приседания", 3, 8, rest: 180), ex("Жим гантелей на наклонной", 3, 10),
            ex("Подтягивания", 3, 8), ex("Румынская тяга", 3, 10, rest: 150),
            ex("Махи гантелями в стороны", 3, 15, rest: 60), ex("Молотки с гантелями", 3, 12, rest: 90),
            ex("Подъём на носки стоя", 3, 15, rest: 60)
        ]),
        WorkoutTemplate(id: "catalog-upper", name: "Верх", exercises: [
            ex("Жим штанги лёжа", 4, 6, rest: 180), ex("Тяга штанги в наклоне", 4, 8, rest: 150),
            ex("Жим гантелей сидя", 3, 10), ex("Тяга верхнего блока", 3, 10),
            ex("Подъём штанги на бицепс", 3, 10, rest: 90), ex("Французский жим лёжа", 3, 10, rest: 90)
        ]),
        WorkoutTemplate(id: "catalog-lower", name: "Низ", exercises: [
            ex("Приседания со штангой", 4, 6, rest: 180), ex("Румынская тяга", 3, 8, rest: 150),
            ex("Жим ногами", 3, 12), ex("Сгибание ног сидя", 3, 12, rest: 90),
            ex("Подъём на носки стоя", 4, 12, rest: 60), ex("Скручивания на блоке стоя на коленях", 3, 15, rest: 60)
        ]),
        WorkoutTemplate(id: "catalog-push", name: "Жим", exercises: [
            ex("Жим штанги лёжа", 4, 8, rest: 180), ex("Жим гантелей на наклонной", 3, 10),
            ex("Жим штанги стоя", 3, 8, rest: 150), ex("Сведение в кроссовере", 3, 12, rest: 90),
            ex("Махи гантелями в стороны", 4, 15, rest: 60), ex("Разгибания на верхнем блоке", 3, 12, rest: 90)
        ]),
        WorkoutTemplate(id: "catalog-pull", name: "Тяга", exercises: [
            ex("Становая тяга", 3, 5, rest: 180), ex("Подтягивания", 4, 8, rest: 150),
            ex("Тяга горизонтального блока", 3, 10), ex("Тяга каната к лицу", 3, 15, rest: 60),
            ex("Подъём штанги на бицепс", 3, 10, rest: 90), ex("Молотки с гантелями", 3, 12, rest: 90)
        ]),
        WorkoutTemplate(id: "catalog-legs", name: "Ноги", exercises: [
            ex("Приседания со штангой", 4, 8, rest: 180), ex("Жим ногами", 3, 12),
            ex("Румынская тяга", 3, 10, rest: 150), ex("Разгибания ног в тренажёре", 3, 15, rest: 90),
            ex("Сгибание ног лёжа", 3, 12, rest: 90), ex("Подъём на носки сидя", 4, 15, rest: 60)
        ]),
        WorkoutTemplate(id: "catalog-arms", name: "Руки", exercises: [
            ex("Жим лёжа узким хватом", 3, 8, rest: 150), ex("Подъём EZ-грифа на бицепс", 3, 10, rest: 90),
            ex("Французский жим лёжа", 3, 10, rest: 90), ex("Молотки с гантелями", 3, 12, rest: 90),
            ex("Разгибания на блоке с канатом", 3, 12, rest: 60), ex("Сгибания на нижнем блоке", 3, 15, rest: 60)
        ]),
        WorkoutTemplate(id: "catalog-home", name: "Дома без железа", exercises: [
            ex("Отжимания", 4, 15, rest: 60), ex("Приседания с выпрыгиванием", 4, 15, rest: 60),
            ex("Австралийские подтягивания", 3, 12, rest: 60), ex("Выпады в ходьбе", 3, 20, rest: 60),
            ex("Обратные отжимания от скамьи", 3, 15, rest: 60), ex("Планка", 3, 60, rest: 45),
            ex("Бёрпи", 3, 10, rest: 60)
        ])
    ]
}

// MARK: - Свободная тренировка

/// Тренировка вне программы: из своего шаблона, каталога или пустая.
/// Живёт в «нулевой неделе»: ключ подходов — `free-N`, программа его не видит
/// и не двигается, а история, рекорды и серия — видят.
struct FreeWorkoutDay: Codable, Equatable, Hashable, Sendable {
    var number: Int
    var title: String
    var templateID: String?
    var date: Date
    var exercises: [FreeExercise]
    var completed: Bool = false

    var plan: WorkoutDayPlan {
        WorkoutDayPlan(id: ProgramProfile.freeKey(number), number: number, title: title, exercises: exercises.map(\.prescription))
    }
}

/// Упражнение свободной тренировки — простыми полями, одинаковыми с Android.
struct FreeExercise: Codable, Equatable, Hashable, Sendable {
    var id: String
    var name: String
    var sets: Int
    var reps: String
    var kilograms: Double?

    init(_ prescription: ExercisePrescription) {
        id = prescription.id
        name = prescription.name
        sets = prescription.sets
        reps = prescription.reps
        if case .kilograms(let value) = prescription.load { kilograms = value }
    }

    var prescription: ExercisePrescription {
        ExercisePrescription(id: id, name: name, sets: sets, reps: reps, load: kilograms.map { .kilograms($0) } ?? .repRange(reps))
    }
}

extension ProgramProfile {
    /// Неделя свободных тренировок. В программах недели начинаются с первой.
    static let freeWeek = 0

    static func freeKey(_ number: Int) -> String { "free-\(number)" }

    func freeDay(_ number: Int) -> FreeWorkoutDay? {
        freeWorkouts.first { $0.number == number }
    }

    /// Свободные тренировки неделей плана — для истории и рекордов.
    var freeWeekPlan: WorkoutWeekPlan? {
        guard !freeWorkouts.isEmpty else { return nil }
        return WorkoutWeekPlan(id: Self.freeWeek, number: Self.freeWeek, days: freeWorkouts.map(\.plan))
    }

    /// Недели, по которым читается история: программа и свободные тренировки.
    var historyWeeks: [WorkoutWeekPlan] {
        guard let free = freeWeekPlan else { return workoutPlan.weeks }
        return workoutPlan.weeks + [free]
    }

    /// Подпись дня в истории: «Неделя 3, день 2» или название своей тренировки.
    func historyTitle(week: Int, day: Int) -> String {
        if week == Self.freeWeek { return freeDay(day)?.title ?? "Своя тренировка" }
        return "Неделя \(week), день \(day)"
    }

    /// День по неделе и номеру — и в программе, и среди свободных.
    func dayPlan(week: Int, day: Int) -> WorkoutDayPlan? {
        if week == Self.freeWeek { return freeDay(day)?.plan }
        return workoutPlan.weeks.first { $0.number == week }?.days.first { $0.number == day }
    }

    /// Начинает свободную тренировку: день появляется сразу, чтобы подходы
    /// писались по ходу и тренировка пережила выгрузку приложения.
    func startFreeWorkout(title: String, templateID: String? = nil, exercises: [ExercisePrescription],
                          now: Date = Date(), calendar: Calendar = .current) -> ScheduledWorkout {
        let number = (freeWorkouts.map(\.number).max() ?? 0) + 1
        let day = FreeWorkoutDay(number: number, title: title, templateID: templateID, date: now, exercises: exercises.map(FreeExercise.init))
        freeWorkouts.append(day)
        return ScheduledWorkout(
            week: Self.freeWeek,
            day: day.plan,
            date: now,
            weekday: calendar.component(.weekday, from: now),
            isCompleted: false,
            benchSession: nil
        )
    }

    /// Отменили свободную тренировку, которую ещё не закончили, — её как не было.
    func discardFreeWorkout(_ number: Int) {
        guard let day = freeDay(number), !day.completed else { return }
        let prefix = Self.freeKey(number) + "|"
        setLog = setLog.filter { !$0.key.hasPrefix(prefix) }
        setProgress = setProgress.filter { !$0.key.hasPrefix(prefix) }
        freeWorkouts.removeAll { $0.number == number }
    }

    /// Удаляет закрытую свободную тренировку из истории целиком.
    func deleteFreeWorkout(_ number: Int) {
        let key = Self.freeKey(number)
        setLog = setLog.filter { !$0.key.hasPrefix(key + "|") }
        setProgress = setProgress.filter { !$0.key.hasPrefix(key + "|") }
        completionLog.removeAll { $0.key == key }
        freeWorkouts.removeAll { $0.number == number }
    }

    /// Отметка свободной тренировки: в историю попадает запись с датой,
    /// а отметки дней программы и дата «последней тренировки» очереди не трогаются.
    func setFreeCompleted(_ number: Int, _ done: Bool, now: Date = Date(), durationSeconds: Int? = nil, effort: String? = nil) {
        guard let index = freeWorkouts.firstIndex(where: { $0.number == number }) else { return }
        freeWorkouts[index].completed = done
        let key = Self.freeKey(number)
        completionLog.removeAll { $0.key == key }
        guard done else { return }
        freeWorkouts[index].date = now
        completionLog.append(CompletionRecord(
            key: key, date: now, week: Self.freeWeek, day: number,
            squat: nil, bench: nil, deadlift: nil,
            durationSeconds: durationSeconds, effort: effort
        ))
    }

    /// Замена упражнения в свободной тренировке — прямо в её дне, без правок программы.
    func replaceFreeExercise(day number: Int, old: ExercisePrescription, with name: String) -> ExercisePrescription {
        let fresh = ExercisePrescription(id: "tx-" + UUID().uuidString, name: name, sets: old.sets, reps: old.reps, load: .repRange(old.reps))
        guard let index = freeWorkouts.firstIndex(where: { $0.number == number }) else { return fresh }
        var exercises = freeWorkouts[index].exercises
        if let position = exercises.firstIndex(where: { $0.id == old.id }) {
            exercises[position] = FreeExercise(fresh)
        } else {
            exercises.append(FreeExercise(fresh))
        }
        freeWorkouts[index].exercises = exercises
        return fresh
    }

    /// Добавляет упражнения в идущую свободную тренировку.
    func appendFreeExercises(day number: Int, _ list: [ExercisePrescription]) {
        guard let index = freeWorkouts.firstIndex(where: { $0.number == number }) else { return }
        freeWorkouts[index].exercises += list.map(FreeExercise.init)
    }

    // MARK: Шаблоны и свои упражнения

    func saveTemplate(_ template: WorkoutTemplate) {
        if let index = workoutTemplates.firstIndex(where: { $0.id == template.id }) {
            workoutTemplates[index] = template
        } else {
            workoutTemplates.append(template)
        }
    }

    func deleteTemplate(_ id: String) {
        workoutTemplates.removeAll { $0.id == id }
    }

    func saveCustomExercise(_ exercise: LibraryExercise) {
        if let index = customExercises.firstIndex(where: { $0.id == exercise.id }) {
            customExercises[index] = exercise
        } else {
            customExercises.insert(exercise, at: 0)
        }
    }

    func deleteCustomExercise(_ id: String) {
        customExercises.removeAll { $0.id == id }
    }

    var exerciseLibrary: [LibraryExercise] { ExerciseLibrary.all(custom: customExercises) }

    /// Начинает тренировку по шаблону.
    func startWorkout(from template: WorkoutTemplate, now: Date = Date()) -> ScheduledWorkout {
        startFreeWorkout(title: template.name, templateID: template.id, exercises: template.exercises.map(\.prescription), now: now)
    }
}
