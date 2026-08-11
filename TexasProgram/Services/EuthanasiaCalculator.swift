import Foundation

/// Три базовых движения «Эвтаназии худобы». Упражнение выбирается один раз
/// на весь цикл и тестируется перед началом.
enum EuthanasiaPattern: String, CaseIterable, Identifiable, Codable, Sendable {
    case pull = "Тяга"
    case squat = "Присед"
    case press = "Жим"

    var id: String { rawValue }

    /// Количество подъёмов штанги в блоке на плотность.
    var totalReps: Int {
        switch self {
        case .pull: return 45
        case .squat: return 40
        case .press: return 50
        }
    }

    var options: [String] {
        switch self {
        case .pull: return ["Тяга в наклоне", "Тяга Пендли", "Подтягивания"]
        case .squat: return ["Присед Зерчера", "Фронтальный присед", "Присед классический", "Присед с гирей", "Присед в колодец"]
        case .press: return ["Отжимания на брусьях", "Жим на наклонной", "Жим классический"]
        }
    }

    /// Ориентир по времени теста — из инструкции.
    var testHint: String {
        switch self {
        case .pull: return "обычно 15–25 минут"
        case .squat: return "обычно 25–30 минут"
        case .press: return "обычно 30–35 минут"
        }
    }
}

/// Данные одного движения: выбранное упражнение и два теста.
struct EuthanasiaMovement: Codable, Equatable, Hashable, Sendable {
    var exercise: String
    /// Один повторный максимум.
    var oneRepMax: Double
    /// За сколько минут пройдено КПШ на 75 % от максимума.
    var testMinutes: Double
}

/// Уровень сложности: отличается шагом уплотнения.
enum EuthanasiaLevel: String, CaseIterable, Identifiable, Codable, Sendable {
    case beginner = "Начальный"
    case medium = "Средний"
    case pro = "Профи"

    var id: String { rawValue }

    var subtitle: String {
        switch self {
        case .beginner: return "К седьмой инверсии время сжимается до 60 %"
        case .medium: return "До 50 % — подойдёт большинству"
        case .pro: return "До 40 % — программа проверит, насколько ты крут"
        }
    }

    /// Насколько сокращается время за одну инверсию.
    var step: Double {
        switch self {
        case .beginner: return 0.0571
        case .medium: return 0.0714
        case .pro: return 0.0857
        }
    }

    /// Доля исходного времени теста на этой инверсии.
    func timeFactor(inversion: Int) -> Double {
        max(1 - Double(inversion) * step, 0.1)
    }
}

/// Вход программы: три движения и уровень.
struct EuthanasiaInput: Equatable, Codable, Sendable {
    var pull: EuthanasiaMovement
    var squat: EuthanasiaMovement
    var press: EuthanasiaMovement
    var level: EuthanasiaLevel

    func movement(_ pattern: EuthanasiaPattern) -> EuthanasiaMovement {
        switch pattern {
        case .pull: return pull
        case .squat: return squat
        case .press: return press
        }
    }

    static let demo = EuthanasiaInput(
        pull: EuthanasiaMovement(exercise: "Тяга в наклоне", oneRepMax: 100, testMinutes: 20),
        squat: EuthanasiaMovement(exercise: "Присед классический", oneRepMax: 140, testMinutes: 28),
        press: EuthanasiaMovement(exercise: "Жим классический", oneRepMax: 100, testMinutes: 32),
        level: .medium
    )
}

/// «Эвтаназия худобы»: восемь недель, три тренировки, три базовых движения.
///
/// Первая неделя — тестовая, дальше семь «инверсий». Силовая часть циклится
/// 3×5 → 3×3 → волна 5/3/1, а блок на плотность держит одно и то же количество
/// повторений при сокращающемся времени: прогрессирует не вес, а плотность.
enum EuthanasiaCalculator {
    /// Тестовая неделя плюс семь инверсий.
    static let weekCount = 8

    private static let memo = PlanMemo<EuthanasiaInput, WorkoutPlan>()

    static func generate(input: EuthanasiaInput) -> WorkoutPlan {
        memo.resolve(input) { value in
            WorkoutPlan(weeks: (1...weekCount).map { week($0, input: value) }, isPeaking: false)
        }
    }

    /// Проценты силовой части и блока плотности по инверсиям 1…7.
    private static func strength(inversion: Int) -> (scheme: [(sets: Int, reps: String, percent: Double)], density: Double) {
        switch inversion {
        case 1, 4:
            return ([(3, "5", 0.65)], 0.55)
        case 2, 5:
            return ([(3, "3", 0.70)], 0.60)
        case 3, 6:
            return ([(1, "5", 0.65), (1, "3", 0.75), (1, "1", 0.85)], 0.65)
        default:
            // Седьмая инверсия: силовая как в первой, а плотность идёт
            // на тестовых 75 % — это и есть финальная проверка.
            return ([(3, "5", 0.65)], 0.75)
        }
    }

