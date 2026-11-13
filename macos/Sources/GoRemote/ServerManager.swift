import Foundation
import Combine

@MainActor
final class ServerManager: ObservableObject {
    @Published private(set) var isRunning = false
    @Published var errorMessage: String?

    private var process: Process?

    func start() {
        guard !isRunning else {
            return
        }

        guard let executableURL = Bundle.main.executableURL else {
            errorMessage = "Cannot locate application executable"
            return
        }

        let directory = executableURL.deletingLastPathComponent()
        let serverURL = directory.appendingPathComponent("goremote-server")

        guard FileManager.default.isExecutableFile(atPath: serverURL.path) else {
            errorMessage = "Go server executable not found"
            return
        }

        let child = Process()
        child.executableURL = serverURL

        child.standardOutput = FileHandle.nullDevice
        child.standardError = FileHandle.nullDevice

        child.terminationHandler = { [weak self] process in
            Task { @MainActor in
                self?.isRunning = false
                self?.process = nil

                if process.terminationStatus != 0 {
                    self?.errorMessage = "Go server exited unexpectedly"
                }
            }
        }

        do {
            try child.run()
            process = child
            isRunning = true
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func stop() {
        guard let process, process.isRunning else {
            isRunning = false
            return
        }

        process.terminate()
    }
}