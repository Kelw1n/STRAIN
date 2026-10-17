package com.texasprogram.app

import com.texasprogram.app.data.BackupService
import com.texasprogram.app.model.AchievementCalculator
import com.texasprogram.app.model.AchievementKind
import com.texasprogram.app.model.AchievementProgress
import com.texasprogram.app.model.ProgramInput
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.SocialFormat
import com.texasprogram.app.model.SocialPost
import com.texasprogram.app.model.SocialProgramBuilder
import com.texasprogram.app.model.SocialUser
import com.texasprogram.app.service.SocialService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/// Лента: достижения, программа в профиле, разбор ответов сервера и подписи.
class SocialTest {

    private fun texas() = ProgramProfile.texas(ProgramInput.demo)
    private fun day(offset: Long): LocalDate = LocalDate.of(2026, 10, 12).plusDays(offset)
    private fun progress(profile: ProgramProfile, kind: AchievementKind) =
        AchievementCalculator.progress(profile).first { it.kind == kind }

    @Test
    fun firstWorkoutGivesFirstPathLevel() {
        assertEquals(0, progress(texas(), AchievementKind.PATH).level)
        val path = progress(texas().toggleCompleted(1, 1, day(0)), AchievementKind.PATH)
        assertEquals(1, path.level)
        assertEquals(15.0, path.nextThreshold!!, 0.0)
        assertEquals("1 из 15 · тренировок всего", path.caption)
    }

    @Test
    fun strengthNeedsBodyWeight() {
        val profile = texas()
        val bench = progress(profile, AchievementKind.BENCH)
        assertTrue(bench.needsBodyWeight)
        val measured = progress(profile.recordBodyWeight(profile.bench5RM, day(0)), AchievementKind.BENCH)
        assertFalse(measured.needsBodyWeight)
        assertEquals(1.0, measured.value, 0.0001)
        assertEquals(2, measured.level)
    }

    /// Рекорд — превышение прошлого лучшего веса; первая тренировка движения рекордом не считается.
    @Test
    fun recordsCountOnlyImprovements() {
        val base = texas()
        val squat = base.workoutPlan.weeks[0].days[0].exercises.first()
        val later = base.workoutPlan.weeks[1].days[0].exercises.first { it.name == squat.name }
        val once = base.recordSet(1, 1, squat, 0, reps = 5, weight = 100.0)
        assertEquals(0, AchievementCalculator.recordCount(once))
        val twice = once.recordSet(2, 1, later, 0, reps = 5, weight = 105.0)
        assertEquals(1, AchievementCalculator.recordCount(twice))
    }

    @Test
    fun bestWeekStreak() {
        val dates = listOf(day(-21), day(-14), day(-12), day(-7), day(7), day(14))
        assertEquals(3, AchievementCalculator.bestWeekStreak(dates))
        assertEquals(0, AchievementCalculator.bestWeekStreak(emptyList()))
    }

    @Test
    fun badgesAreTopThreeLevels() {
        val progress = listOf(
            AchievementProgress(AchievementKind.PATH, 20.0, false),
            AchievementProgress(AchievementKind.IRON, 0.0, false),
            AchievementProgress(AchievementKind.RECORDS, 5.0, false),
            AchievementProgress(AchievementKind.STREAK, 8.0, false),
            AchievementProgress(AchievementKind.BENCH, 1.0, false)
        )
        val badges = AchievementCalculator.badges(progress)
        assertEquals(listOf("streak", "path", "records"), badges.map { it.id })
        assertEquals(3, badges.first().level)
    }

    @Test
    fun programDaysForProfile() {
        val days = SocialProgramBuilder.days(texas())
        assertEquals(3, days.size)
        assertTrue(days[0].exercises.isNotEmpty())
        assertTrue(days[0].groups.size <= 2)
        assertEquals(days[0].exercises.sumOf { it.sets }, days[0].setCount)
    }

    @Test
    fun postDecodesWithMissingFields() {
        val raw = """
            {"id":"p1","author":{"id":"u1","username":"tim"},"createdAt":1792000000000,
             "title":"Верх","volumeKg":1470,"durationSeconds":6480,"effort":null,
             "workout":{"exercises":[{"name":"Жим лёжа","sets":[{"reps":5,"weight":80},{"reps":5,"weight":85,"record":true}]}]}}
        """
        val post = SocialService.json.decodeFromString<SocialPost>(raw)
        assertEquals("", post.description)
        assertTrue(post.photos.isEmpty())
        assertEquals(0, post.likes)
        assertFalse(post.liked)
        assertNull(post.effort)
        assertEquals(85.0, post.workout.exercises.first().best!!.weight, 0.0)
        assertTrue(post.workout.exercises.first().hasRecord)
        assertEquals(1470.0 / 108, post.density, 0.01)
    }

