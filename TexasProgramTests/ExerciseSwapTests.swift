import XCTest
@testable import TexasProgram

/// Замена упражнения на ходу: похожие по движению, вес по истории или примерно,
/// «только сегодня» и «до конца программы», откат при отмене тренировки.
final class ExerciseSwapTests: XCTestCase {
    private func texas() -> ProgramProfile { ProgramProfile(input: .demo) }

    func testFamiliesAndEstimates() {
        XCTAssertEqual(ExerciseSwap.family(for: "Приседания")?.title, "Присед")
        XCTAssertEqual(ExerciseSwap.family(for: "Тяга штанги в наклоне")?.title, "Горизонтальная тяга")
        XCTAssertEqual(ExerciseSwap.estimate(from: "Приседания", weight: 100, to: "Фронтальные приседания", step: 2.5), 80)
        XCTAssertEqual(ExerciseSwap.estimate(from: "Жим лёжа", weight: 100, to: "Отжимания на брусьях", step: 2.5), 0, "с весом тела")
        XCTAssertNil(ExerciseSwap.estimate(from: "Жим лёжа", weight: 100, to: "Приседания", step: 2.5), "другое движение")
        XCTAssertNil(ExerciseSwap.estimate(from: "Что-то своё", weight: 100, to: "Фронтальные приседания", step: 2.5))
    }

    func testHistoryComesFirstWithLastWeight() throws {
        let profile = texas()
        let day = try XCTUnwrap(profile.workoutPlan.weeks.first?.days.first)
        let squat = try XCTUnwrap(day.exercises.first { $0.name == "Приседания" })
        var undo = ProgramProfile.SwapUndo()
        // Неделю назад вместо приседа делали фронтальный.
        let front = profile.swapExercise(squat, to: "Фронтальные приседания", week: 1, day: day.number, scope: .today, undo: &undo)
        profile.recordSet(week: 1, day: day.number, exercise: front, index: 0, reps: 5, weight: 90)

        let found = ExerciseSwap.suggestions(replacing: "Приседания", currentWeight: 120, profile: profile, week: 2, day: day.number)
        XCTAssertEqual(found.done.first?.name, "Фронтальные приседания")
        XCTAssertEqual(found.done.first?.weight, 90)
        XCTAssertEqual(found.done.first?.reps, 5)
        XCTAssertFalse(found.similar.contains { $0.name == "Фронтальные приседания" }, "уже в «делал»")
        XCTAssertFalse(found.similar.contains { $0.name == "Приседания" }, "само себя не предлагаем")
        XCTAssertEqual(found.similar.first { $0.name == "Присед Зерчера" }?.weight, 90)
    }

    func testTodayOnlyAndUndo() throws {
        let profile = texas()
        let squat = try XCTUnwrap(profile.workoutPlan.weeks[0].days[0].exercises.first { $0.name == "Приседания" })
        var undo = ProgramProfile.SwapUndo()
        _ = profile.swapExercise(squat, to: "Жим ногами", week: 1, day: 1, scope: .today, undo: &undo)
        XCTAssertTrue(profile.workoutPlan.weeks[0].days[0].exercises.contains { $0.name == "Жим ногами" })
        XCTAssertTrue(profile.workoutPlan.weeks[1].days[0].exercises.contains { $0.name == "Приседания" }, "следующая неделя по программе")
        profile.undoSwaps(undo)
        XCTAssertTrue(profile.planEdits.isEmpty)
        XCTAssertTrue(profile.workoutPlan.weeks[0].days[0].exercises.contains { $0.name == "Приседания" })
    }

    func testRestOfProgramKeepsPastWeeks() throws {
        let profile = texas()
        let dayNumber = 1
        let accessory = try XCTUnwrap(profile.workoutPlan.weeks[1].days[0].exercises.first { !profile.isProgramLift($0) })
        var undo = ProgramProfile.SwapUndo()
        _ = profile.swapExercise(accessory, to: "Своё упражнение", week: 2, day: dayNumber, scope: .rest, undo: &undo)
        let weeks = profile.workoutPlan.weeks
        XCTAssertTrue(weeks[0].days[0].exercises.contains { $0.id == accessory.id }, "прошлая неделя не меняется")
        for week in weeks.dropFirst() where week.days.first?.exercises.contains(where: { $0.name == "Своё упражнение" }) == false {
            XCTAssertFalse(week.days[0].exercises.contains { $0.id == accessory.id }, "неделя \(week.number) осталась со старым")
        }
        XCTAssertTrue(weeks[1].days[0].exercises.contains { $0.name == "Своё упражнение" })
        XCTAssertTrue(weeks.last?.days[0].exercises.contains { $0.name == "Своё упражнение" } ?? false)
    }

