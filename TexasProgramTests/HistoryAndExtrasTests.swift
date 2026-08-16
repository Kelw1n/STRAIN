import UserNotifications
import XCTest
@testable import TexasProgram

/// История движения, вес тела, «всё по плану», выгрузка и напоминания.
final class HistoryAndExtrasTests: XCTestCase {

    private func texas() -> ProgramProfile { ProgramProfile(input: .demo) }

    private func squat(_ profile: ProgramProfile, week: Int = 1, day: Int = 1) throws -> ExercisePrescription {
        try XCTUnwrap(profile.workoutPlan.weeks.first { $0.number == week }?
            .days.first { $0.number == day }?.exercises.first { $0.name == "Приседания" })
    }

    // MARK: - История упражнения

    func testHistoryCollectsOnlyLoggedWorkouts() throws {
        let profile = texas()
        let first = try squat(profile, week: 1, day: 1)
        let second = try squat(profile, week: 2, day: 1)

        profile.recordSet(week: 1, day: 1, exercise: first, index: 0, reps: 5, weight: 80)
        profile.recordSet(week: 2, day: 1, exercise: second, index: 0, reps: 5, weight: 85)

        let history = profile.history(forExerciseNamed: "Приседания")
        XCTAssertEqual(history.count, 2)
        XCTAssertEqual(history.first?.week, 1)
        XCTAssertEqual(history.last?.best, 85)
    }

    func testHistoryPointKnowsBestSetAndTonnage() throws {
        let profile = texas()
        let exercise = try squat(profile)
        profile.recordSet(week: 1, day: 1, exercise: exercise, index: 0, reps: 5, weight: 80)
        profile.recordSet(week: 1, day: 1, exercise: exercise, index: 1, reps: 3, weight: 90)

        let point = try XCTUnwrap(profile.history(forExerciseNamed: "Приседания").first)
        XCTAssertEqual(point.best, 90)
        XCTAssertEqual(point.totalReps, 8)
        XCTAssertEqual(point.tonnage, 5 * 80 + 3 * 90)
    }

    /// План рядом с фактом: по нему на графике видно, добрал человек назначенное или нет.
    func testHistoryCarriesThePlannedWeight() throws {
        let profile = texas()
        let exercise = try squat(profile)
        guard case .kilograms(let planned) = exercise.load else { return XCTFail("присед должен идти в килограммах") }
        profile.recordSet(week: 1, day: 1, exercise: exercise, index: 0, reps: 5, weight: planned - 10)

        let point = try XCTUnwrap(profile.history(forExerciseNamed: "Приседания").first)
        XCTAssertEqual(point.planned, planned)
        XCTAssertEqual(point.best, planned - 10)
    }

    func testLoggedNamesListOnlyExercisesWithEntries() throws {
        let profile = texas()
        XCTAssertTrue(profile.loggedExerciseNames.isEmpty)

        profile.recordSet(week: 1, day: 1, exercise: try squat(profile), index: 0, reps: 5, weight: 80)
        XCTAssertEqual(profile.loggedExerciseNames, ["Приседания"])
    }

    // MARK: - Вес тела

    func testBodyWeightKeepsOneEntryPerDay() {
        let profile = texas()
        let day = Date()

        profile.recordBodyWeight(80, on: day)
        profile.recordBodyWeight(80.5, on: day)

        XCTAssertEqual(profile.bodyWeightLog.count, 1)
        XCTAssertEqual(profile.latestBodyWeight, 80.5)
    }

    func testBodyWeightSeriesIsSortedByDate() throws {
        let profile = texas()
        let today = Date()
        let yesterday = try XCTUnwrap(Calendar.current.date(byAdding: .day, value: -1, to: today))

        profile.recordBodyWeight(81, on: today)
        profile.recordBodyWeight(80, on: yesterday)

        XCTAssertEqual(profile.bodyWeightSeries.map(\.weight), [80, 81])
    }

    func testRelativeStrengthNeedsABodyWeight() {
        let profile = texas()
        XCTAssertNil(profile.relativeStrength(profile.squat5RM))

        profile.recordBodyWeight(80)
        XCTAssertEqual(try XCTUnwrap(profile.relativeStrength(100)), 1.25, accuracy: 0.001)
    }

    // MARK: - Всё по плану

    func testAsPlannedFillsEverySetWithPlannedWeights() throws {
        let profile = texas()
        let exercise = try squat(profile)

        guard case .kilograms(let planned) = exercise.load else { return XCTFail("присед должен идти в килограммах") }

        profile.completeAsPlanned(week: 1, day: 1)

        XCTAssertEqual(profile.completedSets(week: 1, day: 1, exercise: exercise), exercise.sets)
        let entry = try XCTUnwrap(profile.setEntry(week: 1, day: 1, exercise: exercise, index: 0))
        XCTAssertEqual(entry.weight, planned)
        XCTAssertEqual(entry.reps, exercise.plannedReps)
        XCTAssertTrue(profile.isCompleted(week: 1, day: 1))
    }

