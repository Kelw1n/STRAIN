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
    val isMe: Boolean = false,
    /// Идёт тренировка — что делает прямо сейчас.
    val live: SocialLive? = null,
    /// Когда последний раз заходил — для «был(а) N минут назад».
    val lastSeen: Double? = null
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
    val image: String? = null,
    /// Удалено отправителем — приходит при обновлении переписки, чтобы пропасть и здесь.
    val deleted: Boolean = false,
    val reactions: List<SocialReaction> = emptyList(),
    /// На какое сообщение это ответ.
    val replyTo: SocialMessageQuote? = null
) {
    val instant: Instant get() = Instant.ofEpochMilli(createdAt.toLong())
    val isLocal: Boolean get() = id.startsWith("local-")
    /// Подпись в списке переписок: фото без текста — «Фото».
    val preview: String get() = if (text.isEmpty() && image != null) "📷 Фото" else text

    val quote: SocialMessageQuote get() = SocialMessageQuote(id, text, image, fromMe)

    companion object {
        /// Переписка после обновления с сервера: новое добавлено, изменённое
        /// (реакции, прочтение) заменено, удалённое убрано. Старые — сверху.
        fun merged(current: List<SocialMessage>, incoming: List<SocialMessage>): List<SocialMessage> {
            val byId = LinkedHashMap<String, SocialMessage>()
            for (message in current) byId[message.id] = message
            for (message in incoming) if (message.deleted) byId.remove(message.id) else byId[message.id] = message
            return byId.values.sortedWith(compareBy({ it.createdAt }, { it.id }))
        }
    }
}

@Serializable
data class SocialReaction(val emoji: String, val count: Int = 1, val mine: Boolean = false) {
    companion object {
        /// Реакции, которые можно поставить, — те же, что принимает сервер.
        val choices = listOf("🔥", "💪", "👍", "❤️", "😂", "😮")

        /// Своя реакция поставлена или снята — сразу, не дожидаясь сервера.
        fun toggled(reactions: List<SocialReaction>, emoji: String): List<SocialReaction> {
            val had = reactions.firstOrNull { it.mine }?.emoji
            val result = reactions.mapNotNull {
                if (!it.mine) it else it.copy(count = it.count - 1, mine = false).takeIf { copy -> copy.count > 0 }
            }.toMutableList()
            if (had == emoji) return result
            val index = result.indexOfFirst { it.emoji == emoji }
            if (index >= 0) result[index] = result[index].copy(count = result[index].count + 1, mine = true)
            else result.add(SocialReaction(emoji, 1, true))
            return result
        }
    }
}

/// Цитата сообщения, на которое ответили.
@Serializable
data class SocialMessageQuote(val id: String, val text: String = "", val image: String? = null, val fromMe: Boolean = false) {
    val preview: String get() = text.ifEmpty { if (image != null) "📷 Фото" else "Сообщение удалено" }
}

/// Переписка в списке: с кем, последнее сообщение и сколько не прочитано.
@Serializable
data class SocialChat(val user: SocialUserShort, val last: SocialMessage, val unread: Int = 0, val live: SocialLive? = null)

@Serializable
data class SocialChatList(val chats: List<SocialChat> = emptyList(), val unread: Int = 0)

@Serializable
data class SocialChatPage(
    val user: SocialUserShort,
    val messages: List<SocialMessage> = emptyList(),
    val next: Double? = null,
    /// Время сервера на момент ответа: с него начинается следующее обновление.
    val serverTime: Double? = null,
    val typing: Boolean = false,
    val lastSeen: Double? = null,
    val live: SocialLive? = null
)

// MARK: - Тренируется сейчас

/// Идущая тренировка друга: что за день, какое упражнение и сколько подходов.
@Serializable
data class SocialLive(
    val title: String = "",
    val exercise: String = "",
    val done: Int = 0,
    val total: Int = 0,
    val setsDone: Int = 0,
    val setsTotal: Int = 0,
    val startedAt: Double = 0.0,
    val updatedAt: Double? = null
) {
    /// «Присед · 3/5».
    val line: String get() = when {
        exercise.isEmpty() -> title
        total > 0 -> "$exercise · $done/$total"
        else -> exercise
    }
}

@Serializable
data class SocialLiveFriend(val user: SocialUserShort, val live: SocialLive)

// MARK: - Рейтинг недели

/// Сводка недели: её присылает само приложение — в ней все тренировки,
/// а не только выложенные в ленту.
@Serializable
data class SocialWeekStats(
    val week: String = "",
    val workouts: Int = 0,
    val volumeKg: Double = 0.0,
    val records: Int = 0,
    val streakWeeks: Int = 0
)

@Serializable
data class SocialLeaderboardEntry(val user: SocialUserShort, val stats: SocialWeekStats = SocialWeekStats())

// MARK: - Активность

/// Кто что сделал: лайк, комментарий, ответ или подписка.
@Serializable
data class SocialActivityItem(
    val kind: String = "like",
    val actor: SocialUserShort,
    val postId: String? = null,
    val postTitle: String? = null,
    val text: String? = null,
    val createdAt: Double = 0.0,
    val unseen: Boolean = false
) {
    val key: String get() = "$kind-${actor.id}-${postId.orEmpty()}-${createdAt.toLong()}"
    val instant: Instant get() = Instant.ofEpochMilli(createdAt.toLong())

    /// «поставил(а) лайк тренировке «День 1»».
    val action: String get() = when (kind) {
        "comment" -> "прокомментировал(а) «${postTitle.orEmpty()}»: ${text.orEmpty()}"
        "reply" -> "ответил(а) тебе: ${text.orEmpty()}"
        "follow" -> "подписался(-ась) на тебя"
        else -> "поставил(а) лайк тренировке «${postTitle.orEmpty()}»"
    }
}

