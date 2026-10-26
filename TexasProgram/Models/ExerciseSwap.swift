import Foundation

// MARK: - Замена упражнения на ходу
//
// Тренажёр занят — меняем упражнение на похожее прямо в тренировке. Похожие
// ищутся по тому же движению, что и картинка упражнения: присед к приседу,
// тяга к тяге. Вес — по истории нового упражнения, а если его ещё не делали —
// примерно, от веса текущего: у каждого варианта есть доля от основного
// движения группы. Правила те же, что на Android.

enum ExerciseSwap {
    struct Option: Equatable, Sendable {
        let name: String
        /// Доля от основного движения группы: 0,8 — восемьдесят процентов его веса.
        /// 0 — с весом тела, `nil` — вес не прикинуть.
        let ratio: Double?
    }

    struct Family: Sendable {
        let title: String
        /// Ключи картинок упражнений, которые относятся к этой группе.
        let keys: Set<String>
        let options: [Option]
    }

    static let families: [Family] = [
        Family(title: "Присед", keys: ["squat", "front_squat", "goblet", "leg_press", "lunge"], options: [
            Option(name: "Приседания", ratio: 1),
            Option(name: "Фронтальные приседания", ratio: 0.8),
            Option(name: "Присед Зерчера", ratio: 0.75),
            Option(name: "Гакк-приседания", ratio: 1.1),
            Option(name: "Жим ногами", ratio: 1.8),
            Option(name: "Гоблет-приседания", ratio: 0.35),
            Option(name: "Болгарские выпады", ratio: 0.3)
        ]),
        Family(title: "Тяга с пола", keys: ["deadlift", "rdl", "hip_thrust", "hyperext"], options: [
            Option(name: "Становая тяга", ratio: 1),
            Option(name: "Тяга трэп-грифа", ratio: 1.05),
            Option(name: "Румынская тяга", ratio: 0.7),
            Option(name: "Становая тяга на прямых ногах", ratio: 0.65),
            Option(name: "Ягодичный мост со штангой", ratio: 0.9),
            Option(name: "Гуд монинг", ratio: 0.4),
            Option(name: "Гиперэкстензия", ratio: 0.15)
        ]),
        Family(title: "Жим лёжа", keys: ["bench", "incline", "pushup", "dips"], options: [
            Option(name: "Жим лёжа", ratio: 1),
            Option(name: "Жим лёжа на наклонной скамье", ratio: 0.8),
            Option(name: "Жим гантелей лёжа", ratio: 0.38),
            Option(name: "Жим гантелей на наклонной скамье", ratio: 0.33),
            Option(name: "Жим в тренажёре от груди", ratio: 0.9),
            Option(name: "Жим лёжа узким хватом", ratio: 0.85),
            Option(name: "Отжимания на брусьях", ratio: 0),
            Option(name: "Отжимания", ratio: 0)
        ]),
        Family(title: "Жим над головой", keys: ["ohp", "seated_press"], options: [
            Option(name: "Жим штанги стоя", ratio: 1),
            Option(name: "Жим штанги сидя", ratio: 1.05),
            Option(name: "Жим гантелей сидя", ratio: 0.4),
            Option(name: "Жим в тренажёре сидя", ratio: 1.1),
            Option(name: "Жим Арнольда", ratio: 0.35)
        ]),
        Family(title: "Вертикальная тяга", keys: ["pullup", "pulldown"], options: [
            Option(name: "Тяга верхнего блока", ratio: 1),
            Option(name: "Тяга верхнего блока узким хватом", ratio: 1),
            Option(name: "Подтягивания", ratio: 0),
            Option(name: "Пуловер на блоке", ratio: 0.5)
        ]),
        Family(title: "Горизонтальная тяга", keys: ["row", "cable_row"], options: [
            Option(name: "Тяга штанги в наклоне", ratio: 1),
            Option(name: "Тяга горизонтального блока", ratio: 0.9),
            Option(name: "Тяга Т-грифа", ratio: 0.9),
            Option(name: "Тяга в тренажёре Хаммер", ratio: 0.9),
            Option(name: "Тяга гантели в наклоне", ratio: 0.45)
        ]),
        Family(title: "Бицепс бедра", keys: ["leg_curl"], options: [
            Option(name: "Сгибание ног лёжа", ratio: 1),
            Option(name: "Сгибание ног сидя", ratio: 1.1),
            Option(name: "Нордические сгибания", ratio: 0)
        ]),
        Family(title: "Квадрицепс", keys: ["leg_extension"], options: [
            Option(name: "Разгибание ног", ratio: 1),
            Option(name: "Сисси-приседания", ratio: 0),
            Option(name: "Выпады с гантелями", ratio: nil)
        ]),
        Family(title: "Дельты", keys: ["lateral_raise"], options: [
            Option(name: "Махи в стороны", ratio: 1),
            Option(name: "Махи в кроссовере", ratio: 0.6),
            Option(name: "Махи в наклоне", ratio: 0.8),
            Option(name: "Тяга штанги к подбородку", ratio: 3)
        ]),
        Family(title: "Бицепс", keys: ["curl"], options: [
            Option(name: "Сгибание рук со штангой", ratio: 1),
            Option(name: "Сгибание рук с гантелями", ratio: 0.42),
            Option(name: "Молотки с гантелями", ratio: 0.45),
            Option(name: "Сгибание рук на скамье Скотта", ratio: 0.8),
            Option(name: "Сгибание рук на блоке", ratio: 0.9)
        ]),
        Family(title: "Трицепс", keys: ["pushdown"], options: [
            Option(name: "Разгибание рук на блоке", ratio: 1),
            Option(name: "Французский жим", ratio: 0.9),
            Option(name: "Разгибание рук из-за головы", ratio: 0.8),
            Option(name: "Отжимания на брусьях", ratio: 0)
        ]),
        Family(title: "Икры", keys: ["calf"], options: [
            Option(name: "Подъёмы на носки стоя", ratio: 1),
            Option(name: "Подъёмы на носки сидя", ratio: 0.6),
            Option(name: "Носки в жиме ногами", ratio: 1.5)
        ]),
        Family(title: "Кор", keys: ["plank", "crunch", "hanging_raise"], options: [
            Option(name: "Планка", ratio: 0),
            Option(name: "Боковая планка", ratio: 0),
            Option(name: "Подъём ног в висе", ratio: 0),
            Option(name: "Скручивания на блоке", ratio: nil),
            Option(name: "Колесо для пресса", ratio: 0)
        ]),
        Family(title: "Трапеции и хват", keys: ["shrug"], options: [
            Option(name: "Шраги со штангой", ratio: 1),
            Option(name: "Шраги с гантелями", ratio: 0.42),
            Option(name: "Прогулка фермера", ratio: 0.45)
        ])
    ]

