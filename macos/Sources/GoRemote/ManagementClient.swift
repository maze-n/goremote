
import Foundation
import GRPCCore
import GRPCNIOTransportHTTP2

enum ManagementClient {
    private static let host = "127.0.0.1"
    private static let port = 8091

    static func startPairing() async throws -> (
        code: String,
        expiresInSeconds: Int
    ) {
        try await withGRPCClient(
            transport: .http2NIOTS(
                target: .ipv4(address: host, port: port),
                transportSecurity: .plaintext
            )
        ) { client in
            let service = Goremote_V1_ManagementService.Client(
                wrapping: client
            )

            let request = Goremote_V1_StartPairingRequest()
            let response = try await service.startPairing(request)

            return (
                code: response.code,
                expiresInSeconds: Int(response.expiresInSeconds)
            )
        }
    }
}
