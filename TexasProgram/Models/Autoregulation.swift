import Foundation

/// Основные движения, вокруг которых строится прогрессия.
enum MainLift: String, CaseIterable, Identifiable, Codable, Sendable {
    case squat = "Приседания"
    case bench = "Жим лёжа"
    case deadlift = "Становая тяга"

    var id: String { rawValue }

    /// Верх тела прибавляет медленнее — шаг у него меньше.
    var isUpper: Bool { self == .bench }
}

/// Как прошёл тяжёлый подход.
///
/// Спрашиваем одним касанием, без клавиатуры: в зале после рабочего подхода
/// заполнять форму никто не станет.
enum LiftOutcome: String, CaseIterable, Identifiable, Codable, Sendable {
    case easy = "Легко"
    case solid = "Нормально"
    case grind = "На пределе"
    case missed = "Не добил"

    var id: String { rawValue }

    var subtitle: String {
        switch self {
        case .easy: return "осталось два повтора и больше"
        case .solid: return "остался один повтор"
        case .grind: return "впритык, запаса не было"
        case .missed: return "не сделал все повторения"
        }
    }

    /// Прибавка к рабочему весу на следующую неделю.
    ///
    /// Числа — ориентир из практики линейных программ, а не закон: шаг вверх
    /// крупнее у ног, мельче у жима, а неудача откатывает процентом, чтобы
    /// откат был соразмерен весу.
    func next(after weight: Double, isUpper: Bool) -> Double {
        switch self {
        case .easy: return weight + (isUpper ? 2.5 : 5)
        case .solid: return weight + 2.5
        case .grind: return weight
        case .missed: return weight * 0.925
        }
    }

    var isFailure: Bool { self == .missed }
}

/// Оценка одного движения за одну неделю.
struct LiftReport: Codable, Equatable, Hashable, Identifiable, Sendable {
    /// Пространство имён цикла плюс номер недели: «3», «peak-3», «c2-3».
    var key: String
    var lift: MainLift
    var outcome: LiftOutcome
    var date: Date

    var id: String { key + "|" + lift.rawValue }
}
