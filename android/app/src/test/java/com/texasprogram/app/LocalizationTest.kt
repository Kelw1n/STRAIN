package com.texasprogram.app

import com.texasprogram.app.service.L10n
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

/// Те же фразы, что в LocalizationTests.swift и tools/i18n/reference.py: перевод на
/// iPhone и Android должен совпадать дословно.
class LocalizationTest {
    @Before
    fun english() {
        L10n.install(File("src/main/assets/i18n-en.json").readText())
        L10n.setEnglish(true)
    }

    @After
    fun russian() = L10n.setEnglish(false)

    @Test
    fun translatesPhrasesTemplatesAndPieces() {
        val cases = mapOf(
            "Неделя 3 из 12" to "Week 3 of 12",
            "ДЕНЬ 1" to "DAY 1",
            "4 тренировки" to "4 workouts",
            "был(а) вчера" to "last seen yesterday",
            "Ноябрь 2026" to "November 2026",
            "жим лёжа" to "bench press",
            "Приседания · тест 1ПМ" to "Squats · 1RM test",
            "Ждём ответа: @kel" to "Waiting for a reply: @kel",
            "Каждую неделю добавляй к норме 30 г углеводов и смотри, как меняется вес. Пошёл вверх — держи норму." to
                "Every week add 30 g of carbs to your target and watch your weight. Once it goes up — hold the target.",
            // Неизвестная фраза остаётся целиком русской, без английских вставок.
            "Верх по средам и фулбади" to "Верх по средам и фулбади",
        )
        for ((russian, english) in cases) assertEquals(russian, english, L10n.tr(russian))
    }

    @Test
    fun russianModeLeavesTextAlone() {
        L10n.setEnglish(false)
        assertEquals("Неделя 3 из 12", L10n.tr("Неделя 3 из 12"))
    }
}
