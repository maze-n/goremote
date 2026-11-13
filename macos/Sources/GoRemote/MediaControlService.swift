import AppKit
import GRPCCore
import GRPCNIOTransportHTTP2

struct MediaControlService:
    Goremote_V1_MediaControlService.SimpleServiceProtocol
{
    func playPause(
        request: Goremote_V1_MediaCommandRequest,
        context: ServerContext
    ) async throws -> Goremote_V1_MediaCommandResponse {
        let success = await MainActor.run {
            MediaKeyController.post(.playPause)
        }

        return response(success: success, message: "Play/Pause")
    }

    func next(
        request: Goremote_V1_MediaCommandRequest,
        context: ServerContext
    ) async throws -> Goremote_V1_MediaCommandResponse {
        let success = await MainActor.run {
            MediaKeyController.post(.next)
        }

        return response(success: success, message: "Next")
    }

    func previous(
        request: Goremote_V1_MediaCommandRequest,
        context: ServerContext
    ) async throws -> Goremote_V1_MediaCommandResponse {
        let success = await MainActor.run {
            MediaKeyController.post(.previous)
        }

        return response(success: success, message: "Previous")
    }

    private func response(
        success: Bool,
        message: String
    ) -> Goremote_V1_MediaCommandResponse {
        Goremote_V1_MediaCommandResponse.with {
            $0.success = success
            $0.message = message
        }
    }
}