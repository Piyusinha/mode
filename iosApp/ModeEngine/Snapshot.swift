import XCTest

/// Turns one XCUITest snapshot into JSON: the nested tree, or phone-harness's
/// flat list of text boxes with tap-ready centres.
enum Snapshot {
    /// A node with only the fields that carry something; empty ones are left
    /// out, since the tree goes to a model and size is cost.
    static func node(_ s: XCUIElementSnapshot) -> [String: Any] {
        var out: [String: Any] = ["type": typeName(s.elementType)]
        if !s.label.isEmpty { out["label"] = s.label }
        if !s.identifier.isEmpty { out["id"] = s.identifier }
        if let value = s.value as? String, !value.isEmpty { out["value"] = value }
        if let placeholder = s.placeholderValue, !placeholder.isEmpty { out["placeholder"] = placeholder }
        out["frame"] = frame(s.frame)
        if !s.isEnabled { out["enabled"] = false }
        if s.isSelected { out["selected"] = true }
        let children = s.children.map { Snapshot.node($0) }
        if !children.isEmpty { out["children"] = children }
        return out
    }

    /// Every visible piece of text as `{text, confidence, source, type, x, y, w, h}`,
    /// (x, y) the centre. `source` is "tree": the OS reported it, nothing was read off pixels.
    static func textBoxes(_ root: XCUIElementSnapshot, within screen: CGRect) -> [[String: Any]] {
        var boxes: [[String: Any]] = []
        func walk(_ s: XCUIElementSnapshot) {
            let text = !s.label.isEmpty ? s.label
                : (s.value as? String).flatMap { $0.isEmpty ? nil : $0 }
                ?? s.placeholderValue ?? ""
            let f = s.frame
            if !text.isEmpty, f.width > 0, f.height > 0, screen.intersects(f) {
                boxes.append([
                    "text": text, "confidence": 1.0, "source": "tree", "type": typeName(s.elementType),
                    "x": Double(f.midX).rounded(), "y": Double(f.midY).rounded(),
                    "w": Double(f.width).rounded(), "h": Double(f.height).rounded(),
                ])
            }
            s.children.forEach(walk)
        }
        walk(root)
        return boxes
    }

    private static func frame(_ f: CGRect) -> [Double] {
        [f.minX, f.minY, f.width, f.height].map { Double($0).rounded() }
    }

    static func typeName(_ type: XCUIElement.ElementType) -> String {
        switch type {
        case .application: "application"
        case .window: "window"
        case .button: "button"
        case .staticText: "text"
        case .textField: "textField"
        case .secureTextField: "secureTextField"
        case .searchField: "searchField"
        case .textView: "textView"
        case .link: "link"
        case .image: "image"
        case .icon: "icon"
        case .cell: "cell"
        case .table: "table"
        case .collectionView: "collectionView"
        case .scrollView: "scrollView"
        case .navigationBar: "navigationBar"
        case .tabBar: "tabBar"
        case .tab: "tab"
        case .toolbar: "toolbar"
        case .switch: "switch"
        case .toggle: "toggle"
        case .slider: "slider"
        case .segmentedControl: "segmentedControl"
        case .picker: "picker"
        case .pickerWheel: "pickerWheel"
        case .pageIndicator: "pageIndicator"
        case .alert: "alert"
        case .sheet: "sheet"
        case .keyboard: "keyboard"
        case .key: "key"
        case .webView: "webView"
        case .map: "map"
        case .menu: "menu"
        case .menuItem: "menuItem"
        case .activityIndicator: "activityIndicator"
        case .progressIndicator: "progressIndicator"
        case .other: "other"
        default: "type\(type.rawValue)"
        }
    }
}
