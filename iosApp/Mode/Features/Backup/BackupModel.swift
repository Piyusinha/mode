import Foundation
import ModeDesignSystem
import ModeKit
import Observation
import SwiftUI
import UniformTypeIdentifiers

/// Settings → Backup and restore (board Backup): maps the presenter's state to [BackupState], keeps the password
/// fields, shows the share sheet for a made backup and the Files picker for one to restore.
@MainActor
@Observable
final class BackupModel {
    typealias State = BackupState
    typealias Event = BackupEvent

    private(set) var state = State()
    /// The password fields, typed here and sent to the presenter as they change.
    var password = "" { didSet { if password != oldValue { presenter.setPassword(text: password) } } }
    var passwordAgain = "" { didSet { if passwordAgain != oldValue { presenter.setPasswordAgain(text: passwordAgain) } } }
    var restorePassword = "" {
        didSet {
            guard restorePassword != oldValue else { return }
            presenter.setRestorePassword(text: restorePassword)
            // Typing again takes the red ring away.
            if presenter.state.value.problem == .wrongPassword { presenter.dismissProblem() }
        }
    }
    /// The made backup, while the share sheet is up.
    var sharing: SharedFile?
    /// The Files picker, to choose a backup to restore.
    var choosingFile = false
    private var sharedCompleted = false
    /// The file picked in Files isn't a `.modebackup` (Mode can't declare the type yet, so the picker shows every file).
    private var pickedWrongFile = false
    private var stage: BackupStage?
    private let presenter: BackupPresenter
    private var updates: Updates?

    init(screens: Screens) {
        presenter = screens.backup()
        updates = observe(presenter.state) { [weak self] s in self?.apply(s) }
    }

    func send(_ event: Event) {
        switch event {
        case let .includeSignIns(on): presenter.setIncludeSignIns(on: on)
        case .make: presenter.make()
        case .chooseFile:
            clearWrongFile()
            choosingFile = true
        case .restore: presenter.restore()
        case .cancelRestore: presenter.cancelRestore()
        case .dismissProblem:
            clearWrongFile()
            presenter.dismissProblem()
        case .done: presenter.done()
        }
    }

    /// A file chosen in Files: copied into the temporary folder, then opened.
    func picked(_ result: Result<URL, Error>) {
        guard case let .success(url) = result else { return }
        guard let path = BackupFile.copyToTemporary(url) else {
            pickedWrongFile = true
            state.problem = L10n.Backup.notABackup
            return
        }
        clearWrongFile()
        presenter.open(path: path)
    }

    /// The share sheet ended: saved or sent, or closed.
    func shareFinished(_ completed: Bool) { sharedCompleted = sharedCompleted || completed }

    func shareDismissed() {
        sharing = nil
        presenter.shared(completed: sharedCompleted)
        sharedCompleted = false
    }

    private func clearWrongFile() {
        guard pickedWrongFile else { return }
        pickedWrongFile = false
        state.problem = nil
    }

    private func apply(_ s: BackupUiState) {
        var mapped = Self.map(s)
        if pickedWrongFile && mapped.problem == nil { mapped.problem = L10n.Backup.notABackup }
        state = mapped
        // A new stage after a backup or a restore clears the fields in the presenter: follow it.
        if s.stage != stage {
            stage = s.stage
            if s.password.isEmpty { password = "" }
            if s.passwordAgain.isEmpty { passwordAgain = "" }
            if s.restorePassword.isEmpty { restorePassword = "" }
        }
        if s.stage == .ready, let path = s.readyPath, sharing == nil {
            sharedCompleted = false
            sharing = SharedFile(url: URL(fileURLWithPath: path))
        }
    }

    isolated deinit {
        updates?.cancel()
        presenter.close()
    }

    // MARK: Words

    private static func map(_ s: BackupUiState) -> State {
        var state = State()
        switch s.stage {
        case .overview, .making, .ready: state.page = .overview
        case .preview, .restoring: state.page = .preview
        case .restored: state.page = .restored
        }
        state.contents = contents(s.counts)
        state.signInsDetail = s.signInNames.isEmpty ? nil : L10n.Backup.signInsDetail(names(s.signInNames))
        state.includeSignIns = s.includeSignIns
        state.passwordsMatch = s.passwordsMatch && !s.passwordAgain.isEmpty
        state.canMake = s.canMake
        state.making = s.stage == .making
        state.saveNote = s.lastBackupAtMs.map { L10n.Backup.saveNote(lastBackup: lastBackup($0.int64Value)) } ?? L10n.Backup.saveNote

        let size = ByteCountFormatter.string(fromByteCount: s.backupBytes, countStyle: .file)
        state.fileDetail = L10n.Backup.fileDetail(s.backupAtMs.map { dateAndTime($0.int64Value) } ?? "", device: s.backupDevice ?? "", size: size)
        state.inBackup = contents(s.backupCounts)
            + (s.backupSignInNames.isEmpty ? [] : [tile("signIns", L10n.Backup.signIns, names(s.backupSignInNames), .signIns, .orange)])
        state.locked = s.locked
        state.wrongPassword = s.problem == .wrongPassword
        state.canRestore = !state.wrongPassword && (!s.locked || !s.restorePassword.isEmpty)
        state.restoring = s.stage == .restoring

        state.restoredLine = s.backupAtMs.map { L10n.Backup.restoredLine(day: backupDay($0.int64Value)) } ?? L10n.Backup.restoredLinePlain
        state.added = [
            added("chats", L10n.Backup.chats, s.added.chats),
            added("memory", L10n.Backup.memory, s.added.memories),
            added("tasks", L10n.Backup.tasks, s.added.tasks),
            added("repeats", L10n.Backup.repeats, s.added.repeats),
        ] + (s.restoredSignIns.isEmpty ? [] : [ModeBackupItem(id: "signIns", title: L10n.Backup.signIns, value: names(s.restoredSignIns), mark: .added)])
        state.skippedNote = skipped(s.skipped)

        state.problem = switch s.problem {
        case .notAbackup: L10n.Backup.notABackup
        case .newerVersion: L10n.Backup.newerVersion
        case .unreadable: L10n.Backup.unreadable
        case .couldNotSave: L10n.Backup.couldNotSave
        case .wrongPassword, nil: nil
        }
        return state
    }

