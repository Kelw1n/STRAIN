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
class AppStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("strain.store", Context.MODE_PRIVATE)

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
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<ProgramProfile>>(raw)
        } catch (_: Exception) {
            // Повреждённые или устаревшие данные не должны валить запуск.
            emptyList()
        }
    }

    private fun persist() {
        prefs.edit()
            .putString(KEY_PROFILES, json.encodeToString<List<ProgramProfile>>(profiles))
            .putString(KEY_ACTIVE, activeId)
            .apply()
    }

    fun add(profile: ProgramProfile) {
        profiles = profiles + profile
        activeId = profile.id
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

    fun suggestedName(): String = if (profiles.isEmpty()) "Профиль" else "Профиль ${profiles.size + 1}"

    private companion object {
        const val KEY_PROFILES = "profiles"
        const val KEY_ACTIVE = "activeId"
    }
}