    /// Уже записанное не перетирается вслепую — вернее, перетирается плановым,
    /// поэтому кнопка и прячется, когда день уже закрыт.
    func testAsPlannedDoesNotToggleAnAlreadyCompletedDay() {
        let profile = texas()
        profile.toggleCompleted(week: 1, day: 1)
        XCTAssertTrue(profile.isCompleted(week: 1, day: 1))

        profile.completeAsPlanned(week: 1, day: 1)
        XCTAssertTrue(profile.isCompleted(week: 1, day: 1))
    }

    /// Диапазон «8-10» — берём нижнюю границу: приписывать верхнюю нечестно.
    func testPlannedRepsTakeTheLowerBoundOfARange() {
        XCTAssertEqual(ExercisePrescription(name: "x", sets: 3, reps: "8-10", load: .kilograms(50)).plannedReps, 8)
        XCTAssertEqual(ExercisePrescription(name: "x", sets: 3, reps: "5", load: .kilograms(50)).plannedReps, 5)
        XCTAssertNil(ExercisePrescription(name: "x", sets: 3, reps: "до отказа", load: .kilograms(50)).plannedReps)
    }

    // MARK: - Прогрессия подсобки

    /// Подсобка из демо-профиля: упражнение с диапазоном повторов и без веса.
    private func accessory(_ profile: ProgramProfile) throws -> (ExercisePrescription, week: Int, day: Int) {
        for week in profile.workoutPlan.weeks {
            for day in week.days {
                if let found = day.exercises.first(where: { $0.needsOwnWeight && $0.topReps != nil }) {
                    return (found, week.number, day.number)
                }
            }
        }
        throw XCTSkip("в демо-программе нет подсобки без веса")
    }

    func testFirstTimeAccessoryAsksForAWeight() throws {
        let profile = texas()
        let (exercise, week, day) = try accessory(profile)

        let hint = try XCTUnwrap(profile.accessoryHint(for: exercise, week: week, day: day))
        XCTAssertNil(hint.weight)
        XCTAssertFalse(hint.isStepUp)
    }

    /// Верх диапазона во всех подходах — вес растёт на шаг.
    func testAccessoryStepsUpWhenTopOfRangeIsHit() throws {
        let profile = texas()
        let (exercise, week, day) = try accessory(profile)
        let top = try XCTUnwrap(exercise.topReps)

        for index in 0..<exercise.sets {
            profile.recordSet(week: week, day: day, exercise: exercise, index: index, reps: top, weight: 20)
        }

        let next = try XCTUnwrap(profile.accessoryHint(for: exercise, week: week + 1, day: day))
        XCTAssertEqual(next.weight, 20 + AccessoryHint.step)
        XCTAssertTrue(next.isStepUp)
    }

    /// Недобрал хоть в одном подходе — вес тот же, растут повторы.
    func testAccessoryHoldsWeightUntilEverySetHitsTheTop() throws {
        let profile = texas()
        let (exercise, week, day) = try accessory(profile)
        let top = try XCTUnwrap(exercise.topReps)

        for index in 0..<exercise.sets {
            profile.recordSet(week: week, day: day, exercise: exercise,
                              index: index, reps: index == 0 ? top : top - 2, weight: 20)
        }

        let next = try XCTUnwrap(profile.accessoryHint(for: exercise, week: week + 1, day: day))
        XCTAssertEqual(next.weight, 20)
        XCTAssertFalse(next.isStepUp)
    }

    /// Незакрытые подходы — не повод прибавлять, даже если в сделанных верх диапазона.
    func testAccessoryNeedsEverySetLogged() throws {
        let profile = texas()
        let (exercise, week, day) = try accessory(profile)
        let top = try XCTUnwrap(exercise.topReps)
        try XCTSkipIf(exercise.sets < 2)

        profile.recordSet(week: week, day: day, exercise: exercise, index: 0, reps: top, weight: 20)

        let next = try XCTUnwrap(profile.accessoryHint(for: exercise, week: week + 1, day: day))
        XCTAssertEqual(next.weight, 20)
        XCTAssertFalse(next.isStepUp)
    }

    func testAccessoryHintIsOffWhenSwitchedOff() throws {
        let profile = texas()
        let (exercise, week, day) = try accessory(profile)
        profile.accessoryProgressionEnabled = false

        XCTAssertNil(profile.accessoryHint(for: exercise, week: week, day: day))
    }

    /// Основные движения ведёт программа — подсказка им не нужна.
    func testMainLiftsHaveNoAccessoryHint() throws {
        let profile = texas()
        XCTAssertNil(profile.accessoryHint(for: try squat(profile), week: 1, day: 1))
    }

