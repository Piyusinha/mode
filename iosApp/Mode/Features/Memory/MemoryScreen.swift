import ModeDesignSystem
import SwiftUI

/// Memory: learn switch, then About You, skills and app tips. Draws state, sends events.
struct MemoryScreen: View {
    let state: MemoryViewModel.State
    @Binding var confirmingForget: Bool
    let onEvent: (MemoryViewModel.Event) -> Void

    var body: some View {
        List {
            Section {
                ModeToggleRow(L10n.Memory.learn, subtitle: L10n.Memory.learnHint, isOn: Binding(get: { state.learn }, set: { onEvent(.learn($0)) }))
            } header: {
                Text(L10n.Memory.subtitle).modeText(.footnote, color: ModeColor.textSecondary)
            }
            if state.isEmpty {
                ModeEmptyState(icon: .memory, title: L10n.Memory.emptyTitle, message: L10n.Memory.emptyMessage).frame(maxWidth: .infinity)
            }
            group(L10n.Memory.aboutYou, state.aboutYou)
            group(L10n.Memory.skills, state.skills)
            group(L10n.Memory.tips, state.tips)
            if !state.isEmpty {
                Section {
                    ModeButton(L10n.Memory.forgetEverything, role: .destructive, fullWidth: true) { onEvent(.askForget) }
                }
            }
        }
        .listStyle(.insetGrouped)
        .modeScreen()
        .navigationTitle(Text(L10n.Tab.memory))
        .navigationBarTitleDisplayMode(.large)
        .confirmationDialog(Text(L10n.Memory.forgetConfirm), isPresented: $confirmingForget, titleVisibility: .visible) {
            Button(role: .destructive) { onEvent(.confirmForget) } label: { Text(L10n.Memory.forgetEverything) }
        }
    }

    @ViewBuilder
    private func group(_ title: LocalizedStringResource, _ entries: [MemoryViewModel.Entry]) -> some View {
        if !entries.isEmpty {
            Section {
                ForEach(entries) { entry in
                    ModeRow(entry.title, subtitle: entry.subtitle, leading: entry.leading)
                        .swipeActions { Button(role: .destructive) { onEvent(.delete(entry.id)) } label: { Text(L10n.Common.delete) } }
                }
            } header: { ModeSectionHeader(title) }
        }
    }
}

struct MemoryRoute: View {
    @State private var model: MemoryViewModel

    init(app: AppModel) {
        _model = State(initialValue: MemoryViewModel(screens: app.screens))
    }

    var body: some View {
        MemoryScreen(state: model.state, confirmingForget: $model.confirmingForget, onEvent: model.send)
    }
}
