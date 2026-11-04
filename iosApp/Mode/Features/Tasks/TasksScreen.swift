import ModeDesignSystem
import SwiftUI

/// Tasks, as on its board. Draws state, sends events.
struct TasksScreen: View {
    let state: TasksViewModel.State
    /// The running task's plan, as bars on its card.
    let progress: (done: Int, total: Int)?
    @Binding var filter: TasksViewModel.Filter
    let onEvent: (TasksViewModel.Event) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.s) {
                Text(L10n.Tab.tasks).modeText(.largeTitle).accessibilityAddTraits(.isHeader)
                    .padding(.leading, ModeSpacing.xs)
                    .padding(.bottom, ModeSpacing.s - ModeSpacing.xxs)
                ModePillSegments([(.now, L10n.Tasks.now), (.scheduled, L10n.Tasks.scheduled), (.done, L10n.Tasks.done)], selection: $filter)
                switch filter {
                case .now: now
                case .scheduled: scheduled
                case .done: done
                }
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .scrollEdgeEffectStyle(.soft, for: .all)
        .modeScreen()
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button { onEvent(.newTask) } label: { Label { Text(L10n.Tasks.newTask) } icon: { ModeIcon.ask.image } }
                    Button { onEvent(.newRepeat) } label: { Label { Text(L10n.Tasks.newRepeat) } icon: { ModeIcon.repeatTask.image } }
                } label: {
                    ModeIcon.add.image
                        .font(ModeIconFont.regular)
                        .foregroundStyle(ModeColor.textPrimary)
                        .frame(width: ModeSize.touch, height: ModeSize.touch)
                        .background(ModeColor.card, in: .circle)
                        .modeShadow(in: Circle())
                }
                .accessibilityLabel(Text(L10n.Tasks.newTask))
            }
            .sharedBackgroundVisibility(.hidden)
        }
    }

    @ViewBuilder
    private var now: some View {
        if state.nowIsEmpty {
            ModeEmptyState(icon: .tasks, title: L10n.Tasks.emptyTitle, message: L10n.Tasks.emptyMessage).frame(maxWidth: .infinity)
        }
        if let running = state.running {
            ModeSectionHeader(L10n.Tasks.running)
            ModeRunningCard(title: running.title, status: running.subtitle, since: running.since, paused: running.paused, progress: progress) {
                onEvent(.openLive)
            }
        }
        if let waiting = state.waiting {
            ModeSectionHeader(L10n.Tasks.waitingForYou)
            ModeWaitingCard(title: waiting.title, subtitle: waiting.subtitle, actionTitle: L10n.Tasks.open) { onEvent(.openLive) }
        }
        if !state.upNext.isEmpty {
            ModeSectionHeader(L10n.Tasks.upNext)
            ModeCard {
                ForEach(state.upNext) { row in
                    ModeRow(row.title, subtitle: row.subtitle, leading: row.leading, action: nil) {
                        Button { onEvent(.cancel(row.id)) } label: {
                            ModeIcon.close.image
                                .font(ModeIconFont.small.weight(.semibold))
                                .foregroundStyle(ModeColor.textSecondary)
                                .frame(width: ModeSize.touch, height: ModeSize.touch)
                                .contentShape(.rect)
                        }
                        .buttonStyle(.plain)
                        .padding(.trailing, -ModeSpacing.s - ModeSpacing.xxs)
                        .accessibilityLabel(Text(L10n.Tasks.cancel(row.title)))
                    }
                    .frame(minHeight: ModeSize.taskRow)
                }
            }
        }
        if !state.repeats.isEmpty {
            ModeSectionHeader(L10n.Tasks.repeats)
            ModeCard {
                ForEach(state.repeats) { row in
                    repeatRow(row) { EmptyView() }
                }
            }
        }
    }

    @ViewBuilder
    private var scheduled: some View {
        if !state.repeats.isEmpty {
            ModeCard {
                ForEach(state.repeats) { row in
                    repeatRow(row) {
                        Toggle(isOn: Binding(get: { row.enabled }, set: { onEvent(.setRepeat(row.id, $0)) })) { Text(row.title) }
                            .labelsHidden()
                            .tint(ModeColor.success)
                    }
                }
            }
            .padding(.top, ModeSpacing.m)
        }
        ModeButton(L10n.Tasks.newRepeat, icon: .repeatTask, role: .secondary, fullWidth: true) { onEvent(.newRepeat) }
            .padding(.top, ModeSpacing.m)
    }

    @ViewBuilder
    private var done: some View {
        if !state.done.isEmpty {
            ModeCard {
                ForEach(state.done) { row in
                    ModeRow(row.title, subtitle: row.subtitle, leading: row.leading, onTap: { onEvent(.open(row)) })
                        .frame(minHeight: ModeSize.recentRow)
                }
            }
            .padding(.top, ModeSpacing.m)
        }
    }

    /// A repeat: Mo resting until it's time, the task, and its time and days after the repeat mark.
    private func repeatRow<Trailing: View>(_ row: TasksViewModel.RepeatRow, @ViewBuilder trailing: () -> Trailing) -> some View {
        HStack(spacing: ModeSpacing.m) {
            ModeMo(.sleep, size: ModeSize.moRow)
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(row.title).modeText(.callout)
                Label { Text(row.subtitle) } icon: { ModeIcon.repeatTask.image.font(ModeIconFont.chevron) }
                    .labelStyle(ModeTightLabel())
                    .modeText(.footnote, color: ModeColor.textSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            trailing()
        }
        .opacity(row.enabled ? 1 : ModeOpacity.muted * 2)
        .frame(minHeight: ModeSize.taskRow)
        .contentShape(.rect)
        .contextMenu {
            Button(role: .destructive) { onEvent(.deleteRepeat(row.id)) } label: { Label { Text(L10n.Common.delete) } icon: { ModeIcon.trash.image } }
        }
    }
}

/// An icon and its text with the board's 4-point gap.
private struct ModeTightLabel: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: ModeSpacing.xs) {
            configuration.icon
            configuration.title
        }
    }
}

/// Connects Tasks to its ViewModel, and opens the Repeat sheet.
struct TasksRoute: View {
    let app: AppModel
    let run: RunStatusViewModel
    @State private var model: TasksViewModel

    init(app: AppModel, run: RunStatusViewModel, navigate: @escaping (Route) -> Void, newTask: @escaping () -> Void) {
        self.app = app
        self.run = run
        _model = State(initialValue: TasksViewModel(screens: app.screens, navigate: navigate, newTask: newTask))
    }

    var body: some View {
        TasksScreen(state: model.state, progress: run.state.running?.progress, filter: $model.filter, onEvent: model.send)
            .sheet(isPresented: $model.showingRepeat) { RepeatRoute(app: app, text: "") }
    }
}
