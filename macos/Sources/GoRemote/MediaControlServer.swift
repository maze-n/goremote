import Foundation
import GRPCCore
import GRPCNIOTransportHTTP2

@MainActor
final class MediaControlServer {

    private var task: Task<Void, Never>?

    func start() {
        guard task == nil else {
            return
        }

        let server = GRPCServer(
            transport: .http2NIOTS(
                address: .ipv4(
                    host: "127.0.0.1",
                    port: 8092
                ),
                transportSecurity: .plaintext
            ),
            services: [
                MediaControlService()
            ]
        )

        task = Task {
            do {
                try await server.serve()
            } catch is CancellationError {
                // Expected when the application shuts down.
            } catch {
                NSLog(
                    "MediaControlServer failed: %@",
                    error.localizedDescription
                )
            }
        }
    }

    func stop() {
        task?.cancel()
        task = nil
    }
}