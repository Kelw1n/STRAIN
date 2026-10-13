package com.texasprogram.app

import com.texasprogram.app.data.BackupService
import com.texasprogram.app.model.ExercisePrescription
import com.texasprogram.app.model.LoadPrescription
import com.texasprogram.app.model.ProgramInput
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.formatWeight
import com.texasprogram.app.service.WorkoutSessionManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.LocalDate

/// Шаг прибавки, округление плана под блины зала, застой подсобки,
/// копия с весом тела и подходы, отмеченные до старта тренировки.
class WeightStepTest {

    private fun texas() = ProgramProfile.texas(ProgramInput.demo)

    private data class Found(val exercise: ExercisePrescription, val week: Int, val day: Int)

    private fun accessory(profile: ProgramProfile): Found {
        for (week in profile.workoutPlan.weeks) {
            for (day in week.days) {
                val found = day.exercises.firstOrNull { it.needsOwnWeight && it.topReps != null }
                if (found != null) return Found(found, week.number, day.number)
            }
        }
        assumeTrue("в демо-программе нет подсобки без веса", false)
        error("unreachable")
    }

    private fun same(profile: ProgramProfile, exercise: ExercisePrescription, week: Int, day: Int): ExercisePrescription =
        profile.workoutPlan.weeks.first { it.number == week }.days.first { it.number == day }
            .exercises.first { it.name == exercise.name }

    private fun bench(profile: ProgramProfile): Double {
        val exercise = profile.workoutPlan.weeks.first().days.first().exercises.first { it.name == "Жим лёжа" }
        return (exercise.load as LoadPrescription.Kilograms).value
    }

    private fun logAll(profile: ProgramProfile, found: Found, reps: Int, weight: Double, week: Int = found.week): ProgramProfile {
        val exercise = same(profile, found.exercise, week, found.day)
        var next = profile
        for (index in 0 until exercise.sets) next = next.recordSet(week, found.day, exercise, index, reps, weight)
        return next
    }

    // MARK: - Шаг

    @Test
    fun defaultStepIsTwoAndAHalf() {
        val profile = texas()
        assertEquals(2.5, profile.weightStep("Жим лёжа"), 0.0)
        assertFalse(profile.hasOwnWeightStep("Жим лёжа"))
    }

    @Test
    fun ownStepIsKeptPerExerciseAndIgnoresCase() {
        val profile = texas().setWeightStep(5.0, "Жим лёжа")
        assertEquals(5.0, profile.weightStep("жим лёжа "), 0.0)
        assertEquals(5.0, profile.weightStep("Жим лежа"), 0.0)
        assertEquals(2.5, profile.weightStep("Сгибание рук"), 0.0)
    }

    @Test
    fun stepEqualToDefaultIsNotStored() {
        val profile = texas().setWeightStep(5.0, "Жим лёжа").setWeightStep(2.5, "Жим лёжа")
        assertTrue(profile.weightSteps.isEmpty())
    }

    @Test
    fun accessoryStepsUpByItsOwnStep() {
        var profile = texas()
        val found = accessory(profile)
        val top = found.exercise.topReps!!
        profile = logAll(profile.setWeightStep(5.0, found.exercise.name), found, top, 55.0)
        assertEquals(60.0, profile.accessoryHint(found.exercise, found.week + 1, found.day)!!.weight!!, 0.001)
    }

    @Test
    fun smallStepKeepsQuarters() {
        var profile = texas()
        val found = accessory(profile)
        profile = logAll(profile.setWeightStep(1.25, found.exercise.name), found, found.exercise.topReps!!, 55.0)
        assertEquals(56.25, profile.accessoryHint(found.exercise, found.week + 1, found.day)!!.weight!!, 0.001)
        assertEquals("56.25", formatWeight(56.25))
        assertEquals("52.5", formatWeight(52.5))
        assertEquals("60", formatWeight(60.0))
    }

    @Test
    fun defaultStepAppliesToEveryExerciseWithoutOwn() {
        var profile = texas().copy(defaultWeightStep = 5.0)
        val found = accessory(profile)
        profile = logAll(profile, found, found.exercise.topReps!!, 40.0)
        assertEquals(45.0, profile.accessoryHint(found.exercise, found.week + 1, found.day)!!.weight!!, 0.001)
    }

    // MARK: - Округление плана

    @Test
    fun roundingTiesGoDown() {
        assertEquals(80.0, ProgramProfile.roundWeight(82.5, 5.0), 0.0)
        assertEquals(85.0, ProgramProfile.roundWeight(83.0, 5.0), 0.0)
        assertEquals(57.5, ProgramProfile.roundWeight(57.5, 1.25), 0.0)
        assertEquals(82.0, ProgramProfile.roundWeight(82.5, 1.0), 0.0)
    }

    @Test
    fun planRoundsMainLiftToTheStep() {
        val profile = texas()
        val before = bench(profile)
        val after = bench(profile.setWeightStep(5.0, "Жим лёжа"))
        assertEquals(0.0, after % 5.0, 0.0001)
        assertTrue(kotlin.math.abs(after - before) <= 2.5)
    }

    @Test
    fun defaultGymLeavesPlanUntouched() {
        val profile = texas()
        assertEquals(bench(profile), bench(profile.setWeightStep(2.5, "Жим лёжа")), 0.0)
    }

    // MARK: - Застой

    private fun stalled(): Pair<ProgramProfile, Found> {
        var profile = texas()
        val found = accessory(profile)
        val top = found.exercise.topReps!!
        assumeTrue(top >= 3)
        for (offset in 0 until 3) profile = logAll(profile, found, top - 2, 50.0, found.week + offset)
        return profile to Found(same(profile, found.exercise, found.week + 3, found.day), found.week + 3, found.day)
    }

