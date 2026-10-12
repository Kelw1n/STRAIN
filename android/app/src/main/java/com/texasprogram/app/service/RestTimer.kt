package com.texasprogram.app.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.texasprogram.app.MainActivity
import com.texasprogram.app.R

/// Таймер отдыха между подходами.
///
/// Один на процесс: его видят и экран, и кнопки уведомления. Считает не тики,
/// а разницу до сохранённого момента окончания — свёрнутое приложение
/// не теряет секунды, а выгруженное поднимает отдых из настроек.
/// Сигнал в конце ставится будильником, потому что в фоне код не выполняется.
///
/// Отсчёт в шторке — обычное уведомление с системным обратным секундомером,
/// как «остров» отдыха в iOS-версии. Без фоновой службы и без своей разметки:
/// именно такие уведомления оболочки поднимали в «остров» и роняли приложение.
/// Каждый вызов системы обёрнут — ошибка уведомления не трогает таймер.
class RestTimer private constructor(context: Context) {

    companion object {
        val presets = listOf(180L, 300L, 600L)

        const val CHANNEL_DONE = "rest.done"
        const val CHANNEL_LIVE = "rest.live"
        const val DONE_ID = 4202
        const val LIVE_ID = 4203
        const val ACTION_FINISHED = "com.texasprogram.app.REST_FINISHED"
        const val ACTION_MINUS = "com.texasprogram.app.REST_MINUS"
        const val ACTION_PLUS = "com.texasprogram.app.REST_PLUS"
        const val ACTION_SKIP = "com.texasprogram.app.REST_SKIP"

        @Volatile
        private var instance: RestTimer? = null

        fun get(context: Context): RestTimer =
            instance ?: synchronized(this) {
                instance ?: RestTimer(context.applicationContext).also { instance = it }
            }
    }

    private val context: Context = context.applicationContext
    private val prefs = this.context.getSharedPreferences("strain.rest", Context.MODE_PRIVATE)

    var endsAtMillis by mutableLongStateOf(0L)
        private set
    var totalSeconds by mutableLongStateOf(0L)
        private set
    var nowMillis by mutableLongStateOf(System.currentTimeMillis())
        private set
    var startedAtMillis by mutableLongStateOf(0L)
        private set
    var exerciseName by mutableStateOf<String?>(null)
        private set
    var setDetails by mutableStateOf<String?>(null)
        private set
    var completedSets by mutableIntStateOf(0)
        private set
    var totalSets by mutableIntStateOf(0)
        private set

    /// Показывать ли отсчёт в шторке. Выключается в настройках.
    var liveNotification by mutableStateOf(prefs.getBoolean("live", true))
        private set

    private var toneGenerator: android.media.ToneGenerator? = null
    private var lastBeepSecond: Long = -1L

    init {
        restore()
    }

    val isRunning: Boolean get() = endsAtMillis > 0

    val remainingSeconds: Long
        get() = if (!isRunning) 0 else ((endsAtMillis - nowMillis).coerceAtLeast(0) + 999) / 1000

    val elapsedSeconds: Long
        get() = if (!isRunning || startedAtMillis <= 0L) 0 else ((nowMillis - startedAtMillis) / 1000).coerceAtLeast(0)

    val elapsedText: String
        get() = "%d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)

    val progress: Float
        get() = if (totalSeconds <= 0) 0f
        else (1f - remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)

    val remainingText: String
        get() = "%d:%02d".format(remainingSeconds / 60, remainingSeconds % 60)

    fun start(
        seconds: Long,
        exerciseName: String? = null,
        setDetails: String? = null,
        completedSets: Int = 0,
        totalSets: Int = 0
    ) {
        totalSeconds = seconds
        val now = System.currentTimeMillis()
        endsAtMillis = now + seconds * 1000
        nowMillis = now
        startedAtMillis = now
        this.exerciseName = exerciseName
        this.setDetails = setDetails
        this.completedSets = completedSets
        this.totalSets = totalSets
        lastBeepSecond = -1L
        ensureChannels()
        scheduleAlarm()
        persist()
        showLive()
    }