    private static func contents(_ c: BackupCounts) -> [ModeBackupItem] {
        [
            tile("chats", L10n.Backup.chats, L10n.Backup.chatsCount(Int(c.chats), messages: Int(c.messages)), .chats, .coral),
            tile("memory", L10n.Backup.memory, count(c.memories), .memory, .lilac),
            tile("tasks", L10n.Backup.tasks, count(c.tasks), .tasks, .blue),
            tile("repeats", L10n.Backup.repeats, count(c.repeats), .repeats, .green),
            tile("settings", L10n.Backup.settings, L10n.Backup.all, .settings, .gray),
        ]
    }

    private static func tile(_ id: String, _ title: String, _ value: String, _ icon: ModeBackupIcon, _ tone: ModeBackupTone) -> ModeBackupItem {
        ModeBackupItem(id: id, title: title, value: value, mark: .tile(icon, tone))
    }

    private static func added(_ id: String, _ title: String, _ n: Int32) -> ModeBackupItem {
        ModeBackupItem(id: id, title: title, value: L10n.Backup.added(Int(n)), mark: .added)
    }

    private static func count(_ n: Int32) -> String { Int(n).formatted() }

    /// "ChatGPT, OpenRouter, ElevenLabs".
    private static func names(_ list: [String]) -> String { list.joined(separator: ", ") }

    /// "4 chats and 3 memories were already on this iPhone, …"; nil when nothing was skipped.
    private static func skipped(_ c: BackupCounts) -> String? {
        let parts = [
            c.chats > 0 ? L10n.Backup.skippedChats(Int(c.chats)) : nil,
            c.memories > 0 ? L10n.Backup.skippedMemories(Int(c.memories)) : nil,
            c.tasks > 0 ? L10n.Backup.skippedTasks(Int(c.tasks)) : nil,
            c.repeats > 0 ? L10n.Backup.skippedRepeats(Int(c.repeats)) : nil,
        ].compactMap { $0 }
        guard !parts.isEmpty else { return nil }
        let one = parts.count == 1 && c.chats + c.memories + c.tasks + c.repeats == 1
        return L10n.Backup.skipped(parts.formatted(.list(type: .and)), one: one)
    }

    private static func date(_ ms: Int64) -> Date { Date(timeIntervalSince1970: TimeInterval(ms) / 1000) }

    /// "Thu 6 Nov, 16:20".
    private static func dateAndTime(_ ms: Int64) -> String {
        let d = date(ms)
        return L10n.Backup.dayAndTime(d.formatted(.dateTime.weekday(.abbreviated).day().month(.abbreviated)),
                                      d.formatted(date: .omitted, time: .shortened))
    }

    /// "yesterday, 21:40", "today, 9:05", or "Thu 6 Nov, 16:20".
    private static func lastBackup(_ ms: Int64) -> String {
        let d = date(ms)
        let time = d.formatted(date: .omitted, time: .shortened)
        if Calendar.current.isDateInToday(d) { return L10n.Backup.dayAndTime(L10n.Backup.today, time) }
        if Calendar.current.isDateInYesterday(d) { return L10n.Backup.dayAndTime(L10n.Backup.yesterday, time) }
        return dateAndTime(ms)
    }

    /// The backup's day as people say it, for "from Thursday’s backup": today, yesterday, a weekday this week, or a date.
    private static func backupDay(_ ms: Int64) -> String {
        let d = date(ms)
        let calendar = Calendar.current
        if calendar.isDateInToday(d) { return L10n.Backup.today }
        if calendar.isDateInYesterday(d) { return L10n.Backup.yesterday }
        let days = calendar.dateComponents([.day], from: calendar.startOfDay(for: d), to: calendar.startOfDay(for: .now)).day ?? .max
        return days < 7 ? d.formatted(.dateTime.weekday(.wide)) : d.formatted(.dateTime.day().month(.wide))
    }
}

struct BackupRoute: View {
    @State private var model: BackupModel

    init(app: AppModel) {
        _model = State(initialValue: BackupModel(screens: app.screens))
    }

    var body: some View {
        BackupScreen(state: model.state, password: $model.password, passwordAgain: $model.passwordAgain,
                     restorePassword: $model.restorePassword, onEvent: model.send)
            .sheet(item: $model.sharing, onDismiss: model.shareDismissed) { file in
                FileShareSheet(url: file.url, onFinish: model.shareFinished)
            }
            // `.modebackup` isn't declared as a type yet (it needs Info.plist keys), so any file can be picked
            // and BackupFile checks the extension.
            .fileImporter(isPresented: $model.choosingFile, allowedContentTypes: [BackupFile.type]) { model.picked($0) }
    }
}