    /// Группа движения по названию: по тем же правилам, что и картинка.
    static func family(for name: String) -> Family? {
        let key = ExerciseArt.key(for: name)
        return families.first { $0.keys.contains(key) }
    }

    private static func option(_ name: String, in family: Family) -> Option? {
        let key = ProgramProfile.weightStepKey(name)
        return family.options.first { ProgramProfile.weightStepKey($0.name) == key }
    }

    /// Вес нового упражнения от веса текущего — только если оба есть в группе
    /// и у обоих есть доля. С весом тела — ноль.
    static func estimate(from current: String, weight: Double, to target: String, step: Double) -> Double? {
        guard let family = family(for: current), family.keys.contains(ExerciseArt.key(for: target)) || option(target, in: family) != nil,
              let to = option(target, in: family)?.ratio else { return nil }
        if to == 0 { return 0 }
        guard weight > 0, let from = option(current, in: family)?.ratio, from > 0 else { return nil }
        let value = ProgramProfile.roundWeight(weight / from * to, step: step)
        return value > 0 ? value : nil
    }

    // MARK: Варианты

    struct Suggestion: Equatable, Identifiable, Sendable {
        enum Source: Equatable, Sendable { case history, estimate, unknown }

        let name: String
        /// Вес для подходов: из истории или прикинутый. `nil` — впишет сам.
        let weight: Double?
        /// Повторы из прошлого раза, если упражнение уже делали.
        let reps: Int?
        let source: Source

