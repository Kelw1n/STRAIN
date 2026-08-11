package com.texasprogram.app

import com.texasprogram.app.data.BackupService
import com.texasprogram.app.model.ExercisePrescription
import com.texasprogram.app.model.LoadPrescription
import com.texasprogram.app.model.ProgramInput
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.TrainingProgramKind
import com.texasprogram.app.model.WorkoutPlan
import com.texasprogram.app.service.EuthanasiaCalculator
import com.texasprogram.app.service.EuthanasiaInput
import com.texasprogram.app.service.EuthanasiaLevel
import com.texasprogram.app.service.ProTexasCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/// Две программы из Лимарина: продвинутый техас и «Эвтаназия худобы».
///
/// Числа сверены с таблицами: проценты дней, сдвиги блоков и множители
/// уплотнения взяты из формул, а не выведены на глаз.
class NewProgramsTest {

    private fun day(plan: WorkoutPlan, week: Int, day: Int): List<ExercisePrescription> =
        plan.weeks.firstOrNull { it.number == week }?.days?.firstOrNull { it.number == day }?.exercises.orEmpty()

    private fun load(list: List<ExercisePrescription>, name: String): LoadPrescription? =
        list.firstOrNull { it.name == name }?.load

    // MARK: - Продвинутый техас

    @Test
    fun proTexasIsTenBlocksOfTwoWeeks() {
        val plan = ProTexasCalculator.generate(ProgramInput.demo)
        assertEquals(20, plan.weeks.size)
        assertTrue(plan.weeks.all { it.days.size == 3 })
        assertEquals(1, ProTexasCalculator.block(1))
        assertEquals(1, ProTexasCalculator.block(2))
        assertEquals(2, ProTexasCalculator.block(3))
        assertEquals(10, ProTexasCalculator.block(20))
    }

    /// Первые два блока идут ниже 5ПМ — это разгон, дальше по 2,5 кг за блок.
    @Test
    fun proTexasBlockRamp() {
        assertEquals(80.0, ProTexasCalculator.intensityWeight(100.0, 1), 0.001)
        assertEquals(90.0, ProTexasCalculator.intensityWeight(100.0, 2), 0.001)
        assertEquals(100.0, ProTexasCalculator.intensityWeight(100.0, 3), 0.001)
        assertEquals(102.5, ProTexasCalculator.intensityWeight(100.0, 4), 0.001)
        assertEquals(117.5, ProTexasCalculator.intensityWeight(100.0, 10), 0.001)
    }

    /// Проценты дней внутри блока: 80 / 70 / 90, затем 90 / 70 / 100.
    @Test
    fun proTexasDayPercentages() {
        val plan = ProTexasCalculator.generate(ProgramInput.demo)
        // Третий блок идёт ровно от 5ПМ — недели 5 и 6, интенсивность 100 кг.
        assertEquals(LoadPrescription.Kilograms(80.0), load(day(plan, 5, 1), "Приседания"))
        assertEquals(LoadPrescription.Kilograms(70.0), load(day(plan, 5, 2), "Приседания"))
        assertEquals(LoadPrescription.Kilograms(90.0), load(day(plan, 5, 3), "Приседания"))
        assertEquals(LoadPrescription.Kilograms(90.0), load(day(plan, 6, 1), "Приседания"))
        assertEquals(LoadPrescription.Kilograms(70.0), load(day(plan, 6, 2), "Приседания"))
        assertEquals(LoadPrescription.Kilograms(100.0), load(day(plan, 6, 3), "Приседания"))
    }

    @Test
    fun proTexasSchemes() {
        val plan = ProTexasCalculator.generate(ProgramInput.demo)
        val volumeFirst = day(plan, 5, 1).first { it.name == "Приседания" }
        assertEquals(5, volumeFirst.sets)
        assertEquals("7", volumeFirst.reps)

        val intensityFirst = day(plan, 5, 3).first { it.name == "Приседания" }
        assertEquals(3, intensityFirst.sets)
        assertEquals("3", intensityFirst.reps)

        val peak = day(plan, 6, 3).first { it.name == "Приседания" }
        assertEquals(1, peak.sets)
        assertEquals("5", peak.reps)
    }

    /// Становая есть только в интенсивные дни.
    @Test
    fun proTexasDeadliftOnlyOnIntensityDays() {
        val plan = ProTexasCalculator.generate(ProgramInput.demo)
        assertNull(load(day(plan, 5, 1), "Становая тяга"))
        assertNull(load(day(plan, 5, 2), "Становая тяга"))
        assertTrue(load(day(plan, 5, 3), "Становая тяга") != null)
        assertTrue(load(day(plan, 6, 3), "Становая тяга") != null)
    }

    @Test
    fun proTexasProfileUsesFiveRepMax() {
        val profile = ProgramProfile.proTexas(ProgramInput.demo)
        assertEquals(TrainingProgramKind.PRO_TEXAS, profile.programKind)
        assertEquals("5ПМ", profile.maximumLabel)
        assertEquals(3, profile.trainingDayCount)
        assertEquals(60, profile.totalDays)
        assertTrue(profile.benchWave.isEmpty())
    }

    // MARK: - Эвтаназия худобы

    @Test
    fun euthanasiaIsEightWeeksOfThreeDays() {
        val plan = EuthanasiaCalculator.generate(EuthanasiaInput.demo)
        assertEquals(8, plan.weeks.size)
        assertTrue(plan.weeks.all { it.days.size == 3 })
    }

