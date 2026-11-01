import ModeKit

/// Swift's Task; ModeKit has a Kotlin `Task` (a saved task) with the same name.
typealias Updates = _Concurrency.Task<Void, Never>

/// Streams a presenter's state into a ViewModel on the main actor, until cancelled.
@MainActor
func observe<T>(_ flow: SkieSwiftStateFlow<T>, _ apply: @escaping @MainActor (T) -> Void) -> Updates {
    Updates { @MainActor in
        for await value in flow {
            apply(value)
        }
    }
}
