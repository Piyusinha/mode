import ModeDesignSystem
import SwiftUI

// Memory, one screen per kind, as on the "Memory, screen by screen" boards. Every view draws its
// state and sends events; MemoryViewModel does the rest.

/// The hub: learning on or off, a row for each kind of memory, and Forget Everything.
struct MemoryScreen: View {
    let state: MemoryViewModel.State
    let onEvent: (MemoryViewModel.Event) -> Void
    let open: (Route) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.m) {
                Text(L10n.Memory.subtitle).modeText(.callout, color: ModeColor.textSecondary)
                ModeCard { ModeToggleRow(L10n.Memory.learn, isOn: Binding(get: { state.learn }, set: { onEvent(.learn($0)) })) }
                Text(L10n.Memory.hubHint).modeText(.footnote, color: ModeColor.textSecondary).padding(.horizontal, ModeSpacing.xs)
                ModeCard {
                    ModeRow(String(localized: L10n.Memory.aboutYou), subtitle: state.aboutYouSummary, leading: .icon(.person), emphasis: .prominent,
                            action: { open(.memoryAboutYou) }) { ModeIcon.chevron.image.foregroundStyle(ModeColor.textSecondary) }
                    ModeRow(String(localized: L10n.Memory.skills), subtitle: state.routinesSummary, leading: .icon(.skill), emphasis: .prominent,
                            action: { open(.memoryRoutines(app: nil)) }) { ModeIcon.chevron.image.foregroundStyle(ModeColor.textSecondary) }
                    ModeRow(String(localized: L10n.Memory.tips), subtitle: state.appsSummary, leading: .icon(.tip), emphasis: .prominent,
                            action: { open(.memoryApps) }) { ModeIcon.chevron.image.foregroundStyle(ModeColor.textSecondary) }
                }
                if !state.isEmpty {
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
    }
}

