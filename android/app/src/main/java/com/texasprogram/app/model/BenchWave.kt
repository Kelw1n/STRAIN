package com.texasprogram.app.model

/// Тип подхода в волне из 14 жимовых тренировок.
enum class BenchSetKind(val title: String) {
    NORMAL("обычный"),
    NEGATIVE("негатив"),
    TEST("тест"),
    RECORD("рекорд");

    val isSpecial: Boolean get() = this != NORMAL

    val hint: String
        get() = when (this) {
            NORMAL -> "Рабочий подход"
            NEGATIVE -> "Негатив — только со страхующим"
            TEST -> "Подход на максимум повторов"
            RECORD -> "Попытка нового рекорда"
        }
}

/// Один подход жима: процент от 1ПМ, посчитанный вес и повторы.
data class BenchSetPlan(
    val id: Int,
    val percent: Double,
    val weight: Double,
    val reps: String,
    val kind: BenchSetKind
) {
    val percentText: String get() = "${Math.round(percent * 100)}%"
    val weightText: String get() = "${Math.round(weight)} кг"
}

/// Одна из 14 жимовых тренировок волнового цикла.
data class BenchSessionPlan(
    val id: Int,
    val week: Int,
    val dayNumber: Int,
    val dayTitle: String,
    val sets: List<BenchSetPlan>
) {
    /// Первый нестандартный подход задаёт характер тренировки.
    val highlight: BenchSetKind? get() = sets.firstOrNull { it.kind.isSpecial }?.kind

    val exerciseName: String
        get() = highlight?.let { "Жим лёжа · ${it.title}" } ?: "Жим лёжа"

    val topWeight: Double get() = sets.maxOfOrNull { it.weight } ?: 0.0

    val repsText: String get() = sets.joinToString(" / ") { it.reps }

    val weightsText: String get() = sets.joinToString(" / ") { Math.round(it.weight).toString() } + " кг"
}
