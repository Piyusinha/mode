import ModeDesignSystem
import SwiftUI

/// Tasks, as on its board. Draws state, sends events.
struct TasksScreen: View {
    let state: TasksViewModel.State
    @Binding var filter: TasksViewModel.Filter
    let onEvent: (TasksViewModel.Event) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.m) {
                ModeSegmented([(.now, L10n.Tasks.now), (.scheduled, L10n.Tasks.scheduled), (.done, L10n.Tasks.done)], selection: $filter)
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
        .navigationTitle(Text(L10n.Tab.tasks))
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button { onEvent(.newTask) } label: { Label { Text(L10n.Tasks.newTask) } icon: { ModeIcon.ask.image } }
                    Button { onEvent(.newRepeat) } label: { Label { Text(L10n.Tasks.newRepeat) } icon: { ModeIcon.repeatTask.image } }
                } label: { ModeIcon.add.image }
                .accessibilityLabel(Text(L10n.Tasks.newTask))
            }
        }
    }

    @ViewBuilder
    private var now: some View {
        if state.nowIsEmpty {
            ModeEmptyState(icon: .tasks, title: L10n.Tasks.emptyTitle, message: L10n.Tasks.emptyMessage).frame(maxWidth: .infinity)
        }
        if let running = state.running {
            ModeSectionHeader(L10n.Tasks.running)
            ModeCard {
                ModeRow(running.title, subtitle: running.subtitle, leading: running.leading, emphasis: .prominent, onTap: { onEvent(.openLive) })
            }
        }
        if let waiting = state.waiting {
            ModeSectionHeader(L10n.Tasks.waitingForYou)
            ModeCard {
                ModeRow(waiting.title, subtitle: waiting.subtitle, leading: waiting.leading, emphasis: .prominent, action: nil) {
                    ModeStatusView(.prominent(L10n.Tasks.open)) { onEvent(.openLive) }
                }
            }
        }
        if !state.upNext.isEmpty {
            ModeSectionHeader(L10n.Tasks.upNext)
            ModeCard {
                ForEach(state.upNext) { row in
                    ModeRow(row.title, subtitle: row.subtitle, leading: row.leading, action: nil) {
                        Button { onEvent(.cancel(row.id)) } label: {
                            ModeIcon.close.image.foregroundStyle(ModeColor.textSecondary).frame(width: ModeSize.touch, height: ModeSize.touch)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(Text(L10n.Tasks.cancel(row.title)))
                    }
                }
            }
        }
        if !state.repeats.isEmpty {
            ModeSectionHeader(L10n.Tasks.repeats)
            repeatsCard
        }
    }

    @ViewBuilder
    private var scheduled: some View {
        if !state.repeats.isEmpty { repeatsCard }
        ModeButton(L10n.Tasks.newRepeat, icon: .repeatTask, role: .secondary, fullWidth: true) { onEvent(.newRepeat) }
    }

    @ViewBuilder
    private var done: some View {
        ModeCard {
            ForEach(state.done) { row in
                ModeRow(row.title, subtitle: row.subtitle, leading: row.leading, onTap: { onEvent(.open(row)) })
            }
        }
    }

    private var repeatsCard: some View {
        ModeCard {
            ForEach(state.repeats) { row in
                ModeRow(row.title, subtitle: row.subtitle, leading: .icon(.repeatTask), action: nil) {
                    Toggle(isOn: Binding(get: { row.enabled }, set: { onEvent(.setRepeat(row.id, $0)) })) { Text(row.title) }
                        .labelsHidden()
                        .tint(ModeColor.success)
                }
                .contextMenu {
                    Button(role: .destructive) { onEvent(.deleteRepeat(row.id)) } label: { Label { Text(L10n.Common.delete) } icon: { ModeIcon.trash.image } }
                }
            }
        }
    }
}

/// Connects Tasks to its ViewModel, and opens the Repeat sheet.
struct TasksRoute: View {
    let app: AppModel
    @State private var model: TasksViewModel

    init(app: AppModel, navigate: @escaping (Route) -> Void, newTask: @escaping () -> Void) {
        self.app = app
        _model = State(initialValue: TasksViewModel(screens: app.screens, navigate: navigate, newTask: newTask))
    }

    var body: some View {
        TasksScreen(state: model.state, filter: $model.filter, onEvent: model.send)
            .sheet(isPresented: $model.showingRepeat) { RepeatRoute(app: app, text: "") }
    }
}
