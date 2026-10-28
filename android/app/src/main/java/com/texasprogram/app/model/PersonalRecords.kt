package com.texasprogram.app.model

import com.texasprogram.app.service.ExerciseArt
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.min
import kotlin.math.roundToLong

// MARK: - Рекорды для профиля

/// Лучший подход движения за всё время: вес, повторы, расчётный максимум и дата.
/// Считается на телефоне по всем циклам и уходит в профиль — друзья видят витрину.
@Serializable
data class SocialRecord(
    val name: String,
    val weight: Double,
    val reps: Int,
    /// Расчётный разовый максимум по Эпли, до 0,5 кг.
    val estimate: Double = PersonalRecords.estimate(weight, reps),
    /// Когда поставлен: миллисекунды, как остальные даты сервера.
    val date: Double = 0.0,
    /// squat / bench / deadlift — для витрины и суммы троеборья.
    val lift: String? = null
) {
    val setText: String get() = "${formatWeight(weight)} × $reps"
}

object PersonalRecords {
    enum class Lift(val raw: String, val title: String) {
        SQUAT("squat", "Присед"),
        BENCH("bench", "Жим лёжа"),
        DEADLIFT("deadlift", "Становая")
    }

    /// Сколько движений уходит в профиль: витрине хватает, сервер не распухает.
    const val LIMIT = 30

    /// Присед, жим лёжа или становая — по названию, тем же разбором, что и картинки.
    /// Жим должен быть лёжа: жим стоя и жим ногами в сумму не идут.
    fun lift(name: String): Lift? = when (ExerciseArt.key(name)) {
        "squat" -> Lift.SQUAT
        "deadlift" -> Lift.DEADLIFT
        "bench" -> if ("леж" in ProgramProfile.weightStepKey(name)) Lift.BENCH else null
        else -> null
    }

    /// Эпли: 140 × 5 ≈ 163. Дальше двенадцати повторов формула врёт — их не считаем.
    fun estimate(weight: Double, reps: Int): Double {
        if (weight <= 0 || reps <= 0) return 0.0
        val raw = if (reps == 1) weight else weight * (1 + min(reps, 12) / 30.0)
        return Math.round(raw * 2) / 2.0
    }

    fun records(profile: ProgramProfile): List<SocialRecord> =
        records(profile.completionLog, profile.setLog, names(profile))

    /// Лучший подход каждого движения по всем циклам: только из отмеченных
    /// тренировок, дата — день, когда тренировку закрыли.
    fun records(
        log: List<CompletionRecord>,
        sets: Map<String, SetEntry>,
        names: Map<String, String>,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<SocialRecord> {
        val days = HashMap<String, Long>()
        for (record in log) days[record.key] = maxOf(days[record.key] ?: record.epochDay, record.epochDay)
        val best = HashMap<String, SocialRecord>()
        for ((key, entry) in sets) {
            if (entry.weight <= 0 || entry.reps <= 0) continue
            val parts = key.split("|")
            if (parts.size < 3) continue
            val day = days[parts[0]] ?: continue
            val id = parts[1]
            val name = names[id] ?: if (isGenerated(id)) continue else id
            val candidate = SocialRecord(
                name = name,
                weight = entry.weight,
                reps = entry.reps,
                estimate = estimate(entry.weight, entry.reps),
                date = LocalDate.ofEpochDay(day).atStartOfDay(zone).toInstant().toEpochMilli().toDouble(),
                lift = lift(name)?.raw
            )
            val group = ProgramProfile.weightStepKey(name)
            val current = best[group]
            if (current != null && !isBetter(candidate, current)) continue
            best[group] = candidate
        }
        return best.values.sortedWith(order).take(LIMIT)
    }

    /// Лучший в движении для витрины: у приседа это может быть и вариант приседа.
    fun best(records: List<SocialRecord>, lift: Lift): SocialRecord? =
        records.filter { it.lift == lift.raw }.sortedWith(order).firstOrNull()

    /// Сумма троеборья по расчётным максимумам — только когда есть все три движения.
    fun total(records: List<SocialRecord>): Double? {
        val bests = Lift.entries.mapNotNull { best(records, it) }
        if (bests.size != Lift.entries.size) return null
        return bests.sumOf { it.estimate }
    }

    /// Больше расчётный максимум; при равном — тяжелее вес, потом раньше поставлен.
    fun isBetter(lhs: SocialRecord, rhs: SocialRecord): Boolean = order.compare(lhs, rhs) < 0

    private val order: Comparator<SocialRecord> =
        compareByDescending<SocialRecord> { it.estimate }
            .thenByDescending { it.weight }
            .thenBy { it.date }
            .thenBy { it.name }

    /// Название по идентификатору упражнения: у программы он и есть название,
    /// у своих и добавленных — случайный, его ищем в правках и своей программе.
    fun names(profile: ProgramProfile): Map<String, String> {
        val map = HashMap<String, String>()
        for (edit in profile.planEdits) {
            if (edit.kind == PlanEditKind.ADD || edit.kind == PlanEditKind.REPLACE) map[edit.id] = edit.name
        }
        profile.customProgram?.days?.forEach { day -> day.exercises.forEach { map[it.id] = it.name } }
        profile.workoutPlan.weeks.forEach { week -> week.days.forEach { day -> day.exercises.forEach { map[it.key] = it.name } } }
        return map
    }

    private fun isGenerated(id: String): Boolean = id.startsWith("custom-") || id.startsWith("own-")

    /// Для подписи «12 окт»: дата из миллисекунд, по своему часовому поясу.
    fun localDate(record: SocialRecord, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        java.time.Instant.ofEpochMilli(record.date.roundToLong()).atZone(zone).toLocalDate()
}
