import SwiftUI
import SwiftData

@main
struct TexasProgramApp: App {
    /// Таймер живёт над экранами: отсчёт не должен сбрасываться при переходе по вкладкам.
    @State private var restTimer = RestTimer()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(restTimer)
                .onOpenURL { url in
                    handleDeepLink(url)
                }
        }
        .modelContainer(for: [ProgramProfile.self])
        .onChange(of: scenePhase) { _, phase in
            // После фона пересчитываем остаток от даты окончания, а не от тиков.
            if phase == .active { restTimer.refresh() }
        }
    }

    private func handleDeepLink(_ url: URL) {
        let path = url.absoluteString.lowercased()
        if path.contains("add30") || path.contains("add") {
            restTimer.add(30)
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        } else if path.contains("stop") || path.contains("finish") {
            restTimer.stop()
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        }
    }
}
