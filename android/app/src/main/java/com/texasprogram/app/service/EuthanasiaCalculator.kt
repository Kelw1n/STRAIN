package com.texasprogram.app.service

import com.texasprogram.app.model.ExercisePrescription
import com.texasprogram.app.model.LoadPrescription
import com.texasprogram.app.model.WorkoutDayPlan
import com.texasprogram.app.model.WorkoutPlan
import com.texasprogram.app.model.WorkoutWeekPlan
import com.texasprogram.app.model.formatWeight
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.roundToInt

/// Три базовых движения «Эвтаназии худобы». Упражнение выбирается один раз
/// на весь цикл и тестируется перед началом.
enum class EuthanasiaPattern(val title: String) {
    PULL("Тяга"),
    SQUAT("Присед"),
    PRESS("Жим");

    /// Количество подъёмов штанги в блоке на плотность.
    val totalReps: Int
        get() = when (this) {
            PULL -> 45
            SQUAT -> 40
            PRESS -> 50
        }

    val options: List<String>
        get() = when (this) {
            PULL -> listOf("Тяга в наклоне", "Тяга Пендли", "Подтягивания")
            SQUAT -> listOf("Присед Зерчера", "Фронтальный присед", "Присед классический", "Присед с гирей", "Присед в колодец")
            PRESS -> listOf("Отжимания на брусьях", "Жим на наклонной", "Жим классический")
        }

    /// Ориентир по времени теста — из инструкции.
    val testHint: String
        get() = when (this) {
            PULL -> "обычно 15–25 минут"
            SQUAT -> "обычно 25–30 минут"
            PRESS -> "обычно 30–35 минут"
        }
}

/// Данные одного движения: выбранное упражнение и два теста.
@Serializable
data class EuthanasiaMovement(
    val exercise: String,
    /// Один повторный максимум.
    val oneRepMax: Double,
    /// За сколько минут пройдено КПШ на 75 % от максимума.
    val testMinutes: Double
)

/// Уровень сложности: отличается шагом уплотнения.
enum class EuthanasiaLevel(val title: String) {
    BEGINNER("Начальный"),
    MEDIUM("Средний"),
    PRO("Профи");

    val subtitle: String
        get() = when (this) {
            BEGINNER -> "К седьмой инверсии время сжимается до 60 %"
            MEDIUM -> "До 50 % — подойдёт большинству"
            PRO -> "До 40 % — программа проверит, насколько ты крут"
        }

    /// Насколько сокращается время за одну инверсию.
    val step: Double
        get() = when (this) {
            BEGINNER -> 0.0571
            MEDIUM -> 0.0714
            PRO -> 0.0857
        }

    /// Доля исходного времени теста на этой инверсии.
    fun timeFactor(inversion: Int): Double = max(1 - inversion * step, 0.1)
}

/// Вход программы: три движения и уровень.
@Serializable
data class EuthanasiaInput(
    val pull: EuthanasiaMovement,
    val squat: EuthanasiaMovement,
    val press: EuthanasiaMovement,
    val level: EuthanasiaLevel
) {
    fun movement(pattern: EuthanasiaPattern): EuthanasiaMovement = when (pattern) {
        EuthanasiaPattern.PULL -> pull
        EuthanasiaPattern.SQUAT -> squat
        EuthanasiaPattern.PRESS -> press
    }

    companion object {
        val demo = EuthanasiaInput(
            pull = EuthanasiaMovement("Тяга в наклоне", 100.0, 20.0),
            squat = EuthanasiaMovement("Присед классический", 140.0, 28.0),
            press = EuthanasiaMovement("Жим классический", 100.0, 32.0),
            level = EuthanasiaLevel.MEDIUM
        )
    }
}

/// «Эвтаназия худобы»: восемь недель, три тренировки, три базовых движения.
///
/// Первая неделя — тестовая, дальше семь «инверсий». Силовая часть циклится
/// 3×5 → 3×3 → волна 5/3/1, а блок на плотность держит одно и то же количество
/// повторений при сокращающемся времени: прогрессирует не вес, а плотность.
object EuthanasiaCalculator {
    /// Тестовая неделя плюс семь инверсий.
    const val WEEK_COUNT = 8

    private var cacheKey: Any? = null
    private var cacheValue: WorkoutPlan? = null

    fun generate(input: EuthanasiaInput): WorkoutPlan {
        cacheValue?.let { if (cacheKey == input) return it }
        val plan = WorkoutPlan(weeks = (1..WEEK_COUNT).map { week(it, input) }, isPeaking = false)
        cacheKey = input
        cacheValue = plan
        return plan
    }

    private data class Scheme(val sets: Int, val reps: String, val percent: Double)

    /// Проценты силовой части и блока плотности по инверсиям 1…7.
    private fun strength(inversion: Int): Pair<List<Scheme>, Double> = when (inversion) {
        1, 4 -> listOf(Scheme(3, "5", 0.65)) to 0.55
        2, 5 -> listOf(Scheme(3, "3", 0.70)) to 0.60
        3, 6 -> listOf(Scheme(1, "5", 0.65), Scheme(1, "3", 0.75), Scheme(1, "1", 0.85)) to 0.65
        // Седьмая инверсия: силовая как в первой, а плотность идёт
        // на тестовых 75 % — это и есть финальная проверка.
        else -> listOf(Scheme(3, "5", 0.65)) to 0.75
    }

