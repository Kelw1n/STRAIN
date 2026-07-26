import XCTest
@testable import TexasProgram

final class ProgramCalculatorTests: XCTestCase {
    func testRoundToPlateMatchesExcelMROUND() {
        XCTAssertEqual(ProgramCalculator.roundToPlate(88), 87.5)
        XCTAssertEqual(ProgramCalculator.roundToPlate(89), 90)
        XCTAssertEqual(ProgramCalculator.roundToPlate(91.25), 92.5)
    }

    func testBasePlanHasTwelveWeeksAndThreeDays() {
        let plan = ProgramCalculator.generate(input: .demo)
        XCTAssertEqual(plan.weeks.count, 12)
        XCTAssertTrue(plan.weeks.allSatisfy { $0.days.count == 3 })
        XCTAssertEqual(plan.weeks[0].days[0].exercises[0].load, .kilograms(80))
        XCTAssertEqual(plan.weeks[0].days[0].exercises[1].load, .kilograms(80))
        XCTAssertEqual(plan.weeks[0].days[2].exercises[0].load, .kilograms(90))
    }

    func testAllExcelWeekThreeLoads() {
        let plan = ProgramCalculator.generate(input: .demo)
        let expectedDayThree = stride(from: 90.0, through: 117.5, by: 2.5).map { $0 }
        for (index, week) in plan.weeks.enumerated() {
            XCTAssertEqual(week.days[2].exercises[0].load, .kilograms(expectedDayThree[index]))
            XCTAssertEqual(week.days[2].exercises[1].load, .kilograms(expectedDayThree[index]))
            XCTAssertEqual(week.days[2].exercises[2].load, .kilograms(expectedDayThree[index]))
            XCTAssertEqual(week.days[0].exercises[0].load, .kilograms(ProgramCalculator.roundToPlate(expectedDayThree[index] * 0.9)))
        }
    }

    func testOptionalExercisesArePropagated() {
        var input = ProgramInput.demo
        input.pull = AdditionalExerciseCategory.pull.options[1]
        input.back = AdditionalExerciseCategory.back.options[0]
        let day = ProgramCalculator.generate(input: input).weeks[0].days[0]
        XCTAssertTrue(day.exercises.contains { $0.name == input.pull! })
        XCTAssertFalse(day.exercises.contains { $0.name == input.back })
    }

    func testPeakingEndsWithOneRepMaxTest() {
        let plan = ProgramCalculator.generatePeaking(input: .demo)
        XCTAssertEqual(plan.weeks.count, 4)
        XCTAssertTrue(plan.weeks.last!.days[2].exercises.prefix(3).allSatisfy { $0.load == .testOneRepMax })
    }

    func testBeginnerPeakingMatchesThreeWeekExcelOption() {
        var input = ProgramInput.demo
        input.level = .beginner
        let plan = ProgramCalculator.generatePeaking(input: input)
        XCTAssertEqual(plan.weeks.count, 3)
        XCTAssertTrue(plan.weeks.last!.days[2].exercises.prefix(3).allSatisfy { $0.load == .testOneRepMax })
    }

    func testUpperLowerPlanHasSevenWeeksAndFourDays() {
        let plan = UpperLowerCalculator.generate(input: .demo)
        XCTAssertEqual(plan.weeks.count, 7)
        XCTAssertTrue(plan.weeks.allSatisfy { $0.days.count == 4 })
        XCTAssertEqual(plan.weeks[0].days[1].exercises[0].load, .kilograms(85))
        XCTAssertEqual(plan.weeks[0].days[1].exercises[1].load, .kilograms(85))
        XCTAssertEqual(plan.weeks[0].days[3].exercises[0].load, .kilograms(75))
        XCTAssertEqual(plan.weeks[0].days[3].exercises[1].load, .kilograms(100))
    }

    func testBenchWaveHasFourteenSessionsMappedToUpperDays() {
        let wave = UpperLowerCalculator.benchWave(input: .demo)
        XCTAssertEqual(wave.count, 14)
        XCTAssertEqual(wave.map(\.id), Array(1...14))
        XCTAssertEqual(wave.map(\.week), [1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7])
        XCTAssertEqual(wave[0].dayNumber, 1)
        XCTAssertEqual(wave[1].dayNumber, 3)
    }

    func testBenchWaveWeightsAreRoundedToFive() {
        let wave = UpperLowerCalculator.benchWave(input: .demo)
        XCTAssertEqual(wave[0].sets.map(\.weight), [80, 85, 85, 85, 85])
        XCTAssertEqual(wave[1].highlight, .negative)
        XCTAssertEqual(wave[13].highlight, .record)
        XCTAssertEqual(wave[13].topWeight, 110)
    }

