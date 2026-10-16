package com.texasprogram.app.model

import com.texasprogram.app.service.MuscleCategory
import com.texasprogram.app.service.RuDate
import com.texasprogram.app.service.RuPlural
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

// MARK: - Лента STRAIN: то, что приходит с сервера и уходит на него
//
// Поля совпадают с ответами server/social.js и с iOS-версией: сервер хранит
// тренировку и программы как есть, поэтому имена ключей — общий договор.
// Все поля с умолчаниями: старая или чужая версия могла поле не прислать.

@Serializable
data class SocialAuthor(
    val id: String,
    val username: String,
    val avatar: String? = null
)

@Serializable
data class SocialUserShort(
    val id: String,
    val username: String,
    val avatar: String? = null,
    val bio: String = "",
    val isFollowing: Boolean = false,
    val isMe: Boolean = false
)

/// Медаль на показ: какое достижение и на каком уровне.
@Serializable
data class SocialBadge(val id: String, val level: Int)

/// Тренировочный день программы в профиле.
@Serializable
data class SocialProgramDay(
    val title: String = "Тренировка",
    val groups: List<String> = emptyList(),
    val exercises: List<Exercise> = emptyList()
) {
    @Serializable
    data class Exercise(val name: String, val sets: Int, val reps: String, val load: String)

    val setCount: Int get() = exercises.sumOf { it.sets }
}

@Serializable
data class SocialUser(
    val id: String,
    val username: String,
    val bio: String = "",
    val heightCm: Int? = null,
    val weightKg: Double? = null,
    val showBody: Boolean = true,
    val avatar: String? = null,
    val cover: String? = null,
    val programs: List<SocialProgramDay> = emptyList(),
    val achievements: List<SocialBadge> = emptyList(),
    val workouts: Int = 0,
    val followers: Int = 0,
    val following: Int = 0,
    val isFollowing: Boolean = false,
    val isMe: Boolean = false
) {
    /// «178 см · 75 кг» — то, что видно под юзернеймом.
    val bodyLine: String?
        get() {
            val parts = listOfNotNull(heightCm?.let { "$it см" }, weightKg?.let { "${formatWeight(it)} кг" })
            return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        }
}

@Serializable
data class SocialWorkout(
    val program: String? = null,
    val exercises: List<Exercise> = emptyList()
) {
    @Serializable
    data class SetLine(val reps: Int = 0, val weight: Double = 0.0, val record: Boolean = false) {
        /// «80 кг × 5», а без веса — «12 повт.».
        val text: String get() = if (weight > 0) "${formatWeight(weight)} кг × $reps" else "$reps повт."
    }

    @Serializable
    data class Exercise(val name: String = "Упражнение", val sets: List<SetLine> = emptyList()) {
        /// Лучший подход — по нему пост коротко рассказывает об упражнении.
        val best: SetLine? get() = sets.maxWithOrNull(compareBy({ it.weight }, { it.reps }))
        val hasRecord: Boolean get() = sets.any { it.record }
    }
}

@Serializable
data class SocialPost(
    val id: String,
    val author: SocialAuthor,
    val createdAt: Double = 0.0,
    val title: String = "Тренировка",
    val description: String = "",
    val durationSeconds: Int = 0,
    val volumeKg: Double = 0.0,
    val sets: Int = 0,
    val effort: Int? = null,
    val photos: List<String> = emptyList(),
    val workout: SocialWorkout = SocialWorkout(),
    val likes: Int = 0,
    val comments: Int = 0,
    val liked: Boolean = false
) {
    val instant: Instant get() = Instant.ofEpochMilli(createdAt.toLong())

    /// Плотность — сколько килограммов в минуту.
    val density: Double get() = if (durationSeconds >= 60) volumeKg / (durationSeconds / 60.0) else 0.0
}

@Serializable
data class SocialFeedPage(val posts: List<SocialPost> = emptyList(), val next: Double? = null)

