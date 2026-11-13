import AppKit

@MainActor
final class AppDelegate: NSObject, NSApplicationDelegate {
    let server = ServerManager()
    private let mediaControlServer = MediaControlServer()

    func applicationDidFinishLaunching(_ notification: Notification) {
        // Start the Swift-hosted media service first.
        mediaControlServer.start()

        // Then launch the Go backend.
        server.start()
    }

    func applicationWillTerminate(_ notification: Notification) {
        // Stop the Go backend before stopping its media-control dependency.
        server.stop()
        mediaControlServer.stop()
    }
}