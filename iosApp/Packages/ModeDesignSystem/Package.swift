// swift-tools-version: 6.2
import PackageDescription

// Mode's design system: every colour, font, size, icon and component the app's
// screens use. Screens never contain their own values; they use these.
let package = Package(
    name: "ModeDesignSystem",
    defaultLocalization: "en",
    platforms: [.iOS(.v26)],
    products: [.library(name: "ModeDesignSystem", targets: ["ModeDesignSystem"])],
    targets: [
        .target(
            name: "ModeDesignSystem",
            resources: [.process("Resources")]
        ),
    ]
)
