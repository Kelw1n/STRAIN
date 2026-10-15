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
        let post = try Fixtures.posts()[1]
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
        try snap("05-profile-full", height: 3000) {
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
        window.rootViewController = UIHostingController(rootView: content().environment(RestTimer()))
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
         "isMe":\(isMe),"isFollowing":false,
         "achievements":[{"id":"path","level":2},{"id":"streak","level":3},{"id":"bench","level":1}],
         "programs":[]}
        """)
    }

    static func posts() throws -> [SocialPost] {
        let now = Date().timeIntervalSince1970 * 1000
        return try decode("""
        [
         {"id":"p1","author":{"id":"u2","username":"bro.lifts"},"createdAt":\(now - 25 * 60_000),
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
