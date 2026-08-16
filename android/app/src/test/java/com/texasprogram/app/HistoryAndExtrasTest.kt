package com.texasprogram.app

import com.texasprogram.app.model.AccessoryHint
import com.texasprogram.app.model.ExercisePrescription
import com.texasprogram.app.model.LoadPrescription
import com.texasprogram.app.model.ProgramInput
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.service.CSVExport
import com.texasprogram.app.service.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/// История движения, вес тела, прогрессия подсобки, «всё по плану», выгрузка и напоминания.
class HistoryAndExtrasTest {

    private fun texas() = ProgramProfile.texas(ProgramInput.demo)

    private fun squat(profile: ProgramProfile, week: Int = 1, day: Int = 1): ExercisePrescription =
        profile.workoutPlan.weeks.first { it.number == week }
            .days.first { it.number == day }.exercises.first { it.name == "Приседания" }

    /// Подсобка из демо-профиля: упражнение с диапазоном повторов и без веса.
    private fun accessory(profile: ProgramProfile): Triple<ExercisePrescription, Int, Int>? {
        for (week in profile.workoutPlan.weeks) {
            for (day in week.days) {
                val found = day.exercises.firstOrNull { it.needsOwnWeight && it.topReps != null }
                if (found != null) return Triple(found, week.number, day.number)
            }
        }
        return null
    }

    // MARK: - История упражнения

    @Test
    fun historyCollectsOnlyLoggedWorkouts() {
        var profile = texas()
        profile = profile.recordSet(1, 1, squat(profile), 0, 5, 80.0)
        profile = profile.recordSet(2, 1, squat(profile, 2), 0, 5, 85.0)

        val history = profile.history("Приседания")
        assertEquals(2, history.size)
        assertEquals(1, history.first().week)
        assertEquals(85.0, history.last().best, 0.001)
    }

    @Test
    fun historyPointKnowsBestSetAndTonnage() {
        var profile = texas()
        val exercise = squat(profile)
        profile = profile.recordSet(1, 1, exercise, 0, 5, 80.0).recordSet(1, 1, exercise, 1, 3, 90.0)

        val point = profile.history("Приседания").first()
        assertEquals(90.0, point.best, 0.001)
        assertEquals(8, point.totalReps)
        assertEquals(5 * 80.0 + 3 * 90.0, point.tonnage, 0.001)
    }

    /// План рядом с фактом: по нему на графике видно, добрал человек назначенное или нет.
    @Test
    fun historyCarriesThePlannedWeight() {
        var profile = texas()
        val exercise = squat(profile)
        val planned = (exercise.load as LoadPrescription.Kilograms).value
        profile = profile.recordSet(1, 1, exercise, 0, 5, planned - 10)

        val point = profile.history("Приседания").first()
        assertEquals(planned, point.planned!!, 0.001)
        assertEquals(planned - 10, point.best, 0.001)
    }

    @Test
    fun loggedNamesListOnlyExercisesWithEntries() {
        var profile = texas()
        assertTrue(profile.loggedExerciseNames.isEmpty())

        profile = profile.recordSet(1, 1, squat(profile), 0, 5, 80.0)
        assertEquals(listOf("Приседания"), profile.loggedExerciseNames)
    }

    // MARK: - Вес тела

    @Test
    fun bodyWeightKeepsOneEntryPerDay() {
        val profile = texas().recordBodyWeight(80.0).recordBodyWeight(80.5)

        assertEquals(1, profile.bodyWeightLog.size)
        assertEquals(80.5, profile.latestBodyWeight!!, 0.001)
    }

    @Test
    fun bodyWeightSeriesIsSortedByDate() {
        val today = LocalDate.now()
        val profile = texas()
            .recordBodyWeight(81.0, today)
            .recordBodyWeight(80.0, today.minusDays(1))

        assertEquals(listOf(80.0, 81.0), profile.bodyWeightSeries.map { it.weight })
    }

    @Test
    fun relativeStrengthNeedsABodyWeight() {
        val profile = texas()
        assertNull(profile.relativeStrength(profile.squat5RM))
        assertEquals(1.25, profile.recordBodyWeight(80.0).relativeStrength(100.0)!!, 0.001)
    }

    // MARK: - Прогрессия подсобки

