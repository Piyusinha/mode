import ModeDesignSystem
import ModeKit
import Observation
import SwiftUI

/// Settings → Health & repair (its board): every part Mode needs, its status, and the repair.
@MainActor
@Observable
final class HealthModel {
    struct Row: Identifiable, Equatable {
        let id: String
        let group: String
        let title: String
        let detail: String
        let icon: ModeHealthIcon
        let status: ModeHealthRow.Status
        let repair: Bool
        let opens: String?
    }

    struct State: Equatable {
        var rows: [Row] = []
        var problems = 0
        var repairable = 0
        var checking = false
        var repairing = false
        /// The banner when something needs attention: "2 things need attention", "Mode can repair one; one needs you."
        var attentionTitle = ""
        var attentionDetail = ""
    }

    enum Event { case check, repair, open(String), share }

    private(set) var state = State()
    var sharing: SharedText?
    private let presenter: HealthPresenter
    private let navigate: (Route) -> Void
    private var updates: Updates?

    init(screens: Screens, navigate: @escaping (Route) -> Void) {
        presenter = screens.health()
        self.navigate = navigate
        updates = observe(presenter.state) { [weak self] s in self?.state = Self.map(s) }
    }

    func send(_ event: Event) {
        switch event {
        case .check: presenter.check()
        case .repair: presenter.repair()
        case .share: sharing = SharedText(text: presenter.report())
        case let .open(page):
            switch page {
            case "providers", "voice": navigate(page == "voice" ? .voice : .settings)
            case "listening": navigate(.listening)
            default: if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
            }
        }
    }

    private static func map(_ s: HealthUiState) -> State {
        State(rows: s.rows.map { r in
            let (title, icon): (LocalizedStringResource, ModeHealthIcon) = switch r.item {
            case .paired: (L10n.Health.paired, .paired)
            case .vpn: (L10n.Health.vpn, .vpn)
            case .reach: (L10n.Health.reach, .reach)
            case .screen: (L10n.Health.screen, .screen)
            case .ai: (L10n.Health.ai, .ai)
            case .actions: (L10n.Health.actions, .actions)
            case .microphone: (L10n.Health.microphone, .microphone)
            case .listening: (L10n.Health.listening, .listening)
            case .speaking: (L10n.Health.speaking, .speaking)
            default: (L10n.Health.notifications, .notifications)
            }
            let status: ModeHealthRow.Status = switch r.state {
            case .ok: .ok
            case .warn: .warning
            case .repaired: .repaired
            case .busy: .busy
            case .waiting: .info
            default: .checking
            }
            let detail = r.state == .repaired ? String(localized: L10n.Health.repaired) : r.detail
            return Row(id: r.item.name, group: r.item.group, title: String(localized: title), detail: detail, icon: icon, status: status,
                       repair: r.fix == .repair, opens: r.fix == .open ? r.opens : nil)
        }, problems: Int(s.problems), repairable: Int(s.repairable), checking: s.checking, repairing: s.repairing,
        attentionTitle: L10n.Health.needAttention(Int(s.problems)),
        attentionDetail: s.repairable > 0
            ? L10n.Health.canRepair(spelled(Int(s.repairable)), rest: spelled(Int(s.problems - s.repairable)), restCount: Int(s.problems - s.repairable))
            : String(localized: L10n.Health.needsYou))
    }

    /// A small count in words ("one", "two"), as people say it.
    private static func spelled(_ n: Int) -> String {
        let words = NumberFormatter()
        words.numberStyle = .spellOut
        return words.string(from: NSNumber(value: n)) ?? String(n)
    }
}

struct HealthScreen: View {
    let state: HealthModel.State
    let onEvent: (HealthModel.Event) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.s) {
                ModePageTitle(L10n.Health.title).padding(.bottom, ModeSpacing.m)
                banner
                section(L10n.Health.phoneControl, "phone")
                section(L10n.Settings.ai, "ai")
                section(L10n.Voice.section, "voice")
                section(L10n.Health.thisIphone, "iphone")
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .scrollEdgeEffectStyle(.soft, for: .all)
        .modeScreen()
        .modePageChrome(L10n.Health.title)
        .safeAreaInset(edge: .bottom) {
            HStack(spacing: ModeSpacing.m - ModeSpacing.xxs) {
                ModeWideButton(L10n.Health.share, style: .raised) { onEvent(.share) }
                ModeWideButton(state.checking ? L10n.Health.checking : L10n.Health.checkAgain, style: .ink) { onEvent(.check) }
                    .disabled(state.checking || state.repairing)
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.top, ModeSpacing.xxl)
            .padding(.bottom, ModeSpacing.s)
            .background(ModeEdgeFade(edge: .bottom).ignoresSafeArea(edges: .bottom))
        }
    }

    @ViewBuilder private var banner: some View {
        if state.checking || state.repairing {
            ModeHealthBanner(ok: true, busy: true, title: String(localized: state.repairing ? L10n.Health.repairing : L10n.Health.checking),
                             detail: String(localized: L10n.Health.checkingDetail))
        } else if state.problems == 0 {
            ModeHealthBanner(ok: true, title: String(localized: L10n.Health.allGood), detail: String(localized: L10n.Health.checkedNow))
        } else {
            ModeHealthBanner(ok: false, title: state.attentionTitle, detail: state.attentionDetail,
                             actionTitle: state.repairable > 0 ? L10n.Health.repair : nil, action: state.repairable > 0 ? { onEvent(.repair) } : nil)
        }
    }

    private func section(_ title: LocalizedStringResource, _ group: String) -> some View {
        VStack(alignment: .leading, spacing: ModeSpacing.s) {
            ModeSectionHeader(title)
            ModeCard {
                ForEach(state.rows.filter { $0.group == group }) { row in
                    ModeHealthRow(row.title, detail: row.detail, icon: row.icon, status: row.status,
                                  actionTitle: row.repair || row.opens != nil ? L10n.Health.fix : nil,
                                  actionStyle: row.repair ? .repair : .open,
                                  action: row.repair ? { onEvent(.repair) } : row.opens.map { page in { onEvent(.open(page)) } })
                }
            }
        }
    }
}

struct HealthRoute: View {
    @State private var model: HealthModel

    init(app: AppModel, navigate: @escaping (Route) -> Void) {
        _model = State(initialValue: HealthModel(screens: app.screens, navigate: navigate))
    }

    var body: some View {
        HealthScreen(state: model.state, onEvent: model.send)
            .task { model.send(.check) }
            .sheet(item: $model.sharing) { ShareSheet(text: $0.text) }
    }
}
