package com.piyush.mode.feature.settings

import com.piyush.mode.agent.Brain
import com.piyush.mode.feature.settings.data.BrainProbe
import com.piyush.mode.feature.settings.data.ModelBench
import com.piyush.mode.llm.ChatResult
import com.piyush.mode.llm.LlmClient
import com.piyush.mode.llm.ToolCall
import com.piyush.mode.llm.Usage
import com.piyush.mode.testing.InMemoryKeyValueStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrainProbesTest {
    /** A brain that answers every choice with [letter] and writes briefs naming [app], asking [ask]. */
    private fun brain(letter: String, app: String = "FoodNow", ask: List<String> = emptyList()) = LlmClient { _, messages, tools ->
        val tool = (tools.first() as JsonObject)["function"].let { (it as JsonObject)["name"].toString().trim('"') }
        val call = if (tool == "decision") {
            ToolCall("decision", JsonObject(mapOf("answer" to JsonPrimitive("$letter) because it fits"))))
        } else {
            ToolCall(
                "brief",
                JsonObject(
                    mapOf(
                        "intent" to JsonPrimitive(messages.last().text), "app" to JsonPrimitive(app), "start" to JsonPrimitive("fresh"),
                        "done_when" to JsonPrimitive("placed"), "steps" to JsonArray(listOf(JsonPrimitive("Order it"))),
                        "ask" to JsonArray(ask.map(::JsonPrimitive)),
                    ),
                ),
            )
        }
        ChatResult(call, "", Usage(10, 1, 0.0))
    }

    @Test
    fun readsTheOptionLetterAnAnswerStartsWith() {
        assertEquals('B', letterOf("B) Open Napoli Corner"))
        assertEquals('C', letterOf("Option C: tap Dark"))
        assertEquals('B', letterOf("**B.** Tap \"Tue 3 Nov\""))
        assertEquals('B', letterOf("b - type it"))
        assertNull(letterOf("Open Napoli Corner"), "no letter, no answer")
        assertNull(letterOf("Because B is right"))
    }

    @Test
    fun aChoiceIsRightOnlyWithTheRightLetter() = runTest {
        val probe = BrainProbe.ALL.first() as BrainProbe.Decide
        assertTrue(probe.passes(Brain(brain("B"), "m")))
        assertFalse(probe.passes(Brain(brain("A"), "m")), "the sponsored place is wrong")
        assertFalse(probe.passes(Brain(LlmClient { _, _, _ -> error("offline") }, "m")), "no answer is wrong")
    }

    @Test
    fun aPlanIsCheckedForItsAppAndWhetherItAsks() = runTest {
        val sameAsLastTime = BrainProbe.ALL.filterIsInstance<BrainProbe.Plan>()[0]
        val whichPizza = BrainProbe.ALL.filterIsInstance<BrainProbe.Plan>()[1]
        assertTrue(sameAsLastTime.passes(Brain(brain("A", app = "FoodNow app"), "m")))
        assertFalse(sameAsLastTime.passes(Brain(brain("A", app = "GoRide"), "m")), "the wrong app from history")
        assertFalse(sameAsLastTime.passes(Brain(brain("A", ask = listOf("Which dish?")), "m")), "it was already known")
        assertTrue(whichPizza.passes(Brain(brain("A", ask = listOf("Which pizza?")), "m")))
        assertFalse(whichPizza.passes(Brain(brain("A"), "m")), "which pizza is the user's to choose")
    }

    @Test
    fun eachRoleKeepsItsOwnResults() = runTest {
        val bench = ModelBench(InMemoryKeyValueStore())
        bench.measureBrain("chatgpt", brain("B"), "gpt-6.1-sol@high", BrainProbe.ALL.take(2))
        assertEquals(2, bench.results("chatgpt", brain = true)["gpt-6.1-sol@high"]?.correct, "both answers are B")
        assertTrue(bench.results("chatgpt").isEmpty(), "phone control's results do not show the brain's")
        assertTrue(bench.results("claude_oauth", brain = true).isEmpty())
    }

    @Test
    fun aModelThatCannotBeAskedSaysWhyAndIsNotTimed() = runTest {
        val bench = ModelBench(InMemoryKeyValueStore())
        val refusing = LlmClient { _, _, _ -> throw com.piyush.mode.llm.LlmFailed("Claude: model not available on your plan") }
        val brain = bench.measureBrain("claude_oauth", refusing, "claude-opus-5-5@high", BrainProbe.ALL.take(3))
        assertEquals(0, brain.correct)
        assertEquals(0L, brain.millis)
        assertEquals("Claude: model not available on your plan", brain.error)
        val phone = bench.measure("claude_oauth", refusing, "claude-opus-5-5@low", listOf(ModelBench.Probe(ByteArray(1), "1")))
        assertEquals("Claude: model not available on your plan", phone.error)
        // A model that answers, even wrongly, has no error.
        assertNull(bench.measureBrain("claude_oauth", brain("A"), "m", BrainProbe.ALL.take(2)).error)
    }

    private fun letterOf(answer: String): Char? = BrainProbe.letter(answer)
}
