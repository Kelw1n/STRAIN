import Foundation

/// Разминочные подходы перед рабочим весом — чтобы не считать их в голове.
///
/// Только для упражнений со штангой: присед, становая, жимы и тяга в наклоне.
/// Пустой гриф на 10, дальше 40% на 5, 60% на 3 и 80% на 2, от 140 кг ещё
/// 90% на 1. Веса — вниз до 2,5 кг, повторы веса и всё, что не легче рабочего
/// хотя бы на 2,5 кг, выпадают. В становой пустого грифа нет: с пола без блинов
/// не тянут. Расчёт тот же, что на Android, — `WarmUp.kt`.
enum WarmUp {
    struct Step: Equatable, Hashable, Sendable {
        let weight: Double
        let reps: Int
    }

    static let barWeight: Double = 20
    static let settingKey = "warmup_enabled"

    private static let barbellKeys: Set<String> = ["squat", "front_squat", "deadlift", "rdl", "bench", "incline", "ohp", "row"]

    /// Нужна ли разминка этому упражнению: штанга, а не гантели или гири.
    static func applies(to name: String) -> Bool {
        let text = name.lowercased()
        if text.contains("гантел") || text.contains("гир") || text.contains("тренажер") || text.contains("тренажёр") || text.contains("смит") { return false }
        return barbellKeys.contains(ExerciseArt.key(for: name))
    }

    static func steps(for name: String, working: Double) -> [Step] {
        guard applies(to: name) else { return [] }
        return steps(working: working, fromBar: ExerciseArt.key(for: name) != "deadlift")
    }

    static func steps(working: Double, fromBar: Bool = true) -> [Step] {
        guard working.isFinite, working >= barWeight + 15 else { return [] }
        var plan: [(Double?, Int)] = [(0.4, 5), (0.6, 3), (0.8, 2)]
        if fromBar { plan.insert((nil, 10), at: 0) }
        if working >= 140 { plan.append((0.9, 1)) }
        var result: [Step] = []
        for (share, reps) in plan {
            let raw = share.map { working * $0 } ?? barWeight
            let weight = max(barWeight, (raw / 2.5 + 1e-9).rounded(.down) * 2.5)
            guard weight <= working - 2.5 + 1e-9 else { continue }
            if let last = result.last, weight < last.weight + 2.5 - 1e-9 { continue }
            result.append(Step(weight: weight, reps: reps))
        }
        return result
    }
}
