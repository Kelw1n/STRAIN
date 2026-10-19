import UIKit
import XCTest
@testable import TexasProgram

/// Лента: достижения, программа в профиле, разбор ответов сервера и подписи.
final class SocialTests: XCTestCase {

    private func texas() -> ProgramProfile { ProgramProfile(input: .demo) }

    private var calendar: Calendar { WorkoutScheduler.trainingCalendar() }

    private func day(_ offset: Int) -> Date {
        let monday = calendar.date(from: DateComponents(year: 2026, month: 10, day: 12, hour: 12))!
        return calendar.date(byAdding: .day, value: offset, to: monday)!
    }

    private func progress(_ profile: ProgramProfile, _ kind: AchievementKind) -> AchievementProgress {
        AchievementCalculator.progress(for: profile).first { $0.kind == kind }!
    }

    // MARK: Достижения

    func testFirstWorkoutGivesFirstPathLevel() {
        let profile = texas()
        XCTAssertEqual(progress(profile, .path).level, 0)
        profile.toggleCompleted(week: 1, day: 1, now: day(0))
        let path = progress(profile, .path)
        XCTAssertEqual(path.level, 1)
        XCTAssertEqual(path.nextThreshold, 15)
        XCTAssertEqual(path.caption, "1 из 15 · тренировок всего")
    }

    func testStrengthNeedsBodyWeight() {
        let profile = texas()
        let bench = progress(profile, .bench)
        XCTAssertTrue(bench.needsBodyWeight)
        XCTAssertEqual(bench.level, 0)

        profile.recordBodyWeight(profile.bench5RM, on: day(0))
        let measured = progress(profile, .bench)
        XCTAssertFalse(measured.needsBodyWeight)
        XCTAssertEqual(measured.value, 1, accuracy: 0.0001, "жим своего веса — ×1")
        XCTAssertEqual(measured.level, 2)
    }

    /// Рекорд — превышение прошлого лучшего веса; первая тренировка движения рекордом не считается.
    func testRecordsCountOnlyImprovements() throws {
        let profile = texas()
        let squat = try XCTUnwrap(profile.workoutPlan.weeks[0].days[0].exercises.first)
        profile.recordSet(week: 1, day: 1, exercise: squat, index: 0, reps: 5, weight: 100)
        XCTAssertEqual(AchievementCalculator.recordCount(profile), 0)
        let later = try XCTUnwrap(profile.workoutPlan.weeks[1].days[0].exercises.first { $0.name == squat.name })
        profile.recordSet(week: 2, day: 1, exercise: later, index: 0, reps: 5, weight: 105)
        XCTAssertEqual(AchievementCalculator.recordCount(profile), 1)
    }

    func testBestWeekStreak() {
        let dates = [day(-21), day(-14), day(-12), day(-7), day(7), day(14)]
        XCTAssertEqual(AchievementCalculator.bestWeekStreak(dates, calendar: calendar), 3)
        XCTAssertEqual(AchievementCalculator.bestWeekStreak([], calendar: calendar), 0)
    }

    func testBadgesAreTopThreeLevels() {
        let progress = [
            AchievementProgress(kind: .path, value: 20, needsBodyWeight: false),
            AchievementProgress(kind: .iron, value: 0, needsBodyWeight: false),
            AchievementProgress(kind: .records, value: 5, needsBodyWeight: false),
            AchievementProgress(kind: .streak, value: 8, needsBodyWeight: false),
            AchievementProgress(kind: .bench, value: 1, needsBodyWeight: false)
        ]
        let badges = AchievementCalculator.badges(from: progress)
        XCTAssertEqual(badges.map(\.id), ["streak", "path", "records"])
        XCTAssertEqual(badges.first?.level, 3)
    }

    func testProgramDaysForProfile() {
        let days = SocialProgramBuilder.days(for: texas())
        XCTAssertEqual(days.count, 3)
        XCTAssertFalse(days[0].exercises.isEmpty)
        XCTAssertLessThanOrEqual(days[0].groups.count, 2)
        XCTAssertEqual(days[0].setCount, days[0].exercises.reduce(0) { $0 + $1.sets })
    }

