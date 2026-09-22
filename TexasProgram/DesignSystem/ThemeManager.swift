import SwiftUI

/// Доступные темы оформления приложения.
public enum AppThemeStyle: String, CaseIterable, Identifiable, Codable {
    case claudeLight = "claudeLight"
    case claudeDark = "claudeDark"
    case strainDark = "strainDark"
    case system = "system"

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .claudeLight: return "Claude Светлая"
        case .claudeDark: return "Claude Тёмная"
        case .strainDark: return "STRAIN Неон"
        case .system: return "Системная (Claude)"
        }
    }

    public var description: String {
        switch self {
        case .claudeLight: return "Нежный молочно-пергаментный фон, карточки слоновой кости и тёплый терракотовый акцент"
        case .claudeDark: return "Матовый глубокий графит, бархатный антрацит и тлеющий терракотово-оранжевый"
        case .strainDark: return "Оригинальный неоновый бирюзово-синий кибер-стиль"
        case .system: return "Автоматически переключает между светлой и тёмной темой Claude в зависимости от настроек iOS"
        }
    }

    /// Принудительная цветовая схема для всего интерфейса
    public var preferredColorScheme: ColorScheme? {
        switch self {
        case .claudeLight: return .light
        case .claudeDark: return .dark
        case .strainDark: return .dark
        case .system: return nil
        }
    }

    public var isClaude: Bool {
        self == .claudeLight || self == .claudeDark || self == .system
    }
}

/// Синглтон для быстрого доступа к активной теме и публикации изменений.
@Observable
public final class ThemeManager {
    public static let shared = ThemeManager()

    public var current: AppThemeStyle {
        didSet {
            UserDefaults.standard.set(current.rawValue, forKey: "app_theme_style")
        }
    }

    private init() {
        if let saved = UserDefaults.standard.string(forKey: "app_theme_style"),
           let style = AppThemeStyle(rawValue: saved) {
            self.current = style
        } else {
            // По умолчанию светлая тема Claude, как просил пользователь
            self.current = .claudeLight
        }
    }
}
