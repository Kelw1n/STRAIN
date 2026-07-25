import SwiftUI
import SwiftData

@main
struct TexasProgramApp: App {
    var body: some Scene {
        WindowGroup {
            RootView()
        }
        .modelContainer(for: [ProgramProfile.self])
    }
}
