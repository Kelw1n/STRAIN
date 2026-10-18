import SwiftUI

// MARK: - Палитра

enum Theme {
    static var style: AppThemeStyle { ThemeManager.shared.current }

    /// Гарнитура цифр и заголовков. У REBRN плотный гротеск без скруглений,
    /// у остальных тем — прежний скруглённый.
    static var fontDesign: Font.Design { style.isRebrn ? .default : .rounded }

    static var accent: Color {
        if style.isRebrn {
            // Фирменный неоново-пламенный оранжевый REBRN (#FF6029)
            return Color(red: 1.0, green: 0.38, blue: 0.16)
        } else if style.isClaude {
            // Фирменный теплый терракотово-оранжевый Claude (#D97757)
            return Color(red: 0.851, green: 0.467, blue: 0.341)
        } else {
            return Color(red: 0.16, green: 0.84, blue: 0.76)
        }
    }

    static var accentDeep: Color {
        if style.isRebrn {
            // Глубокий тлеющий янтарно-оранжевый
            return Color(red: 0.88, green: 0.28, blue: 0.10)
        } else if style.isClaude {
            // Глубокий оттенок теплой глины / янтаря (#C16141)
            return Color(red: 0.757, green: 0.380, blue: 0.255)
        } else {
            return Color(red: 0.35, green: 0.47, blue: 0.98)
        }
    }

    static var success: Color {
        if style.isRebrn {
            // Мятно-зеленый индикатор выполнения
            return Color(red: 0.18, green: 0.82, blue: 0.45)
        } else if style.isClaude {
            // Мягкий шалфейный зеленый (#4CB079)
            return Color(red: 0.298, green: 0.686, blue: 0.475)
        } else {
            return Color(red: 0.27, green: 0.85, blue: 0.53)
        }
    }

    static var warning: Color {
        if style.isRebrn {
            return Color(red: 1.0, green: 0.65, blue: 0.20)
        } else if style.isClaude {
            // Тёплый янтарь (#EBA048)
            return Color(red: 0.922, green: 0.627, blue: 0.282)
        } else {
            return Color(red: 1.00, green: 0.64, blue: 0.26)
        }
    }

    static var record: Color {
        if style.isRebrn {
            return Color(red: 1.0, green: 0.32, blue: 0.32)
        } else if style.isClaude {
            // Благородная ржавчина (#E25E4C)
            return Color(red: 0.886, green: 0.369, blue: 0.298)
        } else {
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
        if style.isRebrn {
            // У REBRN карточки держатся на контрасте с чёрным фоном, без обводки.
            return LinearGradient(colors: [.clear], startPoint: .top, endPoint: .bottom)
        } else if style.isClaude {
            return LinearGradient(
                colors: scheme == .dark
                    ? [Color(red: 0.35, green: 0.33, blue: 0.31).opacity(0.8), Color(red: 0.25, green: 0.23, blue: 0.21).opacity(0.4)]
                    : [Color(red: 0.88, green: 0.84, blue: 0.80), Color(red: 0.93, green: 0.90, blue: 0.87).opacity(0.6)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        } else {
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
        if style.isRebrn {
            // Графитовые карточки REBRN (#161617) на чистом чёрном
            return Color(red: 0.086, green: 0.086, blue: 0.09)
        } else if style.isClaude {
            return scheme == .dark
                ? Color(red: 0.155, green: 0.147, blue: 0.140)
                : Color(red: 1.0, green: 0.996, blue: 0.992)
        } else {
            return scheme == .dark ? Color.white.opacity(0.055) : Color.white.opacity(0.78)
        }
    }
}

// MARK: - Анимации

/// Пружины почти без перелёта: экран не качается и не «дёргается»
/// после движения, а быстро встаёт на место.
enum Motion {
    static let card = Animation.spring(response: 0.36, dampingFraction: 0.92)
    static let snappy = Animation.spring(response: 0.28, dampingFraction: 0.9)
    /// Чуть живее — для отметок и наград, но без заметного качания.
    static let bouncy = Animation.spring(response: 0.34, dampingFraction: 0.78)
    static let smooth = Animation.easeInOut(duration: 0.22)
    /// Появление и уход крупных панелей: тренировка, листы, плашки.
    static let sheet = Animation.spring(response: 0.36, dampingFraction: 0.92)
    /// Перестановка и отклик на палец.
    static let reorder = Animation.spring(response: 0.26, dampingFraction: 0.86)
    /// Переключение вкладок.
    static let tab = Animation.easeOut(duration: 0.2)
    /// Нажатие: кнопка прижимается сразу, без задержки.
    static let press = Animation.spring(response: 0.16, dampingFraction: 0.95)
    /// Отпускание: мягко пружинит назад с едва заметным отскоком.
    static let release = Animation.spring(response: 0.34, dampingFraction: 0.68)

    static func maybe(_ animation: Animation, reduce: Bool) -> Animation? {
        reduce ? nil : animation
    }
}

// MARK: - Фон приложения

struct AppBackground: View {
    @Environment(\.colorScheme) private var scheme

    private var base: Color {
        if Theme.style.isRebrn {
            // Чистый OLED-чёрный, как у REBRN
            return .black
        } else if Theme.style.isClaude {
            // Тёплый молочный пергамент (#FAF7F2) в светлой, и тёплый матовый уголь (#151413) в тёмной
            return scheme == .dark ? Color(red: 0.086, green: 0.082, blue: 0.078) : Color(red: 0.980, green: 0.969, blue: 0.949)
        } else {
            return scheme == .dark ? Color(red: 0.043, green: 0.055, blue: 0.075) : Color(red: 0.945, green: 0.957, blue: 0.973)
        }
    }

    private var glow: Double {
        if Theme.style.isRebrn {
            return 0.08
        } else if Theme.style.isClaude {
            return scheme == .dark ? 0.18 : 0.12
        } else {
            return scheme == .dark ? 0.26 : 0.20
        }
    }

    var body: some View {
        ZStack {
            base
            if Theme.style.isRebrn {
                // Без свечения: у REBRN фон пустой, акцент живёт только в элементах.
                EmptyView()
            } else {
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
