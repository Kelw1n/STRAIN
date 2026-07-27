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
        }
        .modelContainer(for: [ProgramProfile.self])
        .onChange(of: scenePhase) { _, phase in
            // После фона пересчитываем остаток от даты окончания, а не от тиков.
            if phase == .active { restTimer.refresh() }
        }
    }
}
