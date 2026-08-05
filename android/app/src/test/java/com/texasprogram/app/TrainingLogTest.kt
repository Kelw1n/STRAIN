package com.texasprogram.app

import com.texasprogram.app.data.BackupService
import com.texasprogram.app.model.AdditionalExerciseCategory
import com.texasprogram.app.model.CustomExercise
import com.texasprogram.app.model.CustomProgram
import com.texasprogram.app.model.ExercisePrescription
import com.texasprogram.app.model.LoadPrescription
import com.texasprogram.app.model.PlanEditScope
import com.texasprogram.app.model.ProgramInput
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.SplitTemplate
import com.texasprogram.app.model.TrainingProgramKind
import com.texasprogram.app.service.FullBodyCalculator
import com.texasprogram.app.service.FullBodyLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/// Факт подхода, откат, порядок, тоннаж, серия, новый цикл, фулбади и своя программа.
class TrainingLogTest {

    private fun date(day: Int, month: Int, year: Int) = LocalDate.of(year, month, day)

    private fun exercises(profile: ProgramProfile, week: Int, day: Int): List<ExercisePrescription> =
        profile.workoutPlan.weeks.firstOrNull { it.number == week }
            ?.days?.firstOrNull { it.number == day }?.exercises.orEmpty()

    // MARK: - Факт подхода

    @Test
    fun recordedSetFillsDotAndStaysInLog() {
        val base = ProgramProfile.texas(ProgramInput.demo)
        val squat = exercises(base, 1, 1)[0]

        val profile = base.recordSet(1, 1, squat, 2, 3, 82.5)

        assertEquals(3, profile.setEntry(1, 1, squat, 2)?.reps)
        assertEquals(82.5, profile.setEntry(1, 1, squat, 2)!!.weight, 0.001)
        // Запись факта закрывает и точку: отмечать одно и то же дважды незачем.
        assertEquals(3, profile.completedSets(1, 1, squat))
    }

    @Test
    fun recordedSetDoesNotLowerDoneCount() {
        val base = ProgramProfile.texas(ProgramInput.demo)
        val squat = exercises(base, 1, 1)[0]
        var profile = base.toggleSet(1, 1, squat, 4)
        assertEquals(5, profile.completedSets(1, 1, squat))

        profile = profile.recordSet(1, 1, squat, 0, 5, 80.0)
        assertEquals(5, profile.completedSets(1, 1, squat))
    }

    @Test
    fun historyKeepsPlannedAndActualApart() {
        val day = date(27, 7, 2026)
        val base = ProgramProfile.texas(ProgramInput.demo)
        val squat = exercises(base, 1, 1)[0]
        val profile = base.recordSet(1, 1, squat, 0, 3, 70.0, day).toggleCompleted(1, 1, day)

        val record = profile.completionLog.first()
        assertEquals(80.0, record.squat!!, 0.001)
        assertEquals(70.0, record.actualSquat!!, 0.001)
    }

    /// Факт можно вписать после отметки — история должна догнать, не сдвинув дату.
    @Test
    fun loggingAfterCompletionUpdatesRecordKeepingDate() {
        val day = date(27, 7, 2026)
        val base = ProgramProfile.texas(ProgramInput.demo)
        val squat = exercises(base, 1, 1)[0]
        var profile = base.toggleCompleted(1, 1, day)
        assertNull(profile.completionLog.first().actualSquat)

        profile = profile.recordSet(1, 1, squat, 0, 5, 77.5, date(30, 7, 2026))
        assertEquals(77.5, profile.completionLog.first().actualSquat!!, 0.001)
        assertEquals(day.toEpochDay(), profile.completionLog.first().epochDay)
    }

    @Test
    fun clearingEntryLeavesTheDot() {
        val base = ProgramProfile.texas(ProgramInput.demo)
        val squat = exercises(base, 1, 1)[0]
        val profile = base.recordSet(1, 1, squat, 0, 5, 80.0).clearSetEntry(1, 1, squat, 0)

        assertNull(profile.setEntry(1, 1, squat, 0))
        // Точка остаётся: подход сделан, просто цифры больше не записаны.
        assertEquals(1, profile.completedSets(1, 1, squat))
    }

    // MARK: - Пропуск тренировки

    @Test
    fun skipDoesNotCompleteTheDay() {
        val profile = ProgramProfile.texas(ProgramInput.demo).markSkipped(1, 1, true)
        assertTrue(profile.isSkipped(1, 1))
        // Тренировка остаётся в расписании: её ещё можно закрыть.
        assertFalse(profile.isCompleted(1, 1))
        assertEquals(0, profile.completedDayCount)
    }

