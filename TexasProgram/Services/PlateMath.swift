import Foundation

/// Расчёт блинов на гриф и разминочных подходов.
///
/// Обе задачи — чистые функции от рабочего веса, никакого состояния.
enum PlateMath {
    /// Олимпийский гриф.
    static let barWeight: Double = 20

    /// Блины от тяжёлого к лёгкому, по паре каждого достаточно для любого веса цикла.
    static let availablePlates: [Double] = [25, 20, 15, 10, 5, 2.5, 1.25]

    /// Набор блинов на одну сторону грифа.
    ///
    /// `nil`, если вес меньше грифа или не набирается имеющимися блинами —
    /// показывать заведомо неверную раскладку хуже, чем не показывать ничего.
    static func perSide(for total: Double, bar: Double = barWeight) -> [Double]? {
        guard total.isFinite, total >= bar else { return nil }
        var remaining = (total - bar) / 2
        guard remaining > 0 else { return [] }

        var result: [Double] = []
        for plate in availablePlates {
            while remaining >= plate - 0.001 {
                result.append(plate)
                remaining -= plate
            }
        }
        return remaining < 0.01 ? result : nil
    }

    /// Строка вида «20 + 20 + 10 + 2,5».
    static func perSideText(for total: Double, bar: Double = barWeight) -> String? {
        guard let plates = perSide(for: total, bar: bar) else { return nil }
        guard !plates.isEmpty else { return "пустой гриф" }
        return plates.map { format($0) }.joined(separator: " + ")
    }

    /// Разминочные подходы до рабочего веса.
    ///
    /// Классическая схема: пустой гриф, затем 55 / 70 / 85 процентов с падающим
    /// числом повторов. Ступени, которые совпали с грифом или рабочим весом,
    /// выбрасываются, чтобы не было дублей.
    static func warmups(to working: Double, bar: Double = barWeight) -> [WarmupSet] {
        guard working.isFinite, working > bar else { return [] }

        var result: [WarmupSet] = [WarmupSet(weight: bar, reps: 5)]
        let steps: [(percent: Double, reps: Int)] = [(0.55, 5), (0.70, 3), (0.85, 2)]

        for step in steps {
            let weight = ProgramCalculator.roundToPlate(working * step.percent)
            guard weight > bar, weight < working else { continue }
            guard weight > (result.last?.weight ?? 0) else { continue }
            result.append(WarmupSet(weight: weight, reps: step.reps))
        }
        return result
    }

    /// Целые без дробной части, 2,5 и 1,25 — с запятой и без лишних нулей.
    private static func format(_ value: Double) -> String {
        if value == value.rounded() { return String(Int(value)) }
        return value.formatted(.number.precision(.fractionLength(0...2)))
    }
}

struct WarmupSet: Identifiable, Equatable, Hashable {
    let weight: Double
    let reps: Int

    var id: Double { weight }

    var weightText: String {
        weight == weight.rounded()
            ? String(Int(weight))
            : weight.formatted(.number.precision(.fractionLength(1)))
    }
}
