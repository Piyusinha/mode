import Contacts
import ContactsUI
import SwiftUI

/// iOS's contact picker: the user picks one card; Mode never reads the rest (no permission needed).
/// Presented by UIKit from the front-most screen: inside a SwiftUI sheet Apple's picker can show
/// empty or close without answering.
@MainActor
enum ContactPicker {
    private static var delegate: Delegate?

    static func present(onPick: @escaping (_ given: String, _ family: String) -> Void) {
        guard let top = frontViewController() else { return }
        let picker = CNContactPickerViewController()
        let d = Delegate { given, family in
            onPick(given, family)
            delegate = nil
        } cancelled: {
            delegate = nil
        }
        delegate = d
        picker.delegate = d
        top.present(picker, animated: true)
    }

    private static func frontViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive } ?? UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        var top = scene?.keyWindow?.rootViewController
        while let next = top?.presentedViewController { top = next }
        return top
    }

    private final class Delegate: NSObject, CNContactPickerDelegate {
        let picked: (String, String) -> Void
        let cancelled: () -> Void

        init(picked: @escaping (String, String) -> Void, cancelled: @escaping () -> Void) {
            self.picked = picked
            self.cancelled = cancelled
        }

        func contactPicker(_ picker: CNContactPickerViewController, didSelect contact: CNContact) {
            picked(contact.givenName, contact.familyName)
        }

        func contactPickerDidCancel(_ picker: CNContactPickerViewController) { cancelled() }
    }
}

/// iOS's share sheet for some text (the diagnosis report, a conversation).
struct ShareSheet: UIViewControllerRepresentable {
    let text: String

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [text], applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

/// Lets an optional String drive `.sheet(item:)`.
struct SharedText: Identifiable {
    let text: String
    var id: String { text }
}
