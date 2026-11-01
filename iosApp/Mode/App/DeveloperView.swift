import ModeKit
import SwiftUI

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
