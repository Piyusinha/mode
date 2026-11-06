package com.piyush.mode.app.screens.settings

import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.app.screens.common.ScreenClock
import com.piyush.mode.feature.backup.data.BackupService
import com.piyush.mode.feature.backup.domain.BackupException
import com.piyush.mode.feature.backup.domain.BackupFailure
import com.piyush.mode.feature.backup.domain.ItemCounts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where Settings › Backup and restore is. */
public enum class BackupStage { Overview, Making, Ready, Preview, Restoring, Restored }

public data class BackupCounts(val chats: Int = 0, val messages: Int = 0, val memories: Int = 0, val tasks: Int = 0, val repeats: Int = 0)

public enum class BackupProblem { NotABackup, NewerVersion, WrongPassword, Unreadable, CouldNotSave }

public data class BackupUiState(
    val stage: BackupStage = BackupStage.Overview,
    /** What a backup made now would hold. */
    val counts: BackupCounts = BackupCounts(),
    /** Connected AI and voice providers, display names ("ChatGPT", "OpenRouter", "ElevenLabs"). */
    val signInNames: List<String> = emptyList(),
    val includeSignIns: Boolean = false,
    val password: String = "",
    val passwordAgain: String = "",
    /** Both typed, equal, and at least 8 characters. */
    val passwordsMatch: Boolean = false,
    /** Make a backup is allowed (sign-ins off, or a matching password). */
    val canMake: Boolean = true,
    /** When the last backup was saved (shared), epoch ms; null: never. */
    val lastBackupAtMs: Long? = null,
    /** Ready: the file to hand to the share sheet. */
    val readyPath: String? = null,
    val readyName: String? = null,
    /** Preview / Restored: the picked backup. */
    val backupAtMs: Long? = null,
    val backupDevice: String? = null,
    val backupBytes: Long = 0,
    val backupCounts: BackupCounts = BackupCounts(),
    val backupSignInNames: List<String> = emptyList(),
    /** The picked backup is locked: restore needs its password. */
    val locked: Boolean = false,
    val restorePassword: String = "",
    /** Restored: what was added, and what was already here. */
    val added: BackupCounts = BackupCounts(),
    val skipped: BackupCounts = BackupCounts(),
    val restoredSignIns: List<String> = emptyList(),
    val problem: BackupProblem? = null,
)

/**
 * Settings › Backup and restore. Making one: what it holds (live), the sign-ins switch and its password,
 * then the file for the share sheet. Restoring one: a file picked in Files (Swift copies it to a temporary
 * path first; that copy is deleted when the restore is done or cancelled), its preview, its password if
 * locked, then what was added and what was already here.
 */
