package com.piyush.mode.agent

import com.piyush.mode.ops.Op
import com.piyush.mode.ops.PhoneBackend
import com.piyush.mode.ops.wireName
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** A 402×874-point phone whose screen is 500×1000 in screenshots, with scripted text per screen. */
private class FakePhone(
    var screens: List<List<Pair<String, Pair<Double, Double>>>>,
    private val supported: Set<String> = setOf(
        "screen.capture", "screen.text", "input.tap", "input.scroll", "input.text", "input.keys", "nav.home", "apps.launch",
    ),
) : PhoneBackend {
    val actions = mutableListOf<Op>()
    private var screen = 0
    override val name = "fake"
    override suspend fun ops() = supported

    override suspend fun send(op: Op): JsonElement = when (op) {
        is Op.ScreenCapture -> buildJsonObject {
            put("jpeg_b64", "AAAA")
            put("bounds", buildJsonObject { put("x", 0); put("y", 0); put("w", 402.0); put("h", 874.0); put("id", 0) })
            put("img_px", buildJsonObject { put("w", 500); put("h", 1000) })
        }
        is Op.ScreenText -> buildJsonArray {
            for ((text, at) in screens[screen.coerceAtMost(screens.lastIndex)]) add(
                buildJsonObject { put("text", text); put("x", at.first); put("y", at.second); put("confidence", 1.0) },
            )
        }
        else -> {
            require(op.wireName in supported)
            actions += op
            screen++
            JsonNull
        }
    }
}

private fun call(name: String, vararg args: Pair<String, Any>) = ToolCall(
    name,
    JsonObject(args.associate { (k, v) -> k to (if (v is Number) JsonPrimitive(v) else if (v is Boolean) JsonPrimitive(v) else JsonPrimitive(v.toString())) }),
)

/** Answers each step with the next scripted call and records which model was asked. */
private class ScriptedModel(vararg replies: ToolCall?) : LlmClient {
    private val script = replies.toList()
    private var next = 0
    val models = mutableListOf<String>()
    var lastTools: JsonArray? = null
    var lastUserText = ""
    override suspend fun chat(model: String, messages: List<ChatMessage>, tools: JsonArray): ChatResult {
        models += model
        lastTools = tools
        lastUserText = messages.last().text
        val reply = if (next < script.size) script[next] else call("done", "summary" to "out of script")
        next++
        return ChatResult(reply, "", Usage(100, 10, 0.001))
    }
}

private class Gate(val allow: Boolean = true, val answer: String? = "yes") : UserGate {
    val asked = mutableListOf<String>()
    override suspend fun confirm(question: String) = allow.also { asked += question }
    override suspend fun ask(question: String) = answer.also { asked += question }
}

private val noSettle = AgentConfig(settleMs = 0, maxSteps = 10)

class AgentTest {

    @Test
    fun tapsInScreenshotPixelsLandInScreenPoints() = runTest {
        val phone = FakePhone(listOf(listOf("Wi-Fi" to (100.5 to 437.0)), listOf("Wi-Fi page" to (201.0 to 100.0))))
        val model = ScriptedModel(call("tap", "x" to 125, "y" to 500, "reason" to "open wifi"), call("done", "summary" to "opened"))
        val result = Agent(phone, model, Gate(), noSettle).run("open wifi")

        assertIs<AgentOutcome.Done>(result.outcome)
        assertEquals(Op.Home, phone.actions.first(), "a run starts from the Home Screen")
        val tap = phone.actions[1] as Op.Tap
        assertEquals(125 * 402.0 / 500, tap.x)
        assertEquals(500 * 874.0 / 1000, tap.y)
        assertEquals(2, result.steps)
        assertEquals(0.002, result.costUsd, 1e-9)
    }

    @Test
    fun theModelSeesTextPositionsInScreenshotPixels() = runTest {
        val phone = FakePhone(listOf(listOf("General" to (99.0 to 783.0))))
        val model = ScriptedModel(call("done", "summary" to "x"))
        Agent(phone, model, Gate(), noSettle).run("task")
        // 99 pt × 500/402 ≈ 123 px, 783 pt × 1000/874 ≈ 896 px
        assertTrue("\"General\" at (123, 896)" in model.lastUserText, model.lastUserText)
        assertTrue("TASK: task" in model.lastUserText)
    }

    @Test
    fun riskyTapsNeedTheUsersYes() = runTest {
        val phone = FakePhone(listOf(listOf("Request Uber Go" to (201.0 to 800.0))))
        val gate = Gate(allow = false)
        val model = ScriptedModel(call("tap", "x" to 250, "y" to 915, "reason" to "book"), call("done", "summary" to "stopped"))
        Agent(phone, model, gate, noSettle).run("get a cab")
        assertEquals(listOf<Op>(Op.Home), phone.actions, "declined, so nothing was tapped")
        assertTrue(gate.asked.single().contains("Request Uber Go"))
    }

    @Test
    fun noProgressTwiceStepsUpToTheStrongModel() = runTest {
        // The screen never changes, whatever is tapped.
        val phone = FakePhone(listOf(listOf("Same" to (10.0 to 10.0))))
        phone.screens = List(10) { listOf("Same" to (10.0 to 10.0)) }
        val tap = call("tap", "x" to 1, "y" to 1, "reason" to "try")
        val model = ScriptedModel(tap, tap, tap, call("done", "summary" to "x"))
        Agent(phone, model, Gate(), AgentConfig(model = "cheap", strongModel = "strong", settleMs = 0)).run("t")
        // Two taps change nothing, so the third decision goes to the strong model, then back to cheap.
        assertEquals(listOf("cheap", "cheap", "strong", "cheap"), model.models)
    }

