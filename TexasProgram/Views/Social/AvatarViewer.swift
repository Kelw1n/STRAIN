import SwiftUI
import UIKit

// MARK: - Аватарка на весь экран

/// Аватарка на весь экран, как в Telegram и WhatsApp: зажми или нажми на неё
/// в профиле. Фото вырастает из аватарки, щипок приближает, двойное касание —
/// ×2,5, смахивание вниз или вверх закрывает.
struct AvatarViewer: View {
    let imageId: String
    let username: String
    var onClose: () -> Void

    @State private var appeared = false
    @State private var scale: CGFloat = 1
    @State private var baseScale: CGFloat = 1
    @State private var pan: CGSize = .zero
    @State private var basePan: CGSize = .zero
    /// Смахивание без приближения: фото едет за пальцем, фон гаснет.
    @State private var pull: CGSize = .zero
    @State private var closing = false

    static let maxScale: CGFloat = 4
    static let doubleTapScale: CGFloat = 2.5

    private var pullProgress: CGFloat { min(1, abs(pull.height) / 320) }

    var body: some View {
        GeometryReader { proxy in
            let side = proxy.size.width
            ZStack {
                Color.black
                    .opacity((appeared ? 1 : 0) * (1 - pullProgress * 0.75))
                    .ignoresSafeArea()
                    .onTapGesture(perform: close)
                photo(side: side)
                    .scaleEffect(appeared ? scale * (1 - pullProgress * 0.2) : 0.35)
                    .offset(x: pan.width + pull.width, y: pan.height + pull.height)
                    .opacity(appeared ? 1 : 0)
                    .gesture(drag(side: side, height: proxy.size.height))
                    .simultaneousGesture(magnify(side: side, height: proxy.size.height))
                    .onTapGesture(count: 2) { toggleZoom() }
            }
            .frame(width: proxy.size.width, height: proxy.size.height)
        }
        .ignoresSafeArea()
        .overlay(alignment: .top) { header }
        .preferredColorScheme(.dark)
        .onAppear {
            withAnimation(.spring(response: 0.38, dampingFraction: 0.86)) { appeared = true }
        }
    }

    // Пока грузится чёткая, под ней — маленькая аватарка из кэша профиля:
    // фото видно сразу, без серой заглушки.
    private func photo(side: CGFloat) -> some View {
        ZStack {
            RemoteImage(id: imageId, maxPoints: 118)
            RemoteImage(id: imageId, maxPoints: side, showsPlaceholder: false)
        }
        .frame(width: side, height: side)
        .clipped()
        .contentShape(Rectangle())
        .accessibilityElement()
        .accessibilityLabel("Аватарка @\(username)")
        .accessibilityAddTraits(.isImage)
    }

    private var header: some View {
        HStack {
            Text("@\(username)")
                .font(.system(size: 17, weight: .bold))
                .foregroundStyle(.white)
                .lineLimit(1)
            Spacer()
            Button(action: close) {
                Image(systemName: "xmark")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 40, height: 40)
                    .chatGlass(Circle())
            }
            .accessibilityLabel("Закрыть")
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .opacity(appeared ? 1 - min(1, pullProgress * 3) : 0)
    }

    // MARK: Жесты

    private func drag(side: CGFloat, height: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 4)
            .onChanged { value in
                guard !closing else { return }
                if baseScale > 1.01 {
                    pan = clamped(CGSize(width: basePan.width + value.translation.width,
                                         height: basePan.height + value.translation.height),
                                  scale: scale, side: side, height: height)
                } else {
                    pull = value.translation
                }
            }
            .onEnded { value in
                guard !closing else { return }
                if baseScale > 1.01 {
                    basePan = pan
                    return
                }
                let flung = abs(value.predictedEndTranslation.height) > 360
                if abs(value.translation.height) > 120 || flung {
                    close()
                } else {
                    withAnimation(Motion.snappy) { pull = .zero }
                }
            }
    }

    private func magnify(side: CGFloat, height: CGFloat) -> some Gesture {
        MagnifyGesture()
            .onChanged { value in
                guard !closing else { return }
                pull = .zero
                scale = min(Self.maxScale, max(1, baseScale * value.magnification))
            }
            .onEnded { _ in
                guard !closing else { return }
                baseScale = scale
                withAnimation(Motion.snappy) {
                    if scale <= 1.01 {
                        scale = 1
                        baseScale = 1
                        pan = .zero
                    } else {
                        pan = clamped(pan, scale: scale, side: side, height: height)
                    }
                }
                basePan = pan
            }
    }

    private func toggleZoom() {
        guard !closing else { return }
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        withAnimation(Motion.card) {
            scale = baseScale > 1.01 ? 1 : Self.doubleTapScale
            baseScale = scale
            pan = .zero
            basePan = .zero
            pull = .zero
        }
    }

    /// Приближенное фото не уезжает краями внутрь экрана.
    private func clamped(_ offset: CGSize, scale: CGFloat, side: CGFloat, height: CGFloat) -> CGSize {
        let maxX = max(0, side * (scale - 1) / 2)
        let maxY = max(0, (side * scale - height) / 2)
        return CGSize(width: min(maxX, max(-maxX, offset.width)), height: min(maxY, max(-maxY, offset.height)))
    }

    private func close() {
        guard !closing else { return }
        closing = true
        withAnimation(.easeIn(duration: 0.18)) {
            appeared = false
            scale = 1
            pan = .zero
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) { onClose() }
    }
}

extension View {
    /// Аватарка на весь экран поверх всего. Системная анимация листа выключена:
    /// фото само вырастает из аватарки и гаснет обратно.
    func avatarViewer(isPresented: Binding<Bool>, imageId: String?, username: String) -> some View {
        fullScreenCover(isPresented: isPresented) {
            if let imageId {
                AvatarViewer(imageId: imageId, username: username) {
                    var still = Transaction()
                    still.disablesAnimations = true
                    withTransaction(still) { isPresented.wrappedValue = false }
                }
                .presentationBackground(.clear)
            }
        }
    }
}

/// Открыть аватарку без анимации листа — свою анимацию рисует сам просмотр.
func presentWithoutAnimation(_ change: () -> Void) {
    var still = Transaction()
    still.disablesAnimations = true
    withTransaction(still, change)
}
