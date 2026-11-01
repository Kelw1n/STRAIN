package com.texasprogram.app

import com.texasprogram.app.data.BackupService
import com.texasprogram.app.model.ExerciseLibrary
import com.texasprogram.app.model.ExercisePrescription
import com.texasprogram.app.model.FreeWorkoutDay
import com.texasprogram.app.model.LibraryExercise
import com.texasprogram.app.model.LoadPrescription
import com.texasprogram.app.model.PersonalRecords
import com.texasprogram.app.model.ProgramInput
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.SocialProgramExercise
import com.texasprogram.app.model.TemplateCatalog
import com.texasprogram.app.model.TemplateExercise
import com.texasprogram.app.model.TemplateSet
import com.texasprogram.app.model.WorkoutTemplate
import com.texasprogram.app.model.appendFreeExercises
import com.texasprogram.app.model.deleteFreeWorkout
import com.texasprogram.app.model.discardFreeWorkout
import com.texasprogram.app.model.exerciseLibrary
import com.texasprogram.app.model.freeDay
import com.texasprogram.app.model.historyTitle
import com.texasprogram.app.model.replaceFreeExercise
import com.texasprogram.app.model.saveCustomExercise
import com.texasprogram.app.model.saveTemplate
import com.texasprogram.app.model.startFreeWorkout
import com.texasprogram.app.model.startWorkout
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/// Библиотека, «Мои тренировки» и тренировки вне программы — те же случаи,
/// что в WorkoutTemplateTests на iOS.
class WorkoutTemplateTest {
    @Before
    fun loadLibrary() {
        ExerciseLibrary.load(File("src/main/assets/exercise-library.json").readText())
    }

