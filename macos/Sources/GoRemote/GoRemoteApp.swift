
import SwiftUI

@main
struct GoRemoteApp: App {
    @StateObject private var server = ServerManager()

    var body: some Scene {
        MenuBarExtra {
            MenuBarView(server: server)
        } label: {
            Label(
                "GoRemote",
                systemImage: server.isRunning
                    ? "dot.radiowaves.left.and.right"
                    : "wifi.slash"
            )
        }
        .menuBarExtraStyle(.window)
    }
}
