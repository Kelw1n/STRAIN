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
        case .negative: return LinearGradient(colors: [Theme.accentDeep, Theme.accent], startPoint: .topLeading, endPoint: .bottomTrailing)
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

/// Один подход жима: процент от 1ПМ, посчитанный вес и повторы.
struct BenchSetPlan: Identifiable, Hashable, Codable, Sendable {
    let id: Int
    let percent: Double
    let weight: Double
    let reps: String
    let kind: BenchSetKind

    var percentText: String {
        (percent * 100).formatted(.number.precision(.fractionLength(0))) + "%"
    }

    var weightText: String {
        weight.formatted(.number.precision(.fractionLength(0))) + " кг"
    }
}

/// Одна из 14 жимовых тренировок волнового цикла.
struct BenchSessionPlan: Identifiable, Hashable, Codable, Sendable {
    let id: Int
    let week: Int
    let dayNumber: Int
    let dayTitle: String
    let sets: [BenchSetPlan]

    /// Первый нестандартный подход задаёт характер тренировки.
    var highlight: BenchSetKind? {
        sets.first(where: { $0.kind.isSpecial })?.kind
    }

    var exerciseName: String {
        guard let highlight else { return "Жим лёжа" }
        return "Жим лёжа · \(highlight.rawValue)"
    }

    var topWeight: Double {
        sets.map(\.weight).max() ?? 0
    }

    var topPercent: Double {
        sets.map(\.percent).max() ?? 0
    }

    var repsText: String {
        sets.map(\.reps).joined(separator: " / ")
    }

    var weightsText: String {
        sets.map { $0.weight.formatted(.number.precision(.fractionLength(0))) }.joined(separator: " / ") + " кг"
    }

    var shortDayTitle: String {
        dayTitle.split(separator: "·").first.map { $0.trimmingCharacters(in: .whitespaces).capitalized } ?? dayTitle
    }

    var accent: LinearGradient {
        highlight?.gradient ?? Theme.accentGradient
    }

    var accentColor: Color {
        highlight?.tint ?? Theme.accent
    }
}
