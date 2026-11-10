import ModeDesignSystem
import ModeKit
import Observation
import SwiftUI

/// Settings → Access (boards "Access · full access" and "Access · ask first"): how much Mode does on its own.
@MainActor
@Observable
final class AccessViewModel {
    struct State: Equatable {
        var fullAccess = false
        var words: [String] = []
        var canAddWord = true
        var readCodes = true
        /// "Off" or "₹2,000".
        var askAbove = ""
        var askAboveOn = false
    }

    enum Event {
        case fullAccess(Bool)
        case addWord(String)
        case removeWord(String)
        case readCodes(Bool)
        case askAbove(Double?)
    }

    private(set) var state = State()
    /// The amounts offered for "Ask before paying more than", formatted.
    let amounts: [(Double, String)]
    private let presenter: AccessPresenter
    private var updates: Updates?

    init(screens: Screens) {
        presenter = screens.access()
        amounts = AccessPresenter.companion.AMOUNTS.map { ($0.doubleValue, Self.money($0.doubleValue)) }
        updates = observe(presenter.state) { [weak self] s in
            self?.state = State(
                fullAccess: s.fullAccess, words: s.words, canAddWord: s.canAddWord, readCodes: s.readCodes,
                askAbove: s.askAbove.map { Self.money($0.doubleValue) } ?? String(localized: L10n.Settings.off),
                askAboveOn: s.askAbove != nil
            )
        }
    }

    func send(_ event: Event) {
        switch event {
        case let .fullAccess(on): presenter.setFullAccess(on: on)
        case let .addWord(word): presenter.addWord(word: word)
        case let .removeWord(word): presenter.removeWord(word: word)
        case let .readCodes(on): presenter.setReadCodes(on: on)
        case let .askAbove(amount): presenter.setAskAbove(amount: amount.map { KotlinDouble(value: $0) })
        }
    }

    isolated deinit {
        updates?.cancel()
        presenter.close()
    }

    /// In the phone's own currency: amounts on screen are in it too.
    private static func money(_ amount: Double) -> String {
        amount.formatted(.currency(code: Locale.current.currency?.identifier ?? "INR").precision(.fractionLength(0)))
    }
}

struct AccessScreen: View {
    let state: AccessViewModel.State
    let amounts: [(Double, String)]
    let onEvent: (AccessViewModel.Event) -> Void
    @State private var adding = false
    @State private var newWord = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.s) {
                ModePageTitle(L10n.Access.title, subtitle: L10n.Access.subtitle)
                    .padding(.bottom, ModeSpacing.xl)
                ModeCard {
                    ModeChoiceRow(String(localized: L10n.Access.askFirst), tag: nil, detail: String(localized: L10n.Access.askFirstDetail),
                                  leading: .none, selected: !state.fullAccess) { onEvent(.fullAccess(false)) }
                    ModeChoiceRow(String(localized: L10n.Access.full), tag: nil, detail: String(localized: L10n.Access.fullDetail),
                                  leading: .none, selected: state.fullAccess) { onEvent(.fullAccess(true)) }
                }

                if state.fullAccess {
                    ModeSectionHeader(L10n.Access.spending)
                    ModeCard {
                        Menu {
                            Button { onEvent(.askAbove(nil)) } label: { Text(L10n.Settings.off) }
                            ForEach(amounts, id: \.0) { amount, label in
                                Button { onEvent(.askAbove(amount)) } label: { Text(label) }
                            }
                        } label: {
                            ModeValueRow(String(localized: L10n.Access.askAbove), value: state.askAbove, action: {}).allowsHitTesting(false)
                        }
                        .buttonStyle(.plain)
                    }
                    ModeFootnote(state.askAboveOn ? L10n.Access.askAboveOnNote : L10n.Access.askAboveOffNote)
                } else {
                    ModeSectionHeader(L10n.Access.oneTask)
                    ModeCard {
                        ModeChipFlow {
                            ForEach(state.words, id: \.self) { word in
                                ModeRemovableChip(word, removeLabel: L10n.Access.remove(word)) { onEvent(.removeWord(word)) }
                            }
                            if state.canAddWord { ModeAddChip(L10n.Access.addWord) { adding = true } }
                        }
                        .padding(.vertical, ModeSpacing.headerGap)
                    }
                    ModeFootnote(verbatim: L10n.Access.oneTaskNote(state.words.first ?? "just do it"))
                }

                ModeSectionHeader(L10n.Access.codes)
                ModeCard {
                    ModeToggleRow(L10n.Access.readCodes, isOn: Binding(get: { state.readCodes }, set: { onEvent(.readCodes($0)) }))
                }
                ModeFootnote(state.readCodes ? L10n.Access.readCodesNote : L10n.Access.askCodesNote)

                ModeSectionHeader(L10n.Access.alwaysAsks)
                ModeCard {
                    ModeBadgeRow(String(localized: L10n.Access.pin), subtitle: String(localized: L10n.Access.pinDetail), icon: .lock)
                    ModeBadgeRow(String(localized: L10n.Access.passwords), subtitle: String(localized: L10n.Access.passwordsDetail), icon: .lock)
                    ModeBadgeRow(String(localized: L10n.Access.faceID), subtitle: String(localized: L10n.Access.faceIDDetail), icon: .lock)
                }
                ModeFootnote(L10n.Access.alwaysAsksNote)
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .scrollEdgeEffectStyle(.soft, for: .all)
        .modeScreen()
        .modePageChrome(L10n.Access.title)
        .alert(Text(L10n.Access.addWordTitle), isPresented: $adding) {
            TextField(text: $newWord) { Text(L10n.Access.addWordPrompt) }
                .textInputAutocapitalization(.never)
            Button(role: .cancel) { newWord = "" } label: { Text(L10n.Common.cancel) }
            Button { onEvent(.addWord(newWord)); newWord = "" } label: { Text(L10n.Access.add) }
        } message: {
            Text(L10n.Access.addWordMessage)
        }
    }
}

struct AccessRoute: View {
    @LazyState private var model: AccessViewModel

    init(app: AppModel) {
        _model = LazyState(wrappedValue: AccessViewModel(screens: app.screens))
    }

    var body: some View {
        AccessScreen(state: model.state, amounts: model.amounts, onEvent: model.send)
    }
}
