package com.piyush.mode.app.screens

import com.piyush.mode.app.screens.settings.BackupCounts
import com.piyush.mode.app.screens.settings.BackupPresenter
import com.piyush.mode.app.screens.settings.BackupProblem
import com.piyush.mode.app.screens.settings.BackupStage
import com.piyush.mode.feature.backup.data.BackupFiles
import com.piyush.mode.feature.backup.data.BackupService
import com.piyush.mode.testing.InMemoryKeyValueStore
import com.piyush.mode.testing.InMemorySecretStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupPresenterTest {
    private class Files : BackupFiles {
        val files = mutableMapOf<String, ByteArray>()
        var full = false
        override val tempDir = "/tmp"
        override fun read(path: String) = files[path]
        override fun write(path: String, bytes: ByteArray) {
            check(!full) { "disk full" }
            files[path] = bytes
        }
        override fun delete(path: String) { files.remove(path) }
    }

    private class Phone(f: Fixture, val files: Files = Files()) {
        val prefs = InMemoryKeyValueStore()
        val secrets = InMemorySecretStore()
        val service = BackupService(f.db, prefs, secrets, files, deviceName = { "Piyush’s iPhone" }, now = f.clock.now, zone = f.clock.zone)
    }

    private fun Fixture.chat(text: String) {
        val chat = conversations.create(text)
        conversations.addTurn(chat.id, com.piyush.mode.feature.conversations.domain.TurnRole.User, text)
        conversations.addTurn(chat.id, com.piyush.mode.feature.conversations.domain.TurnRole.Mode, "Done.")
    }

    @Test
    fun makingNeedsAMatchingPasswordOfEightOnlyWhenSignInsAreIn() = runTest {
        val f = Fixture(backgroundScope)
        val phone = Phone(f)
        phone.secrets.set("openrouter_api_key", "sk-or-1")
        phone.secrets.set("chatgpt_refresh_token", "rt")
        val p = BackupPresenter(backgroundScope, phone.service, f.clock)

        assertEquals(listOf("ChatGPT", "OpenRouter"), p.state.value.signInNames)
        assertTrue(p.state.value.canMake)
        p.setIncludeSignIns(true)
        assertFalse(p.state.value.canMake)
        p.setPassword("short")
        p.setPasswordAgain("short")
        assertFalse(p.state.value.passwordsMatch, "under 8 characters")
        p.setPassword("correct horse")
        p.setPasswordAgain("correct horsf")
        assertFalse(p.state.value.passwordsMatch)
        assertFalse(p.state.value.canMake)
        p.setPasswordAgain("correct horse")
        assertTrue(p.state.value.passwordsMatch)
        assertTrue(p.state.value.canMake)
        p.setIncludeSignIns(false)
        p.setPasswordAgain("")
        assertTrue(p.state.value.canMake, "no password needed without sign-ins")

        p.setIncludeSignIns(true)
        p.make()
        assertEquals(BackupStage.Overview, p.state.value.stage, "not allowed: nothing happens")
    }

    @Test
    fun aBackupIsMadeSharedAndRemembered() = runTest {
        val f = Fixture(backgroundScope)
        val phone = Phone(f)
        f.chat("Send Asha good morning")
        val p = BackupPresenter(backgroundScope, phone.service, f.clock)
        p.state.awaitState { it.counts == BackupCounts(chats = 1, messages = 2) }
        assertNull(p.state.value.lastBackupAtMs)

        p.make()
        val ready = p.state.awaitState { it.stage == BackupStage.Ready }
        assertEquals("Mode backup 2026-11-01.modebackup", ready.readyName)
        assertTrue(ready.readyPath!! in phone.files.files)

        // Cancelled share sheet: the file goes, nothing is remembered.
        p.shared(completed = false)
        assertEquals(BackupStage.Overview, p.state.value.stage)
        assertTrue(phone.files.files.isEmpty())
        assertNull(p.state.value.lastBackupAtMs)

        p.make()
        p.state.awaitState { it.stage == BackupStage.Ready }
        p.shared(completed = true)
        assertEquals(nov(1, 8), p.state.value.lastBackupAtMs)
        assertEquals(nov(1, 8), BackupPresenter(backgroundScope, phone.service, f.clock).state.value.lastBackupAtMs, "kept")
        assertTrue(phone.files.files.isEmpty())
        assertNull(p.state.value.readyPath)
    }

    @Test
    fun aBackupThatCannotBeWrittenSaysSo() = runTest {
        val f = Fixture(backgroundScope)
        val phone = Phone(f)
        phone.files.full = true
        val p = BackupPresenter(backgroundScope, phone.service, f.clock)
        p.make()
        val s = p.state.awaitState { it.problem != null }
        assertEquals(BackupProblem.CouldNotSave, s.problem)
        assertEquals(BackupStage.Overview, s.stage)
        p.dismissProblem()
        assertNull(p.state.value.problem)
    }

    @Test
    fun aLockedBackupIsPreviewedThenRestoredWithItsPassword() = runTest {
        val from = Fixture(backgroundScope)
        val old = Phone(from)
        from.chat("Send Asha good morning")
        from.chat("Order coffee")
        old.secrets.set("openrouter_api_key", "sk-or-old")
        val made = BackupPresenter(backgroundScope, old.service, from.clock)
        made.setIncludeSignIns(true)
        made.setPassword("correct horse")
        made.setPasswordAgain("correct horse")
        made.make()
        val path = made.state.awaitState { it.stage == BackupStage.Ready }.readyPath!!

        // A new iPhone, with one chat of its own; Swift copied the picked file to /tmp/picked.
        val to = Fixture(backgroundScope)
        val phone = Phone(to)
        to.chat("Weather")
        phone.files.files["/tmp/picked"] = old.files.files.getValue(path)
        val p = BackupPresenter(backgroundScope, phone.service, to.clock)

        p.open("/tmp/picked")
        val preview = p.state.awaitState { it.stage == BackupStage.Preview }
        assertTrue(preview.locked)
        assertEquals(BackupCounts(chats = 2, messages = 4), preview.backupCounts)
        assertEquals(listOf("OpenRouter"), preview.backupSignInNames)
        assertEquals("Piyush’s iPhone", preview.backupDevice)
        assertEquals(nov(1, 8), preview.backupAtMs)
        assertTrue(preview.backupBytes > 0)

        p.restore()
        assertEquals(BackupStage.Preview, p.state.value.stage, "no password typed: nothing happens")
        p.setRestorePassword("wrong horse")
        p.restore()
        val wrong = p.state.awaitState { it.problem != null }
        assertEquals(BackupProblem.WrongPassword, wrong.problem)
        assertEquals(BackupStage.Preview, wrong.stage)
        assertEquals(1L, to.db.conversationQueries.countAll().executeAsOne(), "nothing written")
        p.setRestorePassword("correct horse")
        assertNull(p.state.value.problem, "typing again clears it")

        p.restore()
        val done = p.state.awaitState { it.stage == BackupStage.Restored }
        assertEquals(BackupCounts(chats = 2, messages = 4), done.added)
        assertEquals(BackupCounts(), done.skipped)
        assertEquals(listOf("OpenRouter"), done.restoredSignIns)
        assertEquals(listOf("OpenRouter"), done.signInNames)
        assertFalse("/tmp/picked" in phone.files.files, "the copy is gone")
        p.state.awaitState { it.counts == BackupCounts(chats = 3, messages = 6) }

        p.done()
        assertEquals(BackupStage.Overview, p.state.value.stage)
        assertEquals(BackupCounts(), p.state.value.added)
        assertNull(p.state.value.backupAtMs)
    }

    @Test
    fun restoringTheSameBackupAgainAddsNothingAndCancellingLeavesThePhoneAlone() = runTest {
        val f = Fixture(backgroundScope)
        val phone = Phone(f)
        f.chat("Send Asha good morning")
        val file = phone.service.make(false, null)
        phone.files.files["/tmp/picked"] = phone.files.files.getValue(file.path)
        val p = BackupPresenter(backgroundScope, phone.service, f.clock)

        p.open("/tmp/picked")
        assertFalse(p.state.awaitState { it.stage == BackupStage.Preview }.locked)
        p.cancelRestore()
        assertEquals(BackupStage.Overview, p.state.value.stage)
        assertFalse("/tmp/picked" in phone.files.files)

        phone.files.files["/tmp/picked"] = phone.files.files.getValue(file.path)
        p.open("/tmp/picked")
        p.state.awaitState { it.stage == BackupStage.Preview }
        p.restore()
        val done = p.state.awaitState { it.stage == BackupStage.Restored }
        assertEquals(BackupCounts(), done.added)
        assertEquals(BackupCounts(chats = 1, messages = 2), done.skipped)
    }

    @Test
    fun aFileThatIsNotABackupOrIsFromANewerModeIsSaidToBeSo() = runTest {
        val f = Fixture(backgroundScope)
        val phone = Phone(f)
        val p = BackupPresenter(backgroundScope, phone.service, f.clock)
        phone.files.files["/tmp/photo.jpg"] = byteArrayOf(1, 2, 3)
        p.open("/tmp/photo.jpg")
        assertEquals(BackupProblem.NotABackup, p.state.awaitState { it.problem != null }.problem)
        assertEquals(BackupStage.Overview, p.state.value.stage)
        p.dismissProblem()

        val file = phone.service.make(false, null)
        val text = phone.files.files.getValue(file.path).decodeToString()
        phone.files.files["/tmp/newer"] = text.replace("\"version\":1", "\"version\":2").encodeToByteArray()
        p.open("/tmp/newer")
        assertEquals(BackupProblem.NewerVersion, p.state.awaitState { it.problem != null }.problem)
        assertEquals(BackupStage.Overview, p.state.value.stage)
    }
}
