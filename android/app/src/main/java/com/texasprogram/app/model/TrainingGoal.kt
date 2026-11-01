package com.texasprogram.app.model

import com.texasprogram.app.service.RuPlural
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/// «Цель» как в REBRN: дни, срок, время каждой тренировки и несколько
/// напоминаний до неё. Расчёт тот же, что на iOS, — `TrainingGoal.swift`.
object TrainingGoal {
    /// За сколько минут до тренировки можно напомнить.
    val offsetOptions = listOf(0, 15, 30, 60, 120)
    /// Не больше трёх напоминаний на тренировку.
    const val MAX_OFFSETS = 3
    val monthOptions = listOf(0, 1, 2, 3, 6, 12)

    fun offsetTitle(minutes: Int): String = when (minutes) {
        0 -> "В начале тренировки"
        60 -> "За 1 час"
        120 -> "За 2 часа"
        else -> "За $minutes минут"
    }

    /// «через 30 минут», «через час» — в заголовке уведомления.
    fun inText(minutes: Int): String = when (minutes) {
        60 -> "через час"
        120 -> "через 2 часа"
        else -> "через $minutes минут"
    }

    fun monthsTitle(months: Int): String =
        if (months == 0) "Без срока" else "$months " + RuPlural.word(months, "месяц", "месяца", "месяцев")

    fun timeText(minutes: Int): String = "%d:%02d".format(minutes / 60, minutes % 60)
}

/// Минуты от полуночи, когда тренировка в этот день недели.
fun ProgramProfile.trainingMinutes(weekday: Int): Int =
    trainingTimes[weekday.toString()] ?: (reminderHour * 60 + reminderMinute)

/// Напоминания по порядку, без повторов и не больше трёх. Пусто — «в начале».
val ProgramProfile.activeReminderOffsets: List<Int>
    get() = reminderOffsets.filter { it in TrainingGoal.offsetOptions }.distinct().sorted()
        .ifEmpty { listOf(0) }.take(TrainingGoal.MAX_OFFSETS)

/// Последний день срока, если он задан.
fun ProgramProfile.goalEnd(): LocalDate? {
    val start = goalStartEpochDay ?: return null
    if (goalMonths <= 0) return null
    return LocalDate.ofEpochDay(start).plusMonths(goalMonths.toLong())
}

/// Какая идёт неделя срока: «неделя 3 из 13». Срока нет или он не начался — null.
fun ProgramProfile.goalWeek(today: LocalDate = LocalDate.now()): Pair<Int, Int>? {
    val start = LocalDate.ofEpochDay(goalStartEpochDay ?: return null)
    val end = goalEnd() ?: return null
    if (today.isBefore(start)) return null
    val total = maxOf(1, Math.ceil(ChronoUnit.DAYS.between(start, end) / 7.0).toInt())
    val week = (ChronoUnit.DAYS.between(start, today) / 7 + 1).toInt()
    return minOf(week, total) to total
}