@Serializable
data class SocialComment(
    val id: String,
    val postId: String = "",
    val author: SocialAuthor,
    val text: String = "",
    val createdAt: Double = 0.0,
    /// Корневой комментарий ветки, если это ответ.
    val replyTo: String? = null,
    /// Кому отвечают — «@имя» перед текстом ответа.
    val replyToUsername: String? = null
) {
    val instant: Instant get() = Instant.ofEpochMilli(createdAt.toLong())
    val isReply: Boolean get() = replyTo != null

    companion object {
        /// Корневые комментарии по порядку, под каждым — его ответы.
        fun threads(comments: List<SocialComment>): List<SocialCommentThread> {
            val ids = comments.map { it.id }.toSet()
            val replies = mutableMapOf<String, MutableList<SocialComment>>()
            val roots = mutableListOf<SocialComment>()
            for (comment in comments) {
                // Ответ на удалённый корень показываем как обычный комментарий.
                val root = comment.replyTo
                if (root != null && root in ids) replies.getOrPut(root) { mutableListOf() }.add(comment) else roots.add(comment)
            }
            return roots.map { SocialCommentThread(it, replies[it.id].orEmpty()) }
        }
    }
}

/// Комментарий и ответы под ним.
data class SocialCommentThread(val root: SocialComment, val replies: List<SocialComment>)

// MARK: - Личные сообщения

@Serializable
data class SocialMessage(
    val id: String,
    val text: String = "",
    val fromMe: Boolean = false,
    val createdAt: Double = 0.0,
    val read: Boolean = false,
    /// Фото в сообщении — id картинки на сервере.
    val image: String? = null
) {
    val instant: Instant get() = Instant.ofEpochMilli(createdAt.toLong())
    val isLocal: Boolean get() = id.startsWith("local-")
    /// Подпись в списке переписок: фото без текста — «Фото».
    val preview: String get() = if (text.isEmpty() && image != null) "📷 Фото" else text
}

/// Переписка в списке: с кем, последнее сообщение и сколько не прочитано.
@Serializable
data class SocialChat(val user: SocialUserShort, val last: SocialMessage, val unread: Int = 0)

@Serializable
data class SocialChatList(val chats: List<SocialChat> = emptyList(), val unread: Int = 0)

@Serializable
data class SocialChatPage(val user: SocialUserShort, val messages: List<SocialMessage> = emptyList(), val next: Double? = null)

/// Пост, который ещё не ушёл на сервер: лежит на телефоне вместе с фото
/// и отправляется, как только появится связь.
@Serializable
data class SocialPostDraft(
    val clientId: String,
    val createdAtMillis: Long,
    val title: String,
    val description: String,
    val durationSeconds: Int,
    val volumeKg: Double,
    val sets: Int,
    val effort: Int? = null,
    val workout: SocialWorkout,
    val photoCount: Int = 0,
    /// Уже загруженные фото: после обрыва связи их не грузим второй раз.
    val uploadedPhotos: List<String?> = emptyList()
)

// MARK: - Подписи

object SocialFormat {
    /// «только что», «5 минут назад», «вчера», «12 окт».
    fun ago(instant: Instant, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        val seconds = maxOf(0L, ChronoUnit.SECONDS.between(instant, now))
        if (seconds < 60) return "только что"
        val minutes = (seconds / 60).toInt()
        if (minutes < 60) return "$minutes ${RuPlural.word(minutes, "минуту", "минуты", "минут")} назад"
        val date = instant.atZone(zone).toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        val hours = minutes / 60
        if (hours < 24 && date == today) return "$hours ${RuPlural.word(hours, "час", "часа", "часов")} назад"
        if (date == today.minusDays(1)) return "вчера"
        val days = ChronoUnit.DAYS.between(date, today).toInt()
        if (days < 7) return "$days ${RuPlural.word(days, "день", "дня", "дней")} назад"
        return RuDate.shortDayMonth(date)
    }

    /// «1ч 48м», «45м».
    fun duration(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return if (hours > 0) "${hours}ч ${minutes}м" else "${maxOf(minutes, if (seconds > 0) 1 else 0)}м"
    }

    /// «1 470» — с узким пробелом между тысячами.
    fun grouped(value: Double): String {
        val rounded = value.roundToLong()
        val digits = kotlin.math.abs(rounded).toString()
        val out = StringBuilder()
        digits.forEachIndexed { index, c ->
            if (index > 0 && (digits.length - index) % 3 == 0) out.append(' ')
            out.append(c)
        }
        return (if (rounded < 0) "−" else "") + out
    }

