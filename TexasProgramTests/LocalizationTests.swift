import XCTest
@testable import TexasProgram

/// Те же фразы, что в LocalizationTest.kt и tools/i18n/reference.py: перевод на
/// iPhone и Android должен совпадать дословно.
final class LocalizationTests: XCTestCase {
    override func setUp() {
        super.setUp()
        UserDefaults.standard.set(AppLanguage.en.rawValue, forKey: AppLanguage.storageKey)
        L10n.reload()
    }

    override func tearDown() {
        UserDefaults.standard.set(AppLanguage.ru.rawValue, forKey: AppLanguage.storageKey)
        L10n.reload()
        super.tearDown()
    }

    func testTranslatesPhrasesTemplatesAndPieces() {
        let cases: [(String, String)] = [
            ("Неделя 3 из 12", "Week 3 of 12"),
            ("ДЕНЬ 1", "DAY 1"),
            ("4 тренировки", "4 workouts"),
            ("Выбрать фото сбоку", "Choose a side-view photo"),
            ("Восстановить историю", "Restore workout history"),
            ("был(а) вчера", "last seen yesterday"),
            ("Ноябрь 2026", "November 2026"),
            ("жим лёжа", "bench press"),
            ("Приседания · тест 1ПМ", "Squats · 1RM test"),
            ("Ждём ответа: @kel", "Waiting for a reply: @kel"),
            ("Каждую неделю добавляй к норме 30\u{00A0}г углеводов и смотри, как меняется вес. Пошёл вверх — держи норму.",
             "Every week add 30 g of carbs to your target and watch your weight. Once it goes up — hold the target."),
            // Неизвестная фраза остаётся целиком русской, без английских вставок.
            ("Верх по средам и фулбади", "Верх по средам и фулбади"),
        ]
        for (russian, english) in cases {
            XCTAssertEqual(L10n.tr(russian), english, russian)
        }
    }

    func testEnglishMonthlyProgress() throws {
        let date = try XCTUnwrap(Calendar.current.date(from: DateComponents(year: 2026, month: 11, day: 10)))
        XCTAssertEqual(ProgressionText.month(1, next: .bronze, now: date), "November: 1 workout · 7 to bronze")
        XCTAssertEqual(ProgressionText.month(20, next: nil, now: date), "November: 20 workouts · gold earned")
    }

    func testRussianModeLeavesTextAlone() {
        UserDefaults.standard.set(AppLanguage.ru.rawValue, forKey: AppLanguage.storageKey)
        L10n.reload()
        XCTAssertEqual(L10n.tr("Неделя 3 из 12"), "Неделя 3 из 12")
    }
}
