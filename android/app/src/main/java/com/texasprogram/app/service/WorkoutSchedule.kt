package com.texasprogram.app.service

import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.ScheduleMode
import com.texasprogram.app.model.WorkoutDayPlan
import com.texasprogram.app.model.WorkoutPlan
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/// Русские названия без DateFormat: набор строк фиксированный, форматтер тут лишний.
object RuDate {
    val weekdayFull = listOf("", "воскресенье", "понедельник", "вторник", "среда", "четверг", "пятница", "суббота")
    val weekdayShort = listOf("", "вс", "пн", "вт", "ср", "чт", "пт", "сб")
    val monthGenitive = listOf(
        "", "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря"
    )

    /// Номера дней недели в порядке «понедельник → воскресенье» (1 — воскресенье).
    val weekOrder = listOf(2, 3, 4, 5, 6, 7, 1)

    fun full(weekday: Int): String = weekdayFull.getOrElse(weekday) { "" }

    fun short(weekday: Int): String = weekdayShort.getOrElse(weekday) { "" }

    fun dayMonth(date: LocalDate): String = "${date.dayOfMonth} ${monthGenitive.getOrElse(date.monthValue) { "" }}"

    /// Нумерация как в Calendar на iOS: 1 — воскресенье, 2 — понедельник.
    fun weekdayOf(date: LocalDate): Int = (date.dayOfWeek.value % 7) + 1

    fun mondayOf(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

    /// Смещение дня недели от понедельника.
    fun offsetFromMonday(weekday: Int): Long = ((weekday - 2 + 7) % 7).toLong()
}

/// Тренировка плана, привязанная к календарной дате.
data class ScheduledWorkout(
    val week: Int,
    val day: WorkoutDayPlan,
    val date: LocalDate,
    val weekday: Int,
    val isCompleted: Boolean,
    /// Номер тренировки волны жима, выданный этому дню. Волна идёт своим порядком,
    /// не привязанным к дню недели, поэтому номер приходит из расписания.
    val benchSession: Int? = null
) {
    val id: String get() = "$week-${day.number}"
    val weekdayName: String get() = RuDate.full(weekday)
    val shortWeekday: String get() = RuDate.short(weekday)

    /// Заголовок по реальной дате: в режиме очереди день недели берётся отсюда,
    /// а не из номера дня в программе.
    val fullTitle: String get() = weekdayName.uppercase() + " · " + day.title
}

data class WorkoutSchedule(
    val today: ScheduledWorkout?,
    val upcoming: ScheduledWorkout?,
    val overdue: List<ScheduledWorkout>,
    val todayAlreadyDone: Boolean,
    /// Все невыполненные тренировки с назначенными датами — для экрана «План».
    val allPending: List<ScheduledWorkout>,
    val referenceDate: LocalDate
) {
    val focus: ScheduledWorkout? get() = today ?: upcoming
    val isRestDay: Boolean get() = today == null && !todayAlreadyDone

    fun relativeTitle(workout: ScheduledWorkout): String {
        val days = ChronoUnit.DAYS.between(referenceDate, workout.date)
        return when {
            days < 0 -> "Пропущено"
            days == 0L -> "Сегодня"
            days == 1L -> "Завтра"
            days == 2L -> "Послезавтра"
            else -> "Через $days дн."
        }
    }
}

object WorkoutScheduler {

    fun build(profile: ProgramProfile, plan: WorkoutPlan, today: LocalDate = LocalDate.now()): WorkoutSchedule =
        when (profile.scheduleMode) {
            ScheduleMode.QUEUE -> buildQueue(profile, plan, today)
            ScheduleMode.WEEKDAY -> buildByWeekday(profile, plan, today)
        }

    // MARK: - Очередь

    /// Невыполненные тренировки раздаются подряд по ближайшим тренировочным дням.
    ///
    /// День плана здесь не привязан к своему дню недели: пропустил четверг — тренировка
    /// не ждёт следующего четверга, а становится первой в очереди. Ничего не теряется
    /// и не копится в «пропущено», сдвигается только календарь.
    private fun buildQueue(profile: ProgramProfile, plan: WorkoutPlan, today: LocalDate): WorkoutSchedule {
        val weekdays = profile.weekdays.toSet()

        val pending = ArrayList<Pair<Int, com.texasprogram.app.model.WorkoutDayPlan>>()
        for (week in plan.weeks) {
            for (day in week.days) {
                if (!profile.isCompleted(week.number, day.number)) pending.add(week.number to day)
            }
        }

        val trainedToday = profile.lastCompletionDate() == today
        val todayIsTrainingDay = weekdays.contains(RuDate.weekdayOf(today))

        val entries = ArrayList<ScheduledWorkout>(pending.size)
        if (weekdays.isNotEmpty()) {
            // Если сегодня уже отметились, очередь начинается с завтра.
            var cursor = if (trainedToday) today.plusDays(1) else today
            var index = 0
            var guard = 0
            val limit = pending.size * 7 + 14
            while (index < pending.size && guard < limit) {
                guard++
                val weekday = RuDate.weekdayOf(cursor)
                if (weekdays.contains(weekday)) {
                    val (week, day) = pending[index]
                    entries.add(ScheduledWorkout(week, day, cursor, weekday, isCompleted = false))
                    index++
                }
                cursor = cursor.plusDays(1)
            }
        }

        assignBenchSessions(entries, profile)

        return WorkoutSchedule(
            today = entries.firstOrNull { it.date == today },
            upcoming = entries.firstOrNull { it.date > today },
            overdue = emptyList(),
            todayAlreadyDone = trainedToday && todayIsTrainingDay,
            allPending = entries,
            referenceDate = today
        )
    }

