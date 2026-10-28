import SwiftUI
import UIKit
import XCTest
@testable import TexasProgram

/// Снимки экранов ленты и профиля: по ним на CI видно вёрстку — ничего не
/// вылезает за края телефона и не прячется под панелью вкладок.
///
/// В обычном прогоне тесты пропускаются. Снимки делает workflow `snapshots.yml`:
/// он передаёт `TEST_RUNNER_SNAPSHOTS=1` и папку для картинок `TEST_RUNNER_SNAPSHOT_DIR`.
final class SnapshotTests: XCTestCase {
    private var directory: URL!

    override func setUpWithError() throws {
        let environment = ProcessInfo.processInfo.environment
        try XCTSkipUnless(environment["SNAPSHOTS"] == "1", "Снимки экранов делаются только по запросу")
        directory = URL(fileURLWithPath: environment["SNAPSHOT_DIR"] ?? NSTemporaryDirectory(), isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    }

    // MARK: Экраны

    @MainActor func testFeedTab() throws {
        let profile = ProgramProfile(input: .demo)
        try snap("01-feed-tab", wait: 4) {
            MainTabView(profile: profile, onAddProfile: {}, onDeleteProfile: {})
        }
    }

    @MainActor func testFeedPosts() throws {
        let posts = try Fixtures.posts()
        try snap("02-feed-posts") {
            NavigationStack {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        ForEach(posts) { post in
                            PostCardView(post: post, onOpen: {}, onOpenAuthor: {}, onChange: { _ in })
                            Divider().overlay(Color.white.opacity(0.07))
                        }
                    }
                    .padding(.horizontal, 16)
                }
                .background(Color.black.ignoresSafeArea())
                .navigationBarTitleDisplayMode(.inline)
            }
            .preferredColorScheme(.dark)
        }
    }

    @MainActor func testPostDetail() throws {
        let post = try Fixtures.posts()[2]
        try snap("03-post-detail", height: 2200) {
            NavigationStack { PostDetailView(post: post) }
        }
    }

    @MainActor func testOwnProfile() throws {
        let profile = ProgramProfile(input: .demo)
        let user = try Fixtures.user(isMe: true)
        let posts = try Fixtures.posts()
        let progress = AchievementCalculator.progress(for: profile)
        let content = {
            ProfileContent(
                user: user,
                badges: [SocialBadge(id: "path", level: 2), SocialBadge(id: "streak", level: 3), SocialBadge(id: "bench", level: 1)],
                achievedCount: progress.filter { $0.level > 0 }.count,
                programs: SocialProgramBuilder.days(for: profile),
                posts: posts,
                loadingPosts: false,
                postsError: nil,
                path: .constant(NavigationPath()),
                primaryAction: { FollowButton(isFollowing: false) {} },
                onAchievements: {},
                onLoadMore: {},
                onPostChange: { _, _ in }
            )
        }
        try snap("04-profile") {
            NavigationStack {
                ScrollView { content() }
                    .background(Color.black.ignoresSafeArea())
                    .navigationBarTitleDisplayMode(.inline)
            }
            .preferredColorScheme(.dark)
        }
        try snap("05-profile-full", height: 2600) {
            NavigationStack {
                VStack { content(); Spacer(minLength: 0) }
                    .background(Color.black.ignoresSafeArea())
                    .navigationBarTitleDisplayMode(.inline)
            }
            .preferredColorScheme(.dark)
        }
    }

    @MainActor func testSignUp() throws {
        try snap("06-signup") {
            NavigationStack {
                SignUpView()
                    .background(Color.black.ignoresSafeArea())
                    .navigationBarTitleDisplayMode(.inline)
            }
            .preferredColorScheme(.dark)
        }
    }

    @MainActor func testEditProfile() throws {
        let profile = ProgramProfile(input: .demo)
        try snap("07-edit-profile", height: 1400) { EditProfileView(profile: profile) }
    }

    @MainActor func testAchievements() throws {
        let profile = ProgramProfile(input: .demo)
        profile.toggleCompleted(week: 1, day: 1, now: Date())
        try snap("08-achievements", height: 2400) {
            NavigationStack { AchievementsView(progress: AchievementCalculator.progress(for: profile)) }
        }
    }

    @MainActor func testCommunity() throws {
        try snap("09-community", wait: 4) { CommunityView(onCreateProfile: {}) }
    }

    @MainActor func testProgramDay() throws {
        let day = try XCTUnwrap(SocialProgramBuilder.days(for: ProgramProfile(input: .demo)).first)
        try snap("10-program-day") { NavigationStack { ProgramDayView(day: day) } }
    }

    @MainActor func testWorkoutSummary() throws {
        let profile = ProgramProfile(input: .demo)
        let workout = try XCTUnwrap(profile.schedule.focus)
        try snap("11-summary", height: 2600) {
            WorkoutCompletionSummaryView(profile: profile, workout: workout)
        }
    }

    @MainActor func testWorkoutHub() throws {
        let profile = ProgramProfile(input: .demo)
        try snap("12-workout-hub") {
            WorkoutHubView(profile: profile, onStartWorkout: { _ in })
        }
    }

    @MainActor func testProgress() throws {
        let profile = ProgramProfile(input: .demo)
        // Во всю высоту: внизу разделы «История упражнений», «Заметки» и «Жим 14».
        try snap("13-progress", height: 2400) {
            ActivityProgressView(profile: profile, onStartWorkout: {}, onOpenBench: {})
        }
    }

    /// Идущая тренировка с парой закрытых подходов — как её видно в зале.
    @MainActor func testActiveWorkout() throws {
        let profile = ProgramProfile(input: .demo)
        let workout = try XCTUnwrap(profile.schedule.focus)
        let manager = WorkoutSessionManager.shared
        manager.startWorkout(workout: workout, profile: profile)
        defer { manager.discardWorkout(profile: profile) }
        if let first = workout.day.exercises.first {
            profile.recordSet(week: workout.week, day: workout.day.number, exercise: first, index: 0, reps: 5, weight: 100)
            profile.recordSet(week: workout.week, day: workout.day.number, exercise: first, index: 1, reps: 5, weight: 100)
        }
        try snap("14-active-workout", wait: 2) {
            ActiveWorkoutSessionView(profile: profile, workout: workout)
        }
    }

    /// Переписка: пузыри своих и чужих сообщений, список переписок и экран чата.
    @MainActor func testChat() throws {
        let now = Date().timeIntervalSince1970 * 1000
        let messages: [SocialMessage] = [
            SocialMessage(id: "1", text: "Го сегодня в зал?", fromMe: false, createdAt: now - 3_600_000),
            SocialMessage(id: "2", text: "Давай в 7. Сегодня присед 140 на 5, подстрахуешь?", fromMe: true, createdAt: now - 3_500_000, read: true),
            SocialMessage(id: "3", text: "Конечно 💪", fromMe: false, createdAt: now - 3_400_000,
                          reactions: [SocialReaction(emoji: "🔥", count: 1, mine: true)],
                          replyTo: SocialMessageQuote(id: "2", text: "Давай в 7. Сегодня присед 140 на 5, подстрахуешь?", image: nil, fromMe: true)),
            SocialMessage(id: "4", text: "Возьми пояс", fromMe: true, createdAt: now - 60_000,
                          reactions: [SocialReaction(emoji: "👍", count: 1, mine: false), SocialReaction(emoji: "💪", count: 2, mine: true)]),
            SocialMessage(id: "5", text: "Вот так стоял", image: "0123456789abcdef01234567", fromMe: false, createdAt: now - 30_000)
        ]
        let chats: [SocialChat] = try Fixtures.decode("""
        [{"user":{"id":"u2","username":"bro.lifts"},"last":{"id":"m1","text":"Возьми пояс","fromMe":false,"createdAt":\(now - 60_000)},"unread":2},
         {"user":{"id":"u3","username":"heavy.anna"},"last":{"id":"m2","text":"Скинь программу","fromMe":true,"createdAt":\(now - 86_400_000)},"unread":0,
          "live":{"title":"День 2","exercise":"Становая тяга","done":2,"total":5,"startedAt":\(now - 1_800_000)}}]
        """)
        try snap("15-chat-bubbles") {
            NavigationStack {
                ScrollView {
                    VStack(spacing: 4) {
                        ForEach(messages) { MessageBubble(message: $0, username: "bro.lifts", onDelete: {}) }
                        TypingBubble()
                    }
                    .padding(12)
                    VStack(spacing: 0) {
                        ForEach(chats) { ChatRow(chat: $0) }
                    }
                    .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                    .padding(16)
                }
                .background(Color.black.ignoresSafeArea())
                .navigationBarTitleDisplayMode(.inline)
            }
            .preferredColorScheme(.dark)
        }
        try snap("16-chat-screen") {
            NavigationStack { ChatView(username: "bro.lifts", path: .constant(NavigationPath())) }
        }
    }

    /// Новое в переписке: пересланное, изменённое, тренировка карточкой,
    /// непрослушанное голосовое, сообщение в очереди и не ушедшее.
    @MainActor func testChatExtras() throws {
        let now = Date().timeIntervalSince1970 * 1000
        let messages: [SocialMessage] = try Fixtures.decode("""
        [{"id":"1","text":"Смотри, как надо","fromMe":false,"createdAt":\(now - 3_600_000),"forwarded":{"username":"heavy.anna"}},
         {"id":"2","text":"Жму 105 на 5","fromMe":true,"createdAt":\(now - 3_500_000),"read":true,"edited":true},
         {"id":"3","text":"🏋️ День 1 · Объём","fromMe":false,"createdAt":\(now - 3_000_000),
          "workout":{"title":"День 1 · Объём","durationSeconds":4380,"volumeKg":12850,"sets":18,"effort":8,
                     "workout":{"exercises":[{"name":"Приседания","sets":[{"reps":5,"weight":140,"record":true},{"reps":5,"weight":140}]},
                                             {"name":"Жим лёжа","sets":[{"reps":5,"weight":100}]},
                                             {"name":"Тяга в наклоне","sets":[{"reps":8,"weight":70}]},
                                             {"name":"Подъём на бицепс","sets":[{"reps":12,"weight":30}]}]}}},
         {"id":"4","text":"🏋️ День 2","fromMe":true,"createdAt":\(now - 2_000_000),"read":false,
          "workout":{"title":"День 2 · Восстановление","durationSeconds":2700,"volumeKg":6400,"sets":12,"workout":{"exercises":[{"name":"Жим стоя","sets":[{"reps":5,"weight":60}]}]}}},
         {"id":"5","text":"🎤 Голосовое сообщение","fromMe":false,"createdAt":\(now - 120_000),"heard":false,
          "voice":{"id":"v1","durationMs":14200,"waveform":"2468ace8642468aceca8642"}}]
        """)
        let pending = ChatPending(clientId: "c1", username: "bro.lifts", createdAt: now - 30_000, text: "Уже в зале, жду")
        let failed = ChatPending(clientId: "c2", username: "bro.lifts", createdAt: now - 10_000, text: "Ответ на удалённое")
        try snap("18-chat-extras", height: 1500) {
            NavigationStack {
                ScrollView {
                    VStack(spacing: 4) {
                        ForEach(messages) { MessageBubble(message: $0, username: "bro.lifts", onDelete: {}) }
                        MessageBubble(message: pending.message, username: "bro.lifts", onDelete: {})
                        MessageBubble(message: failed.message, username: "bro.lifts", failure: "Сообщение удалено", onDelete: {})
                    }
                    .padding(12)
                }
                .background(Color.black.ignoresSafeArea())
                .navigationBarTitleDisplayMode(.inline)
            }
            .preferredColorScheme(.dark)
        }
    }

    /// Итоги месяца: первый слайд историй и карточка для сторис.
    @MainActor func testRecap() throws {
        let calendar = WorkoutScheduler.trainingCalendar()
        let now = Date()
        let parts = calendar.dateComponents([.year, .month], from: now)
        func day(_ offset: Int) -> Date { calendar.date(byAdding: .day, value: -offset, to: now) ?? now }
        let log = (0..<9).map { index in
            CompletionRecord(key: "1-\(index)", date: day(index * 3), week: 1, day: index, durationSeconds: 3600 + index * 120)
        }
        var sets: [String: SetEntry] = [:]
        for index in 0..<9 {
            sets["1-\(index)|Приседания|0"] = SetEntry(reps: 5, weight: 120 + Double(index) * 2.5)
            sets["1-\(index)|Жим лёжа|0"] = SetEntry(reps: 5, weight: 90 + Double(index))
            sets["1-\(index)|Становая тяга|0"] = SetEntry(reps: 3, weight: 160)
        }
        let recap = TrainingRecap.build(log: log, setLog: sets, period: .month(year: parts.year ?? 2026, month: parts.month ?? 1), calendar: calendar)
        try snap("19-recap-intro", wait: 2.5) { TrainingRecapView(recap: recap) }
        try snap("20-recap-card") {
            ZStack { Color.black.ignoresSafeArea(); TrainingRecapCard(recap: recap) }
        }
    }

    /// Тренируются сейчас, колокольчик активности и рейтинг недели.
    @MainActor func testSocialExtras() throws {
        let now = Date().timeIntervalSince1970 * 1000
        let week = WeeklyStatsBuilder.weekKey(for: Date())
        let live: [SocialLiveFriend] = try Fixtures.decode("""
        [{"user":{"id":"u2","username":"bro.lifts"},"live":{"title":"День 1 · Объём","exercise":"Приседания","done":3,"total":5,"setsDone":3,"setsTotal":18,"startedAt":\(now - 1_560_000)}},
         {"user":{"id":"u3","username":"heavy.anna"},"live":{"title":"День 2","exercise":"Жим лёжа с очень длинным названием","done":1,"total":8,"startedAt":\(now - 300_000)}}]
        """)
        let activity: SocialActivityPage = try Fixtures.decode("""
        {"items":[
          {"kind":"like","actor":{"id":"u2","username":"bro.lifts"},"postId":"p1","postTitle":"День 1 · Объём","createdAt":\(now - 120_000),"unseen":true},
          {"kind":"reply","actor":{"id":"u3","username":"heavy.anna"},"postId":"p1","postTitle":"День 1","text":"Спасибо, попробую с поясом!","createdAt":\(now - 3_600_000),"unseen":true},
          {"kind":"comment","actor":{"id":"u4","username":"semobsessed"},"postId":"p2","postTitle":"День 3 · Сила","text":"Мощно 🔥 сколько отдыхал между подходами?","createdAt":\(now - 7_200_000)},
          {"kind":"follow","actor":{"id":"u5","username":"tim"},"createdAt":\(now - 90_000_000)}],"unseen":2}
        """)
        let board: [SocialLeaderboardEntry] = try Fixtures.decode("""
        [{"user":{"id":"u1","username":"kelw1n","isMe":true},"stats":{"week":"\(week)","workouts":3,"volumeKg":14250,"records":2,"streakWeeks":6}},
         {"user":{"id":"u2","username":"bro.lifts"},"stats":{"week":"\(week)","workouts":4,"volumeKg":18900,"records":1,"streakWeeks":3}},
         {"user":{"id":"u3","username":"heavy.anna"},"stats":{"week":"\(week)","workouts":2,"volumeKg":7300,"records":3,"streakWeeks":12}},
         {"user":{"id":"u4","username":"semobsessed"},"stats":{}}]
        """)
        let together: SocialTogetherState = try Fixtures.decode("""
        {"incoming":[{"id":"t1","status":"pending","fromMe":false,"partner":{"id":"u3","username":"heavy.anna"}}],
         "active":{"id":"t2","status":"accepted","fromMe":true,"partner":{"id":"u2","username":"bro.lifts"},
                   "partnerLive":{"title":"День 1","exercise":"Приседания","done":3,"total":5,"setsDone":8,"setsTotal":18,"startedAt":\(now - 1_500_000)}}}
        """)
        let invite: SocialMessage = try Fixtures.decode("""
        {"id":"m9","text":"Го тренироваться вместе 💪","fromMe":false,"createdAt":\(now),"invite":{"id":"t1","status":"pending","fromMe":false}}
        """)
        try snap("17-social-extras", height: 2600) {
            NavigationStack {
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        if let first = together.incoming.first { TogetherInviteBanner(invite: first).padding(.horizontal, -12) }
                        if let active = together.active { TogetherCard(together: active) }
                        if let inviteCard = invite.invite {
                            InviteBubble(message: invite, invite: inviteCard, username: "heavy.anna") { _ in }
                        }
                        LiveFriendsStrip(friends: live) { _ in }
                        LiveBadge(live: live[0].live)
                        VStack(spacing: 0) {
                            ForEach(activity.items) { ActivityRow(item: $0, onOpenActor: {}, onOpen: {}) }
                        }
                        .padding(.horizontal, -16)
                        VStack(spacing: 0) {
                            let ranked = LeaderboardMetric.ranked(board, by: .volume)
                            ForEach(Array(ranked.enumerated()), id: \.element.id) { index, entry in
                                LeaderboardRow(place: index + 1, entry: entry, metric: .volume) {}
                            }
                        }
                        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                    }
                    .padding(16)
                }
                .background(Color.black.ignoresSafeArea())
                .navigationBarTitleDisplayMode(.inline)
            }
            .preferredColorScheme(.dark)
        }
    }

    // MARK: Питание

    private func meal(_ name: String, daysAgo: Int, hour: Int, grams: Double?, _ nutrients: Nutrients, photo: Bool = false) -> FoodEntry {
        let calendar = Calendar.current
        let date = calendar.date(byAdding: DateComponents(day: -daysAgo, hour: hour), to: calendar.startOfDay(for: Date()))!
        return FoodEntry(date: date, name: name, grams: grams, nutrients: nutrients, fromPhoto: photo)
    }

    private func dayOfMeals(_ daysAgo: Int) -> [FoodEntry] {
        [
            meal("Овсянка с бананом", daysAgo: daysAgo, hour: 9, grams: 320, Nutrients(kcal: 430, protein: 14, fat: 9, carbs: 74)),
            meal("Куриная грудка с рисом", daysAgo: daysAgo, hour: 14, grams: 350, Nutrients(kcal: 560, protein: 52, fat: 7, carbs: 70), photo: true),
            meal("Творог 5%", daysAgo: daysAgo, hour: 18, grams: 200, Nutrients(kcal: 242, protein: 34, fat: 10, carbs: 6))
        ]
    }

    /// Неделя наблюдения на четвёртом дне: точки дней, еда и вес за сегодня.
    @MainActor func testNutritionObservation() throws {
        let profile = ProgramProfile(input: .demo)
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        var diary = NutritionData(entries: (0...3).flatMap { dayOfMeals($0) })
        diary.plan = NutritionPlan(observationStart: calendar.date(byAdding: .day, value: -3, to: today)!)
        for daysAgo in 1...3 {
            profile.recordBodyWeight(82.4 + Double(daysAgo) * 0.1, on: calendar.date(byAdding: .day, value: -daysAgo, to: today)!)
        }
        NutritionStore.write(diary, for: profile.profileID)
        defer { NutritionStore.delete(profile.profileID) }
        try snap("20-nutrition-observe", height: 2000) { NutritionView(profile: profile) }
    }

    /// Набор: норма на день и недельная проверка с советом «+30 г углеводов».
    @MainActor func testNutritionGoal() throws {
        let profile = ProgramProfile(input: .demo)
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        let maintenance = Nutrients(kcal: 2600, protein: 160, fat: 85, carbs: 300)
        var plan = NutritionPlan(observationStart: calendar.date(byAdding: .day, value: -14, to: today)!)
        plan.goal = .bulk
        plan.maintenance = maintenance
        plan.target = LimarMethod.startingNorm(for: .bulk, maintenance: maintenance)
        plan.stepStart = calendar.date(byAdding: .day, value: -7, to: today)
        for daysAgo in 1...14 {
            profile.recordBodyWeight(82 + (daysAgo % 2 == 0 ? 0.1 : 0), on: calendar.date(byAdding: .day, value: -daysAgo, to: today)!)
        }
        NutritionStore.write(NutritionData(entries: dayOfMeals(0), plan: plan), for: profile.profileID)
        defer { NutritionStore.delete(profile.profileID) }
        try snap("21-nutrition-goal", height: 2000) { NutritionView(profile: profile) }
    }

    @MainActor func testNutritionGuide() throws {
        try snap("22-nutrition-guide", height: 2000) { NutritionGuideView() }
    }

    @MainActor func testNutritionEntry() throws {
        let entry = dayOfMeals(0)[1]
        try snap("23-nutrition-entry") {
            FoodEntrySheet(original: entry, day: Date(), onSave: { _ in }, onDelete: {})
        }
    }

    // MARK: Уровни

    /// Три тренировки в неделю за десять недель, жим и присед растут.
    private func levelProfile() -> ProgramProfile {
        let profile = ProgramProfile(input: .demo)
        let calendar = WorkoutScheduler.trainingCalendar()
        var log: [CompletionRecord] = []
        var sets: [String: SetEntry] = [:]
        for index in 0..<30 {
            let date = calendar.date(byAdding: .day, value: -(index * 7 / 3) - 1, to: Date()) ?? Date()
            let key = "\(index / 3 + 1)-\(index % 3 + 1)"
            log.append(CompletionRecord(key: key, date: date, week: index / 3 + 1, day: index % 3 + 1))
            for set in 0..<5 {
                sets["\(key)|Приседания|\(set)"] = SetEntry(reps: 5, weight: 140 - Double(index) * 1.25)
                sets["\(key)|Жим лёжа|\(set)"] = SetEntry(reps: 5, weight: 100 - Double(index))
            }
        }
        profile.completionLog = log
        profile.setLog = sets
        return profile
    }

    @MainActor func testLevels() throws {
        let profile = levelProfile()
        try snap("24-level-card") {
            VStack { LevelCard(profile: profile); Spacer() }
                .padding(16)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.black)
        }
        try snap("25-level-sheet", height: 2200) { LevelSheet(profile: profile) }
        let xp = Progression.xp(for: profile)
        let gain = ProgressionEvents.Gain(before: Progression.threshold(for: Progression.level(forXP: xp).level) - 40, after: xp)
        try snap("26-level-up", wait: 3) { LevelUpOverlay(gain: gain) {} }
        try snap("27-level-share") {
            ZStack { Color.black.ignoresSafeArea(); LevelShareCard(level: Progression.level(forXP: xp)) }
        }
    }

    @MainActor func testUserLevelAndAvatar() throws {
        let user = try Fixtures.user(isMe: false)
        try snap("34-user-level", height: 1800) { UserLevelSheet(user: user, myLevel: 9) }
        try snap("36-records", height: 1400) {
            RecordsSheet(username: user.username, records: user.records, bodyWeight: user.weightKg)
        }
        try snap("35-avatar-viewer", wait: 1) {
            AvatarViewer(imageId: "0123456789abcdef01234567", username: user.username) {}
        }
    }

    // MARK: ИИ-тренер

    @MainActor func testCoach() throws {
        let profile = levelProfile()
        let messages = [
            CoachMessage(role: .user, text: "Почему встал жим?"),
            CoachMessage(
                role: .coach,
                text: "Жим три тренировки подряд на **100 кг** без прибавки в повторах, а тяжёлый день ты отметил как «тяжело». Это застой.\n\n- Сбрось максимум на 10% и набери заново.\n- Спи 8 часов и добавь 20 г белка.",
                actions: [CoachAction(type: "stall_reset", lift: "bench"), CoachAction(type: "weight_step", exercise: "Жим лёжа", step: 1.25)]
            ),
            CoachMessage(role: .user, text: Coach.reviewPrompt, reviewWeek: "2026-W43"),
            CoachMessage(role: .coach, text: "Неделя закрыта: 3 из 3 тренировок, присед +2,5 кг. Тяга отстаёт — на этой неделе держи вес.", reviewWeek: "2026-W43")
        ]
        CoachHistory.write(messages, for: profile.profileID)
        defer { CoachHistory.delete(profile.profileID) }
        try snap("32-coach", height: 1700, wait: 2) { CoachView(profile: profile) }
        try snap("33-coach-entry") {
            VStack { CoachEntryCard(profile: profile); Spacer() }
                .padding(16)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.black)
        }
    }

    // MARK: Челленджи

    @MainActor func testChallenges() throws {
        let now = Date().timeIntervalSince1970 * 1000
        let day = 86_400_000.0
        func member(_ id: String, _ name: String, _ place: Int, _ score: Int, _ detail: String, me: Bool = false, level: Int? = nil) -> String {
            let levelPart = level.map { ",\"level\":\($0)" } ?? ""
            return #"{"user":{"id":"\#(id)","username":"\#(name)","isMe":\#(me)\#(levelPart)},"status":"joined","score":\#(score),"detail":"\#(detail)","place":\#(place)}"#
        }
        let json = """
        [{"id":"inv","kind":"volume","weeks":2,"startsAt":\(now - day),"endsAt":\(now + 13 * day),"status":"active","myStatus":"invited",
          "owner":{"id":"u2","username":"bro.lifts"},"members":[\(member("u2", "bro.lifts", 1, 4200, "4200 кг"))]},
         {"id":"run","kind":"workouts","weeks":2,"startsAt":\(now - 5 * day),"endsAt":\(now + 9 * day),"status":"active","myStatus":"joined",
          "members":[\(member("u2", "bro.lifts", 1, 4, "4 тренировки", level: 12)),\(member("u1", "kelw1n", 2, 3, "3 тренировки", me: true, level: 9)),
                     \(member("u3", "heavy.anna", 3, 1, "1 тренировка", level: 21)),{"user":{"id":"u4","username":"slowpoke"},"status":"invited"}]},
         {"id":"fin","kind":"strength","weeks":4,"startsAt":\(now - 33 * day),"endsAt":\(now - 5 * day),"status":"finished","myStatus":"joined",
          "members":[\(member("u1", "kelw1n", 1, 6, "+5,2 кг · 6,3% веса", me: true, level: 9)),\(member("u3", "heavy.anna", 2, 4, "+3,1 кг · 4,8% веса"))]}]
        """
        let list = try JSONDecoder().decode([SocialChallenge].self, from: Data(json.utf8))
        ChallengeStore.shared.setForPreview(list)
        try snap("28-challenges", height: 1800) {
            ScrollView { ChallengesSection().padding(16) }.background(Color.black)
        }
        try snap("29-challenge-detail", height: 1800) { ChallengeDetailSheet(id: "fin") }
        try snap("30-challenge-new", height: 1800) { NewChallengeSheet() }
        try snap("31-challenge-share") {
            ZStack { Color.black.ignoresSafeArea(); ChallengeShareCard(challenge: list[2]) }
        }
    }

    // MARK: Съёмка

    /// Экран рисуется в настоящем окне — так работают прокрутка, навигация и
    /// поля ввода, которые `ImageRenderer` не умеет. `height` — высокий кадр,
    /// чтобы увидеть экран целиком, а не только первый экран телефона.
    @MainActor private func snap<Content: View>(
        _ name: String,
        height: CGFloat? = nil,
        wait: TimeInterval = 1.5,
        @ViewBuilder _ content: () -> Content
    ) throws {
        let scene = try XCTUnwrap(
            UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first,
            "Нет сцены приложения"
        )
        let window = UIWindow(windowScene: scene)
        if let height {
            window.frame = CGRect(x: 0, y: 0, width: scene.screen.bounds.width, height: height)
        }
        window.overrideUserInterfaceStyle = .dark
        // Цвет кнопок — как в приложении: его задаёт RootView.
        window.rootViewController = UIHostingController(rootView: content().tint(Theme.accent).environment(RestTimer()))
        window.makeKeyAndVisible()
        // Даём SwiftUI отрисоваться, запустить .task и дождаться ответа сервера.
        RunLoop.main.run(until: Date().addingTimeInterval(wait))
        window.layoutIfNeeded()

        let format = UIGraphicsImageRendererFormat()
        format.scale = 2
        let image = UIGraphicsImageRenderer(bounds: window.bounds, format: format).image { _ in
            _ = window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
        }
        let data = try XCTUnwrap(image.jpegData(compressionQuality: 0.6))
        try data.write(to: directory.appendingPathComponent("\(name).jpg"))
        window.isHidden = true
        window.rootViewController = nil
    }
}

