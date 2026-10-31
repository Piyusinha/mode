import Foundation

/// Calls into XCTest's private Objective-C API, each one checked first: a
/// selector that is gone in some future Xcode becomes a no-op, not a crash.
enum Private {
    static func responds(_ target: NSObject, _ name: String) -> Bool {
        target.responds(to: NSSelectorFromString(name))
    }

    static func object(_ target: NSObject, _ getter: String) -> NSObject? {
        guard responds(target, getter) else { return nil }
        return target.perform(NSSelectorFromString(getter))?.takeUnretainedValue() as? NSObject
    }

    @discardableResult
    static func setBool(_ target: NSObject, _ setter: String, _ value: Bool) -> Bool {
        let selector = NSSelectorFromString(setter)
        guard let method = class_getInstanceMethod(type(of: target), selector) else { return false }
        typealias Setter = @convention(c) (AnyObject, Selector, Bool) -> Void
        unsafeBitCast(method_getImplementation(method), to: Setter.self)(target, selector, value)
        return true
    }

    @discardableResult
    static func setUInt(_ target: NSObject, _ setter: String, _ value: UInt) -> Bool {
        let selector = NSSelectorFromString(setter)
        guard let method = class_getInstanceMethod(type(of: target), selector) else { return false }
        typealias Setter = @convention(c) (AnyObject, Selector, UInt) -> Void
        unsafeBitCast(method_getImplementation(method), to: Setter.self)(target, selector, value)
        return true
    }

    static func bool(_ target: NSObject, _ getter: String) -> Bool? {
        let selector = NSSelectorFromString(getter)
        guard let method = class_getInstanceMethod(type(of: target), selector) else { return nil }
        typealias Getter = @convention(c) (AnyObject, Selector) -> Bool
        return unsafeBitCast(method_getImplementation(method), to: Getter.self)(target, selector)
    }

    static func uint(_ target: NSObject, _ getter: String) -> UInt? {
        let selector = NSSelectorFromString(getter)
        guard let method = class_getInstanceMethod(type(of: target), selector) else { return nil }
        typealias Getter = @convention(c) (AnyObject, Selector) -> UInt
        return unsafeBitCast(method_getImplementation(method), to: Getter.self)(target, selector)
    }
}
