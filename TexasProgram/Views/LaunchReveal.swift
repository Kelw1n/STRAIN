import SwiftUI
import UIKit

/// Открытие приложения, как у REBRN: на чёрном оранжевый знак, потом он
/// с ускорением растёт и становится окном, сквозь которое проступает приложение.
///
/// Первый кадр совпадает с системным экраном запуска (`TexasProgram-Info.plist`:
/// чёрный фон и тот же знак шириной 120 pt по центру), поэтому стыка не видно.
struct LaunchReveal: View {
    var onFinish: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var scale: CGFloat = 1
    @State private var fill: Double = 1
    @State private var fade: Double = 1

    /// Цвет знака на экране запуска — тот же, что в LaunchMark.
    private static let markColor = Color(red: 1.0, green: 0.376, blue: 0.161)
    private static let markWidth: CGFloat = 120
    private static let markImage = UIImage(named: "splash_mark")

    var body: some View {
        ZStack {
            // Чёрное всё, кроме знака: в нём дырка, сквозь неё видно приложение.
            Color.canvas
                .mask {
                    Rectangle()
                        .overlay { mark.blendMode(.destinationOut) }
                        .compositingGroup()
                }

            // Заливка знака: гаснет, как только знак начинает расти.
            mark
                .foregroundStyle(Self.markColor)
                .opacity(fill)
        }
        .ignoresSafeArea()
        .opacity(fade)
        .allowsHitTesting(fade > 0.5)
        .accessibilityHidden(true)
        .task { await run() }
    }

    @ViewBuilder
    private var mark: some View {
        if let image = Self.markImage {
            Image(uiImage: image)
                .renderingMode(.template)
                .resizable()
                .scaledToFit()
                .frame(width: Self.markWidth)
                .scaleEffect(scale)
        } else {
            Color.clear.frame(width: Self.markWidth, height: Self.markWidth * 0.44)
        }
    }

    private func run() async {
        // Пока знак стоит, под ним успевает собраться первый экран.
        try? await Task.sleep(for: .milliseconds(380))
        if reduceMotion {
            withAnimation(.easeOut(duration: 0.25)) { fade = 0 }
        } else {
            withAnimation(.easeIn(duration: 0.18)) { fill = 0 }
            withAnimation(.timingCurve(0.6, 0, 0.9, 0.5, duration: 0.55)) { scale = 24 }
            try? await Task.sleep(for: .milliseconds(400))
            withAnimation(.easeOut(duration: 0.18)) { fade = 0 }
        }
        try? await Task.sleep(for: .milliseconds(260))
        onFinish()
    }
}