/// Посты и профиль как с сервера: те же JSON, что отдаёт `/v2`.
private enum Fixtures {
    static func decode<T: Decodable>(_ json: String) throws -> T {
        try JSONDecoder().decode(T.self, from: Data(json.utf8))
    }

    static func user(isMe: Bool) throws -> SocialUser {
        try decode("""
        {"id":"u1","username":"kelw1n","bio":"Техасская программа, второй месяц. Жим 120 к Новому году.",
         "heightCm":182,"weightKg":86.4,"showBody":true,"workouts":27,"followers":14,"following":9,
         "isMe":\(isMe),"isFollowing":false,"level":12,"xp":8000,
         "achievements":[{"id":"path","level":2},{"id":"streak","level":3},{"id":"bench","level":1}],
         "records":[{"name":"Становая тяга","weight":180,"reps":3,"estimate":198,"date":1761000000000,"lift":"deadlift"},
                    {"name":"Приседания","weight":140,"reps":5,"estimate":163.5,"date":1760000000000,"lift":"squat"},
                    {"name":"Жим лёжа","weight":100,"reps":5,"estimate":116.5,"date":1761400000000,"lift":"bench"},
                    {"name":"Жим стоя","weight":60,"reps":5,"estimate":70,"date":1761300000000},
                    {"name":"Подтягивания","weight":20,"reps":8,"estimate":25.5,"date":1761200000000}],
         "programs":[]}
        """)
    }

