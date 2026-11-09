import Foundation

/// Как шли веса последние две недели программы — от ответа зависит,
/// будет ли разгрузка в начале следующего цикла.
enum CycleFeel: String, CaseIterable, Identifiable, Codable, Sendable {
    case easy
    case normal
    case hard

    var id: String { rawValue }

    var title: String {
        switch self {
        case .easy: return "Легко"
        case .normal: return "Тяжело, но всё сделал"
        case .hard: return "Не добирал повторы"
        }
    }

    var caption: String {
        switch self {
        case .easy: return "Оставался запас, можно было больше"
        case .normal: return "Последние подходы шли на пределе"
        case .hard: return "Срывал подходы или стоял на одном весе"
        }
    }

    /// Что будет со стартом следующего цикла.
    var outcome: String {
        switch self {
        case .easy: return "Без разгрузки: следующий цикл начнётся сразу со следующего шага прибавки."
        case .normal: return "Без разгрузки: следующий цикл начнётся с весов, на которых ты закончил."
        case .hard: return "Разгрузка: старт на 10 % легче, дальше веса растут с той же прибавкой и быстро догоняют прошлый максимум."
        }
    }

    /// Разгрузочный коэффициент: только для тяжёлого ответа.
    static let deload = 0.9
    /// Лёгкий ответ у готовых программ поднимает максимум ещё на шаг.
    static let easyBonus = 1.025
}

/// Рост одного упражнения за цикл.
struct CycleGrowth: Equatable, Identifiable, Sendable {
    let name: String
    let from: Double
    let to: Double

    var id: String { name }
    var delta: Double { to - from }
}

/// Итоги законченного цикла.
struct CycleRecap: Equatable, Sendable {
    let weeks: Int
    let workouts: Int
    let tonnage: Double
    let growth: [CycleGrowth]
}

/// Подсказка по записям последних двух недель: что человек сам записал.
struct CycleFeelHint: Equatable, Sendable {
    /// Среднее усилие по ползунку 1–10, если его ставили.
    let averageEffort: Double?
    let missedSets: Int
    let loggedSets: Int
    let suggestion: CycleFeel

    var text: String {
        var parts: [String] = []
        if let averageEffort {
            parts.append("среднее усилие \(String(format: "%.1f", averageEffort).replacingOccurrences(of: ".", with: ",")) из 10")
        }
        if loggedSets > 0 {
            parts.append(missedSets == 0
                         ? "все записанные подходы выполнены"
                         : "недобрано подходов: \(missedSets) из \(loggedSets)")
        }
        return "По записям: " + parts.joined(separator: ", ") + ". Похоже на «\(suggestion.title)»."
    }
}

/// Старт следующего цикла.
enum NextCycleStart: Equatable, Sendable {
    /// Своя программа: новые стартовые веса упражнений.
    case custom(CustomProgram, changes: [CycleGrowth])
    /// Готовая программа: новые максимумы приседа, жима и тяги.
    case maxes(squat: Double, bench: Double, deadlift: Double, changes: [CycleGrowth])

    var changes: [CycleGrowth] {
        switch self {
        case .custom(_, let changes): return changes
        case .maxes(_, _, _, let changes): return changes
        }
    }
}

enum CycleFinish {
    /// Первое число в записи повторов: «8–10» → 8, «5» → 5, «3×5» → 3.
    static func minimumReps(_ text: String) -> Int? {
        var digits = ""
        for character in text {
            if character.isNumber { digits.append(character) } else if !digits.isEmpty { break }
        }
        return Int(digits)
    }

    /// Что выбрать по записям. Нет ни усилия, ни подходов — подсказки нет.
    static func suggestion(averageEffort: Double?, missedSets: Int, loggedSets: Int) -> CycleFeel? {
        guard averageEffort != nil || loggedSets > 0 else { return nil }
        let missedShare = loggedSets > 0 ? Double(missedSets) / Double(loggedSets) : 0
        if missedShare >= 0.15 || (averageEffort ?? 0) >= 9 { return .hard }
        if missedSets == 0, let averageEffort, averageEffort <= 5 { return .easy }
        return .normal
    }

