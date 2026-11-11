
import AppKit

@MainActor
final class AppDelegate: NSObject, NSApplicationDelegate {
    let server = ServerManager()

    func applicationDidFinishLaunching(_ notification: Notification) {
        server.start()
    }

    func applicationWillTerminate(_ notification: Notification) {
        server.stop()
    }
}
