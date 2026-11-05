import ModeDesignSystem
import SwiftUI

/// Tasks, as on its board. Draws state, sends events.
struct TasksScreen: View {
    let state: TasksViewModel.State
    /// The running task's plan, as bars on its card.
    let progress: (done: Int, total: Int)?
    @Binding var filter: TasksViewModel.Filter
    let onEvent: (TasksViewModel.Event) -> Void
    /// A scheduled task whose options (pause, delete) are open.
    @State private var managing: TasksViewModel.ScheduleRow?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.s) {
                Text(L10n.Tab.tasks).modeText(.largeTitle).accessibilityAddTraits(.isHeader)
                    .padding(.leading, ModeSpacing.xs)
                    .padding(.bottom, ModeSpacing.s - ModeSpacing.xxs)
                ModePillSegments([(.now, L10n.Tasks.now), (.scheduled, L10n.Tasks.scheduled), (.done, L10n.Tasks.done)], selection: $filter)
                    .padding(.bottom, ModeSpacing.xs)
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
        .confirmationDialog(managing?.title ?? "", isPresented: Binding(get: { managing != nil }, set: { if !$0 { managing = nil } }),
                            titleVisibility: .visible, presenting: managing) { row in
            Button(String(localized: row.enabled ? L10n.Tasks.pause : L10n.Tasks.resume)) { onEvent(.setRepeat(row.id, !row.enabled)) }
            Button(String(localized: L10n.Common.delete), role: .destructive) { onEvent(.deleteRepeat(row.id)) }
        }
    }

    @ViewBuilder
    private var now: some View {
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
                    .frame(minHeight: ModeSize.tasksRow)
                }
            }
        }
        if state.nowIsEmpty { nothingYet }
    }

    /// Mo, "Nothing here yet", and three easy tasks to try.
    private var nothingYet: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.xl) {
            VStack(spacing: ModeSpacing.m) {
                ModeMo(.idle, size: ModeSize.moTasksEmpty)
                Text(L10n.Tasks.nothingYet).modeText(.title2).multilineTextAlignment(.center)
                Text(L10n.Tasks.nothingYetMessage).modeText(.callout, color: ModeColor.textSecondary).multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, ModeSpacing.l)
            VStack(alignment: .leading, spacing: ModeSpacing.s + ModeSpacing.xxs) {
                Text(L10n.Tasks.tryOne).modeText(.subheadlineEmphasis, color: ModeColor.textSecondary)
                    .padding(.leading, ModeSpacing.s)
                ForEach(state.tryThese, id: \.self) { job in
                    ModeTryRow(job) { onEvent(.tryTask(job)) }
                }
            }
        }
        .padding(.top, ModeSpacing.xxxl)
    }

    @ViewBuilder
    private var scheduled: some View {
        if state.scheduledIsEmpty {
            ModeEmptyState(icon: .clock, title: L10n.Tasks.nothingScheduled, message: L10n.Tasks.nothingScheduledMessage)
                .frame(maxWidth: .infinity)
        }
        if !state.scheduled.isEmpty {
            ModeSectionHeader(L10n.Tasks.upNext)
            scheduleCard(state.scheduled)
        }
        if !state.repeats.isEmpty {
            ModeSectionHeader(L10n.Tasks.repeats)
            scheduleCard(state.repeats)
        }
    }

    private func scheduleCard(_ rows: [TasksViewModel.ScheduleRow]) -> some View {
        ModeCard {
            ForEach(rows) { row in
                ModeRow(row.title, subtitle: row.subtitle, leading: .icon(row.icon), onTap: { managing = row })
                    .frame(minHeight: ModeSize.tasksRow)
                    .opacity(row.enabled ? 1 : ModeOpacity.muted * 2)
            }
        }
    }

    @ViewBuilder
    private var done: some View {
        if state.doneIsEmpty {
            ModeEmptyState(icon: .done, title: L10n.Tasks.nothingDone, message: L10n.Tasks.nothingDoneMessage)
                .frame(maxWidth: .infinity)
        }
        if !state.doneToday.isEmpty {
            ModeSectionHeader(L10n.Tasks.today)
            doneCard(state.doneToday)
        }
        if !state.doneYesterday.isEmpty {
            ModeSectionHeader(L10n.Tasks.yesterday)
            doneCard(state.doneYesterday)
        }
    }

    private func doneCard(_ rows: [TasksViewModel.DoneRow]) -> some View {
        ModeCard {
            ForEach(rows) { row in
                ModeRow(row.title, subtitle: row.subtitle, leading: row.leading, onTap: { onEvent(.open(row)) })
                    .frame(minHeight: ModeSize.tasksRow)
            }
        }
    }
}

/// Connects Tasks to its ViewModel.
struct TasksRoute: View {
    let app: AppModel
    let run: RunStatusViewModel
    @State private var model: TasksViewModel

    init(app: AppModel, run: RunStatusViewModel, navigate: @escaping (Route) -> Void) {
        self.app = app
        self.run = run
        _model = State(initialValue: TasksViewModel(screens: app.screens, navigate: navigate))
    }

    var body: some View {
        TasksScreen(state: model.state, progress: run.state.running?.progress, filter: $model.filter, onEvent: model.send)
    }
}
