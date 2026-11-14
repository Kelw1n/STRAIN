package com.texasprogram.app.service

import com.texasprogram.app.model.activeReminderOffsets
import com.texasprogram.app.model.trainingMinutes
import com.texasprogram.app.model.TrainingGoal
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.texasprogram.app.MainActivity
import com.texasprogram.app.R
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.ScheduleMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/// Напоминания о тренировке в дни расписания.
///
/// По одному повторяющемуся будильнику на каждый тренировочный день недели.
/// Точность тут не нужна: напоминание, опоздавшее на пару минут, всё ещё
/// напоминание — поэтому обходимся без разрешения на точные будильники,
/// из-за которого приложение уже один раз падало.
object ReminderScheduler {

    const val CHANNEL = "workout-reminder"
    private const val ACTION = "com.texasprogram.app.REMINDER"
    private const val EXTRA_DAY = "day"
    private const val EXTRA_OFFSET = "offset"
    private const val WEEK_MILLIS = AlarmManager.INTERVAL_DAY * 7

    /// Пересобирает расписание под текущие настройки профиля.
    ///
    /// Вызывается и при запуске приложения: будильники не переживают
    /// перезагрузку телефона, а запуск случается всё равно.
    fun reschedule(context: Context, profile: ProgramProfile) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        // Сначала снимаем все: поменялось время, дни или напоминания — старые висели бы дальше.
        cancelAll(context, manager)

        if (!profile.reminderEnabled) return

        for ((weekday, day, offset, minutes) in plan(profile)) {
            val at = nextOccurrence(weekday, minutes / 60, minutes % 60)
            runCatching {
                manager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    WEEK_MILLIS,
                    intent(context, weekday, day, offset)
                )
            }
        }
    }

    /// Одно напоминание: день недели, номер дня программы (0 — в очереди),
    /// за сколько минут до тренировки и во сколько.
    data class Planned(val weekday: Int, val day: Int, val offset: Int, val minutes: Int)

    /// Что поставим: у каждого дня своё время, у тренировки — до трёх напоминаний.
    /// Напоминание, которое выпало бы на вчера, не ставим. Как на iOS.
    fun plan(profile: ProgramProfile): List<Planned> {
        if (!profile.reminderEnabled) return emptyList()
        return profile.weekdays.flatMapIndexed { index, weekday ->
            // В очереди тип дня заранее неизвестен: номер 0 даёт общий текст.
            val day = if (profile.scheduleMode == ScheduleMode.WEEKDAY) index + 1 else 0
            val training = profile.trainingMinutes(weekday)
            profile.activeReminderOffsets.mapNotNull { offset ->
                val minutes = training - offset
                if (minutes < 0) null else Planned(weekday, day, offset, minutes)
            }
        }
    }

    private fun cancelAll(context: Context, manager: AlarmManager) {
        for (weekday in 1..7) {
            for (offset in TrainingGoal.offsetOptions) manager.cancel(intent(context, weekday, 0, offset))
        }
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        cancelAll(context, manager)
    }

    /// Ближайшее срабатывание: сегодня, если время ещё не прошло, иначе через неделю.
    ///
    /// Нумерация дней как в iOS-версии: 1 — воскресенье, 2 — понедельник.
    fun nextOccurrence(
        weekday: Int,
        hour: Int,
        minute: Int,
        now: LocalDateTime = LocalDateTime.now()
    ): LocalDateTime {
        val target = if (weekday == 1) 7 else weekday - 1
        val time = LocalTime.of(hour, minute)
        var shift = (target - now.toLocalDate().dayOfWeek.value + 7) % 7
        if (shift == 0 && now.toLocalTime() >= time) shift = 7
        val date: LocalDate = now.toLocalDate().plusDays(shift.toLong())
        return LocalDateTime.of(date, time)
    }

    /// В режиме очереди тип дня заранее неизвестен — он зависит от того,
    /// что осталось невыполненным, поэтому обещать «день 2» нельзя.
    fun body(day: Int, mode: ScheduleMode): String =
        if (mode == ScheduleMode.WEEKDAY && day > 0) "По расписанию день $day. Открой приложение и посмотри веса."
        else "Загляни в приложение — план ждёт."

    /// Напоминание «в начале» — прежний код запроса, остальные — свои: снять
    /// и переставить можно каждое отдельно.
    private fun intent(context: Context, weekday: Int, day: Int, offset: Int = 0): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            if (offset == 0) 2000 + weekday else 2100 + weekday * 10 + TrainingGoal.offsetOptions.indexOf(offset),
            Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION)
                .putExtra(EXTRA_DAY, day)
                .putExtra(EXTRA_OFFSET, offset),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun dayOf(intent: Intent): Int = intent.getIntExtra(EXTRA_DAY, 0)
    fun offsetOf(intent: Intent): Int = intent.getIntExtra(EXTRA_OFFSET, 0)

    fun title(offset: Int): String = if (offset == 0) "Сегодня тренировка" else "Тренировка ${TrainingGoal.inText(offset)}"

    /// Утром уже потренировался — вечернее «Сегодня тренировка» ни к чему.
    fun trainedOn(profile: ProgramProfile, date: LocalDate = LocalDate.now()): Boolean =
        profile.completionLog.any { it.epochDay == date.toEpochDay() }
}

/// После перезагрузки телефона и обновления приложения будильники пропадают.
/// Раньше их ставили заново только при запуске STRAIN: перезагрузил телефон
/// и не открывал приложение — напоминаний нет.
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val profile = com.texasprogram.app.data.AppStore.get(context).active ?: return
        ReminderScheduler.reschedule(context, profile)
        val nutrition = com.texasprogram.app.data.NutritionStore
        nutrition.read(nutrition.directory(context), profile.id)?.supplements?.let {
            SupplementReminders.reschedule(context, it)
        }
    }
}

/// Срабатывает в назначенное время и показывает напоминание.
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Профилей не осталось — напоминать не о чем: раньше будильник удалённого
        // профиля звенел и дальше.
        val profile = com.texasprogram.app.data.AppStore.get(context).active ?: return
        if (ReminderScheduler.trainedOn(profile)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                ReminderScheduler.CHANNEL,
                L("Напоминание о тренировке"),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )

        val day = ReminderScheduler.dayOf(intent)
        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_rest)
            .setContentTitle(L(ReminderScheduler.title(ReminderScheduler.offsetOf(intent))))
            // Ресивер не знает режима расписания — берём общий текст, если дня нет.
            .setContentText(L(ReminderScheduler.body(day, ScheduleMode.WEEKDAY)))
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(3000 + day * 10 + ReminderScheduler.offsetOf(intent) / 15, notification) }
    }
}
