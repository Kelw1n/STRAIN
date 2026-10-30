@file:UseSerializers(IsoInstantSerializer::class)

package com.texasprogram.app.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max

// MARK: - Пищевая ценность

/// Калории и Б/Ж/У: одна запись, итог дня или норма на день.
@Serializable
data class Nutrients(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0
) {
    operator fun plus(other: Nutrients) =
        Nutrients(kcal + other.kcal, protein + other.protein, fat + other.fat, carbs + other.carbs)

    fun divided(count: Int): Nutrients {
        if (count <= 0) return ZERO
        return Nutrients(kcal / count, protein / count, fat / count, carbs / count)
    }

    /// Норма со сдвигом углеводов или жиров: калории меняются вместе с граммами.
    fun adding(carbs: Double = 0.0, fat: Double = 0.0) = Nutrients(
        kcal = max(0.0, kcal + carbs * 4 + fat * 9),
        protein = protein,
        fat = max(0.0, this.fat + fat),
        carbs = max(0.0, this.carbs + carbs)
    )

    /// Порция другого веса: всё меняется в той же пропорции.
    fun scaled(factor: Double): Nutrients {
        if (!factor.isFinite() || factor < 0) return this
        return Nutrients(kcal * factor, protein * factor, fat * factor, carbs * factor)
    }

    /// Для нормы: калории до десятков, граммы до целых.
    fun rounded() = Nutrients(roundHalfUp(kcal / 10) * 10, roundHalfUp(protein), roundHalfUp(fat), roundHalfUp(carbs))

    companion object {
        val ZERO = Nutrients()

        /// Калории по Б/Ж/У: 4 ккал в грамме белка и углеводов, 9 — в грамме жира.
        fun kcal(protein: Double, fat: Double, carbs: Double): Double = protein * 4 + fat * 9 + carbs * 4
    }
}

/// Округление как `rounded()` в Swift: половинки — от нуля. `kotlin.math.round`
/// округляет их к чётному, и 2,5 стало бы 2 — на iPhone та же норма вышла бы другой.
fun roundHalfUp(value: Double): Double =
    if (value < 0) -Math.round(-value).toDouble() else Math.round(value).toDouble()

// MARK: - Дневник

/// Одна запись дневника: продукт или блюдо.
@Serializable
data class FoodEntry(
    val id: String = newNutritionId(),
    /// Когда съедено. День записи — по местной дате.
    val date: Instant,
    val name: String,
    /// Порция в граммах, если известна.
    val grams: Double? = null,
    val nutrients: Nutrients,
    /// Распознано по фото — видно, где цифры от ИИ, а где свои.
    val fromPhoto: Boolean = false
)

/// Идентификатор как у iOS (`UUID().uuidString`): копию с Android читает iPhone.
fun newNutritionId(): String = java.util.UUID.randomUUID().toString().uppercase()

@Serializable
enum class NutritionGoal(val title: String, val explanation: String) {
    @SerialName("bulk") BULK("Набор", "Каждую неделю +30 г углеводов, пока вес не пойдёт вверх"),
    @SerialName("cut") CUT("Сушка", "Каждую неделю −13 г жиров, пока вес не пойдёт вниз"),
    @SerialName("hold") HOLD("Держать вес", "Ешь на поддержке, вес держится на месте")
}

/// Как менялась норма: с какого дня и почему.
@Serializable
data class NormChange(
    val date: Instant,
    val norm: Nutrients,
    val note: String
)

/// Где человек в методе: неделя наблюдения, затем цель и норма на день.
@Serializable
data class NutritionPlan(
    /// Первый день недели наблюдения — полночь по местному времени.
    val observationStart: Instant,
    /// Поддержка, найденная неделей наблюдения.
    val maintenance: Nutrients? = null,
    val goal: NutritionGoal? = null,
    /// Норма на день сейчас.
    val target: Nutrients? = null,
    /// С какого дня действует текущая норма — от него считается неделя до проверки.
    val stepStart: Instant? = null,
    val history: List<NormChange> = emptyList()
)

/// Всё питание профиля. Лежит отдельным файлом, а не в профиле:
/// так вкладку можно убрать, и прошлая версия приложения откроется как была.
@Serializable
data class NutritionData(
    val entries: List<FoodEntry> = emptyList(),
    val plan: NutritionPlan? = null
)

