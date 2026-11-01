import Contacts
import ContactsUI
import SwiftUI

/// iOS's contact picker: the user picks one card; Mode never reads the rest (no permission needed).
struct ContactPicker: UIViewControllerRepresentable {
    let onPick: (_ given: String, _ family: String) -> Void

    func makeUIViewController(context: Context) -> CNContactPickerViewController {
        let picker = CNContactPickerViewController()
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: CNContactPickerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onPick: onPick) }

    final class Coordinator: NSObject, CNContactPickerDelegate {
        let onPick: (String, String) -> Void

        init(onPick: @escaping (String, String) -> Void) {
            self.onPick = onPick
        }

        func contactPicker(_ picker: CNContactPickerViewController, didSelect contact: CNContact) {
            onPick(contact.givenName, contact.familyName)
        }
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
