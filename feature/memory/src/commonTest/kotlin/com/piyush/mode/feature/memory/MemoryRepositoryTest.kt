package com.piyush.mode.feature.memory

import com.piyush.mode.feature.memory.data.MemoryRepository
import com.piyush.mode.feature.memory.domain.MemoryDraft
import com.piyush.mode.feature.memory.domain.MemoryKind
import com.piyush.mode.feature.memory.domain.SaveResult
import com.piyush.mode.testing.testDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemoryRepositoryTest {
    private var clock = 1_000L
    private var ids = 0
    private val repo = MemoryRepository(testDatabase(), now = { clock }, newId = { "m${++ids}" })

    private fun fact(text: String, subject: String = "sonali") = MemoryDraft(MemoryKind.Fact, subject, "Sonali", text, "task said so")

    @Test
    fun aNewFactIsAddedWithItsTask() {
        val saved = assertIs<SaveResult.Added>(repo.save(fact("Sonali: message her on WhatsApp"), sourceTaskId = "k1"))
        assertEquals("k1", saved.item.sourceTaskId)
        assertEquals(listOf(saved.item.id), repo.changedByTask("k1").map { it.id })
    }

    @Test
    fun theSameFactAgainChangesNothingAndACloseOneUpdatesIt() {
        repo.save(fact("Sonali: message her on WhatsApp"))
        assertIs<SaveResult.Unchanged>(repo.save(fact("sonali: message her on whatsapp.")))
        val updated = assertIs<SaveResult.Updated>(repo.save(fact("Sonali: message her on WhatsApp, in Hindi")))
        assertEquals("Sonali: message her on WhatsApp", updated.item.previousText)
        assertEquals(1, repo.all().size)
    }

    @Test
    fun aDifferentRouteForTheSameSkillIsKeptBeside() {
        val skill = { text: String -> MemoryDraft(MemoryKind.Skill, "turn off dark mode", "Turn off Dark Mode", text, "run k1", listOf("dark", "mode"), "Settings") }
        repo.save(skill("1. open Settings 2. search Dark Mode 3. Appearance 4. Light"))
        repo.save(skill("1. open Control Center 2. long-press brightness 3. tap Dark Mode"))
        assertEquals(2, repo.ofKind(MemoryKind.Skill).size)
    }

    @Test
    fun secretsAreNeverSaved() {
        val r = assertIs<SaveResult.Rejected>(repo.save(fact("Bank OTP is 482913")))
        assertTrue("secret" in r.why)
        assertTrue(repo.all().isEmpty())
        repo.save(fact("Sonali: message her on WhatsApp"))
        assertNull(repo.update(repo.all().single().id, "Her card 4111 1111 1111 1111", "screen"))
    }

    @Test
    fun undoRemovesAnAdditionAndRestoresAChange() {
        val added = (repo.save(fact("Sonali: WhatsApp")) as SaveResult.Added).item
        repo.update(added.id, "Sonali: Telegram", "she said")
        repo.undo(added.id)
        assertEquals("Sonali: WhatsApp", repo.get(added.id)!!.text)
        repo.undo(added.id)
        assertNull(repo.get(added.id), "the addition itself is undone")
    }

    @Test
    fun useIsRecordedForRanking() {
        val item = (repo.save(fact("Sonali: WhatsApp")) as SaveResult.Added).item
        clock = 5_000
        repo.recordUse(listOf(item.id), success = true)
        repo.recordUse(listOf(item.id), success = false)
        val after = repo.get(item.id)!!
        assertEquals(2, after.uses)
        assertEquals(1, after.successes)
        assertEquals(1, after.failures)
        assertEquals(5_000L, after.lastUsedAt)
    }

    @Test
    fun forgetEverythingEmptiesIt() {
        repo.save(fact("Sonali: WhatsApp"))
        repo.forgetEverything()
        assertTrue(repo.all().isEmpty())
    }
}
