import ModeDesignSystem
import SwiftUI

/// Tasks, as on its board: Scheduled and Done. Draws state, sends events.
struct TasksScreen: View {
    let state: TasksViewModel.State
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
                ModePillSegments([(.scheduled, L10n.Tasks.scheduled), (.done, L10n.Tasks.done)], selection: $filter)
                    .padding(.bottom, ModeSpacing.xs)
                switch filter {
                case .scheduled: scheduled
                case .done: done
                }
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSize.tabBarClearance)
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
        TasksScreen(state: model.state, filter: $model.filter, onEvent: model.send)
    }
}
