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
                        .font(.system(size: 15, weight: .black, design: .rounded))
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
                                withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                                    dragOffset = maxDrag
                                    isConfirmed = true
                                }
                                UINotificationFeedbackGenerator().notificationOccurred(.warning)
                                DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                                    onConfirm()
                                }
                            } else {
                                withAnimation(.spring(response: 0.35, dampingFraction: 0.75)) {
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
