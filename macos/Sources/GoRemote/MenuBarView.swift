
import SwiftUI
import AppKit

struct MenuBarView: View {
    @ObservedObject var server: ServerManager

    @State private var pairingCode: String?
    @State private var pairingExpiresAt: Date?
    @State private var isGeneratingPairing = false
    @State private var errorMessage: String?

    var body: some View {
        VStack(spacing: 12) {

            // Server status
            HStack {
                Circle()
                    .fill(server.isRunning ? .green : .red)
                    .frame(width: 8, height: 8)

                Text(
                    server.isRunning
                        ? "Server running"
                        : "Server stopped"
                )

                Spacer()
            }

            Divider()

            // Pairing PIN
            if let pairingCode, let pairingExpiresAt {
                VStack(spacing: 6) {
                    Text("Pairing PIN")
                        .font(.caption)
                        .foregroundStyle(.secondary)

                    Text(pairingCode)
                        .font(
                            .system(
                                size: 32,
                                weight: .bold,
                                design: .monospaced
                            )
                        )
                        .tracking(6)

                    TimelineView(
                        .periodic(from: .now, by: 1)
                    ) { timeline in

                        let remaining = max(
                            0,
                            Int(
                                pairingExpiresAt
                                    .timeIntervalSince(timeline.date)
                                    .rounded(.up)
                            )
                        )

                        Text(
                            remaining > 0
                                ? "Expires in \(remaining) seconds"
                                : "PIN expired"
                        )
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    }
                }
                .padding(.vertical, 8)
            }

            // Generate PIN button
            Button {
                generatePairingCode()
            } label: {
                if isGeneratingPairing {
                    ProgressView()
                        .controlSize(.small)
                } else {
                    Label(
                        "Generate Pairing PIN",
                        systemImage: "qrcode"
                    )
                }
            }
            .disabled(
                !server.isRunning || isGeneratingPairing
            )

            // Error message
            if let errorMessage {
                Text(errorMessage)
                    .font(.caption)
                    .foregroundStyle(.red)
                    .multilineTextAlignment(.center)
            }

            Divider()

            // Quit application
            Button("Quit GoRemote") {
                NSApplication.shared.terminate(nil)
            }
        }
        .padding()
        .frame(width: 260)
    }

    // MARK: - Pairing

    private func generatePairingCode() {
        isGeneratingPairing = true
        errorMessage = nil
        pairingCode = nil
        pairingExpiresAt = nil

        Task {
            defer {
                isGeneratingPairing = false
            }

            do {
                let result = try await ManagementClient.startPairing()

                pairingCode = result.code

                pairingExpiresAt = Date().addingTimeInterval(
                    TimeInterval(result.expiresInSeconds)
                )

            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }
}