    // MARK: Ответы сервера

    func testPostDecodesWithMissingFields() throws {
        let json = """
        {"id":"p1","author":{"id":"u1","username":"tim"},"createdAt":1792000000000,
         "title":"Верх","volumeKg":1470,"durationSeconds":6480,
         "workout":{"exercises":[{"name":"Жим лёжа","sets":[{"reps":5,"weight":80},{"reps":5,"weight":85,"record":true}]}]}}
        """
        let post = try JSONDecoder().decode(SocialPost.self, from: Data(json.utf8))
        XCTAssertEqual(post.description, "")
        XCTAssertEqual(post.photos, [])
        XCTAssertEqual(post.likes, 0)
        XCTAssertFalse(post.liked)
        XCTAssertEqual(post.workout.exercises.first?.best?.weight, 85)
        XCTAssertTrue(post.workout.exercises.first?.hasRecord ?? false)
        XCTAssertEqual(post.density, 1470 / 108, accuracy: 0.01)
    }

    func testBrokenWorkoutDoesNotDropPost() throws {
        let json = """
        {"id":"p2","author":{"id":"u1","username":"tim"},"createdAt":0,"workout":"не тренировка"}
        """
        let post = try JSONDecoder().decode(SocialPost.self, from: Data(json.utf8))
        XCTAssertTrue(post.workout.exercises.isEmpty)
        XCTAssertEqual(post.title, "Тренировка")
    }

