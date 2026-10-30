package com.texasprogram.app

import com.texasprogram.app.model.CompletionRecord
import com.texasprogram.app.model.PersonalRecords
import com.texasprogram.app.model.SetEntry
import com.texasprogram.app.model.SocialUser
import com.texasprogram.app.service.SocialService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/// Рекорды в профиле: лучший подход движения за все циклы, присед/жим/становая
/// и сумма. Те же числа считает iPhone.
class PersonalRecordsTest {
    @Test
    fun estimateAndLifts() {
        assertEquals(163.5, PersonalRecords.estimate(140.0, 5), 0.0)
        assertEquals(100.0, PersonalRecords.estimate(100.0, 1), 0.0)
        assertEquals("дальше 12 повторов не растёт", 84.0, PersonalRecords.estimate(60.0, 20), 0.0)
        assertEquals(PersonalRecords.Lift.SQUAT, PersonalRecords.lift("Приседания"))
        assertEquals(PersonalRecords.Lift.BENCH, PersonalRecords.lift("Жим лёжа"))
        assertEquals(PersonalRecords.Lift.DEADLIFT, PersonalRecords.lift("Становая тяга"))
        assertNull(PersonalRecords.lift("Фронтальные приседания"))
        assertNull(PersonalRecords.lift("Жим стоя"))
        assertNull(PersonalRecords.lift("Жим ногами"))
        assertNull(PersonalRecords.lift("Румынская тяга"))
    }

    @Test
    fun bestSetAcrossCyclesOnlyFromCompletedDays() {
        val first = LocalDate.of(2026, 10, 9).toEpochDay()
        val second = LocalDate.of(2026, 10, 20).toEpochDay()
        val log = listOf(CompletionRecord("1-1", first, 1, 1), CompletionRecord("c2-1-1", second, 1, 1))
        val sets = mutableMapOf(
            "1-1|Приседания|0" to SetEntry(5, 140.0),
            "1-1|Приседания|1" to SetEntry(3, 145.0),
            "c2-1-1|Приседания|0" to SetEntry(2, 150.0),
            "c2-1-1|Жим лёжа|0" to SetEntry(5, 100.0),
            "c2-1-1|custom-abc|0" to SetEntry(10, 20.0),
            "c2-1-1|own-lost|0" to SetEntry(10, 40.0),
            "2-1|Становая тяга|0" to SetEntry(1, 200.0)
        )
        val names = mapOf("custom-abc" to "Молотки")
        var records = PersonalRecords.records(log, sets, names, ZoneOffset.UTC)
        assertEquals("незакрытый день и потерянное упражнение не в счёт", listOf("Приседания", "Жим лёжа", "Молотки"), records.map { it.name })
        val squat = PersonalRecords.best(records, PersonalRecords.Lift.SQUAT)!!
        assertEquals("140 × 5", squat.setText)
        assertEquals(163.5, squat.estimate, 0.0)
        assertEquals(LocalDate.of(2026, 10, 9), PersonalRecords.localDate(squat, ZoneOffset.UTC))
        assertNull("без становой суммы нет", PersonalRecords.total(records))

        sets["c2-1-1|Становая тяга|0"] = SetEntry(3, 180.0)
        records = PersonalRecords.records(log, sets, names, ZoneOffset.UTC)
        assertEquals(163.5 + 116.5 + 198.0, PersonalRecords.total(records)!!, 0.0)
    }

    @Test
    fun recordsTravelInProfile() {
        val user = SocialService.json.decodeFromString<SocialUser>(
            """{"id":"u","username":"tim","records":[{"name":"Жим лёжа","weight":100,"reps":5,"estimate":116.5,"date":1761500000000,"lift":"bench"},
               {"name":"Подтягивания","weight":20,"reps":8}]}"""
        )
        assertEquals(2, user.records.size)
        assertEquals("старый формат без максимума — считаем сами", 25.5, user.records[1].estimate, 0.0)
        assertEquals(100.0, PersonalRecords.best(user.records, PersonalRecords.Lift.BENCH)!!.weight, 0.0)
    }
}
