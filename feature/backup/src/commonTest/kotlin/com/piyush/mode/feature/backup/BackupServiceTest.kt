package com.piyush.mode.feature.backup

import com.piyush.mode.core.database.ModeDatabase
import com.piyush.mode.feature.backup.data.BackupFiles
import com.piyush.mode.feature.backup.data.BackupPayload
import com.piyush.mode.feature.backup.data.BackupService
import com.piyush.mode.feature.backup.domain.BackupException
import com.piyush.mode.feature.backup.domain.BackupFailure
import com.piyush.mode.feature.backup.domain.ItemCounts
import com.piyush.mode.feature.backup.domain.RestoreReport
import com.piyush.mode.testing.InMemoryKeyValueStore
import com.piyush.mode.testing.InMemorySecretStore
import com.piyush.mode.testing.testDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Files in a map; the temporary folder is "/tmp". */
internal class MemoryFiles : BackupFiles {
    val files = mutableMapOf<String, ByteArray>()
    override val tempDir: String = "/tmp"
    override fun read(path: String): ByteArray? = files[path]
    override fun write(path: String, bytes: ByteArray) { files[path] = bytes }
    override fun delete(path: String) { files.remove(path) }
}

/** One iPhone: its database, settings, Keychain and files. */
internal class Phone(val files: MemoryFiles = MemoryFiles()) {
    val db: ModeDatabase = testDatabase()
    val prefs = InMemoryKeyValueStore()
    val secrets = InMemorySecretStore()
    val restored = mutableListOf<RestoreReport>()
    val service = BackupService(
        db, prefs, secrets, files,
        deviceName = { "Piyush’s iPhone" }, appVersion = "1.4",
        now = { NOW }, zone = { TimeZone.UTC },
        onRestored = { restored += it },
    )

    fun rows(): BackupPayload = db.transactionWithResult { BackupPayload.read(db) }

    companion object {
        /** 6 Nov 2026, 16:20 UTC. */
        const val NOW = 1_793_982_000_000L
    }
}

/** A row in every table, every column set (nullable ones too), under ids that start with [p]. */
internal fun ModeDatabase.seed(p: String, chats: Int = 2) {
    transaction {
        repeat(chats) { i ->
            val chat = "$p-chat$i"
            conversationQueries.insertOrIgnore(chat, "Chat $p$i", 1_000L + i, 2_000L + i)
            turnQueries.insertOrIgnore("$p-turn$i-a", chat, "user", "Send Asha good morning", "$p-task$i", 1_100L + i)
            turnQueries.insertOrIgnore("$p-turn$i-b", chat, "mode", "Sent it.", null, 1_200L + i)
            taskQueries.insertOrIgnore(
                "$p-task$i", chat, "Send Asha good morning", "voice", "done", 1_050L + i, 1_060L + i, 1_090L + i,
                "$p-run$i", "Sent", 4, 0.012, i.toLong() % 2,
            )
            runQueries.insertOrIgnore("$p-run$i", "$p-task$i", "Send Asha good morning", "done", "Sent", "haiku", 4, 9_000, 0.012, "eval", 1_060L + i)
        }
        memoryQueries.insertOrIgnore(
            "$p-mem", "fact", "Asha", "Asha is my sister", "Asha is my sister", "she said so", "asha sister", "WhatsApp",
            "$p-task0", "Asha is a friend", 3, 2, 1, 900, 950, 1_000,
        )
        memoryQueries.insertOrIgnore("$p-tip", "tip", "Maps", "Search first", "Tap search first", "", "", null, null, null, 0, 0, 0, 800, 800, null)
        scheduleQueries.insertOrIgnore("$p-rep", "Read my calendar", 8, 0, 31, 1, 5_000, 4_000, 700)
    }
}

class BackupServiceTest {
    private fun settingsOf(phone: Phone) {
        phone.prefs.putString("agent_model", "anthropic/claude-haiku-5.5")
        phone.prefs.putString("voice_language", "hi")
        phone.prefs.putString("routine.dismissed.calendar", "1")
        // This iPhone's own:
        phone.prefs.putString("telemetry.installation_id", "install-a")
        phone.prefs.putString("onboarding_done", "true")
        phone.prefs.putString("home_tips_started_at", "123")
        phone.prefs.putString("routine.suggested.calendar", "20398")
        phone.prefs.putString("AppleLanguages", "en")
    }

    private fun signInsOf(phone: Phone) {
        phone.secrets.set("openrouter_api_key", "sk-or-from-a")
        phone.secrets.set("chatgpt_refresh_token", "rt-a")
        phone.secrets.set("chatgpt_access_token", "at-a")
        phone.secrets.set("chatgpt_client_id", "client-a")
        phone.secrets.set("chatgpt_host_id", "urn:uuid:phone-a")
        phone.secrets.set("voice_key_elevenlabs", "el-a")
    }