    @Test
    fun userBodyLine() {
        val hidden = SocialService.json.decodeFromString<SocialUser>("""{"id":"u","username":"a"}""")
        assertNull(hidden.bodyLine)
        val shown = SocialService.json.decodeFromString<SocialUser>("""{"id":"u","username":"a","heightCm":178,"weightKg":75}""")
        assertEquals("178 см · 75 кг", shown.bodyLine)
    }

    /// В правку профиля уходят только заданные поля; стёртый рост — явный null.
    @Test
    fun profilePatchSendsOnlyChangedFields() {
        val patch = Json.parseToJsonElement(SocialService.ProfilePatch(bio = "Техас", clearHeight = true).toJson()).jsonObject
        assertEquals(setOf("bio", "heightCm"), patch.keys)
        assertEquals(JsonNull, patch["heightCm"])
        val empty = Json.parseToJsonElement(SocialService.ProfilePatch().toJson()).jsonObject
        assertTrue(empty.isEmpty())
    }

    @Test
    fun usernameRules() {
        assertEquals("kelw1n", SocialFormat.normalizedUsername(" @Kelw1n "))
        assertTrue(SocialFormat.isValidUsername("kelw1n"))
        assertTrue(SocialFormat.isValidUsername("tim_g.2008"))
        assertFalse(SocialFormat.isValidUsername("ab"))
        assertFalse(SocialFormat.isValidUsername("тимур"))
        assertFalse(SocialFormat.isValidUsername("with space"))
    }

    @Test
    fun agoAndDuration() {
        val zone = ZoneOffset.UTC
        val now = day(0).atTime(12, 0).toInstant(zone)
        assertEquals("только что", SocialFormat.ago(now.minusSeconds(20), now, zone))
        assertEquals("10 минут назад", SocialFormat.ago(now.minusSeconds(600), now, zone))
        assertEquals("3 часа назад", SocialFormat.ago(now.minusSeconds(3 * 3600), now, zone))
        assertEquals("вчера", SocialFormat.ago(day(-1).atTime(12, 0).toInstant(zone), now, zone))
        assertEquals("1ч 48м", SocialFormat.duration(6480))
        assertEquals("45м", SocialFormat.duration(45 * 60))
        assertEquals("1 470", SocialFormat.grouped(1470.0))
    }

    /// Копия несёт код входа и дату, которую читает и iOS-версия.
    @Test
    fun backupCarriesTokenAndIsoDate() {
        val token = "a".repeat(64)
        val archive = BackupService.parse(BackupService.export(listOf(texas()), token))
        assertEquals(token, archive.socialToken)
        // Без долей секунды: строгий разбор ISO 8601 в iOS их не принимает.
        val date = Instant.parse(archive.exportedAt)
        assertEquals(0, date.nano)
        assertNull(BackupService.parse(BackupService.export(listOf(texas()))).socialToken)
    }

    // MARK: Копирование и ответы

    @Test
    fun copyWorkoutTakesWorkingWeight() {
        val workout = SocialService.json.decodeFromString<com.texasprogram.app.model.SocialWorkout>(
            """{"exercises":[{"name":"Присед","sets":[{"reps":5,"weight":100},{"reps":3,"weight":140},{"reps":3,"weight":140}]},
               {"name":"Подтягивания","sets":[{"reps":10,"weight":0},{"reps":8,"weight":0}]}]}"""
        )
        val program = com.texasprogram.app.model.ProgramCopy.program(workout, "Тяжёлая пятница", "Пятница")
        assertEquals(1, program.days.size)
        val squat = program.days[0].exercises[0]
        assertEquals(3, squat.sets)
        assertEquals("3", squat.reps)
        assertEquals(140.0, squat.kilograms!!, 0.0)
        val pullups = program.days[0].exercises[1]
        assertNull(pullups.kilograms)
        assertEquals("свой вес", pullups.loadText)
        assertTrue(program.isRunnable)
    }

    @Test
    fun copyProgramDaysParsesLoads() {
        val copy = com.texasprogram.app.model.ProgramCopy
        assertEquals(100.0, copy.kilograms("100 кг")!!, 0.0)
        assertEquals(82.5, copy.kilograms("82,5 кг")!!, 0.0)
        assertEquals(100.0, copy.kilograms("220 lbs")!!, 0.0)
        assertNull(copy.kilograms("RPE 8"))
        val days = SocialProgramBuilder.days(texas())
        val program = copy.program(days, "Программа @tim")
        assertEquals(days.size, program.days.size)
        assertEquals((1..days.size).toList(), program.days.map { it.number })
        assertTrue(program.isRunnable)
        assertTrue(ProgramProfile.custom(program).workoutPlan.weeks.isNotEmpty())
    }