    fun add(extraSeconds: Long) {
        if (!isRunning) return
        nowMillis = System.currentTimeMillis()
        val newRemaining = maxOf(0L, remainingSeconds + extraSeconds)
        if (newRemaining <= 0) {
            stop()
            return
        }
        val now = System.currentTimeMillis()
        endsAtMillis = now + (newRemaining * 1000)
        totalSeconds = maxOf(newRemaining, totalSeconds + extraSeconds)
        nowMillis = now
        lastBeepSecond = -1L
        scheduleAlarm()
        persist()
        showLive()
    }

    fun stop() {
        endsAtMillis = 0
        totalSeconds = 0
        startedAtMillis = 0
        exerciseName = null
        setDetails = null
        completedSets = 0
        totalSets = 0
        lastBeepSecond = -1L
        cancelAlarm()
        persist()
        cancelLive()
    }

    fun updateLiveNotification(enabled: Boolean) {
        liveNotification = enabled
        runCatching { prefs.edit().putBoolean("live", enabled).apply() }
        if (enabled) showLive() else cancelLive()
    }

    /// Дёргается раз в секунду из интерфейса и при возврате из фона.
    fun tick() {
        nowMillis = System.currentTimeMillis()
        if (isRunning) {
            val rem = remainingSeconds
            if (rem in 1..3 && rem != lastBeepSecond) {
                lastBeepSecond = rem
                playCountdownBeep()
                vibrate(45)
            }
            if (nowMillis >= endsAtMillis) {
                playFinishTone()
                vibrate(300)
                stop()
            }
        }
    }

    /// Будильник сработал, пока приложение было в фоне: звук уже дал
    /// системный сигнал, здесь только убираем отсчёт.
    fun finishedInBackground() {
        if (isRunning && System.currentTimeMillis() + 1500 >= endsAtMillis) stop()
    }

    // MARK: - Сохранение

    private fun persist() {
        runCatching {
            prefs.edit()
                .putLong("endsAt", endsAtMillis)
                .putLong("total", totalSeconds)
                .putLong("startedAt", startedAtMillis)
                .putString("exercise", exerciseName)
                .putString("details", setDetails)
                .putInt("done", completedSets)
                .putInt("sets", totalSets)
                .apply()
        }
    }

    private fun restore() {
        val ends = prefs.getLong("endsAt", 0L)
        if (ends <= System.currentTimeMillis()) {
            if (ends > 0) runCatching { prefs.edit().putLong("endsAt", 0L).apply() }
            return
        }
        endsAtMillis = ends
        totalSeconds = prefs.getLong("total", 0L)
        startedAtMillis = prefs.getLong("startedAt", 0L)
        exerciseName = prefs.getString("exercise", null)
        setDetails = prefs.getString("details", null)
        completedSets = prefs.getInt("done", 0)
        totalSets = prefs.getInt("sets", 0)
        nowMillis = System.currentTimeMillis()
    }

    // MARK: - Отсчёт в шторке

