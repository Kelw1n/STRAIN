package com.texasprogram.app.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.texasprogram.app.MainActivity
import com.texasprogram.app.R

/// Таймер отдыха между подходами.
///
/// Считает не тики, а разницу до сохранённого момента окончания: свёрнутое
/// приложение не теряет секунды. Отсчёт живёт только внутри приложения —
/// постоянного уведомления в шторке нет намеренно, потому что именно оно
/// поднималось оболочками в «остров» и роняло приложение.
/// Сигнал в конце ставится будильником, потому что в фоне код не выполняется.
class RestTimer(private val context: Context) {

    companion object {
        val presets = listOf(180L, 300L, 600L)

        const val CHANNEL_DONE = "rest.done"
        const val DONE_ID = 4202
        const val ACTION_FINISHED = "com.texasprogram.app.REST_FINISHED"
    }

    var endsAtMillis by mutableLongStateOf(0L)
        private set
    var totalSeconds by mutableLongStateOf(0L)
        private set
    var nowMillis by mutableLongStateOf(System.currentTimeMillis())
        private set

    val isRunning: Boolean get() = endsAtMillis > 0

    val remainingSeconds: Long
        get() = if (!isRunning) 0 else ((endsAtMillis - nowMillis).coerceAtLeast(0) + 999) / 1000

    val progress: Float
        get() = if (totalSeconds <= 0) 0f
        else (1f - remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)

    val remainingText: String
        get() = "%d:%02d".format(remainingSeconds / 60, remainingSeconds % 60)

    fun start(seconds: Long) {
        totalSeconds = seconds
        endsAtMillis = System.currentTimeMillis() + seconds * 1000
        nowMillis = System.currentTimeMillis()
        ensureChannel()
        scheduleAlarm()
    }

    fun add(extraSeconds: Long) {
        if (!isRunning) return
        endsAtMillis += extraSeconds * 1000
        totalSeconds += extraSeconds
        scheduleAlarm()
    }

    fun stop() {
        endsAtMillis = 0
        totalSeconds = 0
        cancelAlarm()
    }

    /// Дёргается раз в секунду из интерфейса и при возврате из фона.
    fun tick() {
        nowMillis = System.currentTimeMillis()
        if (isRunning && nowMillis >= endsAtMillis) stop()
    }

    private fun ensureChannel() {
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, "Отдых закончен", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    // MARK: - Будильник на окончание

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        1,
        Intent(context, RestFinishedReceiver::class.java).setAction(ACTION_FINISHED),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun scheduleAlarm() {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val trigger = SystemClock.elapsedRealtime() + (endsAtMillis - System.currentTimeMillis())
        // setExactAndAllowWhileIdle не требует особого разрешения для короткого таймера
        // и переживает режим сна, в отличие от обычного set().
        manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, alarmIntent())
    }

    private fun cancelAlarm() {
        context.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent())
    }
}

/// Срабатывает в момент окончания отдыха, даже если приложение свёрнуто.
class RestFinishedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(RestTimer.CHANNEL_DONE, "Отдых закончен", NotificationManager.IMPORTANCE_HIGH)
        )
        val notification = NotificationCompat.Builder(context, RestTimer.CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_rest)
            .setContentTitle("Отдых закончен")
            .setContentText("Пора к следующему подходу.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()
        try {
            NotificationManagerCompat.from(context).notify(RestTimer.DONE_ID, notification)
        } catch (_: SecurityException) {
        }
    }
}
