import Foundation

/// Сколько человек тренируется — от этого зависит, какая схема ему подходит.
///
/// Новичок растёт от тренировки к тренировке, опытный — от недели к неделе,
/// и чем дольше стаж, тем мельче шаг прибавки.
enum FullBodyLevel: String, CaseIterable, Identifiable, Codable, Sendable {
    case underYear = "Меньше года"
    case aboutYear = "Около года"
    case overYear = "Больше года"
    case twoYears = "Два года и больше"

    var id: String { rawValue }

    var subtitle: String {
        switch self {
        case .underYear: return "Вес растёт каждую тренировку"
        case .aboutYear: return "Недельная волна: объём, лёгкий, тяжёлый"
        case .overYear: return "Та же волна и больше подсобки"
        case .twoYears: return "Волна с прибавкой через неделю"
        }
    }

    var explanation: String {
        switch self {
        case .underYear:
            return "Линейная прогрессия: присед прибавляет 2,5 кг каждую тренировку, жим — раз в неделю. Пока это работает, ничего сложнее не нужно."
        case .aboutYear:
            return "Техасская волна внутри недели: тяжёлый объём, лёгкий день, один тяжёлый подход. Прибавка 2,5 кг в неделю."
        case .overYear:
            return "Та же волна, но подсобки больше: вертикальный жим и тяга каждый день. Восстановления хватает, объём можно держать выше."
        case .twoYears:
            return "Прибавка через неделю: 2,5 кг раз в две недели. На этом стаже линейно каждую неделю уже не растёт."
        }
    }

    /// Прибавка к тяжёлому подходу на неделе.
    func increment(week: Int) -> Double {
        switch self {
        case .underYear, .aboutYear, .overYear:
            return Double(week - 1) * 2.5
        case .twoYears:
            // Полблина в неделю не набрать: прибавляем 2,5 кг раз в две недели.
            return Double((week - 1) / 2) * 2.5
        }
    }
}

/// Фулбади на три тренировки в неделю: каждый день присед, жим, тяга, руки и кор.
///
/// Прогрессия жима и приседа — линейная, как в техасском методе: расчёт идёт от
/// пятиповторного максимума, тяжёлый подход прибавляет 2,5 кг, лёгкие дни считаются
/// процентами от него. Волны из «Верх / Низ» здесь нет.
enum FullBodyCalculator {
    static let weekCount = 12

    /// Только Equatable: `PlanMemo` большего не требует, а `ProgramInput`
    /// хешируемым не объявлен.
    private struct Key: Equatable {
        let input: ProgramInput
        let level: FullBodyLevel
    }

    private static let memo = PlanMemo<Key, WorkoutPlan>()

    static func generate(input: ProgramInput, level: FullBodyLevel) -> WorkoutPlan {
        memo.resolve(Key(input: input, level: level)) { key in
            WorkoutPlan(
                weeks: (1...weekCount).map { week(($0), input: key.input, level: key.level) },
                isPeaking: false
            )
        }
    }

    private static func week(_ number: Int, input: ProgramInput, level: FullBodyLevel) -> WorkoutWeekPlan {
        let days = level == .underYear
            ? linearWeek(number, input: input)
            : waveWeek(number, input: input, level: level)
        return WorkoutWeekPlan(id: number, number: number, days: days)
    }

    // MARK: - Меньше года: вес растёт каждую тренировку

    private static func linearWeek(_ week: Int, input: ProgramInput) -> [WorkoutDayPlan] {
        // Присед прибавляет каждую тренировку, жим и тяга — раз в неделю:
        // верх тела так быстро не растёт, и упереться в потолок можно за месяц.
        let benchWeight = ProgramCalculator.roundToPlate(input.bench5RM * 0.85 + Double(week - 1) * 2.5)
        let deadWeight = ProgramCalculator.roundToPlate(input.deadlift5RM * 0.85 + Double(week - 1) * 5)

        return (1...3).map { dayNumber in
            let session = (week - 1) * 3 + dayNumber
            let squatWeight = ProgramCalculator.roundToPlate(input.squat5RM * 0.8 + Double(session - 1) * 2.5)

            var exercises = [
                ExercisePrescription(name: "Приседания", sets: 3, reps: "5", load: .kilograms(squatWeight)),
                ExercisePrescription(name: "Жим лёжа", sets: 3, reps: "5", load: .kilograms(benchWeight))
            ]
            // Становая тяжело восстанавливается — один раз в неделю.
            if dayNumber == 2 {
                exercises.append(ExercisePrescription(name: "Становая тяга", sets: 1, reps: "5", load: .kilograms(deadWeight)))
            }
            exercises.append(contentsOf: accessories(input: input, dayNumber: dayNumber, extended: false))
            return day(week, dayNumber, "ФУЛБАДИ " + letter(dayNumber), exercises)
        }
    }

