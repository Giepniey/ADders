// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "ADdersPlayer",
    platforms: [
        .iOS(.v15),
        .macOS(.v12)
    ],
    products: [
        .library(name: "ADdersPlayer", targets: ["ADdersPlayer"]),
    ],
    dependencies: [],
    targets: [
        .target(
            name: "ADdersPlayer",
            path: "Sources/ADdersPlayer"
        ),
        .testTarget(
            name: "ADdersPlayerTests",
            dependencies: ["ADdersPlayer"],
            path: "Tests/ADdersPlayerTests"
        ),
    ]
)