    /// Пропущенная неделя остаётся со своими весами, а замирает следующая.
    @Test
    fun holdFreezesTheNextWeek() {
        val base = ProgramProfile.texas(ProgramInput.demo)
        assertEquals(LoadPrescription.Kilograms(90.0), exercises(base, 1, 3)[0].load)
        assertEquals(LoadPrescription.Kilograms(92.5), exercises(base, 2, 3)[0].load)

        val profile = base.markSkipped(1, 1, true)
        assertEquals(LoadPrescription.Kilograms(90.0), exercises(profile, 1, 3)[0].load)
        assertEquals(LoadPrescription.Kilograms(90.0), exercises(profile, 2, 3)[0].load)
        assertEquals(LoadPrescription.Kilograms(92.5), exercises(profile, 3, 3)[0].load)
    }

    @Test
    fun twoHeldWeeksShiftProgressionTwice() {
        val profile = ProgramProfile.texas(ProgramInput.demo).markSkipped(1, 1, true).markSkipped(3, 2, true)
        assertEquals(1, profile.holdCount(2))
        assertEquals(2, profile.holdCount(4))
        assertEquals(3, profile.effectiveWeek(5))
        assertEquals(LoadPrescription.Kilograms(95.0), exercises(profile, 5, 3)[0].load)
    }

    /// Несколько пропусков одной недели задерживают её только один раз.
    @Test
    fun sameWeekCountsOnce() {
        val profile = ProgramProfile.texas(ProgramInput.demo).markSkipped(1, 1, true).markSkipped(1, 2, true)
        assertEquals(1, profile.holdCount(2))
        assertEquals(LoadPrescription.Kilograms(90.0), exercises(profile, 2, 3)[0].load)
    }

    @Test
    fun skipWithoutHoldLeavesWeightsAlone() {
        val profile = ProgramProfile.texas(ProgramInput.demo).markSkipped(1, 1, false)
        assertTrue(profile.isSkipped(1, 1))
        assertEquals(0, profile.holdCount(2))
        assertEquals(LoadPrescription.Kilograms(92.5), exercises(profile, 2, 3)[0].load)
    }

    /// Возместил пропущенное — задержка снимается сама.
    @Test
    fun completingSkippedWorkoutReleasesTheHold() {
        var profile = ProgramProfile.texas(ProgramInput.demo).markSkipped(1, 1, true)
        assertEquals(LoadPrescription.Kilograms(90.0), exercises(profile, 2, 3)[0].load)

        profile = profile.toggleCompleted(1, 1)
        assertFalse(profile.isSkipped(1, 1))
        assertEquals(LoadPrescription.Kilograms(92.5), exercises(profile, 2, 3)[0].load)
    }

    /// Задержка подменяет только веса: номера дней остаются своими.
    @Test
    fun holdKeepsDayIdentity() {
        val profile = ProgramProfile.texas(ProgramInput.demo).markSkipped(1, 1, true)
        val week2 = profile.workoutPlan.weeks.first { it.number == 2 }
        assertEquals(listOf("2-1", "2-2", "2-3"), week2.days.map { it.id })
        assertEquals(listOf(1, 2, 3), week2.days.map { it.number })
    }

    // MARK: - Свой порядок

    @Test
    fun customOrderMovesExerciseAndUnknownStayLast() {
        val base = ProgramProfile.texas(ProgramInput.demo)
        val original = exercises(base, 1, 1)
        assertEquals("Приседания", original[0].name)

        val profile = base.setOrder(listOf(original[1].key, original[0].key), 1, 1, PlanEditScope.EVERY_WEEK)

        val reordered = exercises(profile, 1, 1)
        assertEquals(original[1].name, reordered[0].name)
        assertEquals(original[0].name, reordered[1].name)
        assertEquals(original.size, reordered.size)
        assertEquals(original.last().name, reordered.last().name)
    }

    @Test
    fun emptyOrderRemovesTheRearrangement() {
        val base = ProgramProfile.texas(ProgramInput.demo)
        val original = exercises(base, 1, 1)
        var profile = base.setOrder(listOf(original[1].key, original[0].key), 1, 1, PlanEditScope.EVERY_WEEK)
        assertEquals(original[1].name, exercises(profile, 1, 1)[0].name)

        profile = profile.setOrder(emptyList(), 1, 1, PlanEditScope.EVERY_WEEK)
        assertEquals(original[0].name, exercises(profile, 1, 1)[0].name)
        assertTrue(profile.planEdits.none { it.kind == com.texasprogram.app.model.PlanEditKind.ORDER })
    }

