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
import com.texasprogram.app.data.AppStore
import com.texasprogram.app.data.NutritionStore
import com.texasprogram.app.model.Supplement
import com.texasprogram.app.model.SupplementData
import com.texasprogram.app.model.SupplementTracker
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/// Напоминания о добавках: один будильник на каждое время, в уведомлении все
/// добавки этого времени — «Креатин 5 г · Омега-3».
///
/// Будильник повторяется каждый день, а перед показом смотрит, что выпито:
/// всё уже отмечено — сегодня молчит. Как на iOS — `SupplementReminders.swift`.
object SupplementReminders {
    const val CHANNEL = "supplement-reminder"
    private const val ACTION = "com.texasprogram.app.SUPPLEMENT_REMINDER"
    private const val EXTRA_MINUTES = "minutes"
    private const val PREFS = "strain.supplements"
    private const val KEY = "scheduled"

    /// Одно время напоминания и добавки в нём.
    data class Slot(val hour: Int, val minute: Int, val items: List<Supplement>) {
        val minutes: Int get() = hour * 60 + minute
        val id: String get() = "%02d%02d".format(hour, minute)
    }

    fun slots(data: SupplementData): List<Slot> =
        SupplementTracker.ordered(data.items)
            .filter { it.reminder != null }
            .groupBy { it.reminder!!.hour * 60 + it.reminder.minute }
            .toSortedMap()
            .map { (minutes, items) -> Slot(minutes / 60, minutes % 60, items) }

    fun body(items: List<Supplement>): String =
        items.joinToString(" · ") { item -> item.dose.trim().let { if (it.isEmpty()) item.name else "${item.name} $it" } }

    /// Что из этого времени ещё не выпито сегодня — о том и напоминаем.
    fun due(data: SupplementData, minutes: Int, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): List<Supplement> =
        slots(data).firstOrNull { it.minutes == minutes }?.items.orEmpty()
            .filterNot { SupplementTracker.isTaken(it, today, data, zone) }

    /// Ближайшее срабатывание: сегодня, если время ещё не прошло, иначе завтра.
    fun nextOccurrence(hour: Int, minute: Int, now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        val today = LocalDateTime.of(now.toLocalDate(), LocalTime.of(hour, minute))
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    /// Снимает прошлые будильники (их времена — в настройках) и ставит новые.
    fun reschedule(context: Context, data: SupplementData) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getStringSet(KEY, emptySet()).orEmpty().forEach { old ->
            old.toIntOrNull()?.let { manager.cancel(intent(context, it)) }
        }
        val slots = slots(data)
        for (slot in slots) {
            val at = nextOccurrence(slot.hour, slot.minute)
            runCatching {
                manager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    AlarmManager.INTERVAL_DAY,
                    intent(context, slot.minutes)
                )
            }
        }
        prefs.edit().putStringSet(KEY, slots.map { it.minutes.toString() }.toSet()).apply()
    }

    fun cancel(context: Context) = reschedule(context, SupplementData())

    fun minutesOf(intent: Intent): Int = intent.getIntExtra(EXTRA_MINUTES, -1)

    private fun intent(context: Context, minutes: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            6000 + minutes,
            Intent(context, SupplementReminderReceiver::class.java)
                .setAction(ACTION)
                .putExtra(EXTRA_MINUTES, minutes),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}

/// Срабатывает в назначенное время: напоминает о том, что ещё не выпито.
class SupplementReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val minutes = SupplementReminders.minutesOf(intent)
        if (minutes < 0) return
        val profile = AppStore.get(context).active ?: return
        val data = NutritionStore.read(NutritionStore.directory(context), profile.id)?.supplements ?: return
        val due = SupplementReminders.due(data, minutes)
        if (due.isEmpty()) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(SupplementReminders.CHANNEL, "Напоминание о добавках", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val notification = NotificationCompat.Builder(context, SupplementReminders.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_rest)
            .setContentTitle("Добавки")
            .setContentText(SupplementReminders.body(due))
            .setStyle(NotificationCompat.BigTextStyle().bigText(SupplementReminders.body(due)))
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
        runCatching { NotificationManagerCompat.from(context).notify(4000 + minutes, notification) }
    }
}
