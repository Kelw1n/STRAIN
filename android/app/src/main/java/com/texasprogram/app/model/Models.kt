package com.texasprogram.app.model

import kotlinx.serialization.Serializable

enum class TrainingProgramKind(val title: String) {
    TEXAS("Техасский метод"),
    UPPER_LOWER("Верх / Низ");

    val subtitle: String
        get() = when (this) {
            TEXAS -> "12 недель · 3 тренировки в неделю · расчёт по 5ПМ"
            UPPER_LOWER -> "7 недель · 4 тренировки в неделю · расчёт по 1ПМ"
        }
}

enum class TrainingLevel(val title: String) {
    BEGINNER("Начальный"),
    INTERMEDIATE("Средний")
}

/// Как невыполненные тренировки раскладываются по календарю.
enum class ScheduleMode(val title: String) {
    /// День недели задаёт тип тренировки: в понедельник — понедельничная,
    /// просто ближайшей невыполненной недели.
    WEEKDAY("По дням недели"),

    /// Невыполненные идут строго подряд по ближайшим тренировочным дням,
    /// даже если тип дня не совпадает с днём недели.
    QUEUE("Очередь");

    val explanation: String
        get() = when (this) {
            WEEKDAY -> "В понедельник — понедельничная тренировка, в четверг — четверговая. Пропущенная не теряется: она достанется следующему такому же дню недели."
            QUEUE -> "Невыполненные тренировки идут строго подряд по ближайшим тренировочным дням. Пропустил четверг — сделаешь его в ближайший тренировочный день, каким бы он ни был."
        }
}

enum class AdditionalExerciseCategory(val title: String) {
    PULL("Дополнительная тяга"),
    ARMS("Руки"),
    CORE("Кор"),
    BACK("Спина"),
    PRESS("Вертикальный жим");

    val options: List<String>
        get() = when (this) {
            PULL -> listOf(
                "Становая тяга с плинтов (ниже колена)",
                "Тяга трэп-грифа",
                "Румынская тяга",
                "Становая тяга рывковым хватом на прямых ногах",
                "Обратная гиперэкстензия",
                "Glute Ham Raise (GHR)"
            )
            ARMS -> listOf("Растянутый суперсет: бицепс + трицепс")
            CORE -> listOf(
                "Копенгагенская планка",
                "Молитва в блоке",
                "Скручивания лёжа на полу",
                "Подъём ног в висе",
                "Скручивания на фитболе"
            )
            BACK -> listOf(
                "Подтягивания",
                "Тяга верхнего блока узким/широким хватом",
                "Вертикальная тяга в хамере",
                "Тяга Пендли",
                "Тяга в наклоне",
                "Горизонтальная тяга блока узким/широким хватом"
            )
            PRESS -> listOf(
                "Жим штанги стоя",
                "Жим гантелей сидя",
                "Армейский жим гирь",
                "Отжимания в стойке на руках",
                "Z-жим"
            )
        }
}

/// Запись о выполненной тренировке: когда и с какими весами основных движений.
@Serializable
data class CompletionRecord(
    val key: String,
    val epochDay: Long,
    val week: Int,
    val day: Int,
    val squat: Double? = null,
    val bench: Double? = null,
    val deadlift: Double? = null
)

data class ProgramInput(
    val squat5RM: Double,
    val bench5RM: Double,
    val deadlift5RM: Double,
    val level: TrainingLevel,
    val pull: String? = null,
    val arms: String? = null,
    val core: String? = null,
    val back: String? = null,
    val press: String? = null
) {
    companion object {
        val demo = ProgramInput(100.0, 100.0, 100.0, TrainingLevel.INTERMEDIATE, core = "Копенгагенская планка")
    }
}

data class UpperLowerInput(
    val squat1RM: Double,
    val bench1RM: Double,
    val deadlift1RM: Double
) {
    companion object {
        val demo = UpperLowerInput(100.0, 100.0, 130.0)
    }
}

/// Нагрузка упражнения: конкретный вес, RPE, диапазон или тест максимума.
@Serializable
sealed class LoadPrescription {
    @Serializable
    data class Kilograms(val value: Double) : LoadPrescription()

    @Serializable
    data class Rpe(val text: String) : LoadPrescription()

    @Serializable
    data class RepRange(val text: String) : LoadPrescription()

    @Serializable
    data object TestOneRepMax : LoadPrescription()

    val displayText: String
        get() = when (this) {
            is Kilograms -> formatWeight(value) + " кг"
            is Rpe -> text
            is RepRange -> text
            TestOneRepMax -> "Тест 1ПМ"
        }
}

/// «87.5» вместо «87.50», «90» вместо «90.0» — как в iOS-версии.
fun formatWeight(value: Double): String {
    val rounded = Math.round(value * 10.0) / 10.0
    return if (rounded == Math.floor(rounded)) rounded.toInt().toString()
    else rounded.toString()
}

data class ExercisePrescription(
    val name: String,
    val sets: Int,
    val reps: String,
    val load: LoadPrescription,
    val isOptional: Boolean = false,
    /// Номер тренировки в волне «Жим 14», если упражнение — часть волны.
    val benchSession: Int? = null,
    /// Только у своих упражнений: имя можно поменять, а отметки подходов
    /// должны остаться на месте, поэтому у них отдельный идентификатор.
    val id: String? = null
) {
    /// Ключ идентичности. У упражнений программы это имя — так отметки,
    /// сохранённые до появления правок, продолжают находиться.
    val key: String get() = id ?: name
}

data class WorkoutDayPlan(
    val id: String,
    val number: Int,
    val title: String,
    val exercises: List<ExercisePrescription>
)

data class WorkoutWeekPlan(
    val number: Int,
    val days: List<WorkoutDayPlan>
)

data class WorkoutPlan(
    val weeks: List<WorkoutWeekPlan>,
    val isPeaking: Boolean
)
