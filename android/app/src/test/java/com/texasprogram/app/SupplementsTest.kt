package com.texasprogram.app

import com.texasprogram.app.data.NutritionStore
import com.texasprogram.app.model.NutritionData
import com.texasprogram.app.model.Supplement
import com.texasprogram.app.model.SupplementData
import com.texasprogram.app.model.SupplementIntake
import com.texasprogram.app.model.SupplementReminder
import com.texasprogram.app.model.SupplementSlot
import com.texasprogram.app.model.SupplementTracker
import com.texasprogram.app.service.SupplementReminders
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/// Трекер добавок: те же случаи, что в SupplementTests на iOS.
class SupplementsTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private fun day(d: Int) = LocalDate.of(2026, 3, d)
    private fun moment(d: Int, hour: Int = 10) = day(d).atTime(hour, 0).atZone(zone).toInstant()

    private val creatine = "00000000-0000-0000-0000-00000000000A"
    private val protein = "00000000-0000-0000-0000-00000000000B"

    private fun data(vararg taken: Pair<String, Int>) = SupplementData(
        items = listOf(
            Supplement(creatine, "Креатин", "5 г", SupplementSlot.MORNING, SupplementReminder(9, 0), moment(1)),
            Supplement(protein, "Протеин", "30 г", SupplementSlot.WORKOUT, null, moment(1))
        ),
        taken = taken.map { SupplementIntake(it.first, moment(it.second)) }
    )

    @Test
    fun dayStateCountsWorkoutOnlyOnTrainingDays() {
        val data = data(creatine to 5)
        val rest = SupplementTracker.state(day(5), data, emptySet(), zone)
        assertEquals(1, rest.required)
        assertTrue(rest.isComplete)
        val training = SupplementTracker.state(day(5), data, setOf(day(5)), zone)
        assertEquals(2, training.required)
        assertEquals(1, training.taken)
        assertFalse(training.isComplete)
        assertEquals(0.5, training.share, 1e-9)
    }

    @Test
    fun streakSkipsUnfinishedTodayAndStopsOnMiss() {
        val data = data(creatine to 2, creatine to 3, creatine to 4, creatine to 5, creatine to 6, creatine to 7)
        assertEquals(2, SupplementTracker.streak(data, setOf(day(5)), day(8), zone))
        assertEquals(6, SupplementTracker.streak(data, emptySet(), day(8), zone))
    }

    @Test
    fun streakCountsTodayWhenDone() {
        assertEquals(2, SupplementTracker.streak(data(creatine to 7, creatine to 8), emptySet(), day(8), zone))
        assertEquals(0, SupplementTracker.streak(SupplementData(), emptySet(), day(8), zone))
    }

    @Test
    fun daysBeforeCreationAreNotRequired() {
        val base = data()
        val data = base.copy(items = listOf(base.items[0].copy(created = moment(10))) + base.items.drop(1))
        assertEquals(0, SupplementTracker.state(day(9), data, emptySet(), zone).required)
        assertEquals(1, SupplementTracker.state(day(10), data, emptySet(), zone).required)
    }

    @Test
    fun toggleTodayAndPastDay() {
        var data = data()
        val item = data.items[0]
        data = SupplementTracker.toggle(item, day(8), data, moment(8, 7), zone)
        assertEquals(moment(8, 7), data.taken.last().date)
        data = SupplementTracker.toggle(item, day(6), data, moment(8, 7), zone)
        assertEquals(moment(6, 12), data.taken.last().date)
        assertTrue(SupplementTracker.isTaken(item, day(6), data, zone))
        data = SupplementTracker.toggle(item, day(8), data, moment(8, 9), zone)
        assertFalse(SupplementTracker.isTaken(item, day(8), data, zone))
        assertEquals(1, data.taken.size)
    }

    @Test
    fun recentWeekEndsToday() {
        val recent = SupplementTracker.recent(7, data(creatine to 8), emptySet(), day(8), zone)
        assertEquals(7, recent.size)
        assertEquals(day(2), recent.first().day)
        assertEquals(day(8), recent.last().day)
        assertTrue(recent.last().isComplete)
    }

    @Test
    fun orderBySlot() {
        val items = listOf(
            Supplement(name = "Магний", slot = SupplementSlot.EVENING),
            Supplement(name = "Протеин", slot = SupplementSlot.WORKOUT),
            Supplement(name = "Креатин", slot = SupplementSlot.MORNING),
            Supplement(name = "Омега", slot = SupplementSlot.MORNING)
        )
        assertEquals(listOf("Креатин", "Омега", "Магний", "Протеин"), SupplementTracker.ordered(items).map { it.name })
    }

    @Test
    fun prunedDropsOldIntakes() {
        val base = data(creatine to 8)
        val data = base.copy(taken = base.taken + SupplementIntake(creatine, moment(8).minusSeconds(500L * 86_400)))
        assertEquals(1, SupplementTracker.pruned(data, moment(8), zone).taken.size)
    }

    @Test
    fun reminderSlotsGroupByTime() {
        val data = SupplementData(
            listOf(
                Supplement(name = "Креатин", dose = "5 г", reminder = SupplementReminder(9, 0)),
                Supplement(name = "Магний", slot = SupplementSlot.EVENING, reminder = SupplementReminder(22, 30)),
                Supplement(name = "Омега-3", dose = "2 капсулы", reminder = SupplementReminder(9, 0)),
                Supplement(name = "Цинк")
            )
        )
        val slots = SupplementReminders.slots(data)
        assertEquals(listOf("0900", "2230"), slots.map { it.id })
        assertEquals("Креатин 5 г · Омега-3 2 капсулы", SupplementReminders.body(slots[0].items))
        assertEquals("Магний", SupplementReminders.body(slots[1].items))
    }

    @Test
    fun reminderStaysSilentWhenTaken() {
        assertEquals(listOf("Креатин"), SupplementReminders.due(data(), 9 * 60, day(8), zone).map { it.name })
        assertTrue(SupplementReminders.due(data(creatine to 8), 9 * 60, day(8), zone).isEmpty())
        assertTrue(SupplementReminders.due(data(), 10 * 60, day(8), zone).isEmpty())
    }

    @Test
    fun nextOccurrenceTodayOrTomorrow() {
        val now = LocalDateTime.of(2026, 3, 8, 8, 0)
        assertEquals(LocalDateTime.of(2026, 3, 8, 9, 0), SupplementReminders.nextOccurrence(9, 0, now))
        assertEquals(LocalDateTime.of(2026, 3, 9, 7, 30), SupplementReminders.nextOccurrence(7, 30, now))
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun oldNutritionFileWithoutSupplementsOpens() {
        assertNull(json.decodeFromString<NutritionData>("""{"entries":[]}""").supplements)
    }

    /// Так питание с добавками пишет iPhone (копия — ISO 8601, ключ supplementID).
    @Test
    fun iosBackupJson() {
        val text = """
        {"entries":[],"supplements":{"items":[{"id":"0B5B7C2E-6A43-4C79-9D8B-3F0E3C1D2A11","name":"Креатин","dose":"5 г","slot":"morning","reminder":{"hour":9,"minute":0},"created":"2026-03-01T07:00:00Z"},{"id":"1C5B7C2E-6A43-4C79-9D8B-3F0E3C1D2A12","name":"Протеин","dose":"30 г","slot":"workout","created":"2026-03-01T07:00:00Z"}],"taken":[{"supplementID":"0B5B7C2E-6A43-4C79-9D8B-3F0E3C1D2A11","date":"2026-03-02T06:15:00Z"}]}}
        """.trimIndent()
        val data = json.decodeFromString<NutritionData>(text)
        val supplements = data.supplements!!
        assertEquals(listOf("Креатин", "Протеин"), supplements.items.map { it.name })
        assertEquals(SupplementReminder(9, 0), supplements.items[0].reminder)
        assertNull(supplements.items[1].reminder)
        assertEquals(SupplementSlot.WORKOUT, supplements.items[1].slot)
        assertEquals(supplements.items[0].id, supplements.taken[0].supplementId)

        val written = json.encodeToString(NutritionData.serializer(), data)
        assertTrue(written.contains("\"supplementID\""))
        assertTrue(written.contains("\"slot\":\"workout\""))
        assertEquals(data, json.decodeFromString<NutritionData>(written))
    }

    @Test
    fun storeKeepsSupplements() {
        val directory: File = Files.createTempDirectory("supplements").toFile()
        try {
            val store = NutritionStore(directory)
            store.open("P1")
            val item = Supplement(name = "Креатин", dose = "5 г")
            store.saveSupplement(item)
            store.toggleSupplement(item, LocalDate.now())
            assertEquals(1, NutritionStore.read(directory, "P1")?.supplements?.taken?.size)

            val renamed = item.copy(name = "Креатин моногидрат")
            store.saveSupplement(renamed)
            assertEquals(listOf("Креатин моногидрат"), store.supplements.items.map { it.name })

            store.deleteSupplement(renamed)
            assertNull(NutritionStore.read(directory, "P1")?.supplements)
        } finally {
            directory.deleteRecursively()
        }
    }
}