    fun normalizedUsername(raw: String): String = raw.trim().replace("@", "").lowercase()

    private val usernamePattern = Regex("^[a-z0-9_.]{3,24}$")
    fun isValidUsername(value: String): Boolean = usernamePattern.matches(value)
}

// MARK: - Достижения
//
// Восемь медалей по пять уровней. Всё считается на телефоне из того, что уже
// записано; на сервер уходят только итоговые уровни — их видят в профиле.

enum class AchievementGroup(val title: String) {
    VOLUME("ОБЪЁМ И СЧЁТ"),
    RECORDS("РЕКОРДЫ И ПОСТОЯНСТВО"),
    STRENGTH("ОТНОСИТЕЛЬНАЯ СИЛА")
}

enum class AchievementKind(
    val id: String,
    val title: String,
    val group: AchievementGroup,
    val thresholds: List<Double>,
    val caption: String
) {
    PATH("path", "Путь", AchievementGroup.VOLUME, listOf(1.0, 15.0, 50.0, 100.0, 250.0), "тренировок всего"),
    IRON("iron", "Гора железа", AchievementGroup.VOLUME, listOf(1_000.0, 30_000.0, 100_000.0, 500_000.0, 1_000_000.0), "тоннаж за всё время"),
    RECORDS("records", "Охота за рекордами", AchievementGroup.RECORDS, listOf(1.0, 5.0, 20.0, 50.0, 100.0), "личных рекордов всего"),
    STREAK("streak", "Без пропусков", AchievementGroup.RECORDS, listOf(2.0, 4.0, 8.0, 16.0, 52.0), "недель подряд, лучшая серия"),
    BENCH("bench", "Жим", AchievementGroup.STRENGTH, listOf(0.75, 1.0, 1.25, 1.5, 2.0), "жим лёжа ÷ свой вес"),
    SQUAT("squat", "Присед", AchievementGroup.STRENGTH, listOf(1.0, 1.25, 1.5, 2.0, 2.5), "присед ÷ свой вес"),
    DEADLIFT("deadlift", "Тяга", AchievementGroup.STRENGTH, listOf(1.25, 1.5, 2.0, 2.5, 3.0), "становая ÷ свой вес"),
    TOTAL("total", "Троеборец", AchievementGroup.STRENGTH, listOf(3.0, 4.0, 5.0, 6.0, 7.0), "сумма трёх ÷ свой вес");

    val isRatio: Boolean get() = group == AchievementGroup.STRENGTH

    fun format(value: Double): String = when (this) {
        IRON -> if (value < 1000) "${value.roundToLong()} кг" else formatWeight(Math.round(value / 100.0) / 10.0).replace('.', ',') + " т"
        BENCH, SQUAT, DEADLIFT, TOTAL -> "×" + formatWeight(Math.round(value * 100.0) / 100.0).replace('.', ',')
        else -> "${value.roundToLong()}"
    }

    companion object {
        fun byId(id: String): AchievementKind? = entries.firstOrNull { it.id == id }
    }
}

data class AchievementProgress(
    val kind: AchievementKind,
    val value: Double,
    /// Для силы к весу без замера веса считать не от чего.
    val needsBodyWeight: Boolean
) {
    /// Взятые уровни, 0…5.
    val level: Int get() = kind.thresholds.count { value >= it - 1e-9 }
    val isMaxed: Boolean get() = level >= kind.thresholds.size
    val nextThreshold: Double? get() = if (isMaxed) null else kind.thresholds[level]

    /// «0 из 15 · тренировок всего».
    val caption: String
        get() {
            if (needsBodyWeight) return "Запиши вес тела · ${kind.caption}"
            val next = nextThreshold ?: return "${kind.format(value)} · все уровни взяты"
            return "${kind.format(value)} из ${kind.format(next)} · ${kind.caption}"
        }

    /// Доля пути до следующего уровня внутри текущего отрезка шкалы, 0…1.
    val segmentFraction: Double
        get() {
            val next = nextThreshold ?: return 1.0
            val start = if (level == 0) 0.0 else kind.thresholds[level - 1]
            if (next <= start) return 0.0
            return ((value - start) / (next - start)).coerceIn(0.0, 1.0)
        }
}