    @Test
    fun commentThreadsGroupReplies() {
        val comments = SocialService.json.decodeFromString<List<com.texasprogram.app.model.SocialComment>>(
            """[{"id":"a","author":{"id":"1","username":"a"},"text":"вопрос","createdAt":1},
               {"id":"b","author":{"id":"2","username":"b"},"text":"ответ","createdAt":2,"replyTo":"a","replyToUsername":"a"},
               {"id":"c","author":{"id":"3","username":"c"},"text":"просто","createdAt":3},
               {"id":"d","author":{"id":"1","username":"a"},"text":"спасибо","createdAt":4,"replyTo":"a","replyToUsername":"b"},
               {"id":"e","author":{"id":"1","username":"a"},"text":"сирота","createdAt":5,"replyTo":"x"}]"""
        )
        val threads = com.texasprogram.app.model.SocialComment.threads(comments)
        assertEquals(listOf("a", "c", "e"), threads.map { it.root.id })
        assertEquals(listOf("b", "d"), threads[0].replies.map { it.id })
    }

    // MARK: Переписка

    @Test
    fun reactionToggleKeepsOnePerPerson() {
        val reaction = com.texasprogram.app.model.SocialReaction
        val fire = reaction.toggled(emptyList(), "🔥")
        assertEquals(listOf(com.texasprogram.app.model.SocialReaction("🔥", 1, true)), fire)
        assertEquals(emptyList<com.texasprogram.app.model.SocialReaction>(), reaction.toggled(fire, "🔥"))
        val shared = listOf(com.texasprogram.app.model.SocialReaction("🔥", 2, true), com.texasprogram.app.model.SocialReaction("💪", 1, false))
        assertEquals(
            listOf(com.texasprogram.app.model.SocialReaction("🔥", 1, false), com.texasprogram.app.model.SocialReaction("💪", 2, true)),
            reaction.toggled(shared, "💪")
        )
    }

    @Test
    fun mergedMessagesReplaceAndRemove() {
        val message = com.texasprogram.app.model.SocialMessage
        val current = listOf(
            com.texasprogram.app.model.SocialMessage("a", "раз", fromMe = true, createdAt = 1.0),
            com.texasprogram.app.model.SocialMessage("b", "два", createdAt = 2.0)
        )
        val incoming = SocialService.json.decodeFromString<List<com.texasprogram.app.model.SocialMessage>>(
            """[{"id":"c","text":"три","createdAt":3,"replyTo":{"id":"a","text":"раз","fromMe":true}},
               {"id":"b","deleted":true,"createdAt":2,"text":""},
               {"id":"a","text":"раз","fromMe":true,"createdAt":1,"read":true,"reactions":[{"emoji":"🔥","count":1,"mine":false}]}]"""
        )
        val merged = message.merged(current, incoming)
        assertEquals(listOf("a", "c"), merged.map { it.id })
        assertTrue(merged[0].read)
        assertEquals("🔥", merged[0].reactions.first().emoji)
        assertEquals("раз", merged[1].replyTo?.preview)
    }

    @Test
    fun chatPageDecodesPresence() {
        val page = SocialService.json.decodeFromString<com.texasprogram.app.model.SocialChatPage>(
            """{"user":{"id":"u","username":"bro"},"messages":[],"next":null,"serverTime":1000,"typing":true,"lastSeen":900,
               "live":{"title":"День 1","exercise":"Присед","done":3,"total":5,"setsDone":3,"setsTotal":15,"startedAt":100}}"""
        )
        assertEquals(1000.0, page.serverTime!!, 0.0)
        assertTrue(page.typing)
        assertEquals("Присед · 3/5", page.live?.line)
        val now = Instant.ofEpochSecond(1_000_000)
        assertEquals("в сети", SocialFormat.lastSeen(now.minusSeconds(30), now, ZoneOffset.UTC))
        assertEquals("был(а) 10 минут назад", SocialFormat.lastSeen(now.minusSeconds(600), now, ZoneOffset.UTC))
    }

    // MARK: Совместная тренировка