    // MARK: - Тоннаж

    @Test
    fun tonnageSumsRecordedSetsByWeek() {
        val base = ProgramProfile.texas(ProgramInput.demo)
        val w1 = exercises(base, 1, 1)[0]
        val w2 = exercises(base, 2, 1)[0]

        val profile = base
            .recordSet(1, 1, w1, 0, 5, 80.0)
            .recordSet(1, 1, w1, 1, 5, 80.0)
            .recordSet(2, 1, w2, 0, 3, 100.0)

        val weeks = profile.weeklyTonnage
        assertEquals(2, weeks.size)
        assertEquals(1, weeks[0].week)
        assertEquals(800.0, weeks[0].total, 0.001)
        assertEquals(2, weeks[0].setCount)
        assertEquals(300.0, weeks[1].total, 0.001)
    }

    @Test
    fun tonnageIsEmptyWithoutRecordedSets() {
        val base = ProgramProfile.texas(ProgramInput.demo)
        val squat = exercises(base, 1, 1)[0]
        // Закрытая точка без цифр в тоннаж не идёт: гадать по плану нельзя.
        assertTrue(base.toggleSet(1, 1, squat, 0).weeklyTonnage.isEmpty())
    }

    // MARK: - Серия без пропусков

    private fun closeWeek(profile: ProgramProfile, week: Int): ProgramProfile =
        (1..3).fold(profile) { acc, day -> acc.toggleCompleted(week, day) }

    @Test
    fun streakCountsFullyClosedWeeks() {
        var profile = ProgramProfile.texas(ProgramInput.demo)
        assertEquals(0 to 0, profile.weekStreak)

        profile = closeWeek(closeWeek(profile, 1), 2)
        assertEquals(2 to 2, profile.weekStreak)
    }

    /// Незакрытая последняя неделя серию не рвёт: она просто ещё идёт.
    @Test
    fun currentStreakLooksBackFromLastClosedWeek() {
        var profile = ProgramProfile.texas(ProgramInput.demo)
        profile = closeWeek(closeWeek(closeWeek(profile, 1), 2), 3)
        profile = profile.toggleCompleted(4, 1)

        assertEquals(3 to 3, profile.weekStreak)
    }

    @Test
    fun gapBreaksCurrentStreakButKeepsBest() {
        var profile = ProgramProfile.texas(ProgramInput.demo)
        profile = closeWeek(closeWeek(closeWeek(profile, 1), 2), 3)
        // Четвёртую пропустили целиком, пятую закрыли.
        profile = closeWeek(profile, 5)

        assertEquals(1 to 3, profile.weekStreak)
    }

    // MARK: - Новый цикл

    @Test
    fun newCycleHidesMarksButKeepsHistory() {
        val day = date(27, 7, 2026)
        var profile = ProgramProfile.texas(ProgramInput.demo).toggleCompleted(1, 1, day)
        assertEquals(1, profile.completedDayCount)

        profile = profile.startNewCycle(110.0, 105.0, 130.0)

        assertEquals(2, profile.cycleNumber)
        assertEquals(110.0, profile.squat5RM, 0.001)
        assertFalse(profile.isCompleted(1, 1))
        assertEquals(0, profile.completedDayCount)
        // История для графиков осталась.
        assertEquals(1, profile.completionLog.size)
    }

    @Test
    fun newCycleMarksDoNotCollideWithOld() {
        var profile = ProgramProfile.texas(ProgramInput.demo).toggleCompleted(1, 1, date(27, 7, 2026))
        profile = profile.startNewCycle(110.0, 105.0, 130.0).toggleCompleted(1, 1, date(10, 11, 2026))

        assertEquals(2, profile.completionLog.size)
        assertEquals(2, profile.completedDayKeys.size)
        assertTrue(profile.completedDayKeys.contains("1-1"))
        assertTrue(profile.completedDayKeys.contains("c2-1-1"))
        assertEquals(1, profile.completedDayCount)
    }

    /// Первый цикл ключи не меняет: сохранённые профили должны продолжать работать.
    @Test
    fun firstCycleKeysAreUnchanged() {
        val profile = ProgramProfile.texas(ProgramInput.demo)
        assertEquals("2-3", profile.dayKey(2, 3))
        assertEquals("peak-2-3", profile.copy(peakingActive = true).dayKey(2, 3))
    }

