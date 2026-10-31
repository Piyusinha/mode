import XCTest

/// Which app is in front.
///
/// XCUITest has no public call for this. The accessibility client XCTest uses
/// internally knows, and WebDriverAgent reads it the same way:
///
///     XCUIDevice.accessibilityInterface   (XCAXClient_iOS)
///       .activeApplications.first          -> pid
///       .applicationProcessTracker         (XCUIApplicationMonitor)
///       .monitoredApplicationWithProcessIdentifier(pid).bundleID
///
/// Every private step is checked before it is called, so an iOS that renames
/// something degrades to the public fallback instead of crashing; `diagnose()`
/// shows which step stopped answering.
enum ForegroundApp {
    static let springboardID = "com.apple.springboard"

    /// The bundle id in front, or nil when the private path is unavailable.
    static func bundleID() -> String? {
        guard let client = client(), let pid = activePID(client),
              let app = monitoredApplication(client, pid: pid) else { return nil }
        let getter = NSSelectorFromString("bundleID")
        guard app.responds(to: getter) else { return nil }
        return app.perform(getter)?.takeUnretainedValue() as? String
    }

    /// Each step of the lookup and what it found, for when `bundleID()` comes back nil.
    static func diagnose() -> [String: Any] {
        var out: [String: Any] = [
            "device_accessibility_interface": XCUIDevice.shared.responds(to: NSSelectorFromString("accessibilityInterface")),
        ]
        guard let client = client() else { return out }
        out["client"] = String(describing: type(of: client))
        out["has_active_applications"] = client.responds(to: NSSelectorFromString("activeApplications"))
        guard let pid = activePID(client) else { return out }
        out["pid"] = pid
        out["tracker"] = tracker(client).map { String(describing: type(of: $0)) } ?? "nil"
        guard let app = monitoredApplication(client, pid: pid) else { return out }
        out["application"] = String(describing: type(of: app))
        out["bundle_id"] = bundleID() ?? "nil"
        let tuning = { (name: String) in
            ["nteraction", "IdleAnimation", "uiescence", "yping"].contains { name.contains($0) }
        }
        out["application_tuning"] = methodNames(of: XCUIApplication.self).filter(tuning)
        if let process = app.perform(NSSelectorFromString("currentProcess"))?.takeUnretainedValue() as? NSObject {
            out["process"] = String(describing: type(of: process))
            out["process_tuning"] = methodNames(of: type(of: process)).filter(tuning)
        }
        return out
    }

    /// `XCUIDevice.accessibilityInterface` in recent Xcodes, `+[XCAXClient_iOS sharedClient]` in older ones.
    private static func client() -> NSObject? {
        let device = XCUIDevice.shared
        let accessor = NSSelectorFromString("accessibilityInterface")
        if device.responds(to: accessor),
           let client = device.perform(accessor)?.takeUnretainedValue() as? NSObject {
            return client
        }
        let shared = NSSelectorFromString("sharedClient")
        if let clientClass = NSClassFromString("XCAXClient_iOS") as AnyObject?,
           clientClass.responds(to: shared),
           let client = clientClass.perform(shared)?.takeUnretainedValue() as? NSObject {
            return client
        }
        return nil
    }

    private static func activePID(_ client: NSObject) -> pid_t? {
        let selector = NSSelectorFromString("activeApplications")
        guard client.responds(to: selector),
              let first = (client.perform(selector)?.takeUnretainedValue() as? [NSObject])?.first,
              first.responds(to: NSSelectorFromString("processIdentifier")) else { return nil }
        return (first.value(forKey: "processIdentifier") as? NSNumber)?.int32Value
    }

    private static func tracker(_ client: NSObject) -> NSObject? {
        let selector = NSSelectorFromString("applicationProcessTracker")
        guard client.responds(to: selector) else { return nil }
        return client.perform(selector)?.takeUnretainedValue() as? NSObject
    }

    /// The XCTest classes that might turn Automation Mode on, with their automation-mode methods.
    static func automationModeClasses() -> [String: [String]] {
        let candidates = [
            "XCUIDeviceAutomationModeInterface", "XCUIDevice", "XCTRunnerDaemonSession",
            "XCTestDriver", "XCTRunnerIDESession", "XCUIApplication", "XCUIApplicationImpl",
            "XCTAutomationSession", "XCUIRemoteAccessibilityInterface", "XCTestConfiguration",
        ]
        var out: [String: [String]] = [:]
        for name in candidates {
            guard let cls = NSClassFromString(name), let meta = object_getClass(cls) else { continue }
            let methods = (methodNames(of: cls) + methodNames(of: meta).map { "+" + $0 })
                .filter { $0.localizedCaseInsensitiveContains("automation") }
            out[name] = methods
        }
        return out
    }

    private static func methodNames(of cls: AnyClass) -> [String] {
        var count: UInt32 = 0
        guard let methods = class_copyMethodList(cls, &count) else { return [] }
        defer { free(methods) }
        return (0..<Int(count)).map { NSStringFromSelector(method_getName(methods[$0])) }
    }

    private static func monitoredApplication(_ client: NSObject, pid: pid_t) -> NSObject? {
        let selector = NSSelectorFromString("monitoredApplicationWithProcessIdentifier:")
        guard let tracker = tracker(client),
              let method = class_getInstanceMethod(type(of: tracker), selector) else { return nil }
        typealias Lookup = @convention(c) (AnyObject, Selector, pid_t) -> Unmanaged<AnyObject>?
        let lookup = unsafeBitCast(method_getImplementation(method), to: Lookup.self)
        return lookup(tracker, selector, pid)?.takeUnretainedValue() as? NSObject
    }
}