/// Дата в JSON — ISO 8601 без долей секунды, как пишет и читает iOS-версия:
/// с долями iPhone не открыл бы копию целиком.
object IsoInstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("IsoInstant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.truncatedTo(ChronoUnit.SECONDS).toString())
    }

    override fun deserialize(decoder: Decoder): Instant {
        val text = decoder.decodeString()
        return runCatching { Instant.parse(text) }.getOrElse { OffsetDateTime.parse(text).toInstant() }
    }
}

// MARK: - Метод Лимара

/// Метод Лимара (@freedom_limar): неделю ешь как обычно и записывай еду и вес
/// натощак. Средние калории при стоящем весе — поддержка. Дальше на наборе
/// каждую неделю +30 г углеводов, на сушке — минус жиры, и смотришь на вес.
///
/// Свои правила поверх ролика: сравниваем средний вес недели, а не дни — по
/// дням вес прыгает; неделя считается, если записано хотя бы 5 дней из 7.
object LimarMethod {
    const val WEEK_LENGTH = 7
    const val MINIMUM_DAYS = 5
    /// Средний вес сдвинулся меньше чем на столько к прошлой неделе — «стоит на месте».
    const val STABLE_CHANGE = 0.2
    /// Внутри недели наблюдения вес прыгает сильнее — начало с концом сравниваем с запасом.
    const val OBSERVATION_SHIFT = 0.5
    const val CARB_STEP = 30.0
    /// Те же ~120 ккал, что и 30 г углеводов на наборе.
    const val FAT_STEP = 13.0
    /// Ниже этого жиры лучше не опускать: предупреждаем, но не запрещаем.
    const val FAT_FLOOR_PER_KG = 0.6

    fun day(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): LocalDate = instant.atZone(zone).toLocalDate()

