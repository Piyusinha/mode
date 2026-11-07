import Foundation
import UniformTypeIdentifiers
import ModeDesignSystem
import SwiftUI

/// Whether Backup and restore is wired to its logic: Settings shows its row only then.
enum BackupFeature {
    static let ready = true
}

/// What Backup and restore shows (board Backup): its view model maps the presenter's state to this, so the
/// screen only draws it. `BackupModel.State` once the model is wired.
struct BackupState: Equatable {
    enum Page: Equatable {
        /// Backup and restore: what's in a backup, the sign-ins switch (and the password card), Make a backup, Restore.
        case overview
        /// Restore: the backup picked, what's in it, its password when it's locked.
        case preview
        /// Restored: what was added.
        case restored
    }

    var page: Page = .overview
    /// What's in it: Chats, What Mode knows, Tasks and history, Repeats, Settings and voice.
    var contents: [ModeBackupItem] = []
    /// The providers under the sign-ins switch and that it's locked ("ChatGPT, OpenRouter, ElevenLabs. Locked …");
    /// nil when nothing is signed in (no switch).
    var signInsDetail: String?
    var includeSignIns = false
    var passwordsMatch = false
    var canMake = true
    var making = false
    /// "Save it to Files or iCloud Drive, or AirDrop it to your Mac. Last backup: yesterday, 21:40."
    var saveNote = ""

    /// "Thu 6 Nov, 16:20 · Alex’s iPhone · 2.4 MB".
    var fileDetail = ""
    var inBackup: [ModeBackupItem] = []
    var locked = false
    var wrongPassword = false
    var canRestore = true
    var restoring = false

    /// "Your chats, memory, tasks and settings from Thursday’s backup are in Mode."
    var restoredLine = ""
    var added: [ModeBackupItem] = []
    /// "4 chats and 3 memories were already on this iPhone, so they were left as they are."
    var skippedNote: String?

    /// Something went wrong (not a backup, from a newer Mode, unreadable, couldn't save), in a sentence.
    var problem: String?
}

enum BackupEvent {
    case includeSignIns(Bool)
    case make
    case chooseFile
    case restore
    case cancelRestore
    case dismissProblem
    case done
}

/// Backup and restore, Restore and Restored, as on the Backup board. Draws [BackupState], sends [BackupEvent].
struct BackupScreen: View {
    let state: BackupState
    @Binding var password: String
    @Binding var passwordAgain: String
    @Binding var restorePassword: String
    let onEvent: (BackupEvent) -> Void

    private var title: LocalizedStringResource {
        switch state.page {
        case .overview: L10n.Backup.title
        case .preview: L10n.Backup.restoreTitle
        case .restored: L10n.Backup.restoredTitle
        }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.s) {
                ModePageTitle(title).padding(.bottom, ModeSpacing.xs)
                if let problem = state.problem {
                    ModeVoiceNotice(problem, actionTitle: L10n.Backup.ok) { onEvent(.dismissProblem) }
                }
                switch state.page {
                case .overview: overview
                case .preview: preview
                case .restored: restored
                }
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.xxl)
            .animation(ModeMotion.standard, value: state.includeSignIns)
        }
        .scrollEdgeEffectStyle(.soft, for: .all)
        .scrollDismissesKeyboard(.interactively)
        .modeScreen()
        .modePageChrome(title)
        .navigationBarBackButtonHidden(state.page != .overview)
        .toolbar {
            if state.page != .overview {
                ToolbarItem(placement: .topBarLeading) {
                    ModeIconButton(.back, label: L10n.Common.back) { onEvent(state.page == .preview ? .cancelRestore : .done) }
                }
                .sharedBackgroundVisibility(.hidden)
            }
        }
    }

    @ViewBuilder private var overview: some View {
        Text(L10n.Backup.intro).modeText(.subheadline, color: ModeColor.textSecondary)
            .padding(.horizontal, ModeSpacing.noteInset)
        ModeSectionHeader(L10n.Backup.whatsInIt)
        ModeBackupList(state.contents) {
            if let detail = state.signInsDetail {
                ModeBackupSwitchRow(L10n.Backup.includeSignIns, detail: detail,
                                    isOn: Binding(get: { state.includeSignIns }, set: { onEvent(.includeSignIns($0)) }))
            }
        }
        if state.includeSignIns {
            ModeBackupPasswordCard(password: L10n.Backup.password, again: L10n.Backup.again, text: $password, againText: $passwordAgain,
                                   matches: state.passwordsMatch, matchLabel: L10n.Backup.passwordsMatch, warning: L10n.Backup.lockedWarning)
                .transition(.opacity)
        }
        ModeBackupButton(state.making ? L10n.Backup.making : L10n.Backup.make, upload: true, busy: state.making) { onEvent(.make) }
            .disabled(!state.canMake || state.making)
            .padding(.top, ModeSpacing.s)
        ModeFootnote(verbatim: state.saveNote, centered: true)
        ModeSectionHeader(L10n.Backup.restoreSection)
        ModeBackupLinkRow(L10n.Backup.restoreFrom, icon: .restore, tone: .blue) { onEvent(.chooseFile) }
        ModeFootnote(L10n.Backup.restoreNote)
    }

    @ViewBuilder private var preview: some View {
        ModeBackupFileCard(L10n.Backup.file, detail: state.fileDetail)
        ModeSectionHeader(L10n.Backup.inThisBackup).padding(.top, -ModeSpacing.xs)
        ModeBackupList(state.inBackup, compact: true)
        if state.locked {
            ModeBackupLockField(L10n.Backup.backupPassword, text: $restorePassword,
                                note: state.wrongPassword ? L10n.Backup.wrongPassword : L10n.Backup.lockedNote, wrong: state.wrongPassword)
        }
        ModeBackupButton(state.restoring ? L10n.Backup.restoring : L10n.Backup.restoreInto, busy: state.restoring) { onEvent(.restore) }
            .disabled(!state.canRestore || state.restoring)
            .padding(.top, ModeSpacing.s)
        ModeFootnote(L10n.Backup.mergeNote, centered: true)
    }

    @ViewBuilder private var restored: some View {
        ModeBackupDone(state.restoredLine)
        ModeBackupList(state.added, compact: true)
        if let skipped = state.skippedNote { ModeFootnote(verbatim: skipped) }
        ModeBackupButton(L10n.Common.done) { onEvent(.done) }
            .padding(.top, ModeSpacing.s)
    }
}

/// A backup picked in Files: copied out of its security-scoped place into this app's temporary folder, so the
/// logic can read it at a plain path.
enum BackupFile {
    static let fileExtension = "modebackup"
    /// Its own type (declared in project.yml), so the Files picker shows only Mode backups.
    static let type = UTType(filenameExtension: fileExtension, conformingTo: .data) ?? .data

    /// The copy's path, or nil when it isn't a `.modebackup` file or couldn't be copied.
    static func copyToTemporary(_ url: URL) -> String? {
        guard url.pathExtension.lowercased() == fileExtension else { return nil }
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        let target = FileManager.default.temporaryDirectory.appendingPathComponent(url.lastPathComponent)
        try? FileManager.default.removeItem(at: target)
        do {
            try FileManager.default.copyItem(at: url, to: target)
            return target.path
        } catch {
            return nil
        }
    }
}
