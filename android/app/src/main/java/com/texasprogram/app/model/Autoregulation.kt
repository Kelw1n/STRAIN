package com.texasprogram.app.model

import kotlinx.serialization.Serializable

/// Основные движения, вокруг которых строится прогрессия.
enum class MainLift(val title: String) {
    SQUAT("Приседания"),
    BENCH("Жим лёжа"),
    DEADLIFT("Становая тяга");

    /// Верх тела прибавляет медленнее — шаг у него меньше.
    val isUpper: Boolean get() = this == BENCH

    companion object {
        fun byName(name: String): MainLift? = entries.firstOrNull { it.title == name }
    }
}

/// Как прошёл тяжёлый подход.
///
/// Спрашиваем одним касанием, без клавиатуры: в зале после рабочего подхода
/// заполнять форму никто не станет.
enum class LiftOutcome(val title: String) {
    EASY("Легко"),
    SOLID("Нормально"),
    GRIND("На пределе"),
    MISSED("Не добил");

    val subtitle: String
        get() = when (this) {
            EASY -> "осталось два повтора и больше"
            SOLID -> "остался один повтор"
            GRIND -> "впритык, запаса не было"
            MISSED -> "не сделал все повторения"
        }

    /// Прибавка к рабочему весу на следующую неделю.
    ///
    /// Числа — ориентир из практики линейных программ, а не закон: шаг вверх
    /// крупнее у ног, мельче у жима, а неудача откатывает процентом.
    fun next(weight: Double, isUpper: Boolean): Double = when (this) {
        EASY -> weight + if (isUpper) 2.5 else 5.0
        SOLID -> weight + 2.5
        GRIND -> weight
        MISSED -> weight * 0.925
    }

    val isFailure: Boolean get() = this == MISSED
}

/// Оценка одного движения за одну неделю.
@Serializable
data class LiftReport(
    /// Пространство имён цикла плюс номер недели: «3», «peak-3», «c2-3».
    val key: String,
    val lift: MainLift,
    val outcome: LiftOutcome,
    val epochDay: Long
)
