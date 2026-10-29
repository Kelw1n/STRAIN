import SwiftUI

// MARK: - Стили Жидкого Стекла (Liquid Glass)

struct LiquidGlassBackground: ViewModifier {
    @Environment(\.colorScheme) private var scheme
    var cornerRadius: CGFloat = 32
    var glowColor: Color = Theme.accent
    var glowIntensity: Double = 0.15

    func body(content: Content) -> some View {
        content
            .background {
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .fill(.ultraThinMaterial)
                    .overlay {
                        // Глубокий темный оттенок стекла
                        RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                            .fill(scheme == .dark ? Color.black.opacity(0.65) : Color.white.opacity(0.40))
                    }
                    .overlay {
                        // Тонкий градиентный контур (спекулярный блик)
                        RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                            .strokeBorder(
                                LinearGradient(
                                    stops: [
                                        .init(color: .white.opacity(scheme == .dark ? 0.35 : 0.70), location: 0.0),
                                        .init(color: .white.opacity(0.10), location: 0.3),
                                        .init(color: .clear, location: 0.6),
                                        .init(color: .white.opacity(scheme == .dark ? 0.15 : 0.30), location: 1.0)
                                    ],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                ),
                                lineWidth: 1
                            )
                    }
                    .shadow(color: Color.black.opacity(scheme == .dark ? 0.40 : 0.12), radius: 20, x: 0, y: 10)
                    .shadow(color: glowColor.opacity(scheme == .dark ? glowIntensity : glowIntensity * 0.5), radius: 12, x: 0, y: 4)
            }
    }
}

extension View {
    func liquidGlass(cornerRadius: CGFloat = 32, glowColor: Color = Theme.accent, glowIntensity: Double = 0.15) -> some View {
        modifier(LiquidGlassBackground(cornerRadius: cornerRadius, glowColor: glowColor, glowIntensity: glowIntensity))
    }
}

// MARK: - Прозрачное стекло, как в iOS 26

/// Настройка «Прозрачное стекло, как в iOS 26».
///
/// В iOS 27 Apple по умолчанию сделала жидкое стекло матовее: системное
/// `.regular` сильнее размывает то, что под ним, и панели выглядят запотевшими.
/// Прозрачное `.clear` осталось таким, как в iOS 26: сквозь него видно ленту,
/// а края преломляют свет. Включено — панель вкладок и кнопки поверх экрана из
/// `.clear`, выключено — из системного `.regular`, как задумано в iOS 27.
/// До iOS 26 и в сборке без её SDK стекло нарисовано, и настройка ни на что не влияет.
enum GlassPreference {
    static let storageKey = "glass_clear_enabled"

    /// Затемнение прозрачного стекла под текстом и иконками. Без него белое на
    /// светлом фото не прочитать, а с более сильным стекло превращается в плашку.
    static let dimming: Double = 0.3

    /// Есть ли настоящее стекло: собрано Xcode 26 и запущено на iOS 26.
    static var isSupported: Bool {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) { return true }
        #endif
        return false
    }
}

#if compiler(>=6.2)
@available(iOS 26.0, *)
extension GlassPreference {
    /// Стекло плавающей плашки или кнопки. Затемнение — только у прозрачного:
    /// матовое и так глушит то, что под ним.
    static func glass(clear: Bool, dimming: Double = GlassPreference.dimming, interactive: Bool = false) -> Glass {
        var glass = clear ? Glass.clear : Glass.regular
        if clear && dimming > 0 {
            glass = glass.tint(Color.black.opacity(dimming))
        }
        if interactive {
            glass = glass.interactive()
        }
        return glass
    }
}
#endif

/// Стекло плавающей плашки или кнопки. Модификатор, а не функция: у него свой
/// `@AppStorage`, и переключатель в настройках меняет стекло сразу, без перезапуска.
struct FloatingGlass<S: Shape>: ViewModifier {
    private let shape: S
    private let enabled: Bool
    private let interactive: Bool
    private let dimming: Double
    @AppStorage(GlassPreference.storageKey) private var clearGlass = true

    init(shape: S, enabled: Bool, interactive: Bool, dimming: Double) {
        self.shape = shape
        self.enabled = enabled
        self.interactive = interactive
        self.dimming = dimming
    }

    @ViewBuilder
    func body(content: Content) -> some View {
        if !enabled {
            content
        } else {
            #if compiler(>=6.2)
            if #available(iOS 26.0, *) {
                content.glassEffect(
                    GlassPreference.glass(clear: clearGlass, dimming: dimming, interactive: interactive),
                    in: shape
                )
            } else {
                material(content)
            }
            #else
            material(content)
            #endif
        }
    }

    /// До iOS 26 — матовое стекло с тонкой кромкой.
    private func material(_ content: Content) -> some View {
        content
            .background(.ultraThinMaterial, in: shape)
            .overlay(shape.stroke(Color.white.opacity(0.12), lineWidth: 1))
    }
}

