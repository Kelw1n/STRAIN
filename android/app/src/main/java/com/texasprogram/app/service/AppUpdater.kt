package com.texasprogram.app.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/// Обновление Android-версии без магазина приложений.
///
/// CI кладёт в релиз `android-latest` рядом со `STRAIN.apk` файл `version.json`
/// с номером сборки. Приложение сверяет его со своим номером, по кнопке скачивает
/// новый APK и отдаёт системному установщику. Android сам спрашивает «Обновить?»,
/// данные остаются на месте: новая сборка подписана тем же ключом.
object AppUpdater {
    private const val BASE = "https://github.com/Kelw1n/STRAIN/releases/download/android-latest/"
    private const val VERSION_URL = BASE + "version.json"
    private const val APK_URL = BASE + "STRAIN.apk"
    /// Свёрнутое и снова открытое приложение проверяет не чаще раза в шесть часов.
    private const val RECHECK_MS = 6 * 60 * 60 * 1000L

    @Serializable
    data class Release(val versionCode: Long, val versionName: String = "", val notes: String = "")

    sealed interface State {
        /// Какую версию предлагаем; `null` — обновлений нет.
        val release: Release?

        data object Idle : State {
            override val release: Release? get() = null
        }
        data class Available(override val release: Release) : State
        /// `progress` — от 0 до 1; −1, если сервер не сказал размер файла.
        data class Downloading(override val release: Release, val progress: Float) : State
        /// Нужно один раз разрешить установку из STRAIN в настройках Android.
        data class NeedsPermission(override val release: Release) : State
        /// Скачано и проверено: открыт системный установщик.
        data class Ready(override val release: Release) : State
        data class Failed(override val release: Release, val message: String) : State
    }

    var state by mutableStateOf<State>(State.Idle)
        private set

    /// «Позже» на плашке — до следующего запуска приложения не показываем.
    var dismissed by mutableStateOf(false)
        private set

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true }
    private var checking = false
    private var lastCheck = 0L
    private var download: Job? = null

    fun installedVersionCode(context: Context): Long = runCatching {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
    }.getOrDefault(0L)

    fun installedVersionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

    /// Есть ли версия новее установленной; `null` — проверить не удалось (нет сети).
    /// Ошибки молча пропускаем: проверка — не повод мешать человеку тренироваться.
    suspend fun check(context: Context, force: Boolean = false): Boolean? {
        val now = SystemClock.elapsedRealtime()
        if (checking || download?.isActive == true) return state.release != null
        if (!force && lastCheck != 0L && now - lastCheck < RECHECK_MS) return state.release != null
        checking = true
        try {
            val text = withContext(Dispatchers.IO) { fetchText(VERSION_URL) }
            lastCheck = now
            val release = json.decodeFromString<Release>(text)
            val app = context.applicationContext
            return if (release.versionCode > installedVersionCode(app)) {
                if (state.release?.versionCode != release.versionCode) state = State.Available(release)
                true
            } else {
                state = State.Idle
                withContext(Dispatchers.IO) { updatesDir(app).deleteRecursively() }
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return if (state.release != null) true else null
        } finally {
            checking = false
        }
    }

    fun dismiss() {
        dismissed = true
    }

    /// «Обновить»: скачать, если ещё не скачано, и открыть установщик.
    fun update(context: Context) {
        val release = state.release ?: return
        val app = context.applicationContext
        if (!app.packageManager.canRequestPackageInstalls()) {
            state = State.NeedsPermission(release)
            openInstallPermission(app)
            return
        }
        val file = apkFile(app)
        if (state is State.Ready && file.exists()) {
            install(app, file, release)
            return
        }
        if (download?.isActive == true) return
        download = scope.launch {
            state = State.Downloading(release, 0f)
            try {
                withContext(Dispatchers.IO) {
                    fetchFile(APK_URL, file) { progress -> state = State.Downloading(release, progress) }
                }
                // Скачалось именно наше приложение, а не страница ошибки.
                val info = app.packageManager.getPackageArchiveInfo(file.path, 0)
                if (info == null || info.packageName != app.packageName) throw IOException("Не тот файл")
                state = State.Ready(release)
                install(app, file, release)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                file.delete()
                state = State.Failed(release, "Не удалось скачать обновление. Проверь интернет и попробуй ещё раз.")
            }
        }
    }

    /// Приложение снова на экране: вернулись из настроек с разрешением — продолжаем
    /// сами; давно не проверяли — проверяем.
    fun onResume(context: Context) {
        val app = context.applicationContext
        if (state is State.NeedsPermission && app.packageManager.canRequestPackageInstalls()) {
            update(app)
            return
        }
        scope.launch { check(app) }
    }

    /// Экран «Установка неизвестных приложений» сразу на STRAIN.
    fun openInstallPermission(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun install(context: Context, file: File, release: Release) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            state = State.Failed(release, "Не удалось открыть установщик Android.")
        }
    }

    private fun updatesDir(context: Context) = File(context.cacheDir, "updates")

    private fun apkFile(context: Context) = File(updatesDir(context), "STRAIN.apk")

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            useCaches = false
            instanceFollowRedirects = true
        }

    private fun fetchText(url: String): String {
        // Метка времени — чтобы промежуточный кэш не отдал прошлую версию.
        val connection = open("$url?t=${System.currentTimeMillis()}")
        try {
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /// Качаем во временный файл и переименовываем в конце: оборванная загрузка
    /// не оставит битый APK, который потом отдали бы установщику.
    private fun fetchFile(url: String, target: File, onProgress: (Float) -> Unit) {
        target.parentFile?.mkdirs()
        val partial = File(target.parentFile, target.name + ".part")
        val connection = open(url)
        try {
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong
            if (total <= 0) onProgress(-1f)
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var loaded = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        loaded += read
                        if (total > 0) {
                            val percent = (loaded * 100 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent / 100f)
                            }
                        }
                    }
                }
            }
            target.delete()
            if (!partial.renameTo(target)) throw IOException("Не удалось сохранить файл")
        } finally {
            connection.disconnect()
            partial.delete()
        }
    }
}
