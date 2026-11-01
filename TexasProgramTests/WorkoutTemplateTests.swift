import SwiftData
import XCTest
@testable import TexasProgram

/// Библиотека упражнений, «Мои тренировки» и тренировки вне программы.
/// Те же случаи — в WorkoutTemplateTest на Android.
@MainActor
final class WorkoutTemplateTests: XCTestCase {
    private var container: ModelContainer?

    // MARK: Библиотека

    func testLibraryLoadsFromBundle() {
        XCTAssertGreaterThan(ExerciseLibrary.builtIn.count, 100)
        XCTAssertEqual(ExerciseLibrary.groups.map(\.id).prefix(3), ["chest", "back", "shoulders"])
        XCTAssertEqual(ExerciseLibrary.groupTitle("hamstrings"), "Бицепс бедра")
        XCTAssertEqual(ExerciseLibrary.equipmentTitle("dumbbell"), "Гантели")
        let ids = ExerciseLibrary.builtIn.map(\.id)
        XCTAssertEqual(Set(ids).count, ids.count, "ключи не повторяются")
        XCTAssertTrue(ExerciseLibrary.builtIn.allSatisfy { !$0.steps.isEmpty })
    }

    func testSearchAndFilters() {
        let all = ExerciseLibrary.builtIn
        let found = ExerciseLibrary.filter(all, query: "гант жим", group: nil, equipment: nil).map(\.name)
        XCTAssertTrue(found.contains("Жим гантелей лёжа"))
        XCTAssertTrue(found.allSatisfy { $0.lowercased().contains("гант") })

        // «ё» и «е» — одно и то же.
        XCTAssertFalse(ExerciseLibrary.filter(all, query: "лежа", group: nil, equipment: nil).isEmpty)

        let chestBarbell = ExerciseLibrary.filter(all, query: "", group: "chest", equipment: "barbell")
        XCTAssertTrue(chestBarbell.contains { $0.name == "Жим штанги лёжа" })
        XCTAssertTrue(chestBarbell.allSatisfy { $0.equipment == "barbell" })

        // Фильтр по мышце берёт и второстепенную: жим лёжа узким хватом — трицепс, но и грудь.
        let triceps = ExerciseLibrary.filter(all, query: "", group: "triceps", equipment: nil)
        XCTAssertTrue(triceps.contains { $0.name == "Жим штанги лёжа" })
        XCTAssertEqual(ExerciseLibrary.sections(triceps, group: "triceps").map(\.group.id), ["triceps"])

        let sections = ExerciseLibrary.sections(all).map(\.group.id)
        XCTAssertEqual(sections.first, "chest")
        XCTAssertEqual(ExerciseLibrary.find(named: "жим штанги лежа")?.id, "bench_press")
    }

    func testCustomExercisesComeFirst() {
        let profile = ProgramProfile(input: .demo)
        let own = LibraryExercise.custom(name: "Жим Ларсена", group: "chest", equipment: "barbell")
        profile.saveCustomExercise(own)
        XCTAssertEqual(profile.exerciseLibrary.first?.name, "Жим Ларсена")
        XCTAssertTrue(own.isCustom)
        XCTAssertEqual(ExerciseLibrary.find(named: "жим ларсена", custom: profile.customExercises)?.id, own.id)
        profile.deleteCustomExercise(own.id)
        XCTAssertTrue(profile.customExercises.isEmpty)
    }

    // MARK: Шаблоны

    func testTemplatePrescription() {
        let exercise = TemplateExercise(name: "Жим штанги лёжа", restSeconds: 180, sets: [
            TemplateSet(weight: 0, reps: 8), TemplateSet(weight: 80, reps: 8), TemplateSet(weight: 82.5, reps: 6)
        ])
        let prescription = exercise.prescription
        XCTAssertEqual(prescription.sets, 3)
        XCTAssertEqual(prescription.reps, "8")
        XCTAssertEqual(prescription.load, .kilograms(80))

        let empty = TemplateExercise(name: "Подтягивания", sets: [TemplateSet(weight: 0, reps: 10)])
        XCTAssertTrue(empty.prescription.needsOwnWeight)

        let template = WorkoutTemplate(name: "Грудь", exercises: [exercise, empty])
        XCTAssertEqual(template.summary, "2 упр · 4 подх")
        XCTAssertEqual(template.groups(), ["Грудь", "Спина"])
        let copy = template.copy()
        XCTAssertNotEqual(copy.id, template.id)
        XCTAssertNotEqual(copy.exercises.first?.id, exercise.id)
        XCTAssertEqual(copy.exercises.first?.sets, exercise.sets)
    }

    func testTemplateFromCommunityProgram() {
        let template = WorkoutTemplate.from(title: "Руки", exercises: [("Молотки", 3, "8-12", "16 кг"), ("Отжимания", 4, "15", "—")])
        XCTAssertEqual(template.exercises[0].sets, Array(repeating: TemplateSet(weight: 16, reps: 8), count: 3))
        XCTAssertEqual(template.exercises[1].sets, Array(repeating: TemplateSet(weight: 0, reps: 15), count: 4))
    }