    func testBenchWaveIsLinkedToDayPrescriptions() {
        let wave = UpperLowerCalculator.benchWave(input: .demo)
        let plan = UpperLowerCalculator.generate(input: .demo)
        XCTAssertEqual(plan.weeks[0].days[0].exercises[0].benchSession, 1)
        XCTAssertEqual(plan.weeks[0].days[2].exercises[0].benchSession, 2)
        XCTAssertEqual(plan.weeks[6].days[2].exercises[0].benchSession, 14)
        XCTAssertEqual(plan.weeks[0].days[0].exercises[0].load, .repRange(wave[0].weightsText))
        XCTAssertNil(plan.weeks[0].days[1].exercises[0].benchSession)
    }

    func testBenchSessionCompletionTracking() {
        let profile = ProgramProfile(upperLowerInput: .demo)
        XCTAssertEqual(profile.nextBenchSession, 1)
        profile.toggleBenchCompleted(1)
        XCTAssertTrue(profile.isBenchCompleted(1))
        XCTAssertEqual(profile.completedBenchCount, 1)
        XCTAssertEqual(profile.nextBenchSession, 2)
        profile.toggleBenchCompleted(1)
        XCTAssertEqual(profile.completedBenchCount, 0)
        XCTAssertEqual(profile.nextBenchSession, 1)
    }

    func testExerciseIdentityIsStableAcrossRegeneration() {
        let first = ProgramCalculator.generate(input: .demo).weeks[0].days[0].exercises.map(\.id)
        var other = ProgramInput.demo
        other.squat5RM = 120
        _ = ProgramCalculator.generate(input: other)
        let second = ProgramCalculator.generate(input: .demo).weeks[0].days[0].exercises.map(\.id)
        XCTAssertEqual(first, second)
        XCTAssertEqual(Set(first).count, first.count)
    }

    func testDayAndBenchCompletionStayInSync() {
        let profile = ProgramProfile(upperLowerInput: .demo)

        profile.toggleCompleted(week: 1, day: 1)
        XCTAssertTrue(profile.isBenchCompleted(1))

        profile.toggleBenchCompleted(2)
        XCTAssertTrue(profile.isCompleted(week: 1, day: 3))
        XCTAssertEqual(profile.completedDayCount, 2)
        XCTAssertEqual(profile.nextBenchSession, 3)

        profile.toggleBenchCompleted(2)
        XCTAssertFalse(profile.isCompleted(week: 1, day: 3))
        XCTAssertEqual(profile.nextBenchSession, 2)

        // Дни низа с волной жима не связаны.
        profile.toggleCompleted(week: 1, day: 2)
        XCTAssertEqual(profile.completedBenchCount, 1)
    }

    func testTexasCompletionDoesNotTouchBenchWave() {
        let profile = ProgramProfile(input: .demo)
        profile.toggleCompleted(week: 1, day: 1)
        XCTAssertTrue(profile.isCompleted(week: 1, day: 1))
        XCTAssertTrue(profile.completedBenchSessions.isEmpty)
    }

    // MARK: - Расписание по дням недели

