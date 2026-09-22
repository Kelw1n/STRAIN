import SwiftUI

/// Стиль визуального оформления приложения.
public enum AppThemeStyle: String, CaseIterable, Identifiable, Codable {
    case claude = "claude"
    case strain = "strain"

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .claude: return "Claude"
        case .strain: return "STRAIN Classic"
        }
    }

    public var description: String {
        switch self {
        case .claude: return "Тёплый молочно-терракотовый светлый и матово-угольный тёмный дизайн"
        case .strain: return "Оригинальный бирюзово-неоновый градиент"
        }
    }
}

/// Синглтон для быстрого доступа к активной теме и публикации изменений.
@Observable
public final class ThemeManager {
    public static let shared = ThemeManager()

    /// Текущая тема хранится в UserDefaults.
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
            // По умолчанию ставим тему Claude, так как пользователь запросил её
            self.current = .claude
        }
    }
}