    @Test
    fun cycleAndPeakingNamespacesDoNotOverlap() {
        var profile = ProgramProfile.texas(ProgramInput.demo).startNewCycle(100.0, 100.0, 100.0)
        assertEquals("c2-1-1", profile.dayKey(1, 1))
        assertTrue(profile.isOwnKey("c2-1-1"))
        assertFalse(profile.isOwnKey("1-1"))
        assertFalse(profile.isOwnKey("c2-peak-1-1"))

        profile = profile.copy(peakingActive = true)
        assertEquals("c2-peak-1-1", profile.dayKey(1, 1))
        assertTrue(profile.isOwnKey("c2-peak-1-1"))
        assertFalse(profile.isOwnKey("c2-1-1"))
    }

    // MARK: - Фулбади

    private fun fullBodyPlan(level: FullBodyLevel) = FullBodyCalculator.generate(ProgramInput.demo, level)

    @Test
    fun fullBodyIsTwelveWeeksOfThreeDays() {
        for (level in FullBodyLevel.entries) {
            val plan = fullBodyPlan(level)
            assertEquals(level.title, 12, plan.weeks.size)
            assertTrue(level.title, plan.weeks.all { it.days.size == 3 })
        }
    }

    /// Фулбади — это всё тело каждый раз, а не сплит.
    @Test
    fun everyFullBodySessionHitsEverything() {
        for (level in FullBodyLevel.entries) {
            for (week in fullBodyPlan(level).weeks) {
                for (day in week.days) {
                    val names = day.exercises.map { it.name }
                    val where = "${level.title} н${week.number} д${day.number}"
                    assertTrue(where, names.contains("Приседания"))
                    assertTrue(where, names.contains("Жим лёжа"))
                    assertTrue("руки: $where", names.any { it.contains("бицепс") })
                    assertTrue("тяга: $where", names.any { it.contains("Подтягивания") || it.contains("Тяга") })
                    // У demo кор выбран «Копенгагенская планка» — проверяем именно его.
                    assertTrue("кор: $where", names.contains("Копенгагенская планка"))
                }
            }
        }
    }

    /// Волны из «Верх / Низ» в фулбади быть не должно — прогрессия линейная.
    @Test
    fun fullBodyHasNoBenchWave() {
        for (level in FullBodyLevel.entries) {
            val sessions = fullBodyPlan(level).weeks.flatMap { week ->
                week.days.flatMap { day -> day.exercises.mapNotNull { it.benchSession } }
            }
            assertTrue(level.title, sessions.isEmpty())
        }
        val profile = ProgramProfile.fullBody(ProgramInput.demo, FullBodyLevel.ABOUT_YEAR)
        assertTrue(profile.benchWave.isEmpty())
        assertFalse(profile.carriesBenchWave(1))
    }

    /// У новичка вес приседа растёт от тренировки к тренировке.
    @Test
    fun underYearGrowsEverySession() {
        val plan = fullBodyPlan(FullBodyLevel.UNDER_YEAR)
        fun squat(week: Int, day: Int) =
            plan.weeks[week - 1].days[day - 1].exercises.first { it.name == "Приседания" }.load

        assertEquals(LoadPrescription.Kilograms(80.0), squat(1, 1))
        assertEquals(LoadPrescription.Kilograms(82.5), squat(1, 2))
        assertEquals(LoadPrescription.Kilograms(85.0), squat(1, 3))
        assertEquals(LoadPrescription.Kilograms(87.5), squat(2, 1))
    }

    /// Со стажем от года идёт техасская волна: 90 %, 80 % от объёма, максимум.
    @Test
    fun aboutYearRepeatsTexasWave() {
        val week1 = fullBodyPlan(FullBodyLevel.ABOUT_YEAR).weeks[0].days
        fun squat(index: Int) = week1[index].exercises.first { it.name == "Приседания" }

        assertEquals(LoadPrescription.Kilograms(90.0), squat(0).load)
        assertEquals(5, squat(0).sets)
        assertEquals(LoadPrescription.Kilograms(72.5), squat(1).load)
        assertEquals(LoadPrescription.Kilograms(100.0), squat(2).load)
        assertEquals(1, squat(2).sets)

        val week5 = fullBodyPlan(FullBodyLevel.ABOUT_YEAR).weeks[4].days
        assertEquals(LoadPrescription.Kilograms(110.0), week5[2].exercises.first { it.name == "Приседания" }.load)
    }