    // MARK: - Год и дальше: недельная волна техасского метода

    private static func waveWeek(_ week: Int, input: ProgramInput, level: FullBodyLevel) -> [WorkoutDayPlan] {
        let increment = level.increment(week: week)
        let squatTop = ProgramCalculator.roundToPlate(input.squat5RM + increment)
        let benchTop = ProgramCalculator.roundToPlate(input.bench5RM + increment)
        let deadTop = ProgramCalculator.roundToPlate(input.deadlift5RM + increment)

        // День объёма — 90 % от тяжёлого, лёгкий день — 80 % от дня объёма.
        let squatVolume = ProgramCalculator.roundToPlate(squatTop * 0.9)
        let benchVolume = ProgramCalculator.roundToPlate(benchTop * 0.9)
        let squatLight = ProgramCalculator.roundToPlate(squatVolume * 0.8)
        let benchLight = ProgramCalculator.roundToPlate(benchVolume * 0.8)

        let extended = level != .aboutYear

        var day1: [ExercisePrescription] = [
            ExercisePrescription(name: "Приседания", sets: 5, reps: "5", load: .kilograms(squatVolume)),
            ExercisePrescription(name: "Жим лёжа", sets: 5, reps: "5", load: .kilograms(benchVolume))
        ]
        day1.append(contentsOf: accessories(input: input, dayNumber: 1, extended: extended))

        var day2: [ExercisePrescription] = [
            ExercisePrescription(name: "Приседания", sets: 2, reps: "5", load: .kilograms(squatLight)),
            ExercisePrescription(name: "Жим лёжа", sets: 3, reps: "5", load: .kilograms(benchLight)),
            ExercisePrescription(name: "Становая тяга", sets: 1, reps: "5", load: .kilograms(deadTop))
        ]
        day2.append(contentsOf: accessories(input: input, dayNumber: 2, extended: extended))

        var day3: [ExercisePrescription] = [
            ExercisePrescription(name: "Приседания", sets: 1, reps: "5", load: .kilograms(squatTop)),
            ExercisePrescription(name: "Жим лёжа", sets: 1, reps: "5", load: .kilograms(benchTop))
        ]
        day3.append(contentsOf: accessories(input: input, dayNumber: 3, extended: extended))

        return [
            day(week, 1, "ФУЛБАДИ · ОБЪЁМ", day1),
            day(week, 2, "ФУЛБАДИ · ЛЁГКИЙ", day2),
            day(week, 3, "ФУЛБАДИ · ТЯЖЁЛЫЙ", day3)
        ]
    }

    // MARK: - Подсобка

    /// Тяга, руки и кор есть в каждой тренировке — иначе это не фулбади, а сплит.
    /// Выбранные в настройках упражнения подставляются вместо стандартных.
    private static func accessories(input: ProgramInput, dayNumber: Int, extended: Bool) -> [ExercisePrescription] {
        var result: [ExercisePrescription] = []

        let pullName = input.back ?? (dayNumber == 2 ? "Тяга в наклоне" : "Подтягивания")
        result.append(ExercisePrescription(name: pullName, sets: 3, reps: "8–12", load: .rpe("RPE 8"), isOptional: true))

        if extended {
            let pressName = input.press ?? "Жим штанги стоя"
            result.append(ExercisePrescription(name: pressName, sets: 3, reps: "6–8", load: .rpe("RPE 8"), isOptional: true))
        }

        let armsName = input.arms ?? "Растянутый суперсет: бицепс + трицепс"
        result.append(ExercisePrescription(name: armsName, sets: 3, reps: "8–12", load: .rpe("RPE 8–9"), isOptional: true))

        let coreName = input.core ?? "Скручивания лёжа на полу"
        result.append(ExercisePrescription(name: coreName, sets: 3, reps: "10–15", load: .rpe("RPE 9"), isOptional: true))

        return result
    }

    private static func letter(_ dayNumber: Int) -> String {
        switch dayNumber {
        case 1: return "A"
        case 2: return "B"
        default: return "C"
        }
    }

    private static func day(_ week: Int, _ number: Int, _ title: String, _ exercises: [ExercisePrescription]) -> WorkoutDayPlan {
        WorkoutDayPlan(id: "\(week)-\(number)", number: number, title: title, exercises: exercises)
    }
}
