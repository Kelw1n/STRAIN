package com.texasprogram.app

import com.texasprogram.app.model.CompletionRecord
import com.texasprogram.app.model.SetEntry
import com.texasprogram.app.model.TrainingRecap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import kotlin.math.roundToInt

/// Итоги месяца и года: счёт тренировок, объёма, рекордов и серий — как на iOS.
class TrainingRecapTest {
    private fun record(key: String, date: LocalDate, minutes: Int = 60) =
        CompletionRecord(key = key, epochDay = date.toEpochDay(), week = 1, day = 1, durationSeconds = minutes * 60)

    @Test
    fun monthTotalsRecordsAndFavorites() {
        val log = listOf(
            record("1-1", LocalDate.of(2026, 9, 28)),
            record("1-2", LocalDate.of(2026, 10, 6), 70),
            record("1-3", LocalDate.of(2026, 10, 8), 50),
            record("2-1", LocalDate.of(2026, 10, 20), 65)
        )
        val sets = mapOf(
            "1-1|squat|0" to SetEntry(5, 100.0),
            "1-2|squat|0" to SetEntry(5, 100.0),
            "1-2|squat|1" to SetEntry(5, 100.0),
            "1-3|bench|0" to SetEntry(5, 80.0),
            "2-1|squat|0" to SetEntry(3, 110.0),
            "2-1|bench|0" to SetEntry(5, 82.5)
        )
        val recap = TrainingRecap.build(log, sets, mapOf("squat" to "Присед", "bench" to "Жим лёжа"), TrainingRecap.Period.Month(2026, 10))
        assertEquals(3, recap.workouts)
        assertEquals((70 + 50 + 65) * 60, recap.seconds)
        assertEquals(5, recap.sets)
        assertEquals(23, recap.reps)
        assertEquals(2142.5, recap.volumeKg, 0.01)
        assertEquals("первый раз — не рекорд, равный — не рекорд", 2, recap.records)
        assertEquals("Присед", recap.favorite?.name)
        assertEquals(110.0, recap.heaviest?.bestWeight ?: 0.0, 0.0)
        assertEquals("Присед", recap.topByVolume.first().name)
        assertEquals(1, recap.previousWorkouts)
        assertEquals(500.0, recap.previousVolumeKg, 0.01)
        assertEquals(((2142.5 - 500) / 500 * 100).roundToInt(), recap.volumeChange)
        assertEquals(2, recap.activeWeeks)
        assertEquals(1, recap.longestWeekStreak)
        assertEquals(3, recap.buckets.sum())
        assertEquals("6-е и 20-е — вторники", 3, recap.busiestWeekday)
    }

    @Test
    fun yearBucketsAndStreak() {
        val log = listOf(
            record("a", LocalDate.of(2026, 1, 5)), record("b", LocalDate.of(2026, 1, 12)),
            record("c", LocalDate.of(2026, 1, 19)), record("d", LocalDate.of(2026, 3, 2))
        )
        val recap = TrainingRecap.build(log, emptyMap(), period = TrainingRecap.Period.Year(2026))
        assertEquals(12, recap.buckets.size)
        assertEquals(3, recap.buckets[0])
        assertEquals(1, recap.buckets[2])
        assertEquals(3, recap.longestWeekStreak)
        assertNull(recap.favorite)
    }

    @Test
    fun periodsOnlyWithWorkouts() {
        val log = listOf(record("x", LocalDate.of(2026, 10, 2)), record("y", LocalDate.of(2025, 6, 1)))
        val periods = TrainingRecap.periods(log, LocalDate.of(2026, 10, 20))
        assertEquals(listOf(TrainingRecap.Period.Month(2026, 10), TrainingRecap.Period.Year(2026), TrainingRecap.Period.Year(2025)), periods)
        assertEquals(TrainingRecap.Period.Month(2025, 12), TrainingRecap.Period.Month(2026, 1).previous)
        assertEquals("Октябрь 2026", TrainingRecap.Period.Month(2026, 10).title)
        assertEquals("в октябре", TrainingRecap.Period.Month(2026, 10).inPhrase)
        assertEquals("в 2025 году", TrainingRecap.Period.Year(2026).previousPhrase)
    }

    @Test
    fun volumeComparison() {
        fun recap(kg: Double) = TrainingRecap.build(
            listOf(record("1-1", LocalDate.of(2026, 10, 1))), mapOf("1-1|x|0" to SetEntry(1, kg)), period = TrainingRecap.Period.Month(2026, 10)
        )
        assertEquals("как 2 слона", recap(12_000.0).volumeComparison)
        assertEquals("как 3 легковые машины", recap(4_500.0).volumeComparison)
        assertEquals("как 5 мешков цемента", recap(250.0).volumeComparison)
        assertNull(recap(20.0).volumeComparison)
    }
}
