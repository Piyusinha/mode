import ModeDesignSystem
import SwiftUI

/// The Repeat sheet. Draws state, sends events.
struct RepeatScreen: View {
    let state: RepeatViewModel.State
    let dayLabels: [String]
    let onEvent: (RepeatViewModel.Event) -> Void
    let onCancel: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.m + ModeSpacing.xxs) {
                ModeRepeatHeader(L10n.Repeat.title, cancelTitle: L10n.Common.cancel, saveTitle: L10n.Common.save, canSave: state.canSave,
                                onCancel: onCancel, onSave: { onEvent(.save) })
                ModeRepeatNote(L10n.Repeat.moNote)
                ModeTextField(L10n.Repeat.task, prompt: L10n.Repeat.taskPrompt, text: Binding(get: { state.text }, set: { onEvent(.text($0)) }))
                ModeTimeWheel(hour: Binding(get: { state.hour }, set: { onEvent(.hour($0)) }),
                              minute: Binding(get: { state.minute }, set: { onEvent(.minute($0)) }))
                ModePillSegments(
                    [(.everyDay, L10n.Repeat.everyDay), (.weekdays, L10n.Repeat.weekdays), (.weekends, L10n.Repeat.weekends), (.once, L10n.Repeat.once)],
                    selection: Binding(get: { state.frequency }, set: { onEvent(.frequency($0)) }), compact: true
                )
                if state.frequency != .once {
                    ModeDayPicker(labels: dayLabels, selected: state.days) { onEvent(.toggleDay($0)) }
                }
                Text(state.note).modeText(.footnote, color: ModeColor.textSecondary)
                    .padding(.horizontal, ModeSpacing.xs)
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.top, ModeSpacing.xl)
        }
        .scrollBounceBehavior(.basedOnSize)
        .modeScreen()
        .presentationDragIndicator(.visible)
        .presentationBackground(ModeColor.page)
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
    @LazyState private var model: RepeatViewModel
    private let dismiss: () -> Void

    init(app: AppModel, text: String, dismiss: @escaping () -> Void) {
        self.dismiss = dismiss
        _model = LazyState(wrappedValue: RepeatViewModel(screens: app.screens, text: text, dismiss: dismiss))
    }

    var body: some View {
        RepeatScreen(state: model.state, dayLabels: model.dayLabels, onEvent: model.send, onCancel: dismiss)
    }
}