    private fun actionIntent(action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        code,
        Intent(context, RestActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun showLive() {
        if (!liveNotification || !isRunning) return
        runCatching {
            ensureChannels()
            val name = exerciseName?.takeIf { it.isNotBlank() } ?: "Отдых между подходами"
            val line = buildString {
                if (totalSets > 0) append("Подход ${(completedSets + 1).coerceAtMost(totalSets)} из $totalSets")
                setDetails?.takeIf { it.isNotBlank() }?.let {
                    if (isNotEmpty()) append(" · ")
                    append("было $it")
                }
            }
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val builder = NotificationCompat.Builder(context, CHANNEL_LIVE)
                .setSmallIcon(R.drawable.ic_stat_rest)
                .setColor(0xFFFF6029.toInt())
                .setContentTitle(name)
                .setContentText(line.ifEmpty { "Отдых" })
                .setSubText("Отдых")
                .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(true)
                .setWhen(endsAtMillis)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                // Не висит после конца, даже если будильник опоздал.
                .setTimeoutAfter((endsAtMillis - System.currentTimeMillis()).coerceAtLeast(1000L) + 3000L)
                .setContentIntent(open)
                .addAction(0, "−30 с", actionIntent(ACTION_MINUS, 11))
                .addAction(0, "+30 с", actionIntent(ACTION_PLUS, 12))
                .addAction(0, "Пропустить", actionIntent(ACTION_SKIP, 13))
            exerciseName?.let { ExerciseArt.cached(it) }?.let { builder.setLargeIcon(it) }
            val notification: Notification = builder.build()
            NotificationManagerCompat.from(context).notify(LIVE_ID, notification)
        }
    }

    private fun cancelLive() {
        runCatching { NotificationManagerCompat.from(context).cancel(LIVE_ID) }
    }

    private fun playCountdownBeep() {
        try {
            if (toneGenerator == null) {
                toneGenerator = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 80)
            }
            toneGenerator?.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 100)
        } catch (_: Exception) {}
    }

    private fun playFinishTone() {
        try {
            if (toneGenerator == null) {
                toneGenerator = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 90)
            }
            toneGenerator?.startTone(android.media.ToneGenerator.TONE_PROP_PROMPT, 350)
        } catch (_: Exception) {}
    }

    private fun vibrate(durationMillis: Long) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            }
            vibrator?.vibrate(android.os.VibrationEffect.createOneShot(durationMillis, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }

    private fun ensureChannels() {
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_DONE, "Отдых закончен", NotificationManager.IMPORTANCE_HIGH)
            )
            // Тихий канал: отсчёт не звенит и не всплывает поверх экрана.
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_LIVE, "Отсчёт отдыха", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                }
            )
        }
    }

    // MARK: - Будильник на окончание

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        1,
        Intent(context, RestFinishedReceiver::class.java).setAction(ACTION_FINISHED),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /// С Android 12 точный будильник требует особого разрешения, а при targetSdk 33+
    /// его больше не выдают при установке. Вызов без разрешения бросает
    /// SecurityException — именно это роняло приложение на тапе по кружку подхода.
    /// Поэтому сначала спрашиваем систему, а если нельзя — ставим неточный.
    private fun scheduleAlarm() {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val trigger = SystemClock.elapsedRealtime() + (endsAtMillis - System.currentTimeMillis())
        val intent = alarmIntent()
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        try {
            if (exact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, intent)
            } else {
                // Неточный может опоздать на минуту, но это лучше падения:
                // сам отсчёт в приложении идёт по часам и не зависит от будильника.
                manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, intent)
            }
        } catch (_: SecurityException) {
            runCatching {
                manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, intent)
            }
        }
    }

    private fun cancelAlarm() {
        runCatching { context.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent()) }
    }
}

/// Кнопки отсчёта в шторке: −30 с, +30 с и «Пропустить».
class RestActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching {
            val timer = RestTimer.get(context)
            when (intent.action) {
                RestTimer.ACTION_MINUS -> timer.add(-30)
                RestTimer.ACTION_PLUS -> timer.add(30)
                RestTimer.ACTION_SKIP -> timer.stop()
            }
        }
    }
}

/// Срабатывает в момент окончания отдыха, даже если приложение свёрнуто.
class RestFinishedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching { RestTimer.get(context).finishedInBackground() }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            manager.createNotificationChannel(
                NotificationChannel(RestTimer.CHANNEL_DONE, "Отдых закончен", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val notification = NotificationCompat.Builder(context, RestTimer.CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_rest)
            .setColor(0xFFFF6029.toInt())
            .setContentTitle("Отдых закончен")
            .setContentText("Пора к следующему подходу.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
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
