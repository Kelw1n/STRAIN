import XCTest
@testable import TexasProgram

/// Что нового в обновлении: история версий из version.json и пункты описания —
/// так же, как на Android.
@MainActor
final class AppUpdaterTests: XCTestCase {
    private func release(_ json: String) throws -> AppUpdater.Release {
        try JSONDecoder().decode(AppUpdater.Release.self, from: Data(json.utf8))
    }

    func testChangesSinceInstalledVersionNewestFirst() throws {
        let release = try release("""
        {"versionCode":1240,"versionName":"3.1.240","notes":"Плашка","date":"2026-10-30",
         "history":[{"versionCode":1240,"versionName":"3.1.240","notes":"Плашка\\n• Разминка"},
                    {"versionCode":1235,"versionName":"3.1.235","notes":"- Стекло"},
                    {"versionCode":1230,"versionName":"3.1.230","notes":"Рекорды"}]}
        """)
        XCTAssertEqual(release.changes(since: 1230).map(\.versionCode), [1240, 1235])
        XCTAssertEqual(release.changes(since: 1230, upTo: 1235).map(\.versionCode), [1235])
        XCTAssertEqual(release.changes(since: 1235).first?.points, ["Плашка", "Разминка"])
        XCTAssertEqual(release.history[1].points, ["Стекло"])
        XCTAssertEqual(AppUpdater.summary(release.changes(since: 1230)), "2 обновления с твоей версии · Плашка")
    }

    func testFileWithoutHistory() throws {
        let release = try release(#"{"versionCode":1240,"versionName":"3.1.240","notes":"Рекорды"}"#)
        XCTAssertEqual(release.changes(since: 1).first?.points, ["Рекорды"])
        XCTAssertTrue(release.changes(since: 1240).isEmpty)
    }

    func testBannerAndWhatsNewAfterUpdate() throws {
        let defaults = try XCTUnwrap(UserDefaults(suiteName: "AppUpdaterTests"))
        defaults.removePersistentDomain(forName: "AppUpdaterTests")
        let release = try release("""
        {"versionCode":1240,"versionName":"3.1.240","notes":"Плашка",
         "history":[{"versionCode":1240,"notes":"Плашка"},{"versionCode":1235,"notes":"Стекло"}]}
        """)
        let updater = AppUpdater()
        // Старая сборка: предлагаем новую, «что нового» ещё нечего показывать.
        updater.apply(release, installed: 1230, defaults: defaults)
        XCTAssertEqual(updater.available?.versionCode, 1240)
        XCTAssertEqual(updater.pending.map(\.versionCode), [1240, 1235])
        XCTAssertTrue(updater.whatsNew.isEmpty)
        // Поставили новую: плашки нет, один раз — что нового.
        updater.apply(release, installed: 1240, defaults: defaults)
        XCTAssertNil(updater.available)
        XCTAssertEqual(updater.whatsNew.map(\.versionCode), [1240, 1235])
    }
}
