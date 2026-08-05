import XCTest
@testable import TexasProgram

/// Факт подхода, пропуск тренировки, свой порядок и тоннаж.
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

    // MARK: - Пропуск тренировки

    func testSkipDoesNotCompleteTheDay() {
        let profile = ProgramProfile(input: .demo)
        profile.markSkipped(week: 1, day: 1, holdsProgression: true)

        XCTAssertTrue(profile.isSkipped(week: 1, day: 1))
        // Тренировка остаётся в расписании: её ещё можно закрыть.
        XCTAssertFalse(profile.isCompleted(week: 1, day: 1))
        XCTAssertEqual(profile.completedDayCount, 0)
    }

    /// Пропущенная неделя остаётся со своими весами, а замирает следующая:
    /// стимул был у той, которую отработали, а не у той, что пропустили.
    func testHoldFreezesTheNextWeek() {
        let profile = ProgramProfile(input: .demo)
        XCTAssertEqual(exercises(profile, week: 1, day: 3)[0].load, .kilograms(90))
        XCTAssertEqual(exercises(profile, week: 2, day: 3)[0].load, .kilograms(92.5))

        profile.markSkipped(week: 1, day: 1, holdsProgression: true)

        XCTAssertEqual(exercises(profile, week: 1, day: 3)[0].load, .kilograms(90))
        XCTAssertEqual(exercises(profile, week: 2, day: 3)[0].load, .kilograms(90))
        XCTAssertEqual(exercises(profile, week: 3, day: 3)[0].load, .kilograms(92.5))
    }

    func testTwoHeldWeeksShiftProgressionTwice() {
        let profile = ProgramProfile(input: .demo)
        profile.markSkipped(week: 1, day: 1, holdsProgression: true)
        profile.markSkipped(week: 3, day: 2, holdsProgression: true)

        XCTAssertEqual(profile.holdCount(before: 2), 1)
        XCTAssertEqual(profile.holdCount(before: 4), 2)
        XCTAssertEqual(profile.effectiveWeek(for: 5), 3)
        XCTAssertEqual(exercises(profile, week: 5, day: 3)[0].load, .kilograms(95))
    }

    /// Несколько пропусков одной недели задерживают её только один раз.
    func testSameWeekCountsOnce() {
        let profile = ProgramProfile(input: .demo)
        profile.markSkipped(week: 1, day: 1, holdsProgression: true)
        profile.markSkipped(week: 1, day: 2, holdsProgression: true)

        XCTAssertEqual(profile.holdCount(before: 2), 1)
        XCTAssertEqual(exercises(profile, week: 2, day: 3)[0].load, .kilograms(90))
    }

    func testSkipWithoutHoldLeavesWeightsAlone() {
        let profile = ProgramProfile(input: .demo)
        profile.markSkipped(week: 1, day: 1, holdsProgression: false)

        XCTAssertTrue(profile.isSkipped(week: 1, day: 1))
        XCTAssertEqual(profile.holdCount(before: 2), 0)
        XCTAssertEqual(exercises(profile, week: 2, day: 3)[0].load, .kilograms(92.5))
    }

    /// Возместил пропущенное — задержка снимается сама.
    func testCompletingSkippedWorkoutReleasesTheHold() {
        let profile = ProgramProfile(input: .demo)
        profile.markSkipped(week: 1, day: 1, holdsProgression: true)
        XCTAssertEqual(exercises(profile, week: 2, day: 3)[0].load, .kilograms(90))

        profile.toggleCompleted(week: 1, day: 1)

        XCTAssertFalse(profile.isSkipped(week: 1, day: 1))
        XCTAssertEqual(exercises(profile, week: 2, day: 3)[0].load, .kilograms(92.5))
    }

    func testSkipCanBeRemovedByHand() {
        let profile = ProgramProfile(input: .demo)
        profile.markSkipped(week: 2, day: 1, holdsProgression: true)
        XCTAssertEqual(profile.activeSkips.count, 1)

        profile.removeSkip(profile.activeSkips[0])
        XCTAssertTrue(profile.activeSkips.isEmpty)
        XCTAssertEqual(exercises(profile, week: 3, day: 3)[0].load, .kilograms(95))
    }

    /// Пикирование нумерует недели с единицы: без разделения пропуск основной
    /// программы заморозил бы и пиковый цикл.
    func testSkipDoesNotLeakIntoPeaking() {
        let profile = ProgramProfile(input: .demo)
        profile.markSkipped(week: 1, day: 1, holdsProgression: true)
        XCTAssertEqual(profile.holdCount(before: 2), 1)

        profile.peakingActive = true
        XCTAssertEqual(profile.holdCount(before: 2), 0)
        XCTAssertTrue(profile.activeSkips.isEmpty)

        profile.peakingActive = false
        XCTAssertEqual(profile.holdCount(before: 2), 1)
    }

    /// Задержка подменяет только веса: номера дней и заголовки остаются своими,
    /// иначе отметки поехали бы в чужую неделю.
    func testHoldKeepsDayIdentity() {
        let profile = ProgramProfile(input: .demo)
        profile.markSkipped(week: 1, day: 1, holdsProgression: true)

        let week2 = profile.workoutPlan.weeks.first { $0.number == 2 }
        XCTAssertEqual(week2?.days.map(\.id), ["2-1", "2-2", "2-3"])
        XCTAssertEqual(week2?.days.map(\.number), [1, 2, 3])
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

    // MARK: - Серия без пропусков

    private func closeWeek(_ profile: ProgramProfile, _ week: Int) {
        for day in 1...3 { profile.toggleCompleted(week: week, day: day) }
    }

    func testStreakCountsFullyClosedWeeks() {
        let profile = ProgramProfile(input: .demo)
        XCTAssertEqual(profile.weekStreak.current, 0)
        XCTAssertEqual(profile.weekStreak.best, 0)

        closeWeek(profile, 1)
        closeWeek(profile, 2)
        XCTAssertEqual(profile.weekStreak.current, 2)
        XCTAssertEqual(profile.weekStreak.best, 2)
    }

    func testUnfinishedWeekDoesNotCount() {
        let profile = ProgramProfile(input: .demo)
        closeWeek(profile, 1)
        // Вторая неделя начата, но не закрыта — серия остаётся равной единице.
        profile.toggleCompleted(week: 2, day: 1)
        XCTAssertEqual(profile.weekStreak.current, 1)
    }

    /// Незакрытая последняя неделя серию не рвёт: она просто ещё идёт.
    func testCurrentStreakLooksBackFromLastClosedWeek() {
        let profile = ProgramProfile(input: .demo)
        closeWeek(profile, 1)
        closeWeek(profile, 2)
        closeWeek(profile, 3)
        profile.toggleCompleted(week: 4, day: 1)

        XCTAssertEqual(profile.weekStreak.current, 3)
        XCTAssertEqual(profile.weekStreak.best, 3)
    }

    func testGapBreaksCurrentStreakButKeepsBest() {
        let profile = ProgramProfile(input: .demo)
        closeWeek(profile, 1)
        closeWeek(profile, 2)
        closeWeek(profile, 3)
        // Четвёртую пропустили целиком, пятую закрыли.
        closeWeek(profile, 5)

        XCTAssertEqual(profile.weekStreak.current, 1)
        XCTAssertEqual(profile.weekStreak.best, 3)
    }

    /// У пикирования свои недели и свои отметки — серия считается отдельно.
    func testStreakIsSeparateForPeaking() {
        let profile = ProgramProfile(input: .demo)
        closeWeek(profile, 1)
        closeWeek(profile, 2)
        XCTAssertEqual(profile.weekStreak.current, 2)

        profile.peakingActive = true
        XCTAssertEqual(profile.weekStreak.current, 0)

        profile.peakingActive = false
        XCTAssertEqual(profile.weekStreak.current, 2)
    }

    // MARK: - Новый цикл

    func testNewCycleHidesMarksButKeepsHistory() {
        let day = date(27, 7, 2026)
        let profile = ProgramProfile(input: .demo)
        profile.toggleCompleted(week: 1, day: 1, now: day)
        XCTAssertEqual(profile.completedDayCount, 1)
        XCTAssertEqual(profile.completionLog.count, 1)

        profile.startNewCycle(squat: 110, bench: 105, deadlift: 130)

        XCTAssertEqual(profile.cycleNumber, 2)
        XCTAssertEqual(profile.squat5RM, 110)
        // Отметки прошлого цикла больше не видны…
        XCTAssertFalse(profile.isCompleted(week: 1, day: 1))
        XCTAssertEqual(profile.completedDayCount, 0)
        // …но история для графиков осталась.
        XCTAssertEqual(profile.completionLog.count, 1)
    }

    func testNewCycleMarksDoNotCollideWithOld() {
        let profile = ProgramProfile(input: .demo)
        profile.toggleCompleted(week: 1, day: 1, now: date(27, 7, 2026))
        profile.startNewCycle(squat: 110, bench: 105, deadlift: 130)
        profile.toggleCompleted(week: 1, day: 1, now: date(10, 11, 2026))

        // Два разных дня в истории, а не перезаписанный один.
        XCTAssertEqual(profile.completionLog.count, 2)
        XCTAssertEqual(profile.completedDayKeys.count, 2)
        XCTAssertTrue(profile.completedDayKeys.contains("1-1"))
        XCTAssertTrue(profile.completedDayKeys.contains("c2-1-1"))
        XCTAssertEqual(profile.completedDayCount, 1)
    }

    /// Первый цикл ключи не меняет: сохранённые профили должны продолжать работать.
    func testFirstCycleKeysAreUnchanged() {
        let profile = ProgramProfile(input: .demo)
        XCTAssertEqual(profile.dayKey(week: 2, day: 3), "2-3")
        profile.peakingActive = true
        XCTAssertEqual(profile.dayKey(week: 2, day: 3), "peak-2-3")
    }

    func testCycleAndPeakingNamespacesDoNotOverlap() {
        let profile = ProgramProfile(input: .demo)
        profile.startNewCycle(squat: 100, bench: 100, deadlift: 100)
        XCTAssertEqual(profile.dayKey(week: 1, day: 1), "c2-1-1")
        XCTAssertTrue(profile.isOwnKey("c2-1-1"))
        XCTAssertFalse(profile.isOwnKey("1-1"))
        XCTAssertFalse(profile.isOwnKey("c2-peak-1-1"))

        profile.peakingActive = true
        XCTAssertEqual(profile.dayKey(week: 1, day: 1), "c2-peak-1-1")
        XCTAssertTrue(profile.isOwnKey("c2-peak-1-1"))
        XCTAssertFalse(profile.isOwnKey("c2-1-1"))
        XCTAssertFalse(profile.isOwnKey("peak-1-1"))
    }

    func testTonnageIgnoresPreviousCycle() {
        let profile = ProgramProfile(input: .demo)
        let squat = exercises(profile, week: 1, day: 1)[0]
        profile.recordSet(week: 1, day: 1, exercise: squat, index: 0, reps: 5, weight: 80)
        XCTAssertEqual(profile.weeklyTonnage.first?.total, 400)

        profile.startNewCycle(squat: 110, bench: 105, deadlift: 130)
        XCTAssertTrue(profile.weeklyTonnage.isEmpty)
    }

    // MARK: - Фулбади

    private func fullBodyPlan(_ level: FullBodyLevel) -> WorkoutPlan {
        FullBodyCalculator.generate(input: .demo, level: level)
    }

    func testFullBodyIsTwelveWeeksOfThreeDays() {
        for level in FullBodyLevel.allCases {
            let plan = fullBodyPlan(level)
            XCTAssertEqual(plan.weeks.count, 12, level.rawValue)
            XCTAssertTrue(plan.weeks.allSatisfy { $0.days.count == 3 }, level.rawValue)
        }
    }

    /// Фулбади — это всё тело каждый раз, а не сплит: присед, жим, тяга,
    /// руки и кор должны быть в каждой тренировке.
    func testEveryFullBodySessionHitsEverything() {
        for level in FullBodyLevel.allCases {
            for week in fullBodyPlan(level).weeks {
                for day in week.days {
                    let names = day.exercises.map(\.name)
                    XCTAssertTrue(names.contains("Приседания"), "\(level.rawValue) н\(week.number) д\(day.number)")
                    XCTAssertTrue(names.contains("Жим лёжа"), "\(level.rawValue) н\(week.number) д\(day.number)")
                    XCTAssertTrue(names.contains { $0.contains("бицепс") }, "руки: \(level.rawValue) д\(day.number)")
                    XCTAssertTrue(names.contains { $0.contains("Подтягивания") || $0.contains("Тяга") },
                                  "тяга: \(level.rawValue) д\(day.number)")
                    // У demo кор выбран «Копенгагенская планка» — проверяем именно его.
                    XCTAssertTrue(names.contains("Копенгагенская планка"), "кор: \(level.rawValue) д\(day.number)")
                }
            }
        }
    }

    /// Волны из «Верх / Низ» в фулбади быть не должно — прогрессия линейная.
    func testFullBodyHasNoBenchWave() {
        for level in FullBodyLevel.allCases {
            let sessions = fullBodyPlan(level).weeks.flatMap { week in
                week.days.flatMap { $0.exercises.compactMap(\.benchSession) }
            }
            XCTAssertTrue(sessions.isEmpty, level.rawValue)
        }
        let profile = ProgramProfile(fullBodyInput: .demo, level: .aboutYear)
        XCTAssertTrue(profile.benchWave.isEmpty)
        XCTAssertFalse(profile.carriesBenchWave(day: 1))
    }

    /// У новичка вес приседа растёт от тренировки к тренировке, а не раз в неделю.
    func testUnderYearGrowsEverySession() {
        let plan = fullBodyPlan(.underYear)
        func squat(_ week: Int, _ day: Int) -> LoadPrescription? {
            plan.weeks[week - 1].days[day - 1].exercises.first { $0.name == "Приседания" }?.load
        }
        XCTAssertEqual(squat(1, 1), .kilograms(80))
        XCTAssertEqual(squat(1, 2), .kilograms(82.5))
        XCTAssertEqual(squat(1, 3), .kilograms(85))
        XCTAssertEqual(squat(2, 1), .kilograms(87.5))
    }

    /// Со стажем от года идёт техасская волна: объём 90 %, лёгкий 80 % от объёма,
    /// тяжёлый — сам максимум, плюс 2,5 кг в неделю.
    func testAboutYearRepeatsTexasWave() {
        let plan = fullBodyPlan(.aboutYear)
        let week1 = plan.weeks[0].days
        func squat(_ day: [ExercisePrescription]) -> ExercisePrescription? {
            day.first { $0.name == "Приседания" }
        }
        XCTAssertEqual(squat(week1[0].exercises)?.load, .kilograms(90))
        XCTAssertEqual(squat(week1[0].exercises)?.sets, 5)
        XCTAssertEqual(squat(week1[1].exercises)?.load, .kilograms(72.5))
        XCTAssertEqual(squat(week1[2].exercises)?.load, .kilograms(100))
        XCTAssertEqual(squat(week1[2].exercises)?.sets, 1)

        let week5 = plan.weeks[4].days
        XCTAssertEqual(squat(week5[2].exercises)?.load, .kilograms(110))
    }

    /// На стаже два года прибавка идёт через неделю, а не каждую.
    func testTwoYearsAddsWeightEveryOtherWeek() {
        let plan = fullBodyPlan(.twoYears)
        func top(_ week: Int) -> LoadPrescription? {
            plan.weeks[week - 1].days[2].exercises.first { $0.name == "Приседания" }?.load
        }
        XCTAssertEqual(top(1), .kilograms(100))
        XCTAssertEqual(top(2), .kilograms(100))
        XCTAssertEqual(top(3), .kilograms(102.5))
        XCTAssertEqual(top(4), .kilograms(102.5))
        XCTAssertEqual(top(5), .kilograms(105))
    }

    /// Объём должен расти со стажем — это и есть разница между уровнями.
    func testVolumeGrowsWithExperience() {
        let sets = FullBodyLevel.allCases.map { $0.volume }
        // День объёма: 3 → 5 → 5 → 6 подходов.
        XCTAssertEqual(sets.map(\.volumeSets), [3, 5, 5, 6])
        // Подсобка на руки и тягу тоже прибавляет.
        XCTAssertEqual(sets.map(\.pullSets), [2, 3, 4, 4])
        XCTAssertEqual(sets.map(\.armSets), [2, 3, 3, 4])
        // Добивка появляется только с опытом.
        XCTAssertEqual(sets.map(\.backoffSets), [0, 0, 1, 2])
    }

    /// Набор упражнений меняется со стажем, а не только число подходов.
    func testAccessorySlotsDependOnExperience() {
        XCTAssertEqual(FullBodyLevel.underYear.accessorySlots, [.back, .arms, .core])
        XCTAssertEqual(FullBodyLevel.aboutYear.accessorySlots, [.back, .arms, .core])
        XCTAssertEqual(FullBodyLevel.overYear.accessorySlots, [.back, .pull, .press, .arms, .core])
        XCTAssertEqual(FullBodyLevel.twoYears.accessorySlots, [.back, .pull, .press, .arms, .core])
    }

    func testVerticalPressAndExtraPullAppearOnlyWithExperience() {
        let about = fullBodyPlan(.aboutYear).weeks[0].days
        let over = fullBodyPlan(.overYear).weeks[0].days

        XCTAssertFalse(about.flatMap { $0.exercises.map(\.name) }.contains("Жим штанги стоя"))
        XCTAssertTrue(over[0].exercises.map(\.name).contains("Жим штанги стоя"))
        // Дополнительная тяга только в лёгкий день: в остальные спина уже загружена.
        XCTAssertTrue(over[1].exercises.map(\.name).contains("Румынская тяга"))
        XCTAssertFalse(over[0].exercises.map(\.name).contains("Румынская тяга"))
    }

    /// У добивки своё имя и свой идентификатор: иначе она слилась бы с рабочим
    /// подходом в отметках и подменила бы вес в истории.
    func testBackoffIsASeparateExercise() {
        let heavy = fullBodyPlan(.twoYears).weeks[0].days[2].exercises
        let working = heavy.first { $0.name == "Приседания" }
        let backoff = heavy.first { $0.name == "Приседания · добивка" }

        XCTAssertEqual(working?.load, .kilograms(100))
        XCTAssertEqual(working?.sets, 1)
        XCTAssertEqual(backoff?.load, .kilograms(90))
        XCTAssertEqual(backoff?.sets, 2)
        XCTAssertEqual(backoff?.id, "squat-backoff")
        XCTAssertNotEqual(working?.id, backoff?.id)

        XCTAssertNil(fullBodyPlan(.aboutYear).weeks[0].days[2].exercises.first { $0.name.contains("добивка") })
    }

    func testFullBodyProfileCountsFromFiveRepMax() {
        let profile = ProgramProfile(fullBodyInput: .demo, level: .overYear)
        XCTAssertEqual(profile.programKind, .fullBody)
        XCTAssertEqual(profile.maximumLabel, "5ПМ")
        XCTAssertEqual(profile.fullBodyLevel, .overYear)
        XCTAssertEqual(profile.trainingDayCount, 3)
        XCTAssertEqual(profile.totalDays, 36)
    }

    /// Выбранная подсобка подставляется вместо стандартной.
    func testFullBodyUsesChosenAccessories() {
        var input = ProgramInput.demo
        input.back = "Тяга Пендли"
        input.core = "Подъём ног в висе"
        let names = FullBodyCalculator.generate(input: input, level: .aboutYear)
            .weeks[0].days[0].exercises.map(\.name)
        XCTAssertTrue(names.contains("Тяга Пендли"))
        XCTAssertTrue(names.contains("Подъём ног в висе"))
    }

    // MARK: - Своя программа

    func testCustomSkeletonMatchesTemplate() {
        let program = CustomProgram.skeleton(template: .pushPullLegs, name: "Моя", weeks: 6)
        XCTAssertEqual(program.days.count, 3)
        XCTAssertEqual(program.days.map(\.title), ["ЖИМ", "ТЯГА", "НОГИ"])
        XCTAssertFalse(program.isRunnable)
    }

    func testCustomProgramGrowsWeightByWeek() {
        var program = CustomProgram.skeleton(template: .fullBody, name: "Моя", weeks: 4)
        program.days[0].exercises = [
            CustomExercise(name: "Приседания", sets: 3, reps: "5", kilograms: 100, loadText: nil, weeklyIncrement: 2.5)
        ]
        program.days[1].exercises = [
            CustomExercise(name: "Планка", sets: 3, reps: "60 с", kilograms: nil, loadText: "свой вес", weeklyIncrement: 0)
        ]
        program.days[2].exercises = program.days[0].exercises

        let plan = program.plan
        XCTAssertEqual(plan.weeks.count, 4)
        XCTAssertEqual(plan.weeks[0].days[0].exercises[0].load, .kilograms(100))
        XCTAssertEqual(plan.weeks[3].days[0].exercises[0].load, .kilograms(107.5))
        // Без веса прибавка не работает — подпись остаётся как есть.
        XCTAssertEqual(plan.weeks[3].days[1].exercises[0].load, .repRange("свой вес"))
    }

    func testCustomProfileBuildsPlanAndDayCount() {
        var program = CustomProgram.skeleton(template: .upperLower, name: "Моя", weeks: 5)
        for index in program.days.indices {
            program.days[index].exercises = [
                CustomExercise(name: "Упражнение", sets: 3, reps: "8", kilograms: 50, loadText: nil, weeklyIncrement: 0)
            ]
        }
        XCTAssertTrue(program.isRunnable)

        let profile = ProgramProfile(customProgram: program)
        XCTAssertEqual(profile.programKind, .custom)
        XCTAssertEqual(profile.trainingDayCount, 4)
        XCTAssertEqual(profile.workoutPlan.weeks.count, 5)
        XCTAssertEqual(profile.totalDays, 20)
        XCTAssertFalse(profile.carriesBenchWave(day: 1))
        XCTAssertEqual(profile.defaultWeekdays.count, 4)
    }

    func testBackupKeepsCustomProgramAndCycle() throws {
        var program = CustomProgram.skeleton(template: .fullBody, name: "Моя", weeks: 3)
        for index in program.days.indices {
            program.days[index].exercises = [
                CustomExercise(name: "Тяга", sets: 4, reps: "6", kilograms: 60, loadText: nil, weeklyIncrement: 5)
            ]
        }
        let profile = ProgramProfile(customProgram: program)
        profile.startNewCycle(squat: 100, bench: 100, deadlift: 100)

        let archive = try BackupService.archive(from: try BackupService.export([profile]))
        let snapshot = try XCTUnwrap(archive.profiles.first)
        XCTAssertEqual(snapshot.programKind, "CUSTOM")
        XCTAssertEqual(snapshot.cycleNumber, 2)
        XCTAssertEqual(snapshot.customProgram?.days.count, 3)
        XCTAssertEqual(snapshot.customProgram?.weeks, 3)
    }

    // MARK: - Резервная копия

    func testBackupCarriesLogAndSkips() throws {
        let profile = ProgramProfile(input: .demo)
        let squat = exercises(profile, week: 1, day: 1)[0]
        profile.recordSet(week: 1, day: 1, exercise: squat, index: 0, reps: 4, weight: 75)
        profile.markSkipped(week: 4, day: 2, holdsProgression: true)
        profile.keepScreenOn = false

        let archive = try BackupService.archive(from: try BackupService.export([profile]))
        let snapshot = try XCTUnwrap(archive.profiles.first)

        XCTAssertEqual(snapshot.setLog?.count, 1)
        XCTAssertEqual(snapshot.setLog?.values.first?.reps, 4)
        XCTAssertEqual(snapshot.skipped?.count, 1)
        XCTAssertEqual(snapshot.skipped?.first?.week, 4)
        XCTAssertEqual(snapshot.skipped?.first?.holdsProgression, true)
        XCTAssertEqual(snapshot.keepScreenOn, false)
    }

    /// Копии, снятые раньше, этих полей не содержат — падать на них нельзя.
    func testBackupWithoutNewFieldsStillDecodes() throws {
        let profile = ProgramProfile(input: .demo)
        let data = try BackupService.export([profile])
        var object = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        var profiles = try XCTUnwrap(object["profiles"] as? [[String: Any]])
        for key in ["setLog", "skipped", "keepScreenOn", "planEdits"] {
            profiles[0].removeValue(forKey: key)
        }
        object["profiles"] = profiles

        let trimmed = try JSONSerialization.data(withJSONObject: object)
        let archive = try BackupService.archive(from: trimmed)
        XCTAssertNil(archive.profiles[0].setLog)
        XCTAssertNil(archive.profiles[0].skipped)
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
