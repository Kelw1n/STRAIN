package com.texasprogram.app.model

import com.texasprogram.app.service.RuDate
import com.texasprogram.app.service.RuPlural
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// MARK: - Челленджи с друзьями
//
// Сервер хранит челлендж и счёт участников, а сам счёт считает приложение:
// вся история тренировок живёт на телефоне. Правила счёта совпадают с iOS
// до единицы — одна и та же история даёт один и тот же счёт.

enum class ChallengeKind(val key: String, val title: String, val subtitle: String, val rules: String) {
    WORKOUTS(
        "workouts", "Больше тренировок", "Кто чаще придёт в зал",
        "Каждая отмеченная тренировка за время челленджа — одно очко."
    ),
    VOLUME(
        "volume", "Больше тоннаж", "Кто поднимет больше килограммов за всё время",
        "Складываются килограммы всех рабочих подходов: вес × повторения."
    ),
    STREAK(
        "streak", "Без пропусков", "Цель недели каждую неделю — кто дольше продержится",
        "Неделя засчитывается, если выполнена цель недели. Первая неделя без цели — серия остановилась. При равенстве выше тот, у кого больше тренировок."
    ),
    STRENGTH(
        "strength", "Рост силы", "Прибавка в силе к весу тела — честно для лёгких и тяжёлых",
        "Сравнивается лучший расчётный разовый максимум в каждом движении до старта и за время челленджа. Прибавка делится на вес тела — поэтому запиши вес в «Замерах веса»."
    );

    companion object {
        fun of(key: String): ChallengeKind? = entries.firstOrNull { it.key == key }
    }
}

