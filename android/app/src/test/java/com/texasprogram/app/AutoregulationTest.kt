package com.texasprogram.app

import com.texasprogram.app.data.BackupService
import com.texasprogram.app.model.LiftOutcome
import com.texasprogram.app.model.LoadPrescription
import com.texasprogram.app.model.MainLift
import com.texasprogram.app.model.ProgramInput
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.UpperLowerInput
import com.texasprogram.app.service.EuthanasiaInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/// Подстройка весов под то, как прошёл тяжёлый день.
class AutoregulationTest {

    private fun intensity(profile: ProgramProfile, week: Int, lift: MainLift): LoadPrescription? =
        profile.workoutPlan.weeks.firstOrNull { it.number == week }
            ?.days?.lastOrNull()?.exercises?.firstOrNull { it.name == lift.title }?.load

    private fun kg(profile: ProgramProfile, week: Int, lift: MainLift): Double? =
        (intensity(profile, week, lift) as? LoadPrescription.Kilograms)?.value

    private fun texas() = ProgramProfile.texas(ProgramInput.demo).copy(autoregulationEnabled = true)

    /// Включённый переключатель сам по себе ничего не меняет: пока нет ответов,
    /// недели идут с обычной прибавкой.
    @Test
    fun enablingAloneKeepsThePlanIdentical() {
        val plain = ProgramProfile.texas(ProgramInput.demo)
        val auto = texas()
        for (week in 1..12) {
            assertEquals("неделя $week", intensity(plain, week, MainLift.SQUAT), intensity(auto, week, MainLift.SQUAT))
        }
    }

    @Test
    fun easyAddsFiveToSquatAndTwoAndHalfToBench() {
        var profile = texas()
        assertEquals(90.0, kg(profile, 1, MainLift.SQUAT)!!, 0.001)
        assertEquals(90.0, kg(profile, 1, MainLift.BENCH)!!, 0.001)

        profile = profile.setReport(MainLift.SQUAT, 1, LiftOutcome.EASY)
        profile = profile.setReport(MainLift.BENCH, 1, LiftOutcome.EASY)

        assertEquals(95.0, kg(profile, 2, MainLift.SQUAT)!!, 0.001)
        assertEquals(92.5, kg(profile, 2, MainLift.BENCH)!!, 0.001)
    }

    @Test
    fun grindHoldsTheWeight() {
        val profile = texas().setReport(MainLift.SQUAT, 1, LiftOutcome.GRIND)
        assertEquals(90.0, kg(profile, 2, MainLift.SQUAT)!!, 0.001)
        // Следующая неделя без ответа снова прибавляет обычные 2,5.
        assertEquals(92.5, kg(profile, 3, MainLift.SQUAT)!!, 0.001)
    }

    @Test
    fun missedRollsBackByPercent() {
        val profile = texas().setReport(MainLift.SQUAT, 1, LiftOutcome.MISSED)
        // 90 минус 7,5 % это 83,25, округление до блина даёт 82,5.
        assertEquals(82.5, kg(profile, 2, MainLift.SQUAT)!!, 0.001)
    }

    /// Каждое движение живёт отдельно — ради этого всё и затевалось.
    @Test
    fun liftsProgressIndependently() {
        val profile = texas()
            .setReport(MainLift.SQUAT, 1, LiftOutcome.GRIND)
            .setReport(MainLift.BENCH, 1, LiftOutcome.EASY)

        assertEquals(90.0, kg(profile, 2, MainLift.SQUAT)!!, 0.001)
        assertEquals(92.5, kg(profile, 2, MainLift.BENCH)!!, 0.001)
        // Тяга без ответа идёт как обычно.
        assertEquals(92.5, kg(profile, 2, MainLift.DEADLIFT)!!, 0.001)
    }

    /// Лёгкие дни считаются процентами от тяжёлого, значит едут за ним.
    @Test
    fun easierDaysFollowTheIntensityDay() {
        val profile = texas().setReport(MainLift.SQUAT, 1, LiftOutcome.EASY)
        val week2 = profile.workoutPlan.weeks.first { it.number == 2 }

        fun squat(day: Int) =
            (week2.days[day].exercises.first { it.name == "Приседания" }.load as LoadPrescription.Kilograms).value

        // Тяжёлый 95, объём 90 % от него, лёгкий 80 % от объёма.
        assertEquals(95.0, squat(2), 0.001)
        assertEquals(85.0, squat(0), 0.001)
        assertEquals(67.5, squat(1), 0.001)
    }