    private var utcCalendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar
    }

    /// Начало суток — расписание оперирует днями, а не моментами времени.
    private func date(_ day: Int, _ month: Int, _ year: Int) -> Date {
        utcCalendar.date(from: DateComponents(year: year, month: month, day: day))!
    }

    func testDefaultWeekdaysMatchProgram() {
        XCTAssertEqual(ProgramProfile(upperLowerInput: .demo).weekdays, [2, 3, 5, 6])
        XCTAssertEqual(ProgramProfile(input: .demo).weekdays, [2, 4, 6])
    }

    func testCustomWeekdayIsStored() {
        let profile = ProgramProfile(input: .demo)
        profile.setWeekday(5, forDay: 2)
        XCTAssertEqual(profile.weekdays, [2, 5, 6])
        XCTAssertEqual(profile.weekdayName(forDay: 2), "четверг")
    }

    func testSundayShowsRestDayAndNextMondayWorkout() {
        let profile = ProgramProfile(upperLowerInput: .demo)
        profile.cycleStartedAt = date(1, 7, 2026)
        // Первая неделя закрыта целиком, во второй сделаны понедельник и вторник.
        for day in 1...4 { profile.toggleCompleted(week: 1, day: day) }
        profile.toggleCompleted(week: 2, day: 1)
        profile.toggleCompleted(week: 2, day: 2)

        let sunday = date(26, 7, 2026)
        let schedule = WorkoutScheduler.build(profile: profile, plan: profile.workoutPlan, now: sunday, calendar: utcCalendar)

        XCTAssertNil(schedule.today)
        XCTAssertTrue(schedule.isRestDay)
        XCTAssertEqual(schedule.overdue.map(\.day.number), [3, 4])
        XCTAssertEqual(schedule.upcoming?.week, 3)
        XCTAssertEqual(schedule.upcoming?.day.number, 1)
        XCTAssertEqual(schedule.upcoming?.date, date(27, 7, 2026))
        XCTAssertEqual(schedule.relativeTitle(for: schedule.upcoming!, calendar: utcCalendar), "Завтра")
    }

    func testFreshProfileHasNoMissedWorkouts() {
        let profile = ProgramProfile(upperLowerInput: .demo)
        let sunday = date(26, 7, 2026)
        profile.cycleStartedAt = sunday
        let schedule = WorkoutScheduler.build(profile: profile, plan: profile.workoutPlan, now: sunday, calendar: utcCalendar)

        XCTAssertTrue(schedule.overdue.isEmpty)
        XCTAssertTrue(schedule.isRestDay)
        // Неделя 1 целиком в прошлом относительно воскресенья — переносится на следующую.
        XCTAssertEqual(schedule.upcoming?.week, 1)
        XCTAssertEqual(schedule.upcoming?.day.number, 1)
        XCTAssertEqual(schedule.upcoming?.date, date(27, 7, 2026))
    }

    func testMidWeekInstallDoesNotReportEarlierDaysAsMissed() {
        let profile = ProgramProfile(upperLowerInput: .demo)
        let wednesday = date(29, 7, 2026)
        profile.cycleStartedAt = wednesday
        let schedule = WorkoutScheduler.build(profile: profile, plan: profile.workoutPlan, now: wednesday, calendar: utcCalendar)

        XCTAssertTrue(schedule.overdue.isEmpty)
        XCTAssertEqual(schedule.upcoming?.day.number, 3)
        XCTAssertEqual(schedule.upcoming?.date, date(30, 7, 2026))
    }

    func testSetCurrentBenchSessionMarksPreviousAndSchedulesTarget() {
        let profile = ProgramProfile(upperLowerInput: .demo)
        let sunday = date(26, 7, 2026)
        profile.cycleStartedAt = sunday
        profile.setCurrentBenchSession(4, now: sunday, calendar: utcCalendar)

        XCTAssertEqual(profile.completedBenchSessions.sorted(), [1, 2, 3])
        XCTAssertTrue(profile.isCompleted(week: 1, day: 4))
        XCTAssertTrue(profile.isCompleted(week: 2, day: 2))
        XCTAssertFalse(profile.isCompleted(week: 2, day: 3))

        let schedule = WorkoutScheduler.build(profile: profile, plan: profile.workoutPlan, now: sunday, calendar: utcCalendar)
        XCTAssertTrue(schedule.overdue.isEmpty)
        XCTAssertEqual(schedule.upcoming?.week, 2)
        XCTAssertEqual(schedule.upcoming?.day.number, 3)
        // Четверг ближайшей недели.
        XCTAssertEqual(schedule.upcoming?.date, date(30, 7, 2026))
    }

    func testSetCurrentWorkoutOnMondaySchedulesTomorrow() {
        let profile = ProgramProfile(upperLowerInput: .demo)
        let sunday = date(26, 7, 2026)
        profile.setCurrentBenchSession(3, now: sunday, calendar: utcCalendar)

        let schedule = WorkoutScheduler.build(profile: profile, plan: profile.workoutPlan, now: sunday, calendar: utcCalendar)
        XCTAssertEqual(schedule.upcoming?.week, 2)
        XCTAssertEqual(schedule.upcoming?.day.number, 1)
        XCTAssertEqual(schedule.upcoming?.date, date(27, 7, 2026))
        XCTAssertEqual(schedule.relativeTitle(for: schedule.upcoming!, calendar: utcCalendar), "Завтра")
    }

    func testMondayShowsTodaysWorkout() {
        let profile = ProgramProfile(upperLowerInput: .demo)
        let monday = date(27, 7, 2026)
        let schedule = WorkoutScheduler.build(profile: profile, plan: profile.workoutPlan, now: monday, calendar: utcCalendar)

        XCTAssertEqual(schedule.today?.week, 1)
        XCTAssertEqual(schedule.today?.day.number, 1)
        XCTAssertFalse(schedule.isRestDay)
        XCTAssertTrue(schedule.overdue.isEmpty)
        XCTAssertEqual(schedule.relativeTitle(for: schedule.today!, calendar: utcCalendar), "Сегодня")
    }

    func testCompletedTodayIsReported() {
        let profile = ProgramProfile(upperLowerInput: .demo)
        profile.toggleCompleted(week: 1, day: 1)
        let monday = date(27, 7, 2026)
        let schedule = WorkoutScheduler.build(profile: profile, plan: profile.workoutPlan, now: monday, calendar: utcCalendar)

        XCTAssertNil(schedule.today)
        XCTAssertTrue(schedule.todayAlreadyDone)
        XCTAssertFalse(schedule.isRestDay)
        XCTAssertEqual(schedule.upcoming?.day.number, 2)
        XCTAssertEqual(schedule.upcoming?.date, date(28, 7, 2026))
    }

    func testUpperLowerBenchWaveComesFromFourteenSessionSheet() {
        let plan = UpperLowerCalculator.generate(input: .demo)
        let firstBench = plan.weeks[0].days[0].exercises[0]
        let lastBench = plan.weeks[6].days[2].exercises[0]
        XCTAssertEqual(firstBench.sets, 5)
        XCTAssertEqual(firstBench.reps, "6 / 5 / 5 / 4 / 4")
        XCTAssertEqual(lastBench.sets, 3)
        XCTAssertTrue(lastBench.name.contains("рекорд"))
    }
}
