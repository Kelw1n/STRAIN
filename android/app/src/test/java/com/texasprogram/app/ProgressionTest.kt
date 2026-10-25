package com.texasprogram.app

import com.texasprogram.app.model.CompletionRecord
import com.texasprogram.app.model.Progression
import com.texasprogram.app.model.SetEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/// Опыт и уровни: та же история, что в тесте iOS, — опыт обязан совпасть до единицы.
class ProgressionTest {
    private fun day(month: Int, day: Int) = LocalDate.of(2026, month, day).toEpochDay()

    private val log = listOf(
        CompletionRecord("1-1", day(10, 5), 1, 1),
        CompletionRecord("1-2", day(10, 7), 1, 2),
        // Повтор той же тренировки: считается один раз, по последней дате.
        CompletionRecord("1-3", day(10, 8), 1, 3),
        CompletionRecord("1-3", day(10, 9), 1, 3),
        CompletionRecord("2-1", day(10, 12), 2, 1),
        // Неделя пропущена — серия начинается заново.
        CompletionRecord("c2-1-1", day(10, 26), 1, 1)
    )

    private val sets = mapOf(
        "1-1|Приседания|0" to SetEntry(5, 100.0),
        "1-1|Приседания|1" to SetEntry(5, 100.0),
        "1-1|Жим лёжа|0" to SetEntry(5, 80.0),
        "1-2|Приседания|0" to SetEntry(5, 102.5),
        "2-1|Жим лёжа|0" to SetEntry(3, 85.0),
        "2-1|Жим лёжа|1" to SetEntry(0, 85.0),
        "c2-1-1|Приседания|0" to SetEntry(5, 100.0),
        // Подход без завершённой тренировки опыта не даёт и рекорд не двигает.
        "3-1|Приседания|0" to SetEntry(5, 200.0)
    )

    @Test
    fun experienceMatchesIphone() {
        // Неделя 5 окт.: 115 + 155 (рекорд) + 100 + 150 за цель = 520.
        // Неделя 12 окт.: 155 (рекорд жима) × 1,1 серии = 170. Неделя 26 окт.: 105.
        assertEquals(795, Progression.xp(log, sets, 3))
        assertEquals(690, Progression.xp(log.filter { it.key != "c2-1-1" }, sets, 3))
        // Цель недели не выше трёх: четырёхдневная программа бонус не отнимает.
        assertEquals(795, Progression.xp(log, sets, 4))

        val level = Progression.level(795)
        assertEquals(4, level.level)
        assertEquals(540, level.floor)
        assertEquals(960, level.next)
        assertEquals("Новичок", level.title)
        assertEquals(165, level.toNext)
    }

    @Test
    fun levelCurveAndTitles() {
        assertEquals(1, Progression.level(0).level)
        assertEquals(1, Progression.level(59).level)
        assertEquals(2, Progression.level(60).level)
        assertEquals(144_060, Progression.threshold(50))
        val top = Progression.level(10_000_000)
        assertEquals(50, top.level)
        assertNull(top.next)
        assertEquals(1.0, top.fraction, 0.0)
        assertEquals("Легенда", top.title)
        assertEquals("Атлет", Progression.title(10))
        assertEquals("Мастер", Progression.title(25))
    }

    @Test
    fun monthAwards() {
        val september = (1..12).map { CompletionRecord("s$it", day(9, it), 1, it) }
        val august = (1..8).map { CompletionRecord("a$it", day(8, it), 1, it) }
        val october = (1..5).map { CompletionRecord("o$it", day(10, it), 1, it) }
        val awards = Progression.monthAwards(september + august + october)
        assertEquals(listOf("Сентябрь 2026", "Август 2026"), awards.map { it.title })
        assertEquals(listOf(Progression.MonthTier.SILVER, Progression.MonthTier.BRONZE), awards.map { it.tier })
        val (count, next) = Progression.currentMonth(october, LocalDate.of(2026, 10, 20))
        assertEquals(5, count)
        assertEquals(Progression.MonthTier.BRONZE, next)
    }
}
