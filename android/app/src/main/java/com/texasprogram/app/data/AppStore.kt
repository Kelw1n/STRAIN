package com.texasprogram.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.texasprogram.app.model.ProgramProfile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/// Локальное хранилище профилей. Аналог SwiftData из iOS-версии:
/// список профилей плюс идентификатор активного.
class AppStore private constructor(val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("strain.store", Context.MODE_PRIVATE)

    /// Один поток записи: сохранения идут строго по порядку, последнее побеждает.
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    var profiles by mutableStateOf(loadProfiles())
        private set

    var activeId by mutableStateOf(prefs.getString(KEY_ACTIVE, "").orEmpty())
        private set

    val active: ProgramProfile?
        get() = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()

    private fun loadProfiles(): List<ProgramProfile> {
        val raw = prefs.getString(KEY_PROFILES, null)
            ?: prefs.getString(KEY_PROFILES_BACKUP, null)
            ?: return emptyList()
        return try {
            json.decodeFromString<List<ProgramProfile>>(raw)
        } catch (_: Exception) {
            // Если основной повреждён, пробуем резервную копию
            val backup = prefs.getString(KEY_PROFILES_BACKUP, null)
            try {
                backup?.let { json.decodeFromString<List<ProgramProfile>>(it) } ?: keepUnreadable(raw)
            } catch (_: Exception) {
                keepUnreadable(raw)
            }
        }
    }

    /// Не прочиталось ни основное, ни запасное. Пустой список откроет создание
    /// профиля, и первое же сохранение затёрло бы данные навсегда — поэтому
    /// сырой текст откладываем под отдельный ключ: его можно будет достать.
    private fun keepUnreadable(raw: String): List<ProgramProfile> {
        if (prefs.getString(KEY_UNREADABLE, null) == null) {
            prefs.edit().putString(KEY_UNREADABLE, raw).commit()
        }
        return emptyList()
    }

    /// Запись в фоне и по порядку: разбор в JSON всех профилей с журналом
    /// подходов на каждую отметку подхода в главном потоке давал подёргивания.
    /// Профили неизменяемые, поэтому список можно отдать другому потоку как есть.
    /// `commit` в своём потоке — запись доходит до диска, а не висит в памяти.
    private fun persist() {
        val snapshot = profiles
        val active = activeId
        writer.execute {
            runCatching {
                val serialized = json.encodeToString<List<ProgramProfile>>(snapshot)
                prefs.edit()
                    .putString(KEY_PROFILES, serialized)
                    .putString(KEY_PROFILES_BACKUP, serialized)
                    .putString(KEY_ACTIVE, active)
                    .commit()
            }
        }
    }

    /// Дождаться записи — при уходе приложения в фон.
    fun flush() {
        runCatching { writer.submit {}.get(2, java.util.concurrent.TimeUnit.SECONDS) }
    }

    /// `activate = false` — профиль добавится, но открытым останется текущий
    /// (копия чужой программы из ленты).
    fun add(profile: ProgramProfile, activate: Boolean = true) {
        profiles = profiles + profile
        if (activate) activeId = profile.id
        persist()
    }

    fun setActive(id: String) {
        activeId = id
        persist()
    }

    fun delete(id: String) {
        profiles = profiles.filterNot { it.id == id }
        if (activeId == id) activeId = profiles.firstOrNull()?.id.orEmpty()
        persist()
        // Дневник питания удалённого профиля больше никому не нужен.
        NutritionStore.delete(NutritionStore.directory(context), id)
        // Профилей не осталось: раньше будильник удалённого профиля звенел и дальше.
        if (profiles.isEmpty()) com.texasprogram.app.service.ReminderScheduler.cancel(context)
    }

    /// Профили неизменяемые: правка — это замена элемента списка новой копией.
    fun update(id: String, transform: (ProgramProfile) -> ProgramProfile) {
        profiles = profiles.map { if (it.id == id) transform(it) else it }
        persist()
    }

    fun updateActive(transform: (ProgramProfile) -> ProgramProfile) {
        val current = active ?: return
        update(current.id, transform)
    }

    /// Восстановление из копии: профиль с тем же идентификатором заменяется.
    fun restore(profile: ProgramProfile) {
        profiles = profiles.filterNot { it.id == profile.id } + profile
        if (activeId.isEmpty()) activeId = profile.id
        persist()
    }

    /// Восстановление копии целиком: профили с теми же идентификаторами
    /// заменяются, и сразу открывается первый из копии. Раньше открытым
    /// оставался текущий — после переустановки это был только что созданный
    /// пустой профиль, и казалось, что копия «импортировалась, но не применилась».
    fun restoreAll(restored: List<ProgramProfile>) {
        if (restored.isEmpty()) return
        val ids = restored.map { it.id }.toSet()
        profiles = profiles.filterNot { it.id in ids } + restored
        activeId = restored.first().id
        persist()
    }

    fun suggestedName(): String = if (profiles.isEmpty()) "Профиль" else "Профиль ${profiles.size + 1}"

    companion object {
        @Volatile
        private var instance: AppStore? = null

        /// Один на процесс: новый экземпляр после пересоздания экрана читал бы
        /// настройки, пока фоновая запись ещё не дошла, и получил бы старые данные.
        fun get(context: Context): AppStore =
            instance ?: synchronized(this) {
                instance ?: AppStore(context.applicationContext).also { instance = it }
            }

        private const val KEY_PROFILES = "profiles"
        private const val KEY_PROFILES_BACKUP = "profiles_backup"
        private const val KEY_ACTIVE = "activeId"
        private const val KEY_UNREADABLE = "profiles_unreadable"
    }
}