    @Test
    fun outcomesAccumulateOverWeeks() {
        val profile = texas()
            .setReport(MainLift.SQUAT, 1, LiftOutcome.EASY)    // 90 → 95
            .setReport(MainLift.SQUAT, 2, LiftOutcome.SOLID)   // 95 → 97,5
            .setReport(MainLift.SQUAT, 3, LiftOutcome.GRIND)   // 97,5 → 97,5

        assertEquals(97.5, kg(profile, 4, MainLift.SQUAT)!!, 0.001)
    }

    @Test
    fun answerCanBeChangedAndCleared() {
        var profile = texas().setReport(MainLift.SQUAT, 1, LiftOutcome.MISSED)
        assertEquals(82.5, kg(profile, 2, MainLift.SQUAT)!!, 0.001)

        profile = profile.setReport(MainLift.SQUAT, 1, LiftOutcome.EASY)
        assertEquals(1, profile.reports(1).size)
        assertEquals(95.0, kg(profile, 2, MainLift.SQUAT)!!, 0.001)

        profile = profile.clearReport(MainLift.SQUAT, 1)
        assertEquals(92.5, kg(profile, 2, MainLift.SQUAT)!!, 0.001)
    }

    @Test
    fun disabledSwitchIgnoresAnswers() {
        var profile = texas().setReport(MainLift.SQUAT, 1, LiftOutcome.MISSED)
        assertEquals(82.5, kg(profile, 2, MainLift.SQUAT)!!, 0.001)

        profile = profile.copy(autoregulationEnabled = false)
        assertEquals(92.5, kg(profile, 2, MainLift.SQUAT)!!, 0.001)
    }

    // MARK: - Застой

    @Test
    fun twoMissesInARowMarkTheLiftAsStalled() {
        var profile = texas().setReport(MainLift.SQUAT, 1, LiftOutcome.MISSED)
        assertTrue(profile.stalledLifts.isEmpty())

        profile = profile.setReport(MainLift.SQUAT, 2, LiftOutcome.MISSED)
        assertEquals(listOf(MainLift.SQUAT), profile.stalledLifts)
    }

    /// Удачная неделя между двумя провалами застоем не считается.
    @Test
    fun successBetweenMissesResetsTheCounter() {
        val profile = texas()
            .setReport(MainLift.SQUAT, 1, LiftOutcome.MISSED)
            .setReport(MainLift.SQUAT, 2, LiftOutcome.SOLID)
            .setReport(MainLift.SQUAT, 3, LiftOutcome.MISSED)
        assertTrue(profile.stalledLifts.isEmpty())
    }

    // MARK: - Пространство имён

    /// У пикирования и нового цикла свои недели: ответы не должны смешиваться.
    @Test
    fun reportsAreNamespaced() {
        var profile = texas().setReport(MainLift.SQUAT, 1, LiftOutcome.EASY)
        assertNotNull(profile.report(MainLift.SQUAT, 1))

        assertNull(profile.copy(peakingActive = true).report(MainLift.SQUAT, 1))

        profile = profile.startNewCycle(100.0, 100.0, 100.0)
        assertNull(profile.report(MainLift.SQUAT, 1))
        assertEquals("c2-1", profile.reportKey(1))
    }

    /// Механизм завязан на тяжёлый день — там, где его нет, он выключен.
    @Test
    fun onlyTexasProgrammesSupportIt() {
        assertTrue(ProgramProfile.texas(ProgramInput.demo).supportsAutoregulation)
        assertTrue(ProgramProfile.proTexas(ProgramInput.demo).supportsAutoregulation)
        assertFalse(ProgramProfile.upperLower(UpperLowerInput.demo).supportsAutoregulation)
        assertFalse(ProgramProfile.euthanasia(EuthanasiaInput.demo).supportsAutoregulation)
    }

    // MARK: - Заметки

    @Test
    fun notesAreKeptPerWorkout() {
        var profile = ProgramProfile.texas(ProgramInput.demo).setNote("болело плечо", 2, 1)

        assertEquals("болело плечо", profile.note(2, 1))
        assertEquals("", profile.note(2, 2))

        // Пустая заметка не занимает место в хранилище.
        profile = profile.setNote("   ", 2, 1)
        assertEquals("", profile.note(2, 1))
        assertTrue(profile.workoutNotes.isEmpty())
    }

    // MARK: - Копия

    @Test
    fun backupCarriesReportsAndNotes() {
        val profile = texas()
            .setReport(MainLift.BENCH, 2, LiftOutcome.GRIND)
            .setNote("спал пять часов", 2, 3)

        val snapshot = BackupService.parse(BackupService.export(listOf(profile))).profiles.first()

        assertTrue(snapshot.autoregulationEnabled)
        assertEquals(1, snapshot.liftReports.size)
        assertEquals(LiftOutcome.GRIND, snapshot.liftReports.first().outcome)
        assertEquals("спал пять часов", snapshot.workoutNotes["2-3"])
    }
}
