import SwiftUI

/// Тип подхода в волне из 14 жимовых тренировок.
enum BenchSetKind: String, Codable, Hashable, Sendable {
    case normal = "обычный"
    case negative = "негатив"
    case test = "тест"
    case record = "рекорд"

    var isSpecial: Bool { self != .normal }

    var systemImage: String {
        switch self {
        case .normal: return "circle.dashed"
        case .negative: return "arrow.down.circle.fill"
        case .test: return "flame.fill"
        case .record: return "trophy.fill"
        }
    }

    var tint: Color {
        switch self {
        case .normal: return Theme.accent
        case .negative: return Theme.accentDeep
        case .test: return Theme.warning
        case .record: return Theme.record
        }
    }

    var gradient: LinearGradient {
        switch self {
        case .normal: return Theme.accentGradient
        case .negative: return Theme.deepGradient
        case .test, .record: return Theme.recordGradient
        }
    }

    var hint: String {
        switch self {
        case .normal: return "Рабочий подход"
        case .negative: return "Негатив — только со страхующим"
        case .test: return "Подход на максимум повторов"
        case .record: return "Попытка нового рекорда"
        }
    }
}

/// Форматирование весов вынесено сюда: `Double.formatted` дорогой, и вызывать его
/// на каждом кадре прокрутки нельзя. Все строки считаются один раз при сборке волны.
enum WeightFormat {
    static func short(_ value: Double) -> String {
        String(Int(value.rounded()))
    }

    static func kilograms(_ value: Double) -> String {
        short(value) + " кг"
    }

    /// Веса основных программ идут с шагом 2,5 кг, поэтому дробная часть — только «,5».
    static func kilogramsPrecise(_ value: Double) -> String {
        if value.rounded() == value { return String(Int(value)) + " кг" }
        return String(format: "%.1f", value).replacingOccurrences(of: ".", with: ",") + " кг"
    }
}

/// Один подход жима: процент от 1ПМ, посчитанный вес и повторы.
struct BenchSetPlan: Identifiable, Hashable, Codable, Sendable {
    let id: Int
    let percent: Double
    let weight: Double
    let reps: String
    let kind: BenchSetKind
    let percentText: String
    let weightText: String

    init(id: Int, percent: Double, weight: Double, reps: String, kind: BenchSetKind) {
        self.id = id
        self.percent = percent
        self.weight = weight
        self.reps = reps
        self.kind = kind
        self.percentText = String(Int((percent * 100).rounded())) + "%"
        self.weightText = WeightFormat.kilograms(weight)
    }
}

/// Одна из 14 жимовых тренировок волнового цикла.
struct BenchSessionPlan: Identifiable, Hashable, Codable, Sendable {
    let id: Int
    let week: Int
    let dayNumber: Int
    let dayTitle: String
    let shortDayTitle: String
    let sets: [BenchSetPlan]
    /// Первый нестандартный подход задаёт характер тренировки.
    let highlight: BenchSetKind?
    let exerciseName: String
    let topWeight: Double
    let topWeightText: String
    let repsText: String
    let weightsText: String

    init(id: Int, week: Int, dayNumber: Int, dayTitle: String, sets: [BenchSetPlan]) {
        self.id = id
        self.week = week
        self.dayNumber = dayNumber
        self.dayTitle = dayTitle
        self.shortDayTitle = dayTitle.split(separator: "·").first
            .map { $0.trimmingCharacters(in: .whitespaces).capitalized } ?? dayTitle
        self.sets = sets

        let special = sets.first(where: { $0.kind.isSpecial })?.kind
        self.highlight = special
        self.exerciseName = special.map { "Жим лёжа · \($0.rawValue)" } ?? "Жим лёжа"

        let top = sets.map(\.weight).max() ?? 0
        self.topWeight = top
        self.topWeightText = WeightFormat.kilograms(top)
        self.repsText = sets.map(\.reps).joined(separator: " / ")
        self.weightsText = sets.map { WeightFormat.short($0.weight) }.joined(separator: " / ") + " кг"
    }

    var accent: LinearGradient { highlight?.gradient ?? Theme.accentGradient }
    var accentColor: Color { highlight?.tint ?? Theme.accent }
}