    /// «Всё по плану» на подсобке берёт вес прогрессии, а не пропускает её.
    func testAsPlannedUsesTheAccessoryWeight() throws {
        let profile = texas()
        let (exercise, week, day) = try accessory(profile)
        let top = try XCTUnwrap(exercise.topReps)
        for index in 0..<exercise.sets {
            profile.recordSet(week: week, day: day, exercise: exercise, index: index, reps: top, weight: 20)
        }

        profile.completeAsPlanned(week: week + 1, day: day)

        let entry = try XCTUnwrap(profile.setEntry(week: week + 1, day: day, exercise: exercise, index: 0))
        XCTAssertEqual(entry.weight, 20 + AccessoryHint.step)
    }

    // MARK: - История по старым отметкам

    func testBackfillFillsCompletedDaysWithPlannedWeights() throws {
        let profile = texas()
        let exercise = try squat(profile)
        guard case .kilograms(let planned) = exercise.load else { return XCTFail("присед должен идти в килограммах") }

        profile.toggleCompleted(week: 1, day: 1)
        XCTAssertEqual(profile.backfillableWorkouts, 1)

        XCTAssertEqual(profile.backfillHistoryFromMarks(), 1)

        let entry = try XCTUnwrap(profile.setEntry(week: 1, day: 1, exercise: exercise, index: 0))
        XCTAssertEqual(entry.weight, planned)
        XCTAssertEqual(profile.entries(week: 1, day: 1, exercise: exercise).count, exercise.sets)
        // Заполнять больше нечего — предложение уходит.
        XCTAssertEqual(profile.backfillableWorkouts, 0)
    }

    /// Настоящие числа человека дороже плановых — их не трогаем.
    func testBackfillNeverOverwritesRealEntries() throws {
        let profile = texas()
        let exercise = try squat(profile)
        profile.recordSet(week: 1, day: 1, exercise: exercise, index: 0, reps: 3, weight: 60)
        profile.toggleCompleted(week: 1, day: 1)

        profile.backfillHistoryFromMarks()

        let entry = try XCTUnwrap(profile.setEntry(week: 1, day: 1, exercise: exercise, index: 0))
        XCTAssertEqual(entry.weight, 60)
        XCTAssertEqual(entry.reps, 3)
    }

    /// Неотмеченный день выдумывать нечего.
    func testBackfillIgnoresUntouchedDays() {
        let profile = texas()
        XCTAssertEqual(profile.backfillableWorkouts, 0)
        XCTAssertEqual(profile.backfillHistoryFromMarks(), 0)
        XCTAssertTrue(profile.setLog.isEmpty)
    }

    /// Половина точек — половина записей: приписывать недоделанное нельзя.
    func testBackfillFollowsPartialDots() throws {
        let profile = texas()
        let exercise = try squat(profile)
        _ = profile.toggleSet(week: 1, day: 1, exercise: exercise, index: 1)
        XCTAssertEqual(profile.completedSets(week: 1, day: 1, exercise: exercise), 2)

        profile.backfillHistoryFromMarks()

        XCTAssertEqual(profile.entries(week: 1, day: 1, exercise: exercise).count, 2)
    }

    // MARK: - Выгрузка

    func testCSVHasHeaderAndOneRowPerSet() throws {
        let profile = texas()
        let exercise = try squat(profile)
        profile.recordSet(week: 1, day: 1, exercise: exercise, index: 0, reps: 5, weight: 80)
        profile.recordSet(week: 1, day: 1, exercise: exercise, index: 1, reps: 5, weight: 82.5)

        let rows = CSVExport.text(for: [profile]).split(separator: "\r\n")
        XCTAssertEqual(rows.count, 3)
        XCTAssertTrue(rows[0].hasPrefix("Профиль;"))
        // Русская локаль Excel ждёт запятую в дробях, иначе вес станет текстом.
        XCTAssertTrue(rows[2].contains(";82,5;"), String(rows[2]))
    }

    func testCSVQuotesNamesWithTheSeparator() throws {
        let profile = texas()
        profile.name = "Профиль; основной"
        profile.recordSet(week: 1, day: 1, exercise: try squat(profile), index: 0, reps: 5, weight: 80)

        let text = CSVExport.text(for: [profile])
        XCTAssertTrue(text.contains("\"Профиль; основной\""), text)
    }

    // MARK: - Напоминания

    func testRemindersAreScheduledForEveryTrainingDay() {
        let profile = texas()
        XCTAssertTrue(ReminderScheduler.plan(for: profile).isEmpty)

        profile.reminderEnabled = true
        profile.reminderHour = 19
        profile.reminderMinute = 30

        let requests = ReminderScheduler.plan(for: profile)
        XCTAssertEqual(requests.count, profile.weekdays.count)

        let trigger = requests.first?.trigger as? UNCalendarNotificationTrigger
        XCTAssertEqual(trigger?.dateComponents.hour, 19)
        XCTAssertEqual(trigger?.dateComponents.minute, 30)
        XCTAssertTrue(trigger?.repeats ?? false)
    }
}
