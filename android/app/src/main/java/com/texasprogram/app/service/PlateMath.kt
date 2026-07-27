package com.texasprogram.app.service

import kotlin.math.floor

/// Расчёт блинов на гриф и разминочных подходов — чистые функции от рабочего веса.
object PlateMath {
    const val BAR_WEIGHT = 20.0

    private val availablePlates = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

    /// Блины на одну сторону. `null`, если вес меньше грифа или не набирается:
    /// показать заведомо неверную раскладку хуже, чем не показать ничего.
    fun perSide(total: Double, bar: Double = BAR_WEIGHT): List<Double>? {
        if (total.isNaN() || total.isInfinite() || total < bar) return null
        var remaining = (total - bar) / 2
        if (remaining <= 0.0) return emptyList()

        val result = mutableListOf<Double>()
        for (plate in availablePlates) {
            while (remaining >= plate - 0.001) {
                result.add(plate)
                remaining -= plate
            }
        }
        return if (remaining < 0.01) result else null
    }

    fun perSideText(total: Double, bar: Double = BAR_WEIGHT): String? {
        val plates = perSide(total, bar) ?: return null
        if (plates.isEmpty()) return "пустой гриф"
        return plates.joinToString(" + ") { format(it) }
    }

    /// Разминка: пустой гриф, затем 55 / 70 / 85 процентов с падающим числом повторов.
    /// Ступени, совпавшие с грифом или рабочим весом, выбрасываются.
    fun warmups(working: Double, bar: Double = BAR_WEIGHT): List<WarmupSet> {
        if (working.isNaN() || working <= bar) return emptyList()

        val result = mutableListOf(WarmupSet(bar, 5))
        for ((percent, reps) in listOf(0.55 to 5, 0.70 to 3, 0.85 to 2)) {
            val weight = ProgramCalculator.roundToPlate(working * percent)
            if (weight <= bar || weight >= working) continue
            if (weight <= result.last().weight) continue
            result.add(WarmupSet(weight, reps))
        }
        return result
    }

    private fun format(value: Double): String =
        if (value == floor(value)) value.toInt().toString()
        else value.toString().trimEnd('0').trimEnd('.').replace('.', ',')
}

data class WarmupSet(val weight: Double, val reps: Int) {
    val weightText: String
        get() = if (weight == floor(weight)) weight.toInt().toString() else weight.toString()
}