/// A list of memory cards in sections, with Edit to delete several: About You, routines, one app's tips.
struct MemoryCardsScreen: View {
    let title: String
    let hint: String
    let sections: [MemoryViewModel.Section]
    let editing: Bool
    let selected: (MemoryViewModel.Card) -> Bool?
    let deleteTitle: String
    let hasSelection: Bool
    let onEvent: (MemoryViewModel.Event) -> Void
    /// A link under the cards (one app's routines), if any.
    var link: (title: String, subtitle: String, action: () -> Void)?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.m) {
                Text(hint).modeText(.callout, color: ModeColor.textSecondary)
                if sections.allSatisfy(\.cards.isEmpty) {
                    ModeEmptyState(icon: .memory, title: L10n.Memory.nothingHere, message: L10n.Memory.nothingHereMessage).frame(maxWidth: .infinity)
                }
                ForEach(sections) { section in
                    if let header = section.header { ModeSectionHeader(LocalizedStringResource(stringLiteral: header)) }
                    ForEach(section.cards) { card in
                        ModeMemoryCard(title: card.title, text: card.body, steps: card.steps, footer: card.footer, selected: selected(card)) {
                            onEvent(.tap(card))
                        }
                    }
                }
                if let link, !editing {
                    ModeCard {
                        ModeRow(link.title, subtitle: link.subtitle, leading: .icon(.skill), action: link.action) {
                            ModeIcon.chevron.image.foregroundStyle(ModeColor.textSecondary)
                        }
                    }
                }
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .scrollEdgeEffectStyle(.soft, for: .all)
        .modeScreen()
        .navigationTitle(Text(title))
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            if !sections.allSatisfy(\.cards.isEmpty) {
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
}

/// App Tips: every app Mode learned something in.
struct MemoryAppsScreen: View {
    let apps: [MemoryViewModel.AppRow]
    let open: (String) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.m) {
                Text(L10n.Memory.appsHint).modeText(.callout, color: ModeColor.textSecondary)
                if apps.isEmpty {
                    ModeEmptyState(icon: .tip, title: L10n.Memory.nothingHere, message: L10n.Memory.nothingHereMessage).frame(maxWidth: .infinity)
                } else {
                    ModeCard {
                        ForEach(apps) { app in
                            ModeRow(app.name, subtitle: app.subtitle, leading: .initial(app.initial), action: { open(app.name) }) {
                                ModeIcon.chevron.image.foregroundStyle(ModeColor.textSecondary)
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .scrollEdgeEffectStyle(.soft, for: .all)
        .modeScreen()
        .navigationTitle(Text(L10n.Memory.tips))
        .navigationBarTitleDisplayMode(.large)
    }
}

/// One item, full screen: its title and text to correct, where it was learned, how often it worked, Delete.
struct MemoryEditScreen: View {
    let title: LocalizedStringResource
    let textLabel: LocalizedStringResource
    @Binding var draftTitle: String
    @Binding var draftText: String
    let learnedFrom: String?
    let worked: String?
    let onSave: () -> Void
    let onDelete: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.l) {
                ModeTextAreaCard(L10n.Memory.titleLabel, text: $draftTitle)
                ModeTextAreaCard(textLabel, text: $draftText, minLines: 8)
                if let learnedFrom { ModeNote(icon: .info, LocalizedStringResource(stringLiteral: learnedFrom)) }
                if let worked { ModeNote(icon: .done, LocalizedStringResource(stringLiteral: worked)) }
                ModeButton(L10n.Common.delete, icon: .trash, role: .destructive, fullWidth: true, action: onDelete)
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .scrollDismissesKeyboard(.interactively)
        .modeScreen()
        .navigationTitle(Text(title))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .confirmationAction) { Button(action: onSave) { Text(L10n.Common.save) } } }
    }
}

// MARK: - Routes: each screen with its view model.

struct MemoryRoute: View {
    @State private var model: MemoryViewModel
    let navigate: (Route) -> Void

    init(app: AppModel, navigate: @escaping (Route) -> Void) {
        self.navigate = navigate
        _model = State(initialValue: MemoryViewModel(screens: app.screens))
    }

    var body: some View {
        MemoryScreen(state: model.state, onEvent: model.send, open: navigate)
            .confirmationDialog(Text(L10n.Memory.forgetConfirm), isPresented: $model.confirmingForget, titleVisibility: .visible) {
                Button(role: .destructive) { model.send(.confirmForget) } label: { Text(L10n.Memory.forgetEverything) }
            }
    }
}

struct MemoryAboutYouRoute: View {
    @State private var model: MemoryViewModel

    init(app: AppModel, navigate: @escaping (Route) -> Void) {
        _model = State(initialValue: MemoryViewModel(screens: app.screens, openEditor: { navigate(.memoryEdit(id: $0)) }))
    }

    var body: some View {
        MemoryCardsScreen(title: String(localized: L10n.Memory.aboutYou), hint: String(localized: L10n.Memory.aboutYouHint),
                          sections: [.init(id: "", header: nil, cards: model.state.aboutYou)], editing: model.editing,
                          selected: model.isSelected, deleteTitle: model.deleteTitle, hasSelection: !model.selected.isEmpty, onEvent: model.send)
    }
}

struct MemoryRoutinesRoute: View {
    @State private var model: MemoryViewModel
    let appName: String?

    init(app: AppModel, appName: String?, navigate: @escaping (Route) -> Void) {
        self.appName = appName
        _model = State(initialValue: MemoryViewModel(screens: app.screens, openEditor: { navigate(.memoryEdit(id: $0)) }))
    }

    var body: some View {
        MemoryCardsScreen(title: appName ?? String(localized: L10n.Memory.skills), hint: String(localized: L10n.Memory.routinesHint),
                          sections: model.state.routines.filter { appName == nil || $0.id == appName }, editing: model.editing,
                          selected: model.isSelected, deleteTitle: model.deleteTitle, hasSelection: !model.selected.isEmpty, onEvent: model.send)
    }
}

struct MemoryAppsRoute: View {
    @State private var model: MemoryViewModel
    let navigate: (Route) -> Void

    init(app: AppModel, navigate: @escaping (Route) -> Void) {
        self.navigate = navigate
        _model = State(initialValue: MemoryViewModel(screens: app.screens))
    }

    var body: some View {
        MemoryAppsScreen(apps: model.state.apps) { navigate(.memoryApp(name: $0)) }
    }
}

struct MemoryAppRoute: View {
    @State private var model: MemoryViewModel
    let name: String
    let navigate: (Route) -> Void

    init(app: AppModel, name: String, navigate: @escaping (Route) -> Void) {
        self.name = name
        self.navigate = navigate
        _model = State(initialValue: MemoryViewModel(screens: app.screens, openEditor: { navigate(.memoryEdit(id: $0)) }))
    }

    var body: some View {
        let detail = model.state.appDetails[name]
        MemoryCardsScreen(title: name, hint: detail?.subtitle ?? "", sections: [.init(id: name, header: nil, cards: detail?.tips ?? [])],
                          editing: model.editing, selected: model.isSelected, deleteTitle: model.deleteTitle,
                          hasSelection: !model.selected.isEmpty, onEvent: model.send,
                          link: detail?.routines.map { routines in
                              (String(localized: L10n.Memory.routinesInApp), routines, { navigate(.memoryRoutines(app: name)) })
                          })
    }
}

struct MemoryEditRoute: View {
    @State private var model: MemoryViewModel
    @Environment(\.dismiss) private var dismiss
    let id: String

    init(app: AppModel, id: String) {
        self.id = id
        _model = State(initialValue: MemoryViewModel(screens: app.screens))
    }

    var body: some View {
        let card = model.state.card(id)
        MemoryEditScreen(
            title: card?.kind == .routine ? L10n.Memory.editRoutine : card?.kind == .tip ? L10n.Memory.editTip : L10n.Memory.editFact,
            textLabel: card?.kind == .routine ? L10n.Memory.stepsLabel : L10n.Memory.whatModeKnows,
            draftTitle: $model.draftTitle, draftText: $model.draftText,
            learnedFrom: card?.learnedFrom, worked: card?.worked,
            onSave: { model.send(.save); dismiss() },
            onDelete: { model.send(.delete); dismiss() }
        )
        .onChange(of: card, initial: true) { _, _ in model.beginEditing(id) }
    }
}
