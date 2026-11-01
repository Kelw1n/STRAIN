@file:UseSerializers(IsoInstantSerializer::class)

package com.texasprogram.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// MARK: - Добавки

/// Когда пьётся добавка. «С тренировкой» — протеин, предтрен: в дни без
/// тренировки такую не ждём, и серия из-за неё не рвётся.
@Serializable
enum class SupplementSlot(val title: String) {
    @SerialName("morning") MORNING("Утром"),
    @SerialName("day") DAY("Днём"),
    @SerialName("evening") EVENING("Вечером"),
    @SerialName("workout") WORKOUT("С тренировкой")
}

/// Время напоминания: часы и минуты по местному времени.
@Serializable
data class SupplementReminder(val hour: Int, val minute: Int) {
    val text: String get() = "%d:%02d".format(hour, minute)
}

/// Одна добавка из списка: креатин, омега-3, витамин D.
@Serializable
data class Supplement(
    val id: String = newNutritionId(),
    val name: String,
    /// Доза как её говорят: «5 г», «2 капсулы».
    val dose: String = "",
    val slot: SupplementSlot = SupplementSlot.MORNING,
    val reminder: SupplementReminder? = null,
    /// С какого дня в списке: дни раньше не считаются пропущенными.
    val created: Instant = Instant.now()
)

/// Отметка «выпил»: какая добавка и когда. Ключ `supplementID` — как на iOS.
@Serializable
data class SupplementIntake(
    @SerialName("supplementID") val supplementId: String,
    val date: Instant
)

/// Добавки профиля и отметки. Лежат в файле питания — и в резервной копии вместе с ним.
@Serializable
data class SupplementData(
    val items: List<Supplement> = emptyList(),
    val taken: List<SupplementIntake> = emptyList()
)

/// Частые добавки — добавить в одно касание.
data class SupplementPreset(val name: String, val dose: String, val slot: SupplementSlot) {
    companion object {
        val ALL = listOf(
            SupplementPreset("Креатин", "5 г", SupplementSlot.MORNING),
            SupplementPreset("Омега-3", "2 капсулы", SupplementSlot.MORNING),
            SupplementPreset("Витамин D3", "2000 МЕ", SupplementSlot.MORNING),
            SupplementPreset("Магний", "400 мг", SupplementSlot.EVENING),
            SupplementPreset("Протеин", "30 г", SupplementSlot.WORKOUT),
            SupplementPreset("Мультивитамины", "1 таблетка", SupplementSlot.MORNING),
            SupplementPreset("Цинк", "25 мг", SupplementSlot.EVENING),
            SupplementPreset("Кофеин", "200 мг", SupplementSlot.WORKOUT)
        )
    }
}

// MARK: - Расчёты

/// Что выпито за день, серия дней и последние дни. Один расчёт на iOS и
/// Android — `Supplements.swift`.
object SupplementTracker {
    /// Как выглядит день: сколько нужно было выпить и сколько выпито.
    data class DayState(val day: LocalDate, val required: Int, val taken: Int) {
        val isComplete: Boolean get() = required > 0 && taken >= required
        val share: Double get() = if (required > 0) minOf(taken.toDouble() / required, 1.0) else 0.0
    }

    private fun dayOf(instant: Instant, zone: ZoneId): LocalDate = instant.atZone(zone).toLocalDate()

    /// Выпита ли добавка в этот день.
    fun isTaken(item: Supplement, day: LocalDate, data: SupplementData, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        data.taken.any { it.supplementId == item.id && dayOf(it.date, zone) == day }

    /// Когда отмечена — для подписи «в 9:12».
    fun intake(item: Supplement, day: LocalDate, data: SupplementData, zone: ZoneId = ZoneId.systemDefault()): SupplementIntake? =
        data.taken.firstOrNull { it.supplementId == item.id && dayOf(it.date, zone) == day }

    /// Добавки, которые ждём в этот день: уже были в списке, а «с тренировкой» —
    /// только в день тренировки.
    fun required(day: LocalDate, items: List<Supplement>, trainedDays: Set<LocalDate>, zone: ZoneId = ZoneId.systemDefault()): List<Supplement> {
        val trained = day in trainedDays
        return items.filter { !dayOf(it.created, zone).isAfter(day) && (it.slot != SupplementSlot.WORKOUT || trained) }
    }

    fun state(day: LocalDate, data: SupplementData, trainedDays: Set<LocalDate>, zone: ZoneId = ZoneId.systemDefault()): DayState {
        val takenIds = data.taken.filter { dayOf(it.date, zone) == day }.map { it.supplementId }.toSet()
        val required = required(day, data.items, trainedDays, zone)
        return DayState(day, required.size, required.count { it.id in takenIds })
    }

    /// Дни подряд, когда выпито всё. Сегодня ещё идёт: не допил — серия
    /// считается со вчера и не обнуляется до завтра. День, в который ничего
    /// не ждали (только «с тренировкой», а тренировки не было), серию не рвёт.
    fun streak(data: SupplementData, trainedDays: Set<LocalDate>, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Int {
        val first = data.items.minOfOrNull { dayOf(it.created, zone) } ?: return 0
        var day = today
        if (!state(day, data, trainedDays, zone).isComplete) day = day.minusDays(1)
        var count = 0
        // Год назад и раньше не смотрим: проход по дням не должен расти без конца.
        repeat(400) {
            if (day.isBefore(first)) return count
            val state = state(day, data, trainedDays, zone)
            if (state.required > 0) {
                if (!state.isComplete) return count
                count += 1
            }
            day = day.minusDays(1)
        }
        return count
    }

    /// Последние `count` дней по порядку, сегодня последним.
    fun recent(count: Int = 7, data: SupplementData, trainedDays: Set<LocalDate>, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): List<DayState> =
        (count - 1 downTo 0).map { state(today.minusDays(it.toLong()), data, trainedDays, zone) }

    /// Отметить или снять отметку. Отметка за прошлый день — на полдень того
    /// дня, за сегодня — на сейчас.
    fun toggle(item: Supplement, day: LocalDate, data: SupplementData, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): SupplementData {
        if (isTaken(item, day, data, zone)) {
            return data.copy(taken = data.taken.filterNot { it.supplementId == item.id && dayOf(it.date, zone) == day })
        }
        val moment = if (dayOf(now, zone) == day) now else day.atTime(LocalTime.NOON).atZone(zone).toInstant()
        return data.copy(taken = data.taken + SupplementIntake(item.id, moment))
    }

    /// Порядок в списке: утро, день, вечер, с тренировкой; внутри — как добавлены.
    fun ordered(items: List<Supplement>): List<Supplement> = items.sortedBy { it.slot.ordinal }

    /// Отметки старше года больше ни на что не влияют — чистим, чтобы файл не рос.
    fun pruned(data: SupplementData, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): SupplementData {
        val limit = dayOf(now, zone).minusDays(400).atStartOfDay(zone).toInstant()
        return data.copy(taken = data.taken.filterNot { it.date.isBefore(limit) })
    }
}
