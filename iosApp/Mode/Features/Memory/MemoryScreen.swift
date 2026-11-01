import ModeDesignSystem
import SwiftUI

/// Memory, as on its board. Draws state, sends events.
struct MemoryScreen: View {
    let state: MemoryViewModel.State
    let editing: Bool
    let leading: (MemoryViewModel.Entry) -> ModeRowLeading
    let deleteTitle: String
    let hasSelection: Bool
    let onEvent: (MemoryViewModel.Event) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.m) {
                Text(L10n.Memory.subtitle).modeText(.callout, color: ModeColor.textSecondary)
                ModeCard { ModeToggleRow(L10n.Memory.learn, isOn: Binding(get: { state.learn }, set: { onEvent(.learn($0)) })) }
                if state.isEmpty {
                    ModeEmptyState(icon: .memory, title: L10n.Memory.emptyTitle, message: L10n.Memory.emptyMessage).frame(maxWidth: .infinity)
                }
                group(L10n.Memory.aboutYou, state.aboutYou)
                group(L10n.Memory.skills, state.skills)
                group(L10n.Memory.tips, state.tips)
                if !state.isEmpty && !editing {
                    ModeButton(L10n.Memory.forgetEverything, role: .destructive) { onEvent(.askForget) }.frame(maxWidth: .infinity)
                }
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .scrollEdgeEffectStyle(.soft, for: .all)
        .modeScreen()
        .navigationTitle(Text(L10n.Tab.memory))
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            if !state.isEmpty {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { onEvent(.toggleEditing) } label: { Text(editing ? L10n.Common.done : L10n.Memory.edit) }
                }
            }
        }
        .safeAreaInset(edge: .bottom) {
            if editing && hasSelection {
                Button(role: .destructive) { onEvent(.deleteSelected) } label: { Text(deleteTitle).frame(maxWidth: .infinity) }
                    .buttonStyle(.glassProminent)
                    .tint(ModeColor.danger)
                    .padding(.horizontal, ModeSpacing.screenEdge)
            }
        }
    }

    @ViewBuilder
    private func group(_ title: LocalizedStringResource, _ entries: [MemoryViewModel.Entry]) -> some View {
        if !entries.isEmpty {
            ModeSectionHeader(title)
            ModeCard {
                ForEach(entries) { entry in
                    ModeRow(entry.title, subtitle: entry.subtitle, leading: leading(entry), onTap: { onEvent(.open(entry)) })
                }
            }
        }
    }
}

/// One remembered item: its text to correct, where it was learned, how often it worked, and Delete.
private struct MemoryDetailSheet: View {
    let entry: MemoryViewModel.Entry
    @Binding var text: String
    let onSave: () -> Void
    let onDelete: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: ModeSpacing.l) {
                    ModeFieldCard(L10n.Memory.whatModeKnows, prompt: L10n.Memory.whatModeKnows, text: $text)
                    ModeNote(icon: .info, LocalizedStringResource(stringLiteral: L10n.Memory.learnedFrom(entry.learnedFrom)))
                    if let worked = entry.worked { ModeNote(icon: .done, LocalizedStringResource(stringLiteral: worked)) }
                    ModeButton(L10n.Common.delete, icon: .trash, role: .destructive) { onDelete() }
                }
                .padding(ModeSpacing.screenEdge)
            }
            .modeScreen()
            .navigationTitle(entry.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(action: onSave) { Text(L10n.Common.done) } } }
        }
        .presentationDetents([.medium, .large])
    }
}

struct MemoryRoute: View {
    @State private var model: MemoryViewModel

    init(app: AppModel) {
        _model = State(initialValue: MemoryViewModel(screens: app.screens))
    }

    var body: some View {
        MemoryScreen(state: model.state, editing: model.editing, leading: model.leading, deleteTitle: model.deleteTitle,
                     hasSelection: !model.selected.isEmpty, onEvent: model.send)
            .sheet(item: $model.detail) { entry in
                MemoryDetailSheet(entry: entry, text: $model.detailText, onSave: { model.send(.saveDetail) }, onDelete: { model.send(.deleteDetail) })
            }
            .confirmationDialog(Text(L10n.Memory.forgetConfirm), isPresented: $model.confirmingForget, titleVisibility: .visible) {
                Button(role: .destructive) { model.send(.confirmForget) } label: { Text(L10n.Memory.forgetEverything) }
            }
    }
}