    /// На стаже два года прибавка идёт через неделю, а не каждую.
    @Test
    fun twoYearsAddsWeightEveryOtherWeek() {
        val plan = fullBodyPlan(FullBodyLevel.TWO_YEARS)
        fun top(week: Int) = plan.weeks[week - 1].days[2].exercises.first { it.name == "Приседания" }.load

        assertEquals(LoadPrescription.Kilograms(100.0), top(1))
        assertEquals(LoadPrescription.Kilograms(100.0), top(2))
        assertEquals(LoadPrescription.Kilograms(102.5), top(3))
        assertEquals(LoadPrescription.Kilograms(102.5), top(4))
        assertEquals(LoadPrescription.Kilograms(105.0), top(5))
    }

    /// Объём должен расти со стажем — это и есть разница между уровнями.
    @Test
    fun volumeGrowsWithExperience() {
        val volumes = FullBodyLevel.entries.map { it.volume }
        assertEquals(listOf(3, 5, 5, 6), volumes.map { it.volumeSets })
        assertEquals(listOf(2, 3, 4, 4), volumes.map { it.pullSets })
        assertEquals(listOf(2, 3, 3, 4), volumes.map { it.armSets })
        assertEquals(listOf(0, 0, 1, 2), volumes.map { it.backoffSets })
    }

    /// Набор упражнений меняется со стажем, а не только число подходов.
    @Test
    fun accessorySlotsDependOnExperience() {
        assertEquals(
            listOf(AdditionalExerciseCategory.BACK, AdditionalExerciseCategory.ARMS, AdditionalExerciseCategory.CORE),
            FullBodyLevel.UNDER_YEAR.accessorySlots
        )
        assertEquals(
            listOf(
                AdditionalExerciseCategory.BACK, AdditionalExerciseCategory.PULL,
                AdditionalExerciseCategory.PRESS, AdditionalExerciseCategory.ARMS,
                AdditionalExerciseCategory.CORE
            ),
            FullBodyLevel.OVER_YEAR.accessorySlots
        )
    }

    @Test
    fun verticalPressAndExtraPullAppearOnlyWithExperience() {
        val about = fullBodyPlan(FullBodyLevel.ABOUT_YEAR).weeks[0].days
        val over = fullBodyPlan(FullBodyLevel.OVER_YEAR).weeks[0].days

        assertFalse(about.flatMap { it.exercises.map { e -> e.name } }.contains("Жим штанги стоя"))
        assertTrue(over[0].exercises.map { it.name }.contains("Жим штанги стоя"))
        // Дополнительная тяга только в лёгкий день: в остальные спина уже загружена.
        assertTrue(over[1].exercises.map { it.name }.contains("Румынская тяга"))
        assertFalse(over[0].exercises.map { it.name }.contains("Румынская тяга"))
    }

    /// У добивки своё имя и свой ключ: иначе она слилась бы с рабочим подходом.
    @Test
    fun backoffIsASeparateExercise() {
        val heavy = fullBodyPlan(FullBodyLevel.TWO_YEARS).weeks[0].days[2].exercises
        val working = heavy.first { it.name == "Приседания" }
        val backoff = heavy.first { it.name == "Приседания · добивка" }

        assertEquals(LoadPrescription.Kilograms(100.0), working.load)
        assertEquals(1, working.sets)
        assertEquals(LoadPrescription.Kilograms(90.0), backoff.load)
        assertEquals(2, backoff.sets)
        assertEquals("squat-backoff", backoff.key)
        assertFalse(working.key == backoff.key)

        assertTrue(fullBodyPlan(FullBodyLevel.ABOUT_YEAR).weeks[0].days[2].exercises.none { it.name.contains("добивка") })
    }

    @Test
    fun fullBodyProfileCountsFromFiveRepMax() {
        val profile = ProgramProfile.fullBody(ProgramInput.demo, FullBodyLevel.OVER_YEAR)
        assertEquals(TrainingProgramKind.FULL_BODY, profile.programKind)
        assertEquals("5ПМ", profile.maximumLabel)
        assertEquals(FullBodyLevel.OVER_YEAR, profile.fullBodyLevel)
        assertEquals(3, profile.trainingDayCount)
        assertEquals(36, profile.totalDays)
    }

    // MARK: - Своя программа

