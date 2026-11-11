
import SwiftUI

struct MenuBarView: View {
    @ObservedObject var server: ServerManager

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Circle()
                    .fill(server.isRunning ? .green : .red)
                    .frame(width: 9, height: 9)

                Text(
                    server.isRunning
                        ? "Server Running"
                        : "Server Stopped"
                )
                .fontWeight(.medium)
            }

            Divider()

            if server.isRunning {
                Button("Stop Server") {
                    server.stop()
                }
            } else {
                Button("Start Server") {
                    server.start()
                }
            }

            if let error = server.errorMessage {
                Text(error)
                    .font(.caption)
                    .foregroundStyle(.red)
            }

            Divider()

            Button("Quit GoRemote") {
                NSApplication.shared.terminate(nil)
            }
        }
        .padding()
        .frame(width: 240)
    }
}
