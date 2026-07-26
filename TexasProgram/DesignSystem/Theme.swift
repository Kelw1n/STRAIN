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

    static var successGradient: LinearGradient {
        LinearGradient(colors: [success, accent], startPoint: .topLeading, endPoint: .bottomTrailing)
    }

    static var recordGradient: LinearGradient {
        LinearGradient(colors: [warning, record], startPoint: .topLeading, endPoint: .bottomTrailing)
    }

    static func hairline(_ scheme: ColorScheme) -> LinearGradient {
        LinearGradient(
            colors: scheme == .dark
                ? [.white.opacity(0.22), .white.opacity(0.04)]
                : [.white.opacity(0.95), .white.opacity(0.30)],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
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
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var drift = false

    private var base: Color {
        scheme == .dark ? Color(red: 0.043, green: 0.055, blue: 0.075) : Color(red: 0.945, green: 0.957, blue: 0.973)
    }

    private var glowOpacity: Double { scheme == .dark ? 0.30 : 0.22 }

    var body: some View {
        ZStack {
            base
            Circle()
                .fill(Theme.accent.opacity(glowOpacity))
                .frame(width: 340, height: 340)
                .blur(radius: 120)
                .offset(x: drift ? -120 : -160, y: drift ? -300 : -350)
            Circle()
                .fill(Theme.accentDeep.opacity(glowOpacity))
                .frame(width: 380, height: 380)
                .blur(radius: 140)
                .offset(x: drift ? 150 : 120, y: drift ? 330 : 390)
            Circle()
                .fill(Theme.record.opacity(glowOpacity * 0.5))
                .frame(width: 260, height: 260)
                .blur(radius: 130)
                .offset(x: drift ? 170 : 130, y: drift ? -180 : -140)
        }
        .ignoresSafeArea()
        .onAppear {
            guard !reduceMotion, !drift else { return }
            withAnimation(.easeInOut(duration: 11).repeatForever(autoreverses: true)) { drift = true }
        }
    }
}

extension View {
    /// Общий фон экрана вместо системного systemGroupedBackground.
    func screenBackground() -> some View {
        background(AppBackground())
    }
}
