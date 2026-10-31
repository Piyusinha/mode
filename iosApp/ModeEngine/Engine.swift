import XCTest

/// The ops, done with XCUITest. Each one maps a phone-harness op onto the
/// iPhone; anything iOS cannot do raises `unsupported` instead of being faked.
final class Engine {
    /// Every op this engine answers, as `GET /ops` lists it.
    static let ops = [
        "tree", "screen.text", "screen.capture", "screen.bounds",
        "input.tap", "input.press", "input.drag", "input.scroll", "input.text", "input.keys",
        "nav.home", "apps.launch", "apps.current", "url.open", "session.state",
        "engine.stop", "engine.diag", "engine.tune",
    ]

    var onStop: (() -> Void)?
    private(set) var isServing = true

    private let springboard = XCUIApplication(bundleIdentifier: ForegroundApp.springboardID)

    /// How patient XCTest is before and after each event. Its default waits for
    /// the app to go idle, and an app that never stops animating (Uber's live
    /// map) costs seconds per keystroke; the agent verifies by reading the
    /// screen instead. Interaction options are bits: 1 skips the wait before an
    /// event, 2 the wait after it. `engine.tune` changes these.
    private var idleAnimationWait = false
    private var interactionOptions: UInt = 3
    private var lastLaunched: String?
    /// An XCTest failure raised while an op ran (say, typing with no field
    /// focused). XCTest records it instead of throwing; the op reports it.
    private var pendingIssue: String?

    func noteIssue(_ description: String) {
        pendingIssue = pendingIssue.map { "\($0); \(description)" } ?? description
    }

    func stop() {
        guard isServing else { return }
        isServing = false
        onStop?()
    }

    func handle(_ op: String, _ kw: [String: Any]) throws -> Any? {
        pendingIssue = nil
        let result = try dispatch(op, kw)
        if let issue = pendingIssue {
            pendingIssue = nil
            throw EngineError.failed(issue)
        }
        return result
    }

    private func dispatch(_ op: String, _ kw: [String: Any]) throws -> Any? {
        switch op {
        case "tree":
            return Snapshot.node(try currentApp().snapshot())
        case "screen.text":
            return Snapshot.textBoxes(try currentApp().snapshot(), within: screenFrame())
        case "screen.capture":
            return capture(kw)
        case "screen.bounds":
            return bounds()
        case "input.tap":
            point(try number(kw, "x"), try number(kw, "y")).tap()
        case "input.press":
            point(try number(kw, "x"), try number(kw, "y")).press(forDuration: number(kw, "duration", 0.8))
        case "input.drag":
            try drag(kw)
        case "input.scroll":
            try scroll(kw)
        case "input.text":
            try type(try string(kw, "s"))
        case "input.keys":
            try press(try string(kw, "combo"))
        case "nav.home":
            XCUIDevice.shared.press(.home)
        case "apps.launch":
            return try launch(try string(kw, "name"), fresh: kw["fresh"] as? Bool ?? false)
        case "apps.current":
            return ForegroundApp.bundleID()
        case "url.open":
            guard let url = URL(string: try string(kw, "url")) else { throw EngineError.failed("not a URL") }
            XCUIDevice.shared.system.open(url)
            return waitForForeground(otherThan: [], timeout: 5)
        case "session.state":
            return "ready"
        case "engine.stop":
            stop()
        case "engine.diag":
            return ["foreground": ForegroundApp.diagnose(), "automation_mode": ForegroundApp.automationModeClasses()]
        case "engine.tune":
            return tune(kw)
        default:
            throw EngineError.unsupported("this iPhone cannot '\(op)'")
        }
        return nil
    }

    // MARK: - where things are

    /// The app in front: from the accessibility client when it answers, else
    /// the app this engine last launched if it is still in front, else the Home Screen.
    private func currentApp() -> XCUIApplication {
        if let id = ForegroundApp.bundleID() {
            return patient(XCUIApplication(bundleIdentifier: id))
        }
        if let id = lastLaunched {
            let app = XCUIApplication(bundleIdentifier: id)
            if app.state == .runningForeground { return patient(app) }
        }
        return patient(springboard)
    }

