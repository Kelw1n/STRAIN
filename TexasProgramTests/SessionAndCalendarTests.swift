import XCTest
@testable import TexasProgram

/// Активная тренировка, подходы сверх плана, отмена сессии, склонения и календарь серии.
final class SessionAndCalendarTests: XCTestCase {

    private func texas() -> ProgramProfile { ProgramProfile(input: .demo) }

    private func squat(_ profile: ProgramProfile, week: Int = 1, day: Int = 1) throws -> ExercisePrescription {
        try XCTUnwrap(profile.workoutPlan.weeks.first { $0.number == week }?
            .days.first { $0.number == day }?.exercises.first { $0.name == "Приседания" })
    }

    override func tearDown() {
        // Менеджер — синглтон: следующая проверка должна начинать с пустой сессии.
        let manager = WorkoutSessionManager.shared
        if manager.isRunning { manager.discardWorkout(profile: texas()) }
        manager.pendingWorkout = nil
        manager.showingReplaceConfirm = false
        super.tearDown()
    }

    // MARK: - Подходы сверх плана

    func testExtraSetBeyondPlanReachesHistory() throws {
        let profile = texas()
        let exercise = try squat(profile)
        let extra = exercise.sets // номер первого подхода сверх плана
        profile.recordSet(week: 1, day: 1, exercise: exercise, index: extra, reps: 3, weight: 90)

        let entries = profile.entries(week: 1, day: 1, exercise: exercise)
        XCTAssertEqual(entries.map(\.index), [extra])
        XCTAssertEqual(profile.history(forExerciseNamed: "Приседания").first?.best, 90)
    }

    // MARK: - Отмена сессии

    func testRestoreKeepsDayCompletedBeforeSession() throws {
        let profile = texas()
        let exercise = try squat(profile)
        profile.recordSet(week: 1, day: 1, exercise: exercise, index: 0, reps: 5, weight: 80)
        profile.toggleCompleted(week: 1, day: 1)
        let before = profile.snapshotDay(week: 1, day: 1)

        // Сессия по закрытому дню: записали ещё подход, потом отменили.
        profile.recordSet(week: 1, day: 1, exercise: exercise, index: 1, reps: 5, weight: 100)
        profile.restoreDay(before, week: 1, day: 1)

        XCTAssertTrue(profile.isCompleted(week: 1, day: 1), "отмена не должна снимать отметку дня")
        XCTAssertEqual(profile.completionLog.filter { $0.week == 1 && $0.day == 1 }.count, 1)
        XCTAssertNil(profile.setEntry(week: 1, day: 1, exercise: exercise, index: 1))
        XCTAssertEqual(profile.setEntry(week: 1, day: 1, exercise: exercise, index: 0)?.weight, 80)
    }

    func testRestoreDoesNotTouchOtherDays() throws {
        let profile = texas()
        let monday = try squat(profile, week: 1, day: 1)
        let nextWeek = try squat(profile, week: 2, day: 1)
        let snapshot = profile.snapshotDay(week: 1, day: 1)

        profile.recordSet(week: 1, day: 1, exercise: monday, index: 0, reps: 5, weight: 80)
        profile.recordSet(week: 2, day: 1, exercise: nextWeek, index: 0, reps: 5, weight: 85)
        profile.restoreDay(snapshot, week: 1, day: 1)

        XCTAssertNil(profile.setEntry(week: 1, day: 1, exercise: monday, index: 0))
        XCTAssertEqual(profile.setEntry(week: 2, day: 1, exercise: nextWeek, index: 0)?.weight, 85)
    }

    // MARK: - Активная тренировка

    func testSessionHasAsManySetsAsPlanned() throws {
        let profile = texas()
        let workout = try XCTUnwrap(profile.schedule.focus)
        let manager = WorkoutSessionManager.shared
        manager.startWorkout(workout: workout, profile: profile)

        let planned = profile.exercises(for: workout).map { max($0.sets, 1) }
        XCTAssertEqual(manager.exercises.map(\.sets.count), planned)
    }

    func testElapsedTimeCountsFromStartNotTicks() throws {
        let profile = texas()
        let manager = WorkoutSessionManager.shared
        manager.startWorkout(workout: try XCTUnwrap(profile.schedule.focus), profile: profile)

        // Телефон пролежал заблокированным 40 минут — таймер за это время не тикал.
        manager.refreshElapsed(now: manager.startDate.addingTimeInterval(40 * 60))
        XCTAssertEqual(manager.elapsedSeconds, 40 * 60)
    }

    func testStartingAnotherWorkoutAsksInsteadOfReplacing() throws {
        let profile = texas()
        let pending = profile.schedule.allPending
        XCTAssertGreaterThan(pending.count, 1)
        let manager = WorkoutSessionManager.shared

        manager.requestStart(workout: pending[0], profile: profile)
        manager.requestStart(workout: pending[1], profile: profile)

        XCTAssertEqual(manager.activeWorkout?.id, pending[0].id, "текущая тренировка не должна пропасть")
        XCTAssertEqual(manager.pendingWorkout?.id, pending[1].id)
        XCTAssertTrue(manager.showingReplaceConfirm)

        manager.confirmReplace(profile: profile)
        XCTAssertEqual(manager.activeWorkout?.id, pending[1].id)
        XCTAssertNil(manager.pendingWorkout)
    }

    func testEditingDoneSetRewritesEntry() throws {
        let profile = texas()
        let workout = try XCTUnwrap(profile.schedule.focus)
        let manager = WorkoutSessionManager.shared
        manager.startWorkout(workout: workout, profile: profile)
        let timer = RestTimer()

        manager.toggleSetDone(exIdx: 0, sIdx: 0, profile: profile, restTimer: timer)
        manager.setWeight(77.5, exIdx: 0, sIdx: 0, profile: profile)
        timer.stop()

        let exercise = manager.exercises[0].prescription
        XCTAssertEqual(profile.setEntry(week: workout.week, day: workout.day.number, exercise: exercise, index: 0)?.weight, 77.5)
    }

    // MARK: - Склонения

    func testRussianPlurals() {
        XCTAssertEqual(RuPlural.sets(1), "1 подход")
        XCTAssertEqual(RuPlural.sets(3), "3 подхода")
        XCTAssertEqual(RuPlural.sets(5), "5 подходов")
        XCTAssertEqual(RuPlural.sets(11), "11 подходов")
        XCTAssertEqual(RuPlural.sets(21), "21 подход")
        XCTAssertEqual(RuPlural.exercises(2), "2 упражнения")
        XCTAssertEqual(RuPlural.exercises(7), "7 упражнений")
    }

    // MARK: - Календарь серии

    func testMonthRowsStartOnMondayAndCoverTheMonth() throws {
        let calendar = WorkoutScheduler.trainingCalendar(Calendar(identifier: .gregorian))
        // 1 октября 2026 — четверг: три пустые клетки перед ним.
        let date = try XCTUnwrap(calendar.date(from: DateComponents(year: 2026, month: 10, day: 12)))
        let rows = WorkoutHubView.monthRows(containing: date, calendar: calendar)

        XCTAssertTrue(rows.allSatisfy { $0.count == 7 })
        XCTAssertEqual(rows.first?.prefix(3).allSatisfy { $0 == nil }, true)
        XCTAssertNotNil(rows.first?[3] ?? nil)
        XCTAssertEqual(rows.joined().compactMap { $0 }.count, 31)
    }
}
