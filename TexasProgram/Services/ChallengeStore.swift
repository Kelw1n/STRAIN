import Foundation
import Observation

/// Свои челленджи: список с сервера, счёт после тренировки и опыт за места.
@Observable
@MainActor
final class ChallengeStore {
    static let shared = ChallengeStore()

    private(set) var challenges: [SocialChallenge] = []
    private(set) var loaded = false
    private(set) var error: String?
    /// Профиль, по которому считается счёт: после вступления в челлендж
    /// счёт уходит сразу, не дожидаясь следующей тренировки.
    @ObservationIgnored private weak var profile: ProgramProfile?

    var invites: [SocialChallenge] { challenges.filter(\.isInvite) }
    var running: [SocialChallenge] { challenges.filter { $0.myStatus == "joined" && !$0.isFinished } }
    var finished: [SocialChallenge] { challenges.filter { $0.myStatus == "joined" && $0.isFinished } }

    /// Снимки экранов: список задан заранее, сервер не спрашиваем.
    @ObservationIgnored private var preview = false

    func setForPreview(_ list: [SocialChallenge]) {
        preview = true
        challenges = list
        loaded = true
    }

    func refresh() async {
        if preview { return }
        guard SocialService.shared.isSignedIn else {
            challenges = []
            loaded = true
            return
        }
        do {
            challenges = try await SocialService.shared.challenges()
            error = nil
        } catch is CancellationError {
            return
        } catch {
            self.error = error.socialMessage
        }
        loaded = true
        SocialService.shared.challengeInvites = invites.count
    }

    func replace(_ challenge: SocialChallenge) {
        if let index = challenges.firstIndex(where: { $0.id == challenge.id }) {
            challenges[index] = challenge
        } else {
            challenges.insert(challenge, at: 0)
        }
        SocialService.shared.challengeInvites = invites.count
    }

    func remove(_ id: String) {
        challenges.removeAll { $0.id == id }
        SocialService.shared.challengeInvites = invites.count
    }

    func syncAgain() async {
        if let profile { await sync(profile) } else { await refresh() }
    }

    /// При открытии приложения и после каждой тренировки: свой счёт по идущим
    /// челленджам — на сервер, опыт за места в законченных — в профиль.
    func sync(_ profile: ProgramProfile, now: Date = Date()) async {
        self.profile = profile
        guard SocialService.shared.isSignedIn else { return }
        await refresh()
        let nowMs = now.timeIntervalSince1970 * 1000
        let updates: [ChallengeScoreUpdate] = challenges.compactMap { challenge in
            guard challenge.myStatus == "joined", nowMs >= challenge.startsAt,
                  nowMs < challenge.endsAt + ChallengeScoring.graceMs,
                  let result = ChallengeScoring.result(for: challenge, profile: profile, now: now) else { return nil }
            if let mine = challenge.me, mine.score == result.score, mine.detail == result.detail { return nil }
            return ChallengeScoreUpdate(id: challenge.id, score: result.score, detail: result.detail)
        }
        if !updates.isEmpty, (try? await SocialService.shared.postChallengeScores(updates)) != nil {
            await refresh()
        }
        award(profile, now: now)
    }

    /// Опыт за место — один раз за челлендж, когда места уже окончательные.
    private func award(_ profile: ProgramProfile, now: Date) {
        for challenge in challenges where challenge.myStatus == "joined" && challenge.isFinal(now: now) {
            guard !profile.challengeAwards.contains(where: { $0.id == challenge.id }), let mine = challenge.me else { continue }
            let xp = ChallengeScoring.xp(place: mine.place, participants: challenge.joined.count, score: mine.score)
            guard xp > 0 else { continue }
            let before = Progression.xp(for: profile)
            profile.challengeAwards.append(ChallengeAward(
                id: challenge.id,
                title: challenge.title,
                place: mine.place ?? 0,
                xp: xp,
                epochDay: Int(floor(challenge.endsAt / 86_400_000))
            ))
            ProgressionEvents.shared.celebrate(before: before, after: Progression.xp(for: profile))
        }
    }
}