    @Test
    fun firstTimeAccessoryAsksForAWeight() {
        val profile = texas()
        val target = accessory(profile) ?: return
        val (exercise, week, day) = target

        val hint = profile.accessoryHint(exercise, week, day)
        assertNotNull(hint)
        assertNull(hint!!.weight)
        assertFalse(hint.isStepUp)
    }

    /// Верх диапазона во всех подходах — вес растёт на шаг.
    @Test
    fun accessoryStepsUpWhenTopOfRangeIsHit() {
        var profile = texas()
        val target = accessory(profile) ?: return
        val (exercise, week, day) = target
        val top = exercise.topReps!!

        profile = (0 until exercise.sets).fold(profile) { value, index ->
            value.recordSet(week, day, exercise, index, top, 20.0)
        }

        val next = profile.accessoryHint(exercise, week + 1, day)!!
        assertEquals(20.0 + AccessoryHint.STEP, next.weight!!, 0.001)
        assertTrue(next.isStepUp)
    }

    /// Недобрал хоть в одном подходе — вес тот же, растут повторы.
    @Test
    fun accessoryHoldsWeightUntilEverySetHitsTheTop() {
        var profile = texas()
        val target = accessory(profile) ?: return
        val (exercise, week, day) = target
        val top = exercise.topReps!!

        profile = (0 until exercise.sets).fold(profile) { value, index ->
            value.recordSet(week, day, exercise, index, if (index == 0) top else top - 2, 20.0)
        }

        val next = profile.accessoryHint(exercise, week + 1, day)!!
        assertEquals(20.0, next.weight!!, 0.001)
        assertFalse(next.isStepUp)
    }

    /// Незакрытые подходы — не повод прибавлять, даже если в сделанных верх диапазона.
    @Test
    fun accessoryNeedsEverySetLogged() {
        var profile = texas()
        val target = accessory(profile) ?: return
        val (exercise, week, day) = target
        assumeTrue(exercise.sets >= 2)

        profile = profile.recordSet(week, day, exercise, 0, exercise.topReps!!, 20.0)

        val next = profile.accessoryHint(exercise, week + 1, day)!!
        assertEquals(20.0, next.weight!!, 0.001)
        assertFalse(next.isStepUp)
    }

    @Test
    fun accessoryHintIsOffWhenSwitchedOff() {
        val profile = texas().copy(accessoryProgressionEnabled = false)
        val target = accessory(profile) ?: return
        assertNull(profile.accessoryHint(target.first, target.second, target.third))
    }

    /// Основные движения ведёт программа — подсказка им не нужна.
    @Test
    fun mainLiftsHaveNoAccessoryHint() {
        val profile = texas()
        assertNull(profile.accessoryHint(squat(profile), 1, 1))
    }

    // MARK: - Всё по плану

    @Test
    fun asPlannedFillsEverySetWithPlannedWeights() {
        var profile = texas()
        val exercise = squat(profile)
        val planned = (exercise.load as LoadPrescription.Kilograms).value

        profile = profile.completeAsPlanned(1, 1)

        assertEquals(exercise.sets, profile.completedSets(1, 1, exercise))
        val entry = profile.setEntry(1, 1, exercise, 0)!!
        assertEquals(planned, entry.weight, 0.001)
        assertEquals(exercise.plannedReps, entry.reps)
        assertTrue(profile.isCompleted(1, 1))
    }

    @Test
    fun asPlannedDoesNotToggleAnAlreadyCompletedDay() {
        var profile = texas().toggleCompleted(1, 1)
        assertTrue(profile.isCompleted(1, 1))

        profile = profile.completeAsPlanned(1, 1)
        assertTrue(profile.isCompleted(1, 1))
    }

    /// Диапазон «8-10» — берём нижнюю границу: приписывать верхнюю нечестно.
    @Test
    fun plannedRepsTakeTheLowerBoundOfARange() {
        fun make(reps: String) = ExercisePrescription("x", 3, reps, LoadPrescription.Kilograms(50.0))
        assertEquals(8, make("8-10").plannedReps)
        assertEquals(10, make("8-10").topReps)
        assertEquals(5, make("5").plannedReps)
        assertNull(make("до отказа").plannedReps)
    }

    // MARK: - История по старым отметкам

