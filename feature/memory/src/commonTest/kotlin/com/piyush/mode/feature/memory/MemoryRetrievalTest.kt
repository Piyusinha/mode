package com.piyush.mode.feature.memory

import com.piyush.mode.feature.memory.data.MemoryRepository
import com.piyush.mode.feature.memory.data.MemoryRetrieval
import com.piyush.mode.feature.memory.domain.MemoryDraft
import com.piyush.mode.feature.memory.domain.MemoryKind
import com.piyush.mode.testing.testDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemoryRetrievalTest {
    private var ids = 0
    private val repo = MemoryRepository(testDatabase(), now = { 0L }, newId = { "m${++ids}" })
    private val retrieval = MemoryRetrieval(repo, now = { 0L })

    private fun fact(subject: String, text: String) = repo.save(MemoryDraft(MemoryKind.Fact, subject, subject, text, "e"))
    private fun tip(app: String, text: String) = repo.save(MemoryDraft(MemoryKind.Tip, app, app, text, "e", app = app))
    private fun skill(title: String, text: String, keywords: List<String>, app: String) =
        repo.save(MemoryDraft(MemoryKind.Skill, title.lowercase(), title, text, "e", keywords, app))

    private fun seed() {
        fact("sonali", "Sonali: message her on WhatsApp")
        fact("home", "Home is in Indiranagar, Bengaluru")
        skill("Turn off Dark Mode", "1. open Settings\n2. search \"Dark Mode\"\n3. Appearance → Light", listOf("dark", "mode", "appearance", "light", "turn", "off"), "Settings")
        skill("Message [Person] on WhatsApp", "1. open WhatsApp\n2. search [Person]\n3. type [Text], send", listOf("message", "text", "send", "whatsapp", "saying", "tell"), "WhatsApp")
        tip("whatsapp", "the search bar is at the top of Chats")
        tip("calendar", "the list button is top right; the icon beside it is search")
    }

    @Test
    fun everyFactIsShownWhileThereAreFew() {
        seed()
        val r = retrieval.forTask("turn off dark mode")
        assertEquals(2, r.aboutYou.size)
    }

    @Test
    fun theMatchingSkillIsGivenInFull() {
        seed()
        val r = retrieval.forTask("turn off dark mode")
        assertTrue(r.skills.first().startsWith("SKILL: Turn off Dark Mode\n1. open Settings"), r.skills.toString())
        assertFalse(r.skills.any { it.startsWith("SKILL: Message") })
    }

    @Test
    fun aKnownPersonsNameDoesNotCountAgainstASkillAndTheirAppsTipsComeAlong() {
        seed()
        val r = retrieval.forTask("message Sonali saying I'm late")
        assertTrue(r.skills.first().startsWith("SKILL: Message [Person] on WhatsApp"), r.skills.toString())
        assertEquals(listOf("whatsapp: the search bar is at the top of Chats"), r.tips)
        assertTrue(r.usedIds.size >= 4, "facts, the skill and the tip are all recorded as used")
    }

    @Test
    fun aWeakMatchIsOnlyNamed() {
        seed()
        val r = retrieval.forTask("send a mail")
        assertFalse(r.skills.any { it.startsWith("SKILL:") }, r.skills.toString())
        assertTrue(r.skills.any { "Message [Person] on WhatsApp" in it && "recall" in it }, r.skills.toString())
    }

    @Test
    fun tipsComeForAppsTheTaskNames() {
        seed()
        assertEquals(1, retrieval.forTask("read my calendar").tips.size)
        assertTrue(retrieval.forTask("turn off dark mode").tips.isEmpty())
        assertEquals(1, retrieval.tipsFor("Calendar").size)
    }

    @Test
    fun overTheBudgetTheMatchingFactsWin() {
        repeat(30) { fact("topic$it", "Unrelated preference number $it") }
        fact("sonali", "Sonali: message her on WhatsApp")
        val r = retrieval.forTask("message Sonali")
        assertEquals(MemoryRetrieval.MAX_FACTS, r.aboutYou.size)
        assertTrue("Sonali: message her on WhatsApp" in r.aboutYou)
    }

    @Test
    fun recallFindsAnyKind() {
        seed()
        assertTrue(retrieval.recall("Sonali").first().contains("WhatsApp"))
        assertTrue(retrieval.recall("Message [Person] on WhatsApp").first().startsWith("SKILL: Message"))
        assertTrue(retrieval.recall("   ").isEmpty())
    }
}
