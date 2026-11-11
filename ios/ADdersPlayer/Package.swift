// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "ADdersPlayer",
    platforms: [.iOS(.v15)],
    products: [
        .executable(name: "ADdersPlayer", targets: ["ADdersPlayer"]),
    ],
    dependencies: [],
    targets: [
        .target(
            name: "ADdersPlayer",
            path: "Sources/ADdersPlayer",
            resources: []
        ),
        .testTarget(
            name: "ADdersPlayerTests",
            dependencies: ["ADdersPlayer"],
            path: "Tests/ADdersPlayerTests"
        ),
    ]
)