    @Test
    fun backfillFillsCompletedDaysWithPlannedWeights() {
        var profile = texas()
        val exercise = squat(profile)
        val planned = (exercise.load as LoadPrescription.Kilograms).value

        profile = profile.toggleCompleted(1, 1)
        assertEquals(1, profile.backfillableWorkouts)

        val (filledProfile, count) = profile.backfillHistoryFromMarks()
        assertEquals(1, count)
        assertEquals(planned, filledProfile.setEntry(1, 1, exercise, 0)!!.weight, 0.001)
        assertEquals(exercise.sets, filledProfile.entries(1, 1, exercise).size)
        assertEquals(0, filledProfile.backfillableWorkouts)
    }

    /// Настоящие числа человека дороже плановых — их не трогаем.
    @Test
    fun backfillNeverOverwritesRealEntries() {
        var profile = texas()
        val exercise = squat(profile)
        profile = profile.recordSet(1, 1, exercise, 0, 3, 60.0).toggleCompleted(1, 1)

        val (filled, _) = profile.backfillHistoryFromMarks()
        val entry = filled.setEntry(1, 1, exercise, 0)!!
        assertEquals(60.0, entry.weight, 0.001)
        assertEquals(3, entry.reps)
    }

    @Test
    fun backfillIgnoresUntouchedDays() {
        val profile = texas()
        assertEquals(0, profile.backfillableWorkouts)
        assertEquals(0, profile.backfillHistoryFromMarks().second)
    }

    /// Половина точек — половина записей: приписывать недоделанное нельзя.
    @Test
    fun backfillFollowsPartialDots() {
        var profile = texas()
        val exercise = squat(profile)
        profile = profile.toggleSet(1, 1, exercise, 1)
        assertEquals(2, profile.completedSets(1, 1, exercise))

        val (filled, _) = profile.backfillHistoryFromMarks()
        assertEquals(2, filled.entries(1, 1, exercise).size)
    }

    // MARK: - Выгрузка

    @Test
    fun csvHasHeaderAndOneRowPerSet() {
        var profile = texas()
        val exercise = squat(profile)
        profile = profile.recordSet(1, 1, exercise, 0, 5, 80.0).recordSet(1, 1, exercise, 1, 5, 82.5)

        val rows = CSVExport.text(listOf(profile)).split("\r\n").filter { it.isNotEmpty() }
        assertEquals(3, rows.size)
        assertTrue(rows[0].startsWith("Профиль;"))
        // Русская локаль Excel ждёт запятую в дробях, иначе вес станет текстом.
        assertTrue(rows[2], rows[2].contains(";82,5;"))
    }

    @Test
    fun csvQuotesNamesWithTheSeparator() {
        var profile = texas().copy(name = "Профиль; основной")
        profile = profile.recordSet(1, 1, squat(profile), 0, 5, 80.0)

        assertTrue(CSVExport.text(listOf(profile)).contains("\"Профиль; основной\""))
    }

    // MARK: - Напоминания

    /// Понедельник в нумерации iOS — 2. Если время ещё не прошло, срабатывает сегодня.
    @Test
    fun reminderPicksTheSameDayWhenTimeHasNotPassed() {
        val monday = LocalDateTime.of(2026, 8, 17, 9, 0)
        val next = ReminderScheduler.nextOccurrence(2, 18, 30, monday)

        assertEquals(monday.toLocalDate(), next.toLocalDate())
        assertEquals(18, next.hour)
        assertEquals(30, next.minute)
    }

    /// Время уже прошло — переносим на следующую неделю, а не на сегодня в прошлом.
    @Test
    fun reminderMovesToNextWeekWhenTimeHasPassed() {
        val monday = LocalDateTime.of(2026, 8, 17, 20, 0)
        val next = ReminderScheduler.nextOccurrence(2, 18, 30, monday)

        assertEquals(monday.toLocalDate().plusDays(7), next.toLocalDate())
    }

    @Test
    fun reminderFindsTheNearestMatchingWeekday() {
        val monday = LocalDateTime.of(2026, 8, 17, 9, 0)
        // Среда — 4 в нумерации iOS.
        val next = ReminderScheduler.nextOccurrence(4, 18, 0, monday)

        assertEquals(monday.toLocalDate().plusDays(2), next.toLocalDate())
    }
}