    private fun week(number: Int, input: EuthanasiaInput): WorkoutWeekPlan {
        val inversion = number - 1
        val days = EuthanasiaPattern.entries.mapIndexed { index, pattern ->
            day(number, index + 1, pattern, inversion, input)
        }
        return WorkoutWeekPlan(number, days)
    }

    private fun day(week: Int, number: Int, pattern: EuthanasiaPattern, inversion: Int, input: EuthanasiaInput): WorkoutDayPlan {
        val movement = input.movement(pattern)
        val exercises = mutableListOf<ExercisePrescription>()

        if (inversion == 0) {
            // Инверсия 0 — неделя тестов: сначала максимум, потом время на КПШ.
            exercises.add(
                ExercisePrescription(
                    name = movement.exercise + " · тест 1ПМ",
                    sets = 1,
                    reps = "1",
                    load = LoadPrescription.TestOneRepMax,
                    id = "test-max-" + pattern.name
                )
            )
            exercises.add(
                ExercisePrescription(
                    name = movement.exercise + " · тест на время",
                    sets = 0,
                    reps = "${pattern.totalReps} повторений",
                    load = LoadPrescription.RepRange(
                        formatWeight(ProgramCalculator.roundToPlate(movement.oneRepMax * 0.75)) +
                            " кг · засеки время, " + pattern.testHint
                    ),
                    id = "test-time-" + pattern.name
                )
            )
        } else {
            val (scheme, density) = strength(inversion)
            scheme.forEachIndexed { index, item ->
                exercises.add(
                    ExercisePrescription(
                        name = movement.exercise,
                        sets = item.sets,
                        reps = item.reps,
                        load = LoadPrescription.Kilograms(ProgramCalculator.roundToPlate(movement.oneRepMax * item.percent)),
                        id = if (scheme.size > 1) "main-${pattern.name}-$index" else null
                    )
                )
            }
            // Блок плотности: повторов столько же, времени меньше.
            val minutes = movement.testMinutes * input.level.timeFactor(inversion)
            exercises.add(
                ExercisePrescription(
                    name = movement.exercise + " · плотность",
                    sets = 0,
                    reps = "${pattern.totalReps} повторений",
                    load = LoadPrescription.RepRange(
                        formatWeight(ProgramCalculator.roundToPlate(movement.oneRepMax * density)) +
                            " кг · за " + timeText(minutes)
                    ),
                    id = "density-" + pattern.name
                )
            )
        }

        exercises.addAll(accessories(pattern, inversion))
        val title = if (inversion == 0) "ТЕСТ · " + pattern.title.uppercase()
        else "ИНВЕРСИЯ $inversion · " + pattern.title.uppercase()
        return WorkoutDayPlan("$week-$number", number, title, exercises)
    }

    /// «22:30» вместо «22.5 мин» — так читается с секундомера.
    fun timeText(minutes: Double): String {
        val total = (minutes * 60).roundToInt()
        return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
    }

    // MARK: - Подсобка

    /// Четыре подсобных на каждый день. Схема одинаковая для всех, меняется
    /// только по инверсиям: объём растёт, седьмая идёт разгрузочной.
    private val accessoryPlan = listOf(
        Triple(3, "12", "RPE 6,5"),
        Triple(4, "10", "RPE 7"),
        Triple(3, "8", "RPE 7,5"),
        Triple(4, "10", "RPE 7,5"),
        Triple(5, "8", "RPE 8"),
        Triple(5, "6", "RPE 8,5"),
        Triple(5, "8", "RPE 9"),
        Triple(3, "10", "RPE 7")
    )

    private fun accessoryNames(pattern: EuthanasiaPattern): List<String> = when (pattern) {
        EuthanasiaPattern.PULL -> listOf(
            "Суперсет: разгибание голени сидя в тренажёре U + подъем на бицепс молоток U",
            "Суперсет: жим гантели стоя U + сгибание голени сидя/лёжа в тренажёре",
            "Разведение гантели на горизонтальной скамье на грудь U",
            "Суперсет: подъем на носки стоя U + молитва в блоке на пресс"
        )
        EuthanasiaPattern.SQUAT -> listOf(
            "Суперсет: тяга горизонтальная удобной рукояткой + мах гантели в сторону U",
            "Суперсет: сведение ног в тренажёре + разведение ног в тренажёре",
            "Вертикальная тяга нейтральным средне-широким хватом",
            "Разгибание на трицепс с удобной рукояткой в блоке U"
        )
        EuthanasiaPattern.PRESS -> listOf(
            "Суперсет: румынская тяга с гирей U + подъем штанги на бицепс",
            "Суперсет: мертвая тяга в стороны + отведение на заднюю дельту (тренажёр/гантели)",
            "Подъем на носки сидя",
            "Копенгагенская планка"
        )
    }

    private fun accessories(pattern: EuthanasiaPattern, inversion: Int): List<ExercisePrescription> {
        val plan = accessoryPlan[inversion.coerceIn(0, accessoryPlan.size - 1)]
        return accessoryNames(pattern).map { name ->
            ExercisePrescription(name, plan.first, plan.second, LoadPrescription.Rpe(plan.third), isOptional = true)
        }
    }
}
