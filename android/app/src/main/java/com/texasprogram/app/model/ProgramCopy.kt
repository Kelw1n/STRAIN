package com.texasprogram.app.model

/// Копия чужой тренировки или программы — как «Своя программа»: её можно
/// поправить в конструкторе и запустить отдельным профилем.
object ProgramCopy {
    /// Вся программа друга или один её день — дни из вкладки «Программы».
    fun program(days: List<SocialProgramDay>, name: String) = CustomProgram(
        name = name,
        template = SplitTemplate.CUSTOM,
        weeks = 8,
        days = days.mapIndexed { index, day -> CustomDay(number = index + 1, title = day.title, exercises = day.exercises.map { exercise(it) }) }
    )

    /// Тренировка из поста — так, как её сделали: подходы, повторы и вес.
    fun program(workout: SocialWorkout, title: String, name: String) = CustomProgram(
        name = name,
        template = SplitTemplate.CUSTOM,
        weeks = 8,
        days = listOf(CustomDay(number = 1, title = title, exercises = workout.exercises.map { exercise(it) }))
    )

    fun exercise(item: SocialProgramDay.Exercise): CustomExercise {
        val kilograms = kilograms(item.load)
        return CustomExercise(
            name = item.name,
            sets = maxOf(item.sets, 1),
            reps = item.reps.ifBlank { "5" },
            kilograms = kilograms,
            loadText = if (kilograms == null && item.load.isNotBlank()) item.load else null
        )
    }

    /// Рабочий вес — самый тяжёлый подход, повторы — самые частые в подходах с ним.
    fun exercise(item: SocialWorkout.Exercise): CustomExercise {
        val heaviest = item.sets.maxOfOrNull { it.weight } ?: 0.0
        val reps = item.sets.filter { it.weight == heaviest }
            .groupingBy { it.reps }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key })
            .firstOrNull()?.key ?: 5
        return CustomExercise(
            name = item.name,
            sets = maxOf(item.sets.size, 1),
            reps = "$reps",
            kilograms = heaviest.takeIf { it > 0 },
            loadText = if (heaviest > 0) null else "свой вес"
        )
    }

    /// «100 кг» → 100, «82,5 кг» → 82,5, «220 lbs» → в килограммы. Остальное — подпись.
    fun kilograms(load: String): Double? {
        val text = load.lowercase().replace(',', '.').trim()
        if (!Regex("""^[0-9]+(\.[0-9]+)?\s*(кг|kg|lbs)$""").matches(text)) return null
        val value = text.takeWhile { it.isDigit() || it == '.' }.toDoubleOrNull()?.takeIf { it > 0 } ?: return null
        // Фунты — в килограммы с шагом 0,5.
        return if (text.endsWith("lbs")) Math.round(value / 2.20462 * 2) / 2.0 else value
    }
}
