package com.piyush.mode.app.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piyush.mode.app.R
import com.piyush.mode.app.common.Words
import com.piyush.mode.app.common.rememberWords
import com.piyush.mode.app.graph.ModeGraph
import com.piyush.mode.app.screens.settings.BackupCounts
import com.piyush.mode.app.screens.settings.BackupPresenter
import com.piyush.mode.app.screens.settings.BackupProblem
import com.piyush.mode.app.screens.settings.BackupStage
import com.piyush.mode.app.screens.settings.BackupUiState
import com.piyush.mode.app.shell.rememberPresenter
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.design.Mode
import com.piyush.mode.design.ModeButton
import com.piyush.mode.design.ModeCard
import com.piyush.mode.design.ModeGuideNote
import com.piyush.mode.design.ModeIcon
import com.piyush.mode.design.ModeMo
import com.piyush.mode.design.ModeNoteKind
import com.piyush.mode.design.ModePageBar
import com.piyush.mode.design.ModePageTitle
import com.piyush.mode.design.ModeRow
import com.piyush.mode.design.ModeRowLeading
import com.piyush.mode.design.ModeSectionHeader
import com.piyush.mode.design.ModeSize
import com.piyush.mode.design.ModeSpacing
import com.piyush.mode.design.ModeText
import com.piyush.mode.design.ModeTextField
import com.piyush.mode.design.ModeTextStyle
import com.piyush.mode.design.ModeToggleRow
import com.piyush.mode.design.ModeValueRow
import com.piyush.mode.design.MoMood
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Backup and restore: what a backup holds, sign-ins locked with a password, Make a backup (saved where the
 * user picks with Android's file picker), and Restore from a file, with a preview before anything is added.
 */
@Composable
fun BackupRoute(graph: ModeGraph, close: () -> Unit) {
    val presenter: BackupPresenter = rememberPresenter("backup") { graph.screens.backup() }
    val s by presenter.state.collectAsStateWithLifecycle()
    val w = rememberWords()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME)) { uri ->
        scope.launch {
            val done = uri != null && s.readyPath != null && withContext(Dispatchers.IO) { copyOut(context, s.readyPath!!, uri) }
            presenter.shared(done)
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val path = withContext(Dispatchers.IO) { copyIn(context, uri) }
            if (path != null) presenter.open(path)
        }
    }
    // A backup is made: ask where to save it.
    LaunchedEffect(s.stage, s.readyName) { if (s.stage == BackupStage.Ready) save.launch(s.readyName ?: DEFAULT_NAME) }
    BackHandler(enabled = s.stage == BackupStage.Preview) { presenter.cancelRestore() }

    Column(Modifier.fillMaxSize().imePadding()) {
        ModePageBar(stringResource(R.string.common_back), { if (s.stage == BackupStage.Preview) presenter.cancelRestore() else close() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ModeSpacing.screenEdge).padding(bottom = ModeSpacing.xxl), verticalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
            when (s.stage) {
                BackupStage.Preview, BackupStage.Restoring -> Restore(s, w, presenter)
                BackupStage.Restored -> Restored(s, w, presenter, close)
                else -> Overview(s, w, presenter) { pick.launch(arrayOf(MIME_ANY)) }
            }
            s.problem?.let { ModeGuideNote(problem(it), ModeNoteKind.Problem) }
        }
    }
}

