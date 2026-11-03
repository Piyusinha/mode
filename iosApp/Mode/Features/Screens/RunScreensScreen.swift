import ModeDesignSystem
import SwiftUI

/// All screens, as on its board: a grid of every screen Mode saw, or played back one by one.
struct RunScreensScreen: View {
    let state: RunScreensViewModel.State
    @Binding var mode: RunScreensViewModel.Mode
    @Binding var playIndex: Int
    let onOpen: (RunScreensViewModel.Shot) -> Void
    let onShare: () -> Void
    let onWrong: (RunScreensViewModel.Shot) -> Void
    let savingFix: Bool
    let fixReply: String?
    let onFixSeen: () -> Void

    private let columns = Array(repeating: GridItem(.flexible(), spacing: ModeSize.gridSpacing), count: 3)

    var body: some View {
        Group {
            if state.shots.isEmpty {
                ModeEmptyState(icon: .screens, title: L10n.Screens.emptyTitle, message: L10n.Screens.emptyMessage)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if mode == .grid {
                ScrollView {
                    VStack(alignment: .leading, spacing: ModeSpacing.l) {
                        header
                        LazyVGrid(columns: columns, spacing: ModeSize.gridSpacing) {
                            ForEach(state.shots) { shot in
                                Button { onOpen(shot) } label: { cell(shot) }.buttonStyle(.plain)
                                    .contextMenu {
                                        Button { onWrong(shot) } label: { Label { Text(L10n.Screens.wrongStep) } icon: { ModeIcon.warning.image } }
                                    }
                            }
                        }
                    }
                    .padding(.horizontal, ModeSpacing.screenEdge)
                    .padding(.bottom, ModeSpacing.xxxl * 2)
                }
                .scrollEdgeEffectStyle(.soft, for: .all)
            } else {
                VStack(alignment: .leading, spacing: ModeSpacing.m) {
                    header.padding(.horizontal, ModeSpacing.screenEdge)
                    TabView(selection: $playIndex) {
                        ForEach(Array(state.shots.enumerated()), id: \.offset) { index, shot in
                            VStack(spacing: ModeSpacing.s) {
                                if let image = shot.image {
                                    Image(uiImage: image).resizable().scaledToFit().clipShape(.rect(cornerRadius: ModeRadius.card))
                                }
                                Text(shot.label).modeText(.callout, color: ModeColor.textSecondary)
                                ModeButton(L10n.Screens.wrongStep, icon: .warning, role: .secondary) { onWrong(shot) }
                            }
                            .padding(.horizontal, ModeSpacing.xl)
                            .tag(index)
                        }
                    }
                    .tabViewStyle(.page(indexDisplayMode: .never))
                    .animation(ModeMotion.standard, value: playIndex)
                }
            }
        }
        .modeScreen()
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .topBarTrailing) { ModeToolbarButton(.share, label: L10n.Conversation.share, action: onShare) } }
        .safeAreaInset(edge: .top) {
            if savingFix {
                HStack(spacing: ModeSpacing.s) {
                    ProgressView().tint(ModeColor.tint)
                    Text(L10n.Screens.savingFix).modeText(.subheadline, color: ModeColor.textSecondary)
                }
                .padding(.vertical, ModeSpacing.s)
            } else if let fixReply {
                ModeChip(fixReply, icon: .memory, actionTitle: L10n.Screens.ok, action: onFixSeen)
                    .padding(.vertical, ModeSpacing.s)
            }
        }
        .safeAreaInset(edge: .bottom) {
            if !state.shots.isEmpty {
                ModeGlassSwitch([ModeTabItem(.grid, icon: .grid, title: L10n.Screens.grid), ModeTabItem(.play, icon: .play, title: L10n.Screens.play)], selection: $mode)
                    .padding(.bottom, ModeSpacing.s)
            }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
            Text(state.title).modeText(.title)
            Text(state.subtitle).modeText(.subheadline, color: ModeColor.textSecondary)
        }
    }

    private func cell(_ shot: RunScreensViewModel.Shot) -> some View {
        VStack(alignment: .leading, spacing: ModeSpacing.xs) {
            ZStack(alignment: .topLeading) {
                Group {
                    if let image = shot.image { Image(uiImage: image).resizable().scaledToFill() } else { ModeColor.fill }
                }
                .aspectRatio(ModeSize.screenAspect, contentMode: .fit)
                .clipShape(.rect(cornerRadius: ModeRadius.chip))
                Text(shot.number)
                    .modeText(.captionEmphasis, color: ModeColor.onAccent)
                    .padding(.horizontal, ModeSpacing.s)
                    .padding(.vertical, ModeSpacing.xxs)
                    .background(ModeColor.accent, in: .capsule)
                    .padding(ModeSpacing.xs)
            }
            Text(shot.label).modeText(.caption, color: ModeColor.textSecondary).lineLimit(1)
        }
    }
}

struct RunScreensRoute: View {
    @State private var model: RunScreensViewModel

    init(app: AppModel, taskId: String) {
        _model = State(initialValue: RunScreensViewModel(screens: app.screens, taskId: taskId))
    }

    var body: some View {
        RunScreensScreen(state: model.state, mode: $model.mode, playIndex: $model.playIndex, onOpen: model.open, onShare: model.share,
                         onWrong: model.markWrong, savingFix: model.savingFix, fixReply: model.fixReply, onFixSeen: model.fixSeen)
            .sheet(item: $model.sharing) { ShareSheet(text: $0.text) }
            .sheet(item: $model.fixing) { shot in FixStepSheet(number: shot.number, label: shot.label) { model.saveFix($0, everyTime: $1) } }
    }
}

/// "What should Mode have done?" for a step it got wrong: the user's words, then just this time or every time.
private struct FixStepSheet: View {
    let number: String
    let label: String
    let save: (String, Bool) -> Void
    @State private var text = ""
    @State private var everyTime = true
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.l) {
            Text(L10n.Screens.whatInstead).modeText(.title2)
            Text(L10n.Screens.stepLine(number, label)).modeText(.subheadline, color: ModeColor.textSecondary)
            TextField(text: $text, axis: .vertical) { Text(L10n.Screens.whatInsteadPrompt) }
                .modeText(.body)
                .lineLimit(3...6)
                .padding(ModeSpacing.l)
                .background(ModeColor.field, in: .rect(cornerRadius: ModeRadius.field))
            ModeSegmented([(false, L10n.Screens.justThisTime), (true, L10n.Screens.everyTime)], selection: $everyTime)
            Text(everyTime ? L10n.Screens.everyTimeNote : L10n.Screens.justThisTimeNote).modeText(.footnote, color: ModeColor.textSecondary)
            ModeButton(L10n.Common.save, fullWidth: true) { save(text, everyTime); dismiss() }
                .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        }
        .padding(ModeSpacing.screenEdge)
        .frame(maxHeight: .infinity, alignment: .top)
        .modeScreen()
        .presentationDetents([.medium, .large])
    }
}