extension View {
    /// Жидкое стекло под плавающей плашкой или кнопкой поверх экрана: в переписке,
    /// в просмотре фото, над панелью вкладок. Прозрачное, как в iOS 26, или
    /// матовое — по настройке; до iOS 26 — матовое стекло с тонкой кромкой.
    ///
    /// `interactive` — стекло само пружинит и подсвечивается под пальцем, как
    /// системные кнопки. Только для обычных кнопок: у вью со своим ведением пальца
    /// (микрофон, панель вкладок) оно могло бы забрать касание себе.
    /// Внутрь ячеек прокрутки стекло не ставить: на каждой ячейке оно считало бы
    /// фон заново, и прокрутка тормозила бы.
    func chatGlass<S: Shape>(
        _ shape: S,
        enabled: Bool = true,
        interactive: Bool = false,
        dimming: Double = GlassPreference.dimming
    ) -> some View {
        modifier(FloatingGlass(shape: shape, enabled: enabled, interactive: interactive, dimming: dimming))
    }
}

/// Соседние стеклянные кнопки и плашки — в одном `GlassEffectContainer`.
///
/// Стекло тяжёлое для видеокарты: в общем контейнере система рисует соседей за
/// один проход с общим фоном, а не считает размытие под каждой кнопкой заново.
/// Заодно стекло не пытается смотреть сквозь соседнее — этого оно не умеет.
/// `spacing` меньше зазоров между кнопками: на таком расстоянии стекло соседей
/// начинает сливаться в одну каплю, а в покое кнопки должны быть отдельными.
struct GlassGroup<Content: View>: View {
    private let spacing: CGFloat
    private let content: Content

    init(spacing: CGFloat = 2, @ViewBuilder content: () -> Content) {
        self.spacing = spacing
        self.content = content()
    }

    var body: some View {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            GlassEffectContainer(spacing: spacing) { content }
        } else {
            content
        }
        #else
        content
        #endif
    }
}

// MARK: - Слайдер подтверждения удаления (Slide to Confirm)

struct SlideToConfirmView: View {
    let title: String
    let onConfirm: () -> Void

    @State private var dragOffset: CGFloat = 0
    @State private var isConfirmed = false
    private let height: CGFloat = 58
    private let thumbWidth: CGFloat = 84

    var body: some View {
        GeometryReader { proxy in
            let maxDrag = max(0, proxy.size.width - thumbWidth - 8)

            ZStack(alignment: .leading) {
                // Фоновая подложка слайдера
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .fill(Color(white: 0.16))
                    .overlay(
                        RoundedRectangle(cornerRadius: 22, style: .continuous)
                            .strokeBorder(Color.white.opacity(0.08), lineWidth: 1)
                    )

                // Текст по центру
                HStack {
                    Spacer(minLength: thumbWidth)
                    Text(title)
                        .font(.system(size: 15, weight: .black, design: Theme.fontDesign))
                        .tracking(2.0)
                        .foregroundStyle(Color.white.opacity(0.85))
                    Spacer()
                }

                // Ползунок
                HStack(spacing: 4) {
                    Image(systemName: "chevron.right.2")
                        .font(.system(size: 18, weight: .black))
                        .foregroundStyle(.black)
                }
                .frame(width: thumbWidth, height: height - 8)
                .background(Color.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                .shadow(color: Color.black.opacity(0.35), radius: 8, x: 2, y: 2)
                .offset(x: 4 + dragOffset)
                .gesture(
                    DragGesture()
                        .onChanged { value in
                            guard !isConfirmed else { return }
                            if value.translation.width > 0 {
                                dragOffset = min(value.translation.width, maxDrag)
                            }
                        }
                        .onEnded { value in
                            guard !isConfirmed else { return }
                            if dragOffset >= maxDrag * 0.75 {
                                withAnimation(.spring(response: 0.26, dampingFraction: 0.88)) {
                                    dragOffset = maxDrag
                                    isConfirmed = true
                                }
                                UINotificationFeedbackGenerator().notificationOccurred(.warning)
                                DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                                    onConfirm()
                                }
                            } else {
                                withAnimation(.spring(response: 0.28, dampingFraction: 0.9)) {
                                    dragOffset = 0
                                }
                            }
                        }
                )
            }
        }
        .frame(height: height)
    }
}