    /// Новые стартовые веса своей программы.
    ///
    /// Конечный вес — тот, что программа давала на последней неделе.
    /// Упражнения без прибавки не трогаем: у них вес и так стоит на месте,
    /// а разгрузка без последующего роста просто снизила бы его навсегда.
    static func nextCustom(_ program: CustomProgram, feel: CycleFeel) -> (CustomProgram, [CycleGrowth]) {
        var next = program
        var changes: [CycleGrowth] = []
        let lastWeek = max(program.weeks, 1)
        for d in next.days.indices {
            for e in next.days[d].exercises.indices {
                let exercise = next.days[d].exercises[e]
                guard let start = exercise.kilograms, exercise.weeklyIncrement != 0 else { continue }
                let finish = ProgramCalculator.roundToPlate(start + Double(lastWeek - 1) * exercise.weeklyIncrement)
                let value: Double
                switch feel {
                case .easy: value = finish + exercise.weeklyIncrement
                case .normal: value = finish
                case .hard: value = finish * CycleFeel.deload
                }
                let rounded = max(ProgramCalculator.roundToPlate(value), 0)
                next.days[d].exercises[e].kilograms = rounded
                if !changes.contains(where: { $0.name == exercise.name }) {
                    changes.append(CycleGrowth(name: exercise.name, from: start, to: rounded))
                }
            }
        }
        return (next, changes)
    }

    /// Новые максимумы готовой программы.
    ///
    /// `ratio` — во сколько раз вырос тяжёлый вес движения за цикл: старый
    /// максимум, умноженный на него, и есть ориентир, где человек закончил.
    static func nextMax(_ max: Double, ratio: Double, feel: CycleFeel) -> Double {
        let carried = max * min(Swift.max(ratio, 1), 1.3)
        let value: Double
        switch feel {
        case .easy: value = carried * CycleFeel.easyBonus
        case .normal: value = carried
        case .hard: value = carried * CycleFeel.deload
        }
        return ProgramCalculator.roundToPlate(value)
    }
}

extension ProgramProfile {
    /// Все тренировки плана закрыты: выполнены или сознательно пропущены.
    var isProgramFinished: Bool {
        let weeks = workoutPlan.weeks
        guard !weeks.isEmpty else { return false }
        return weeks.allSatisfy { week in
            week.days.allSatisfy { isCompleted(week: week.number, day: $0.number) || isSkipped(week: week.number, day: $0.number) }
        }
    }

    /// Записи о выполненных днях текущего цикла.
    private var cycleRecords: [CompletionRecord] {
        completionLog.filter { isOwnKey($0.key) }.sorted { $0.date < $1.date }
    }

    var cycleRecap: CycleRecap {
        let tonnage = setLog.reduce(0.0) { sum, item in
            guard let day = item.key.split(separator: "|").first, isOwnKey(String(day)) else { return sum }
            return sum + item.value.tonnage
        }
        let growth: [CycleGrowth]
        if programKind == .custom, let program = customProgram {
            growth = CycleFinish.nextCustom(program, feel: .normal).1
        } else {
            let records = cycleRecords
            growth = MainLift.allCases.compactMap { lift in
                let values = records.compactMap { record -> Double? in
                    switch lift {
                    case .squat: return record.actualSquat ?? record.squat
                    case .bench: return record.actualBench ?? record.bench
                    case .deadlift: return record.actualDeadlift ?? record.deadlift
                    }
                }
                guard let first = values.first, let last = values.last else { return nil }
                return CycleGrowth(name: lift.rawValue, from: first, to: last)
            }
        }
        return CycleRecap(weeks: workoutPlan.weeks.count, workouts: completedDayCount, tonnage: tonnage, growth: growth)
    }