    private suspend fun failure(block: suspend () -> Unit): BackupFailure =
        assertFailsWith<BackupException> { block() }.failure

    @Test
    fun aRoundTripIntoAnEmptyPhoneGivesTheSameRowsAndSettings() = runTest {
        val a = Phone()
        a.db.seed("a")
        settingsOf(a)
        val file = a.service.make(includeSignIns = false, password = null)
        assertEquals("Mode backup 2026-11-06.modebackup", file.name)
        assertEquals("/tmp/Mode backup 2026-11-06.modebackup", file.path)
        assertEquals(ItemCounts(chats = 2, messages = 4, memories = 2, tasks = 2, repeats = 1, runs = 2), file.header.counts)
        assertFalse(file.header.locked)

        val b = Phone(a.files)
        val report = b.service.restore(file.path, null)

        assertEquals(a.rows(), b.rows())
        assertEquals(ItemCounts(chats = 2, messages = 4, memories = 2, tasks = 2, repeats = 1, runs = 2), report.added)
        assertEquals(ItemCounts(), report.skipped)
        assertEquals(listOf("a-rep"), report.addedRepeatIds)
        assertEquals(listOf(report), b.restored, "told once")
        assertEquals(
            mapOf("agent_model" to "anthropic/claude-haiku-5.5", "voice_language" to "hi", "routine.dismissed.calendar" to "1"),
            b.prefs.values,
        )
        assertEquals(3, report.settingsApplied)
    }

    @Test
    fun restoringAddsOnlyWhatIsNewAndLeavesWhatIsHereAsItIs() = runTest {
        val a = Phone()
        a.db.seed("a", chats = 3)
        val file = a.service.make(false, null)

        val b = Phone(a.files)
        // B already has chat 0 (renamed), its first message, task 0 and the fact, changed since; plus its own rows.
        b.db.conversationQueries.insertOrIgnore("a-chat0", "Renamed here", 1, 2)
        b.db.turnQueries.insertOrIgnore("a-turn0-a", "a-chat0", "user", "Edited here", null, 5)
        b.db.taskQueries.insertOrIgnore("a-task0", null, "Here", "app", "failed", 1, null, null, null, null, null, null, 0)
        b.db.memoryQueries.insertOrIgnore("a-mem", "fact", "Asha", "Asha", "Asha lives in Pune", "", "", null, null, null, 0, 0, 0, 1, 1, null)
        b.db.seed("b", chats = 1)
        val before = b.rows()

        val report = b.service.restore(file.path, null)

        assertEquals(ItemCounts(chats = 2, messages = 5, memories = 1, tasks = 2, repeats = 1, runs = 3), report.added)
        assertEquals(ItemCounts(chats = 1, messages = 1, memories = 1, tasks = 1), report.skipped)
        val after = b.rows()
        // Everything that was here is still here, unchanged.
        before.conversations.forEach { assertTrue(it in after.conversations, "$it kept") }
        before.turns.forEach { assertTrue(it in after.turns, "$it kept") }
        before.tasks.forEach { assertTrue(it in after.tasks, "$it kept") }
        before.memories.forEach { assertTrue(it in after.memories, "$it kept") }
        assertEquals("Renamed here", after.conversations.single { it.id == "a-chat0" }.title)
        assertEquals("Asha lives in Pune", after.memories.single { it.id == "a-mem" }.text)
        assertEquals(2 + 2, after.conversations.size)

        // Restoring the same backup again adds nothing.
        val again = b.service.restore(file.path, null)
        assertEquals(ItemCounts(), again.added)
        assertEquals(ItemCounts(chats = 3, messages = 6, memories = 2, tasks = 3, repeats = 1, runs = 3), again.skipped)
    }

    @Test
    fun aLockedBackupRoundTripsWithItsPasswordAndItsHeaderReadsWithoutIt() = runTest {
        val a = Phone()
        a.db.seed("a")
        signInsOf(a)
        val file = a.service.make(includeSignIns = true, password = "correct horse")
        assertTrue(file.header.locked)
        assertEquals(listOf("ChatGPT", "OpenRouter", "ElevenLabs"), file.header.signInNames)
        val bytes = a.files.files.getValue(file.path)
        assertFalse(bytes.decodeToString().contains("sk-or-from-a"), "the key isn't readable in the file")
        assertFalse(bytes.decodeToString().contains("Send Asha good morning"), "nor the chats")

        val b = Phone(a.files)
        val header = assertNotNull(b.service.peek(file.path))
        assertEquals(file.header.counts, header.counts)
        assertEquals("Piyush’s iPhone", header.deviceName)
        assertEquals(bytes.size.toLong(), header.fileBytes)
        assertNotNull(header.lock).let { assertTrue(it.iterations >= 310_000) }

        val report = b.service.restore(file.path, "correct horse")
        assertEquals(a.rows(), b.rows())
        assertEquals(listOf("ChatGPT", "OpenRouter", "ElevenLabs"), report.restoredSignIns)
        assertEquals("sk-or-from-a", b.secrets.get("openrouter_api_key"))
        assertEquals("rt-a", b.secrets.get("chatgpt_refresh_token"))
        assertNull(b.secrets.get("chatgpt_host_id"), "that iPhone's own id stays there")
    }