        var id: String { name }

        var detail: String {
            switch source {
            case .history:
                let load = (weight ?? 0) > 0 ? WeightFormat.kilogramsPrecise(weight ?? 0) : "свой вес"
                return reps.map { "В прошлый раз: \(load) × \($0)" } ?? "В прошлый раз: \(load)"
            case .estimate:
                guard let weight else { return "Вес подберёшь сам" }
                return weight == 0 ? "С весом тела" : "≈ \(WeightFormat.kilogramsPrecise(weight)) — примерно, подбери по ощущениям"
            case .unknown:
                return "Вес подберёшь сам"
            }
        }
    }

    /// Что предложить вместо упражнения: сначала то, что уже делал из этой группы,
    /// потом остальные варианты группы.
    static func suggestions(replacing name: String, currentWeight: Double, profile: ProgramProfile,
                            week: Int, day: Int) -> (done: [Suggestion], similar: [Suggestion]) {
        let current = ProgramProfile.weightStepKey(name)
        let family = family(for: name)
        var done: [Suggestion] = []
        var seen: Set<String> = [current]
        for other in profile.loggedExerciseNames {
            let key = ProgramProfile.weightStepKey(other)
            guard !seen.contains(key), let family, family.keys.contains(ExerciseArt.key(for: other)) else { continue }
            seen.insert(key)
            if let last = lastEntries(other, profile: profile, week: week, day: day) {
                let top = last.max { $0.weight < $1.weight }
                done.append(Suggestion(name: other, weight: top?.weight, reps: top?.reps, source: .history))
            }
        }
        var similar: [Suggestion] = []
        for option in family?.options ?? [] {
            let key = ProgramProfile.weightStepKey(option.name)
            guard !seen.contains(key) else { continue }
            seen.insert(key)
            let weight = estimate(from: name, weight: currentWeight, to: option.name, step: profile.weightStep(for: option.name))
            similar.append(Suggestion(name: option.name, weight: weight, reps: nil, source: weight == nil ? .unknown : .estimate))
        }
        return (done, similar)
    }

    /// Своё название — вес из истории, если такое уже делал.
    static func suggestion(named name: String, currentName: String, currentWeight: Double, profile: ProgramProfile,
                           week: Int, day: Int) -> Suggestion {
        if let last = lastEntries(name, profile: profile, week: week, day: day) {
            let top = last.max { $0.weight < $1.weight }
            return Suggestion(name: name, weight: top?.weight, reps: top?.reps, source: .history)
        }
        let weight = estimate(from: currentName, weight: currentWeight, to: name, step: profile.weightStep(for: name))
        return Suggestion(name: name, weight: weight, reps: nil, source: weight == nil ? .unknown : .estimate)
    }

    private static func lastEntries(_ name: String, profile: ProgramProfile, week: Int, day: Int) -> [SetEntry]? {
        let entries = profile.history(forExerciseNamed: name)
            .filter { !($0.week == week && $0.day == day) }
            .last { !$0.entries.isEmpty }?.entries
        return (entries?.isEmpty ?? true) ? nil : entries
    }
}

/// До какого места действует замена.
enum SwapScope: String, CaseIterable, Identifiable, Sendable {
    case today = "Только сегодня"
    case rest = "До конца программы"

    var id: String { rawValue }

    var explanation: String {
        switch self {
        case .today: return "В следующий раз будет упражнение по программе."
        case .rest: return "Этот день будет с новым упражнением и в следующие недели. Прошлые тренировки не меняются."
        }
    }
}

