import ModeDesignSystem
import SwiftUI

/// The Repeat sheet. Draws state, sends events.
struct RepeatScreen: View {
    let state: RepeatViewModel.State
    let dayLabels: [String]
    let onEvent: (RepeatViewModel.Event) -> Void
    let onCancel: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: ModeSpacing.l) {
                    ModeTextField(L10n.Repeat.task, prompt: L10n.Repeat.taskPrompt, text: Binding(get: { state.text }, set: { onEvent(.text($0)) }))
                    DatePicker(selection: Binding(get: { state.time }, set: { onEvent(.time($0)) }), displayedComponents: .hourAndMinute) {
                        Text(L10n.Repeat.time).modeText(.body)
                    }
                    .datePickerStyle(.wheel)
                    .frame(maxWidth: .infinity)
                    .background(ModeColor.field, in: .rect(cornerRadius: ModeRadius.card))
                    ModeSegmented(
                        [(.everyDay, L10n.Repeat.everyDay), (.weekdays, L10n.Repeat.weekdays), (.weekends, L10n.Repeat.weekends), (.once, L10n.Repeat.once)],
                        selection: Binding(get: { state.frequency }, set: { onEvent(.frequency($0)) })
                    )
                    if state.frequency != .once {
                        ModeDayPicker(labels: dayLabels, selected: state.days) { onEvent(.toggleDay($0)) }
                    }
                    Text(state.note).modeText(.footnote, color: ModeColor.textSecondary)
                }
                .padding(ModeSpacing.screenEdge)
            }
            .modeScreen()
            .navigationTitle(Text(L10n.Repeat.title))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { ModeButton(L10n.Common.cancel, role: .plain, action: onCancel) }
                ToolbarItem(placement: .confirmationAction) {
                    ModeButton(L10n.Common.save) { onEvent(.save) }.disabled(!state.canSave)
                }
            }
        }
    }
}

/// Connects the Repeat sheet to its ViewModel.
struct RepeatRoute: View {
    @Environment(\.dismiss) private var dismiss
    let app: AppModel
    let text: String

    var body: some View {
        RepeatContent(app: app, text: text, dismiss: { dismiss() })
    }
}

private struct RepeatContent: View {
    @State private var model: RepeatViewModel
    private let dismiss: () -> Void

    init(app: AppModel, text: String, dismiss: @escaping () -> Void) {
        self.dismiss = dismiss
        _model = State(initialValue: RepeatViewModel(screens: app.screens, text: text, dismiss: dismiss))
    }

    var body: some View {
        RepeatScreen(state: model.state, dayLabels: model.dayLabels, onEvent: model.send, onCancel: dismiss)
    }
}