    static func posts() throws -> [SocialPost] {
        let now = Date().timeIntervalSince1970 * 1000
        return try decode("""
        [
         {"id":"p3","author":{"id":"u3","username":"heavy.anna"},"createdAt":\(now - 3 * 60_000),
          "title":"Объёмный понедельник","description":"",
          "durationSeconds":6000,"volumeKg":123456,"sets":30,"effort":10,
          "photos":[],
          "workout":{"exercises":[{"name":"Приседания со штангой","sets":[{"reps":8,"weight":120}]}]},
          "likes":12,"comments":4,"liked":false},
         {"id":"p1","author":{"id":"u2","username":"bro.lifts"},"partner":{"id":"u4","username":"semobsessed"},"createdAt":\(now - 25 * 60_000),
          "title":"Неделя 3 · День 1 — объём","description":"Присед зашёл легко, жим тяжело. Завтра отдых.",
          "durationSeconds":4980,"volumeKg":12450,"sets":18,"effort":8,
          "photos":["0123456789abcdef01234567","0123456789abcdef01234568"],
          "workout":{"program":"Техасская программа","exercises":[
            {"name":"Приседания со штангой","sets":[{"reps":5,"weight":140,"record":true},{"reps":5,"weight":140},{"reps":5,"weight":140}]},
            {"name":"Жим лёжа","sets":[{"reps":5,"weight":100},{"reps":5,"weight":100}]}]},
          "likes":5,"comments":2,"liked":true},
         {"id":"p2","author":{"id":"u1","username":"kelw1n"},"createdAt":\(now - 26 * 3_600_000),
          "title":"Тяжёлая пятница","description":"",
          "durationSeconds":3720,"volumeKg":8320.5,"sets":12,
          "photos":[],
          "workout":{"program":"Техасская программа","exercises":[
            {"name":"Приседания со штангой","sets":[{"reps":5,"weight":150,"record":true}]},
            {"name":"Жим лёжа широким хватом с паузой на груди","sets":[{"reps":3,"weight":107.5},{"reps":3,"weight":107.5}]},
            {"name":"Становая тяга","sets":[{"reps":5,"weight":180}]},
            {"name":"Подтягивания","sets":[{"reps":12,"weight":0},{"reps":10,"weight":0}]},
            {"name":"Пресс","sets":[{"reps":20,"weight":0}]}]},
          "likes":0,"comments":0,"liked":false}
        ]
        """)
    }
}
