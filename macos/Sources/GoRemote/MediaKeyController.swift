import AppKit
import CoreGraphics

enum MediaKey: Int {
    case playPause = 16
    case next = 17
    case previous = 18
}

@MainActor
enum MediaKeyController {

    @discardableResult
    static func post(_ key: MediaKey) -> Bool {
        let keyCode = key.rawValue

        guard
            let keyDown = makeEvent(
                keyCode: keyCode,
                state: 0xA
            ),
            let keyUp = makeEvent(
                keyCode: keyCode,
                state: 0xB
            )
        else {
            return false
        }

        keyDown.cgEvent?.post(tap: .cghidEventTap)
        keyUp.cgEvent?.post(tap: .cghidEventTap)

        return true
    }

    private static func makeEvent(
        keyCode: Int,
        state: Int
    ) -> NSEvent? {
        NSEvent.otherEvent(
            with: .systemDefined,
            location: .zero,
            modifierFlags: NSEvent.ModifierFlags(rawValue: 0xA00),
            timestamp: ProcessInfo.processInfo.systemUptime,
            windowNumber: 0,
            context: nil,
            subtype: 8,
            data1: (keyCode << 16) | (state << 8),
            data2: -1
        )
    }
}