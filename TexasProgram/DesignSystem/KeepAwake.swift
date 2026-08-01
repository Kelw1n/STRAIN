import SwiftUI
import UIKit

/// Не даёт экрану гаснуть, пока открыт экран тренировки.
///
/// Флаг общий на всё приложение, поэтому считаем экраны: два открытых
/// одновременно не должны погасить его, когда закроется только один.
private final class AwakeCounter {
    static let shared = AwakeCounter()
    private var count = 0

    func retain() {
        count += 1
        UIApplication.shared.isIdleTimerDisabled = true
    }

    func release() {
        count = max(count - 1, 0)
        if count == 0 { UIApplication.shared.isIdleTimerDisabled = false }
    }
}

private struct KeepAwake: ViewModifier {
    let enabled: Bool
    @State private var holding = false

    func body(content: Content) -> some View {
        content
            .onAppear { sync(enabled) }
            .onDisappear { sync(false) }
            .onChange(of: enabled) { _, value in sync(value) }
    }

    private func sync(_ wanted: Bool) {
        guard wanted != holding else { return }
        holding = wanted
        if wanted { AwakeCounter.shared.retain() } else { AwakeCounter.shared.release() }
    }
}

extension View {
    /// Держит экран включённым, пока вид на экране и настройка включена.
    func keepAwake(_ enabled: Bool) -> some View {
        modifier(KeepAwake(enabled: enabled))
    }
}
