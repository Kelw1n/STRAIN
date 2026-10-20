package com.texasprogram.app.model

import com.texasprogram.app.service.RuDate
import com.texasprogram.app.service.RuPlural
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/// Итоги месяца или года, как «Spotify Wrapped»: сколько тренировок и времени,
/// сколько поднято, рекорды, любимое упражнение, самый тяжёлый подход, серия
/// недель и сравнение с прошлым таким же периодом.
///
/// Считается на телефоне из истории отметок и записанных подходов — все
/// тренировки, а не только выложенные в ленту.
data class TrainingRecap(
    val period: Period,
    val workouts: Int,
    val seconds: Int,
    val volumeKg: Double,
    val sets: Int,
    val reps: Int,
    /// Сколько раз лучший вес движения стал выше всех прошлых.
    val records: Int,
    /// Недель с тренировкой и самая длинная серия таких недель подряд.
    val activeWeeks: Int,
    val longestWeekStreak: Int,
    /// Чаще всего делали.
    val favorite: ExerciseStat?,
    /// Самый тяжёлый подход периода.
    val heaviest: ExerciseStat?,
    /// Больше всего подняли — до трёх упражнений.
    val topByVolume: List<ExerciseStat>,
    /// День недели, в который тренировались чаще всего (1 — воскресенье, как в iOS).
    val busiestWeekday: Int?,
    /// Тренировки по неделям месяца или по месяцам года — для столбиков.
    val buckets: List<Int>,
    val previousWorkouts: Int,
    val previousVolumeKg: Double
) {
    sealed interface Period {
        data class Month(val year: Int, val month: Int) : Period
        data class Year(val year: Int) : Period

        val id: String get() = when (this) {
            is Month -> "%04d-%02d".format(year, month)
            is Year -> "$year"
        }

        val isYear: Boolean get() = this is Year

        /// «Октябрь 2026», «2026 год».
        val title: String get() = when (this) {
            is Month -> "${MONTHS[month].replaceFirstChar { it.uppercase() }} $year"
            is Year -> "$year год"
        }

        /// «октябрь», «2026» — в подписях вроде «Твой октябрь».
        val name: String get() = when (this) {
            is Month -> MONTHS[month]
            is Year -> "$year"
        }

        /// «в октябре», «за 2026 год».
        val inPhrase: String get() = when (this) {
            is Month -> "в " + MONTHS_PREPOSITIONAL[month]
            is Year -> "за $year год"
        }

        /// «в сентябре», «в 2025 году» — с чем сравниваем.
        val previousPhrase: String get() = when (val earlier = previous) {
            is Month -> "в " + MONTHS_PREPOSITIONAL[earlier.month]
            is Year -> "в ${earlier.year} году"
        }

        val previous: Period get() = when (this) {
            is Month -> if (month == 1) Month(year - 1, 12) else Month(year, month - 1)
            is Year -> Year(year - 1)
        }

        val start: LocalDate get() = when (this) {
            is Month -> LocalDate.of(year, month, 1)
            is Year -> LocalDate.of(year, 1, 1)
        }

        /// Первый день после периода.
        val end: LocalDate get() = when (this) {
            is Month -> start.plusMonths(1)
            is Year -> start.plusYears(1)
        }

        fun contains(date: LocalDate): Boolean = !date.isBefore(start) && date.isBefore(end)
    }

    /// Упражнение в итогах: сколько раз делали, сколько подняли и лучший подход.
    data class ExerciseStat(
        val name: String,
        val sessions: Int = 0,
        val volumeKg: Double = 0.0,
        val bestWeight: Double = 0.0,
        val bestReps: Int = 0
    )

    val isEmpty: Boolean get() = workouts == 0
    val hours: Int get() = seconds / 3600
    val minutes: Int get() = (seconds % 3600) / 60

    /// Насколько больше (или меньше) поднято, чем в прошлый такой же период, в процентах.
    val volumeChange: Int?
        get() = if (previousVolumeKg > 0) ((volumeKg - previousVolumeKg) / previousVolumeKg * 100).roundToInt() else null

    /// Поднятое — в понятных предметах: «как 3 легковые машины».
    val volumeComparison: String?
        get() = when {
            volumeKg < 50 -> null
            volumeKg >= 6000 -> (volumeKg / 6000).roundToInt().let { "как $it ${RuPlural.word(it, "слон", "слона", "слонов")}" }
            volumeKg >= 1500 -> (volumeKg / 1500).roundToInt().let {
                "как $it ${RuPlural.word(it, "легковая машина", "легковые машины", "легковых машин")}"
            }
            else -> (volumeKg / 50).roundToInt().let { "как $it ${RuPlural.word(it, "мешок", "мешка", "мешков")} цемента" }
        }

    companion object {
        val MONTHS = listOf("", "январь", "февраль", "март", "апрель", "май", "июнь",
            "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь")
        val MONTHS_PREPOSITIONAL = listOf("", "январе", "феврале", "марте", "апреле", "мае", "июне",
            "июле", "августе", "сентябре", "октябре", "ноябре", "декабре")
        /// «по понедельникам» — индекс как у iOS: 1 — воскресенье.
        val WEEKDAY_DATIVE = listOf("", "воскресеньям", "понедельникам", "вторникам", "средам", "четвергам", "пятницам", "субботам")

        /// Итоги профиля. Подходы берутся прямо из записей: в ключе — день и
        /// упражнение, поэтому прошлые циклы программы тоже попадают в итоги.
        fun build(profile: ProgramProfile, period: Period): TrainingRecap {
            val names = mutableMapOf<String, String>()
            profile.workoutPlan.weeks.forEach { week ->
                week.days.forEach { day -> day.exercises.forEach { names[it.key] = it.name } }
            }
            return build(profile.completionLog, profile.setLog, names, period)
        }

        fun build(
            log: List<CompletionRecord>,
            setLog: Map<String, SetEntry>,
            names: Map<String, String> = emptyMap(),
            period: Period
        ): TrainingRecap {
            val days = setsByDay(setLog)
            val sorted = log.sortedBy { it.epochDay }
            var workouts = 0
            var seconds = 0
            var sets = 0
            var reps = 0
            var records = 0
            var volume = 0.0
            val exercises = mutableMapOf<String, ExerciseStat>()
            val bestSoFar = mutableMapOf<String, Double>()
            val weekdays = mutableMapOf<Int, Int>()
            val weeks = mutableSetOf<LocalDate>()
            val buckets = MutableList(bucketCount(period)) { 0 }

            for (record in sorted) {
                val date = LocalDate.ofEpochDay(record.epochDay)
                val inPeriod = period.contains(date)
                for ((id, entries) in days[record.key].orEmpty()) {
                    val name = names[id] ?: id
                    val top = entries.maxOfOrNull { it.weight } ?: 0.0
                    if (inPeriod) {
                        var stat = exercises[name] ?: ExerciseStat(name)
                        stat = stat.copy(sessions = stat.sessions + 1, volumeKg = stat.volumeKg + entries.sumOf { it.tonnage })
                        for (entry in entries) {
                            if (entry.weight > stat.bestWeight || (entry.weight == stat.bestWeight && entry.reps > stat.bestReps)) {
                                stat = stat.copy(bestWeight = entry.weight, bestReps = entry.reps)
                            }
                        }
                        exercises[name] = stat
                        sets += entries.size
                        reps += entries.sumOf { it.reps }
                        volume += entries.sumOf { it.tonnage }
                    }
                    // Рекорд — лучший вес выше всех прошлых; первый раз рекордом не считаем.
                    if (top <= 0) continue
                    val previous = bestSoFar[name]
                    if (previous != null && top > previous + 1e-9 && inPeriod) records++
                    bestSoFar[name] = maxOf(previous ?: 0.0, top)
                }
                if (!inPeriod) continue
                workouts++
                seconds += record.durationSeconds ?: 0
                val weekday = RuDate.weekdayOf(date)
                weekdays[weekday] = (weekdays[weekday] ?: 0) + 1
                weeks.add(RuDate.mondayOf(date))
                val index = bucketIndex(date, period)
                if (index in buckets.indices) buckets[index]++
            }

            val earlier = sorted.filter { period.previous.contains(LocalDate.ofEpochDay(it.epochDay)) }
            val previousVolume = earlier.sumOf { record -> days[record.key].orEmpty().values.sumOf { entries -> entries.sumOf { it.tonnage } } }
            val stats = exercises.values.toList()
            val busiest = weekdays.entries.maxWithOrNull(compareBy<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key })
            return TrainingRecap(
                period = period,
                workouts = workouts,
                seconds = seconds,
                volumeKg = (volume * 10).roundToInt() / 10.0,
                sets = sets,
                reps = reps,
                records = records,
                activeWeeks = weeks.size,
                longestWeekStreak = longestStreak(weeks),
                favorite = stats.maxWithOrNull(compareBy<ExerciseStat> { it.sessions }.thenBy { it.volumeKg }),
                heaviest = stats.filter { it.bestWeight > 0 }.maxWithOrNull(compareBy<ExerciseStat> { it.bestWeight }.thenBy { it.bestReps }),
                topByVolume = stats.filter { it.volumeKg > 0 }.sortedByDescending { it.volumeKg }.take(3),
                busiestWeekday = busiest?.key,
                buckets = buckets,
                previousWorkouts = earlier.size,
                previousVolumeKg = (previousVolume * 10).roundToInt() / 10.0
            )
        }

        /// Периоды, за которые есть что показать: этот и прошлый месяц, этот и прошлый год.
        fun periods(log: List<CompletionRecord>, today: LocalDate = LocalDate.now()): List<Period> {
            val current = Period.Month(today.year, today.monthValue)
            val candidates = listOf(current, current.previous, Period.Year(today.year), Period.Year(today.year - 1))
            return candidates.filter { period -> log.any { period.contains(LocalDate.ofEpochDay(it.epochDay)) } }
        }

        /// День → упражнение → подходы. Ключ подхода: «день|упражнение|номер».
        fun setsByDay(setLog: Map<String, SetEntry>): Map<String, Map<String, List<SetEntry>>> {
            val result = mutableMapOf<String, MutableMap<String, MutableList<SetEntry>>>()
            for ((key, entry) in setLog) {
                val parts = key.split('|')
                if (parts.size < 3) continue
                result.getOrPut(parts[0]) { mutableMapOf() }.getOrPut(parts[1]) { mutableListOf() }.add(entry)
            }
            return result
        }

        private fun bucketCount(period: Period): Int =
            if (period.isYear) 12 else bucketIndex(period.end.minusDays(1), period) + 1

        /// Номер недели внутри месяца (с понедельника) или номер месяца в году, с нуля.
        private fun bucketIndex(date: LocalDate, period: Period): Int =
            if (period.isYear) date.monthValue - 1
            else (ChronoUnit.WEEKS.between(RuDate.mondayOf(period.start), RuDate.mondayOf(date))).toInt().coerceAtLeast(0)

        private fun longestStreak(weeks: Set<LocalDate>): Int {
            var longest = 0
            for (start in weeks) {
                // Серию считаем только от её первой недели.
                if (start.minusWeeks(1) in weeks) continue
                var length = 0
                var cursor = start
                while (cursor in weeks) {
                    length++
                    cursor = cursor.plusWeeks(1)
                }
                longest = maxOf(longest, length)
            }
            return longest
        }
    }
}