    func testCatalogIsUsable() {
        XCTAssertGreaterThanOrEqual(TemplateCatalog.all.count, 8)
        for template in TemplateCatalog.all {
            XCTAssertFalse(template.exercises.isEmpty, template.name)
            for exercise in template.exercises {
                XCTAssertNotNil(ExerciseLibrary.find(named: exercise.name), "\(template.name): \(exercise.name) нет в библиотеке")
            }
        }
    }

    // MARK: Свободная тренировка

    func testFreeWorkoutDoesNotMoveProgram() {
        let profile = ProgramProfile(input: .demo)
        let completedBefore = profile.completedDayKeys
        let lastBefore = profile.lastCompletionDate
        let template = WorkoutTemplate(name: "Руки", exercises: [TemplateExercise(name: "Молотки с гантелями", sets: [TemplateSet(weight: 16, reps: 10)])])
        let workout = profile.startWorkout(from: template, now: Date(timeIntervalSince1970: 1_800_000_000))
        XCTAssertEqual(workout.week, ProgramProfile.freeWeek)
        XCTAssertEqual(profile.dayKey(week: workout.week, day: workout.day.number), "free-1")
        XCTAssertFalse(profile.isOwnKey("free-1"))

        let exercise = workout.day.exercises[0]
        profile.recordSet(week: 0, day: 1, exercise: exercise, index: 0, reps: 10, weight: 16)
        XCTAssertNotNil(profile.setLog["free-1|\(exercise.id)|0"])

        profile.toggleCompleted(week: 0, day: 1, now: Date(timeIntervalSince1970: 1_800_003_600), durationSeconds: 2400, effort: "Нормально")
        XCTAssertTrue(profile.isCompleted(week: 0, day: 1))
        XCTAssertEqual(profile.completedDayKeys, completedBefore, "программа не двигается")
        XCTAssertEqual(profile.lastCompletionDate, lastBefore, "очередь не думает, что день программы сделан")
        let record = profile.completionLog.first { $0.key == "free-1" }
        XCTAssertEqual(record?.week, 0)
        XCTAssertEqual(record?.durationSeconds, 2400)

        // История и подпись.
        XCTAssertEqual(profile.history(forExerciseNamed: "Молотки с гантелями").map(\.best), [16])
        XCTAssertTrue(profile.loggedExerciseNames.contains("Молотки с гантелями"))
        XCTAssertEqual(profile.historyTitle(week: 0, day: 1), "Руки")
        XCTAssertEqual(PersonalRecords.names(for: profile)[exercise.id], "Молотки с гантелями")

        // Вторая свободная тренировка — следующий номер.
        let next = profile.startWorkout(from: template)
        XCTAssertEqual(next.day.number, 2)
        profile.discardFreeWorkout(2)
        XCTAssertNil(profile.freeDay(2))
        // Законченную отмена не трогает.
        profile.discardFreeWorkout(1)
        XCTAssertNotNil(profile.freeDay(1))

        profile.deleteFreeWorkout(1)
        XCTAssertTrue(profile.freeWorkouts.isEmpty)
        XCTAssertFalse(profile.completionLog.contains { $0.key == "free-1" })
        XCTAssertNil(profile.setLog["free-1|\(exercise.id)|0"])
    }

    func testHistoryIsChronologicalAcrossProgramAndFree() throws {
        let profile = ProgramProfile(input: .demo)
        let day = try XCTUnwrap(profile.workoutPlan.weeks.first?.days.first)
        let lift = try XCTUnwrap(day.exercises.first)
        let start = Date(timeIntervalSince1970: 1_800_000_000)

        // Свободная тренировка с тем же движением — между двумя днями программы.
        profile.recordSet(week: 1, day: day.number, exercise: lift, index: 0, reps: 5, weight: 100)
        profile.toggleCompleted(week: 1, day: day.number, now: start)

        let free = profile.startFreeWorkout(title: "Доп", exercises: [ExercisePrescription(id: "tx-a", name: lift.name, sets: 1, reps: "5", load: .repRange("5"))],
                                            now: start.addingTimeInterval(86_400))
        profile.recordSet(week: 0, day: free.day.number, exercise: free.day.exercises[0], index: 0, reps: 5, weight: 105)
        profile.toggleCompleted(week: 0, day: free.day.number, now: start.addingTimeInterval(86_400))

        let second = try XCTUnwrap(profile.workoutPlan.weeks.dropFirst().first?.days.first)
        let secondLift = try XCTUnwrap(second.exercises.first { $0.name == lift.name })
        profile.recordSet(week: 2, day: second.number, exercise: secondLift, index: 0, reps: 5, weight: 110)
        profile.toggleCompleted(week: 2, day: second.number, now: start.addingTimeInterval(3 * 86_400))

        XCTAssertEqual(profile.history(forExerciseNamed: lift.name).map(\.best), [100, 105, 110])
        XCTAssertEqual(profile.history(forExerciseNamed: lift.name).map(\.week), [1, 0, 2])
    }

