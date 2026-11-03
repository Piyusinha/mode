package com.piyush.mode.feature.memory

import com.piyush.mode.feature.memory.data.MemoryRepository
import com.piyush.mode.feature.memory.data.Reflector
import com.piyush.mode.feature.memory.domain.MemoryDraft
import com.piyush.mode.feature.memory.domain.MemoryKind
import com.piyush.mode.feature.memory.domain.SaveResult
import com.piyush.mode.llm.ChatMessage
import com.piyush.mode.llm.ChatResult
import com.piyush.mode.llm.LlmClient
import com.piyush.mode.llm.ToolCall
import com.piyush.mode.llm.Usage
import com.piyush.mode.testing.testDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReflectorTest {
    private val repo = MemoryRepository(testDatabase())

    private class Critic(private val reply: JsonObject?) : LlmClient {
        var lastMessages: List<ChatMessage> = emptyList()
        override suspend fun chat(model: String, messages: List<ChatMessage>, tools: JsonArray): ChatResult {
            lastMessages = messages
            return ChatResult(reply?.let { ToolCall("save_memory", it) }, "", Usage(900, 120, 0.0004))
        }
    }

    private fun change(op: String, kind: String, title: String, text: String = "", evidence: String = "step 3", id: String? = null, keywords: List<String> = emptyList(), app: String? = null, subject: String = title.lowercase()) =
        buildJsonObject {
            put("op", op); put("kind", kind); put("title", title); put("text", text); put("evidence", evidence); put("subject", subject)
            id?.let { put("id", it) }
            app?.let { put("app", it) }
            put("keywords", buildJsonArray { keywords.forEach { add(JsonPrimitive(it)) } })
        }

    private fun reply(vararg changes: JsonObject, failure: String = "none", cause: String? = null) = buildJsonObject {
        put("failure_type", failure)
        cause?.let { put("root_cause", it) }
        put("changes", JsonArray(changes.toList()))
    }

    private fun input(outcome: Reflector.Outcome = Reflector.Outcome.Done, task: String = "message Asha saying testing Mode") = Reflector.Input(
        taskId = "k1", task = task, outcome = outcome, summary = "Sent.",
        transcript = listOf("1. open WhatsApp — ok → screen changed", "2. tap \"Asha\" — ok → screen changed"),
        notes = emptyList(), answers = emptyList(), appsOpened = listOf("WhatsApp"), finalScreen = byteArrayOf(1, 2),
    )

    @Test
    fun aGoodTaskTeachesAFactASkillAndATip() = runTest {
        val critic = Critic(
            reply(
                change("add", "fact", "Asha", "Asha: message her on WhatsApp", subject = "asha"),
                change("add", "skill", "Message [Person] on WhatsApp", "1. open WhatsApp\n2. tap the person\n3. type, send", keywords = listOf("message", "whatsapp"), app = "WhatsApp"),
                change("add", "tip", "WhatsApp", "search is at the top of Chats", subject = "whatsapp", app = "WhatsApp"),
            ),
        )
        val r = Reflector(critic, repo).reflect(input(), "m")
        assertEquals(listOf("Asha: message her on WhatsApp"), r.remembered.map { it.text }, "only facts are announced")
        assertEquals(3, r.saved.size)
        assertEquals(0.0004, r.costUsd)
        assertEquals(setOf(MemoryKind.Fact, MemoryKind.Skill, MemoryKind.Tip), repo.all().map { it.kind }.toSet())
        assertTrue(repo.all().all { it.sourceTaskId == "k1" })
    }

    @Test
    fun theCriticSeesTheFinalScreenAndWhatIsKnownButNotTheAgentsReasons() = runTest {
        val known = (repo.save(MemoryDraft(MemoryKind.Fact, "asha", "Asha", "Asha: WhatsApp", "e")) as SaveResult.Added).item
        val critic = Critic(reply())
        Reflector(critic, repo).reflect(input(), "m")
        val user = critic.lastMessages.last()
        assertNotNull(user.image)
        assertTrue("id=${known.id} [fact] Asha: Asha: WhatsApp" in user.text, user.text)
        assertTrue("1. open WhatsApp — ok" in user.text)
    }

    @Test
    fun changesWithoutEvidenceOrAKindAreRefused() = runTest {
        val critic = Critic(reply(change("add", "fact", "Rahul", "Rahul: Telegram", evidence = ""), change("add", "person", "X", "y")))
        val r = Reflector(critic, repo).reflect(input(), "m")
        assertTrue(repo.all().isEmpty())
        assertEquals(2, r.refused.size)
    }

    @Test
    fun todaysListDataIsNotKeptAsAFact() = runTest {
        val critic = Critic(
            reply(
                change("add", "fact", "Matsuri on Swiggy", "Sushi restaurant on Lavelle Road, rated 4.7 with 106 ratings, 65-75 mins delivery, Avocado Maki at Rs 570", subject = "matsuri"),
                change("add", "fact", "Home", "Home is in Indiranagar, Bengaluru", subject = "home"),
            ),
        )
        val r = Reflector(critic, repo).reflect(input(), "m")
        assertEquals(listOf("Home is in Indiranagar, Bengaluru"), repo.all().map { it.text })
        assertTrue(r.refused.single().startsWith("today's list data"), r.refused.toString())
    }

    @Test
    fun aSecretIsNeverSavedWhateverTheModelSays() = runTest {
        val critic = Critic(reply(change("add", "fact", "Bank", "Bank OTP 482913", subject = "bank")))
        val r = Reflector(critic, repo).reflect(input(), "m")
        assertTrue(repo.all().isEmpty())
        assertTrue(r.refused.single().contains("secret"))
    }

    @Test
    fun aFailedTaskCannotMakeOrRewriteASkillButCanAddToIt() = runTest {
        val skill = (repo.save(MemoryDraft(MemoryKind.Skill, "turn off dark mode", "Turn off Dark Mode", "1. Settings 2. search Dark Mode", "e", listOf("dark"), "Settings")) as SaveResult.Added).item
        val critic = Critic(
            reply(
                change("add", "skill", "Turn on Turbo", "1. do things"),
                change("update", "skill", "Turn off Dark Mode", "1. Control Center", id = skill.id),
                change("update", "skill", "Turn off Dark Mode", "1. Settings 2. search Dark Mode\nIf it goes wrong: Display & Brightness", id = skill.id),
                failure = "locating_error", cause = "tapped the wrong row",
            ),
        )
        val r = Reflector(critic, repo).reflect(input(Reflector.Outcome.GaveUp, task = "turn off dark mode"), "m")
        assertEquals(2, r.refused.size, r.refused.toString())
        assertTrue(repo.get(skill.id)!!.text.endsWith("If it goes wrong: Display & Brightness"))
        assertEquals("locating_error", r.failureType)
        assertEquals("tapped the wrong row", r.rootCause)
    }

    @Test
    fun atMostFiveChanges() = runTest {
        val critic = Critic(reply(*Array(7) { change("add", "tip", "App$it", "tip number $it", subject = "app$it") }))
        val r = Reflector(critic, repo).reflect(input(), "m")
        assertEquals(5, repo.all().size)
        assertEquals(2, r.refused.size)
    }

    @Test
    fun aDuplicateFactIsNotAnnouncedAgain() = runTest {
        repo.save(MemoryDraft(MemoryKind.Fact, "asha", "Asha", "Asha: message her on WhatsApp", "e"))
        val critic = Critic(reply(change("add", "fact", "Asha", "Asha: message her on WhatsApp", subject = "asha")))
        val r = Reflector(critic, repo).reflect(input(), "m")
        assertTrue(r.remembered.isEmpty())
        assertEquals(1, repo.all().size)
    }

    @Test
    fun noToolCallMeansNothingIsSaved() = runTest {
        val r = Reflector(Critic(null), repo).reflect(input(), "m")
        assertTrue(r.saved.isEmpty() && repo.all().isEmpty())
    }
}
