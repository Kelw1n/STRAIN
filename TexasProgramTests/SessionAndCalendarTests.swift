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
        manager.clearSaved()
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

    /// Подходы, отмеченные до старта, приходят в тренировку отмеченными, а не пустыми.
    func testSessionPicksUpSetsLoggedBeforeStart() throws {
        let profile = texas()
        let manager = WorkoutSessionManager.shared
        let workout = try XCTUnwrap(profile.schedule.focus)
        let first = try XCTUnwrap(profile.exercises(for: workout).first { $0.sets > 0 })
        profile.recordSet(week: workout.week, day: workout.day.number, exercise: first, index: 0, reps: 4, weight: 77.5)

        manager.startWorkout(workout: workout, profile: profile)
        let state = try XCTUnwrap(manager.exercises.first { $0.prescription.id == first.id })
        XCTAssertTrue(state.sets[0].isDone)
        XCTAssertEqual(state.sets[0].weight, 77.5)
        XCTAssertEqual(state.sets[0].reps, 4)
        XCTAssertFalse(state.sets.dropFirst().contains(where: \.isDone))
    }

    /// Сменили профиль посреди тренировки — в новый ничего не пишется,
    /// а вернулись — тренировка снова на месте.
    func testSwitchingProfileDetachesSession() throws {
        let owner = texas()
        let other = texas()
        let manager = WorkoutSessionManager.shared
        let workout = try XCTUnwrap(owner.schedule.focus)
        manager.startWorkout(workout: workout, profile: owner)

        manager.detachIfOtherProfile(other)
        XCTAssertFalse(manager.isRunning)

        manager.restoreIfNeeded(for: owner)
        XCTAssertEqual(manager.activeWorkout?.id, workout.id)
    }

    func testElapsedTimeCountsFromStartNotTicks() throws {
        let profile = texas()
        let manager = WorkoutSessionManager.shared
        manager.startWorkout(workout: try XCTUnwrap(profile.schedule.focus), profile: profile)

        // Телефон пролежал заблокированным 40 минут — таймер за это время не тикал.
        XCTAssertEqual(manager.elapsed(at: manager.startDate.addingTimeInterval(40 * 60)), 40 * 60)
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

    /// Тренировка, где у первого упражнения несколько подходов: ближайшая по дате
    /// зависит от дня недели, и в силовой день там один подход — удалять нечего.
    private func workoutWithSeveralSets(_ profile: ProgramProfile) throws -> ScheduledWorkout {
        try XCTUnwrap(profile.schedule.allPending.first { (profile.exercises(for: $0).first?.sets ?? 0) > 1 })
    }

    func testAddSetAfterDeleteGetsFreshIndex() throws {
        let profile = texas()
        let manager = WorkoutSessionManager.shared
        manager.startWorkout(workout: try workoutWithSeveralSets(profile), profile: profile)
        let before = manager.exercises[0].sets.map(\.index)
        XCTAssertGreaterThan(before.count, 1)

        manager.deleteSet(exIdx: 0, sIdx: 0, profile: profile)
        manager.addSet(to: 0, profile: profile)

        let indexes = manager.exercises[0].sets.map(\.index)
        XCTAssertEqual(Set(indexes).count, indexes.count, "номера подходов не должны повторяться")
        XCTAssertEqual(indexes.last, (before.max() ?? 0) + 1)
    }

    func testDeletingDoneSetClearsItsEntry() throws {
        let profile = texas()
        let workout = try workoutWithSeveralSets(profile)
        let manager = WorkoutSessionManager.shared
        manager.startWorkout(workout: workout, profile: profile)
        let timer = RestTimer()
        manager.toggleSetDone(exIdx: 0, sIdx: 0, profile: profile, restTimer: timer)
        timer.stop()
        let exercise = manager.exercises[0].prescription
        XCTAssertNotNil(profile.setEntry(week: workout.week, day: workout.day.number, exercise: exercise, index: 0))

        manager.deleteSet(exIdx: 0, sIdx: 0, profile: profile)
        XCTAssertNil(profile.setEntry(week: workout.week, day: workout.day.number, exercise: exercise, index: 0))
    }

    func testLastSetCannotBeDeleted() throws {
        let profile = texas()
        let manager = WorkoutSessionManager.shared
        manager.startWorkout(workout: try XCTUnwrap(profile.schedule.focus), profile: profile)
        while manager.exercises[0].sets.count > 1 { manager.deleteSet(exIdx: 0, sIdx: 0, profile: profile) }
        manager.deleteSet(exIdx: 0, sIdx: 0, profile: profile)
        XCTAssertEqual(manager.exercises[0].sets.count, 1)
    }

    func testMoveExerciseKeepsEveryExercise() throws {
        let profile = texas()
        let manager = WorkoutSessionManager.shared
        // Тренировка, где упражнений хватает для перестановки: ближайшая по дате
        // зависит от дня недели, и по вторникам в ней их меньше трёх.
        let workout = try XCTUnwrap(profile.schedule.allPending.first { profile.exercises(for: $0).count > 2 })
        manager.startWorkout(workout: workout, profile: profile)
        let ids = manager.exercises.map(\.id)
        XCTAssertGreaterThan(ids.count, 2)

        manager.moveExercise(from: 0, to: 2)
        XCTAssertEqual(manager.exercises[2].id, ids[0])
        XCTAssertEqual(Set(manager.exercises.map(\.id)), Set(ids))
    }

    func testApplyPreviousCopiesLastTime() throws {
        let profile = texas()
        let pending = profile.schedule.allPending
        let first = try XCTUnwrap(pending.first)
        let manager = WorkoutSessionManager.shared
        // Прошлая тренировка этого движения.
        let squat = try XCTUnwrap(profile.exercises(for: first).first { $0.name == "Приседания" })
        let other = try XCTUnwrap(pending.dropFirst().first { profile.exercises(for: $0).contains { $0.name == "Приседания" } })
        profile.recordSet(week: first.week, day: first.day.number, exercise: squat, index: 0, reps: 4, weight: 92.5)
        profile.toggleCompleted(week: first.week, day: first.day.number)

        manager.startWorkout(workout: other, profile: profile)
        let exIdx = try XCTUnwrap(manager.exercises.firstIndex { $0.prescription.name == "Приседания" })
        XCTAssertEqual(manager.exercises[exIdx].sets[0].previous?.weight, 92.5)

        manager.applyPrevious(exIdx: exIdx, sIdx: 0, profile: profile)
        XCTAssertEqual(manager.exercises[exIdx].sets[0].weight, 92.5)
        XCTAssertEqual(manager.exercises[exIdx].sets[0].reps, 4)
    }

    func testSessionSurvivesRelaunch() throws {
        let profile = texas()
        let workout = try XCTUnwrap(profile.schedule.focus)
        let manager = WorkoutSessionManager.shared
        manager.startWorkout(workout: workout, profile: profile)
        manager.setWeight(81, exIdx: 0, sIdx: 0, profile: profile)
        manager.moveExercise(from: 0, to: 1)
        manager.writeNow()
        let saved = manager.exercises

        // Как будто приложение выгрузили: память пустая, файл остался.
        manager.forgetForTesting()
        XCTAssertFalse(manager.isRunning)
        manager.restoreIfNeeded(for: profile)

        XCTAssertEqual(manager.activeWorkout?.id, workout.id)
        XCTAssertEqual(manager.exercises, saved)
        XCTAssertFalse(manager.isPresented, "поднятая тренировка открывается свёрнутой")
    }

    func testSavedSessionOfAnotherProfileIsIgnored() throws {
        let profile = texas()
        let manager = WorkoutSessionManager.shared
        manager.startWorkout(workout: try XCTUnwrap(profile.schedule.focus), profile: profile)
        manager.writeNow()
        manager.forgetForTesting()

        manager.restoreIfNeeded(for: texas())
        XCTAssertFalse(manager.isRunning)
    }

    // MARK: - Перестановка

    func testReorderShiftsNeighboursTowardsTheGap() {
        let reorder = ExerciseReorder()
        let step = ExerciseReorder.rowHeight + ExerciseReorder.spacing
        reorder.begin(id: "b", index: 1)
        // Потянули вниз на две строки с хвостиком — место третье по счёту (индекс 3).
        reorder.update(translation: step * 2.2, count: 5)

        XCTAssertEqual(reorder.offset(for: "b", index: 1), step * 2.2, accuracy: 0.001, "схваченная едет за пальцем")
        XCTAssertEqual(reorder.offset(for: "a", index: 0), 0)
        XCTAssertEqual(reorder.offset(for: "c", index: 2), -step, "соседи между старым и новым местом поднимаются")
        XCTAssertEqual(reorder.offset(for: "d", index: 3), -step)
        XCTAssertEqual(reorder.offset(for: "e", index: 4), 0)
    }

    func testReorderTargetIsClampedToList() {
        let reorder = ExerciseReorder()
        let step = ExerciseReorder.rowHeight + ExerciseReorder.spacing
        reorder.begin(id: "a", index: 0)
        reorder.update(translation: -step * 3, count: 3)
        XCTAssertEqual(reorder.offset(for: "b", index: 1), 0, "выше первого места не уехать")
        reorder.update(translation: step * 10, count: 3)
        XCTAssertEqual(reorder.offset(for: "c", index: 2), -step, "ниже последнего тоже")
    }

    // MARK: - Картинки

    func testExerciseArtMatchesNames() {
        let cases: [(String, String)] = [
            ("Приседания", "squat"), ("Фронтальный присед", "front_squat"), ("Присед с гирей", "goblet"),
            ("Становая тяга", "deadlift"), ("Румынская тяга", "rdl"), ("Тяга трэп-грифа", "deadlift"),
            ("Жим лёжа", "bench"), ("Жим на наклонной", "incline"), ("Жим штанги стоя", "ohp"),
            ("Жим гантелей сидя", "seated_press"), ("Жим в тренажере", "bench"),
            ("Тяга штанги в наклоне", "row"), ("Тяга верхнего блока", "pulldown"), ("Тяга сидя в блоке", "cable_row"),
            ("Подтягивания", "pullup"), ("Отжимания на брусьях", "dips"), ("Махи в стороны", "lateral_raise"),
            ("бицепс", "curl"), ("Трицепс", "pushdown"), ("Сгибание ног лёжа", "leg_curl"),
            ("Подъём ног в висе", "hanging_raise"), ("Копенгагенская планка", "plank"), ("Икры", "calf"),
            ("Обратная гиперэкстензия", "hyperext"), ("Что-то своё", "generic"),
        ]
        for (name, key) in cases {
            XCTAssertEqual(ExerciseArt.key(for: name), key, name)
        }
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
