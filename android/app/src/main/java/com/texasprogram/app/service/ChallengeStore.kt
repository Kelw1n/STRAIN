package com.texasprogram.app.service

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.texasprogram.app.model.ChallengeAward
import com.texasprogram.app.model.ChallengeScoreUpdate
import com.texasprogram.app.model.ChallengeScoring
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.Progression
import com.texasprogram.app.model.SocialChallenge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/// Свои челленджи: список с сервера, счёт после тренировки и опыт за места.
object ChallengeStore {
    var challenges by mutableStateOf<List<SocialChallenge>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /// Профиль и как дописать в него награды: после вступления в челлендж
    /// счёт уходит сразу, не дожидаясь следующей тренировки.
    private var profile: ProgramProfile? = null
    private var save: ((List<ChallengeAward>) -> Unit)? = null
    private val lock = Mutex()

    val invites: List<SocialChallenge> get() = challenges.filter { it.isInvite }
    val running: List<SocialChallenge> get() = challenges.filter { it.myStatus == "joined" && !it.isFinished }
    val finished: List<SocialChallenge> get() = challenges.filter { it.myStatus == "joined" && it.isFinished }

    /// Снимки экранов: список задан заранее, сервер не спрашиваем.
    private var preview = false

    internal fun setForPreview(list: List<SocialChallenge>) {
        preview = true
        challenges = list
        loaded = true
    }

    suspend fun refresh() {
        if (preview) return
        if (!SocialService.isSignedIn) {
            challenges = emptyList()
            loaded = true
            return
        }
        try {
            challenges = SocialService.challenges()
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Не удалось загрузить челленджи"
        }
        loaded = true
        SocialService.challengeInvites = invites.size
    }

    fun replace(challenge: SocialChallenge) {
        challenges = if (challenges.any { it.id == challenge.id }) {
            challenges.map { if (it.id == challenge.id) challenge else it }
        } else {
            listOf(challenge) + challenges
        }
        SocialService.challengeInvites = invites.size
    }

    fun remove(id: String) {
        challenges = challenges.filterNot { it.id == id }
        SocialService.challengeInvites = invites.size
    }

    suspend fun syncAgain() {
        val current = profile
        val update = save
        if (current != null && update != null) sync(current, update) else refresh()
    }

    /// При открытии приложения и после каждой тренировки: свой счёт по идущим
    /// челленджам — на сервер, опыт за места в законченных — в профиль.
    /// Награды дописываются к свежему профилю, а не к этому снимку: пока шёл
    /// запрос, в профиле могли отметить подход.
    suspend fun sync(profile: ProgramProfile, onAwards: (List<ChallengeAward>) -> Unit) = lock.withLock {
        this.profile = profile
        save = onAwards
        if (!SocialService.isSignedIn) return@withLock
        refresh()
        val now = System.currentTimeMillis()
        val updates = challenges.mapNotNull { challenge ->
            if (challenge.myStatus != "joined" || now < challenge.startsAt || now >= challenge.endsAt + ChallengeScoring.GRACE_MS) return@mapNotNull null
            val result = ChallengeScoring.result(challenge, profile) ?: return@mapNotNull null
            val mine = challenge.me
            if (mine != null && mine.score == result.score && mine.detail == result.detail) return@mapNotNull null
            ChallengeScoreUpdate(challenge.id, result.score, result.detail)
        }
        if (updates.isNotEmpty() && runCatching { SocialService.postChallengeScores(updates) }.isSuccess) refresh()
        award(profile, now, onAwards)
    }

    /// Опыт за место — один раз за челлендж, когда места уже окончательные.
    private fun award(profile: ProgramProfile, now: Long, onAwards: (List<ChallengeAward>) -> Unit) {
        val awards = challenges.mapNotNull { challenge ->
            if (challenge.myStatus != "joined" || !challenge.isFinal(now)) return@mapNotNull null
            if (profile.challengeAwards.any { it.id == challenge.id }) return@mapNotNull null
            val mine = challenge.me ?: return@mapNotNull null
            val xp = ChallengeScoring.xp(mine.place, challenge.joined.size, mine.score)
            if (xp <= 0) return@mapNotNull null
            ChallengeAward(
                id = challenge.id,
                title = challenge.title,
                place = mine.place ?: 0,
                xp = xp,
                epochDay = Math.floorDiv(challenge.endsAt.toLong(), 86_400_000L)
            )
        }
        if (awards.isEmpty()) return
        val before = Progression.xp(profile)
        this.profile = profile.copy(challengeAwards = profile.challengeAwards + awards)
        onAwards(awards)
        ProgressionEvents.celebrate(before, before + awards.sumOf { it.xp })
    }
}
