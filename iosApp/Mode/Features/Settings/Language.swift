import ModeDesignSystem
import ModeKit
import Observation
import SwiftUI

/// Settings → Listening → Language (its board): what the user speaks, and what Mode answers in.
@MainActor
@Observable
final class LanguageModel {
    struct Row: Identifiable, Equatable {
        let id: String
        let name: String
        let detail: String
        let selected: Bool
    }

    struct State: Equatable {
        var rows: [Row] = []
        var replyInSpoken = true
    }

    enum Event { case choose(String), replyInSpoken(Bool) }

    private(set) var state = State()
    private let presenter: LanguagePresenter
    private var updates: Updates?

    init(screens: Screens) {
        presenter = screens.language()
        updates = observe(presenter.state) { [weak self] s in self?.state = Self.map(s) }
    }

    func send(_ event: Event) {
        switch event {
        case let .choose(id): presenter.choose(id: id)
        case let .replyInSpoken(on): presenter.setReplyInSpoken(on: on)
        }
    }

    private static func map(_ s: LanguageUiState) -> State {
        State(rows: s.rows.map { r in
            let reach: String = switch r.reach {
            case .auto: String(localized: L10n.Language.auto)
            case .onIphone: String(localized: L10n.Language.onIphone)
            case .download: String(localized: L10n.Language.download)
            case .cloud: String(localized: L10n.Language.cloud(r.listener ?? ""))
            default: String(localized: L10n.Language.needsCloud)
            }
            return Row(id: r.id, name: r.name, detail: r.native.isEmpty ? reach : "\(r.native) · \(reach)", selected: r.selected)
        }, replyInSpoken: s.replyInSpoken)
    }
}

struct LanguageScreen: View {
    let state: LanguageModel.State
    let onEvent: (LanguageModel.Event) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.s) {
                ModePageTitle(L10n.Language.title, subtitle: L10n.Language.subtitle)
                ModeSectionHeader(L10n.Language.iSpeak)
                ModeCard {
                    ForEach(state.rows) { row in
                        ModeChoiceRow(row.name, tag: nil, detail: row.detail, leading: .none, selected: row.selected, emphasis: .standard) {
                            onEvent(.choose(row.id))
                        }
                    }
                }
                ModeSectionHeader(L10n.Language.repliesIn)
                ModeCard {
                    ModeChoiceRow(String(localized: L10n.Language.spoken), tag: nil, detail: String(localized: L10n.Language.spokenDetail),
                                  leading: .none, selected: state.replyInSpoken, emphasis: .standard) { onEvent(.replyInSpoken(true)) }
                    ModeChoiceRow(String(localized: L10n.Language.english), tag: nil, detail: String(localized: L10n.Language.englishDetail),
                                  leading: .none, selected: !state.replyInSpoken, emphasis: .standard) { onEvent(.replyInSpoken(false)) }
                }
                ModeFootnote(L10n.Language.worksInEnglish).padding(.top, ModeSpacing.xxs)
                ModeExampleCard(lines: [
                    (L10n.Language.exampleYouSay, L10n.Language.exampleSaid),
                    (L10n.Language.exampleWorksOn, L10n.Language.exampleTask),
                    (L10n.Language.exampleSays, L10n.Language.exampleReply),
                ])
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .scrollEdgeEffectStyle(.soft, for: .all)
        .modeScreen()
        .modePageChrome(L10n.Language.title)
    }
}

struct LanguageRoute: View {
    @LazyState private var model: LanguageModel

    init(app: AppModel) {
        _model = LazyState(wrappedValue: LanguageModel(screens: app.screens))
    }

    var body: some View {
        LanguageScreen(state: model.state, onEvent: model.send)
    }
}