@Composable
private fun Overview(s: BackupUiState, w: Words, presenter: BackupPresenter, onRestore: () -> Unit) {
    val c = Mode.colors
    ModePageTitle(stringResource(R.string.backup_title), subtitle = stringResource(R.string.backup_body))
    ModeSectionHeader(stringResource(R.string.backup_whats_in_it))
    ModeCard(rows = contents(s.counts, w).map<_, @Composable () -> Unit> { (title, value) -> { ModeValueRow(title, value) } } + listOf<@Composable () -> Unit>({
        ModeToggleRow(
            stringResource(R.string.backup_include_sign_ins), s.includeSignIns, presenter::setIncludeSignIns,
            subtitle = stringResource(R.string.backup_include_sign_ins_why, s.signInNames.joinToString(", ")),
        )
    }))
    if (s.includeSignIns) {
        ModeTextField(stringResource(R.string.backup_password), stringResource(R.string.backup_password), s.password, presenter::setPassword, secure = true)
        ModeTextField(stringResource(R.string.backup_password_again), stringResource(R.string.backup_password_again), s.passwordAgain, presenter::setPasswordAgain, secure = true)
        ModeGuideNote(stringResource(R.string.backup_password_note), ModeNoteKind.Warning)
    }
    ModeButton(
        stringResource(if (s.stage == BackupStage.Making) R.string.backup_making else R.string.backup_make), presenter::make,
        Modifier.padding(top = ModeSpacing.s), icon = ModeIcon.Share, fullWidth = true, enabled = s.canMake && s.stage == BackupStage.Overview,
    )
    ModeText(
        listOfNotNull(stringResource(R.string.backup_save_note), s.lastBackupAtMs?.let { w.get(R.string.backup_last, "${w.day(relative(it))} ${w.timeOfDay(it)}") }).joinToString(" "),
        ModeTextStyle.Footnote, Modifier.fillMaxWidth().padding(horizontal = ModeSpacing.s), color = c.textSecondary, align = TextAlign.Center,
    )
    ModeSectionHeader(stringResource(R.string.backup_restore_header))
    ModeCard(rows = listOf({ ModeRow(stringResource(R.string.backup_restore_row), leading = ModeRowLeading.IconTile(ModeIcon.Share, c.infoSoft, c.infoText), onClick = onRestore) }))
    ModeText(stringResource(R.string.backup_restore_note), ModeTextStyle.Footnote, Modifier.padding(horizontal = ModeSpacing.s), color = c.textSecondary)
}

@Composable
private fun Restore(s: BackupUiState, w: Words, presenter: BackupPresenter) {
    val c = Mode.colors
    ModePageTitle(stringResource(R.string.backup_restore_title))
    ModeCard(rows = listOf({
        ModeRow(
            stringResource(R.string.backup_file),
            subtitle = listOfNotNull(s.backupAtMs?.let { "${w.day(relative(it))} ${w.timeOfDay(it)}" }, s.backupDevice, size(LocalContext.current, s.backupBytes)).joinToString(" · "),
            leading = ModeRowLeading.Mo(MoMood.Idle),
        )
    }))
    ModeSectionHeader(stringResource(R.string.backup_in_this))
    val rows = contents(s.backupCounts, w) + listOfNotNull(s.backupSignInNames.takeIf { it.isNotEmpty() }?.let { w.get(R.string.backup_sign_ins) to it.joinToString(", ") })
    ModeCard(rows = rows.map { (title, value) -> { ModeValueRow(title, value) } })
    if (s.locked) {
        ModeTextField(stringResource(R.string.backup_restore_password), stringResource(R.string.backup_restore_password), s.restorePassword, presenter::setRestorePassword, secure = true)
        if (s.problem == null) ModeText(stringResource(R.string.backup_locked_note), ModeTextStyle.Footnote, color = c.textSecondary)
    }
    ModeButton(
        stringResource(if (s.stage == BackupStage.Restoring) R.string.backup_restoring else R.string.backup_restore_into), presenter::restore,
        Modifier.padding(top = ModeSpacing.s), fullWidth = true, enabled = s.stage == BackupStage.Preview && (!s.locked || s.restorePassword.isNotEmpty()),
    )
    ModeText(stringResource(R.string.backup_restore_adds), ModeTextStyle.Footnote, Modifier.fillMaxWidth(), color = c.textSecondary, align = TextAlign.Center)
}

