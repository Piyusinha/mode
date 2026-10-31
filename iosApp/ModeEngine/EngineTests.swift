import XCTest

/// The engine's entry point. XCUITest only runs inside a test, so the engine
/// is one test that never returns until it is told to stop or goes idle.
///
/// Settings, from the environment (`xcodebuild` passes `TEST_RUNNER_<NAME>` as `<NAME>`):
///   MODE_ENGINE_TOKEN          bearer token clients must send (a random one is printed if unset)
///   MODE_ENGINE_PORT           default 47610
///   MODE_ENGINE_BIND_ALL=1     listen on every interface, not only loopback (testing from a Mac)
///   MODE_ENGINE_IDLE_SECONDS   stop after this long with no request; default 120
final class EngineTests: XCTestCase {
    private var engine: Engine?

    override func setUp() {
        continueAfterFailure = true
    }

    func testServe() throws {
        let env = ProcessInfo.processInfo.environment
        let token = env["MODE_ENGINE_TOKEN"] ?? UUID().uuidString
        let port = UInt16(env["MODE_ENGINE_PORT"] ?? "") ?? 47610
        let loopbackOnly = env["MODE_ENGINE_BIND_ALL"] != "1"
        let idleLimit = TimeInterval(env["MODE_ENGINE_IDLE_SECONDS"] ?? "") ?? 120

        let engine = Engine()
        self.engine = engine
        let stopped = expectation(description: "engine stopped")
        engine.onStop = { stopped.fulfill() }

        let server = try EngineServer(port: port, token: token, loopbackOnly: loopbackOnly,
                                      ops: Engine.ops) { op, kw in try engine.handle(op, kw) }
        server.start()
        let shownToken = env["MODE_ENGINE_TOKEN"] == nil ? " token=\(token)" : ""
        print("MODE_ENGINE ready port=\(port) loopback_only=\(loopbackOnly)\(shownToken)")

        let watchdog = Timer.scheduledTimer(withTimeInterval: 5, repeats: true) { _ in
            if Date().timeIntervalSince(server.lastRequest) > idleLimit {
                print("MODE_ENGINE idle for \(Int(idleLimit))s, stopping")
                engine.stop()
            }
        }
        wait(for: [stopped], timeout: 6 * 60 * 60)
        watchdog.invalidate()
        server.stop()
        print("MODE_ENGINE stopped")
    }

    /// While serving, an XCTest failure belongs to the op that caused it, not to
    /// this test: hand it to the engine so the client gets it as the op's error.
    override func record(_ issue: XCTIssue) {
        if let engine, engine.isServing {
            engine.noteIssue(issue.compactDescription)
            return
        }
        super.record(issue)
    }
}