    /// Applies the current waiting settings to an app before it is driven.
    private func patient(_ app: XCUIApplication) -> XCUIApplication {
        Private.setBool(app, "setIdleAnimationWaitEnabled:", idleAnimationWait)
        if let process = Private.object(app, "currentProcess") {
            Private.setUInt(process, "setInteractionOptions:", interactionOptions)
        }
        return app
    }

    /// `{idle_animation_wait, interaction_options, typing_frequency}`, any subset;
    /// answers what the foreground app's process now reports.
    private func tune(_ kw: [String: Any]) -> [String: Any] {
        if let wait = kw["idle_animation_wait"] as? Bool { idleAnimationWait = wait }
        if let options = kw["interaction_options"] as? Int { interactionOptions = UInt(options) }
        if let frequency = kw["typing_frequency"] as? Int {
            UserDefaults.standard.set(frequency, forKey: "com.apple.xctest.iOSMaximumTypingFrequency")
        }
        let app = currentApp()
        var out: [String: Any] = [
            "idle_animation_wait": Private.bool(app, "isIdleAnimationWaitEnabled") as Any,
            "typing_frequency": UserDefaults.standard.object(forKey: "com.apple.xctest.iOSMaximumTypingFrequency") as Any,
        ]
        if let process = Private.object(app, "currentProcess") {
            out["interaction_options"] = Private.uint(process, "interactionOptions") as Any
            out["skip_pre_event_quiescence"] = Private.bool(process, "shouldSkipPreEventQuiescence") as Any
            out["skip_post_event_quiescence"] = Private.bool(process, "shouldSkipPostEventQuiescence") as Any
        }
        return out
    }

    private func screenFrame() -> CGRect { springboard.frame }

    private func bounds() -> [String: Any] {
        let frame = screenFrame()
        return ["x": 0, "y": 0, "w": Double(frame.width), "h": Double(frame.height), "id": 0]
    }

