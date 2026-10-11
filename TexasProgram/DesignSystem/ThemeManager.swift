import SwiftUI

/// Доступные темы оформления приложения.
public enum AppThemeStyle: String, CaseIterable, Identifiable, Codable {
    case rebrnBlack = "rebrnBlack"
    case claudeDark = "claudeDark"
    case claudeLight = "claudeLight"
    case strainDark = "strainDark"
    case system = "system"

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .rebrnBlack: return "REBRN Чёрная (OLED)"
        case .claudeDark: return "Claude Тёмная"
        case .claudeLight: return "Claude Светлая"
        case .strainDark: return "STRAIN Неон"
        case .system: return "Системная"
        }
    }

    public var description: String {
        switch self {
        case .rebrnBlack: return "Глубокий матовый OLED-чёрный фон, графитовые карточки и фирменный пламенный оранжевый акцент REBRN"
        case .claudeDark: return "Матовый глубокий графит, бархатный антрацит и тлеющий терракотово-оранжевый"
        case .claudeLight: return "Нежный молочно-пергаментный фон, карточки слоновой кости и тёплый терракотовый акцент"
        case .strainDark: return "Оригинальный неоновый бирюзово-синий кибер-стиль"
        case .system: return "Автоматически переключает световую схему в зависимости от настроек iOS"
        }
    }

    /// Принудительная цветовая схема для всего интерфейса
    public var preferredColorScheme: ColorScheme? {
        switch self {
        case .rebrnBlack: return .dark
        case .claudeDark: return .dark
        case .strainDark: return .dark
        case .claudeLight: return .light
        case .system: return nil
        }
    }

    public var isRebrn: Bool {
        self == .rebrnBlack
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
        let migrationKey = "has_migrated_to_rebrn_black_v2"
        if !UserDefaults.standard.bool(forKey: migrationKey) {
            UserDefaults.standard.set(true, forKey: migrationKey)
            UserDefaults.standard.set(AppThemeStyle.rebrnBlack.rawValue, forKey: "app_theme_style")
            self.current = .rebrnBlack
            return
        }

        if let saved = UserDefaults.standard.string(forKey: "app_theme_style"),
           let style = AppThemeStyle(rawValue: saved) {
            self.current = style
        } else {
            // По умолчанию фирменная глубокая чёрная тема REBRN
            self.current = .rebrnBlack
        }
    }
}