    @Test
    fun togetherDecodes() {
        val state = SocialService.json.decodeFromString<com.texasprogram.app.model.SocialTogetherState>(
            """{"incoming":[{"id":"t1","status":"pending","fromMe":false,"partner":{"id":"2","username":"sem"},"createdAt":5}],
               "outgoing":[],
               "active":{"id":"t2","status":"accepted","fromMe":true,"partner":{"id":"3","username":"tim"},
                         "partnerLive":{"title":"День 1","exercise":"Жим","done":2,"total":4,"setsDone":6,"setsTotal":16}}}"""
        )
        assertEquals("sem", state.incoming.first().partner.username)
        assertEquals("Жим · 2/4", state.active?.partnerLive?.line)
        val message = SocialService.json.decodeFromString<com.texasprogram.app.model.SocialMessage>(
            """{"id":"m","text":"Го","createdAt":1,"invite":{"id":"t1","status":"pending","fromMe":false}}"""
        )
        assertTrue(message.invite!!.isPending)
        assertEquals("Зовёт тренироваться вместе", message.invite!!.statusText)
        val post = SocialService.json.decodeFromString<SocialPost>(
            """{"id":"p","author":{"id":"1","username":"a"},"partner":{"id":"2","username":"b"}}"""
        )
        assertEquals("b", post.partner?.username)
    }

    // MARK: Активность и рейтинг

    @Test
    fun activityDecodes() {
        val page = SocialService.json.decodeFromString<com.texasprogram.app.model.SocialActivityPage>(
            """{"items":[{"kind":"like","actor":{"id":"1","username":"tim"},"postId":"p","postTitle":"День 1","createdAt":5,"unseen":true},
                         {"kind":"follow","actor":{"id":"2","username":"sem"},"createdAt":4}],"unseen":1}"""
        )
        assertEquals(1, page.unseen)
        assertEquals(listOf("like", "follow"), page.items.map { it.kind })
        assertEquals("поставил(а) лайк тренировке «День 1»", page.items[0].action)
        assertFalse(page.items[1].unseen)
    }

    @Test
    fun weeklyStatsCountThisWeekOnly() {
        val base = texas()
        val squat = base.workoutPlan.weeks[0].days[0].exercises.first()
        val later = base.workoutPlan.weeks[1].days[0].exercises.first { it.name == squat.name }
        val logged = base.recordSet(1, 1, squat, 0, reps = 5, weight = 100.0).recordSet(2, 1, later, 0, reps = 5, weight = 105.0)
        // Отметки напрямую: «выполнено» дописало бы плановые подходы всего дня.
        val profile = logged.copy(
            completionLog = listOf(
                com.texasprogram.app.model.CompletionRecord(logged.dayKey(1, 1), day(-7).toEpochDay(), 1, 1),
                com.texasprogram.app.model.CompletionRecord(logged.dayKey(2, 1), day(0).toEpochDay(), 2, 1)
            )
        )
        val stats = com.texasprogram.app.model.WeeklyStatsBuilder.stats(profile, day(2))
        assertEquals("2026-W42", stats.week)
        assertEquals(1, stats.workouts)
        assertEquals(525.0, stats.volumeKg, 0.01)
        assertEquals(1, stats.records)
        assertEquals(2, stats.streakWeeks)
    }

    @Test
    fun currentStreakWaitsForWeekToEnd() {
        val builder = com.texasprogram.app.model.WeeklyStatsBuilder
        assertEquals(2, builder.currentStreak(listOf(day(-14), day(-7)), day(2)))
        assertEquals(2, builder.currentStreak(listOf(day(0), day(-7)), day(2)))
        assertEquals(0, builder.currentStreak(listOf(day(-14)), day(2)))
    }

    @Test
    fun leaderboardIgnoresStaleWeeks() {
        val entries = SocialService.json.decodeFromString<List<com.texasprogram.app.model.SocialLeaderboardEntry>>(
            """[{"user":{"id":"1","username":"old"},"stats":{"week":"2026-W40","workouts":5,"volumeKg":20000,"records":3,"streakWeeks":9}},
               {"user":{"id":"2","username":"last"},"stats":{"week":"2026-W41","workouts":2,"volumeKg":9000,"records":1,"streakWeeks":4}},
               {"user":{"id":"3","username":"now","isMe":true},"stats":{"week":"2026-W42","workouts":2,"volumeKg":6000,"records":2,"streakWeeks":3}},
               {"user":{"id":"4","username":"empty"},"stats":{}}]"""
        )
        val metric = com.texasprogram.app.model.LeaderboardMetric
        val byVolume = metric.ranked(entries, com.texasprogram.app.model.LeaderboardMetric.VOLUME, day(2))
        assertEquals("now", byVolume.first().user.username)
        assertTrue(byVolume.first().user.isMe)
        val byStreak = metric.ranked(entries, com.texasprogram.app.model.LeaderboardMetric.STREAK, day(2))
        assertEquals(listOf("last", "now", "empty", "old"), byStreak.map { it.user.username })
        assertEquals("6 т", com.texasprogram.app.model.LeaderboardMetric.VOLUME.format(byVolume[0].stats))
    }
}
