package com.texasprogram.app.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.texasprogram.app.model.SocialVoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.log10
import kotlin.math.roundToInt

/// Записанное голосовое: звук AAC (m4a), длительность и «волна» для пузыря.
class VoiceRecording(val data: ByteArray, val durationMs: Int, val waveform: String)

/// Запись голосового. Тот же формат, что пишет iPhone, — AAC в m4a: голосовое
/// с Android играет на айфоне и наоборот.
class VoiceRecorder(private val context: Context) {
    var isRecording by mutableStateOf(false)
        private set
    var elapsedMs by mutableLongStateOf(0L)
        private set
    /// Последние уровни громкости от 0 до 1 — живая волна, пока записываем.
    val recentLevels = mutableStateListOf<Float>()

    /// Запись дошла до минуты — экран сам отправляет её.
    var onLimit: (() -> Unit)? = null

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private var meter: Job? = null
    private val levels = ArrayList<Float>()
    private var focus: AudioFocusRequest? = null

    fun start(scope: CoroutineScope): Boolean {
        if (isRecording) return false
        val target = File(context.cacheDir, "voice-message.m4a").also { it.delete() }
        val recorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioChannels(1)
            recorder.setAudioSamplingRate(22_050)
            recorder.setAudioEncodingBitRate(32_000)
            recorder.setOutputFile(target.path)
            recorder.prepare()
            // Музыка в наушниках на время записи замолкает, потом играет дальше.
            focus = requestFocus(context, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE, AudioAttributes.USAGE_VOICE_COMMUNICATION)
            recorder.start()
        } catch (e: Exception) {
            recorder.release()
            abandonFocus(context, focus)
            focus = null
            return false
        }
        this.recorder = recorder
        file = target
        levels.clear()
        recentLevels.clear()
        startedAt = SystemClock.elapsedRealtime()
        elapsedMs = 0
        isRecording = true
        meter = scope.launch {
            while (isActive && isRecording) {
                delay(50)
                tick()
            }
        }
        return true
    }

    /// Закончить и отдать запись; слишком короткая — `null`.
    fun stop(): VoiceRecording? {
        val recorder = recorder ?: return null
        val duration = SystemClock.elapsedRealtime() - startedAt
        // Совсем короткая запись: MediaRecorder бросает исключение — звука нет.
        val stopped = runCatching { recorder.stop() }.isSuccess
        val target = file
        finish()
        try {
            if (!stopped || target == null || duration < MIN_MS) return null
            val bytes = target.readBytes()
            if (bytes.isEmpty()) return null
            return VoiceRecording(bytes, duration.coerceAtMost(MAX_MS).toInt(), waveform(levels))
        } catch (e: IOException) {
            return null
        } finally {
            target?.delete()
        }
    }

    fun cancel() {
        val recorder = recorder ?: return
        runCatching { recorder.stop() }
        val target = file
        finish()
        target?.delete()
    }

    private fun finish() {
        meter?.cancel()
        meter = null
        recorder?.release()
        recorder = null
        isRecording = false
        abandonFocus(context, focus)
        focus = null
    }

    private fun tick() {
        val recorder = recorder ?: return
        // Пик с прошлого замера — в децибелы от −50 (тишина) до 0 и в долю от 0 до 1.
        val amplitude = runCatching { recorder.maxAmplitude }.getOrDefault(0)
        val db = if (amplitude > 0) 20 * log10(amplitude / 32767.0) else -60.0
        val level = ((db + 50) / 50).coerceIn(0.0, 1.0).toFloat()
        levels.add(level)
        recentLevels.add(level)
        while (recentLevels.size > 28) recentLevels.removeAt(0)
        elapsedMs = SystemClock.elapsedRealtime() - startedAt
        if (elapsedMs >= MAX_MS) onLimit?.invoke()
    }

    companion object {
        /// Дольше минуты голосовое не бывает.
        const val MAX_MS = 60_000L
        /// Короче полусекунды — это было нажатие, а не запись.
        const val MIN_MS = 500L

        /// 40 столбиков волны: в каждом — самый громкий кусочек, цифрой от 0 до f.
        fun waveform(levels: List<Float>, bars: Int = 40): String {
            if (levels.isEmpty()) return ""
            return (0 until bars).joinToString("") { bar ->
                val start = bar * levels.size / bars
                val end = maxOf(start + 1, (bar + 1) * levels.size / bars).coerceAtMost(levels.size)
                val peak = levels.subList(start.coerceAtMost(levels.size - 1), end).maxOrNull() ?: 0f
                (peak * 15).roundToInt().coerceIn(0, 15).toString(16)
            }
        }
    }
}