    func testUserHidesBodyLineWhenEmpty() throws {
        let hidden = try JSONDecoder().decode(SocialUser.self, from: Data(#"{"id":"u","username":"a"}"#.utf8))
        XCTAssertNil(hidden.bodyLine)
        let shown = try JSONDecoder().decode(SocialUser.self, from: Data(#"{"id":"u","username":"a","heightCm":178,"weightKg":75}"#.utf8))
        XCTAssertEqual(shown.bodyLine, "178 см · 75 кг")
    }

    // MARK: Подписи

    func testUsernameRules() {
        XCTAssertEqual(SocialFormat.normalizedUsername(" @Kelw1n "), "kelw1n")
        XCTAssertTrue(SocialFormat.isValidUsername("kelw1n"))
        XCTAssertTrue(SocialFormat.isValidUsername("tim_g.2008"))
        XCTAssertFalse(SocialFormat.isValidUsername("ab"))
        XCTAssertFalse(SocialFormat.isValidUsername("тимур"))
        XCTAssertFalse(SocialFormat.isValidUsername("with space"))
    }

    func testAgoAndDuration() {
        let now = day(0)
        XCTAssertEqual(SocialFormat.ago(now.addingTimeInterval(-20), now: now, calendar: calendar), "только что")
        XCTAssertEqual(SocialFormat.ago(now.addingTimeInterval(-600), now: now, calendar: calendar), "10 минут назад")
        XCTAssertEqual(SocialFormat.ago(now.addingTimeInterval(-3 * 3600), now: now, calendar: calendar), "3 часа назад")
        XCTAssertEqual(SocialFormat.ago(day(-1), now: now, calendar: calendar), "вчера")
        XCTAssertEqual(SocialFormat.duration(6480), "1ч 48м")
        XCTAssertEqual(SocialFormat.duration(45 * 60), "45м")
    }

    /// Отменённый запрос (ушли с экрана, набрали следующую букву) — не ошибка для человека.
    func testCancelledRequestHasNoMessage() {
        XCTAssertNil(CancellationError().socialMessage)
        XCTAssertNil(URLError(.cancelled).socialMessage)
        XCTAssertEqual(SocialError.offline.socialMessage, SocialError.offline.message)
    }

    func testDownscaledPhotoKeepsAspect() {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let image = UIGraphicsImageRenderer(size: CGSize(width: 4000, height: 3000), format: format).image { _ in }
        let small = image.downscaled(maxSide: 2048)
        XCTAssertEqual(max(small.size.width, small.size.height), 2048, accuracy: 1)
        XCTAssertEqual(small.size.width / small.size.height, 4.0 / 3.0, accuracy: 0.01)
        let tiny = UIGraphicsImageRenderer(size: CGSize(width: 100, height: 100), format: format).image { _ in }
        XCTAssertTrue(tiny.downscaled() === tiny, "маленькое фото не перерисовывается")
    }

    // MARK: Копирование и ответы

    func testCopyWorkoutTakesWorkingWeight() throws {
        let workout = try JSONDecoder().decode(SocialWorkout.self, from: Data(#"""
        {"exercises":[{"name":"Присед","sets":[{"reps":5,"weight":100},{"reps":3,"weight":140},{"reps":3,"weight":140}]},
                      {"name":"Подтягивания","sets":[{"reps":10,"weight":0},{"reps":8,"weight":0}]}]}
        """#.utf8))
        let program = ProgramCopy.program(workout: workout, title: "Тяжёлая пятница", name: "Пятница")
        XCTAssertEqual(program.days.count, 1)
        XCTAssertEqual(program.days[0].title, "Тяжёлая пятница")
        let squat = program.days[0].exercises[0]
        XCTAssertEqual(squat.sets, 3)
        XCTAssertEqual(squat.reps, "3", "повторы рабочего веса")
        XCTAssertEqual(squat.kilograms, 140)
        let pullups = program.days[0].exercises[1]
        XCTAssertNil(pullups.kilograms)
        XCTAssertEqual(pullups.loadText, "свой вес")
        XCTAssertTrue(program.isRunnable)
    }

    func testCopyProgramDaysParsesLoads() {
        XCTAssertEqual(ProgramCopy.kilograms("100 кг"), 100)
        XCTAssertEqual(ProgramCopy.kilograms("82,5 кг"), 82.5)
        XCTAssertEqual(ProgramCopy.kilograms("220 lbs"), 100)
        XCTAssertNil(ProgramCopy.kilograms("RPE 8"))
        let days = SocialProgramBuilder.days(for: texas())
        let program = ProgramCopy.program(days: days, name: "Программа @tim")
        XCTAssertEqual(program.days.count, days.count)
        XCTAssertEqual(program.days.map(\.number), Array(1...days.count))
        XCTAssertEqual(program.days[0].exercises.count, days[0].exercises.count)
        XCTAssertTrue(program.isRunnable)
        XCTAssertFalse(ProgramProfile(customProgram: program).workoutPlan.weeks.isEmpty)
    }

    func testCommentThreadsGroupReplies() throws {
        let comments = try JSONDecoder().decode([SocialComment].self, from: Data(#"""
        [{"id":"a","postId":"p","author":{"id":"1","username":"a"},"text":"вопрос","createdAt":1},
         {"id":"b","postId":"p","author":{"id":"2","username":"b"},"text":"ответ","createdAt":2,"replyTo":"a","replyToUsername":"a"},
         {"id":"c","postId":"p","author":{"id":"3","username":"c"},"text":"просто","createdAt":3},
         {"id":"d","postId":"p","author":{"id":"1","username":"a"},"text":"спасибо","createdAt":4,"replyTo":"a","replyToUsername":"b"},
         {"id":"e","postId":"p","author":{"id":"1","username":"a"},"text":"сирота","createdAt":5,"replyTo":"x"}]
        """#.utf8))
        let threads = SocialComment.threads(comments)
        XCTAssertEqual(threads.map(\.root.id), ["a", "c", "e"], "ответ без корня — как обычный")
        XCTAssertEqual(threads[0].replies.map(\.id), ["b", "d"])
        XCTAssertEqual(threads[0].replies[1].replyToUsername, "b")
    }

    // MARK: Переписка

    func testReactionToggleKeepsOnePerPerson() {
        let fire = SocialReaction.toggled([], "🔥")
        XCTAssertEqual(fire, [SocialReaction(emoji: "🔥", count: 1, mine: true)])
        XCTAssertEqual(SocialReaction.toggled(fire, "🔥"), [], "та же ещё раз — снимается")

        let shared = [SocialReaction(emoji: "🔥", count: 2, mine: true), SocialReaction(emoji: "💪", count: 1, mine: false)]
        XCTAssertEqual(
            SocialReaction.toggled(shared, "💪"),
            [SocialReaction(emoji: "🔥", count: 1, mine: false), SocialReaction(emoji: "💪", count: 2, mine: true)],
            "другая реакция заменяет свою"
        )
    }

    func testMergedMessagesReplaceAndRemove() throws {
        let current = [
            SocialMessage(id: "a", text: "раз", fromMe: true, createdAt: 1),
            SocialMessage(id: "b", text: "два", fromMe: false, createdAt: 2)
        ]
        let incoming = try JSONDecoder().decode([SocialMessage].self, from: Data(#"""
        [{"id":"c","text":"три","fromMe":false,"createdAt":3,"replyTo":{"id":"a","text":"раз","fromMe":true}},
         {"id":"b","deleted":true,"fromMe":false,"createdAt":2,"text":""},
         {"id":"a","text":"раз","fromMe":true,"createdAt":1,"read":true,"reactions":[{"emoji":"🔥","count":1,"mine":false}]}]
        """#.utf8))
        let merged = SocialMessage.merged(current, with: incoming)
        XCTAssertEqual(merged.map(\.id), ["a", "c"])
        XCTAssertTrue(merged[0].read)
        XCTAssertEqual(merged[0].reactions.first?.emoji, "🔥")
        XCTAssertEqual(merged[1].replyTo?.id, "a")
        XCTAssertEqual(merged[1].replyTo?.preview, "раз")
    }

    func testChatPageDecodesPresence() throws {
        let page = try JSONDecoder().decode(SocialChatPage.self, from: Data(#"""
        {"user":{"id":"u","username":"bro"},"messages":[],"next":null,"serverTime":1000,"typing":true,"lastSeen":900,
         "live":{"title":"День 1","exercise":"Присед","done":3,"total":5,"setsDone":3,"setsTotal":15,"startedAt":100}}
        """#.utf8))
        XCTAssertEqual(page.serverTime, 1000)
        XCTAssertEqual(page.typing, true)
        XCTAssertEqual(page.live?.line, "Присед · 3/5")
        let now = Date(timeIntervalSince1970: 1_000_000)
        XCTAssertEqual(SocialFormat.lastSeen(now.addingTimeInterval(-30), now: now), "в сети")
        XCTAssertEqual(SocialFormat.lastSeen(now.addingTimeInterval(-600), now: now), "был(а) 10 минут назад")
    }

    // MARK: Совместная тренировка

    func testTogetherDecodes() throws {
        let state = try JSONDecoder().decode(SocialTogetherState.self, from: Data(#"""
        {"incoming":[{"id":"t1","status":"pending","fromMe":false,"partner":{"id":"2","username":"sem"},"createdAt":5}],
         "outgoing":[],
         "active":{"id":"t2","status":"accepted","fromMe":true,"partner":{"id":"3","username":"tim"},"meDone":false,"partnerDone":false,
                   "partnerLive":{"title":"День 1","exercise":"Жим","done":2,"total":4,"setsDone":6,"setsTotal":16}}}
        """#.utf8))
        XCTAssertEqual(state.incoming.first?.partner.username, "sem")
        XCTAssertEqual(state.active?.partnerLive?.line, "Жим · 2/4")

        let message = try JSONDecoder().decode(SocialMessage.self, from: Data(#"""
        {"id":"m","text":"Го тренироваться вместе 💪","fromMe":false,"createdAt":1,"invite":{"id":"t1","status":"pending","fromMe":false}}
        """#.utf8))
        XCTAssertEqual(message.invite?.isPending, true)
        XCTAssertEqual(message.invite?.statusText, "Зовёт тренироваться вместе")

        let post = try JSONDecoder().decode(SocialPost.self, from: Data(#"""
        {"id":"p","author":{"id":"1","username":"a"},"partner":{"id":"2","username":"b"}}
        """#.utf8))
        XCTAssertEqual(post.partner?.username, "b")
    }

    /// Скрытая тренировка уходит только напарнику.
    func testPartnerOnlyLiveIsEncoded() throws {
        var live = SocialLive(title: "День 1", exercise: "Жим", done: 1, total: 4, setsDone: 1, setsTotal: 12, startedAt: 0)
        live.partnerOnly = true
        let json = try XCTUnwrap(try JSONSerialization.jsonObject(with: JSONEncoder().encode(live)) as? [String: Any])
        XCTAssertEqual(json["partnerOnly"] as? Bool, true)
    }

    // MARK: Активность и рейтинг

    func testActivityDecodes() throws {
        let page = try JSONDecoder().decode(SocialActivityPage.self, from: Data(#"""
        {"items":[{"kind":"like","actor":{"id":"1","username":"tim"},"postId":"p","postTitle":"День 1","createdAt":5,"unseen":true},
                  {"kind":"follow","actor":{"id":"2","username":"sem"},"createdAt":4}],"unseen":1}
        """#.utf8))
        XCTAssertEqual(page.unseen, 1)
        XCTAssertEqual(page.items.map(\.kind), [.like, .follow])
        XCTAssertEqual(page.items[0].action, "поставил(а) лайк тренировке «День 1»")
        XCTAssertFalse(page.items[1].unseen)
    }

    func testWeeklyStatsCountThisWeekOnly() throws {
        let profile = texas()
        let squat = try XCTUnwrap(profile.workoutPlan.weeks[0].days[0].exercises.first)
        profile.recordSet(week: 1, day: 1, exercise: squat, index: 0, reps: 5, weight: 100)
        let later = try XCTUnwrap(profile.workoutPlan.weeks[1].days[0].exercises.first { $0.name == squat.name })
        profile.recordSet(week: 2, day: 1, exercise: later, index: 0, reps: 5, weight: 105)
        // Отметки напрямую: «выполнено» дописало бы плановые подходы всего дня.
        profile.completionLog = [
            CompletionRecord(key: profile.dayKey(week: 1, day: 1), date: day(-7), week: 1, day: 1),
            CompletionRecord(key: profile.dayKey(week: 2, day: 1), date: day(0), week: 2, day: 1)
        ]

        let stats = WeeklyStatsBuilder.stats(for: profile, now: day(2), calendar: calendar)
        XCTAssertEqual(stats.week, "2026-W42")
        XCTAssertEqual(stats.workouts, 1)
        XCTAssertEqual(stats.volumeKg, 525, accuracy: 0.01)
        XCTAssertEqual(stats.records, 1, "105 после 100 — рекорд этой недели")
        XCTAssertEqual(stats.streakWeeks, 2)
    }

    func testCurrentStreakWaitsForWeekToEnd() {
        XCTAssertEqual(WeeklyStatsBuilder.currentStreak([day(-14), day(-7)], now: day(2), calendar: calendar), 2,
                       "на этой неделе ещё не было — серия не прервана")
        XCTAssertEqual(WeeklyStatsBuilder.currentStreak([day(0), day(-7)], now: day(2), calendar: calendar), 2)
        XCTAssertEqual(WeeklyStatsBuilder.currentStreak([day(-14)], now: day(2), calendar: calendar), 0)
    }

    func testLeaderboardIgnoresStaleWeeks() throws {
        let entries = try JSONDecoder().decode([SocialLeaderboardEntry].self, from: Data(#"""
        [{"user":{"id":"1","username":"old"},"stats":{"week":"2026-W40","workouts":5,"volumeKg":20000,"records":3,"streakWeeks":9}},
         {"user":{"id":"2","username":"last"},"stats":{"week":"2026-W41","workouts":2,"volumeKg":9000,"records":1,"streakWeeks":4}},
         {"user":{"id":"3","username":"now","isMe":true},"stats":{"week":"2026-W42","workouts":2,"volumeKg":6000,"records":2,"streakWeeks":3}},
         {"user":{"id":"4","username":"empty"},"stats":{}}]
        """#.utf8))
        let byVolume = LeaderboardMetric.ranked(entries, by: .volume, now: day(2))
        XCTAssertEqual(byVolume.first?.user.username, "now", "прошлые недели — нули")
        XCTAssertTrue(byVolume.first?.user.isMe ?? false)
        let byStreak = LeaderboardMetric.ranked(entries, by: .streak, now: day(2))
        XCTAssertEqual(byStreak.map(\.user.username), ["last", "now", "empty", "old"],
                       "серия прошлой недели ещё жива, позапрошлой — нет")
        XCTAssertEqual(LeaderboardMetric.volume.format(byVolume[0].stats), "6 т")
    }

    // MARK: Копия

    /// Старые копии с Android писали пустую дату выгрузки — копия всё равно читается.
    func testBackupWithEmptyExportDateStillOpens() throws {
        let data = try BackupService.export([texas()], socialToken: nil)
        var json = try XCTUnwrap(try JSONSerialization.jsonObject(with: data) as? [String: Any])
        json["exportedAt"] = ""
        let patched = try JSONSerialization.data(withJSONObject: json)
        let archive = try BackupService.archive(from: patched)
        XCTAssertEqual(archive.profiles.count, 1)
    }

    func testBackupCarriesSocialToken() throws {
        let token = String(repeating: "a", count: 64)
        let archive = try BackupService.archive(from: try BackupService.export([texas()], socialToken: token))
        XCTAssertEqual(archive.socialToken, token)
        let without = try BackupService.archive(from: try BackupService.export([texas()], socialToken: nil))
        XCTAssertNil(without.socialToken)
    }

    // MARK: Голосовые

    func testVoiceMessageDecodesAndPreviews() throws {
        let json = """
        {"id":"v1","text":"🎤 Голосовое сообщение","fromMe":true,"createdAt":1,
         "voice":{"id":"abc","durationMs":4200,"waveform":"0f8"},
         "replyTo":{"id":"q","text":"","voice":true,"fromMe":false}}
        """
        let message = try JSONDecoder().decode(SocialMessage.self, from: Data(json.utf8))
        XCTAssertEqual(message.voice?.durationMs, 4200)
        XCTAssertEqual(message.voice?.levels, [0, 1, 8.0 / 15])
        XCTAssertEqual(message.preview, SocialVoice.previewText)
        XCTAssertEqual(message.replyTo?.preview, SocialVoice.previewText)
        XCTAssertEqual(SocialVoice.format(65.9), "1:05")
    }

    func testVoiceWaveformBars() {
        // 80 уровней в 40 столбиков: в каждом — громкий кусочек, цифрой от 0 до f.
        let levels = (0..<80).map { $0 % 2 == 0 ? 0.0 : 1.0 }
        let wave = VoiceRecorder.waveform(levels)
        XCTAssertEqual(wave.count, 40)
        XCTAssertTrue(wave.allSatisfy { $0 == "f" })
        XCTAssertEqual(VoiceRecorder.waveform([]), "")
        XCTAssertEqual(VoiceRecorder.waveform([0.5], bars: 4), "8888")
    }

    /// Подтверждённое сообщение остаётся той же строкой: ключ не меняется ни
    /// при подтверждении, ни при следующих обновлениях с сервера.
    func testMergedKeepsRowKey() {
        var confirmed = SocialMessage(id: "srv", text: "Го", fromMe: true, createdAt: 2)
        confirmed.rowKey = "local-1"
        let current = SocialMessage.merged([], with: [confirmed])
        XCTAssertEqual(current.first?.key, "local-1")
        let read = SocialMessage(id: "srv", text: "Го", fromMe: true, createdAt: 2, read: true)
        let updated = SocialMessage.merged(current, with: [read])
        XCTAssertEqual(updated.first?.key, "local-1", "прочтение не меняет строку")
        XCTAssertTrue(updated.first?.read == true)
        XCTAssertEqual(SocialMessage(id: "x", text: "", fromMe: false, createdAt: 1).key, "x")
    }
}