    /// Полночь этого дня — так iPhone хранит начало недели и нормы.
    fun startOf(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Instant = day.atStartOfDay(zone).toInstant()

    fun entries(entries: List<FoodEntry>, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<FoodEntry> =
        entries.filter { day(it.date, zone) == day }.sortedBy { it.date }

    fun total(entries: List<FoodEntry>): Nutrients = entries.fold(Nutrients.ZERO) { sum, entry -> sum + entry.nutrients }

    /// Семь дней начиная с `start`: сколько дней записано и что в среднем.
    data class Week(
        val start: LocalDate,
        val foodDays: Int,
        val weightDays: Int,
        /// Среднее за день — по дням, где еда записана.
        val intake: Nutrients?,
        val weight: Double?,
        /// Последние три замера против первых трёх: насколько вес сдвинулся внутри недели.
        val shift: Double?
    ) {
        val isComplete: Boolean get() = foodDays >= MINIMUM_DAYS && weightDays >= MINIMUM_DAYS
    }

    fun week(start: LocalDate, entries: List<FoodEntry>, weights: List<BodyWeightEntry>, zone: ZoneId = ZoneId.systemDefault()): Week {
        val end = start.plusDays(WEEK_LENGTH.toLong())
        val days = entries
            .filter { day(it.date, zone).let { date -> !date.isBefore(start) && date.isBefore(end) } }
            .groupBy { day(it.date, zone) }
            .values
            .map(::total)
        val measured = weights
            .filter { it.epochDay >= start.toEpochDay() && it.epochDay < end.toEpochDay() }
            .sortedBy { it.epochDay }
            .map { it.weight }
        val shift = if (measured.size >= MINIMUM_DAYS) average(measured.takeLast(3)) - average(measured.take(3)) else null
        return Week(
            start = start,
            foodDays = days.size,
            weightDays = measured.size,
            intake = if (days.isEmpty()) null else days.fold(Nutrients.ZERO, Nutrients::plus).divided(days.size),
            weight = if (measured.isEmpty()) null else average(measured),
            shift = shift
        )
    }

    /// Неделя наблюдения: идёт, мало записей или готово.
    sealed interface Observation {
        /// Какой сейчас день недели наблюдения, 1…7.
        data class Collecting(val day: Int, val week: Week) : Observation
        data class NotEnough(val week: Week) : Observation
        /// Средние калории — поддержка. `shift` есть, если вес внутри недели заметно сдвинулся.
        data class Done(val week: Week, val maintenance: Nutrients, val shift: Double?) : Observation
    }

    fun observation(
        plan: NutritionPlan,
        entries: List<FoodEntry>,
        weights: List<BodyWeightEntry>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Observation {
        val summary = week(day(plan.observationStart, zone), entries, weights, zone)
        val passed = ChronoUnit.DAYS.between(summary.start, today).toInt()
        if (passed < WEEK_LENGTH) return Observation.Collecting(max(passed, 0) + 1, summary)
        val intake = summary.intake
        if (!summary.isComplete || intake == null) return Observation.NotEnough(summary)
        val shift = summary.shift?.takeIf { abs(it) >= OBSERVATION_SHIFT }
        return Observation.Done(summary, intake.rounded(), shift)
    }

    /// С чего начать цель: на наборе сразу +30 г углеводов, на сушке −13 г жиров.
    fun startingNorm(goal: NutritionGoal, maintenance: Nutrients): Nutrients = when (goal) {
        NutritionGoal.BULK -> maintenance.adding(carbs = CARB_STEP)
        NutritionGoal.CUT -> maintenance.adding(fat = -FAT_STEP)
        NutritionGoal.HOLD -> maintenance
    }

    enum class Advice { KEEP, ADD_CARBS, REMOVE_CARBS, CUT_FAT }

    /// Проверка раз в неделю: что стало с весом при текущей норме и что делать дальше.
    data class Check(
        /// Средний вес этой недели против прошлой; `null` — замеров мало.
        val change: Double?,
        val advice: Advice,
        /// Норма, если принять совет.
        val proposed: Nutrients,
        /// Жиры по совету опустятся ниже 0,6 г на кило веса.
        val lowFat: Boolean
    )

    /// `null`, пока с начала текущей нормы не прошло семи дней.
    fun check(
        plan: NutritionPlan,
        weights: List<BodyWeightEntry>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Check? {
        val goal = plan.goal ?: return null
        val target = plan.target ?: return null
        val start = day(plan.stepStart ?: return null, zone)
        if (ChronoUnit.DAYS.between(start, today) < WEEK_LENGTH) return null
        val current = averageWeight(weights, start)
        val previous = averageWeight(weights, start.minusDays(WEEK_LENGTH.toLong()))
        val change = if (current != null && previous != null) current - previous else null

        var advice = Advice.KEEP
        if (change != null) {
            advice = when (goal) {
                NutritionGoal.BULK -> if (change < STABLE_CHANGE) Advice.ADD_CARBS else Advice.KEEP
                NutritionGoal.CUT -> if (change > -STABLE_CHANGE) Advice.CUT_FAT else Advice.KEEP
                NutritionGoal.HOLD -> when {
                    change >= STABLE_CHANGE -> Advice.REMOVE_CARBS
                    change <= -STABLE_CHANGE -> Advice.ADD_CARBS
                    else -> Advice.KEEP
                }
            }
        }
        val proposed = when (advice) {
            Advice.KEEP -> target
            Advice.ADD_CARBS -> target.adding(carbs = CARB_STEP)
            Advice.REMOVE_CARBS -> target.adding(carbs = -CARB_STEP)
            Advice.CUT_FAT -> target.adding(fat = -FAT_STEP)
        }
        val bodyWeight = current ?: previous ?: weights.maxByOrNull { it.epochDay }?.weight
        val lowFat = advice == Advice.CUT_FAT && bodyWeight != null && proposed.fat < bodyWeight * FAT_FLOOR_PER_KG
        return Check(change, advice, proposed, lowFat)
    }

    /// Средний вес семи дней от `start`; меньше пяти замеров — не считаем.
    fun averageWeight(weights: List<BodyWeightEntry>, start: LocalDate): Double? {
        val first = start.toEpochDay()
        val values = weights.filter { it.epochDay >= first && it.epochDay < first + WEEK_LENGTH }.map { it.weight }
        return if (values.size >= MINIMUM_DAYS) average(values) else null
    }

    private fun average(values: List<Double>): Double = if (values.isEmpty()) 0.0 else values.sum() / values.size
}