    func testOwnEveryWeekExerciseSwapsOnlyWhereAsked() throws {
        let profile = texas()
        let added = profile.addExercise(name: "Сгибания на бицепс", sets: 3, reps: "10", kilograms: 20, loadText: nil,
                                        week: 1, day: 1, scope: .everyWeek)
        profile.recordSet(week: 1, day: 1, exercise: added.prescription, index: 0, reps: 10, weight: 20)
        let own = try XCTUnwrap(profile.workoutPlan.weeks[1].days[0].exercises.first { $0.id == added.id })
        var undo = ProgramProfile.SwapUndo()
        let today = profile.swapExercise(own, to: "Молотки", week: 2, day: 1, scope: .today, undo: &undo)
        let weeks = profile.workoutPlan.weeks
        XCTAssertTrue(weeks[1].days[0].exercises.contains { $0.id == today.id && $0.name == "Молотки" })
        XCTAssertFalse(weeks[1].days[0].exercises.contains { $0.id == added.id })
        XCTAssertTrue(weeks[0].days[0].exercises.contains { $0.id == added.id && $0.name == "Сгибания на бицепс" }, "прошлая неделя как была")
        XCTAssertTrue(weeks[2].days[0].exercises.contains { $0.id == added.id }, "следующая неделя по-старому")
        XCTAssertEqual(profile.history(forExerciseNamed: "Сгибания на бицепс").first { !$0.entries.isEmpty }?.week, 1, "история не переехала")

        _ = profile.swapExercise(today, to: "Сгибания со штангой", week: 2, day: 1, scope: .rest, undo: &undo)
        XCTAssertTrue(profile.workoutPlan.weeks[1].days[0].exercises.contains { $0.name == "Сгибания со штангой" })
        XCTAssertTrue(profile.workoutPlan.weeks.last?.days[0].exercises.contains { $0.name == "Сгибания со штангой" } ?? false)
        XCTAssertTrue(profile.workoutPlan.weeks[0].days[0].exercises.contains { $0.id == added.id })

        profile.undoSwaps(undo)
        XCTAssertEqual(profile.planEdits, [added])
    }

    func testSwapKeepsOwnOrderOfDay() throws {
        let profile = texas()
        let reversed = Array(profile.workoutPlan.weeks[1].days[0].exercises.reversed())
        profile.setOrder(reversed.map(\.id), week: 2, day: 1, scope: .everyWeek)
        let orderEdit = try XCTUnwrap(profile.planEdits.first)
        let first = try XCTUnwrap(profile.workoutPlan.weeks[1].days[0].exercises.first)
        XCTAssertEqual(first.id, reversed.first?.id)
        var undo = ProgramProfile.SwapUndo()
        _ = profile.swapExercise(first, to: "Своё упражнение", week: 2, day: 1, scope: .rest, undo: &undo)
        XCTAssertEqual(profile.workoutPlan.weeks[1].days[0].exercises.first?.name, "Своё упражнение", "на месте прежнего, а не в конце")
        XCTAssertEqual(profile.workoutPlan.weeks[1].days[0].exercises.count, reversed.count)
        profile.undoSwaps(undo)
        XCTAssertEqual(profile.planEdits, [orderEdit])
    }

    func testSessionSwapClearsMarkedSetsAndDiscardRestores() throws {
        let profile = texas()
        let manager = WorkoutSessionManager.shared
        manager.forgetForTesting()
        let workout = try XCTUnwrap(profile.schedule.focus)
        manager.startWorkout(workout: workout, profile: profile)
        let index = try XCTUnwrap(manager.exercises.firstIndex { $0.prescription.name == "Приседания" })
        let old = manager.exercises[index].prescription
        manager.toggleSetDone(exIdx: index, sIdx: 0, profile: profile, restTimer: RestTimer())
        XCTAssertNotNil(profile.setEntry(week: workout.week, day: workout.day.number, exercise: old, index: 0))

        let suggestion = ExerciseSwap.suggestion(named: "Фронтальные приседания", currentName: old.name,
                                                 currentWeight: 100, profile: profile, week: workout.week, day: workout.day.number)
        manager.swapExercise(exIdx: index, to: suggestion, scope: .rest, profile: profile)
        XCTAssertEqual(manager.exercises[index].prescription.name, "Фронтальные приседания")
        XCTAssertEqual(manager.exercises[index].sets.first?.weight, 80)
        XCTAssertFalse(manager.exercises[index].sets.contains(where: \.isDone))
        XCTAssertNil(profile.setEntry(week: workout.week, day: workout.day.number, exercise: old, index: 0), "старый подход снят")
        // Основное движение меняется только на сегодня, даже если просили дальше.
        XCTAssertEqual(profile.planEdits.count, 1)

        manager.discardWorkout(profile: profile)
        XCTAssertTrue(profile.planEdits.isEmpty, "отмена тренировки возвращает упражнение")
    }
}
