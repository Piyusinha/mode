import ModeKit
import SwiftUI

/// Whether Settings shows Developer: in debug builds (Xcode) only. The release .ipa people install hides it.
enum DeveloperFeature {
    #if DEBUG
    static let enabled = true
    #else
    static let enabled = false
    #endif
}

/// The developer screens (runs, logs, hands check, model comparison), kept for debugging.
struct DeveloperView: View {
    var body: some View {
        DeveloperScreens()
            .ignoresSafeArea(edges: .bottom)
            .navigationTitle(Text(L10n.Developer.title))
            .navigationBarTitleDisplayMode(.inline)
    }
}

private struct DeveloperScreens: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        IosEntryKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
