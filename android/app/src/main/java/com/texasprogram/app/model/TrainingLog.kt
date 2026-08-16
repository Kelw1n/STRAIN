package com.texasprogram.app.model

import kotlinx.serialization.Serializable
import java.util.UUID

/// Фактически выполненный подход: сколько повторов и с каким весом.
///
/// План говорит, что делать, а это — что получилось. Одно другое не заменяет:
/// без факта график показывает расчёт, а не тренировки.
@Serializable
data class SetEntry(
    val reps: Int,
    val weight: Double
) {
    /// Тоннаж подхода — повторы на вес.
    val tonnage: Double get() = reps * weight
}

/// Пропущенная тренировка.
///
/// Пропуск сам по себе расписание не ломает — невыполненное просто едет вперёд.
/// Ломает другое: веса растут по календарю, и после перерыва штанга оказывается
/// тяжелее, хотя стимула не было. Поэтому пропуск умеет задержать прогрессию.
@Serializable
data class SkippedWorkout(
    val id: String = UUID.randomUUID().toString(),
    val week: Int,
    val day: Int,
    /// Пикирование считает недели с единицы — его пропуски хранятся отдельно.
    val isPeaking: Boolean = false,
    val epochDay: Long,
    /// Держит ли пропуск прогрессию: неделя повторяет веса предыдущей,
    /// пока пропущенное не закрыто.
    val holdsProgression: Boolean
) {
    val title: String get() = "Неделя $week, день $day"
}

/// Замер веса тела.
///
/// Вводится руками. Google Fit сюда не подключаем: лишнее разрешение ради
/// одного числа, которое проще вписать.
@Serializable
data class BodyWeightEntry(
    val epochDay: Long,
    val weight: Double
)

/// Одна тренировка в истории упражнения.
data class ExerciseHistoryPoint(
    val week: Int,
    val day: Int,
    val planned: Double?,
    val entries: List<SetEntry>
) {
    /// Лучший подход дня: по нему и видно, растёт ли движение.
    val best: Double get() = entries.maxOfOrNull { it.weight } ?: 0.0
    val tonnage: Double get() = entries.sumOf { it.tonnage }
    val totalReps: Int get() = entries.sumOf { it.reps }
}

/// Подсказка по рабочему весу подсобки.
data class AccessoryHint(
    /// Нет веса — упражнение делается впервые, число брать неоткуда.
    val weight: Double?,
    /// Вес вырос по сравнению с прошлым разом.
    val isStepUp: Boolean,
    val text: String
) {
    companion object {
        /// Шаг прибавки. Два блина по 1,25 — самая мелкая пара, которая есть
        /// в обычном зале; меньше прибавить всё равно нечем.
        const val STEP = 2.5
    }
}

/// Тоннаж одной недели: сумма повторов на вес по всем записанным подходам.
data class WeeklyTonnage(
    val week: Int,
    val total: Double,
    val setCount: Int
)

/// Разбор ключа отметки подхода: «префикс + неделя-день|упражнение|номер».
///
/// Ключи собирались как строки задолго до тоннажа; разбирать их обратно надёжнее,
/// чем держать рядом второй индекс, который может разъехаться с первым.
object SetKeyParts {
    fun dayKey(key: String): String = key.substringBefore('|')

    /// Номер недели из ключа дня. Префикс задаёт цикл и пиковый режим,
    /// поэтому чужие ключи сюда не попадают.
    fun week(dayKey: String, prefix: String): Int? {
        if (!dayKey.startsWith(prefix)) return null
        return dayKey.removePrefix(prefix).substringBefore('-').toIntOrNull()
    }
}

/// «87.5» вместо «87.50» — для полей ввода, куда «кг» подставлять нельзя.
fun formatPlain(value: Double): String = formatWeight(value)

/// Тоннаж: до тонны — в килограммах, дальше — в тоннах с одним знаком.
fun formatTonnage(value: Double): String {
    if (value < 1000) return "${Math.round(value)} кг"
    val tons = Math.round(value / 100.0) / 10.0
    return "${tons.toString().replace('.', ',')} т"
}