    /// Первая неделя — тестовая: максимум и замер времени, без рабочих весов.
    @Test
    fun euthanasiaFirstWeekIsTesting() {
        val plan = EuthanasiaCalculator.generate(EuthanasiaInput.demo)
        val names = day(plan, 1, 1).map { it.name }
        assertTrue(names.any { it.contains("тест 1ПМ") })
        assertTrue(names.any { it.contains("тест на время") })
        assertFalse(names.any { it.contains("плотность") })
    }

    /// Силовая часть циклится 3×5 → 3×3 → волна 5/3/1.
    @Test
    fun euthanasiaStrengthCycles() {
        val plan = EuthanasiaCalculator.generate(EuthanasiaInput.demo)
        val first = day(plan, 2, 1).first { it.name == "Тяга в наклоне" }
        assertEquals(3, first.sets)
        assertEquals("5", first.reps)
        assertEquals(LoadPrescription.Kilograms(65.0), first.load)

        val second = day(plan, 3, 1).first { it.name == "Тяга в наклоне" }
        assertEquals(3, second.sets)
        assertEquals("3", second.reps)
        assertEquals(LoadPrescription.Kilograms(70.0), second.load)

        val wave = day(plan, 4, 1).filter { it.name == "Тяга в наклоне" }
        assertEquals(3, wave.size)
        assertEquals(listOf("5", "3", "1"), wave.map { it.reps })
        assertEquals(
            listOf(
                LoadPrescription.Kilograms(65.0),
                LoadPrescription.Kilograms(75.0),
                LoadPrescription.Kilograms(85.0)
            ),
            wave.map { it.load }
        )
    }

    /// У волны три упражнения с одним именем — ключи должны быть разными,
    /// иначе отметки подходов слились бы в одну.
    @Test
    fun euthanasiaWaveHasDistinctKeys() {
        val plan = EuthanasiaCalculator.generate(EuthanasiaInput.demo)
        val wave = day(plan, 4, 1).filter { it.name == "Тяга в наклоне" }
        assertEquals(3, wave.map { it.key }.toSet().size)
    }

    /// Плотность: повторов столько же, времени меньше с каждой инверсией.
    @Test
    fun euthanasiaDensityShrinksTime() {
        val plan = EuthanasiaCalculator.generate(EuthanasiaInput.demo)
        fun density(week: Int) = day(plan, week, 1).first { it.name.contains("плотность") }
        // Тест тяги — 20 минут, средний уровень: первая инверсия 92,9 %.
        assertEquals("55 кг · за 18:34", density(2).load.displayText)
        assertEquals("75 кг · за 10:00", density(8).load.displayText)
        assertTrue((2..8).all { density(it).reps == "45 повторений" })
    }

    @Test
    fun euthanasiaLevelsDifferOnlyInStep() {
        assertEquals(0.6, EuthanasiaLevel.BEGINNER.timeFactor(7), 0.002)
        assertEquals(0.5, EuthanasiaLevel.MEDIUM.timeFactor(7), 0.002)
        assertEquals(0.4, EuthanasiaLevel.PRO.timeFactor(7), 0.002)
        assertEquals(0.929, EuthanasiaLevel.MEDIUM.timeFactor(1), 0.002)
    }

    /// Каждое движение считается от своего максимума и своего КПШ.
    @Test
    fun euthanasiaMovementsAreIndependent() {
        val plan = EuthanasiaCalculator.generate(EuthanasiaInput.demo)
        assertEquals("45 повторений", day(plan, 2, 1).first { it.name.contains("плотность") }.reps)
        assertEquals("40 повторений", day(plan, 2, 2).first { it.name.contains("плотность") }.reps)
        assertEquals("50 повторений", day(plan, 2, 3).first { it.name.contains("плотность") }.reps)
        // Присед в демо — 140 кг, 55 % от него это 77,5.
        assertTrue(day(plan, 2, 2).first { it.name.contains("плотность") }.load.displayText.startsWith("77.5 кг"))
    }

    @Test
    fun euthanasiaKeepsFourAccessoriesEveryDay() {
        val plan = EuthanasiaCalculator.generate(EuthanasiaInput.demo)
        for (week in plan.weeks) {
            for (item in week.days) {
                assertEquals("неделя ${week.number}, день ${item.number}", 4, item.exercises.count { it.isOptional })
            }
        }
    }

    @Test
    fun euthanasiaProfileBuildsPlan() {
        val profile = ProgramProfile.euthanasia(EuthanasiaInput.demo)
        assertEquals(TrainingProgramKind.EUTHANASIA, profile.programKind)
        assertEquals(3, profile.trainingDayCount)
        assertEquals(24, profile.totalDays)
        assertFalse(profile.programKind.usesFiveRepMax)
    }

    @Test
    fun timeTextReadsLikeAStopwatch() {
        assertEquals("20:00", EuthanasiaCalculator.timeText(20.0))
        assertEquals("18:34", EuthanasiaCalculator.timeText(18.5667))
        assertEquals("5:15", EuthanasiaCalculator.timeText(5.25))
    }

    // MARK: - Копия

    @Test
    fun backupCarriesBothNewPrograms() {
        val pro = ProgramProfile.proTexas(ProgramInput.demo)
        val evt = ProgramProfile.euthanasia(EuthanasiaInput.demo)
        val archive = BackupService.parse(BackupService.export(listOf(pro, evt)))

        assertEquals("PRO_TEXAS", archive.profiles[0].programKind)
        assertEquals("EUTHANASIA", archive.profiles[1].programKind)
        assertEquals("Тяга в наклоне", archive.profiles[1].euthanasiaInput?.pull?.exercise)
        assertEquals(EuthanasiaLevel.MEDIUM, archive.profiles[1].euthanasiaInput?.level)

        val restored = BackupService.profile(archive.profiles[1])
        assertEquals(8, restored.workoutPlan.weeks.size)
    }
}