    /// A screen point, in the same top-left space as the tree's frames.
    /// Anchored on the app in front so its waiting settings apply to the gesture.
    private func point(_ x: Double, _ y: Double) -> XCUICoordinate {
        currentApp().coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: x, dy: y))
    }

    // MARK: - eyes

    private func capture(_ kw: [String: Any]) -> [String: Any] {
        let shot = XCUIScreen.main.screenshot()
        let maxSide = kw["max_size"] as? Double
        guard let maxSide, maxSide > 0 else {
            return ["png_b64": shot.pngRepresentation.base64EncodedString(), "bounds": bounds()]
        }
        // A smaller JPEG for models: the full PNG is several megabytes.
        let image = shot.image
        let pixels = max(image.size.width * image.scale, image.size.height * image.scale)
        let factor = min(1, maxSide / pixels)
        let size = CGSize(width: image.size.width * image.scale * factor,
                          height: image.size.height * image.scale * factor)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let resized = UIGraphicsImageRenderer(size: size, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: size))
        }
        let jpeg = resized.jpegData(compressionQuality: 0.7) ?? Data()
        return ["jpeg_b64": jpeg.base64EncodedString(), "bounds": bounds(),
                "img_px": ["w": Double(size.width), "h": Double(size.height)]]
    }

    // MARK: - hands

    private func drag(_ kw: [String: Any]) throws {
        let x1 = try number(kw, "x1"), y1 = try number(kw, "y1")
        let x2 = try number(kw, "x2"), y2 = try number(kw, "y2")
        let duration = max(number(kw, "duration", 0.35), 0.05)
        let distance = hypot(x2 - x1, y2 - y1)
        point(x1, y1).press(forDuration: 0.05, thenDragTo: point(x2, y2),
                            withVelocity: XCUIGestureVelocity(CGFloat(distance / duration)),
                            thenHoldForDuration: 0)
    }

    /// Content-space deltas: a negative dy reveals what is further down, so the
    /// finger moves up. Slow, with a hold at the end, so the list stops where
    /// the finger stops instead of coasting past rows.
    private func scroll(_ kw: [String: Any]) throws {
        let x = try number(kw, "x"), y = try number(kw, "y")
        let dy = number(kw, "dy", 0), dx = number(kw, "dx", 0)
        point(x, y).press(forDuration: 0.05, thenDragTo: point(x + dx, y + dy),
                          withVelocity: XCUIGestureVelocity(800),
                          thenHoldForDuration: 0.2)
    }

    private func type(_ text: String) throws {
        let app = currentApp()
        guard app.keyboards.firstMatch.exists else {
            throw EngineError.failed("no keyboard is up: tap a text field first, then type")
        }
        app.typeText(text)
    }

    private func press(_ combo: String) throws {
        let keys: [String: String] = [
            "return": "\n", "enter": "\n", "tab": "\t",
            "delete": XCUIKeyboardKey.delete.rawValue, "backspace": XCUIKeyboardKey.delete.rawValue,
            "escape": XCUIKeyboardKey.escape.rawValue, "space": " ",
        ]
        guard let key = keys[combo.lowercased()] else {
            throw EngineError.unsupported("this iPhone takes single keys only (\(keys.keys.sorted().joined(separator: ", "))), not '\(combo)'")
        }
        try type(key)
    }

    // MARK: - apps

    /// A bundle id is launched directly; anything else is a name, found through Spotlight.
    private func launch(_ name: String, fresh: Bool) throws -> String? {
        let looksLikeBundleID = name.contains(".") && !name.contains(" ")
        if looksLikeBundleID {
            let app = XCUIApplication(bundleIdentifier: name)
            if fresh { app.launch() } else { app.activate() }
            guard app.wait(for: .runningForeground, timeout: 10) else {
                throw EngineError.failed("\(name) did not come to the front; is it installed?")
            }
            lastLaunched = name
            return name
        }

        // Spotlight is its own app on iOS 27 (com.apple.campo), not part of the
        // Home Screen: open it, then type into whatever app came forward.
        XCUIDevice.shared.press(.home)
        let spotlight = try openSpotlight()
        let search = patient(XCUIApplication(bundleIdentifier: spotlight))
        guard search.keyboards.firstMatch.waitForExistence(timeout: 3) else {
            throw EngineError.failed("Spotlight opened (\(spotlight)) but its keyboard did not come up")
        }
        search.typeText(name)
        Thread.sleep(forTimeInterval: 1.0)  // let the top hit settle before opening it
        search.typeText("\n")
        guard let launched = waitForForeground(otherThan: [ForegroundApp.springboardID, spotlight], timeout: 8) else {
            throw EngineError.failed("no app opened for '\(name)'; Spotlight showed no matching app")
        }
        lastLaunched = launched
        return launched
    }

    /// Opens Spotlight from the Home Screen and answers its bundle id: the
    /// Home Screen's own Search button when it has one, else a swipe down.
    private func openSpotlight() throws -> String {
        let screen = screenFrame()
        var button: CGRect?
        if let root = try? springboard.snapshot() {
            func walk(_ s: XCUIElementSnapshot) {
                if button == nil, s.label == "Search", s.frame.midY > screen.height / 2 { button = s.frame }
                s.children.forEach(walk)
            }
            walk(root)
        }
        if let button {
            point(Double(button.midX), Double(button.midY)).tap()
        } else {
            point(Double(screen.midX), Double(screen.height * 0.35))
                .press(forDuration: 0.05, thenDragTo: point(Double(screen.midX), Double(screen.height * 0.65)))
        }
        guard let spotlight = waitForForeground(otherThan: [ForegroundApp.springboardID], timeout: 4) else {
            throw EngineError.failed("Spotlight did not open from the Home Screen")
        }
        return spotlight
    }

    /// The foreground bundle id once it is none of `excluded`, or nil on timeout.
    private func waitForForeground(otherThan excluded: Set<String>, timeout: TimeInterval) -> String? {
        let deadline = Date().addingTimeInterval(timeout)
        repeat {
            if let id = ForegroundApp.bundleID(), !excluded.contains(id) { return id }
            Thread.sleep(forTimeInterval: 0.25)
        } while Date() < deadline
        return nil
    }

    // MARK: - arguments

    private func number(_ kw: [String: Any], _ key: String) throws -> Double {
        guard let value = kw[key] as? Double else { throw EngineError.failed("missing number '\(key)'") }
        return value
    }

    private func number(_ kw: [String: Any], _ key: String, _ fallback: Double) -> Double {
        kw[key] as? Double ?? fallback
    }

    private func string(_ kw: [String: Any], _ key: String) throws -> String {
        guard let value = kw[key] as? String else { throw EngineError.failed("missing string '\(key)'") }
        return value
    }
}
