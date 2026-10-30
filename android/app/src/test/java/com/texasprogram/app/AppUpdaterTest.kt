package com.texasprogram.app

import com.texasprogram.app.service.AppUpdater
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/// Что нового в обновлении: история версий из version.json и пункты описания.
class AppUpdaterTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun changesSinceInstalledVersionNewestFirst() {
        val release = json.decodeFromString<AppUpdater.Release>(
            """{"versionCode":1172,"versionName":"3.1.172","notes":"Стекло","date":"2026-10-30",
               "history":[{"versionCode":1172,"versionName":"3.1.172","notes":"Стекло\n• Плавнее"},
                          {"versionCode":1170,"versionName":"3.1.170","notes":"- Плашка обновления"},
                          {"versionCode":1163,"versionName":"3.1.163","notes":"Рекорды"}]}"""
        )
        assertEquals(listOf(1172L, 1170L), release.changes(since = 1163).map { it.versionCode })
        assertEquals("только между установленной и новой", listOf(1170L), release.changes(since = 1163, upTo = 1170).map { it.versionCode })
        assertEquals(listOf("Стекло", "Плавнее"), release.changes(since = 1170).single().points)
        assertEquals(listOf("Плашка обновления"), release.history[1].points)
    }

    @Test
    fun oldVersionFileWithoutHistory() {
        val release = json.decodeFromString<AppUpdater.Release>("""{"versionCode":1163,"versionName":"3.1.163","notes":"Рекорды"}""")
        assertEquals(listOf("Рекорды"), release.changes(since = 1158).single().points)
        assertEquals(emptyList<AppUpdater.Change>(), release.changes(since = 1163))
    }
}
