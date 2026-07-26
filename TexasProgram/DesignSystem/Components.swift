import SwiftUI

// MARK: - Карточка

struct CardView<Content: View>: View {
    @Environment(\.colorScheme) private var scheme
    private let padding: CGFloat
    private let spacing: CGFloat
    private let cornerRadius: CGFloat
    private let content: Content

    init(padding: CGFloat = 18, spacing: CGFloat = 14, cornerRadius: CGFloat = 24, @ViewBuilder content: () -> Content) {
        self.padding = padding
        self.spacing = spacing
        self.cornerRadius = cornerRadius
        self.content = content()
    }

    private var shape: RoundedRectangle { RoundedRectangle(cornerRadius: cornerRadius, style: .continuous) }

    var body: some View {
        VStack(alignment: .leading, spacing: spacing) { content }
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.surface(scheme), in: shape)
            .overlay(shape.strokeBorder(Theme.hairline(scheme), lineWidth: 1))
    }
}

/// Карточка с подсвеченной градиентной рамкой — для «следующего» элемента.
/// Свечение статичное: анимация радиуса тени пересобирала слой на каждом кадре.
struct HighlightCard<Content: View>: View {
    @Environment(\.colorScheme) private var scheme
    private let gradient: LinearGradient
    private let glow: Color
    private let content: Content

    init(gradient: LinearGradient = Theme.accentGradient, glow: Color = Theme.accent, @ViewBuilder content: () -> Content) {
        self.gradient = gradient
        self.glow = glow
        self.content = content()
    }

    private var shape: RoundedRectangle { RoundedRectangle(cornerRadius: 26, style: .continuous) }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) { content }
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.surface(scheme), in: shape)
            .overlay(shape.strokeBorder(gradient, lineWidth: 1.6))
            .shadow(color: glow.opacity(scheme == .dark ? 0.28 : 0.16), radius: 14, y: 6)
    }
}

// MARK: - Кнопки

struct PressableStyle: ButtonStyle {
    var scale: CGFloat = 0.97

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? scale : 1)
            .opacity(configuration.isPressed ? 0.9 : 1)
            .animation(.spring(response: 0.3, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

extension ButtonStyle where Self == PressableStyle {
    static var pressable: PressableStyle { PressableStyle() }
}

struct GradientButtonStyle: ButtonStyle {
    var gradient: LinearGradient = Theme.accentGradient
    var glow: Color = Theme.accent

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .foregroundStyle(.white)
            .padding(.vertical, 16)
            .frame(maxWidth: .infinity)
            .background(gradient, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .shadow(color: glow.opacity(configuration.isPressed ? 0.15 : 0.35), radius: configuration.isPressed ? 6 : 16, y: 8)
            .scaleEffect(configuration.isPressed ? 0.975 : 1)
            .animation(.spring(response: 0.3, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

extension ButtonStyle where Self == GradientButtonStyle {
    static var gradientProminent: GradientButtonStyle { GradientButtonStyle() }
}

// MARK: - Кольцо прогресса

struct RingProgress<Label: View>: View {
    private let value: Double
    private let lineWidth: CGFloat
    private let gradient: LinearGradient
    private let label: Label
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var animated: Double = 0

    init(value: Double, lineWidth: CGFloat = 14, gradient: LinearGradient = Theme.accentGradient, @ViewBuilder label: () -> Label) {
        self.value = value.isFinite ? min(max(value, 0), 1) : 0
        self.lineWidth = lineWidth
        self.gradient = gradient
        self.label = label()
    }

    var body: some View {
        ZStack {
            Circle().stroke(Color.primary.opacity(0.09), style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
            Circle()
                .trim(from: 0, to: animated)
                .stroke(gradient, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
            label
        }
        .onAppear {
            if reduceMotion { animated = value } else {
                withAnimation(.easeOut(duration: 1.0).delay(0.15)) { animated = value }
            }
        }
        .onChange(of: value) { _, newValue in
            withAnimation(Motion.maybe(Motion.card, reduce: reduceMotion)) { animated = newValue }
        }
    }
}

// MARK: - Линейный индикатор

struct LineMeter: View {
    let value: Double
    var gradient: LinearGradient = Theme.accentGradient
    var height: CGFloat = 8
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var animated: Double = 0

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .leading) {
                Capsule().fill(Color.primary.opacity(0.09))
                Capsule()
                    .fill(gradient)
                    .frame(width: max(0, proxy.size.width * min(max(animated, 0), 1)))
            }
        }
        .frame(height: height)
        .onAppear {
            if reduceMotion { animated = value } else {
                withAnimation(.easeOut(duration: 0.8).delay(0.1)) { animated = value }
            }
        }
        .onChange(of: value) { _, newValue in
            withAnimation(Motion.maybe(Motion.card, reduce: reduceMotion)) { animated = newValue }
        }
    }
}

// MARK: - Бейдж

struct TagBadge: View {
    let text: String
    var systemImage: String?
    var gradient: LinearGradient = Theme.accentGradient

    var body: some View {
        HStack(spacing: 5) {
            if let systemImage { Image(systemName: systemImage).font(.caption2.weight(.bold)) }
            Text(text).font(.caption.weight(.semibold))
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .foregroundStyle(.white)
        .background(gradient, in: Capsule())
    }
}

struct OutlineBadge: View {
    let text: String
    var tint: Color = Theme.accent

    var body: some View {
        Text(text)
            .font(.caption.weight(.semibold))
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .foregroundStyle(tint)
            .background(tint.opacity(0.14), in: Capsule())
            .overlay(Capsule().strokeBorder(tint.opacity(0.35), lineWidth: 1))
    }
}

// MARK: - Заголовок экрана

struct ScreenHeader: View {
    let eyebrow: String
    let title: String
    var subtitle: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(eyebrow.uppercased())
                .font(.caption.weight(.bold))
                .tracking(1.4)
                .foregroundStyle(Theme.accentGradient)
            Text(title)
                .font(.system(size: 32, weight: .bold, design: .rounded))
            if let subtitle {
                Text(subtitle).font(.subheadline).foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - Появление и прокрутка

/// Каскадное появление. Без `blur`: размытие рендерится вне экрана и на списке
/// из десятка карточек съедает кадры ровно в момент открытия экрана.
private struct AppearIn: ViewModifier {
    let index: Int
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shown = false

    func body(content: Content) -> some View {
        content
            .opacity(shown ? 1 : 0)
            .offset(y: shown ? 0 : 14)
            .onAppear {
                guard !shown else { return }
                if reduceMotion {
                    shown = true
                } else {
                    let delay = Double(min(index, 6)) * 0.045
                    withAnimation(.spring(response: 0.5, dampingFraction: 0.86).delay(delay)) { shown = true }
                }
            }
    }
}

/// Затухание карточек у края экрана. Только opacity и scale — обе операции
/// выполняет композитор, в отличие от размытия.
private struct SoftScroll: ViewModifier {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @ViewBuilder
    func body(content: Content) -> some View {
        if reduceMotion {
            content
        } else {
            content.scrollTransition(.interactive, axis: .vertical) { view, phase in
                view
                    .opacity(phase.isIdentity ? 1 : 0.35)
                    .scaleEffect(phase.isIdentity ? 1 : 0.95)
            }
        }
    }
}

extension View {
    /// Каскадное появление элемента с задержкой по индексу.
    func appearIn(_ index: Int) -> some View { modifier(AppearIn(index: index)) }

    /// Мягкое затухание и уменьшение при уходе за край экрана.
    func softScroll() -> some View { modifier(SoftScroll()) }
}
