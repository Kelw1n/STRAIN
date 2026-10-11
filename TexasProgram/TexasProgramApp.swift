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
            if phase == .active {
                restTimer.refresh()
                WorkoutSessionManager.shared.refreshElapsed()
            }
        }
    }

    /// Разбираем по хосту, а не поиском подстроки по всей ссылке: в ссылке
    /// на бро есть имя и случайный ID, и «add» или «stop» внутри них
    /// превращали приглашение в «+30 секунд к отдыху».
    private func handleDeepLink(_ url: URL) {
        switch url.host?.lowercased() {
        case "timer":
            let action = url.lastPathComponent.lowercased()
            switch action {
            case "minus30", "sub30":
                restTimer.add(-30)
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            case "add30", "add":
                restTimer.add(30)
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            case "stop", "finish", "skip":
                restTimer.stop()
                UINotificationFeedbackGenerator().notificationOccurred(.success)
            default:
                break
            }
        case "bro":
            Task {
                _ = await BroTrackerService.shared.addBuddy(from: url.absoluteString)
            }
        default:
            break
        }
    }
}
