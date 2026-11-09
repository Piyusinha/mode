import SwiftUI

/// A screen's view model, made once: the first time the screen draws, then kept for as long as the screen is.
///
/// `@State` with `State(initialValue: Model(...))` in a view's `init` builds a new model every time the parent
/// redraws (SwiftUI only keeps the first and drops the rest). Each throwaway model started its presenter (flows,
/// database queries) and cancelled it again, which in Kotlin/Native throws: during a task the parents redraw at
/// every step, and on the phone (9 Nov) that churn reached 81% CPU in the background and iOS killed Mode.
/// Here the model is built from an autoclosure, only when the kept storage has none yet.
@MainActor
@propertyWrapper
struct LazyState<Value>: DynamicProperty {
    private final class Box {
        var value: Value?
        var made = false
    }

    @State private var box = Box()
    private let make: () -> Value

    init(wrappedValue: @autoclosure @escaping () -> Value) {
        make = wrappedValue
    }

    var wrappedValue: Value {
        if !box.made {
            box.value = make()
            box.made = true
        }
        return box.value!
    }

    /// `$model.field` bindings, as with `@State`.
    var projectedValue: Projection { Projection(value: wrappedValue) }

    @MainActor
    @dynamicMemberLookup
    struct Projection {
        let value: Value

        subscript<Field>(dynamicMember keyPath: ReferenceWritableKeyPath<Value, Field>) -> Binding<Field> {
            let value = value
            return Binding(get: { value[keyPath: keyPath] }, set: { value[keyPath: keyPath] = $0 })
        }
    }
}