@Serializable
data class SocialChallenge(
    val id: String,
    /// Вид строкой: челлендж нового вида из будущей версии не ломает список.
    val kind: String = "",
    val weeks: Int = 1,
    val startsAt: Double = 0.0,
    val endsAt: Double = 0.0,
    val status: String = "active",
    val owner: SocialUserShort? = null,
    val myStatus: String? = null,
    val members: List<Member> = emptyList()
) {
    @Serializable
    data class Member(
        val user: SocialUserShort,
        val status: String = "invited",
        val score: Double = 0.0,
        val detail: String = "",
        val place: Int? = null
    )

    val challengeKind: ChallengeKind? get() = ChallengeKind.of(kind)
    val title: String get() = challengeKind?.title ?: "Челлендж"
    val joined: List<Member> get() = members.filter { it.status == "joined" }
    val invited: List<Member> get() = members.filter { it.status == "invited" }
    val me: Member? get() = members.firstOrNull { it.user.isMe }
    val isInvite: Boolean get() = myStatus == "invited" && status != "finished"
    val isFinished: Boolean get() = status == "finished"

    /// Местный день начала: полночь, с которой считается окно челленджа.
    fun startDay(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(startsAt.toLong()).atZone(zone).toLocalDate()

    fun endDay(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(endsAt.toLong() - 1).atZone(zone).toLocalDate()

    /// Сутки после конца сервер ещё принимает счёт — потом места окончательные.
    fun isFinal(nowMs: Long = System.currentTimeMillis()): Boolean = nowMs > endsAt + ChallengeScoring.GRACE_MS

    /// «Осталось 5 дней», «Начнётся завтра», «Закончился 3 окт.».
    fun timeLine(nowMs: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
        if (isFinished) return "Закончился ${RuDate.shortDayMonth(endDay(zone))}"
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        if (nowMs < startsAt) {
            val days = ChronoUnit.DAYS.between(today, startDay(zone)).toInt()
            return if (days <= 1) "Начнётся завтра" else "Начнётся через $days ${RuPlural.word(days, "день", "дня", "дней")}"
        }
        val end = Instant.ofEpochMilli(endsAt.toLong()).atZone(zone).toLocalDate()
        val left = max(1, ChronoUnit.DAYS.between(today, end).toInt())
        return "Осталось $left ${RuPlural.word(left, "день", "дня", "дней")}"
    }
}

/// Счёт для сервера: один запрос сразу по всем своим челленджам.
@Serializable
data class ChallengeScoreUpdate(val id: String, val score: Double, val detail: String)

/// Победитель прошлой недели в рейтинге.
@Serializable
data class SocialWeekWinner(
    val week: String,
    val user: SocialUserShort,
    val workouts: Int = 0,
    val volumeKg: Double = 0.0
)

/// Опыт за место в законченном челлендже — лежит в профиле и едет в копии.
@Serializable
data class ChallengeAward(val id: String, val title: String = "", val place: Int = 0, val xp: Int = 0, val epochDay: Long = 0)

// MARK: - Счёт

object ChallengeScoring {
    const val GRACE_MS = 24 * 60 * 60 * 1000.0

    data class Result(val score: Double, val detail: String)

    /// Опыт за место: первое +300, второе +200, третье +100, остальным за участие +50.
    /// Нужны хотя бы двое участников и ненулевой счёт.
    fun xp(place: Int?, participants: Int, score: Double): Int {
        if (participants < 2 || score <= 0 || place == null) return 0
        return when (place) {
            1 -> 300
            2 -> 200
            3 -> 100
            else -> 50
        }
    }

    fun result(challenge: SocialChallenge, profile: ProgramProfile, today: LocalDate = LocalDate.now()): Result? {
        val kind = challenge.challengeKind ?: return null
        return result(
            kind = kind,
            log = profile.completionLog,
            sets = profile.setLog,
            tonnage = { profile.tonnage(it) },
            bodyWeight = profile.latestBodyWeight,
            weeklyGoal = Progression.weeklyGoal(profile),
            startDay = challenge.startDay(),
            weeks = challenge.weeks,
            today = today
        )
    }

    /// Счёт за окно челленджа: с дня начала на `weeks` недель.
    fun result(
        kind: ChallengeKind,
        log: List<CompletionRecord>,
        sets: Map<String, SetEntry>,
        tonnage: (String) -> Double,
        bodyWeight: Double?,
        weeklyGoal: Int,
        startDay: LocalDate,
        weeks: Int,
        today: LocalDate = LocalDate.now()
    ): Result {
        val length = max(1, weeks) * 7
        val start = startDay.toEpochDay()
        // Повтор ключа — та же тренировка: берём последнюю дату.
        val latest = HashMap<String, Long>()
        for (record in log) latest[record.key] = max(latest[record.key] ?: record.epochDay, record.epochDay)
        val days = latest.mapValues { (_, day) -> (day - start).toInt() }
        val inWindow = days.filter { it.value in 0 until length }
        val count = inWindow.size

        return when (kind) {
            ChallengeKind.WORKOUTS ->
                Result(count.toDouble(), "$count ${RuPlural.word(count, "тренировка", "тренировки", "тренировок")}")

            ChallengeKind.VOLUME -> {
                val volume = inWindow.keys.sorted().fold(0.0) { sum, key -> sum + tonnage(key) }
                val score = Math.round(volume * 10) / 10.0
                Result(score, if (score >= 1000) "${decimal(score / 1000)} т" else "${Math.round(score)} кг")
            }

            ChallengeKind.STREAK -> {
                val goal = min(3, max(1, weeklyGoal))
                val todayIndex = (today.toEpochDay() - start).toInt()
                var run = 0
                for (week in 0 until max(1, weeks)) {
                    if (week * 7 > todayIndex) break
                    val done = inWindow.values.count { it >= week * 7 && it < week * 7 + 7 }
                    if (done < goal) break
                    run += 1
                }
                Result((run * 100 + count).toDouble(), "$run из ${max(1, weeks)} нед.")
            }

            ChallengeKind.STRENGTH -> {
                // Лучший расчётный разовый максимум в каждом движении: до начала и за окно.
                val before = HashMap<String, Double>()
                val during = HashMap<String, Double>()
                for ((key, entry) in sets) {
                    if (entry.reps <= 0 || entry.reps > 12 || entry.weight <= 0) continue
                    val parts = key.split("|")
                    if (parts.size < 3) continue
                    val day = days[parts[0]] ?: continue
                    if (day >= length) continue
                    val exercise = parts[1]
                    val max1 = if (entry.reps == 1) entry.weight else entry.weight * (1 + entry.reps.toDouble() / 30)
                    val target = if (day < 0) before else during
                    target[exercise] = max(target[exercise] ?: 0.0, max1)
                }
                var gain = 0.0
                for (exercise in during.keys.sorted()) {
                    val base = before[exercise] ?: continue
                    val best = during[exercise] ?: continue
                    gain += max(0.0, best - base)
                }
                if (bodyWeight == null || bodyWeight <= 0) {
                    Result(0.0, "+${decimal(gain)} кг · нет веса тела")
                } else {
                    val score = Math.round(gain / bodyWeight * 1000) / 10.0
                    Result(score, "+${decimal(gain)} кг · ${decimal(score)}% веса")
                }
            }
        }
    }

    /// Одна цифра после запятой, без «,0»: одинаково на Android и iPhone.
    fun decimal(value: Double): String {
        val tenths = Math.round(value * 10)
        val whole = tenths / 10
        val fraction = abs(tenths % 10)
        return if (fraction == 0L) "$whole" else "$whole,$fraction"
    }
}
