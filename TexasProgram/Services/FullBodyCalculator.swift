import Foundation

/// Объём и состав тренировки для конкретного стажа.
///
/// Различия между уровнями держим числами в одном месте, а не ветками по коду:
/// так видно всю таблицу разом и её можно проверить тестом.
struct FullBodyVolume: Equatable, Sendable {
    /// Подходы основных движений в дни объёма, лёгкий и тяжёлый.
    let volumeSets: Int
    let lightSets: Int
    let topSets: Int
    /// Добивочные подходы после тяжёлого — 90 % от рабочего.
    let backoffSets: Int
    let deadliftSets: Int
    /// Подсобка. Ноль означает, что слот на этом стаже не используется.
    let pullSets: Int
    let extraPullSets: Int
    let verticalPressSets: Int
    let armSets: Int
    let coreSets: Int
}

/// Сколько человек тренируется — от этого зависит и объём, и набор упражнений.
enum FullBodyLevel: String, CaseIterable, Identifiable, Codable, Sendable {
    case underYear = "Меньше года"
    case aboutYear = "Около года"
    case overYear = "Больше года"
    case twoYears = "Два года и больше"

    var id: String { rawValue }

    /// Растёт ли вес от тренировки к тренировке, а не от недели к неделе.
    var isLinear: Bool { self == .underYear }

    var subtitle: String {
        switch self {
        case .underYear: return "Минимум объёма, вес растёт каждую тренировку"
        case .aboutYear: return "Недельная волна, средний объём"
        case .overYear: return "Больше подходов, добивка и вертикальный жим"
        case .twoYears: return "Максимальный объём, прибавка через неделю"
        }
    }

    var explanation: String {
        switch self {
        case .underYear:
            return "Три подхода в основных движениях, из подсобки только тяга, руки и кор по два подхода. Присед прибавляет 2,5 кг каждую тренировку, жим — раз в неделю. Пока растёт так, усложнять нечего."
        case .aboutYear:
            return "Техасская волна внутри недели: пять подходов в день объёма, лёгкий день, один тяжёлый подход. Подсобка по три подхода. Прибавка 2,5 кг в неделю."
        case .overYear:
            return "Тот же каркас, но объёма больше: четыре подхода в тяге, добивка после тяжёлого подхода, добавляются вертикальный жим и дополнительная тяга."
        case .twoYears:
            return "Шесть подходов в день объёма, две добивки, подсобка по четыре подхода. Прибавка 2,5 кг раз в две недели: линейно каждую неделю на этом стаже уже не растёт."
        }
    }

    /// Прибавка к тяжёлому подходу на этой неделе.
    func increment(week: Int) -> Double {
        switch self {
        case .underYear, .aboutYear, .overYear:
            return Double(week - 1) * 2.5
        case .twoYears:
            // Полблина в неделю не набрать: прибавляем 2,5 кг раз в две недели.
            return Double((week - 1) / 2) * 2.5
        }
    }

    var volume: FullBodyVolume {
        switch self {
        case .underYear:
            return FullBodyVolume(volumeSets: 3, lightSets: 3, topSets: 3, backoffSets: 0, deadliftSets: 1,
                                  pullSets: 2, extraPullSets: 0, verticalPressSets: 0, armSets: 2, coreSets: 2)
        case .aboutYear:
            return FullBodyVolume(volumeSets: 5, lightSets: 2, topSets: 1, backoffSets: 0, deadliftSets: 1,
                                  pullSets: 3, extraPullSets: 0, verticalPressSets: 0, armSets: 3, coreSets: 3)
        case .overYear:
            return FullBodyVolume(volumeSets: 5, lightSets: 3, topSets: 1, backoffSets: 1, deadliftSets: 1,
                                  pullSets: 4, extraPullSets: 3, verticalPressSets: 3, armSets: 3, coreSets: 3)
        case .twoYears:
            return FullBodyVolume(volumeSets: 6, lightSets: 3, topSets: 1, backoffSets: 2, deadliftSets: 2,
                                  pullSets: 4, extraPullSets: 3, verticalPressSets: 4, armSets: 4, coreSets: 3)
        }
    }

