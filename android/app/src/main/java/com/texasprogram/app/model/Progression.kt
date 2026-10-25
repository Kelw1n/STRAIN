package com.texasprogram.app.model

import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

/// Опыт за то, что человек и так делает, и уровни 1–50 со званиями.
///
/// Считается заново по всей истории — по завершённым тренировкам и записанным
/// подходам всех циклов, поэтому старые тренировки тоже приносят опыт, а
/// уровень после переноса копии на другой телефон тот же. Правила совпадают
/// с iOS до единицы: одна и та же история — один и тот же уровень.
object Progression {
    const val WORKOUT_XP = 100
    const val SET_XP = 5
    const val RECORD_XP = 50
    const val WEEK_GOAL_XP = 150
    /// Серия недель с тренировкой: каждая следующая неделя +10% к опыту недели, до +50%.
    const val MAX_STREAK_STEPS = 5
    const val MAX_LEVEL = 50

    data class Level(
        val level: Int,
        val xp: Int,
        /// Опыт, с которого начинается этот уровень.
        val floor: Int,
        /// С которого начнётся следующий; на пятидесятом — нет.
        val next: Int?
    ) {
        val title: String get() = title(level)

        /// Доля пути до следующего уровня, 0…1.
        val fraction: Double
            get() {
                val next = next ?: return 1.0
                if (next <= floor) return 1.0
                return ((xp - floor).toDouble() / (next - floor)).coerceIn(0.0, 1.0)
            }

        val toNext: Int? get() = next?.let { max(0, it - xp) }
    }

    /// Опыт, с которого начинается уровень: 60 × (уровень − 1)².
    /// Пятый — после пары недель, двадцатый — месяцев через пять, пятидесятый — через годы.
    fun threshold(level: Int): Int {
        val step = max(0, min(level, MAX_LEVEL) - 1)
        return 60 * step * step
    }

    fun level(xp: Int): Level {
        var level = 1
        while (level < MAX_LEVEL && xp >= threshold(level + 1)) level += 1
        return Level(level, xp, threshold(level), if (level < MAX_LEVEL) threshold(level + 1) else null)
    }

    fun title(level: Int): String = when {
        level < 5 -> "Новичок"
        level < 10 -> "Любитель"
        level < 15 -> "Атлет"
        level < 20 -> "Силач"
        level < 25 -> "Ветеран зала"
        level < 30 -> "Мастер"
        level < 35 -> "Железный"
        level < 40 -> "Титан"
        level < 45 -> "Чемпион"
        level < 50 -> "Икона зала"
        else -> "Легенда"
    }

    /// Цель недели для бонуса: тренировок в неделе программы, но не больше трёх —
    /// иначе смена программы на четырёхдневную отнимала бы опыт за прошлые недели.
    fun weeklyGoal(profile: ProgramProfile): Int = min(3, max(1, profile.trainingDayCount))

    fun level(profile: ProgramProfile): Level = level(xp(profile))

    fun xp(profile: ProgramProfile, excluding: String? = null): Int {
        val log = if (excluding == null) profile.completionLog else profile.completionLog.filter { it.key != excluding }
        return xp(log, profile.setLog, weeklyGoal(profile))
    }

    private data class Workout(val key: String, val day: LocalDate, val week: LocalDate)

    /// Повтор ключа — та же тренировка: берём последнюю дату. Порядок — по дню,
    /// при равенстве — по ключу, чтобы рекорды считались одинаково на любом телефоне.
    private fun orderedWorkouts(log: List<CompletionRecord>): List<Workout> {
        val latest = HashMap<String, Long>()
        for (record in log) latest[record.key] = max(latest[record.key] ?: record.epochDay, record.epochDay)
        return latest.map { (key, epochDay) ->
            val day = LocalDate.ofEpochDay(epochDay)
            Workout(key, day, day.minusDays(day.dayOfWeek.value - 1L))
        }.sortedWith(compareBy<Workout>({ it.day }, { it.key }))
    }