object AchievementCalculator {
    val squatNames = listOf("Приседания", "Приседания со штангой")
    val benchNames = listOf("Жим лёжа")
    val deadliftNames = listOf("Становая тяга")

    fun progress(profile: ProgramProfile): List<AchievementProgress> {
        val bodyWeight = profile.latestBodyWeight
        val squat = best(profile, squatNames, profile.squat5RM)
        val bench = best(profile, benchNames, profile.bench5RM)
        val deadlift = best(profile, deadliftNames, profile.deadlift5RM)
        fun ratio(lift: Double) = if (bodyWeight != null && bodyWeight > 0) lift / bodyWeight else 0.0

        return AchievementKind.entries.map { kind ->
            val value = when (kind) {
                AchievementKind.PATH -> profile.completionLog.size.toDouble()
                AchievementKind.IRON -> profile.setLog.values.sumOf { it.tonnage }
                AchievementKind.RECORDS -> recordCount(profile).toDouble()
                AchievementKind.STREAK -> bestWeekStreak(profile.completionLog.map { LocalDate.ofEpochDay(it.epochDay) }).toDouble()
                AchievementKind.BENCH -> ratio(bench)
                AchievementKind.SQUAT -> ratio(squat)
                AchievementKind.DEADLIFT -> ratio(deadlift)
                AchievementKind.TOTAL -> ratio(squat + bench + deadlift)
            }
            AchievementProgress(kind, value, kind.isRatio && bodyWeight == null)
        }
    }

    /// Медали на показ: три самых высоких взятых уровня.
    fun badges(progress: List<AchievementProgress>, limit: Int = 3): List<SocialBadge> =
        progress.filter { it.level > 0 }
            .sortedWith(compareByDescending<AchievementProgress> { it.level }.thenBy { it.kind.ordinal })
            .take(limit)
            .map { SocialBadge(it.kind.id, it.level) }

    /// Лучший поднятый вес: из записанных подходов или из максимума,
    /// с которого начата программа, — он тоже был поднят по-настоящему.
    fun best(profile: ProgramProfile, names: List<String>, floor: Double): Double {
        var best = floor
        for (name in names) for (point in profile.history(name)) best = maxOf(best, point.best)
        return best
    }

    /// Рекорд — тренировка, где лучший вес движения выше всех прошлых.
    /// Первая тренировка движения рекордом не считается: побить ещё нечего.
    fun recordCount(profile: ProgramProfile): Int {
        var count = 0
        for (name in profile.loggedExerciseNames) {
            var top: Double? = null
            for (point in profile.history(name)) {
                if (point.best <= 0) continue
                val current = top
                if (current == null) top = point.best
                else if (point.best > current + 1e-9) {
                    count++
                    top = point.best
                }
            }
        }
        return count
    }

    /// Самая длинная серия календарных недель (пн–вс) с тренировкой в каждой.
    fun bestWeekStreak(dates: List<LocalDate>): Int {
        val weeks = dates.map { RuDate.mondayOf(it) }.toSortedSet()
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        for (week in weeks) {
            run = if (previous != null && previous.plusWeeks(1) == week) run + 1 else 1
            best = maxOf(best, run)
            previous = week
        }
        return best
    }
}

// MARK: - Программа для профиля

object SocialProgramBuilder {
    /// Дни текущей недели программы: название, две главные группы мышц и упражнения.
    fun days(profile: ProgramProfile): List<SocialProgramDay> {
        val plan = profile.workoutPlan
        val week = plan.weeks.firstOrNull { it.number == profile.currentWeek } ?: plan.weeks.firstOrNull() ?: return emptyList()
        return week.days.map { day ->
            val sets = mutableMapOf<String, Int>()
            for (exercise in day.exercises) {
                val group = MuscleCategory.categorize(exercise.name).shortName
                sets[group] = (sets[group] ?: 0) + exercise.sets
            }
            val groups = sets.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(2)
                .map { it.key }
            SocialProgramDay(
                title = day.title,
                groups = groups,
                exercises = day.exercises.map { SocialProgramDay.Exercise(it.name, it.sets, it.reps, it.load.displayText) }
            )
        }
    }
}
