
import SwiftUI

@main
struct MacRemoteApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self)
    private var appDelegate

    var body: some Scene {
        MenuBarExtra {
            MenuBarView(server: appDelegate.server)
        } label: {
            Label(
                "GoRemote",
                systemImage: appDelegate.server.isRunning
                    ? "dot.radiowaves.left.and.right"
                    : "wifi.slash"
            )
        }
        .menuBarExtraStyle(.window)
    }
}
