package com.texasprogram.app

import com.texasprogram.app.model.AdditionalExerciseCategory
import com.texasprogram.app.model.LoadPrescription
import com.texasprogram.app.model.ProgramInput
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.ScheduleMode
import com.texasprogram.app.model.TrainingLevel
import com.texasprogram.app.model.UpperLowerInput
import com.texasprogram.app.data.BackupService
import com.texasprogram.app.service.PlateMath
import com.texasprogram.app.service.ProgramCalculator
import com.texasprogram.app.service.UpperLowerCalculator
import com.texasprogram.app.service.WorkoutScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgramCalculatorTest {

    private fun date(day: Int, month: Int, year: Int) = LocalDate.of(year, month, day)

    // MARK: - Техасская программа

    @Test
    fun roundToPlateMatchesExcelMround() {
        assertEquals(87.5, ProgramCalculator.roundToPlate(88.0), 0.001)
        assertEquals(90.0, ProgramCalculator.roundToPlate(89.0), 0.001)
        assertEquals(92.5, ProgramCalculator.roundToPlate(91.25), 0.001)
    }

    @Test
    fun basePlanHasTwelveWeeksAndThreeDays() {
        val plan = ProgramCalculator.generate(ProgramInput.demo)
        assertEquals(12, plan.weeks.size)
        assertTrue(plan.weeks.all { it.days.size == 3 })
        assertEquals(LoadPrescription.Kilograms(80.0), plan.weeks[0].days[0].exercises[0].load)
        assertEquals(LoadPrescription.Kilograms(80.0), plan.weeks[0].days[0].exercises[1].load)
        assertEquals(LoadPrescription.Kilograms(90.0), plan.weeks[0].days[2].exercises[0].load)
    }

    @Test
    fun allExcelWeekThreeLoads() {
        val plan = ProgramCalculator.generate(ProgramInput.demo)
        val expected = (0 until 12).map { 90.0 + it * 2.5 }
        plan.weeks.forEachIndexed { index, week ->
            assertEquals(LoadPrescription.Kilograms(expected[index]), week.days[2].exercises[0].load)
            assertEquals(LoadPrescription.Kilograms(expected[index]), week.days[2].exercises[1].load)
            assertEquals(LoadPrescription.Kilograms(expected[index]), week.days[2].exercises[2].load)
            assertEquals(
                LoadPrescription.Kilograms(ProgramCalculator.roundToPlate(expected[index] * 0.9)),
                week.days[0].exercises[0].load
            )
        }
    }

    @Test
    fun optionalExercisesArePropagated() {
        val input = ProgramInput.demo.copy(
            pull = AdditionalExerciseCategory.PULL.options[1],
            back = AdditionalExerciseCategory.BACK.options[0]
        )
        val day = ProgramCalculator.generate(input).weeks[0].days[0]
        assertTrue(day.exercises.any { it.name == input.pull })
        assertFalse(day.exercises.any { it.name == input.back })
    }

    @Test
    fun peakingEndsWithOneRepMaxTest() {
        val plan = ProgramCalculator.generatePeaking(ProgramInput.demo)
        assertEquals(4, plan.weeks.size)
        assertTrue(plan.weeks.last().days[2].exercises.take(3).all { it.load == LoadPrescription.TestOneRepMax })
    }

    @Test
    fun beginnerPeakingMatchesThreeWeekExcelOption() {
        val plan = ProgramCalculator.generatePeaking(ProgramInput.demo.copy(level = TrainingLevel.BEGINNER))
        assertEquals(3, plan.weeks.size)
        assertTrue(plan.weeks.last().days[2].exercises.take(3).all { it.load == LoadPrescription.TestOneRepMax })
    }

    // MARK: - Верх / Низ

    @Test
    fun upperLowerPlanHasSevenWeeksAndFourDays() {
        val plan = UpperLowerCalculator.generate(UpperLowerInput.demo)
        assertEquals(7, plan.weeks.size)
        assertTrue(plan.weeks.all { it.days.size == 4 })
        assertEquals(LoadPrescription.Kilograms(85.0), plan.weeks[0].days[1].exercises[0].load)
        assertEquals(LoadPrescription.Kilograms(85.0), plan.weeks[0].days[1].exercises[1].load)
        assertEquals(LoadPrescription.Kilograms(75.0), plan.weeks[0].days[3].exercises[0].load)
        assertEquals(LoadPrescription.Kilograms(100.0), plan.weeks[0].days[3].exercises[1].load)
    }

    @Test
    fun benchWaveComesFromFourteenSessionSheet() {
        val plan = UpperLowerCalculator.generate(UpperLowerInput.demo)
        val firstBench = plan.weeks[0].days[0].exercises[0]
        val lastBench = plan.weeks[6].days[2].exercises[0]
        assertEquals(5, firstBench.sets)
        assertEquals("6 / 5 / 5 / 4 / 4", firstBench.reps)
        assertEquals(3, lastBench.sets)
        assertTrue(lastBench.name.contains("рекорд"))
    }

    @Test
    fun benchWaveHasFourteenSessions() {
        val wave = UpperLowerCalculator.benchWave(UpperLowerInput.demo)
        assertEquals(14, wave.size)
        assertEquals(1, wave[0].week)
        assertEquals(1, wave[0].dayNumber)
        assertEquals(3, wave[1].dayNumber)
        assertEquals(7, wave[13].week)
    }

    // MARK: - Синхронизация отметок

    @Test
    fun dayAndBenchCompletionStayInSync() {
        var profile = ProgramProfile.upperLower(UpperLowerInput.demo)
        profile = profile.toggleCompleted(1, 1)
        assertTrue(profile.isBenchCompleted(1))
        profile = profile.toggleBenchCompleted(2)
        assertTrue(profile.isCompleted(1, 3))
        profile = profile.toggleCompleted(1, 3)
        assertFalse(profile.isBenchCompleted(2))
    }

    // MARK: - Блины и разминка

    @Test
    fun platesSplitEvenlyPerSide() {
        assertEquals(listOf(25.0, 15.0), PlateMath.perSide(100.0))
        assertEquals(listOf(25.0, 25.0, 10.0), PlateMath.perSide(140.0))
        assertEquals(emptyList<Double>(), PlateMath.perSide(20.0))
        assertEquals(listOf(20.0, 1.25), PlateMath.perSide(62.5))
    }

    @Test
    fun platesRefuseImpossibleWeights() {
        assertNull(PlateMath.perSide(15.0))
        assertNull(PlateMath.perSide(21.0))
    }

    @Test
    fun warmupsRiseToWorkingWeight() {
        val sets = PlateMath.warmups(140.0)
        assertEquals(PlateMath.BAR_WEIGHT, sets.first().weight, 0.001)
        assertTrue(sets.all { it.weight < 140.0 })
        assertEquals(sets.map { it.weight }.sorted(), sets.map { it.weight })
        assertEquals(sets.size, sets.map { it.weight }.toSet().size)
    }

    @Test
    fun warmupsSkippedForLightWeights() {
        assertTrue(PlateMath.warmups(20.0).isEmpty())
    }

    // MARK: - Пикирование

    @Test
    fun peakingSwitchesPlanAndKeepsBaseMarks() {
        var profile = ProgramProfile.texas(ProgramInput.demo).toggleCompleted(1, 1)
        assertEquals(12, profile.workoutPlan.weeks.size)
        assertEquals(1, profile.completedDayCount)

        profile = profile.copy(peakingActive = true)
        assertTrue(profile.isPeaking)
        assertEquals(4, profile.workoutPlan.weeks.size)
        assertEquals(0, profile.completedDayCount)
        assertFalse(profile.isCompleted(1, 1))

        profile = profile.toggleCompleted(1, 1)
        assertTrue(profile.completedDayKeys.contains("peak-1-1"))

        profile = profile.copy(peakingActive = false)
        assertEquals(12, profile.workoutPlan.weeks.size)
        assertTrue(profile.isCompleted(1, 1))
        assertEquals(1, profile.completedDayCount)
    }

    // MARK: - История и подходы

    @Test
    fun completionIsLoggedWithWeights() {
        val day = date(27, 7, 2026)
        var profile = ProgramProfile.texas(ProgramInput.demo).toggleCompleted(1, 3, day)

        assertEquals(1, profile.completionLog.size)
        val record = profile.completionLog[0]
        assertEquals(day.toEpochDay(), record.epochDay)
        assertEquals(90.0, record.squat!!, 0.001)
        assertEquals(90.0, record.deadlift!!, 0.001)

        profile = profile.toggleCompleted(1, 3, day)
        assertTrue(profile.completionLog.isEmpty())
    }

    @Test
    fun tappingDotFillsUpToIt() {
        val base = ProgramProfile.texas(ProgramInput.demo)
        val squat = base.workoutPlan.weeks[0].days[0].exercises[0]

        var profile = base.toggleSet(1, 1, squat, 2)
        assertEquals(3, profile.completedSets(1, 1, squat))

        profile = profile.toggleSet(1, 1, squat, 2)
        assertEquals(2, profile.completedSets(1, 1, squat))
    }

    @Test
    fun completingDayFillsDotsAndUncompletingClearsThem() {
        val base = ProgramProfile.texas(ProgramInput.demo)
        val squat = base.workoutPlan.weeks[0].days[0].exercises[0]

        var profile = base.toggleCompleted(1, 1)
        assertEquals(squat.sets, profile.completedSets(1, 1, squat))

        profile = profile.toggleCompleted(1, 1)
        assertEquals(0, profile.completedSets(1, 1, squat))
    }

    // MARK: - Резервная копия

    @Test
    fun backupRoundTripKeepsEverything() {
        val profile = ProgramProfile.upperLower(UpperLowerInput.demo, "Основной")
            .toggleCompleted(1, 1, date(27, 7, 2026))
            .setWeekday(4, 2)
            .copy(scheduleMode = ScheduleMode.QUEUE)

        val archive = BackupService.parse(BackupService.export(listOf(profile)))
        assertEquals(1, archive.version)
        assertEquals(1, archive.profiles.size)

        val restored = BackupService.profile(archive.profiles[0])
        assertEquals("Основной", restored.name)
        assertEquals(ScheduleMode.QUEUE, restored.scheduleMode)
        assertEquals(listOf(2, 4, 5, 6), restored.scheduleWeekdays)
        assertEquals(listOf("1-1"), restored.completedDayKeys)
        assertEquals(listOf(1), restored.completedBenchSessions)
        assertEquals(1, restored.completionLog.size)
    }

    // MARK: - Режим очереди

    /// Профиль в режиме «по дням недели» — значение по умолчанию.
    private fun weekdayProfile() = ProgramProfile.upperLower(UpperLowerInput.demo)

    /// Профиль с очередью.
    private fun queueProfile() =
        ProgramProfile.upperLower(UpperLowerInput.demo).copy(scheduleMode = ScheduleMode.QUEUE)

    @Test
    fun weekdayModeIsDefault() {
        assertEquals(ScheduleMode.WEEKDAY, ProgramProfile.upperLower(UpperLowerInput.demo).scheduleMode)
        assertEquals(ScheduleMode.WEEKDAY, ProgramProfile.texas(ProgramInput.demo).scheduleMode)
    }

    /// Главный случай: неделя 1 закрыта, во второй сделаны понедельник и вторник,
    /// четверг с пятницей пропущены. Завтра понедельник — значит понедельничная
    /// тренировка третьей недели, а не пропущенный четверг второй.
    @Test
    fun weekdaySlotTakesOwnDayTypeFromNextUncompletedWeek() {
        var profile = weekdayProfile()
        for (day in 1..4) profile = profile.toggleCompleted(1, day)
        profile = profile.toggleCompleted(2, 1)
        profile = profile.toggleCompleted(2, 2)
        profile = profile.copy(lastCompletionEpochDay = null)

        val sunday = date(26, 7, 2026)
        val schedule = WorkoutScheduler.build(profile, profile.workoutPlan, sunday)

        assertNull(schedule.today)
        assertTrue(schedule.isRestDay)
        assertEquals(3, schedule.upcoming?.week)
        assertEquals(1, schedule.upcoming?.day?.number)
        assertEquals(date(27, 7, 2026), schedule.upcoming?.date)
        assertEquals("ПОНЕДЕЛЬНИК · ВЕРХ ТЯЖЁЛЫЙ", schedule.upcoming?.fullTitle)
    }

    /// Главное: в понедельник понедельничные вспомогательные упражнения,
    /// но жим — следующий по волне, а не привязанный к дню недели.
    @Test
    fun benchWaveIsIndependentFromWeekday() {
        var profile = weekdayProfile()
        for (day in 1..4) profile = profile.toggleCompleted(1, day)
        profile = profile.toggleCompleted(2, 1)
        profile = profile.toggleCompleted(2, 2)
        profile = profile.copy(lastCompletionEpochDay = null)

        assertEquals(4, profile.nextBenchSession)

        val schedule = WorkoutScheduler.build(profile, profile.workoutPlan, date(26, 7, 2026))
        val monday = schedule.upcoming!!

        // День — понедельничный, третьей недели.
        assertEquals(3, monday.week)
        assertEquals(1, monday.day.number)
        assertEquals("ВЕРХ ТЯЖЁЛЫЙ", monday.day.title)
        // А жим — четвёртый по волне.
        assertEquals(4, monday.benchSession)

        val exercises = profile.exercises(monday)
        val bench = exercises.firstOrNull { it.benchSession != null }
        assertEquals(4, bench?.benchSession)
        assertEquals("Жим лёжа · негатив", bench?.name)
        assertEquals(LoadPrescription.RepRange("90 / 90 / 95 / 95 / 105 кг"), bench?.load)
        // Вспомогательные остались понедельничными.
        assertTrue(exercises.any { it.name == "Жим гантелей на наклонной скамье (30°)" })
        assertEquals(8, exercises.size)
    }

    /// Волна раздаётся верхним дням по порядку календаря.
    @Test
    fun benchWaveIsHandedOutInCalendarOrder() {
        var profile = weekdayProfile()
        for (day in 1..4) profile = profile.toggleCompleted(1, day)
        profile = profile.toggleCompleted(2, 1)
        profile = profile.toggleCompleted(2, 2)
        profile = profile.copy(lastCompletionEpochDay = null)

        val schedule = WorkoutScheduler.build(profile, profile.workoutPlan, date(26, 7, 2026))
        val upperDays = schedule.allPending.filter { it.benchSession != null }.take(3)

        assertEquals(listOf(4, 5, 6), upperDays.map { it.benchSession })
        assertEquals(
            listOf(date(27, 7, 2026), date(30, 7, 2026), date(3, 8, 2026)),
            upperDays.map { it.date }
        )
    }

    /// Отметка верхнего дня двигает волну на одну тренировку, снятие — возвращает.
    @Test
    fun completingUpperDayAdvancesWaveByOne() {
        var profile = weekdayProfile()
        for (day in 1..4) profile = profile.toggleCompleted(1, day)
        profile = profile.toggleCompleted(2, 1)
        profile = profile.toggleCompleted(2, 2)
        assertEquals(listOf(1, 2, 3), profile.completedBenchSessions.sorted())

        // Закрыли понедельник третьей недели — сделан жим №4.
        profile = profile.toggleCompleted(3, 1)
        assertEquals(listOf(1, 2, 3, 4), profile.completedBenchSessions.sorted())
        assertEquals(5, profile.nextBenchSession)

        // Сняли отметку — волна откатилась.
        profile = profile.toggleCompleted(3, 1)
        assertEquals(listOf(1, 2, 3), profile.completedBenchSessions.sorted())

        // Дни низа волну не двигают.
        profile = profile.toggleCompleted(2, 4)
        assertEquals(listOf(1, 2, 3), profile.completedBenchSessions.sorted())
    }

    /// Пропущенный четверг не теряется: он достаётся ближайшему четвергу.
    @Test
    fun weekdayModeKeepsMissedWorkoutForItsOwnWeekday() {
        var profile = weekdayProfile()
        for (day in 1..4) profile = profile.toggleCompleted(1, day)
        profile = profile.toggleCompleted(2, 1)
        profile = profile.toggleCompleted(2, 2)
        profile = profile.copy(lastCompletionEpochDay = null)

        val schedule = WorkoutScheduler.build(profile, profile.workoutPlan, date(26, 7, 2026))
        val thursday = schedule.allPending.firstOrNull { it.weekday == 5 }

        assertEquals(2, thursday?.week)
        assertEquals(3, thursday?.day?.number)
        assertEquals(date(30, 7, 2026), thursday?.date)
    }

    /// Каждый день недели идёт своим счётчиком.
    @Test
    fun weekdayModeAdvancesEachSlotIndependently() {
        var profile = weekdayProfile()
        for (day in 1..4) profile = profile.toggleCompleted(1, day)
        profile = profile.toggleCompleted(2, 1)
        profile = profile.toggleCompleted(2, 2)
        profile = profile.copy(lastCompletionEpochDay = null)

        val schedule = WorkoutScheduler.build(profile, profile.workoutPlan, date(26, 7, 2026))
        val firstFour = schedule.allPending.take(4)

        assertEquals(
            listOf(date(27, 7, 2026), date(28, 7, 2026), date(30, 7, 2026), date(31, 7, 2026)),
            firstFour.map { it.date }
        )
        assertEquals(listOf(3, 3, 2, 2), firstFour.map { it.week })
        assertEquals(listOf(1, 2, 3, 4), firstFour.map { it.day.number })
    }

    /// Случай со скриншота: сделаны неделя 1 целиком и понедельник со вторником недели 2.
    /// Четверг и пятница пропущены, сегодня воскресенье — следующая должна быть завтра.
    @Test
    fun queueMovesMissedWorkoutToNextTrainingDay() {
        var profile = queueProfile()
        for (day in 1..4) profile = profile.toggleCompleted(1, day)
        profile = profile.toggleCompleted(2, 1)
        profile = profile.toggleCompleted(2, 2)
        profile = profile.copy(lastCompletionEpochDay = null)

        val sunday = date(26, 7, 2026)
        val schedule = WorkoutScheduler.build(profile, profile.workoutPlan, sunday)

        assertTrue(schedule.overdue.isEmpty())
        assertNull(schedule.today)
        assertEquals(2, schedule.upcoming?.week)
        assertEquals(3, schedule.upcoming?.day?.number)
        assertEquals(date(27, 7, 2026), schedule.upcoming?.date)
        assertEquals("Завтра", schedule.relativeTitle(schedule.upcoming!!))
        assertEquals("ПОНЕДЕЛЬНИК · ВЕРХ ОБЪЁМНЫЙ", schedule.upcoming?.fullTitle)
    }

    @Test
    fun queueFillsFollowingSlotsInOrder() {
        var profile = queueProfile()
        for (day in 1..4) profile = profile.toggleCompleted(1, day)
        profile = profile.toggleCompleted(2, 1)
        profile = profile.toggleCompleted(2, 2)
        profile = profile.copy(lastCompletionEpochDay = null)

        val schedule = WorkoutScheduler.build(profile, profile.workoutPlan, date(26, 7, 2026))
        val firstFour = schedule.allPending.take(4)

        assertEquals(
            listOf(date(27, 7, 2026), date(28, 7, 2026), date(30, 7, 2026), date(31, 7, 2026)),
            firstFour.map { it.date }
        )
        assertEquals(listOf(2, 2, 3, 3), firstFour.map { it.week })
        assertEquals(listOf(3, 4, 1, 2), firstFour.map { it.day.number })
    }

    @Test
    fun queueSkipsTodayAfterCompletion() {
        val monday = date(27, 7, 2026)
        val profile = queueProfile().toggleCompleted(1, 1, monday)
        val schedule = WorkoutScheduler.build(profile, profile.workoutPlan, monday)

        assertNull(schedule.today)
        assertTrue(schedule.todayAlreadyDone)
        assertFalse(schedule.isRestDay)
        assertEquals(date(28, 7, 2026), schedule.upcoming?.date)
        assertEquals(2, schedule.upcoming?.day?.number)
    }

    @Test
    fun queueGivesTodaysWorkoutOnTrainingDay() {
        val monday = date(27, 7, 2026)
        val profile = queueProfile()
        val schedule = WorkoutScheduler.build(profile, profile.workoutPlan, monday)

        assertEquals(1, schedule.today?.week)
        assertEquals(1, schedule.today?.day?.number)
        assertEquals("Сегодня", schedule.relativeTitle(schedule.today!!))
    }

    @Test
    fun queueSetCurrentBenchSessionLandsOnNextSlot() {
        val sunday = date(26, 7, 2026)
        val profile = queueProfile().setCurrentBenchSession(4, sunday)

        assertEquals(listOf(1, 2, 3), profile.completedBenchSessions.sorted())
        assertEquals(date(27, 7, 2026), profile.plannedStartDate(3, sunday))

        val schedule = WorkoutScheduler.build(profile, profile.workoutPlan, sunday)
        assertEquals(2, schedule.upcoming?.week)
        assertEquals(3, schedule.upcoming?.day?.number)
        assertEquals(date(27, 7, 2026), schedule.upcoming?.date)
    }

    // MARK: - Расписание по дням недели

    @Test
    fun defaultWeekdaysMatchProgram() {
        assertEquals(listOf(2, 3, 5, 6), ProgramProfile.upperLower(UpperLowerInput.demo).weekdays)
        assertEquals(listOf(2, 4, 6), ProgramProfile.texas(ProgramInput.demo).weekdays)
    }

    @Test
    fun customWeekdayIsStored() {
        val profile = ProgramProfile.texas(ProgramInput.demo).setWeekday(5, 2)
        assertEquals(listOf(2, 5, 6), profile.weekdays)
        assertEquals("четверг", profile.weekdayName(2))
    }

    @Test
    fun freshProfileHasNoMissedWorkouts() {
        val sunday = date(26, 7, 2026)
        val profile = weekdayProfile()
            .copy(cycleStartedEpochDay = sunday.toEpochDay())
        val schedule = WorkoutScheduler.build(profile, profile.workoutPlan, sunday)

        assertTrue(schedule.overdue.isEmpty())
        assertTrue(schedule.isRestDay)
        assertEquals(1, schedule.upcoming?.week)
        assertEquals(1, schedule.upcoming?.day?.number)
        assertEquals(date(27, 7, 2026), schedule.upcoming?.date)
    }

    @Test
    fun midWeekInstallDoesNotReportEarlierDaysAsMissed() {
        val wednesday = date(29, 7, 2026)
        val profile = weekdayProfile()
            .copy(cycleStartedEpochDay = wednesday.toEpochDay())
        val schedule = WorkoutScheduler.build(profile, profile.workoutPlan, wednesday)

        assertTrue(schedule.overdue.isEmpty())
        assertEquals(3, schedule.upcoming?.day?.number)
        assertEquals(date(30, 7, 2026), schedule.upcoming?.date)
    }

    @Test
    fun mondayShowsTodaysWorkout() {
        val monday = date(27, 7, 2026)
        val profile = weekdayProfile()
            .copy(cycleStartedEpochDay = monday.toEpochDay())
        val schedule = WorkoutScheduler.build(profile, profile.workoutPlan, monday)

        assertEquals(1, schedule.today?.week)
        assertEquals(1, schedule.today?.day?.number)
        assertFalse(schedule.isRestDay)
        assertTrue(schedule.overdue.isEmpty())
        assertEquals("Сегодня", schedule.relativeTitle(schedule.today!!))
    }

    @Test
    fun completedTodayIsReported() {
        val monday = date(27, 7, 2026)
        val profile = weekdayProfile()
            .copy(cycleStartedEpochDay = monday.toEpochDay())
            .toggleCompleted(1, 1, monday)
        val schedule = WorkoutScheduler.build(profile, profile.workoutPlan, monday)

        assertNull(schedule.today)
        assertTrue(schedule.todayAlreadyDone)
        assertFalse(schedule.isRestDay)
        assertEquals(2, schedule.upcoming?.day?.number)
        assertEquals(date(28, 7, 2026), schedule.upcoming?.date)
    }

    @Test
    fun setCurrentBenchSessionMarksPreviousAndSchedulesTarget() {
        val sunday = date(26, 7, 2026)
        val profile = weekdayProfile()
            .copy(cycleStartedEpochDay = sunday.toEpochDay())
            .setCurrentBenchSession(4, sunday)

        assertEquals(listOf(1, 2, 3), profile.completedBenchSessions.sorted())
        assertTrue(profile.isCompleted(1, 4))
        assertTrue(profile.isCompleted(2, 2))
        assertFalse(profile.isCompleted(2, 3))

        val schedule = WorkoutScheduler.build(profile, profile.workoutPlan, sunday)
        assertTrue(schedule.overdue.isEmpty())
        // Жим №4 — четверговый, поэтому встаёт на ближайший четверг.
        val target = schedule.allPending.firstOrNull { it.week == 2 && it.day.number == 3 }
        assertEquals(date(30, 7, 2026), target?.date)
        // А раньше него по календарю идёт понедельник третьей недели.
        assertEquals(3, schedule.upcoming?.week)
        assertEquals(1, schedule.upcoming?.day?.number)
        assertEquals(date(27, 7, 2026), schedule.upcoming?.date)
    }

    @Test
    fun setCurrentBenchSessionThreeSchedulesTomorrow() {
        val sunday = date(26, 7, 2026)
        val profile = weekdayProfile()
            .copy(cycleStartedEpochDay = sunday.toEpochDay())
            .setCurrentBenchSession(3, sunday)

        val schedule = WorkoutScheduler.build(profile, profile.workoutPlan, sunday)
        assertEquals(2, schedule.upcoming?.week)
        assertEquals(1, schedule.upcoming?.day?.number)
        assertEquals(date(27, 7, 2026), schedule.upcoming?.date)
        assertEquals("Завтра", schedule.relativeTitle(schedule.upcoming!!))
    }
}
