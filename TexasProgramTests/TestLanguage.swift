import Foundation
@testable import TexasProgram

/// Тесты сверяют русские тексты, а симулятор в CI — на английском, и «как в
/// системе» дало бы английский. XCTest создаёт этот класс до первого теста
/// (NSPrincipalClass в настройках цели), здесь и ставим русский. Снимки
/// экранов выбирают язык сами.
@objc(TestLanguage)
final class TestLanguage: NSObject {
    override init() {
        super.init()
        UserDefaults.standard.set(AppLanguage.ru.rawValue, forKey: AppLanguage.storageKey)
        L10n.reload()
    }
}
