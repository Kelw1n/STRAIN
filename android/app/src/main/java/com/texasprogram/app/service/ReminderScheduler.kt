package com.texasprogram.app.service

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
    private const val WEEK_MILLIS = AlarmManager.INTERVAL_DAY * 7

    /// Пересобирает расписание под текущие настройки профиля.
    ///
    /// Вызывается и при запуске приложения: будильники не переживают
    /// перезагрузку телефона, а запуск случается всё равно.
    fun reschedule(context: Context, profile: ProgramProfile) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        // Сначала снимаем все: поменялось время или дни — старые висели бы дальше.
        for (weekday in 1..7) manager.cancel(intent(context, weekday, 0))

        if (!profile.reminderEnabled) return

        profile.weekdays.forEachIndexed { index, weekday ->
            val at = nextOccurrence(weekday, profile.reminderHour, profile.reminderMinute)
            runCatching {
                manager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    WEEK_MILLIS,
                    intent(context, weekday, index + 1)
                )
            }
        }
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        for (weekday in 1..7) manager.cancel(intent(context, weekday, 0))
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

    private fun intent(context: Context, weekday: Int, day: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            2000 + weekday,
            Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION)
                .putExtra(EXTRA_DAY, day),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun dayOf(intent: Intent): Int = intent.getIntExtra(EXTRA_DAY, 0)
}

/// Срабатывает в назначенное время и показывает напоминание.
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                ReminderScheduler.CHANNEL,
                "Напоминание о тренировке",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )

        val day = ReminderScheduler.dayOf(intent)
        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_rest)
            .setContentTitle("Сегодня тренировка")
            // Ресивер не знает режима расписания — берём общий текст, если дня нет.
            .setContentText(ReminderScheduler.body(day, ScheduleMode.WEEKDAY))
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

        runCatching { NotificationManagerCompat.from(context).notify(3000 + day, notification) }
    }
}
