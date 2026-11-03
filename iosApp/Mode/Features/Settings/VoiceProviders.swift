import ModeDesignSystem
import SwiftUI

/// Setup presents the actual Settings routes, including their provider pages and validated key sheets.
struct VoiceSettingsSheet: View {
    let app: AppModel
    let hearing: Bool
    @State private var path: [Route] = []
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack(path: $path) {
            VoiceRoute(app: app, hearing: hearing, navigate: { path.append($0) })
                .navigationDestination(for: Route.self) { route in
                    if case let .voicePicker(provider) = route { VoicePickerRoute(app: app, provider: provider) }
                }
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        ModeToolbarButton(.done, label: L10n.Common.done) { dismiss() }
                    }
                }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }
}

/// The same searchable, untruncated voice catalog used by Settings, presented as a bottom sheet.
struct AllVoicesSheet: View {
    let app: AppModel
    let provider: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VoicePickerRoute(app: app, provider: provider)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        ModeToolbarButton(.close, label: L10n.Voice.close) { dismiss() }
                    }
                }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }
}

/// A recommendation opens the same key check/save UI used when connecting from Settings.
struct ConnectVoiceSheet: View {
    let app: AppModel
    let service: VoiceService
    @State private var model: VoiceListModel

    init(app: AppModel, service: VoiceService) {
        self.app = app
        self.service = service
        _model = State(initialValue: VoiceListModel(screens: app.screens, hearing: service.hearing, navigate: { _ in }))
    }

    var body: some View {
        Group {
            if let row = model.state.rows.first(where: { $0.service == service }) {
                if row.connected {
                    AllVoicesSheet(app: app, provider: service.id)
                } else {
                    VoiceKeySheet(service: service, masked: row.masked,
                                  check: model.state.keyCheck?.id == service.id ? model.state.keyCheck : nil) {
                        model.send(.checkKey(service, $0))
                    }
                }
            } else {
                ProgressView().tint(ModeColor.tint)
            }
        }
        .onDisappear { model.send(.keySheetClosed); model.stop() }
    }
}
