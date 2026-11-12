
import SwiftUI

@main
struct GoRemoteApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self)
    private var appDelegate

    var body: some Scene {
        MenuBarExtra {
            MenuBarView(server: appDelegate.server)
        } label: {
            Image(systemName: "display.2")
        }
        .menuBarExtraStyle(.window)
    }
}