/// Проигрывание голосовых: одновременно звучит одно. Звук скачивается один
/// раз в кэш приложения и дальше играет оттуда.
object VoicePlayer {
    /// Какое голосовое играет, какое скачивается и какое не удалось открыть.
    var playingId by mutableStateOf<String?>(null)
        private set
    var loadingId by mutableStateOf<String?>(null)
        private set
    var failedId by mutableStateOf<String?>(null)
        private set

    private val scope = MainScope()
    private var player: MediaPlayer? = null
    private var job: Job? = null
    private var focus: AudioFocusRequest? = null
    private var appContext: Context? = null

    /// Сколько проиграно, от 0 до 1. Читается каждый кадр, пока играет.
    fun progress(id: String): Float {
        val player = player ?: return 0f
        if (playingId != id) return 0f
        val duration = runCatching { player.duration }.getOrDefault(0)
        return if (duration > 0) runCatching { player.currentPosition }.getOrDefault(0) / duration.toFloat() else 0f
    }

    fun positionMs(id: String): Long {
        val player = player ?: return 0L
        if (playingId != id) return 0L
        return runCatching { player.currentPosition.toLong() }.getOrDefault(0L)
    }

    fun toggle(context: Context, voice: SocialVoice) {
        if (playingId == voice.id) {
            stop()
            return
        }
        stop()
        failedId = null
        val app = context.applicationContext
        appContext = app
        job = scope.launch {
            val file = File(File(app.cacheDir, "voices"), "${voice.id}.m4a")
            if (!file.exists()) {
                loadingId = voice.id
                val loaded = withContext(Dispatchers.IO) { runCatching { download(SocialService.voiceUrl(voice.id), file) }.isSuccess }
                if (loadingId == voice.id) loadingId = null
                if (!loaded) {
                    failedId = voice.id
                    return@launch
                }
            }
            try {
                val player = MediaPlayer()
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                player.setDataSource(file.path)
                player.prepare()
                player.setOnCompletionListener { if (this@VoicePlayer.player === it) stop() }
                focus = requestFocus(app, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, AudioAttributes.USAGE_MEDIA)
                player.start()
                this@VoicePlayer.player = player
                playingId = voice.id
            } catch (e: Exception) {
                file.delete()
                failedId = voice.id
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        playingId = null
        loadingId = null
        appContext?.let { abandonFocus(it, focus) }
        focus = null
    }

    /// Качаем во временный файл и переименовываем в конце: оборванная загрузка
    /// не оставит битый звук в кэше.
    private fun download(url: String, target: File) {
        target.parentFile?.mkdirs()
        val partial = File(target.parentFile, target.name + ".part")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        try {
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            connection.inputStream.use { input -> partial.outputStream().use { input.copyTo(it) } }
            if (!partial.renameTo(target)) throw IOException("Не удалось сохранить звук")
        } finally {
            connection.disconnect()
            partial.delete()
        }
    }
}

private fun requestFocus(context: Context, gain: Int, usage: Int): AudioFocusRequest? {
    val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
    val request = AudioFocusRequest.Builder(gain)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .build()
    return if (manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) request else null
}

private fun abandonFocus(context: Context, request: AudioFocusRequest?) {
    if (request == null) return
    (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.abandonAudioFocusRequest(request)
}
