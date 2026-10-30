import Observation

/// Общие для всего приложения элементы экрана, которые прячут отдельные экраны.
@Observable
@MainActor
final class AppChrome {
    static let shared = AppChrome()

    /// Переписка прячет панель вкладок: внизу экрана живёт поле ввода.
    var hidesTabBar = false

    private init() {}
}