    @Test
    fun threeFlatSessionsSuggestADeload() {
        val (profile, at) = stalled()
        val hint = profile.accessoryHint(at.exercise, at.week, at.day)!!
        assertTrue(hint.isDeload)
        assertEquals(45.0, hint.weight!!, 0.001)
    }

    @Test
    fun deloadFollowsTheStep() {
        val (profile, at) = stalled()
        assertEquals(45.0, profile.setWeightStep(5.0, at.exercise.name).accessoryHint(at.exercise, at.week, at.day)!!.weight!!, 0.001)
        assertEquals(40.0, profile.setWeightStep(10.0, at.exercise.name).accessoryHint(at.exercise, at.week, at.day)!!.weight!!, 0.001)
    }

    @Test
    fun dismissedStallIsNotSuggestedAgain() {
        val (profile, at) = stalled()
        val hint = profile.dismissStall(at.exercise.name, 50.0).accessoryHint(at.exercise, at.week, at.day)!!
        assertFalse(hint.isDeload)
        assertEquals(50.0, hint.weight!!, 0.001)
    }

    @Test
    fun growingRepsAreNotAStall() {
        var profile = texas()
        val found = accessory(profile)
        val top = found.exercise.topReps!!
        assumeTrue(top >= 4)
        for (offset in 0 until 3) profile = logAll(profile, found, top - 3 + offset, 50.0, found.week + offset)
        val next = same(profile, found.exercise, found.week + 3, found.day)
        assertFalse(profile.accessoryHint(next, found.week + 3, found.day)!!.isDeload)
    }

    // MARK: - Копия

    @Test
    fun backupKeepsStepsAndBodyWeight() {
        val day = LocalDate.of(2026, 10, 12)
        val profile = texas()
            .copy(defaultWeightStep = 5.0)
            .setWeightStep(1.25, "Сгибание рук")
            .dismissStall("Тяга", 60.0)
            .recordBodyWeight(82.4, day)
        val restored = BackupService.profile(BackupService.parse(BackupService.export(listOf(profile))).profiles.first())
        assertEquals(5.0, restored.defaultWeightStep, 0.0)
        assertEquals(1.25, restored.weightSteps["сгибание рук"]!!, 0.0)
        assertEquals(60.0, restored.stallDismissed["тяга"]!!, 0.0)
        assertEquals(day.toEpochDay(), restored.bodyWeightLog.single().epochDay)
        assertEquals(82.4, restored.bodyWeightLog.single().weight, 0.0)
    }

    /// Копия с iPhone: дата веса тела — ISO-строка начала дня.
    @Test
    fun backupReadsBodyWeightFromIos() {
        val start = LocalDate.of(2026, 10, 12).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
        val text = """{"profiles":[{"id":"x","name":"Бро","createdAtEpochDay":20000,"programKind":"TEXAS",
            "squat5RM":100,"bench5RM":80,"deadlift5RM":120,"level":"BEGINNER","cycleStartedEpochDay":20000,
            "bodyWeightLog":[{"date":"$start","weight":81.5}],"defaultWeightStep":5}]}"""
        val restored = BackupService.profile(BackupService.parse(text).profiles.first())
        assertEquals(LocalDate.of(2026, 10, 12).toEpochDay(), restored.bodyWeightLog.single().epochDay)
        assertEquals(5.0, restored.defaultWeightStep, 0.0)
    }

    // MARK: - Тренировка

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("steps").toFile()
        WorkoutSessionManager.attach(dir)
        WorkoutSessionManager.forgetForTesting()
        WorkoutSessionManager.clearSaved()
    }

    @After
    fun tearDown() {
        WorkoutSessionManager.forgetForTesting()
        WorkoutSessionManager.clearSaved()
        dir.deleteRecursively()
    }

    @Test
    fun sessionPicksUpSetsLoggedBeforeStart() {
        var profile = texas()
        val workout = profile.schedule().allPending.first()
        val first = profile.exercises(workout).first { it.sets > 0 }
        profile = profile.recordSet(workout.week, workout.day.number, first, 0, 4, 77.5)

        WorkoutSessionManager.startWorkout(workout, profile)
        val state = WorkoutSessionManager.exercises.first { it.key == first.key }
        assertTrue(state.sets[0].isDone)
        assertEquals(77.5, state.sets[0].weight, 0.0)
        assertEquals(4, state.sets[0].reps)
        assertFalse(state.sets.drop(1).any { it.isDone })
    }

    @Test
    fun switchingProfileDetachesSession() {
        val owner = texas()
        val workout = owner.schedule().allPending.first()
        WorkoutSessionManager.startWorkout(workout, owner)

        WorkoutSessionManager.detachIfOtherProfile(texas())
        assertFalse(WorkoutSessionManager.isRunning)

        WorkoutSessionManager.restoreIfNeeded(owner)
        assertEquals(workout.id, WorkoutSessionManager.activeWorkout?.id)
    }

    @Test
    fun changingStepMidWorkoutMovesPlannedWeight() {
        var profile = texas()
        val workout = profile.schedule().allPending.first()
        WorkoutSessionManager.startWorkout(workout, profile)
        val index = WorkoutSessionManager.exercises.indexOfFirst { it.prescription.name == "Жим лёжа" }
        assumeTrue(index >= 0)
        WorkoutSessionManager.changeWeightStep(5.0, index, profile) { profile = it }
        assertEquals(5.0, profile.weightStep("Жим лёжа"), 0.0)
        val weight = WorkoutSessionManager.exercises[index].sets.first().weight
        assertEquals(0.0, weight % 5.0, 0.0001)
    }
}
