import XCTest
@testable import TexasProgram

/// Факт подхода, откат после несданного веса, свой порядок и тоннаж.
final class TrainingLogTests: XCTestCase {

    private var utcCalendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar
    }

    private func date(_ day: Int, _ month: Int, _ year: Int) -> Date {
        utcCalendar.date(from: DateComponents(year: year, month: month, day: day))!
    }

    private func exercises(_ profile: ProgramProfile, week: Int, day: Int) -> [ExercisePrescription] {
        profile.workoutPlan.weeks.first { $0.number == week }?
            .days.first { $0.number == day }?.exercises ?? []
    }

    // MARK: - Факт подхода

    func testRecordedSetFillsDotAndStaysInLog() {
        let profile = ProgramProfile(input: .demo)
        let squat = exercises(profile, week: 1, day: 1)[0]

        profile.recordSet(week: 1, day: 1, exercise: squat, index: 2, reps: 3, weight: 82.5)

        let entry = profile.setEntry(week: 1, day: 1, exercise: squat, index: 2)
        XCTAssertEqual(entry?.reps, 3)
        XCTAssertEqual(entry?.weight, 82.5)
        // Запись факта закрывает и точку: отмечать одно и то же дважды незачем.
        XCTAssertEqual(profile.completedSets(week: 1, day: 1, exercise: squat), 3)
    }

    func testRecordedSetDoesNotLowerDoneCount() {
        let profile = ProgramProfile(input: .demo)
        let squat = exercises(profile, week: 1, day: 1)[0]
        profile.toggleSet(week: 1, day: 1, exercise: squat, index: 4)
        XCTAssertEqual(profile.completedSets(week: 1, day: 1, exercise: squat), 5)

        profile.recordSet(week: 1, day: 1, exercise: squat, index: 0, reps: 5, weight: 80)
        XCTAssertEqual(profile.completedSets(week: 1, day: 1, exercise: squat), 5)
    }

    func testHistoryKeepsPlannedAndActualApart() throws {
        let day = date(27, 7, 2026)
        let profile = ProgramProfile(input: .demo)
        let squat = exercises(profile, week: 1, day: 1)[0]
        profile.recordSet(week: 1, day: 1, exercise: squat, index: 0, reps: 3, weight: 70, now: day)
        profile.toggleCompleted(week: 1, day: 1, now: day)

        let record = try XCTUnwrap(profile.completionLog.first)
        XCTAssertEqual(record.squat, 80)
        XCTAssertEqual(record.actualSquat, 70)
    }

    /// Факт можно вписать после отметки — история должна догнать, не сдвинув дату.
    func testLoggingAfterCompletionUpdatesRecordKeepingDate() {
        let day = date(27, 7, 2026)
        let profile = ProgramProfile(input: .demo)
        let squat = exercises(profile, week: 1, day: 1)[0]
        profile.toggleCompleted(week: 1, day: 1, now: day)
        XCTAssertNil(profile.completionLog.first?.actualSquat)

        profile.recordSet(week: 1, day: 1, exercise: squat, index: 0, reps: 5, weight: 77.5, now: date(30, 7, 2026))
        XCTAssertEqual(profile.completionLog.first?.actualSquat, 77.5)
        XCTAssertEqual(profile.completionLog.first?.date, day)
    }

    func testClearingEntryLeavesTheDot() {
        let profile = ProgramProfile(input: .demo)
        let squat = exercises(profile, week: 1, day: 1)[0]
        profile.recordSet(week: 1, day: 1, exercise: squat, index: 0, reps: 5, weight: 80)
        profile.clearSetEntry(week: 1, day: 1, exercise: squat, index: 0)

        XCTAssertNil(profile.setEntry(week: 1, day: 1, exercise: squat, index: 0))
        // Точка остаётся: подход сделан, просто цифры больше не записаны.
        XCTAssertEqual(profile.completedSets(week: 1, day: 1, exercise: squat), 1)
    }

    // MARK: - Откат после несданного веса

    func testDeloadCutsWeightsFromItsWeekOnward() {
        let profile = ProgramProfile(input: .demo)
        XCTAssertEqual(exercises(profile, week: 5, day: 3)[0].load, .kilograms(100))

        profile.addDeload(fromWeek: 5, percent: 10)

        // Недели до отката не тронуты.
        XCTAssertEqual(exercises(profile, week: 4, day: 3)[0].load, .kilograms(97.5))
        // С пятой — минус десять процентов, округлённые до блина.
        XCTAssertEqual(exercises(profile, week: 5, day: 3)[0].load, .kilograms(90))
        XCTAssertEqual(exercises(profile, week: 6, day: 3)[0].load, .kilograms(92.5))
    }

    func testDeloadsCompoundAndCanBeUndone() {
        let profile = ProgramProfile(input: .demo)
        profile.addDeload(fromWeek: 3, percent: 10)
        profile.addDeload(fromWeek: 6, percent: 10)

        XCTAssertEqual(profile.deloadFactor(week: 2), 1, accuracy: 0.0001)
        XCTAssertEqual(profile.deloadFactor(week: 3), 0.9, accuracy: 0.0001)
        XCTAssertEqual(profile.deloadFactor(week: 6), 0.81, accuracy: 0.0001)

        profile.removeDeload(profile.deloads[0])
        XCTAssertEqual(profile.deloadFactor(week: 6), 0.9, accuracy: 0.0001)
        // Максимумы откат не трогает — снятие возвращает исходный расчёт.
        profile.removeDeload(profile.deloads[0])
        XCTAssertEqual(exercises(profile, week: 5, day: 3)[0].load, .kilograms(100))
    }

    /// Откат режет только килограммы: RPE и тест 1ПМ процентам не подчиняются.
    func testDeloadLeavesNonNumericLoadsAlone() {
        var input = ProgramInput.demo
        input.pull = AdditionalExerciseCategory.pull.options[0]
        let profile = ProgramProfile(input: input)
        profile.addDeload(fromWeek: 1, percent: 10)

        let pull = exercises(profile, week: 1, day: 1).first { $0.name == input.pull }
        XCTAssertEqual(pull?.load, .rpe("RPE 8"))
    }

    /// Пикирование нумерует недели с единицы: без разделения откат основной
    /// программы срезал бы и пиковый цикл.
    func testDeloadDoesNotLeakIntoPeaking() {
        let profile = ProgramProfile(input: .demo)
        profile.addDeload(fromWeek: 1, percent: 10)
        let base = exercises(profile, week: 1, day: 3)[0].load

        profile.peakingActive = true
        XCTAssertEqual(profile.deloadFactor(week: 1), 1)

        profile.peakingActive = false
        XCTAssertEqual(exercises(profile, week: 1, day: 3)[0].load, base)
    }

    // MARK: - Свой порядок упражнений

    func testCustomOrderMovesExerciseAndUnknownStayLast() {
        let profile = ProgramProfile(input: .demo)
        let original = exercises(profile, week: 1, day: 1)
        XCTAssertEqual(original[0].name, "Приседания")

        // Ставим второе упражнение первым, остальные в списке не упоминаем.
        profile.setOrder([original[1].id, original[0].id], week: 1, day: 1, scope: .everyWeek)

        let reordered = exercises(profile, week: 1, day: 1)
        XCTAssertEqual(reordered[0].name, original[1].name)
        XCTAssertEqual(reordered[1].name, original[0].name)
        XCTAssertEqual(reordered.count, original.count)
        XCTAssertEqual(reordered.last?.name, original.last?.name)
    }

    func testEmptyOrderRemovesTheRearrangement() {
        let profile = ProgramProfile(input: .demo)
        let original = exercises(profile, week: 1, day: 1)
        profile.setOrder([original[1].id, original[0].id], week: 1, day: 1, scope: .everyWeek)
        XCTAssertEqual(exercises(profile, week: 1, day: 1)[0].name, original[1].name)

        profile.setOrder([], week: 1, day: 1, scope: .everyWeek)
        XCTAssertEqual(exercises(profile, week: 1, day: 1)[0].name, original[0].name)
        XCTAssertFalse(profile.planEdits.contains { $0.kind == .order })
    }

    /// Порядок применяется после состава: добавленное упражнение можно поднять наверх.
    func testOrderAppliesAfterAddedExercise() {
        let profile = ProgramProfile(input: .demo)
        profile.addExercise(name: "Планка", sets: 3, reps: "60 с", kilograms: nil, loadText: nil,
                            week: 1, day: 1, scope: .everyWeek)
        let list = exercises(profile, week: 1, day: 1)
        let added = list.last!
        XCTAssertEqual(added.name, "Планка")

        profile.setOrder([added.id], week: 1, day: 1, scope: .everyWeek)
        XCTAssertEqual(exercises(profile, week: 1, day: 1).first?.name, "Планка")
    }

    // MARK: - Тоннаж

    func testTonnageSumsRecordedSetsByWeek() {
        let profile = ProgramProfile(input: .demo)
        let w1 = exercises(profile, week: 1, day: 1)[0]
        let w2 = exercises(profile, week: 2, day: 1)[0]

        profile.recordSet(week: 1, day: 1, exercise: w1, index: 0, reps: 5, weight: 80)
        profile.recordSet(week: 1, day: 1, exercise: w1, index: 1, reps: 5, weight: 80)
        profile.recordSet(week: 2, day: 1, exercise: w2, index: 0, reps: 3, weight: 100)

        let weeks = profile.weeklyTonnage
        XCTAssertEqual(weeks.count, 2)
        XCTAssertEqual(weeks[0].week, 1)
        XCTAssertEqual(weeks[0].total, 800)
        XCTAssertEqual(weeks[0].setCount, 2)
        XCTAssertEqual(weeks[1].total, 300)
    }

    func testTonnageIsEmptyWithoutRecordedSets() {
        let profile = ProgramProfile(input: .demo)
        let squat = exercises(profile, week: 1, day: 1)[0]
        // Закрытая точка без цифр в тоннаж не идёт: гадать по плану нельзя.
        profile.toggleSet(week: 1, day: 1, exercise: squat, index: 0)
        XCTAssertTrue(profile.weeklyTonnage.isEmpty)
    }

    func testTonnageKeepsPeakingSeparate() {
        let profile = ProgramProfile(input: .demo)
        let base = exercises(profile, week: 1, day: 1)[0]
        profile.recordSet(week: 1, day: 1, exercise: base, index: 0, reps: 5, weight: 80)

        profile.peakingActive = true
        XCTAssertTrue(profile.weeklyTonnage.isEmpty)

        let peak = exercises(profile, week: 1, day: 1)[0]
        profile.recordSet(week: 1, day: 1, exercise: peak, index: 0, reps: 2, weight: 100)
        XCTAssertEqual(profile.weeklyTonnage.first?.total, 200)

        profile.peakingActive = false
        XCTAssertEqual(profile.weeklyTonnage.first?.total, 400)
    }

    func testRemovingCustomExerciseClearsItsRecordedSets() {
        let profile = ProgramProfile(input: .demo)
        profile.addExercise(name: "Планка", sets: 3, reps: "60 с", kilograms: 20, loadText: nil,
                            week: 1, day: 1, scope: .everyWeek)
        let added = exercises(profile, week: 1, day: 1).last!
        profile.recordSet(week: 1, day: 1, exercise: added, index: 0, reps: 10, weight: 20)
        XCTAssertFalse(profile.weeklyTonnage.isEmpty)

        profile.removeEdits(week: 1, day: 1)
        XCTAssertNil(profile.setEntry(week: 1, day: 1, exercise: added, index: 0))
        XCTAssertTrue(profile.weeklyTonnage.isEmpty)
    }

    // MARK: - Резервная копия

    func testBackupCarriesLogAndDeloads() throws {
        let profile = ProgramProfile(input: .demo)
        let squat = exercises(profile, week: 1, day: 1)[0]
        profile.recordSet(week: 1, day: 1, exercise: squat, index: 0, reps: 4, weight: 75)
        profile.addDeload(fromWeek: 4, percent: 5)
        profile.keepScreenOn = false

        let archive = try BackupService.archive(from: try BackupService.export([profile]))
        let snapshot = try XCTUnwrap(archive.profiles.first)

        XCTAssertEqual(snapshot.setLog?.count, 1)
        XCTAssertEqual(snapshot.setLog?.values.first?.reps, 4)
        XCTAssertEqual(snapshot.deloads?.count, 1)
        XCTAssertEqual(snapshot.deloads?.first?.percent, 5)
        XCTAssertEqual(snapshot.keepScreenOn, false)
    }

    /// Копии, снятые раньше, этих полей не содержат — падать на них нельзя.
    func testBackupWithoutNewFieldsStillDecodes() throws {
        let profile = ProgramProfile(input: .demo)
        let data = try BackupService.export([profile])
        var object = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        var profiles = try XCTUnwrap(object["profiles"] as? [[String: Any]])
        for key in ["setLog", "deloads", "keepScreenOn", "planEdits"] {
            profiles[0].removeValue(forKey: key)
        }
        object["profiles"] = profiles

        let trimmed = try JSONSerialization.data(withJSONObject: object)
        let archive = try BackupService.archive(from: trimmed)
        XCTAssertNil(archive.profiles[0].setLog)
        XCTAssertNil(archive.profiles[0].deloads)
        XCTAssertNil(archive.profiles[0].keepScreenOn)
    }

    // MARK: - Разбор ключей

    func testSetKeyWeekParsingSeparatesPeaking() {
        XCTAssertEqual(SetKeyParts.week(from: "3-2|Приседания|0", isPeaking: false), 3)
        XCTAssertNil(SetKeyParts.week(from: "3-2|Приседания|0", isPeaking: true))
        XCTAssertEqual(SetKeyParts.week(from: "peak-2-1|Жим лёжа|1", isPeaking: true), 2)
        XCTAssertNil(SetKeyParts.week(from: "peak-2-1|Жим лёжа|1", isPeaking: false))
    }
}