    @Test
    fun aWrongPasswordWritesNothing() = runTest {
        val a = Phone()
        a.db.seed("a")
        settingsOf(a)
        signInsOf(a)
        val file = a.service.make(true, "correct horse")

        val b = Phone(a.files)
        assertEquals(BackupFailure.WrongPassword, failure { b.service.restore(file.path, "wrong horse") })
        assertEquals(BackupFailure.WrongPassword, failure { b.service.restore(file.path, null) })
        assertEquals(BackupPayload(), b.rows())
        assertTrue(b.prefs.values.isEmpty())
        assertTrue(b.secrets.values.isEmpty())
        assertTrue(b.restored.isEmpty())
    }

    @Test
    fun aChangedByteMakesItUnreadableAndWritesNothing() = runTest {
        val a = Phone()
        a.db.seed("a")
        settingsOf(a)
        val locked = a.service.make(true, "correct horse")
        val lockedBytes = a.files.files.getValue(locked.path)
        a.files.files["/tmp/locked"] = lockedBytes.copyOf().also { it[it.size - 20] = (it[it.size - 20].toInt() xor 1).toByte() }
        a.files.files["/tmp/header"] = lockedBytes.decodeToString(0, 400).replace("\"chats\":2", "\"chats\":9").encodeToByteArray() +
            lockedBytes.copyOfRange(400, lockedBytes.size)
        val plain = a.service.make(false, null)
        val plainBytes = a.files.files.getValue(plain.path)
        a.files.files["/tmp/plain"] = plainBytes.decodeToString().replace("Send Asha good morning", "Send Asha good evening").encodeToByteArray()

        val b = Phone(a.files)
        assertEquals(BackupFailure.Unreadable, failure { b.service.restore("/tmp/locked", "correct horse") })
        assertEquals(BackupFailure.Unreadable, failure { b.service.restore("/tmp/header", "correct horse") })
        assertEquals(BackupFailure.Unreadable, failure { b.service.restore("/tmp/plain", null) })
        assertEquals(BackupFailure.Unreadable, failure { b.service.restore("/tmp/missing", null) })
        assertEquals(BackupPayload(), b.rows())
        assertTrue(b.prefs.values.isEmpty())
    }

    @Test
    fun aFileThatIsNotAModeBackupIsSaidToBeSo() = runTest {
        val b = Phone()
        b.files.files["/tmp/photo.jpg"] = byteArrayOf(-1, -40, -1, -32, 0, 16)
        b.files.files["/tmp/notes.json"] = "{\"format\":\"mode-backup\",\"version\":1}\n{}".encodeToByteArray()
        b.files.files["/tmp/empty"] = ByteArray(0)
        for (path in listOf("/tmp/photo.jpg", "/tmp/notes.json", "/tmp/empty")) {
            assertNull(b.service.peek(path))
            assertEquals(BackupFailure.NotABackup, failure { b.service.restore(path, null) })
        }
    }

    @Test
    fun aBackupFromANewerModeAsksForAnUpdate() = runTest {
        val a = Phone()
        a.db.seed("a")
        val file = a.service.make(false, null)
        val text = a.files.files.getValue(file.path).decodeToString()
        a.files.files["/tmp/newer"] = text.replace("\"version\":1", "\"version\":2").encodeToByteArray()
        a.files.files["/tmp/newer-shape"] = text.replace("\"version\":1", "\"version\":3").replace("\"counts\":", "\"tallies\":").encodeToByteArray()

        val b = Phone(a.files)
        for (path in listOf("/tmp/newer", "/tmp/newer-shape")) {
            assertTrue(assertNotNull(b.service.peek(path)).newer)
            assertEquals(BackupFailure.NewerVersion, failure { b.service.restore(path, null) })
        }
        assertEquals(BackupPayload(), b.rows())
    }

