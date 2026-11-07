package com.piyush.mode.feature.backup.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.core.database.ModeDatabase
import com.piyush.mode.core.storage.KeyValueStore
import com.piyush.mode.core.storage.SecretStore
import com.piyush.mode.feature.backup.domain.BackupException
import com.piyush.mode.feature.backup.domain.BackupFailure
import com.piyush.mode.feature.backup.domain.BackupFile
import com.piyush.mode.feature.backup.domain.BackupHeader
import com.piyush.mode.feature.backup.domain.BackupLock
import com.piyush.mode.feature.backup.domain.ItemCounts
import com.piyush.mode.feature.backup.domain.RestoreReport
import com.piyush.mode.feature.settings.data.SignIns
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Backup and restore: everything Mode keeps on this iPhone in one `.modebackup` file (see [BackupArchive]).
 *
 * - **Always in it:** every chat and message, task, run (the history index), memory, repeat, and Mode's
 *   settings except the ones that belong to this iPhone ([DEVICE_ONLY]).
 * - **Only when asked:** the AI and voice sign-ins ([SignIns]), and then only locked with a password.
 * - **Never in it:** run screenshots and step logs, the pairing file, the VPN, logs, this iPhone's ids.
 *
 * A restore merges: rows whose id is already here are left exactly as they are, the rest are added, and
 * nothing is deleted. The database part is one transaction (all or nothing); settings and sign-ins are
 * written only after it succeeds. A sign-in comes back only for a provider this iPhone has none for.
 */
