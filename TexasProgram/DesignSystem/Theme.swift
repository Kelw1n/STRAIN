import SwiftUI

// MARK: - Палитра

enum Theme {
    static var style: AppThemeStyle { ThemeManager.shared.current }

    static var accent: Color {
        switch style {
        case .claude:
            // Фирменный теплый терракотово-оранжевый Claude (#D97757)
            return Color(red: 0.851, green: 0.467, blue: 0.341)
        case .strain:
            return Color(red: 0.16, green: 0.84, blue: 0.76)
        }
    }

    static var accentDeep: Color {
        switch style {
        case .claude:
            // Глубокий оттенок теплой глины / янтаря (#C16141)
            return Color(red: 0.757, green: 0.380, blue: 0.255)
        case .strain:
            return Color(red: 0.35, green: 0.47, blue: 0.98)
        }
    }

    static var success: Color {
        switch style {
        case .claude:
            // Мягкий шалфейный зеленый (#4CB079)
            return Color(red: 0.298, green: 0.686, blue: 0.475)
        case .strain:
            return Color(red: 0.27, green: 0.85, blue: 0.53)
        }
    }

    static var warning: Color {
        switch style {
        case .claude:
            // Тёплый янтарь (#EBA048)
            return Color(red: 0.922, green: 0.627, blue: 0.282)
        case .strain:
            return Color(red: 1.00, green: 0.64, blue: 0.26)
        }
    }

    static var record: Color {
        switch style {
        case .claude:
            // Благородная ржавчина (#E25E4C)
            return Color(red: 0.886, green: 0.369, blue: 0.298)
        case .strain:
            return Color(red: 1.00, green: 0.42, blue: 0.52)
        }
    }

    static var accentGradient: LinearGradient {
        LinearGradient(colors: [accent, accentDeep], startPoint: .topLeading, endPoint: .bottomTrailing)
    }

    static var deepGradient: LinearGradient {
        LinearGradient(colors: [accentDeep, accent], startPoint: .topLeading, endPoint: .bottomTrailing)
    }

    static var successGradient: LinearGradient {
        LinearGradient(colors: [success, accent], startPoint: .topLeading, endPoint: .bottomTrailing)
    }

    static var recordGradient: LinearGradient {
        LinearGradient(colors: [warning, record], startPoint: .topLeading, endPoint: .bottomTrailing)
    }

    static func hairline(_ scheme: ColorScheme) -> LinearGradient {
        switch style {
        case .claude:
            return LinearGradient(
                colors: scheme == .dark
                    ? [Color(red: 0.35, green: 0.33, blue: 0.31).opacity(0.8), Color(red: 0.25, green: 0.23, blue: 0.21).opacity(0.4)]
                    : [Color(red: 0.88, green: 0.84, blue: 0.80), Color(red: 0.93, green: 0.90, blue: 0.87).opacity(0.6)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        case .strain:
            return LinearGradient(
                colors: scheme == .dark
                    ? [.white.opacity(0.16), .white.opacity(0.04)]
                    : [.white.opacity(0.95), .white.opacity(0.30)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        }
    }

    /// Заливка карточки в зависимости от темы и цветовой схемы.
    static func surface(_ scheme: ColorScheme) -> Color {
        switch style {
        case .claude:
            // В Claude светлый режим — нежнейший молочно-слоновый оттенок (#FFFEFC),
            // в тёмном — тёплый бархатный антрацит (#292725).
            return scheme == .dark ? Color(red: 0.161, green: 0.153, blue: 0.145) : Color(red: 1.0, green: 0.996, blue: 0.992)
        case .strain:
            return scheme == .dark ? Color.white.opacity(0.055) : Color.white.opacity(0.78)
        }
    }
}

// MARK: - Анимации

enum Motion {
    static let card = Animation.spring(response: 0.46, dampingFraction: 0.86)
    static let snappy = Animation.spring(response: 0.32, dampingFraction: 0.78)
    static let bouncy = Animation.spring(response: 0.42, dampingFraction: 0.62)
    static let smooth = Animation.easeInOut(duration: 0.28)

    static func maybe(_ animation: Animation, reduce: Bool) -> Animation? {
        reduce ? nil : animation
    }
}

// MARK: - Фон приложения

struct AppBackground: View {
    @Environment(\.colorScheme) private var scheme

    private var base: Color {
        switch Theme.style {
        case .claude:
            // Тёплый молочный пергамент (#FAF7F2) в светлой, и тёплый матовый уголь (#1A1918) в тёмной
            return scheme == .dark ? Color(red: 0.102, green: 0.098, blue: 0.094) : Color(red: 0.980, green: 0.969, blue: 0.949)
        case .strain:
            return scheme == .dark ? Color(red: 0.043, green: 0.055, blue: 0.075) : Color(red: 0.945, green: 0.957, blue: 0.973)
        }
    }

    private var glow: Double {
        switch Theme.style {
        case .claude:
            return scheme == .dark ? 0.18 : 0.12
        case .strain:
            return scheme == .dark ? 0.26 : 0.20
        }
    }

    var body: some View {
        ZStack {
            base
            RadialGradient(
                colors: [Theme.accent.opacity(glow), .clear],
                center: UnitPoint(x: 0.08, y: 0.02),
                startRadius: 0,
                endRadius: 430
            )
            RadialGradient(
                colors: [Theme.accentDeep.opacity(glow * 0.8), .clear],
                center: UnitPoint(x: 0.98, y: 1.0),
                startRadius: 0,
                endRadius: 470
            )
            RadialGradient(
                colors: [Theme.record.opacity(glow * 0.4), .clear],
                center: UnitPoint(x: 1.0, y: 0.12),
                startRadius: 0,
                endRadius: 320
            )
        }
        .ignoresSafeArea()
    }
}

extension View {
    /// Общий фон экрана.
    func screenBackground() -> some View {
        background(AppBackground())
    }
}