    func testProgramProgressionIgnoresFreeWorkouts() throws {
        let profile = ProgramProfile(input: .demo)
        // Подсобка программы без веса: прогрессию ведут только её дни.
        let accessory = try XCTUnwrap(profile.workoutPlan.weeks.flatMap(\.days).flatMap(\.exercises).first { $0.needsOwnWeight && $0.topReps != nil })
        let free = profile.startFreeWorkout(title: "Доп", exercises: [ExercisePrescription(id: "tx-b", name: accessory.name, sets: accessory.sets, reps: accessory.reps, load: .repRange(accessory.reps))])
        for index in 0..<accessory.sets {
            profile.recordSet(week: 0, day: free.day.number, exercise: free.day.exercises[0], index: index, reps: 30, weight: 40)
        }
        let hint = profile.accessoryHint(for: accessory, week: 1, day: 1)
        XCTAssertNil(hint?.weight, "вес программы не берётся из свободной тренировки")

        // А в следующей свободной тренировке прошлый раз виден.
        let nextFree = profile.startFreeWorkout(title: "Доп", exercises: [ExercisePrescription(id: "tx-c", name: accessory.name, sets: accessory.sets, reps: accessory.reps, load: .repRange(accessory.reps))])
        XCTAssertNotNil(profile.accessoryHint(for: nextFree.day.exercises[0], week: 0, day: nextFree.day.number)?.weight)
    }

    func testReplaceAndAppendInFreeWorkout() {
        let profile = ProgramProfile(input: .demo)
        let free = profile.startWorkout(from: WorkoutTemplate(name: "Тест", exercises: [TemplateExercise(name: "Подтягивания")]))
        let replaced = profile.replaceFreeExercise(day: free.day.number, old: free.day.exercises[0], with: "Тяга верхнего блока")
        XCTAssertEqual(profile.freeDay(free.day.number)?.exercises.map(\.name), ["Тяга верхнего блока"])
        XCTAssertEqual(replaced.sets, 3)
        XCTAssertTrue(profile.planEdits.isEmpty, "правок программы нет")
        profile.appendFreeExercises(day: free.day.number, [TemplateExercise(name: "Шраги с гантелями").prescription])
        XCTAssertEqual(profile.freeDay(free.day.number)?.exercises.count, 2)
    }

    // MARK: Копия

    func testBackupKeepsTemplatesAndFreeWorkouts() throws {
        let profile = ProgramProfile(input: .demo, name: "Шаблоны")
        profile.saveCustomExercise(LibraryExercise.custom(name: "Жим Ларсена", group: "chest", equipment: "barbell"))
        profile.saveTemplate(WorkoutTemplate(name: "Руки", exercises: [TemplateExercise(name: "Молотки с гантелями", restSeconds: 90, sets: [TemplateSet(weight: 16, reps: 12)])], isShared: true))
        let free = profile.startWorkout(from: profile.workoutTemplates[0])
        profile.recordSet(week: 0, day: free.day.number, exercise: free.day.exercises[0], index: 0, reps: 12, weight: 16)
        profile.toggleCompleted(week: 0, day: free.day.number)

        let data = try BackupService.export([profile], socialToken: nil)
        let archive = try BackupService.archive(from: data)
        let container = try ModelContainer(for: ProgramProfile.self, configurations: ModelConfiguration(isStoredInMemoryOnly: true))
        self.container = container
        let context = ModelContext(container)
        BackupService.restore(archive, into: context, existing: [])
        let restored = try XCTUnwrap(try context.fetch(FetchDescriptor<ProgramProfile>()).first)

        XCTAssertEqual(restored.customExercises.map(\.name), ["Жим Ларсена"])
        XCTAssertEqual(restored.workoutTemplates.first?.exercises.first?.restSeconds, 90)
        XCTAssertEqual(restored.workoutTemplates.first?.isShared, true)
        XCTAssertEqual(restored.freeWorkouts.first?.completed, true)
        XCTAssertEqual(restored.history(forExerciseNamed: "Молотки с гантелями").map(\.best), [16])
    }

    /// Так «Мои тренировки» пишет Android.
    func testAndroidJSON() throws {
        let json = """
        {"id":"T1","name":"Ноги","exercises":[{"id":"tx-1","name":"Приседания со штангой","restSeconds":180,"sets":[{"weight":100.0,"reps":5}]}],"isShared":false,"createdAt":"2026-03-01T07:00:00Z"}
        """
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let template = try decoder.decode(WorkoutTemplate.self, from: Data(json.utf8))
        XCTAssertEqual(template.exercises.first?.sets.first, TemplateSet(weight: 100, reps: 5))

        let free = """
        {"number":3,"title":"Ноги","templateID":null,"date":"2026-03-02T07:00:00Z","exercises":[{"id":"tx-1","name":"Приседания со штангой","sets":1,"reps":"5","kilograms":100.0}],"completed":true}
        """
        let day = try decoder.decode(FreeWorkoutDay.self, from: Data(free.utf8))
        XCTAssertEqual(day.plan.exercises.first?.load, .kilograms(100))
        XCTAssertEqual(day.plan.id, "free-3")
    }
}
