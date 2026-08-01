import Foundation

/// Что пользователь сделал с упражнением в дне программы.
enum PlanEditKind: String, Codable, Sendable {
    /// Своё упражнение добавлено в конец дня.
    case add
    /// Упражнение программы заменено своим — на том же месте в списке.
    case replace
    /// Упражнение программы убрано из дня.
    case hide
    /// Свой порядок упражнений внутри дня.
    case order
}

/// Насколько далеко расходится правка.
enum PlanEditScope: String, CaseIterable, Identifiable, Codable, Sendable {
    /// Одна конкретная тренировка: эта неделя, этот день.
    case single = "Только эта"
    /// Этот день недели во всех неделях программы.
    case everyWeek = "Каждую неделю"

    var id: String { rawValue }

    var explanation: String {
        switch self {
        case .single:
            return "Изменится только эта тренировка. Остальные недели останутся как были."
        case .everyWeek:
            return "Изменится этот день во всех неделях программы. Правка останется навсегда, пока её не убрать."
        }
    }
}

/// Пользовательская правка дня программы.
///
/// Правки хранятся в профиле, а не в готовом плане: план пересчитывается заново
/// при каждом изменении максимумов, и всё, что лежало бы внутри него, терялось бы.
struct PlanEdit: Codable, Equatable, Hashable, Identifiable, Sendable {
    /// Он же идентификатор упражнения, если правка добавляет своё. Префикс
    /// отделяет пользовательские упражнения от программных, у которых id — имя.
    var id: String
    var kind: PlanEditKind
    /// Номер дня программы: 1…3 у «Техаса», 1…4 у «Верх / Низ».
    var day: Int
    /// Неделя разовой правки. `nil` — правка действует во всех неделях.
    var week: Int?
    /// Пикирование нумерует недели с единицы, поэтому его правки лежат отдельно
    /// и не попадают в основную программу.
    var isPeaking: Bool
    /// Что заменяем или прячем — `ExercisePrescription.id` упражнения программы.
    var targetID: String?
    /// Своё упражнение. У `hide` не используется.
    var name: String
    var sets: Int
    var reps: String
    /// Конкретный вес в килограммах, если он задан.
    var kilograms: Double?
    /// Свободная подпись нагрузки, когда веса нет: «RPE 8», «до отказа».
    var loadText: String?
    /// Порядок упражнений дня для правки вида `order`. Ключи, которых в списке
    /// нет, остаются в конце: план мог измениться после перестановки.
    var order: [String]?

    init(
        id: String = "custom-" + UUID().uuidString,
        kind: PlanEditKind,
        day: Int,
        week: Int?,
        isPeaking: Bool,
        targetID: String? = nil,
        name: String = "",
        sets: Int = 3,
        reps: String = "8–12",
        kilograms: Double? = nil,
        loadText: String? = nil,
        order: [String]? = nil
    ) {
        self.id = id
        self.kind = kind
        self.day = day
        self.week = week
        self.isPeaking = isPeaking
        self.targetID = targetID
        self.name = name
        self.sets = sets
        self.reps = reps
        self.kilograms = kilograms
        self.loadText = loadText
        self.order = order
    }

    var scope: PlanEditScope { week == nil ? .everyWeek : .single }

    /// Действует ли правка на конкретную тренировку.
    func matches(week: Int, day: Int, isPeaking: Bool) -> Bool {
        guard self.day == day, self.isPeaking == isPeaking else { return false }
        guard let ownWeek = self.week else { return true }
        return ownWeek == week
    }

    /// Нагрузка: сначала точный вес, потом подпись, иначе RPE по умолчанию.
    var load: LoadPrescription {
        if let kilograms { return .kilograms(kilograms) }
        if let loadText, !loadText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return .repRange(loadText)
        }
        return .rpe("RPE 8")
    }

    /// Упражнение, которое встанет в план.
    var prescription: ExercisePrescription {
        ExercisePrescription(
            id: id,
            name: name,
            sets: sets,
            reps: reps,
            load: load,
            isOptional: true
        )
    }

    /// Короткое описание для списка правок.
    var summary: String {
        switch kind {
        case .hide: return "Убрано"
        case .replace: return "Заменено на «\(name)»"
        case .add: return "Добавлено «\(name)»"
        case .order: return "Свой порядок"
        }
    }
}
