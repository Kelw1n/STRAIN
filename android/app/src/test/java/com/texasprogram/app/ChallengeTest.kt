package com.texasprogram.app

import com.texasprogram.app.data.BackupService
import com.texasprogram.app.model.ChallengeAward
import com.texasprogram.app.model.ChallengeKind
import com.texasprogram.app.model.ChallengeScoring
import com.texasprogram.app.model.CompletionRecord
import com.texasprogram.app.model.ProgramInput
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.Progression
import com.texasprogram.app.model.SetEntry
import com.texasprogram.app.model.SocialChallenge
import com.texasprogram.app.service.SocialService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/// Счёт челленджей: та же история, что в тесте iOS, — счёт обязан совпасть.
class ChallengeTest {
    private fun day(month: Int, day: Int) = LocalDate.of(2026, month, day)

    private val log = listOf(
        CompletionRecord("1-1", day(10, 5).toEpochDay(), 1, 1),
        CompletionRecord("1-2", day(10, 7).toEpochDay(), 1, 2),
        // Повтор той же тренировки: считается один раз, по последней дате.
        CompletionRecord("1-3", day(10, 8).toEpochDay(), 1, 3),
        CompletionRecord("1-3", day(10, 9).toEpochDay(), 1, 3),
        CompletionRecord("2-1", day(10, 12).toEpochDay(), 2, 1),
        CompletionRecord("c2-1-1", day(10, 26).toEpochDay(), 1, 1)
    )

    private val sets = mapOf(
        "1-1|Приседания|0" to SetEntry(5, 100.0),
        "1-1|Приседания|1" to SetEntry(5, 100.0),
        "1-1|Жим лёжа|0" to SetEntry(5, 80.0),
        "1-2|Приседания|0" to SetEntry(5, 102.5),
        "2-1|Жим лёжа|0" to SetEntry(3, 85.0),
        "2-1|Жим лёжа|1" to SetEntry(0, 85.0),
        "c2-1-1|Приседания|0" to SetEntry(5, 100.0),
        "3-1|Приседания|0" to SetEntry(5, 200.0)
    )

    private fun tonnage(key: String) = sets.filterKeys { it.startsWith("$key|") }.values.sumOf { it.reps * it.weight }

    private fun result(kind: ChallengeKind, start: LocalDate, weeks: Int, bodyWeight: Double? = 80.0) =
        ChallengeScoring.result(kind, log, sets, ::tonnage, bodyWeight, 3, start, weeks, day(10, 20))

    @Test
    fun scoresMatchIphone() {
        val start = day(10, 5)
        assertEquals(ChallengeScoring.Result(4.0, "4 тренировки"), result(ChallengeKind.WORKOUTS, start, 2))
        assertEquals(ChallengeScoring.Result(2167.5, "2,2 т"), result(ChallengeKind.VOLUME, start, 2))
        // Первая неделя — три тренировки из трёх, вторая — одна: серия остановилась.
        assertEquals(ChallengeScoring.Result(104.0, "1 из 2 нед."), result(ChallengeKind.STREAK, start, 2))
        // Жим: 85 × 3 после 80 × 5 — расчётный максимум вырос на 0,17 кг.
        assertEquals(ChallengeScoring.Result(0.2, "+0,2 кг · 0,2% веса"), result(ChallengeKind.STRENGTH, day(10, 12), 1))
        assertEquals(ChallengeScoring.Result(0.0, "+0,2 кг · нет веса тела"), result(ChallengeKind.STRENGTH, day(10, 12), 1, null))
        assertEquals(1.0, result(ChallengeKind.WORKOUTS, day(10, 26), 1).score, 0.0)
    }

    @Test
    fun placeExperience() {
        assertEquals(300, ChallengeScoring.xp(1, 2, 5.0))
        assertEquals(200, ChallengeScoring.xp(2, 3, 5.0))
        assertEquals(100, ChallengeScoring.xp(3, 3, 5.0))
        assertEquals(50, ChallengeScoring.xp(4, 5, 1.0))
        assertEquals("в одиночку не считается", 0, ChallengeScoring.xp(1, 1, 5.0))
        assertEquals("без единой тренировки — без опыта", 0, ChallengeScoring.xp(1, 3, 0.0))
        assertEquals("2", ChallengeScoring.decimal(2.0))
        assertEquals("12,3", ChallengeScoring.decimal(12.34))
    }

    @Test
    fun challengeDecodesWithUnknownKind() {
        val list = SocialService.json.decodeFromString<List<SocialChallenge>>(
            """[{"id":"c1","kind":"workouts","weeks":2,"startsAt":1000,"endsAt":2000,"status":"active","myStatus":"joined",
                 "owner":{"id":"u1","username":"tim"},
                 "members":[{"user":{"id":"u2","username":"sem","level":7},"status":"joined","score":3,"detail":"3 тренировки","place":1},
                            {"user":{"id":"u1","username":"tim","isMe":true},"status":"joined","score":2,"detail":"2 тренировки","place":2},
                            {"user":{"id":"u3","username":"new"},"status":"invited"}]},
                {"id":"c2","kind":"future","weeks":1,"startsAt":1000,"endsAt":2000,"status":"finished","members":[]}]"""
        )
        assertEquals(2, list.size)
        assertEquals(ChallengeKind.WORKOUTS, list[0].challengeKind)
        assertEquals(2, list[0].me?.place)
        assertEquals(2, list[0].joined.size)
        assertEquals(listOf("new"), list[0].invited.map { it.user.username })
        assertEquals(7, list[0].members[0].user.level)
        assertNull("челлендж нового вида не ломает список", list[1].challengeKind)
        assertEquals("Челлендж", list[1].title)
    }

    @Test
    fun awardsAddExperienceAndTravelInBackup() {
        val profile = ProgramProfile.texas(ProgramInput.demo)
        val base = Progression.xp(profile)
        val awarded = profile.copy(challengeAwards = listOf(ChallengeAward("c1", "Больше тренировок", 1, 300, 20_400)))
        assertEquals(base + 300, Progression.xp(awarded))
        val archive = BackupService.parse(BackupService.export(listOf(awarded), null))
        assertEquals(300, archive.profiles.first().challengeAwards.first().xp)
        assertEquals(300, BackupService.profile(archive.profiles.first()).challengeAwards.first().xp)
    }
}
