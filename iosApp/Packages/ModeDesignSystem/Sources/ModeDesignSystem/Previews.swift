import SwiftUI

#Preview("Components, light") {
    ComponentGallery().preferredColorScheme(.light)
}

#Preview("Components, dark, large text") {
    ComponentGallery().preferredColorScheme(.dark).dynamicTypeSize(.accessibility2)
}

/// Every component on one page, for checking light, dark and large text.
private struct ComponentGallery: View {
    @State private var text = ""
    @State private var on = true
    @State private var segment = 0

    var body: some View {
        ScrollView {
            VStack(spacing: ModeSpacing.l) {
                ModeHero(icon: .sparkle, title: "Mode", message: "Your phone, done for you.")
                ModeSectionHeader("Rows")
                ModeCard {
                    ModeRow("Sonali", subtitle: "Message her on WhatsApp", leading: .initial("S")) {}
                    ModeRow("Turn off Dark Mode", subtitle: "Worked 3 of 3 times", leading: .icon(.skill)) {}
                    ModeToggleRow("Learn from my tasks", isOn: $on)
                }
                ModeChip("Remembered: Sonali is on WhatsApp", icon: .sparkle, actionTitle: "Undo") {}
                ModeBadge("Done", icon: .done, tone: .success)
                ModeSegmented([(0, "Every day"), (1, "Weekdays"), (2, "Once")], selection: $segment)
                ModeDayPicker(labels: ["M", "T", "W", "T", "F", "S", "S"], selected: [1, 2, 3, 4, 5]) { _ in }
                ModeTextField("Task", prompt: "What should Mode do?", text: $text)
                ModeStepRow(index: 1, title: "Open Settings", detail: "open Settings", state: .done)
                ModeStepRow(index: 2, title: "Search for Dark Mode", detail: nil, state: .current)
                ModeButton("Continue", fullWidth: true) {}
                ModeButton("Skip for now", role: .secondary) {}
                ModeButton("Forget Everything", role: .destructive) {}
                ModeComposer(text: $text, isPrivate: false, labels: .init(prompt: "Ask for more", send: "Send", privateOn: "Private", privateOff: "Not private"), onTogglePrivate: {}, onSend: {})
                ModeRunningBar(title: "Ordering my usual dinner", status: "Step 9 · checking the cart", stopLabel: "Stop") {}
            }
            .padding(ModeSpacing.screenEdge)
        }
        .modeScreen()
    }
}