@Serializable
data class SocialActivityPage(val items: List<SocialActivityItem> = emptyList(), val unseen: Int = 0)

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

    /// «в сети» или «был(а) 5 минут назад».
    fun lastSeen(instant: Instant, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String =
        if (ChronoUnit.SECONDS.between(instant, now) < 120) "в сети" else "был(а) " + ago(instant, now, zone)

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

// MARK: - Сводка недели для рейтинга

/// Неделя — с понедельника, номер — как в ISO и на iOS: «2026-W42».
object WeeklyStatsBuilder {
    fun weekKey(date: LocalDate): String =
        String.format(java.util.Locale.ROOT, "%04d-W%02d", date.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR), date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR))

    fun stats(profile: ProgramProfile, today: LocalDate = LocalDate.now()): SocialWeekStats {
        val monday = RuDate.mondayOf(today)
        val inWeek = { epochDay: Long -> epochDay >= monday.toEpochDay() && epochDay < monday.plusDays(7).toEpochDay() }
        val log = profile.completionLog
        val thisWeek = log.filter { inWeek(it.epochDay) }
        val volume = thisWeek.sumOf { profile.tonnage(it.key) }

        // Рекорд недели — тренировка этой недели, где лучший вес движения выше всех прошлых.
        val days = log.groupBy { it.key }.mapValues { (_, records) -> records.maxOf { it.epochDay } }
        var records = 0
        for (name in profile.loggedExerciseNames) {
            var top: Double? = null
            for (point in profile.history(name)) {
                if (point.best <= 0) continue
                val current = top
                if (current == null) top = point.best
                else if (point.best > current + 1e-9) {
                    top = point.best
                    val day = days[profile.dayKey(point.week, point.day)]
                    if (day != null && inWeek(day)) records++
                }
            }
        }
        return SocialWeekStats(
            week = weekKey(today),
            workouts = thisWeek.size,
            volumeKg = Math.round(volume * 10) / 10.0,
            records = records,
            streakWeeks = currentStreak(log.map { LocalDate.ofEpochDay(it.epochDay) }, today)
        )
    }

    /// Недели подряд с тренировкой, считая эту. Эта ещё пустая — серия
    /// не прервана, пока не кончилась: считаем с прошлой.
    fun currentStreak(dates: List<LocalDate>, today: LocalDate = LocalDate.now()): Int {
        val weeks = dates.map { RuDate.mondayOf(it) }.toSet()
        var cursor = RuDate.mondayOf(today)
        if (cursor !in weeks) cursor = cursor.minusWeeks(1)
        var count = 0
        while (cursor in weeks) {
            count++
            cursor = cursor.minusWeeks(1)
        }
        return count
    }

    /// Сводка из рейтинга на эту неделю: прошлонедельная — нули, а серия
    /// живёт, только если прошлая неделя была последней с тренировкой.
    fun current(stats: SocialWeekStats, today: LocalDate = LocalDate.now()): SocialWeekStats {
        val key = weekKey(today)
        if (stats.week == key) return stats
        val streak = if (stats.week == weekKey(today.minusWeeks(1)) && stats.workouts > 0) stats.streakWeeks else 0
        return SocialWeekStats(week = key, streakWeeks = streak)
    }
}

/// Чем меряемся в рейтинге.
enum class LeaderboardMetric(val title: String) {
    VOLUME("Тоннаж"), WORKOUTS("Тренировки"), STREAK("Серия"), RECORDS("Рекорды");

    fun value(stats: SocialWeekStats): Double = when (this) {
        VOLUME -> stats.volumeKg
        WORKOUTS -> stats.workouts.toDouble()
        STREAK -> stats.streakWeeks.toDouble()
        RECORDS -> stats.records.toDouble()
    }

    fun format(stats: SocialWeekStats): String = when (this) {
        VOLUME -> AchievementKind.IRON.format(stats.volumeKg)
        WORKOUTS -> "${stats.workouts} ${RuPlural.word(stats.workouts, "тренировка", "тренировки", "тренировок")}"
        STREAK -> "${stats.streakWeeks} ${RuPlural.word(stats.streakWeeks, "неделя", "недели", "недель")}"
        RECORDS -> "${stats.records} ${RuPlural.word(stats.records, "рекорд", "рекорда", "рекордов")}"
    }

    companion object {
        /// Места: больше — выше; поровну — по тоннажу, потом по имени.
        fun ranked(entries: List<SocialLeaderboardEntry>, metric: LeaderboardMetric, today: LocalDate = LocalDate.now()) =
            entries.map { it.copy(stats = WeeklyStatsBuilder.current(it.stats, today)) }
                .sortedWith(
                    compareByDescending<SocialLeaderboardEntry> { metric.value(it.stats) }
                        .thenByDescending { it.stats.volumeKg }
                        .thenBy { it.user.username }
                )
    }
}