    @Test
    fun libraryLoads() {
        assertTrue(ExerciseLibrary.builtIn.size > 100)
        assertEquals(listOf("chest", "back", "shoulders"), ExerciseLibrary.groups.map { it.id }.take(3))
        assertEquals("Бицепс бедра", ExerciseLibrary.groupTitle("hamstrings"))
        val ids = ExerciseLibrary.builtIn.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ExerciseLibrary.builtIn.all { it.steps.isNotEmpty() })
        // Файл один на обе платформы.
        assertEquals(File("src/main/assets/exercise-library.json").readText(), File("../../TexasProgram/Resources/exercise-library.json").readText())
    }

    @Test
    fun searchAndFilters() {
        val all = ExerciseLibrary.builtIn
        val found = ExerciseLibrary.filter(all, "гант жим", null, null).map { it.name }
        assertTrue(found.contains("Жим гантелей лёжа"))
        assertTrue(ExerciseLibrary.filter(all, "лежа", null, null).isNotEmpty())
        val chestBarbell = ExerciseLibrary.filter(all, "", "chest", "barbell")
        assertTrue(chestBarbell.any { it.name == "Жим штанги лёжа" })
        val triceps = ExerciseLibrary.filter(all, "", "triceps", null)
        assertTrue(triceps.any { it.name == "Жим штанги лёжа" })
        assertEquals(listOf("triceps"), ExerciseLibrary.sections(triceps, "triceps").map { it.first.id })
        assertEquals("chest", ExerciseLibrary.sections(all).first().first.id)
        assertEquals("bench_press", ExerciseLibrary.find("жим штанги лежа")?.id)
    }

    @Test
    fun customExercisesComeFirst() {
        val own = LibraryExercise.custom("Жим Ларсена", "chest", "barbell")
        val profile = ProgramProfile.texas(ProgramInput.demo).saveCustomExercise(own)
        assertEquals("Жим Ларсена", profile.exerciseLibrary.first().name)
        assertTrue(own.isCustom)
        assertEquals(own.id, ExerciseLibrary.find("жим ларсена", profile.customExercises)?.id)
    }

    @Test
    fun templatePrescription() {
        val exercise = TemplateExercise(name = "Жим штанги лёжа", restSeconds = 180, sets = listOf(TemplateSet(0.0, 8), TemplateSet(80.0, 8), TemplateSet(82.5, 6)))
        val prescription = exercise.prescription
        assertEquals(3, prescription.sets)
        assertEquals("8", prescription.reps)
        assertEquals(LoadPrescription.Kilograms(80.0), prescription.load)
        assertTrue(TemplateExercise(name = "Подтягивания", sets = listOf(TemplateSet(0.0, 10))).prescription.needsOwnWeight)

        val template = WorkoutTemplate(name = "Грудь", exercises = listOf(exercise, TemplateExercise(name = "Подтягивания", sets = listOf(TemplateSet(0.0, 10)))))
        assertEquals("2 упр · 4 подх", template.summary)
        assertEquals(listOf("Грудь", "Спина"), template.groups())
        val copy = template.duplicate()
        assertNotEquals(template.id, copy.id)
        assertNotEquals(exercise.id, copy.exercises.first().id)
        assertEquals(exercise.sets, copy.exercises.first().sets)
    }

    @Test
    fun templateFromCommunityProgram() {
        val template = WorkoutTemplate.from("Руки", listOf(SocialProgramExercise("Молотки", 3, "8-12", "16 кг"), SocialProgramExercise("Отжимания", 4, "15", "—")))
        assertEquals(List(3) { TemplateSet(16.0, 8) }, template.exercises[0].sets)
        assertEquals(List(4) { TemplateSet(0.0, 15) }, template.exercises[1].sets)
    }

    @Test
    fun catalogIsUsable() {
        assertTrue(TemplateCatalog.all.size >= 8)
        for (template in TemplateCatalog.all) {
            assertTrue(template.name, template.exercises.isNotEmpty())
            for (exercise in template.exercises) assertNotNull("${template.name}: ${exercise.name}", ExerciseLibrary.find(exercise.name))
        }
    }

    @Test
    fun freeWorkoutDoesNotMoveProgram() {
        val base = ProgramProfile.texas(ProgramInput.demo)
        val template = WorkoutTemplate(name = "Руки", exercises = listOf(TemplateExercise(name = "Молотки с гантелями", sets = listOf(TemplateSet(16.0, 10)))))
        var (profile, workout) = base.startWorkout(template, Instant.ofEpochSecond(1_800_000_000))
        assertEquals(ProgramProfile.FREE_WEEK, workout.week)
        assertEquals("free-1", profile.dayKey(workout.week, workout.day.number))
        assertFalse(profile.isOwnKey("free-1"))

        val exercise = workout.day.exercises[0]
        profile = profile.recordSet(0, 1, exercise, 0, 10, 16.0)
        assertNotNull(profile.setLog["free-1|${exercise.key}|0"])

        profile = profile.toggleCompleted(0, 1, durationSeconds = 2400, effort = "Нормально")
        assertTrue(profile.isCompleted(0, 1))
        assertEquals(base.completedDayKeys, profile.completedDayKeys)
        assertEquals(base.lastCompletionEpochDay, profile.lastCompletionEpochDay)
        val record = profile.completionLog.first { it.key == "free-1" }
        assertEquals(0, record.week)
        assertEquals(2400, record.durationSeconds)

        assertEquals(listOf(16.0), profile.history("Молотки с гантелями").map { it.best })
        assertTrue(profile.loggedExerciseNames.contains("Молотки с гантелями"))
        assertEquals("Руки", profile.historyTitle(0, 1))
        assertEquals("Молотки с гантелями", PersonalRecords.names(profile)[exercise.key])

        val (withSecond, second) = profile.startWorkout(template)
        assertEquals(2, second.day.number)
        val discarded = withSecond.discardFreeWorkout(2)
        assertNull(discarded.freeDay(2))
        assertNotNull(discarded.discardFreeWorkout(1).freeDay(1))

        val deleted = discarded.deleteFreeWorkout(1)
        assertTrue(deleted.freeWorkouts.isEmpty())
        assertFalse(deleted.completionLog.any { it.key == "free-1" })
        assertNull(deleted.setLog["free-1|${exercise.key}|0"])
    }

    @Test
    fun historyIsChronologicalAcrossProgramAndFree() {
        var profile = ProgramProfile.texas(ProgramInput.demo)
        val day = profile.workoutPlan.weeks.first().days.first()
        val lift = day.exercises.first()
        val start = LocalDate.of(2027, 3, 1)

        profile = profile.recordSet(1, day.number, lift, 0, 5, 100.0)
        profile = profile.toggleCompleted(1, day.number, start)

        val zone = ZoneId.systemDefault()
        val (withFree, free) = profile.startFreeWorkout("Доп", listOf(ExercisePrescription(name = lift.name, sets = 1, reps = "5", load = LoadPrescription.RepRange("5"), id = "tx-a")),
            now = start.plusDays(1).atTime(12, 0).atZone(zone).toInstant())
        profile = withFree.recordSet(0, free.day.number, free.day.exercises[0], 0, 5, 105.0)
        profile = profile.toggleCompleted(0, free.day.number, start.plusDays(1))

        val second = profile.workoutPlan.weeks[1].days.first()
        val secondLift = second.exercises.first { it.name == lift.name }
        profile = profile.recordSet(2, second.number, secondLift, 0, 5, 110.0)
        profile = profile.toggleCompleted(2, second.number, start.plusDays(3))

        assertEquals(listOf(100.0, 105.0, 110.0), profile.history(lift.name).map { it.best })
        assertEquals(listOf(1, 0, 2), profile.history(lift.name).map { it.week })
    }

    @Test
    fun programProgressionIgnoresFreeWorkouts() {
        var profile = ProgramProfile.texas(ProgramInput.demo)
        val accessory = profile.workoutPlan.weeks.flatMap { it.days }.flatMap { it.exercises }.first { it.needsOwnWeight && it.topReps != null }
        val (withFree, free) = profile.startFreeWorkout("Доп", listOf(ExercisePrescription(name = accessory.name, sets = accessory.sets, reps = accessory.reps, load = LoadPrescription.RepRange(accessory.reps), id = "tx-b")))
        profile = withFree
        for (index in 0 until accessory.sets) profile = profile.recordSet(0, free.day.number, free.day.exercises[0], index, 30, 40.0)
        assertNull(profile.accessoryHint(accessory, 1, 1)?.weight)

        val (withNext, next) = profile.startFreeWorkout("Доп", listOf(ExercisePrescription(name = accessory.name, sets = accessory.sets, reps = accessory.reps, load = LoadPrescription.RepRange(accessory.reps), id = "tx-c")))
        assertNotNull(withNext.accessoryHint(next.day.exercises[0], 0, next.day.number)?.weight)
    }

    @Test
    fun replaceAndAppendInFreeWorkout() {
        val (profile, free) = ProgramProfile.texas(ProgramInput.demo).startWorkout(WorkoutTemplate(name = "Тест", exercises = listOf(TemplateExercise(name = "Подтягивания"))))
        val (replaced, fresh) = profile.replaceFreeExercise(free.day.number, free.day.exercises[0], "Тяга верхнего блока")
        assertEquals(listOf("Тяга верхнего блока"), replaced.freeDay(free.day.number)?.exercises?.map { it.name })
        assertEquals(3, fresh.sets)
        assertTrue(replaced.planEdits.isEmpty())
        val appended = replaced.appendFreeExercises(free.day.number, listOf(TemplateExercise(name = "Шраги с гантелями").prescription))
        assertEquals(2, appended.freeDay(free.day.number)?.exercises?.size)
    }

    @Test
    fun backupKeepsTemplatesAndFreeWorkouts() {
        var profile = ProgramProfile.texas(ProgramInput.demo).copy(name = "Шаблоны")
            .saveCustomExercise(LibraryExercise.custom("Жим Ларсена", "chest", "barbell"))
            .saveTemplate(WorkoutTemplate(name = "Руки", exercises = listOf(TemplateExercise(name = "Молотки с гантелями", restSeconds = 90, sets = listOf(TemplateSet(16.0, 12)))), isShared = true))
        val (started, free) = profile.startWorkout(profile.workoutTemplates[0])
        profile = started.recordSet(0, free.day.number, free.day.exercises[0], 0, 12, 16.0).toggleCompleted(0, free.day.number)

        val restored = BackupService.profile(BackupService.parse(BackupService.export(listOf(profile))).profiles.first())
        assertEquals(listOf("Жим Ларсена"), restored.customExercises.map { it.name })
        assertEquals(90, restored.workoutTemplates.first().exercises.first().restSeconds)
        assertTrue(restored.workoutTemplates.first().isShared)
        assertEquals(true, restored.freeWorkouts.first().completed)
        assertEquals(listOf(16.0), restored.history("Молотки с гантелями").map { it.best })
    }

    /// Так «Мои тренировки» и свободную тренировку пишет iPhone (копия — ISO 8601).
    @Test
    fun iosJson() {
        val json = Json { ignoreUnknownKeys = true }
        val template = json.decodeFromString<WorkoutTemplate>(
            """{"id":"T1","name":"Ноги","exercises":[{"id":"tx-1","name":"Приседания со штангой","restSeconds":180,"sets":[{"weight":100,"reps":5}]}],"isShared":false,"createdAt":"2026-03-01T07:00:00Z"}"""
        )
        assertEquals(TemplateSet(100.0, 5), template.exercises.first().sets.first())
        val day = json.decodeFromString<FreeWorkoutDay>(
            """{"number":3,"title":"Ноги","date":"2026-03-02T07:00:00Z","exercises":[{"id":"tx-1","name":"Приседания со штангой","sets":1,"reps":"5","kilograms":100}],"completed":true}"""
        )
        assertEquals(LoadPrescription.Kilograms(100.0), day.plan.exercises.first().load)
        assertEquals("free-3", day.plan.id)
        // Свои упражнения с iPhone без шагов техники.
        val own = json.decodeFromString<LibraryExercise>("""{"id":"my-1","name":"Жим Ларсена","group":"chest","secondary":[],"equipment":"barbell","steps":[]}""")
        assertTrue(own.isCustom)
    }
}