public class BackupPresenter(
    parent: CoroutineScope,
    private val backup: BackupService,
    private val clock: ScreenClock = ScreenClock(),
) : Presenter<BackupUiState>(parent, BackupUiState()) {
    /** The picked backup's temporary copy while it is previewed or restored. */
    private var picked: String? = null

    init {
        update { it.copy(signInNames = backup.signInNames(), lastBackupAtMs = backup.lastBackupAtMs) }
        scope.launch { backup.counts().collect { c -> update { it.copy(counts = c.shown()) } } }
    }

    public fun setIncludeSignIns(on: Boolean) {
        update { it.copy(includeSignIns = on) }
    }

    public fun setPassword(text: String) {
        update { it.copy(password = text) }
    }

    public fun setPasswordAgain(text: String) {
        update { it.copy(passwordAgain = text) }
    }

    /** Overview → Making → Ready, or back to Overview with CouldNotSave. */
    public fun make() {
        val s = state.value
        if (s.stage != BackupStage.Overview || !s.canMake) return
        update { it.copy(stage = BackupStage.Making, problem = null) }
        scope.launch {
            try {
                val file = backup.make(s.includeSignIns, s.password.takeIf { s.includeSignIns })
                update { it.copy(stage = BackupStage.Ready, readyPath = file.path, readyName = file.name) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                update { it.copy(stage = BackupStage.Overview, problem = BackupProblem.CouldNotSave) }
            }
        }
    }

    /** The share sheet closed; [completed]: the backup was saved or sent. The temporary file goes either way. */
    public fun shared(completed: Boolean) {
        val s = state.value
        if (s.stage != BackupStage.Ready) return
        s.readyPath?.let(backup::discard)
        if (completed) backup.markSaved(clock.now())
        update {
            it.copy(
                stage = BackupStage.Overview, readyPath = null, readyName = null, password = "", passwordAgain = "",
                lastBackupAtMs = if (completed) backup.lastBackupAtMs else it.lastBackupAtMs,
            )
        }
    }

    /** A file picked in Files, copied to [path]: its preview, or NotABackup / NewerVersion. */
    public fun open(path: String) {
        val stage = state.value.stage
        if (stage != BackupStage.Overview && stage != BackupStage.Preview) return
        scope.launch {
            val header = withContext(Dispatchers.Default) { backup.peek(path) }
            val problem = when {
                header == null -> BackupProblem.NotABackup
                header.newer -> BackupProblem.NewerVersion
                else -> null
            }
            if (header == null || problem != null) {
                backup.discard(path)
                update { it.copy(problem = problem) }
                return@launch
            }
            picked?.takeIf { it != path }?.let(backup::discard)
            picked = path
            update {
                it.copy(
                    stage = BackupStage.Preview,
                    backupAtMs = header.createdAtMs,
                    backupDevice = header.deviceName.ifBlank { null },
                    backupBytes = header.fileBytes,
                    backupCounts = header.counts.shown(),
                    backupSignInNames = header.signInNames,
                    locked = header.locked,
                    restorePassword = "",
                    problem = null,
                )
            }
        }
    }

    public fun setRestorePassword(text: String) {
        update { it.copy(restorePassword = text, problem = it.problem.takeUnless { p -> p == BackupProblem.WrongPassword }) }
    }

    /** Preview → Restoring → Restored, or back to Preview with WrongPassword / Unreadable. */
    public fun restore() {
        val s = state.value
        val path = picked
        if (s.stage != BackupStage.Preview || path == null) return
        if (s.locked && s.restorePassword.isEmpty()) return
        update { it.copy(stage = BackupStage.Restoring, problem = null) }
        scope.launch {
            try {
                val report = backup.restore(path, s.restorePassword.takeIf { s.locked })
                backup.discard(path)
                picked = null
                update {
                    it.copy(
                        stage = BackupStage.Restored,
                        added = report.added.shown(),
                        skipped = report.skipped.shown(),
                        restoredSignIns = report.restoredSignIns,
                        restorePassword = "",
                        signInNames = backup.signInNames(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val problem = (e as? BackupException)?.failure?.problem() ?: BackupProblem.Unreadable
                update { it.copy(stage = BackupStage.Preview, problem = problem) }
            }
        }
    }

    /** Preview → Overview, without restoring. */
    public fun cancelRestore() {
        if (state.value.stage != BackupStage.Preview) return
        picked?.let(backup::discard)
        picked = null
        update { it.clearedBackup() }
    }

    public fun dismissProblem() {
        update { it.copy(problem = null) }
    }

    /** Restored → Overview. */
    public fun done() {
        if (state.value.stage != BackupStage.Restored) return
        update { it.clearedBackup().copy(signInNames = backup.signInNames(), lastBackupAtMs = backup.lastBackupAtMs) }
    }

    private fun update(change: (BackupUiState) -> BackupUiState) {
        val next = change(mutableState.value)
        val match = next.password.length >= MIN_PASSWORD && next.password == next.passwordAgain
        mutableState.value = next.copy(passwordsMatch = match, canMake = !next.includeSignIns || match)
    }

    private fun BackupUiState.clearedBackup() = copy(
        stage = BackupStage.Overview, backupAtMs = null, backupDevice = null, backupBytes = 0, backupCounts = BackupCounts(),
        backupSignInNames = emptyList(), locked = false, restorePassword = "", added = BackupCounts(), skipped = BackupCounts(),
        restoredSignIns = emptyList(), problem = null,
    )

    private fun ItemCounts.shown() = BackupCounts(chats, messages, memories, tasks, repeats)

    private fun BackupFailure.problem(): BackupProblem = when (this) {
        BackupFailure.NotABackup -> BackupProblem.NotABackup
        BackupFailure.NewerVersion -> BackupProblem.NewerVersion
        BackupFailure.WrongPassword -> BackupProblem.WrongPassword
        BackupFailure.Unreadable -> BackupProblem.Unreadable
        BackupFailure.CouldNotSave -> BackupProblem.CouldNotSave
    }

    private companion object {
        const val MIN_PASSWORD = 8
    }
}