@OptIn(ExperimentalTime::class)
public class BackupService(
    private val db: ModeDatabase,
    private val prefs: KeyValueStore,
    private val secrets: SecretStore,
    private val files: BackupFiles,
    /** Shown on the restore preview ("Piyush’s iPhone"). */
    private val deviceName: () -> String = { "iPhone" },
    private val appVersion: String = "unknown",
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    /** After a restore wrote something: settings, accounts and repeats read it again. Runs on the caller's context. */
    private val onRestored: (RestoreReport) -> Unit = {},
    private val io: CoroutineDispatcher = Dispatchers.Default,
) {
    /** What a backup made now would hold, kept current. */
    public fun counts(): Flow<ItemCounts> {
        fun count(q: app.cash.sqldelight.Query<Long>) = q.asFlow().mapToOne(io)
        val main = combine(
            count(db.conversationQueries.countAll()),
            count(db.turnQueries.countAll()),
            count(db.memoryQueries.countAll()),
            count(db.taskQueries.countAll()),
            count(db.scheduleQueries.countAll()),
        ) { chats, messages, memories, tasks, repeats ->
            ItemCounts(chats.toInt(), messages.toInt(), memories.toInt(), tasks.toInt(), repeats.toInt())
        }
        return combine(main, count(db.runQueries.count())) { c, runs -> c.copy(runs = runs.toInt()) }
    }

    /** The AI and voice providers connected on this iPhone ("ChatGPT", "OpenRouter"). */
    public fun signInNames(): List<String> = SignIns.connected(secrets).map { it.name }

    /** When a backup was last saved somewhere (the share sheet finished); null: never. */
    public val lastBackupAtMs: Long? get() = prefs.getString(LAST_BACKUP)?.toLongOrNull()

    public fun markSaved(atMs: Long = now()) {
        prefs.putString(LAST_BACKUP, atMs.toString())
    }

    /**
     * Writes a backup to the temporary folder. With [includeSignIns] the connected sign-ins go in too. A
     * [password] locks the whole payload; sign-ins need one. Throws [BackupException] (CouldNotSave).
     */
    @OptIn(ExperimentalEncodingApi::class, ExperimentalStdlibApi::class)
    public suspend fun make(includeSignIns: Boolean, password: String?): BackupFile {
        require(!includeSignIns || !password.isNullOrEmpty()) { "Sign-ins are only backed up locked with a password." }
        return withContext(io) {
            try {
                val rows = db.transactionWithResult { BackupPayload.read(db) }
                val signIns = if (!includeSignIns) emptyList() else SignIns.connected(secrets).mapNotNull { signIn ->
                    val values = signIn.keys.mapNotNull { key -> secrets.get(key)?.takeIf { it.isNotBlank() }?.let { key to it } }.toMap()
                    values.takeIf { it.isNotEmpty() }?.let { BackupPayload.SignInEntries(signIn.id, signIn.name, it) }
                }
                val payload = rows.copy(settings = settingsToBackUp(), signIns = signIns)
                val plain = BackupArchive.json.encodeToString(BackupPayload.serializer(), payload).encodeToByteArray()
                val created = now()
                val base = BackupHeader(
                    createdAtMs = created,
                    deviceName = deviceName(),
                    appVersion = appVersion,
                    counts = payload.counts(),
                    hasSignIns = signIns.isNotEmpty(),
                    signInNames = signIns.map { it.name },
                    locked = !password.isNullOrEmpty(),
                )
                val archive = if (password.isNullOrEmpty()) {
                    val header = base.copy(payloadSha256 = BackupCrypto.sha256(plain).toHexString())
                    BackupArchive(header, BackupArchive.headerBytes(header), plain)
                } else {
                    val salt = BackupCrypto.random(BackupCrypto.SALT_BYTES)
                    val nonce = BackupCrypto.random(BackupCrypto.NONCE_BYTES)
                    val keys = BackupCrypto.keys(password, salt, BackupCrypto.ITERATIONS)
                    val header = base.copy(
                        lock = BackupLock(
                            iterations = BackupCrypto.ITERATIONS,
                            salt = Base64.encode(salt),
                            nonce = Base64.encode(nonce),
                            check = Base64.encode(keys.check),
                        ),
                    )
                    val headerBytes = BackupArchive.headerBytes(header)
                    BackupArchive(header, headerBytes, BackupCrypto.seal(keys.cipher, nonce, plain, headerBytes))
                }
                val bytes = archive.bytes()
                val name = fileName(created)
                val path = files.tempDir.trimEnd('/') + "/" + name
                files.write(path, bytes)
                AppLog.log("backup", "made a backup: ${bytes.size} bytes, ${payload.counts()}, sign-ins ${signIns.size}, locked ${archive.header.locked}")
                BackupFile(path, name, bytes.size.toLong(), archive.header.copy(fileBytes = bytes.size.toLong()))
            } catch (e: BackupException) {
                throw e
            } catch (e: Exception) {
                AppLog.error("backup", "could not make a backup", e)
                throw BackupException(BackupFailure.CouldNotSave, e)
            }
        }
    }

    /** What the backup at [path] holds, readable without its password; null when it isn't a Mode backup. */
    public fun peek(path: String): BackupHeader? = files.read(path)?.let(BackupArchive::read)?.header

    /** Deletes [path] if it is a temporary copy (the backup just shared, or a picked file copied in). */
    public fun discard(path: String) {
        if (files.isTemp(path)) files.delete(path)
    }

    /**
     * Adds what the backup at [path] holds and this iPhone doesn't (see the class comment). Throws
     * [BackupException]: NotABackup, NewerVersion, WrongPassword (nothing tried), Unreadable (nothing written).
     */
    public suspend fun restore(path: String, password: String?): RestoreReport {
        val report = withContext(io) {
            val bytes = files.read(path) ?: throw BackupException(BackupFailure.Unreadable)
            val archive = BackupArchive.read(bytes) ?: throw BackupException(BackupFailure.NotABackup)
            if (archive.header.newer) throw BackupException(BackupFailure.NewerVersion)
            val plain = open(archive, password)
            val payload = try {
                BackupArchive.json.decodeFromString(BackupPayload.serializer(), plain.decodeToString())
            } catch (e: Exception) {
                throw BackupException(BackupFailure.Unreadable, e)
            }
            val merged = try {
                merge(payload)
            } catch (e: Exception) {
                AppLog.error("backup", "restore rolled back", e)
                throw BackupException(BackupFailure.Unreadable, e)
            }
            val settings = applySettings(payload.settings)
            val (restored, kept) = applySignIns(payload.signIns)
            merged.copy(restoredSignIns = restored, keptSignIns = kept, settingsApplied = settings).also {
                AppLog.log("backup", "restored: added ${it.added}, already here ${it.skipped}, settings $settings, sign-ins ${restored.size}")
            }
        }
        onRestored(report)
        return report
    }

    @OptIn(ExperimentalEncodingApi::class, ExperimentalStdlibApi::class)
    private suspend fun open(archive: BackupArchive, password: String?): ByteArray {
        val header = archive.header
        if (!header.locked) {
            val sum = header.payloadSha256 ?: throw BackupException(BackupFailure.Unreadable)
            if (!BackupCrypto.same(BackupCrypto.sha256(archive.payload).toHexString().encodeToByteArray(), sum.lowercase().encodeToByteArray())) {
                throw BackupException(BackupFailure.Unreadable)
            }
            return archive.payload
        }
        val lock = header.lock ?: throw BackupException(BackupFailure.Unreadable)
        if (password.isNullOrEmpty()) throw BackupException(BackupFailure.WrongPassword)
        if (lock.kdf != BackupLock.KDF || lock.cipher != BackupLock.CIPHER || lock.iterations !in 1..MAX_ITERATIONS) {
            throw BackupException(BackupFailure.Unreadable)
        }
        val (salt, nonce, check) = try {
            Triple(Base64.decode(lock.salt), Base64.decode(lock.nonce), Base64.decode(lock.check))
        } catch (e: IllegalArgumentException) {
            throw BackupException(BackupFailure.Unreadable, e)
        }
        val keys = BackupCrypto.keys(password, salt, lock.iterations)
        if (!BackupCrypto.same(keys.check, check)) throw BackupException(BackupFailure.WrongPassword)
        return try {
            BackupCrypto.open(keys.cipher, nonce, archive.payload, archive.headerBytes)
        } catch (e: Exception) {
            // The password was right, so the file was changed or damaged.
            throw BackupException(BackupFailure.Unreadable, e)
        }
    }

    /** Adds every row whose id isn't here yet, in one transaction. */
    private fun merge(p: BackupPayload): RestoreReport = db.transactionWithResult {
        val added = Tally()
        val skipped = Tally()
        val chats = db.conversationQueries.ids().executeAsList().toHashSet()
        for (c in p.conversations) {
            if (!chats.add(c.id)) { skipped.chats++; continue }
            db.conversationQueries.insertOrIgnore(c.id, c.title, c.createdAt, c.updatedAt)
            added.chats++
        }
        val turns = db.turnQueries.ids().executeAsList().toHashSet()
        for (t in p.turns) {
            // A message whose chat is in neither place can't be shown anywhere: left out.
            if (t.conversationId !in chats) continue
            if (!turns.add(t.id)) { skipped.messages++; continue }
            db.turnQueries.insertOrIgnore(t.id, t.conversationId, t.role, t.text, t.taskId, t.createdAt)
            added.messages++
        }
        val tasks = db.taskQueries.ids().executeAsList().toHashSet()
        for (t in p.tasks) {
            if (!tasks.add(t.id)) { skipped.tasks++; continue }
            val (status, endedAt, summary) = finished(t)
            db.taskQueries.insertOrIgnore(
                t.id, t.conversationId?.takeIf { it in chats }, t.text, t.source, status, t.createdAt, t.startedAt,
                endedAt, t.runId, summary, t.steps, t.costUsd, t.private,
            )
            added.tasks++
        }
        val runs = db.runQueries.ids().executeAsList().toHashSet()
        for (r in p.runs) {
            if (!runs.add(r.id)) { skipped.runs++; continue }
            db.runQueries.insertOrIgnore(r.id, r.taskId, r.task, r.outcome, r.summary, r.model, r.steps, r.ms, r.costUsd, r.tag, r.startedAt)
            added.runs++
        }
        val memories = db.memoryQueries.ids().executeAsList().toHashSet()
        for (m in p.memories) {
            if (!memories.add(m.id)) { skipped.memories++; continue }
            db.memoryQueries.insertOrIgnore(
                m.id, m.kind, m.subject, m.title, m.text, m.evidence, m.keywords, m.app, m.sourceTaskId, m.previousText,
                m.uses, m.successes, m.failures, m.createdAt, m.updatedAt, m.lastUsedAt,
            )
            added.memories++
        }
        val schedules = db.scheduleQueries.ids().executeAsList().toHashSet()
        val newRepeats = mutableListOf<String>()
        for (s in p.schedules) {
            if (!schedules.add(s.id)) { skipped.repeats++; continue }
            db.scheduleQueries.insertOrIgnore(s.id, s.text, s.hour, s.minute, s.days, s.enabled, s.nextRunAt, s.lastRunAt, s.createdAt)
            added.repeats++
            newRepeats += s.id
        }
        RestoreReport(added.counts(), skipped.counts(), emptyList(), addedRepeatIds = newRepeats)
    }

    /**
     * A task that was queued, running or waiting when the backup was made never finishes here: restored as
     * ended, so it is never picked up and run on this iPhone.
     */
    private fun finished(t: BackupPayload.Task): Triple<String, Long?, String?> = when (t.status) {
        "queued" -> Triple("not_started", t.endedAt ?: t.createdAt, t.summary ?: "Not run: it was waiting when the backup was made.")
        "running", "waiting" -> Triple("failed", t.endedAt ?: t.startedAt ?: t.createdAt, t.summary ?: "Mode was closed during this task.")
        else -> Triple(t.status, t.endedAt, t.summary)
    }

    private fun settingsToBackUp(): Map<String, String> =
        prefs.names().filter(::portable).sorted().mapNotNull { name -> prefs.getString(name)?.let { name to it } }.toMap()

    /** The backup's settings are the user's setup: applied, except this iPhone's own. */
    private fun applySettings(settings: Map<String, String>): Int {
        var applied = 0
        for ((name, value) in settings) {
            if (!portable(name)) continue
            prefs.putString(name, value)
            applied++
        }
        return applied
    }

    /** Each sign-in in the backup whose provider has none here; the others are left as they are. */
    private fun applySignIns(entries: List<BackupPayload.SignInEntries>): Pair<List<String>, List<String>> {
        val restored = mutableListOf<String>()
        val kept = mutableListOf<String>()
        for (entry in entries) {
            val signIn = SignIns.all.firstOrNull { it.id == entry.id } ?: continue
            if (signIn.connected(secrets)) { kept += signIn.name; continue }
            val values = entry.values.filterKeys { it in signIn.keys }
            if (values[signIn.connectedKey].isNullOrBlank()) continue
            try {
                values.forEach { (key, value) -> secrets.set(key, value) }
                restored += signIn.name
            } catch (e: Exception) {
                AppLog.error("backup", "could not restore the ${signIn.name} sign-in", e)
                signIn.keys.forEach { runCatching { secrets.set(it, null) } }
            }
        }
        return restored to kept
    }

    private fun fileName(atMs: Long): String {
        val date = Instant.fromEpochMilliseconds(atMs).toLocalDateTime(zone()).date
        return "Mode backup $date.${BackupHeader.EXTENSION}"
    }

    private class Tally {
        var chats = 0; var messages = 0; var memories = 0; var tasks = 0; var repeats = 0; var runs = 0
        fun counts() = ItemCounts(chats, messages, memories, tasks, repeats, runs)
    }

    public companion object {
        /** Where [lastBackupAtMs] is kept. */
        internal const val LAST_BACKUP: String = "backup_last_saved_at"

        /**
         * Settings that belong to this iPhone, never backed up or restored:
         * - `telemetry.installation_id`: this install's anonymous id.
         * - `onboarding_done`: whether setup (pairing, VPN, permissions) was done here.
         * - `voice_unlock_set_up`: Unlock by voice is set up in this iPhone's accessibility settings.
         * - `home_tips_started_at`: when this install first showed Home's tips.
         * - `max_cost_moved_to_brain_default`: a one-time upgrade of this install's stored limit.
         * - `claude_models`, `claude_models_at`: a cache of the signed-in plan's models, asked again.
         * - [LAST_BACKUP]: when this iPhone last saved a backup.
         */
        public val DEVICE_ONLY: Set<String> = setOf(
            "telemetry.installation_id",
            "onboarding_done",
            "voice_unlock_set_up",
            "home_tips_started_at",
            "max_cost_moved_to_brain_default",
            "claude_models",
            "claude_models_at",
            LAST_BACKUP,
        )

        /** Prefixes of per-day state: `routine.suggested.<routine>` is the day a routine was last suggested here. */
        public val DEVICE_ONLY_PREFIXES: List<String> = listOf("routine.suggested.")

        /** A guard on a crafted header: a backup this slow to open isn't one Mode made. */
        private const val MAX_ITERATIONS = 10_000_000

        /**
         * Backed up and restored: Mode's own settings (lower-case names; system and framework defaults
         * start upper-case or with `com.apple.`), less [DEVICE_ONLY].
         */
        internal fun portable(name: String): Boolean =
            name.isNotEmpty() && name[0].isLowerCase() && !name.startsWith("com.apple.") &&
                name !in DEVICE_ONLY && DEVICE_ONLY_PREFIXES.none { name.startsWith(it) }
    }
}

private fun BackupPayload.counts() = ItemCounts(
    chats = conversations.size,
    messages = turns.size,
    memories = memories.size,
    tasks = tasks.size,
    repeats = schedules.size,
    runs = runs.size,
)
