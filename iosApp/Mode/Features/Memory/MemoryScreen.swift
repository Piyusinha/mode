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
            VStack(alignment: .leading, spacing: ModeSpacing.s) {
                ModeMemoryHeader(String(localized: L10n.Tab.memory), subtitle: String(localized: L10n.Memory.subtitle))
                    .padding(.top, ModeSpacing.memoryHeaderTop)
                    .padding(.bottom, ModeSpacing.memoryCardGap - ModeSpacing.s)
                ModeMemoryToggleCard(L10n.Memory.learn, isOn: Binding(get: { state.learn }, set: { onEvent(.learn($0)) }))
                Text(L10n.Memory.hubHint).modeText(.footnote, color: ModeColor.textSecondary)
                    .padding(.horizontal, ModeSpacing.xs).padding(.top, ModeSpacing.xs)
                ModeCard(lifted: true) {
                    ModeMemoryRow(String(localized: L10n.Memory.aboutYou), subtitle: state.aboutYouSummary,
                                  leading: .icon(.profile, background: ModeColor.infoSoft, foreground: ModeColor.infoText)) { open(.memoryAboutYou) }
                    ModeMemoryRow(String(localized: L10n.Memory.skills), subtitle: state.routinesSummary, leading: .mo) { open(.memoryRoutines(app: nil)) }
                    ModeMemoryRow(String(localized: L10n.Memory.tips), subtitle: state.appsSummary,
                                  leading: .icon(.tip, background: ModeColor.tileOrange, foreground: ModeColor.tileOrangeText)) { open(.memoryApps) }
                }
                .padding(.top, ModeSpacing.s)
                if !state.isEmpty {
                    ModeButton(L10n.Memory.forgetEverything, role: .destructive) { onEvent(.askForget) }
                        .frame(maxWidth: .infinity)
                        .padding(.top, ModeSpacing.memoryCardGap)
                }
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .scrollEdgeEffectStyle(.soft, for: .all)
        .modeScreen()
        .navigationTitle(Text(L10n.Tab.memory))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(removing: .title)
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
            VStack(alignment: .leading, spacing: ModeSpacing.memoryCardGap) {
                ModeMemoryHeader(title, subtitle: hint)
                    .padding(.top, ModeSpacing.memorySubpageTop)
                    .padding(.bottom, ModeSpacing.memoryCardGap - ModeSpacing.xs)
                if sections.allSatisfy(\.cards.isEmpty) {
                    ModeEmptyState(icon: .memory, title: L10n.Memory.nothingHere, message: L10n.Memory.nothingHereMessage).frame(maxWidth: .infinity)
                }
                ForEach(sections) { section in
                    if let header = section.header {
                        ModeSectionHeader(LocalizedStringResource(stringLiteral: header))
                    }
                    ForEach(section.cards) { card in
                        ModeMemoryCard(title: card.title, text: card.body, steps: card.steps, footer: card.footer, badge: card.badge,
                                       selected: selected(card)) { onEvent(.tap(card)) }
                    }
                }
                if let link, !editing {
                    ModeMemoryLinkCard(link.title, subtitle: link.subtitle, action: link.action)
                }
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .scrollEdgeEffectStyle(.soft, for: .all)
        .modeScreen()
        .navigationTitle(Text(title))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(removing: .title)
        .toolbar {
            if !sections.allSatisfy(\.cards.isEmpty) {
                ToolbarItem(placement: .topBarTrailing) {
                    ModeMemoryBarButton(editing ? L10n.Common.done : L10n.Memory.edit, prominent: editing) { onEvent(.toggleEditing) }
                }
                .sharedBackgroundVisibility(.hidden)
            }
        }
        .safeAreaInset(edge: .bottom) {
            if editing && hasSelection {
                ModeMemoryDeleteButton(deleteTitle) { onEvent(.deleteSelected) }
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
            VStack(alignment: .leading, spacing: ModeSpacing.memoryCardGap) {
                ModeMemoryHeader(String(localized: L10n.Memory.tips), subtitle: String(localized: L10n.Memory.appsHint))
                    .padding(.top, ModeSpacing.memorySubpageTop)
                    .padding(.bottom, ModeSpacing.memoryCardGap - ModeSpacing.xs)
                if apps.isEmpty {
                    ModeEmptyState(icon: .tip, title: L10n.Memory.nothingHere, message: L10n.Memory.nothingHereMessage).frame(maxWidth: .infinity)
                } else {
                    ModeCard(lifted: true) {
                        ForEach(apps) { app in
                            ModeMemoryRow(app.name, subtitle: app.subtitle, leading: .initial(app.initial), compact: true) { open(app.name) }
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
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(removing: .title)
    }
}

/// One app's tips, as on "What you taught, in Memory": the app's tile and name, its tips in one list
/// (each says whose it is; swipe to edit or delete), and a link to its routines.
struct MemoryAppScreen: View {
    let name: String
    let detail: MemoryViewModel.AppDetail?
    let onEvent: (MemoryViewModel.Event) -> Void
    let openRoutines: () -> Void

    var body: some View {
        List {
            ModeMemoryAppHeader(letter: detail?.letter ?? "", title: name, subtitle: detail?.subtitle ?? "")
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
                .listRowInsets(EdgeInsets(top: ModeSpacing.memorySubpageTop, leading: ModeSpacing.s, bottom: ModeSpacing.s, trailing: ModeSpacing.s))
            if let detail, !detail.tips.isEmpty {
                Section {
                    ForEach(detail.tips) { card in
                        ModeMemoryTipRow(text: card.body ?? card.title, badge: card.badge, footer: card.footer)
                            .onTapGesture { onEvent(.tap(card)) }
                            .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                                Button(role: .destructive) { onEvent(.remove(card)) } label: { Text(L10n.Common.delete) }
                                Button { onEvent(.edit(card)) } label: { Text(L10n.Memory.edit) }.tint(ModeColor.info)
                            }
                            .listRowBackground(ModeColor.card)
                            .listRowSeparatorTint(ModeColor.separator)
                            .listRowInsets(EdgeInsets(top: ModeSpacing.none, leading: ModeSpacing.l, bottom: ModeSpacing.none, trailing: ModeSpacing.l))
                    }
                } header: {
                    Text(L10n.Memory.tipsHeading).modeText(.section).textCase(nil)
                        .listRowInsets(EdgeInsets(top: ModeSpacing.none, leading: ModeSpacing.xs, bottom: ModeSpacing.s, trailing: ModeSpacing.xs))
                } footer: {
                    Text(L10n.Memory.swipeHint).modeText(.footnote, color: ModeColor.textSecondary)
                        .listRowInsets(EdgeInsets(top: ModeSpacing.s, leading: ModeSpacing.s, bottom: ModeSpacing.none, trailing: ModeSpacing.s))
                }
            } else {
                ModeEmptyState(icon: .tip, title: L10n.Memory.nothingHere, message: L10n.Memory.nothingHereMessage)
                    .frame(maxWidth: .infinity)
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
            }
            if let routines = detail?.routines {
                Section {
                    ModeMemoryRow(String(localized: L10n.Memory.routinesInApp), subtitle: routines, leading: .mo, compact: true, action: openRoutines)
                        .listRowBackground(ModeColor.card)
                        .listRowInsets(EdgeInsets(top: ModeSpacing.none, leading: ModeSpacing.l, bottom: ModeSpacing.none, trailing: ModeSpacing.l))
                }
            }
        }
        .listStyle(.insetGrouped)
        .listSectionSpacing(ModeSpacing.l)
        .contentMargins(.horizontal, ModeSpacing.screenEdge, for: .scrollContent)
        .contentMargins(.top, ModeSpacing.none, for: .scrollContent)
        .scrollEdgeEffectStyle(.soft, for: .all)
        .modeScreen()
        .navigationTitle(Text(name))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(removing: .title)
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
            VStack(alignment: .leading, spacing: ModeSpacing.memoryCardGap) {
                ModeMemoryHeader(String(localized: title), subtitle: nil)
                    .padding(.top, ModeSpacing.memorySubpageTop)
                    .padding(.bottom, ModeSpacing.xs)
                ModeMemoryField(L10n.Memory.titleLabel, text: $draftTitle)
                ModeMemoryField(textLabel, text: $draftText, minHeight: ModeSize.memoryEditorMin)
                if let learnedFrom { ModeMemoryNote(.learned, learnedFrom) }
                if let worked { ModeMemoryNote(.worked, worked) }
                ModeMemoryDeleteButton(String(localized: L10n.Common.delete), action: onDelete)
                    .padding(.top, ModeSpacing.s)
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .scrollDismissesKeyboard(.interactively)
        .modeScreen()
        .navigationTitle(Text(title))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(removing: .title)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) { ModeMemoryBarButton(L10n.Common.save, prominent: true, action: onSave) }
                .sharedBackgroundVisibility(.hidden)
        }
    }
}

// MARK: - Routes: each screen with its view model.

struct MemoryRoute: View {
    @LazyState private var model: MemoryViewModel
    let navigate: (Route) -> Void

    init(app: AppModel, navigate: @escaping (Route) -> Void) {
        self.navigate = navigate
        _model = LazyState(wrappedValue: MemoryViewModel(screens: app.screens))
    }

    var body: some View {
        MemoryScreen(state: model.state, onEvent: model.send, open: navigate)
            .confirmationDialog(Text(L10n.Memory.forgetConfirm), isPresented: $model.confirmingForget, titleVisibility: .visible) {
                Button(role: .destructive) { model.send(.confirmForget) } label: { Text(L10n.Memory.forgetEverything) }
            }
    }
}

struct MemoryAboutYouRoute: View {
    @LazyState private var model: MemoryViewModel

    init(app: AppModel, navigate: @escaping (Route) -> Void) {
        _model = LazyState(wrappedValue: MemoryViewModel(screens: app.screens, openEditor: { navigate(.memoryEdit(id: $0)) }))
    }

    var body: some View {
        MemoryCardsScreen(title: String(localized: L10n.Memory.aboutYou), hint: String(localized: L10n.Memory.aboutYouHint),
                          sections: [.init(id: "", header: nil, cards: model.state.aboutYou)], editing: model.editing,
                          selected: model.isSelected, deleteTitle: model.deleteTitle, hasSelection: !model.selected.isEmpty, onEvent: model.send)
    }
}

struct MemoryRoutinesRoute: View {
    @LazyState private var model: MemoryViewModel
    let appName: String?

    init(app: AppModel, appName: String?, navigate: @escaping (Route) -> Void) {
        self.appName = appName
        _model = LazyState(wrappedValue: MemoryViewModel(screens: app.screens, openEditor: { navigate(.memoryEdit(id: $0)) }))
    }

    var body: some View {
        MemoryCardsScreen(title: appName ?? String(localized: L10n.Memory.skills), hint: String(localized: L10n.Memory.routinesHint),
                          sections: model.state.routines.filter { appName == nil || $0.id == appName }, editing: model.editing,
                          selected: model.isSelected, deleteTitle: model.deleteTitle, hasSelection: !model.selected.isEmpty, onEvent: model.send)
    }
}

struct MemoryAppsRoute: View {
    @LazyState private var model: MemoryViewModel
    let navigate: (Route) -> Void

    init(app: AppModel, navigate: @escaping (Route) -> Void) {
        self.navigate = navigate
        _model = LazyState(wrappedValue: MemoryViewModel(screens: app.screens))
    }

    var body: some View {
        MemoryAppsScreen(apps: model.state.apps) { navigate(.memoryApp(name: $0)) }
    }
}

struct MemoryAppRoute: View {
    @LazyState private var model: MemoryViewModel
    let name: String
    let navigate: (Route) -> Void

    init(app: AppModel, name: String, navigate: @escaping (Route) -> Void) {
        self.name = name
        self.navigate = navigate
        _model = LazyState(wrappedValue: MemoryViewModel(screens: app.screens, openEditor: { navigate(.memoryEdit(id: $0)) }))
    }

    var body: some View {
        MemoryAppScreen(name: name, detail: model.state.appDetails[name], onEvent: model.send) { navigate(.memoryRoutines(app: name)) }
    }
}

struct MemoryEditRoute: View {
    @LazyState private var model: MemoryViewModel
    @Environment(\.dismiss) private var dismiss
    let id: String

    init(app: AppModel, id: String) {
        self.id = id
        _model = LazyState(wrappedValue: MemoryViewModel(screens: app.screens))
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
