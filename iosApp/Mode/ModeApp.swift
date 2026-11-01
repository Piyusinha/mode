import SwiftUI
import UIKit
import ModeKit
import ModeDesignSystem

@main
struct ModeApp: App {
    init() {
        if !ModeTypography.registerFonts() { NSLog("Mode: Instrument Sans did not load; headings use SF Pro") }
        ModeAppearance.apply()
    }

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
        }
    }
}

/// Hosts the Compose UI from ModeKit.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        IosEntryKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
