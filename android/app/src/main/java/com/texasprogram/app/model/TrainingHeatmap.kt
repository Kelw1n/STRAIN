package com.texasprogram.app.model

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

// MARK: - Карта тренировок за год

/// Год тренировок клеточками, как на GitHub: строка цифр 0–4 по дням, последняя —
/// день `end`. 0 — тренировки не было, 1–4 — насколько тяжёлым был день.
@Serializable
data class SocialHeatmap(
    /// День последней цифры, «2026-10-28» по времени телефона.
    val end: String,
    val days: String
)

object TrainingHeatmap {
    /// 53 недели: последняя неделя может быть неполной, первая — с понедельника.
    const val LENGTH = 371

    /// Тяжесть дня — по тоннажу, от своих же обычных тренировок: день на уровне
    /// девяти десятых самых тяжёлых — ярче всего. Без записанных подходов — бледно.
    fun build(log: List<CompletionRecord>, sets: Map<String, SetEntry>, today: LocalDate = LocalDate.now()): SocialHeatmap {
        val tonnage = HashMap<String, Double>()
        for ((key, entry) in sets) {
            val bar = key.indexOf('|')
            if (bar < 0) continue
            val day = key.substring(0, bar)
            tonnage[day] = (tonnage[day] ?: 0.0) + entry.tonnage
        }
        val latest = HashMap<String, Long>()
        for (record in log) latest[record.key] = maxOf(latest[record.key] ?: record.epochDay, record.epochDay)
        val end = today.toEpochDay()
        val perDay = DoubleArray(LENGTH) { -1.0 }
        for ((key, epochDay) in latest) {
            val back = end - epochDay
            if (back < 0 || back >= LENGTH) continue
            val index = (LENGTH - 1 - back).toInt()
            perDay[index] = maxOf(perDay[index], 0.0) + (tonnage[key] ?: 0.0)
        }
        val worked = perDay.filter { it > 0 }.sorted()
        val reference = if (worked.isEmpty()) 0.0 else worked[((worked.size - 1) * 0.9).toInt()]
        val digits = CharArray(LENGTH) { index ->
            val value = perDay[index]
            when {
                value < 0 -> '0'
                value <= 0 || reference <= 0 -> '1'
                else -> ('0' + ceil(4 * value / reference).toInt().coerceIn(1, 4))
            }
        }
        return SocialHeatmap(today.toString(), String(digits))
    }

    fun build(profile: ProgramProfile, today: LocalDate = LocalDate.now()): SocialHeatmap =
        build(profile.completionLog, profile.setLog, today)

    /// Уровни на сегодня: карта друга могла прийти несколько дней назад —
    /// недостающие дни в конце пустые, лишние в начале отрезаем.
    fun levels(heatmap: SocialHeatmap, today: LocalDate = LocalDate.now()): List<Int> {
        var levels = heatmap.days.map { (it - '0').coerceIn(0, 4) }
        val end = runCatching { LocalDate.parse(heatmap.end) }.getOrNull()
        if (end != null) {
            val gap = ChronoUnit.DAYS.between(end, today)
            if (gap > 0) levels = levels + List(minOf(gap, LENGTH.toLong()).toInt()) { 0 }
            if (gap < 0) levels = levels.dropLast(minOf(-gap, levels.size.toLong()).toInt())
        }
        if (levels.size < LENGTH) levels = List(LENGTH - levels.size) { 0 } + levels
        return levels.takeLast(LENGTH)
    }

    /// Сколько клеточек рисовать: полные 52 недели и текущая — с понедельника по сегодня.
    fun visibleCount(today: LocalDate = LocalDate.now()): Int = 52 * 7 + today.dayOfWeek.value

    /// Тренировок за последние 365 дней.
    fun workouts(levels: List<Int>): Int = levels.takeLast(365).count { it > 0 }
}
