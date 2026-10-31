import XCTest

/// Phase 0 smoke test: proves the UI-test bundle runs on the phone and can read
/// an app that is not ours. Phase 1 replaces it with the engine server.
final class EngineSmokeTests: XCTestCase {
    func testReadsHomeScreen() throws {
        XCUIDevice.shared.press(.home)
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        XCTAssertTrue(springboard.wait(for: .runningForeground, timeout: 10))

        // One snapshot of the whole tree. Querying elements one by one is a
        // round trip each, and the screen can change between them.
        let root = try springboard.snapshot()
        var icons: [String] = []
        func walk(_ node: XCUIElementSnapshot) {
            if node.elementType == .icon, !node.label.isEmpty { icons.append(node.label) }
            node.children.forEach(walk)
        }
        walk(root)

        print("ENGINE_SMOKE home-screen icons (\(icons.count)): \(icons.prefix(30))")
        XCTAssertFalse(icons.isEmpty, "read no icon labels from the Home Screen")
    }
}
