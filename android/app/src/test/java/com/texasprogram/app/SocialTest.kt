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
}