extension ProgramProfile {
    /// Упражнение программы с весом от расчёта — основное движение. Его меняем
    /// только на сегодня: программа строится вокруг него.
    func isProgramLift(_ exercise: ExercisePrescription) -> Bool {
        // В своей программе веса вписаны руками — её упражнения меняются как любые.
        guard programKind != .custom, case .kilograms = exercise.load else { return false }
        return !planEdits.contains { $0.id == exercise.id }
    }

    /// Что поменялось в правках дня — чтобы отмена тренировки вернула как было.
    struct SwapUndo: Codable, Equatable, Sendable {
        var added: [String] = []
        var originals: [PlanEdit] = []
    }

    /// Меняет упражнение прямо в тренировке. Подходы старого упражнения за этот
    /// день к этому моменту уже сняты. «До конца программы» — разовыми правками
    /// на эту и следующие недели: прошлые тренировки и их история остаются.
    func swapExercise(_ old: ExercisePrescription, to name: String, week: Int, day: Int,
                      scope: SwapScope, undo: inout SwapUndo) -> ExercisePrescription {
        let peaking = isPeaking
        func remember(_ edit: PlanEdit) {
            if !undo.originals.contains(where: { $0.id == edit.id }) && !undo.added.contains(edit.id) {
                undo.originals.append(edit)
            }
        }
        func append(target: String, week: Int) -> PlanEdit {
            let edit = PlanEdit(kind: .replace, day: day, week: week, isPeaking: peaking, targetID: target,
                                name: name, sets: old.sets, reps: old.reps)
            planEdits.append(edit)
            undo.added.append(edit.id)
            // Свой порядок дня: новое упражнение встаёт на место прежнего, а не в конец.
            for index in planEdits.indices where planEdits[index].kind == .order
                && planEdits[index].matches(week: week, day: day, isPeaking: peaking) {
                guard var order = planEdits[index].order, let position = order.firstIndex(of: target),
                      !order.contains(edit.id) else { continue }
                remember(planEdits[index])
                order.insert(edit.id, at: position + 1)
                planEdits[index].order = order
            }
            return edit
        }

        // Сегодня: разовая правка этой недели — переименовываем, иначе новая замена.
        // Правку на все недели не трогаем: переименование задело бы прошлые
        // тренировки и «только сегодня» стало бы «навсегда».
        var today: ExercisePrescription
        let target: String
        if let index = planEdits.firstIndex(where: { $0.id == old.id }), planEdits[index].week == week {
            remember(planEdits[index])
            planEdits[index].name = name
            planEdits[index].kilograms = nil
            planEdits[index].loadText = nil
            today = planEdits[index].prescription
            target = planEdits[index].kind == .add ? old.id : (planEdits[index].targetID ?? old.id)
            if planEdits[index].kind == .add { return today }
        } else {
            today = append(target: old.id, week: week).prescription
            target = old.id
        }
        guard scope == .rest else { return today }

        // Следующие недели: там, где на месте этого упражнения ещё стоит прежнее.
        let weeks = workoutPlan.weeks.map(\.number).filter { $0 > week }
        for next in weeks {
            guard let exercises = workoutPlan.weeks.first(where: { $0.number == next })?
                .days.first(where: { $0.number == day })?.exercises,
                  exercises.contains(where: { $0.id == target }) else { continue }
            _ = append(target: target, week: next)
        }
        today = planEdits.first { $0.id == today.id }?.prescription ?? today
        return today
    }

    /// Отмена тренировки: правки дня — как до замен.
    func undoSwaps(_ undo: SwapUndo) {
        guard !undo.added.isEmpty || !undo.originals.isEmpty else { return }
        planEdits.removeAll { undo.added.contains($0.id) }
        for original in undo.originals {
            if let index = planEdits.firstIndex(where: { $0.id == original.id }) {
                planEdits[index] = original
            } else {
                planEdits.append(original)
            }
        }
    }
}