    /// Ближайший тренировочный день, на который встанет первая тренировка очереди.
    fun nextTrainingSlot(profile: ProgramProfile, today: LocalDate = LocalDate.now()): LocalDate {
        val weekdays = profile.weekdays.toSet()
        if (weekdays.isEmpty()) return today
        val trainedToday = profile.lastCompletionDate() == today
        var cursor = if (trainedToday) today.plusDays(1) else today
        repeat(14) {
            if (weekdays.contains(RuDate.weekdayOf(cursor))) return cursor
            cursor = cursor.plusDays(1)
        }
        return cursor
    }

    // MARK: - По дням недели

    /// Раскладывает план по календарю.
    ///
    /// У каждого дня недели свой счётчик.
    ///
    /// Понедельник всегда отдаёт понедельничную тренировку — самую раннюю невыполненную.
    /// Сделал понедельники первой и второй недели — в следующий понедельник будет третья.
    /// Пропущенный четверг не теряется и не переезжает на понедельник: он достанется
    /// ближайшему четвергу.
    private fun buildByWeekday(profile: ProgramProfile, plan: WorkoutPlan, today: LocalDate): WorkoutSchedule {
        val todayWeekday = RuDate.weekdayOf(today)
        val trainedToday = profile.lastCompletionDate() == today

        // Номера тренировочных дней программы: 1..3 для «Техаса», 1..4 для «Верх / Низ».
        val dayNumbers = plan.weeks.firstOrNull()?.days?.map { it.number } ?: emptyList()

        val entries = ArrayList<ScheduledWorkout>(plan.weeks.size * dayNumbers.size)

        for (dayNumber in dayNumbers) {
            val weekday = profile.weekday(dayNumber)
            var cursor = nextOccurrence(weekday, today)
            // Сегодня уже отметились — этот слот занят, следующий такой день через неделю.
            if (trainedToday && weekday == todayWeekday) cursor = cursor.plusWeeks(1)

            for (week in plan.weeks) {
                val day = week.days.firstOrNull { it.number == dayNumber } ?: continue
                if (profile.isCompleted(week.number, dayNumber)) continue
                entries.add(ScheduledWorkout(week.number, day, cursor, weekday, isCompleted = false))
                cursor = cursor.plusWeeks(1)
            }
        }

        entries.sortBy { it.date }
        assignBenchSessions(entries, profile)

        return WorkoutSchedule(
            today = entries.firstOrNull { it.date == today },
            upcoming = entries.firstOrNull { it.date > today },
            overdue = emptyList(),
            todayAlreadyDone = trainedToday && dayNumbers.any { profile.weekday(it) == todayWeekday },
            allPending = entries,
            referenceDate = today
        )
    }

    /// Раздаёт тренировки волны жима по порядку тем дням, которые её несут.
    ///
    /// Волна не смотрит на день недели: следующая невыполненная достаётся ближайшему
    /// верхнему дню, каким бы он ни был. Так в понедельник получаются понедельничные
    /// вспомогательные упражнения и при этом правильный по счёту жим.
    private fun assignBenchSessions(entries: MutableList<ScheduledWorkout>, profile: ProgramProfile) {
        var next = profile.nextBenchSession ?: return
        val total = UpperLowerCalculator.benchSessionCount
        for (index in entries.indices) {
            if (!profile.carriesBenchWave(entries[index].day.number)) continue
            if (next > total) return
            entries[index] = entries[index].copy(benchSession = next)
            next++
        }
    }

    /// Ближайшая дата с нужным днём недели, начиная с указанной.
    fun nextOccurrence(weekday: Int, from: LocalDate): LocalDate {
        val delta = ((weekday - RuDate.weekdayOf(from)) + 7) % 7
        return from.plusDays(delta.toLong())
    }
}