    /// Опыт по истории.
    fun xp(log: List<CompletionRecord>, sets: Map<String, SetEntry>, weeklyGoal: Int): Int {
        val workouts = orderedWorkouts(log)
        if (workouts.isEmpty()) return 0

        // Подходы по дню тренировки: ключ подхода — «день|упражнение|номер».
        val byDay = HashMap<String, MutableList<Pair<String, SetEntry>>>()
        for ((key, entry) in sets) {
            if (entry.reps <= 0) continue
            val parts = key.split("|")
            if (parts.size < 3) continue
            byDay.getOrPut(parts[0]) { mutableListOf() }.add(parts[1] to entry)
        }

        val top = HashMap<String, Double>()
        val weekXP = HashMap<LocalDate, Int>()
        val weekCount = HashMap<LocalDate, Int>()
        for (workout in workouts) {
            val daySets = byDay[workout.key].orEmpty()
            val best = HashMap<String, Double>()
            for ((exercise, entry) in daySets) {
                if (entry.weight > 0) best[exercise] = max(best[exercise] ?: 0.0, entry.weight)
            }
            var records = 0
            for (exercise in best.keys.sorted()) {
                val weight = best.getValue(exercise)
                val current = top[exercise]
                if (current == null) {
                    top[exercise] = weight
                } else if (weight > current + 1e-9) {
                    records += 1
                    top[exercise] = weight
                }
            }
            val gained = WORKOUT_XP + SET_XP * daySets.size + RECORD_XP * records
            weekXP[workout.week] = (weekXP[workout.week] ?: 0) + gained
            weekCount[workout.week] = (weekCount[workout.week] ?: 0) + 1
        }

        val goal = min(3, max(1, weeklyGoal))
        var total = 0
        var run = 0
        var previous: LocalDate? = null
        for (week in weekXP.keys.sorted()) {
            run = if (previous != null && previous.plusWeeks(1) == week) run + 1 else 1
            previous = week
            var points = weekXP.getValue(week)
            if ((weekCount[week] ?: 0) >= goal) points += WEEK_GOAL_XP
            // Целочисленно: одинаковое округление на Android и iPhone.
            total += points * (10 + min(run - 1, MAX_STREAK_STEPS)) / 10
        }
        return total
    }

    // MARK: Награды месяца

    enum class MonthTier(val title: String, val workouts: Int) {
        BRONZE("Бронза", 8),
        SILVER("Серебро", 12),
        GOLD("Золото", 16)
    }

    /// Награда за месяц: получить её можно только в этом месяце, потом она остаётся.
    data class MonthAward(val year: Int, val month: Int, val workouts: Int, val tier: MonthTier) {
        val id: String get() = "$year-$month"
        val title: String get() = "${monthNames[month]} $year"
    }

    val monthNames = listOf(
        "", "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
    )

    fun tier(workouts: Int): MonthTier? = when {
        workouts >= MonthTier.GOLD.workouts -> MonthTier.GOLD
        workouts >= MonthTier.SILVER.workouts -> MonthTier.SILVER
        workouts >= MonthTier.BRONZE.workouts -> MonthTier.BRONZE
        else -> null
    }

    /// Награды по месяцам — новые первыми.
    fun monthAwards(log: List<CompletionRecord>): List<MonthAward> =
        orderedWorkouts(log)
            .groupBy { it.day.year to it.day.monthValue }
            .mapNotNull { (month, items) -> tier(items.size)?.let { MonthAward(month.first, month.second, items.size, it) } }
            .sortedWith(compareByDescending<MonthAward> { it.year }.thenByDescending { it.month })

    /// Этот месяц: сколько уже есть и до какой награды осталось.
    fun currentMonth(log: List<CompletionRecord>, today: LocalDate = LocalDate.now()): Pair<Int, MonthTier?> {
        val count = orderedWorkouts(log).count { it.day.year == today.year && it.day.monthValue == today.monthValue }
        return count to MonthTier.entries.firstOrNull { count < it.workouts }
    }
}
