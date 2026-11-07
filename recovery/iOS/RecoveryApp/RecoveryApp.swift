import SwiftUI

@main
struct RecoveryApp: App {
    @StateObject private var recovery = RecoveryManager()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView(recovery: recovery)
                .onChange(of: scenePhase) { phase in
                    recovery.setForeground(phase == .active)
                }
                .onAppear {
                    recovery.setForeground(scenePhase == .active)
                }
        }
    }
}
