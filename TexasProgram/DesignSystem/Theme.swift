import SwiftUI

// MARK: - Палитра

enum Theme {
    static let accent = Color(red: 0.16, green: 0.84, blue: 0.76)
    static let accentDeep = Color(red: 0.35, green: 0.47, blue: 0.98)
    static let success = Color(red: 0.27, green: 0.85, blue: 0.53)
    static let warning = Color(red: 1.00, green: 0.64, blue: 0.26)
    static let record = Color(red: 1.00, green: 0.42, blue: 0.52)

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
        LinearGradient(
            colors: scheme == .dark
                ? [.white.opacity(0.16), .white.opacity(0.04)]
                : [.white.opacity(0.95), .white.opacity(0.30)],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    /// Заливка карточки. Раньше здесь был `.ultraThinMaterial`: каждая карточка
    /// заставляла систему размывать фон под собой на каждом кадре прокрутки.
    static func surface(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? Color.white.opacity(0.055) : Color.white.opacity(0.78)
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

/// Статичные радиальные градиенты вместо размытых кругов с бесконечной анимацией:
/// `blur(radius: 120)` по трём слоям рендерился вне экрана каждый кадр, и так на каждой вкладке.
struct AppBackground: View {
    @Environment(\.colorScheme) private var scheme

    private var base: Color {
        scheme == .dark ? Color(red: 0.043, green: 0.055, blue: 0.075) : Color(red: 0.945, green: 0.957, blue: 0.973)
    }

    private var glow: Double { scheme == .dark ? 0.26 : 0.20 }

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
                colors: [Theme.accentDeep.opacity(glow), .clear],
                center: UnitPoint(x: 0.98, y: 1.0),
                startRadius: 0,
                endRadius: 470
            )
            RadialGradient(
                colors: [Theme.record.opacity(glow * 0.45), .clear],
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