    @Test
    fun signInsStayOutWhenTheSwitchIsOff() = runTest {
        val a = Phone()
        signInsOf(a)
        val file = a.service.make(includeSignIns = false, password = null)
        assertFalse(file.header.hasSignIns)
        assertEquals(emptyList(), file.header.signInNames)
        val text = a.files.files.getValue(file.path).decodeToString()
        for (secret in a.secrets.values.values) assertFalse(text.contains(secret), "no sign-in in the file")

        val b = Phone(a.files)
        assertEquals(emptyList(), b.service.restore(file.path, null).restoredSignIns)
        assertTrue(b.secrets.values.isEmpty())
        // And sign-ins are never written unlocked.
        assertFailsWith<IllegalArgumentException> { a.service.make(includeSignIns = true, password = null) }
    }

    @Test
    fun aSignInComesBackOnlyForAProviderThisPhoneHasNoneFor() = runTest {
        val a = Phone()
        signInsOf(a)
        val file = a.service.make(true, "correct horse")

        val b = Phone(a.files)
        b.secrets.set("openrouter_api_key", "sk-or-b")
        b.secrets.set("chatgpt_host_id", "urn:uuid:phone-b")
        val report = b.service.restore(file.path, "correct horse")

        assertEquals(listOf("ChatGPT", "ElevenLabs"), report.restoredSignIns)
        assertEquals(listOf("OpenRouter"), report.keptSignIns)
        assertEquals("sk-or-b", b.secrets.get("openrouter_api_key"))
        assertEquals("rt-a", b.secrets.get("chatgpt_refresh_token"))
        assertEquals("client-a", b.secrets.get("chatgpt_client_id"))
        assertEquals("urn:uuid:phone-b", b.secrets.get("chatgpt_host_id"))
        assertEquals("el-a", b.secrets.get("voice_key_elevenlabs"))
        assertEquals(listOf("ChatGPT", "OpenRouter", "ElevenLabs"), b.service.signInNames())
    }

    @Test
    fun thisPhonesOwnSettingsAreNeitherBackedUpNorRestored() = runTest {
        val a = Phone()
        settingsOf(a)
        a.service.markSaved(42)
        val file = a.service.make(false, null)
        val text = a.files.files.getValue(file.path).decodeToString()
        for (own in listOf("install-a", "telemetry.installation_id", "onboarding_done", "home_tips_started_at", "routine.suggested", "AppleLanguages", "backup_last_saved_at")) {
            assertFalse(text.contains(own), "$own stays on that iPhone")
        }

        val b = Phone(a.files)
        b.prefs.putString("telemetry.installation_id", "install-b")
        b.prefs.putString("voice_language", "en")
        b.service.restore(file.path, null)
        assertEquals("install-b", b.prefs.getString("telemetry.installation_id"))
        assertNull(b.prefs.getString("onboarding_done"))
        assertNull(b.service.lastBackupAtMs)
        assertEquals("hi", b.prefs.getString("voice_language"), "the user's setup is applied")
    }

    @Test
    fun aTaskUnfinishedWhenTheBackupWasMadeIsNeverRunHere() = runTest {
        val a = Phone()
        a.db.conversationQueries.insertOrIgnore("c", "Chat", 1, 1)
        a.db.taskQueries.insertOrIgnore("q", "c", "Queued one", "app", "queued", 10, null, null, null, null, null, null, 0)
        a.db.taskQueries.insertOrIgnore("r", "c", "Running one", "app", "running", 10, 11, null, "run-r", null, null, null, 0)
        val file = a.service.make(false, null)

        val b = Phone(a.files)
        b.service.restore(file.path, null)
        val tasks = b.rows().tasks.associateBy { it.id }
        assertEquals("not_started", tasks.getValue("q").status)
        assertEquals("failed", tasks.getValue("r").status)
        assertEquals(11, tasks.getValue("r").endedAt)
        assertTrue(b.db.taskQueries.queued().executeAsList().isEmpty())
    }

    @Test
    fun countsFollowTheDatabase() = runTest {
        val a = Phone()
        assertEquals(ItemCounts(), a.service.counts().first())
        a.db.seed("a")
        assertEquals(ItemCounts(chats = 2, messages = 4, memories = 2, tasks = 2, repeats = 1, runs = 2), a.service.counts().first())
    }

    @Test
    fun onlyTemporaryCopiesAreDeleted() {
        val a = Phone()
        a.files.files["/tmp/Mode backup.modebackup"] = byteArrayOf(1)
        a.files.files["/Users/me/Documents/Mode backup.modebackup"] = byteArrayOf(1)
        a.service.discard("/tmp/Mode backup.modebackup")
        a.service.discard("/Users/me/Documents/Mode backup.modebackup")
        a.service.discard("/tmp/../Users/me/Documents/Mode backup.modebackup")
        assertEquals(setOf("/Users/me/Documents/Mode backup.modebackup"), a.files.files.keys)
    }
}
