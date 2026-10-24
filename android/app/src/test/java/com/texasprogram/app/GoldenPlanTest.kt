package com.texasprogram.app

import com.texasprogram.app.model.CustomDay
import com.texasprogram.app.model.CustomExercise
import com.texasprogram.app.model.CustomProgram
import com.texasprogram.app.model.LoadPrescription
import com.texasprogram.app.model.ProgramInput
import com.texasprogram.app.model.SplitTemplate
import com.texasprogram.app.model.TrainingLevel
import com.texasprogram.app.model.UpperLowerInput
import com.texasprogram.app.model.WorkoutPlan
import com.texasprogram.app.service.EuthanasiaCalculator
import com.texasprogram.app.service.EuthanasiaInput
import com.texasprogram.app.service.EuthanasiaLevel
import com.texasprogram.app.service.EuthanasiaMovement
import com.texasprogram.app.service.FullBodyCalculator
import com.texasprogram.app.service.FullBodyLevel
import com.texasprogram.app.service.ProTexasCalculator
import com.texasprogram.app.service.ProgramCalculator
import com.texasprogram.app.service.UpperLowerCalculator
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/// Эталонная распечатка планов всех программ. Та же распечатка есть в тестах
/// iOS (GoldenPlanTests): одни и те же максимумы должны давать одни и те же
/// веса, подходы и повторы на обеих платформах — построчно.
class GoldenPlanTest {
    /// Неудобные максимумы: на круглых 100 кг ошибки округления не видны.
    private val odd = ProgramInput(
        142.5, 97.5, 187.5, TrainingLevel.BEGINNER,
        pull = "Подтягивания", arms = "Растянутый суперсет: бицепс + трицепс", core = "Скручивания на фитболе",
        back = "Румынская тяга", press = "Z-жим"
    )
    private val light = ProgramInput(61.25, 43.75, 81.25, TrainingLevel.INTERMEDIATE)

    private fun euthanasia(level: EuthanasiaLevel) = EuthanasiaInput(
        pull = EuthanasiaMovement("Тяга в наклоне", 87.5, 23.0),
        squat = EuthanasiaMovement("Присед классический", 132.5, 27.5),
        press = EuthanasiaMovement("Жим классический", 72.5, 31.0),
        level = level
    )

    private val custom = CustomProgram(
        name = "Тест", template = SplitTemplate.CUSTOM, weeks = 3, days = listOf(
            CustomDay(
                id = "d1", number = 1, title = "ДЕНЬ 1", exercises = listOf(
                    CustomExercise(id = "own-a", name = "Жим", sets = 3, reps = "5", kilograms = 61.3, weeklyIncrement = 2.5),
                    CustomExercise(id = "own-b", name = "Планка", sets = 3, reps = "60 с", loadText = "свой вес"),
                    CustomExercise(id = "own-c", name = "Тяга", sets = 4, reps = "8")
                )
            )
        )
    )

    private fun scenarios(): List<Pair<String, WorkoutPlan>> = buildList {
        add("texas-demo" to ProgramCalculator.generate(ProgramInput.demo))
        add("texas-odd" to ProgramCalculator.generate(odd))
        add("texas-light" to ProgramCalculator.generate(light))
        add("peaking-odd" to ProgramCalculator.generatePeaking(odd))
        add("peaking-light" to ProgramCalculator.generatePeaking(light))
        add("ul-demo" to UpperLowerCalculator.generate(UpperLowerInput.demo))
        add("ul-odd" to UpperLowerCalculator.generate(UpperLowerInput(152.5, 107.5, 192.5)))
        add("pt-odd" to ProTexasCalculator.generate(odd))
        add("pt-light" to ProTexasCalculator.generate(light))
        add("eu-demo" to EuthanasiaCalculator.generate(EuthanasiaInput.demo))
        add("custom" to custom.plan)
        FullBodyLevel.entries.forEach { add("fb-${it.name}-odd" to FullBodyCalculator.generate(odd, it)) }
        add("fb-TWO_YEARS-light" to FullBodyCalculator.generate(light, FullBodyLevel.TWO_YEARS))
        EuthanasiaLevel.entries.forEach { add("eu-${it.name}" to EuthanasiaCalculator.generate(euthanasia(it))) }
    }

    private fun dump(plan: WorkoutPlan): List<String> = buildList {
        plan.weeks.forEach { week ->
            week.days.forEach { day ->
                add("W${week.number} D${day.number} ${day.id} ${day.title}")
                day.exercises.forEach { e ->
                    add("  ${e.key}|${e.name}|${e.sets}|${e.reps}|${load(e.load)}|${if (e.isOptional) 1 else 0}|${e.benchSession ?: "-"}")
                }
            }
        }
    }

    private fun load(load: LoadPrescription): String = when (load) {
        is LoadPrescription.Kilograms -> String.format(Locale.US, "kg %.3f", load.value)
        is LoadPrescription.Rpe -> "rpe " + load.text
        is LoadPrescription.RepRange -> "range " + load.text
        LoadPrescription.TestOneRepMax -> "test1rm"
    }

    /// Пишет распечатку в build/golden/plans.txt — CI печатает её строками «GOLDEN|…».
    @Test
    fun printGoldenPlans() {
        val lines = scenarios().flatMap { (name, plan) -> listOf("# $name") + dump(plan) }
        File("build/golden").mkdirs()
        File("build/golden/plans.txt").writeText(lines.joinToString("\n") + "\n")
        assertTrue(lines.size > 1000)
    }
}