    /// Какие слоты подсобки предлагать при настройке. Остальные на этом стаже
    /// в план не попадут, и спрашивать их незачем.
    var accessorySlots: [AdditionalExerciseCategory] {
        var result: [AdditionalExerciseCategory] = [.back]
        if volume.extraPullSets > 0 { result.append(.pull) }
        if volume.verticalPressSets > 0 { result.append(.press) }
        result.append(contentsOf: [.arms, .core])
        return result
    }
}

/// Фулбади на три тренировки в неделю: каждый день присед, жим, тяга, руки и кор.
///
/// Прогрессия линейная, как в техасском методе: расчёт от пятиповторного максимума,
/// тяжёлый подход прибавляет 2,5 кг, лёгкие дни — проценты от него. Стаж меняет
/// объём и набор упражнений, а не только шаг прибавки.
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
                weeks: (1...weekCount).map { week($0, input: key.input, level: key.level) },
                isPeaking: false
            )
        }
    }

    private static func week(_ number: Int, input: ProgramInput, level: FullBodyLevel) -> WorkoutWeekPlan {
        let days = level.isLinear
            ? linearWeek(number, input: input, level: level)
            : waveWeek(number, input: input, level: level)
        return WorkoutWeekPlan(id: number, number: number, days: days)
    }

    // MARK: - Меньше года: вес растёт каждую тренировку

    private static func linearWeek(_ week: Int, input: ProgramInput, level: FullBodyLevel) -> [WorkoutDayPlan] {
        let volume = level.volume
        // Присед прибавляет каждую тренировку, жим и тяга — раз в неделю:
        // верх тела так быстро не растёт, и упереться в потолок можно за месяц.
        let benchWeight = ProgramCalculator.roundToPlate(input.bench5RM * 0.85 + Double(week - 1) * 2.5)
        let deadWeight = ProgramCalculator.roundToPlate(input.deadlift5RM * 0.85 + Double(week - 1) * 5)

        return (1...3).map { dayNumber in
            let session = (week - 1) * 3 + dayNumber
            let squatWeight = ProgramCalculator.roundToPlate(input.squat5RM * 0.8 + Double(session - 1) * 2.5)

            var exercises = [
                ExercisePrescription(name: "Приседания", sets: volume.volumeSets, reps: "5", load: .kilograms(squatWeight)),
                ExercisePrescription(name: "Жим лёжа", sets: volume.volumeSets, reps: "5", load: .kilograms(benchWeight))
            ]
            // Становая тяжело восстанавливается — один раз в неделю.
            if dayNumber == 2 {
                exercises.append(ExercisePrescription(name: "Становая тяга", sets: volume.deadliftSets, reps: "5", load: .kilograms(deadWeight)))
            }
            exercises.append(contentsOf: accessories(input: input, volume: volume, dayNumber: dayNumber))
            return day(week, dayNumber, "ФУЛБАДИ " + letter(dayNumber), exercises)
        }
    }

    // MARK: - Год и дальше: недельная волна техасского метода

    private static func waveWeek(_ week: Int, input: ProgramInput, level: FullBodyLevel) -> [WorkoutDayPlan] {
        let volume = level.volume
        let increment = level.increment(week: week)
        let squatTop = ProgramCalculator.roundToPlate(input.squat5RM + increment)
        let benchTop = ProgramCalculator.roundToPlate(input.bench5RM + increment)
        let deadTop = ProgramCalculator.roundToPlate(input.deadlift5RM + increment)

        // День объёма — 90 % от тяжёлого, лёгкий день — 80 % от дня объёма.
        let squatVolume = ProgramCalculator.roundToPlate(squatTop * 0.9)
        let benchVolume = ProgramCalculator.roundToPlate(benchTop * 0.9)
        let squatLight = ProgramCalculator.roundToPlate(squatVolume * 0.8)
        let benchLight = ProgramCalculator.roundToPlate(benchVolume * 0.8)

        var day1: [ExercisePrescription] = [
            ExercisePrescription(name: "Приседания", sets: volume.volumeSets, reps: "5", load: .kilograms(squatVolume)),
            ExercisePrescription(name: "Жим лёжа", sets: volume.volumeSets, reps: "5", load: .kilograms(benchVolume))
        ]
        day1.append(contentsOf: accessories(input: input, volume: volume, dayNumber: 1))

        var day2: [ExercisePrescription] = [
            ExercisePrescription(name: "Приседания", sets: volume.lightSets, reps: "5", load: .kilograms(squatLight)),
            ExercisePrescription(name: "Жим лёжа", sets: volume.lightSets, reps: "5", load: .kilograms(benchLight)),
            ExercisePrescription(name: "Становая тяга", sets: volume.deadliftSets, reps: "5", load: .kilograms(deadTop))
        ]
        day2.append(contentsOf: accessories(input: input, volume: volume, dayNumber: 2))

        var day3: [ExercisePrescription] = [
            ExercisePrescription(name: "Приседания", sets: volume.topSets, reps: "5", load: .kilograms(squatTop)),
            ExercisePrescription(name: "Жим лёжа", sets: volume.topSets, reps: "5", load: .kilograms(benchTop))
        ]
        // Добивка идёт отдельным упражнением: у неё своё имя и свой идентификатор,
        // иначе она слилась бы с рабочим подходом в отметках и в истории.
        if volume.backoffSets > 0 {
            day3.append(ExercisePrescription(
                id: "squat-backoff",
                name: "Приседания · добивка",
                sets: volume.backoffSets,
                reps: "5",
                load: .kilograms(ProgramCalculator.roundToPlate(squatTop * 0.9))
            ))
            day3.append(ExercisePrescription(
                id: "bench-backoff",
                name: "Жим лёжа · добивка",
                sets: volume.backoffSets,
                reps: "5",
                load: .kilograms(ProgramCalculator.roundToPlate(benchTop * 0.9))
            ))
        }
        day3.append(contentsOf: accessories(input: input, volume: volume, dayNumber: 3))

        return [
            day(week, 1, "ФУЛБАДИ · ОБЪЁМ", day1),
            day(week, 2, "ФУЛБАДИ · ЛЁГКИЙ", day2),
            day(week, 3, "ФУЛБАДИ · ТЯЖЁЛЫЙ", day3)
        ]
    }

    // MARK: - Подсобка

    /// Тяга, руки и кор есть в каждой тренировке — иначе это не фулбади, а сплит.
    /// Число подходов и набор слотов задаёт стаж, названия — выбор в настройках.
    private static func accessories(input: ProgramInput, volume: FullBodyVolume, dayNumber: Int) -> [ExercisePrescription] {
        var result: [ExercisePrescription] = []

        if volume.pullSets > 0 {
            let name = input.back ?? (dayNumber == 2 ? "Тяга в наклоне" : "Подтягивания")
            result.append(ExercisePrescription(name: name, sets: volume.pullSets, reps: "8–12", load: .rpe("RPE 8"), isOptional: true))
        }
        // Дополнительная тяга — только в лёгкий день: в остальные уже есть становая
        // или тяжёлый присед, и спина не вывезет.
        if volume.extraPullSets > 0, dayNumber == 2 {
            let name = input.pull ?? "Румынская тяга"
            result.append(ExercisePrescription(name: name, sets: volume.extraPullSets, reps: "5–8", load: .rpe("RPE 8"), isOptional: true))
        }
        if volume.verticalPressSets > 0 {
            let name = input.press ?? "Жим штанги стоя"
            result.append(ExercisePrescription(name: name, sets: volume.verticalPressSets, reps: "6–8", load: .rpe("RPE 8"), isOptional: true))
        }
        if volume.armSets > 0 {
            let name = input.arms ?? "Растянутый суперсет: бицепс + трицепс"
            result.append(ExercisePrescription(name: name, sets: volume.armSets, reps: "8–12", load: .rpe("RPE 8–9"), isOptional: true))
        }
        if volume.coreSets > 0 {
            let name = input.core ?? "Скручивания лёжа на полу"
            result.append(ExercisePrescription(name: name, sets: volume.coreSets, reps: "10–15", load: .rpe("RPE 9"), isOptional: true))
        }
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