@Composable
private fun Restored(s: BackupUiState, w: Words, presenter: BackupPresenter, close: () -> Unit) {
    val c = Mode.colors
    ModePageTitle(stringResource(R.string.backup_restored_title))
    Column(Modifier.fillMaxWidth().padding(vertical = ModeSpacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ModeSpacing.m)) {
        ModeMo(MoMood.Done, ModeSize.moWelcome)
        ModeText(stringResource(R.string.backup_restored_body), ModeTextStyle.Callout, color = c.textSecondary, align = TextAlign.Center)
    }
    val added = listOf(
        w.get(R.string.backup_chats) to s.added.chats, w.get(R.string.backup_memory) to s.added.memories,
        w.get(R.string.backup_tasks) to s.added.tasks, w.get(R.string.backup_repeats) to s.added.repeats,
    ).map { (t, n) -> t to w.get(R.string.backup_added, n) } + listOfNotNull(s.restoredSignIns.takeIf { it.isNotEmpty() }?.let { w.get(R.string.backup_sign_ins) to it.joinToString(", ") })
    ModeCard(rows = added.map { (t, v) -> { ModeRow(t, leading = ModeRowLeading.Check) { ModeText(v, ModeTextStyle.Subheadline, color = c.textSecondary) } } })
    if (s.skipped.chats + s.skipped.memories > 0) {
        ModeText(w.get(R.string.backup_skipped, s.skipped.chats, s.skipped.memories), ModeTextStyle.Footnote, Modifier.padding(horizontal = ModeSpacing.s), color = c.textSecondary)
    }
    ModeButton(stringResource(R.string.common_done), { presenter.done(); close() }, Modifier.padding(top = ModeSpacing.s), fullWidth = true)
}

private fun contents(n: BackupCounts, w: Words): List<Pair<String, String>> = listOf(
    w.get(R.string.backup_chats) to w.get(R.string.backup_chats_count, n.chats, n.messages),
    w.get(R.string.backup_memory) to n.memories.toString(),
    w.get(R.string.backup_tasks) to n.tasks.toString(),
    w.get(R.string.backup_repeats) to n.repeats.toString(),
    w.get(R.string.backup_settings) to w.get(R.string.backup_settings_all),
)

@Composable
private fun problem(p: BackupProblem): String = stringResource(when (p) {
    BackupProblem.NotABackup -> R.string.backup_problem_not_a_backup
    BackupProblem.NewerVersion -> R.string.backup_problem_newer
    BackupProblem.WrongPassword -> R.string.backup_problem_wrong_password
    BackupProblem.Unreadable -> R.string.backup_problem_unreadable
    BackupProblem.CouldNotSave -> R.string.backup_problem_could_not_save
})

private fun relative(ms: Long) = com.piyush.mode.app.screens.common.ScreenClock().relativeDay(ms)

/** "2.4 MB". */
private fun size(context: Context, bytes: Long): String = android.text.format.Formatter.formatShortFileSize(context, bytes)

/** Writes the made backup to where the user picked. */
private fun copyOut(context: Context, path: String, uri: Uri): Boolean = runCatching {
    context.contentResolver.openOutputStream(uri)?.use { out -> File(path).inputStream().use { it.copyTo(out) } } != null
}.onFailure { AppLog.log("backup", "save failed: ${it.message}") }.getOrDefault(false)

/** Copies a picked file into Mode's cache, where the backup service reads it. */
private fun copyIn(context: Context, uri: Uri): String? = runCatching {
    val dir = File(context.cacheDir, PICKED_DIR).apply { mkdirs() }
    val file = File(dir, PICKED_NAME)
    context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
    file.absolutePath
}.onFailure { AppLog.log("backup", "open failed: ${it.message}") }.getOrNull()

private const val MIME = "application/octet-stream"
private const val MIME_ANY = "*/*"
private const val DEFAULT_NAME = "Mode backup.modebackup"
private const val PICKED_DIR = "backup"
private const val PICKED_NAME = "picked.modebackup"
