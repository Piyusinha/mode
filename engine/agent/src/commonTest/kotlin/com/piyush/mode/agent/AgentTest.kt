package com.piyush.mode.agent

import com.piyush.mode.llm.ChatMessage
import com.piyush.mode.llm.ChatResult
import com.piyush.mode.llm.ToolCall
import com.piyush.mode.llm.Usage
import com.piyush.mode.llm.LlmClient
import com.piyush.mode.llm.LlmFailed
import com.piyush.mode.llm.OpenRouterClient
import com.piyush.mode.ops.Op
import com.piyush.mode.ops.PhoneBackend
import com.piyush.mode.testing.FakePhone
import com.piyush.mode.testing.ScriptedModel
import com.piyush.mode.testing.call
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
        val model = ScriptedModel(call("tap", "x" to 125, "y" to 500, "reason" to "open wifi"), call("done", "evidence" to "on screen", "summary" to "opened"))
        val result = Agent(phone, model, Gate(), noSettle).run("open wifi")

        assertIs<AgentOutcome.Done>(result.outcome)
        val tap = phone.actions.single() as Op.Tap
        assertEquals(125 * 402.0 / 500, tap.x)
        assertEquals(500 * 874.0 / 1000, tap.y)
        assertEquals(2, result.steps)
        assertEquals(0.002, result.costUsd, 1e-9)
    }

    @Test
    fun theModelSeesTextPositionsInScreenshotPixels() = runTest {
        val phone = FakePhone(listOf(listOf("General" to (99.0 to 783.0))))
        val model = ScriptedModel(call("done", "evidence" to "on screen", "summary" to "x"))
        Agent(phone, model, Gate(), noSettle).run("task")
        // 99 pt × 500/402 ≈ 123 px, 783 pt × 1000/874 ≈ 896 px
        assertTrue("\"General\" at (123, 896)" in model.lastUserText, model.lastUserText)
        assertTrue("TASK: task" in model.lastUserText)
    }

    @Test
    fun riskyTapsNeedTheUsersYes() = runTest {
        val phone = FakePhone(listOf(listOf("Request Uber Go" to (201.0 to 800.0))))
        val gate = Gate(allow = false)
        val model = ScriptedModel(call("tap", "x" to 250, "y" to 915, "reason" to "book"), call("done", "evidence" to "on screen", "summary" to "stopped"))
        Agent(phone, model, gate, noSettle).run("get a cab")
        assertTrue(phone.actions.isEmpty(), "declined, so nothing was tapped")
        assertTrue(gate.asked.single().contains("Request Uber Go"))
    }

    @Test
    fun noProgressTwiceStepsUpToTheStrongModel() = runTest {
        // The screen never changes, whatever is tapped.
        val phone = FakePhone(listOf(listOf("Same" to (10.0 to 10.0))))
        phone.screens = List(10) { listOf("Same" to (10.0 to 10.0)) }
        val tap = call("tap", "x" to 1, "y" to 1, "reason" to "try")
        val model = ScriptedModel(tap, tap, tap, call("done", "evidence" to "on screen", "summary" to "x", "success" to false))
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
        assertEquals(3, phone.actions.size)
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
        val model = ScriptedModel(null, call("fly", "reason" to "?"), call("done", "evidence" to "on screen", "summary" to "ok"))
        Agent(phone, model, Gate(), AgentConfig(model = "cheap", strongModel = "strong", settleMs = 0)).run("t")
        assertEquals(listOf("cheap", "cheap", "strong"), model.models)
        assertTrue(phone.actions.isEmpty())
    }

    @Test
    fun onlyToolsThePhoneSupportsAreOffered() = runTest {
        val phone = FakePhone(listOf(listOf("x" to (1.0 to 1.0))), supported = setOf("screen.capture", "screen.text", "input.tap"))
        val model = ScriptedModel(call("done", "evidence" to "on screen", "summary" to "x"))
        Agent(phone, model, Gate(), noSettle).run("t")
        val names = model.lastTools!!.map { it.jsonObject.getValue("function").jsonObject.getValue("name").jsonPrimitive.content }
        assertEquals(listOf("tap", "plan", "wait", "ask_user", "done"), names)
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
        val model = ScriptedModel(call("home", "reason" to "go"), call("done", "evidence" to "on screen", "summary" to "ok"))
        Agent(phone, model, Gate(), noSettle, trace = trace).run("t")
        assertEquals(
            listOf("start:t", "look1:1", "ask1", "answer1:home", "act1:ok", "look2:1", "ask2", "answer2:done", "act2:finished (on screen)", "end:2"),
            events,
        )
    }

    @Test
    fun repeatedWaitingStepsUpAndIsCalledOut() = runTest {
        val phone = FakePhone(List(10) { i -> listOf("log $i" to (1.0 to 1.0)) })
        val wait = call("wait", "seconds" to 0.5, "reason" to "agent thinking")
        val model = ScriptedModel(wait, wait, wait, call("done", "evidence" to "on screen", "summary" to "x"))
        Agent(phone, model, Gate(), AgentConfig(model = "cheap", strongModel = "strong", settleMs = 0)).run("t")
        assertEquals(listOf("cheap", "cheap", "cheap", "strong"), model.models)
        assertTrue("waiting is not getting anywhere" in model.lastUserText)
    }

    @Test
    fun aFailingStrongModelHandsTheStepBackToTheMainModel() = runTest {
        val phone = FakePhone(List(10) { listOf("Same" to (10.0 to 10.0)) })
        val tap = call("tap", "x" to 1, "y" to 1, "reason" to "try")
        val scripted = ScriptedModel(tap, tap, call("done", "evidence" to "on screen", "summary" to "ok", "success" to false))
        val flaky = LlmClient { model, messages, tools ->
            if (model == "strong") throw LlmFailed("strong: HTTP 400: Provider returned error")
            scripted.chat(model, messages, tools)
        }
        val result = Agent(phone, flaky, Gate(), AgentConfig(model = "cheap", strongModel = "strong", settleMs = 0)).run("t")
        assertIs<AgentOutcome.Done>(result.outcome, "the run survives the strong model's error")
        assertEquals(listOf("cheap", "cheap", "cheap"), scripted.models)
    }

    @Test
    fun waitsForTheScreenToBeStillBeforeLookingAgain() = runTest {
        val phone = FakePhone(listOf(listOf("A" to (1.0 to 1.0)), listOf("B" to (1.0 to 1.0))))
        // Capture 1 is step 1's look; after Home the screen moves (BBBB, CCCC) until two match.
        phone.frames = mutableListOf("AAAA", "BBBB", "CCCC", "CCCC", "CCCC")
        val model = ScriptedModel(call("home", "reason" to "x"), call("done", "evidence" to "on screen", "summary" to "ok"))
        Agent(phone, model, Gate(), AgentConfig(settleMs = 0)).run("t")
        // look, then settle: BBBB, CCCC, CCCC (still), then step 2's look.
        assertEquals(5, phone.captures)
    }

    @Test
    fun startingInModeHidesModesScreenAndDoesNotGoHome() = runTest {
        val phone = FakePhone(listOf(listOf("Step 1: thinking" to (1.0 to 1.0))))
        var firstImage: ByteArray? = ByteArray(1)
        var firstText = ""
        val scripted = ScriptedModel(call("done", "evidence" to "on screen", "summary" to "x"))
        val spy = LlmClient { model, messages, tools ->
            if (firstText.isEmpty()) { firstImage = messages.last().image; firstText = messages.last().text }
            scripted.chat(model, messages, tools)
        }
        Agent(phone, spy, Gate(), noSettle).run("turn off dark mode", startsInMode = true)
        assertEquals(null, firstImage, "Mode's own screen is not shown to the model")
        assertTrue("Mode, the app that runs you" in firstText)
        assertTrue("Step 1: thinking" !in firstText)
        assertTrue(phone.actions.isEmpty(), "no Home press")
    }

    @Test
    fun thePlanIsShownBackEveryStep() = runTest {
        val phone = FakePhone(List(10) { i -> listOf("s$i" to (1.0 to 1.0)) })
        val model = ScriptedModel(
            call("plan", "steps" to "1. turn off dark mode\n2. open YouTube"),
            call("home", "reason" to "x"),
            call("done", "evidence" to "on screen", "summary" to "x"),
        )
        Agent(phone, model, Gate(), noSettle).run("t")
        assertTrue("YOUR PLAN" in model.lastUserText && "2. open YouTube" in model.lastUserText, model.lastUserText)
    }

    @Test
    fun aRepeatQuestionIsNotPutToTheUserAgain() = runTest {
        val phone = FakePhone(List(10) { listOf("Sonali" to (1.0 to 1.0)) })
        val gate = Gate(answer = "Sending via agent test")
        val model = ScriptedModel(
            call("ask_user", "question" to "What should the message say?"),
            call("ask_user", "question" to "Please confirm the exact text?"),
            call("done", "evidence" to "on screen", "summary" to "x"),
        )
        Agent(phone, model, gate, noSettle).run("message Sonali")
        assertEquals(listOf("What should the message say?"), gate.asked, "asked once")
        assertTrue("THE USER'S ANSWERS" in model.lastUserText && "Sending via agent test" in model.lastUserText, model.lastUserText)
        assertTrue("not asked" in model.lastUserText)
    }

    @Test
    fun theSendConfirmationShowsWhatWasTyped() = runTest {
        val phone = FakePhone(List(10) { listOf("Send" to (300.0 to 800.0)) })
        val gate = Gate()
        val model = ScriptedModel(
            call("type_text", "text" to "Will sleep after this test", "reason" to "x"),
            call("tap", "x" to 373.0, "y" to 915.0, "reason" to "send"),
            call("done", "evidence" to "on screen", "summary" to "x"),
        )
        Agent(phone, model, gate, noSettle).run("t")
        assertTrue(gate.asked.single().contains("Will sleep after this test"), gate.asked.toString())
    }

    @Test
    fun stopsBeforeAStepOnceTheCostLimitIsReached() = runTest {
        val phone = FakePhone(List(20) { i -> listOf("s$i" to (1.0 to 1.0)) })
        val tap = call("tap", "x" to 1, "y" to 1, "reason" to "go")
        // Each scripted answer costs $0.001.
        val model = ScriptedModel(tap, tap, tap, tap, tap, tap)
        val result = Agent(phone, model, Gate(), AgentConfig(settleMs = 0, maxCostUsd = 0.0025, strongModel = null)).run("t")
        val stopped = assertIs<AgentOutcome.Stopped>(result.outcome)
        assertEquals("Reached the $0.0025 limit for this task after 3 steps. Last: tap \"s2\".", stopped.why)
        assertEquals(3, result.steps)
        assertEquals(3, model.models.size, "no fourth call")
    }

    @Test
    fun controlCenterSwipesDownFromTheTopRight() {
        val g = AgentTools.Geometry(500, 1000, 402.0, 874.0)
        val op = AgentTools.toOp(call("control_center", "reason" to "x"), g) as Op.Drag
        assertTrue(op.x1 > 350 && op.y1 < 5 && op.y2 > 400)
    }

    @Test
    fun backIsTheLeftEdgeSwipe() {
        val g = AgentTools.Geometry(500, 1000, 402.0, 874.0)
        val op = AgentTools.toOp(call("back", "reason" to "x"), g) as Op.Drag
        assertEquals(1.0, op.x1)
        assertTrue(op.x2 > 250, "swipes well across the screen")
        assertEquals(op.y1, op.y2)
    }

    @Test
    fun theModelIsToldWhenItsActionChangedNothing() = runTest {
        val phone = FakePhone(List(10) { listOf("Same" to (10.0 to 10.0)) })
        val tap = call("tap", "x" to 1, "y" to 1, "reason" to "try")
        val model = ScriptedModel(tap, call("done", "evidence" to "on screen", "summary" to "x"))
        Agent(phone, model, Gate(), noSettle).run("t")
        assertTrue("screen did NOT change" in model.lastUserText, model.lastUserText)
    }

    @Test
    fun afterTheUserAnswersModeReopensTheAppItWasIn() = runTest {
        val phone = FakePhone(List(10) { i -> listOf("s$i" to (10.0 to 10.0)) })
        val model = ScriptedModel(
            call("open_app", "name" to "Settings", "reason" to "x"),
            call("ask_user", "question" to "Which Wi-Fi?"),
            call("done", "evidence" to "on screen", "summary" to "x"),
        )
        Agent(phone, model, Gate(answer = "Home Wi-Fi"), noSettle).run("t")
        val launches = phone.actions.filterIsInstance<Op.LaunchApp>().map { it.name }
        assertEquals(listOf("Settings", "bundle.Settings"), launches)
        assertTrue("Mode reopened bundle.Settings" in model.lastUserText, model.lastUserText)
    }

    @Test
    fun aNoteStaysInViewLongAfterItsStepIsSummarised() = runTest {
        val phone = FakePhone(List(30) { i -> listOf("s$i" to (1.0 to 1.0)) })
        val scroll = call("scroll", "direction" to "down", "reason" to "more")
        val model = ScriptedModel(
            call("scroll", "direction" to "down", "reason" to "read", "note" to "09:30 Standup"),
            *Array(14) { scroll },
            call("done", "evidence" to "list end", "summary" to "x"),
        )
        Agent(phone, model, Gate(), AgentConfig(settleMs = 0, maxSteps = 20)).run("what's on today")
        val text = model.lastUserText
        assertTrue("YOUR NOTES" in text && "- 09:30 Standup" in text, text)
        assertTrue("Steps 1–" in text, "step 1 itself is only summarised now")
    }

    @Test
    fun aDoneWithoutEvidenceIsSentBackOnceThenAccepted() = runTest {
        val phone = FakePhone(List(5) { i -> listOf("s$i" to (1.0 to 1.0)) })
        val model = ScriptedModel(call("done", "summary" to "done"), call("done", "summary" to "done again"))
        val result = Agent(phone, model, Gate(), noSettle).run("t")
        val done = assertIs<AgentOutcome.Done>(result.outcome)
        assertEquals("done again", done.summary)
        assertEquals(2, result.steps)
        assertTrue("not accepted yet" in model.lastUserText)
    }

    @Test
    fun aDoneRightAfterAnActionThatChangedNothingIsCheckedOnce() = runTest {
        val phone = FakePhone(List(5) { listOf("Same" to (1.0 to 1.0)) })
        val model = ScriptedModel(
            call("tap", "x" to 1, "y" to 1, "reason" to "send"),
            call("done", "evidence" to "sent", "summary" to "sent"),
            call("done", "evidence" to "sent", "summary" to "sent"),
        )
        val result = Agent(phone, model, Gate(), noSettle).run("t")
        assertIs<AgentOutcome.Done>(result.outcome)
        assertEquals(3, result.steps)
        assertTrue("your last action did not change the screen" in model.lastUserText)
    }

    @Test
    fun aFailedDoneIsNeverSentBack() = runTest {
        val phone = FakePhone(List(5) { listOf("Same" to (1.0 to 1.0)) })
        val model = ScriptedModel(call("done", "summary" to "no such setting", "success" to false))
        assertEquals(1, Agent(phone, model, Gate(), noSettle).run("t").steps)
    }

    @Test
    fun aRunThatGetsNowhereStopsWithAReasonInsteadOfUsingEveryStep() = runTest {
        val phone = FakePhone(List(40) { listOf("Same" to (1.0 to 1.0)) })
        val tap = call("tap", "x" to 1, "y" to 1, "reason" to "try")
        val model = ScriptedModel(*Array(30) { tap })
        val result = Agent(phone, model, Gate(), AgentConfig(model = "cheap", strongModel = "strong", settleMs = 0, maxSteps = 30)).run("t")
        val stopped = assertIs<AgentOutcome.Stopped>(result.outcome)
        assertTrue(stopped.why.startsWith("Stuck: the screen stopped changing"), stopped.why)
        assertTrue("Last: tap" in stopped.why, stopped.why)
        assertTrue(result.steps < 10, "stopped after ${result.steps} steps")
    }

    @Test
    fun stopsOnceTheTimeLimitIsReached() = runTest {
        val phone = FakePhone(listOf(listOf("x" to (1.0 to 1.0))))
        val result = Agent(phone, ScriptedModel(), Gate(), AgentConfig(settleMs = 0, maxMillis = 0)).run("t")
        val stopped = assertIs<AgentOutcome.Stopped>(result.outcome)
        assertTrue(stopped.why.startsWith("Took longer than"), stopped.why)
        assertEquals(0, result.steps)
    }

    @Test
    fun theSameNoteIsKeptOnce() = runTest {
        val phone = FakePhone(List(5) { i -> listOf("s$i" to (1.0 to 1.0)) })
        val scroll = { call("scroll", "direction" to "down", "reason" to "more", "note" to "14:00 Biozyme") }
        val model = ScriptedModel(scroll(), scroll(), call("done", "evidence" to "x", "summary" to "x"))
        Agent(phone, model, Gate(), noSettle).run("t")
        assertEquals(1, Regex("- 14:00 Biozyme").findAll(model.lastUserText).count())
    }

    @Test
    fun replanningAgainAndAgainIsCalledOutAndStepsUp() = runTest {
        val phone = FakePhone(List(10) { listOf("Same" to (1.0 to 1.0)) })
        val plan = call("plan", "steps" to "1. find it")
        val model = ScriptedModel(plan, plan, plan, call("done", "evidence" to "x", "summary" to "x", "success" to false))
        Agent(phone, model, Gate(), AgentConfig(model = "cheap", strongModel = "strong", settleMs = 0)).run("t")
        assertEquals(listOf("cheap", "cheap", "cheap", "strong"), model.models)
        assertTrue("planned again without acting" in model.lastUserText)
        assertTrue("planned or looked things up 3 times in a row" in model.lastUserText, model.lastUserText)
    }

    @Test
    fun aSwipeToTheSamePointIsRefused() {
        val g = AgentTools.Geometry(500, 1000, 402.0, 874.0)
        val e = runCatching { AgentTools.toOp(call("swipe", "x1" to 437, "y1" to 470, "x2" to 437, "y2" to 470), g) }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException && "to tap use tap" in e.message!!)
    }

    @Test
    fun aNoteThatLooksLikeASecretIsHidden() = runTest {
        val phone = FakePhone(List(5) { i -> listOf("s$i" to (1.0 to 1.0)) })
        val model = ScriptedModel(
            call("scroll", "direction" to "down", "reason" to "read", "note" to "Your OTP is 482913"),
            call("done", "evidence" to "x", "summary" to "x"),
        )
        Agent(phone, model, Gate(), noSettle).run("t")
        assertTrue("482913" !in model.lastUserText)
        assertTrue("[hidden: looked like a secret]" in model.lastUserText)
    }

    @Test
    fun anExpectationIsShownOnceOnTheNextStep() = runTest {
        val phone = FakePhone(List(5) { i -> listOf("s$i" to (1.0 to 1.0)) })
        val seen = mutableListOf<String>()
        val scripted = ScriptedModel(
            call("open_app", "name" to "Settings", "reason" to "x", "expect" to "Settings opens"),
            call("scroll", "direction" to "down", "reason" to "x"),
            call("done", "evidence" to "x", "summary" to "x"),
        )
        val model = LlmClient { m, messages, tools -> seen += messages.last().text; scripted.chat(m, messages, tools) }
        val result = Agent(phone, model, Gate(), noSettle).run("t")
        assertEquals(listOf(false, true, false), seen.map { "YOU EXPECTED your last action to: Settings opens" in it })
        assertEquals(listOf("Settings"), result.appsOpened)
        assertEquals(3, result.transcript.size)
        assertTrue(result.finalScreen != null)
    }

    private class Remembers(private val lines: Map<String, List<String>>, private val tips: Map<String, List<String>> = emptyMap()) : AgentMemory {
        val asked = mutableListOf<String>()
        override fun recall(query: String) = lines[query].orEmpty().also { asked += query }
        override fun tipsFor(app: String) = tips[app].orEmpty()
    }

    @Test
    fun recallIsOfferedOnlyWithAMemory() = runTest {
        val phone = FakePhone(listOf(listOf("x" to (1.0 to 1.0))))
        val without = ScriptedModel(call("done", "evidence" to "x", "summary" to "x"))
        Agent(phone, without, Gate(), noSettle).run("t")
        assertTrue("\"recall\"" !in without.lastTools.toString())
        val with = ScriptedModel(call("done", "evidence" to "x", "summary" to "x"))
        Agent(phone, with, Gate(), noSettle, memory = Remembers(emptyMap())).run("t")
        assertTrue("\"recall\"" in with.lastTools.toString())
    }

    @Test
    fun whatRecallFindsIsInTheNextStep() = runTest {
        val phone = FakePhone(List(5) { i -> listOf("s$i" to (1.0 to 1.0)) })
        val memory = Remembers(mapOf("Sonali" to listOf("Sonali: message her on WhatsApp")))
        val model = ScriptedModel(
            call("recall", "query" to "Sonali"),
            call("recall", "query" to "Rahul"),
            call("done", "evidence" to "x", "summary" to "x"),
        )
        Agent(phone, model, Gate(), noSettle, memory = memory).run("message Sonali")
        assertEquals(listOf("Sonali", "Rahul"), memory.asked)
        assertTrue("remembered:\nSonali: message her on WhatsApp" in model.lastUserText, model.lastUserText)
        assertTrue("recall \"Rahul\" — nothing remembered about that" in model.lastUserText)
    }

    @Test
    fun openingAnAppBringsItsTipsOnce() = runTest {
        val phone = FakePhone(List(5) { i -> listOf("s$i" to (1.0 to 1.0)) })
        val memory = Remembers(emptyMap(), tips = mapOf("Calendar" to listOf("Calendar: the list button is top right")))
        val model = ScriptedModel(
            call("open_app", "name" to "Calendar", "reason" to "x"),
            call("open_app", "name" to "Calendar", "reason" to "x"),
            call("done", "evidence" to "x", "summary" to "x"),
        )
        Agent(phone, model, Gate(), noSettle, memory = memory).run("read my calendar")
        assertEquals(1, Regex("Calendar: the list button is top right").findAll(model.lastUserText).count())
        assertTrue("TIPS FOR THESE APPS" in model.lastUserText)
    }
}
