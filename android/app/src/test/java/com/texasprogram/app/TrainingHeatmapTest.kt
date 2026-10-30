package com.texasprogram.app

import com.texasprogram.app.model.CompletionRecord
import com.texasprogram.app.model.SetEntry
import com.texasprogram.app.model.SocialHeatmap
import com.texasprogram.app.model.TrainingHeatmap
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/// Карта тренировок за год: тяжесть дня 0–4 по тоннажу, сдвиг старой карты
/// друга на сегодня. Те же числа считает iPhone.
class TrainingHeatmapTest {
    /// Среда, 28 октября 2026.
    private val today = LocalDate.of(2026, 10, 28)

    private fun day(back: Long) = today.minusDays(back).toEpochDay()

    @Test
    fun levelsFollowTonnage() {
        // Тоннаж 100, 200, 300, 400 и 1000 кг; эталон — 400 (девять десятых).
        val tonnages = mapOf(4L to 100.0, 3L to 200.0, 2L to 300.0, 1L to 400.0, 0L to 1000.0)
        val log = tonnages.keys.map { CompletionRecord("k$it", day(it), 1, 1) } +
            CompletionRecord("empty", day(6), 1, 2) + CompletionRecord("old", day(400), 1, 3)
        val sets = tonnages.entries.associate { (back, tonnage) -> "k$back|Приседания|0" to SetEntry(10, tonnage / 10) } +
            ("open|Жим лёжа|0" to SetEntry(5, 100.0))

        val heatmap = TrainingHeatmap.build(log, sets, today)
        assertEquals("2026-10-28", heatmap.end)
        assertEquals(TrainingHeatmap.LENGTH, heatmap.days.length)
        assertEquals("без подходов — бледно, тяжелее эталона — ярче всего", "1012344", heatmap.days.takeLast(7))
        assertEquals("год назад и незакрытый день не в счёт", 6, heatmap.days.count { it != '0' })
        assertEquals(6, TrainingHeatmap.workouts(TrainingHeatmap.levels(heatmap, today)))
    }

    @Test
    fun friendsOldMapShiftsToToday() {
        val old = SocialHeatmap("2026-10-25", "0".repeat(370) + "4")
        val levels = TrainingHeatmap.levels(old, today)
        assertEquals(TrainingHeatmap.LENGTH, levels.size)
        assertEquals("тренировка три дня назад", listOf(4, 0, 0, 0), levels.takeLast(4))
        assertEquals(listOf(0, 2, 1), TrainingHeatmap.levels(SocialHeatmap("2026-10-28", "21"), today).takeLast(3))
    }

    @Test
    fun visibleWeeksStartOnMonday() {
        assertEquals("среда: 52 недели и три дня", 367, TrainingHeatmap.visibleCount(today))
        assertEquals("понедельник", 365, TrainingHeatmap.visibleCount(today.minusDays(2)))
        assertEquals("воскресенье", 371, TrainingHeatmap.visibleCount(today.minusDays(3)))
    }
}