    @Test
    fun customSkeletonMatchesTemplate() {
        val program = CustomProgram.skeleton(SplitTemplate.PUSH_PULL_LEGS, "Моя", 6)
        assertEquals(3, program.days.size)
        assertEquals(listOf("ЖИМ", "ТЯГА", "НОГИ"), program.days.map { it.title })
        assertFalse(program.isRunnable)
    }

    @Test
    fun customProgramGrowsWeightByWeek() {
        var program = CustomProgram.skeleton(SplitTemplate.FULL_BODY, "Моя", 4)
        val squat = CustomExercise(name = "Приседания", sets = 3, reps = "5", kilograms = 100.0, weeklyIncrement = 2.5)
        val plank = CustomExercise(name = "Планка", sets = 3, reps = "60 с", loadText = "свой вес")
        program = program.copy(days = listOf(
            program.days[0].copy(exercises = listOf(squat)),
            program.days[1].copy(exercises = listOf(plank)),
            program.days[2].copy(exercises = listOf(squat))
        ))

        val plan = program.plan
        assertEquals(4, plan.weeks.size)
        assertEquals(LoadPrescription.Kilograms(100.0), plan.weeks[0].days[0].exercises[0].load)
        assertEquals(LoadPrescription.Kilograms(107.5), plan.weeks[3].days[0].exercises[0].load)
        // Без веса прибавка не работает — подпись остаётся как есть.
        assertEquals(LoadPrescription.RepRange("свой вес"), plan.weeks[3].days[1].exercises[0].load)
    }

    @Test
    fun customProfileBuildsPlanAndDayCount() {
        var program = CustomProgram.skeleton(SplitTemplate.UPPER_LOWER, "Моя", 5)
        program = program.copy(days = program.days.map {
            it.copy(exercises = listOf(CustomExercise(name = "Упражнение", sets = 3, reps = "8", kilograms = 50.0)))
        })
        assertTrue(program.isRunnable)

        val profile = ProgramProfile.custom(program)
        assertEquals(TrainingProgramKind.CUSTOM, profile.programKind)
        assertEquals(4, profile.trainingDayCount)
        assertEquals(5, profile.workoutPlan.weeks.size)
        assertEquals(20, profile.totalDays)
        assertFalse(profile.carriesBenchWave(1))
        assertEquals(4, profile.defaultWeekdays.size)
    }

    // MARK: - Резервная копия

    @Test
    fun backupCarriesEverythingNew() {
        val base = ProgramProfile.texas(ProgramInput.demo)
        val squat = exercises(base, 1, 1)[0]
        val profile = base
            .recordSet(1, 1, squat, 0, 4, 75.0)
            .markSkipped(4, 2, true)
            .copy(keepScreenOn = false)
            .startNewCycle(105.0, 100.0, 120.0)

        val archive = BackupService.parse(BackupService.export(listOf(profile)))
        val snapshot = archive.profiles[0]

        assertEquals(1, snapshot.setLog.size)
        assertEquals(4, snapshot.setLog.values.first().reps)
        assertEquals(1, snapshot.skipped.size)
        assertTrue(snapshot.skipped[0].holdsProgression)
        assertFalse(snapshot.keepScreenOn)
        assertEquals(2, snapshot.cycleNumber)

        val restored = BackupService.profile(snapshot)
        assertEquals(2, restored.cycleNumber)
        assertEquals(1, restored.setLog.size)
    }

    @Test
    fun backupCarriesCustomProgramAndLevel() {
        var program = CustomProgram.skeleton(SplitTemplate.FULL_BODY, "Моя", 3)
        program = program.copy(days = program.days.map {
            it.copy(exercises = listOf(CustomExercise(name = "Тяга", sets = 4, reps = "6", kilograms = 60.0, weeklyIncrement = 5.0)))
        })
        val custom = BackupService.parse(BackupService.export(listOf(ProgramProfile.custom(program)))).profiles[0]
        assertEquals("CUSTOM", custom.programKind)
        assertEquals(3, custom.customProgram?.days?.size)

        val fullBody = ProgramProfile.fullBody(ProgramInput.demo, FullBodyLevel.TWO_YEARS)
        val snapshot = BackupService.parse(BackupService.export(listOf(fullBody))).profiles[0]
        assertEquals("FULL_BODY", snapshot.programKind)
        assertEquals(FullBodyLevel.TWO_YEARS, BackupService.profile(snapshot).fullBodyLevel)
    }
}
