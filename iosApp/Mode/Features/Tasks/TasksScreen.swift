import ModeDesignSystem
import SwiftUI

/// Tasks: running, up next, repeats and done. Draws state, sends events.
struct TasksScreen: View {
    let state: TasksViewModel.State
    let onEvent: (TasksViewModel.Event) -> Void

    var body: some View {
        List {
            if state.isEmpty {
                ModeEmptyState(icon: .tasks, title: L10n.Tasks.emptyTitle, message: L10n.Tasks.emptyMessage)
                    .frame(maxWidth: .infinity)
                    .listRowBackground(Color.clear) // literal-ok: no background
            }
            if let running = state.running {
                Section { runningRow(running) } header: { ModeSectionHeader(L10n.Tasks.now) }
            }
            if !state.upNext.isEmpty {
                Section {
                    ForEach(state.upNext) { item in
                        ModeRow(item.title, subtitle: item.subtitle, leading: .icon(.clock)) {
                            ModeIconButton(.close, label: LocalizedStringResource(stringLiteral: L10n.Tasks.cancel(item.title))) { onEvent(.cancel(item.id)) }
                        }
                    }
                } header: { ModeSectionHeader(L10n.Tasks.upNext) }
            }
            if !state.repeats.isEmpty {
                Section {
                    ForEach(state.repeats) { item in
                        ModeRow(item.title, subtitle: item.subtitle, leading: .icon(.repeatTask)) {
                            Toggle(isOn: Binding(get: { item.enabled }, set: { onEvent(.setRepeat(item.id, $0)) })) { EmptyView() }
                                .labelsHidden()
                                .tint(ModeColor.success)
                        }
                        .swipeActions { Button(role: .destructive) { onEvent(.deleteRepeat(item.id)) } label: { Text(L10n.Common.delete) } }
                    }
                } header: { ModeSectionHeader(L10n.Tasks.repeats) }
            }
            if !state.done.isEmpty {
                Section {
                    ForEach(state.done) { item in
                        ModeRow(item.title, subtitle: item.subtitle, leading: .icon(item.icon)) { onEvent(.open(item)) }
                    }
                } header: { ModeSectionHeader(L10n.Tasks.done) }
            }
        }
        .listStyle(.insetGrouped)
        .modeScreen()
        .navigationTitle(Text(L10n.Tab.tasks))
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                ModeIconButton(.repeatTask, label: L10n.Tasks.newRepeat) { onEvent(.newRepeat) }
            }
        }
    }

    private func runningRow(_ running: TasksViewModel.Running) -> some View {
        ModeRow(running.title, subtitle: running.waiting ? String(localized: L10n.Tasks.waitingForYou) : running.status,
                leading: .icon(running.waiting ? .waiting : .running, running.waiting ? ModeColor.warning : ModeColor.tint),
                action: { onEvent(.openLive) }) {
            ModeIconButton(.stop, label: L10n.Common.stop) { onEvent(.stop) }
        }
    }
}

/// Connects Tasks to its ViewModel, and opens the Repeat sheet.
struct TasksRoute: View {
    let app: AppModel
    @State private var model: TasksViewModel

    init(app: AppModel, navigate: @escaping (Route) -> Void) {
        self.app = app
        _model = State(initialValue: TasksViewModel(screens: app.screens, navigate: navigate))
    }

    var body: some View {
        TasksScreen(state: model.state, onEvent: model.send)
            .sheet(isPresented: $model.showingRepeat) { RepeatRoute(app: app, text: "") }
    }
}