    @Test
    fun stopsAtTheStepLimit() = runTest {
        val phone = FakePhone(List(20) { i -> listOf("screen $i" to (1.0 to 1.0)) })
        val home = call("home", "reason" to "again")
        val model = ScriptedModel(*Array(20) { home })
        val result = Agent(phone, model, Gate(), AgentConfig(maxSteps = 3, settleMs = 0)).run("t")
        val outcome = assertIs<AgentOutcome.Stopped>(result.outcome)
        assertTrue("3 steps" in outcome.why)
        assertEquals(1 + 3, phone.actions.size, "Home first, then three steps")
    }

    @Test
    fun askUserCanStopTheTask() = runTest {
        val phone = FakePhone(listOf(listOf("x" to (1.0 to 1.0))))
        val model = ScriptedModel(call("ask_user", "question" to "Which address?"))
        val result = Agent(phone, model, Gate(answer = null), noSettle).run("t")
        assertIs<AgentOutcome.Stopped>(result.outcome)
    }

    @Test
    fun aReplyWithoutAValidToolIsIgnoredAndLaterStepsUp() = runTest {
        val phone = FakePhone(listOf(listOf("x" to (1.0 to 1.0))))
        val model = ScriptedModel(null, call("fly", "reason" to "?"), call("done", "summary" to "ok"))
        Agent(phone, model, Gate(), AgentConfig(model = "cheap", strongModel = "strong", settleMs = 0)).run("t")
        assertEquals(listOf("cheap", "cheap", "strong"), model.models)
        assertEquals(listOf<Op>(Op.Home), phone.actions)
    }

    @Test
    fun onlyToolsThePhoneSupportsAreOffered() = runTest {
        val phone = FakePhone(listOf(listOf("x" to (1.0 to 1.0))), supported = setOf("screen.capture", "screen.text", "input.tap"))
        val model = ScriptedModel(call("done", "summary" to "x"))
        Agent(phone, model, Gate(), noSettle).run("t")
        val names = model.lastTools!!.map { it.jsonObject.getValue("function").jsonObject.getValue("name").jsonPrimitive.content }
        assertEquals(listOf("tap", "wait", "ask_user", "done"), names)
    }

    @Test
    fun parsesAnOpenRouterToolCallWithCost() {
        val body = """
            {"choices":[{"message":{"content":null,"tool_calls":[{"id":"1","type":"function",
              "function":{"name":"tap","arguments":"{\"x\":10,\"y\":20,\"reason\":\"go\"}"}}]}}],
             "usage":{"prompt_tokens":3000,"completion_tokens":40,"cost":0.00032}}
        """.trimIndent()
        val result = OpenRouterClient.parse(body, "m")
        assertEquals("tap", result.toolCall!!.name)
        assertEquals("go", result.toolCall!!.arguments.getValue("reason").jsonPrimitive.content)
        assertEquals(0.00032, result.usage.costUsd)
        assertEquals(3000, result.usage.promptTokens)
    }

    @Test
    fun riskWordsAreWholeWords() {
        assertTrue(RiskGuard.isRisky("Request Uber Go"))
        assertTrue(RiskGuard.isRisky("Place order"))
        assertTrue(!RiskGuard.isRisky("Places nearby"))
        assertTrue(!RiskGuard.isRisky("Wi-Fi"))
    }

    @Test
    fun schemaListsRequiredArguments() {
        val tap = AgentTools.schema(AgentTools.ALL.filter { it.name == "tap" }).single().jsonObject
            .getValue("function").jsonObject.getValue("parameters").jsonObject
        assertEquals(listOf("x", "y", "reason"), tap.getValue("required").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun traceRecordsEveryPartOfEveryStepInOrder() = runTest {
        val events = mutableListOf<String>()
        val trace = object : AgentTrace {
            override fun runStarted(task: String, config: AgentConfig, tools: List<String>) { events += "start:$task" }
            override fun looked(step: Int, jpeg: ByteArray, imageWidth: Int, imageHeight: Int, texts: List<Triple<String, Int, Int>>, millis: Long) { events += "look$step:${texts.size}" }
            override fun asking(step: Int, model: String, steppedUp: Boolean, prompt: String) { events += "ask$step" }
            override fun answered(step: Int, model: String, toolName: String?, arguments: kotlinx.serialization.json.JsonObject?, text: String, usage: Usage?, millis: Long, error: String?) { events += "answer$step:$toolName" }
            override fun acted(step: Int, action: String, op: String?, outcome: String, millis: Long) { events += "act$step:$outcome" }
            override fun runEnded(result: AgentResult) { events += "end:${result.steps}" }
        }
        val phone = FakePhone(listOf(listOf("A" to (1.0 to 1.0)), listOf("B" to (2.0 to 2.0))))
        val model = ScriptedModel(call("home", "reason" to "go"), call("done", "summary" to "ok"))
        Agent(phone, model, Gate(), noSettle, trace = trace).run("t")
        assertEquals(
            listOf("start:t", "look1:1", "ask1", "answer1:home", "act1:ok", "look2:1", "ask2", "answer2:done", "act2:finished", "end:2"),
            events,
        )
    }

    @Test
    fun repeatedWaitingStepsUpAndIsCalledOut() = runTest {
        val phone = FakePhone(List(10) { i -> listOf("log $i" to (1.0 to 1.0)) })
        val wait = call("wait", "seconds" to 0.5, "reason" to "agent thinking")
        val model = ScriptedModel(wait, wait, wait, call("done", "summary" to "x"))
        Agent(phone, model, Gate(), AgentConfig(model = "cheap", strongModel = "strong", settleMs = 0)).run("t")
        assertEquals(listOf("cheap", "cheap", "cheap", "strong"), model.models)
        assertTrue("waiting is not getting anywhere" in model.lastUserText)
    }
}
