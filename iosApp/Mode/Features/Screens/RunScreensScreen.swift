import ModeDesignSystem
import SwiftUI

/// All screens, as on its board: a grid of every screen Mode saw, or played back one by one.
struct RunScreensScreen: View {
    let state: RunScreensViewModel.State
    @Binding var mode: RunScreensViewModel.Mode
    @Binding var playIndex: Int
    let onOpen: (RunScreensViewModel.Shot) -> Void
    let onShare: () -> Void

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
        RunScreensScreen(state: model.state, mode: $model.mode, playIndex: $model.playIndex, onOpen: model.open, onShare: model.share)
            .sheet(item: $model.sharing) { ShareSheet(text: $0.text) }
    }
}