    /// Подсказка по двум последним неделям плана.
    var cycleFeelHint: CycleFeelHint? {
        let weeks = workoutPlan.weeks.suffix(2)
        let numbers = Set(weeks.map(\.number))
        let efforts = cycleRecords
            .filter { numbers.contains($0.week) }
            .compactMap { $0.effort.flatMap(CycleFinish.minimumReps) }
            .filter { (1...10).contains($0) }
        var missed = 0, logged = 0
        for week in weeks {
            for day in week.days {
                for exercise in day.exercises where exercise.sets > 0 {
                    guard let target = CycleFinish.minimumReps(exercise.reps) else { continue }
                    for index in 0..<exercise.sets {
                        guard let entry = setEntry(week: week.number, day: day.number, exercise: exercise, index: index) else { continue }
                        logged += 1
                        if entry.reps < target { missed += 1 }
                    }
                }
            }
        }
        let average = efforts.isEmpty ? nil : Double(efforts.reduce(0, +)) / Double(efforts.count)
        guard let suggestion = CycleFinish.suggestion(averageEffort: average, missedSets: missed, loggedSets: logged) else { return nil }
        return CycleFeelHint(averageEffort: average, missedSets: missed, loggedSets: logged, suggestion: suggestion)
    }

    /// Во сколько раз вырос тяжёлый вес движения от первой недели к последней.
    private func cycleRatio(_ lift: MainLift) -> Double {
        let plan = workoutPlan
        func heaviest(_ week: WorkoutWeekPlan?) -> Double? {
            week?.days.flatMap(\.exercises)
                .filter { $0.name == lift.rawValue }
                .compactMap { exercise -> Double? in
                    if case .kilograms(let value) = exercise.load { return value }
                    return nil
                }
                .max()
        }
        guard let first = heaviest(plan.weeks.first), first > 0,
              let last = heaviest(plan.weeks.last) else { return 1 }
        return last / first
    }

    /// Каким будет следующий цикл при этом ответе.
    func nextCycleStart(feel: CycleFeel) -> NextCycleStart? {
        if programKind == .custom {
            guard let program = customProgram else { return nil }
            let (next, changes) = CycleFinish.nextCustom(program, feel: feel)
            return .custom(next, changes: changes)
        }
        let current = (squat: peakSquat5RM ?? squat5RM, bench: peakBench5RM ?? bench5RM, deadlift: peakDeadlift5RM ?? deadlift5RM)
        let squat = CycleFinish.nextMax(current.squat, ratio: cycleRatio(.squat), feel: feel)
        let bench = CycleFinish.nextMax(current.bench, ratio: cycleRatio(.bench), feel: feel)
        let deadlift = CycleFinish.nextMax(current.deadlift, ratio: cycleRatio(.deadlift), feel: feel)
        let label = maximumLabel
        let changes = [
            CycleGrowth(name: "\(MainLift.squat.rawValue) · \(label)", from: current.squat, to: squat),
            CycleGrowth(name: "\(MainLift.bench.rawValue) · \(label)", from: current.bench, to: bench),
            CycleGrowth(name: "\(MainLift.deadlift.rawValue) · \(label)", from: current.deadlift, to: deadlift)
        ]
        return .maxes(squat: squat, bench: bench, deadlift: deadlift, changes: changes)
    }

    /// Начинает следующий цикл своей программы с новыми весами.
    func startNewCustomCycle(_ program: CustomProgram, now: Date = Date()) {
        customProgram = program
        startNewCycle(squat: squat5RM, bench: bench5RM, deadlift: deadlift5RM, now: now)
    }

    /// Повтор цикла с теми же весами.
    func repeatCycle(now: Date = Date()) {
        startNewCycle(squat: squat5RM, bench: bench5RM, deadlift: deadlift5RM, now: now)
    }
}
