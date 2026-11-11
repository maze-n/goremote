import AppKit
import CoreGraphics
import ApplicationServices
import Darwin

enum MediaKey: Int {
    case playPause = 16
    case next = 17
    case previous = 18
}

func postMediaKey(_ key: MediaKey) {
    // Check macOS Accessibility permission
    guard AXIsProcessTrusted() else {
        fputs("ERROR: Accessibility permission not granted\n", stderr)
        exit(2)
    }

    let keyCode = key.rawValue

    guard let down = NSEvent.otherEvent(
        with: .systemDefined,
        location: .zero,
        modifierFlags: NSEvent.ModifierFlags(rawValue: 0xA00),
        timestamp: ProcessInfo.processInfo.systemUptime,
        windowNumber: 0,
        context: nil,
        subtype: 8,
        data1: (keyCode << 16) | (0xA << 8),
        data2: -1
    ), let downCG = down.cgEvent else {
        fputs("ERROR: Could not create key-down event\n", stderr)
        exit(3)
    }

    guard let up = NSEvent.otherEvent(
        with: .systemDefined,
        location: .zero,
        modifierFlags: NSEvent.ModifierFlags(rawValue: 0xB00),
        timestamp: ProcessInfo.processInfo.systemUptime,
        windowNumber: 0,
        context: nil,
        subtype: 8,
        data1: (keyCode << 16) | (0xB << 8),
        data2: -1
    ), let upCG = up.cgEvent else {
        fputs("ERROR: Could not create key-up event\n", stderr)
        exit(4)
    }

    downCG.post(tap: .cghidEventTap)

    // Give macOS time to process the key-down event
    usleep(20_000)

    upCG.post(tap: .cghidEventTap)

    print("Posted media key: \(key)")
}

guard CommandLine.arguments.count == 2 else {
    fputs("Usage: media-helper <play-pause|next|previous>\n", stderr)
    exit(1)
}

switch CommandLine.arguments[1] {
case "play-pause":
    postMediaKey(.playPause)
case "next":
    postMediaKey(.next)
case "previous":
    postMediaKey(.previous)
default:
    fputs("Unknown media command\n", stderr)
    exit(1)
}