
 // swift-tools-version: 6.0

import PackageDescription

let package = Package(
    name: "GoRemote",
    platforms: [
        .macOS(.v13)
    ],
    products: [
        .executable(
            name: "GoRemote",
            targets: ["GoRemote"]
        )
    ],
    targets: [
        .executableTarget(
            name: "GoRemote"
        )
    ]
)