    private static func week(_ number: Int, input: EuthanasiaInput) -> WorkoutWeekPlan {
        let inversion = number - 1
        let days = EuthanasiaPattern.allCases.enumerated().map { index, pattern in
            day(number, index + 1, pattern: pattern, inversion: inversion, input: input)
        }
        return WorkoutWeekPlan(id: number, number: number, days: days)
    }

    private static func day(_ week: Int, _ number: Int, pattern: EuthanasiaPattern, inversion: Int, input: EuthanasiaInput) -> WorkoutDayPlan {
        let movement = input.movement(pattern)
        var exercises: [ExercisePrescription] = []

        if inversion == 0 {
            // Инверсия 0 — неделя тестов: сначала максимум, потом время на КПШ.
            exercises.append(ExercisePrescription(
                id: "test-max-" + pattern.rawValue,
                name: movement.exercise + " · тест 1ПМ",
                sets: 1,
                reps: "1",
                load: .testOneRepMax
            ))
            exercises.append(ExercisePrescription(
                id: "test-time-" + pattern.rawValue,
                name: movement.exercise + " · тест на время",
                sets: 0,
                reps: "\(pattern.totalReps) повторений",
                load: .repRange(WeightFormat.kilograms(ProgramCalculator.roundToPlate(movement.oneRepMax * 0.75)) + " · засеки время, " + pattern.testHint)
            ))
        } else {
            let table = strength(inversion: inversion)
            for (index, item) in table.scheme.enumerated() {
                exercises.append(ExercisePrescription(
                    id: table.scheme.count > 1 ? "main-\(pattern.rawValue)-\(index)" : nil,
                    name: movement.exercise,
                    sets: item.sets,
                    reps: item.reps,
                    load: .kilograms(ProgramCalculator.roundToPlate(movement.oneRepMax * item.percent))
                ))
            }
            // Блок плотности: повторов столько же, времени меньше.
            let minutes = movement.testMinutes * input.level.timeFactor(inversion: inversion)
            exercises.append(ExercisePrescription(
                id: "density-" + pattern.rawValue,
                name: movement.exercise + " · плотность",
                sets: 0,
                reps: "\(pattern.totalReps) повторений",
                load: .repRange(WeightFormat.kilograms(ProgramCalculator.roundToPlate(movement.oneRepMax * table.density)) + " · за " + timeText(minutes))
            ))
        }

        exercises.append(contentsOf: accessories(pattern: pattern, inversion: inversion))
        let title = inversion == 0
            ? "ТЕСТ · " + pattern.rawValue.uppercased()
            : "ИНВЕРСИЯ \(inversion) · " + pattern.rawValue.uppercased()
        return WorkoutDayPlan(id: "\(week)-\(number)", number: number, title: title, exercises: exercises)
    }

    /// «22:30» вместо «22.5 мин» — так читается с секундомера.
    static func timeText(_ minutes: Double) -> String {
        let total = Int((minutes * 60).rounded())
        return String(format: "%d:%02d", total / 60, total % 60)
    }

    // MARK: - Подсобка

    /// Четыре подсобных на каждый день. Схема одинаковая для всех, меняется
    /// только по инверсиям: объём растёт, седьмая идёт разгрузочной.
    private static let accessoryPlan: [(sets: Int, reps: String, rpe: String)] = [
        (3, "12", "RPE 6,5"),
        (4, "10", "RPE 7"),
        (3, "8", "RPE 7,5"),
        (4, "10", "RPE 7,5"),
        (5, "8", "RPE 8"),
        (5, "6", "RPE 8,5"),
        (5, "8", "RPE 9"),
        (3, "10", "RPE 7")
    ]

    private static func accessoryNames(_ pattern: EuthanasiaPattern) -> [String] {
        switch pattern {
        case .pull:
            return [
                "Суперсет: разгибание голени сидя в тренажёре U + подъем на бицепс молоток U",
                "Суперсет: жим гантели стоя U + сгибание голени сидя/лёжа в тренажёре",
                "Разведение гантели на горизонтальной скамье на грудь U",
                "Суперсет: подъем на носки стоя U + молитва в блоке на пресс"
            ]
        case .squat:
            return [
                "Суперсет: тяга горизонтальная удобной рукояткой + мах гантели в сторону U",
                "Суперсет: сведение ног в тренажёре + разведение ног в тренажёре",
                "Вертикальная тяга нейтральным средне-широким хватом",
                "Разгибание на трицепс с удобной рукояткой в блоке U"
            ]
        case .press:
            return [
                "Суперсет: румынская тяга с гирей U + подъем штанги на бицепс",
                "Суперсет: мертвая тяга в стороны + отведение на заднюю дельту (тренажёр/гантели)",
                "Подъем на носки сидя",
                "Копенгагенская планка"
            ]
        }
    }

    private static func accessories(pattern: EuthanasiaPattern, inversion: Int) -> [ExercisePrescription] {
        let index = min(max(inversion, 0), accessoryPlan.count - 1)
        let plan = accessoryPlan[index]
        return accessoryNames(pattern).map { name in
            ExercisePrescription(
                name: name,
                sets: plan.sets,
                reps: plan.reps,
                load: .rpe(plan.rpe),
                isOptional: true
            )
        }
    }
}
