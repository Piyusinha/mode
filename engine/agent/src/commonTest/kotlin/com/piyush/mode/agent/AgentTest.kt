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
import kotlinx.coroutines.launch
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
    val offered = mutableListOf<Pair<List<String>, String?>>()
    override suspend fun confirm(question: String) = allow.also { asked += question }
    override suspend fun ask(question: String) = answer.also { asked += question }
    override suspend fun choose(question: String, options: List<String>, recommended: String?) =
        answer.also { asked += question; offered += options to recommended }
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
        assertTrue("[1] \"General\" (123, 896)" in model.lastUserText, model.lastUserText)
        assertTrue("TASK: task" in model.lastUserText)
    }

    @Test
    fun aTapByIdLandsOnThatTextsCentre() = runTest {
        val phone = FakePhone(listOf(listOf("Recent" to (60.0 to 250.0), "Bellandur" to (100.0 to 300.0)), listOf("Ride options" to (100.0 to 300.0))))
        val model = ScriptedModel(call("tap", "id" to 2, "reason" to "Picking the recent destination"), call("done", "evidence" to "x", "summary" to "x"))
        Agent(phone, model, Gate(), noSettle).run("ride to Bellandur")
        val tap = phone.actions.single() as Op.Tap
        assertEquals(100.0, tap.x, 0.5)
        assertEquals(300.0, tap.y, 0.5)
    }

    @Test
    fun anIdThatIsNotOnTheScreenIsRefused() = runTest {
        val phone = FakePhone(List(3) { listOf("Bellandur" to (100.0 to 300.0)) })
        val model = ScriptedModel(call("tap", "id" to 9, "reason" to "Picking it"), call("done", "evidence" to "x", "summary" to "x"))
        Agent(phone, model, Gate(), noSettle).run("ride to Bellandur")
        assertTrue(phone.actions.isEmpty())
        assertTrue("there is no [9] on this screen" in model.lastUserText, model.lastUserText)
    }

    @Test
    fun aScrollThatShowsNothingNewIsTheEndOfTheList() = runTest {
        val top = List(8) { "meal $it" to (100.0 to 120.0 + it * 80) }
        val bottom = List(8) { "meal ${it + 4}" to (100.0 to 120.0 + it * 80) }
        val phone = FakePhone(listOf(top, bottom, bottom.map { (t, p) -> t to (p.first to p.second - 2) }))
        val down = call("scroll", "direction" to "down", "reason" to "Reading the diary")
        val model = ScriptedModel(down, down, call("done", "evidence" to "x", "summary" to "x"))
        Agent(phone, model, Gate(), noSettle).run("what did I eat")
        assertTrue("end of the list: nothing new appeared" in model.lastUserText, model.lastUserText)
        assertEquals(1, Regex("end of the list").findAll(model.lastUserText).count(), "the first scroll showed new meals")
    }

    @Test
    fun aScrollMovesMostOfAScreen() {
        val g = AgentTools.Geometry(500, 1000, 402.0, 874.0)
        val op = AgentTools.toOp(call("scroll", "direction" to "down", "reason" to "r"), g) as Op.Scroll
        assertEquals(-874.0 * 0.6, op.dy, 1e-9)
    }

    @Test
    fun aLearnedRouteIsThePlanWithoutABrief() = runTest {
        val phone = FakePhone(List(4) { listOf("Settings" to (100.0 to 300.0)) })
        val model = ScriptedModel(call("done", "evidence" to "x", "summary" to "x"))
        val route = "1. open Settings\n2. search \"Dark Mode\"\n3. Appearance → Light\nIf it goes wrong: use Control Center"
        Agent(phone, model, Gate(), noSettle.copy(model = "cheap", strongModel = "strong", brainModel = "brain"))
            .run("turn off dark mode", context = TaskContext(knownRoute = route))
        assertEquals(listOf("cheap"), model.models, "no brief: the learned route is the plan")
        assertTrue("1. open Settings\n2. search \"Dark Mode\"\n3. Appearance → Light" in model.lastUserText, model.lastUserText)
        assertTrue("If it goes wrong" !in model.lastUserText.substringAfter("YOUR PLAN"), "only the numbered steps")
    }

    @Test
    fun withoutALearnedRouteTheBrainBriefs() = runTest {
        val phone = FakePhone(List(4) { listOf("Settings" to (100.0 to 300.0)) })
        val model = ScriptedModel(
            call("brief", "intent" to "x", "start" to "fresh", "done_when" to "x", "steps" to listOf("a", "b", "c", "d", "e", "f"), "report" to "x"),
            call("done", "evidence" to "x", "summary" to "x"),
        )
        Agent(phone, model, Gate(), noSettle.copy(model = "cheap", strongModel = "strong", brainModel = "brain")).run("turn off dark mode")
        assertEquals("brain", model.models.first())
        assertTrue("4. d" in model.lastUserText && "5. e" !in model.lastUserText, "at most four steps")
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
        fun tap(x: Int) = call("tap", "x" to x, "y" to 1, "reason" to "try")
        val model = ScriptedModel(tap(1), tap(200), tap(400), call("done", "evidence" to "on screen", "summary" to "x", "success" to false))
        Agent(phone, model, Gate(), AgentConfig(model = "cheap", strongModel = "strong", settleMs = 0)).run("t")
        // Two taps change nothing, so the third decision goes to the strong model, then back to cheap.
        // Two taps change nothing: the third decision goes to the strong model; the next stall too.
        assertEquals(listOf("cheap", "cheap", "strong", "strong"), model.models)
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
        // A tap takes an id or x and y, so only the reason is required.
        assertEquals(listOf("reason"), tap.getValue("required").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(setOf("id", "x", "y", "reason"), tap.getValue("properties").jsonObject.keys.intersect(setOf("id", "x", "y", "reason")))
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
        // Capture 1 is step 1's look; after Home the screen moves (a big frame, then a small one) until two match.
        phone.frames = mutableListOf("AAAA", "BBBBBBBBBBBBBBBB", "CCCC", "CCCC", "CCCC")
        val model = ScriptedModel(call("home", "reason" to "x"), call("done", "evidence" to "on screen", "summary" to "ok"))
        Agent(phone, model, Gate(), AgentConfig(settleMs = 0)).run("t")
        // look, then settle: BBBB, CCCC, CCCC (still), then step 2's look.
        assertEquals(5, phone.captures)
    }

    @Test
    fun aScreenThatNeverStopsMovingIsWaitedOnForAtMostTheCap() = runTest {
        // On the phone: a blinking caret made every small screenshot differ, and a tap took 8.5-9 s.
        val phone = FakePhone(listOf(listOf("A" to (1.0 to 1.0))))
        phone.frames = MutableList(60) { if (it % 2 == 0) "AAAA" else "BBBBBBBBBBBBBBBB" }
        val model = ScriptedModel(call("home", "reason" to "x"), call("done", "evidence" to "on screen", "summary" to "ok"))
        Agent(phone, model, Gate(), AgentConfig(settleMs = 0)).run("t")
        // One settle polls every 150 ms for at most 1.5 s (about 11 small shots), plus the looks around it;
        // before the cap it kept going for every frame that differed.
        assertTrue(phone.captures <= 16, "captured ${phone.captures} times")
    }

    @Test
    fun aBlinkingCaretCountsAsStill() = runTest {
        val phone = FakePhone(listOf(listOf("A" to (1.0 to 1.0))))
        // Two frames a sliver apart in size (a caret on, then off): still at once.
        phone.frames = mutableListOf("AAAA", "A".repeat(400), "B".repeat(400), "C".repeat(400))
        val model = ScriptedModel(call("home", "reason" to "x"), call("done", "evidence" to "on screen", "summary" to "ok"))
        Agent(phone, model, Gate(), AgentConfig(settleMs = 0)).run("t")
        // look, two settle frames (still at once), then the looks for the done and its check: 5. Byte-exact
        // comparison would have kept settling through every differing frame.
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
    fun aConfirmedAddIsNeverRepeatedEvenWhenThePlusComesBack() = runTest {
        // This run's shape: + on a result; "Food logged!" right after; later the row shows + again.
        val results = listOf("Dark chocolate oats - high protein" to (120.0 to 272.0), "392 cal, 100 gram" to (120.0 to 291.0))
        val phone = FakePhone(listOf(results, results + ("Food logged!" to (200.0 to 700.0))) + List(10) { results })
        val add = call("tap", "x" to 411, "y" to 340, "reason" to "Adding the 100 g oats to Dinner")
        val model = ScriptedModel(
            add, add,
            call("done", "evidence" to "Food logged!", "summary" to "x"), call("done", "evidence" to "Food logged!", "summary" to "x"),
        )
        Agent(phone, model, Gate(), noSettle.copy(model = "cheap", strongModel = "strong")).run("add oats 100 g to dinner")
        assertEquals(1, phone.actions.count { it is Op.Tap }, "the second + is refused: it was confirmed")
        assertTrue("already done at step 1" in model.lastUserText || "DONE SO FAR" in model.lastUserText, model.lastUserText)
        assertTrue("Dark chocolate oats - high protein" in model.lastUserText, "the record names the item")
    }

    @Test
    fun waitingOrScrollingIsNotACheckButLeavingThePageIs() = runTest {
        val results = listOf("Chocolate oats" to (120.0 to 272.0), "196 cal" to (120.0 to 291.0), "Search Results" to (100.0 to 200.0))
        val diary = listOf("Dinner" to (100.0 to 300.0), "Breakfast" to (100.0 to 200.0), "Calories remaining" to (200.0 to 150.0))
        // The fake phone moves to its next screen on each phone action: add → results, close → the diary.
        val phone = FakePhone(listOf(results, results, diary, results) + List(10) { results })
        val add = call("tap", "x" to 411, "y" to 340, "reason" to "Adding the oats")
        val model = ScriptedModel(
            add,                                                                        // goes through, no proof
            call("wait", "seconds" to 1, "reason" to "checking"),
            add,                                                                        // held: a wait is not a check
            call("tap", "x" to 30, "y" to 100, "reason" to "Closing the search to see the diary"), // leaves the page
            add,                                                                        // now allowed
            call("done", "evidence" to "x", "summary" to "x"), call("done", "evidence" to "x", "summary" to "x"),
        )
        Agent(phone, model, Gate(), noSettle).run("add oats")
        assertEquals(2, phone.actions.count { it is Op.Tap && it.x > 300 }, "the add after the wait waited; after leaving the page it went")
    }

    @Test
    fun scrollingBackAndForthIsHeldAndKeptGoingOneWay() = runTest {
        // On the phone: up, down, up, down looking for "Snacks", just below the last section shown.
        val phone = FakePhone(List(12) { i -> listOf("Diary" to (100.0 to 200.0), "row ${i % 3}" to (100.0 to 300.0 + i)) })
        fun scroll(d: String) = call("scroll", "direction" to d, "reason" to "Looking for Snacks")
        val model = ScriptedModel(
            scroll("down"), scroll("up"), scroll("down"), scroll("up"), scroll("down"),
            call("done", "evidence" to "x", "summary" to "x"), call("done", "evidence" to "x", "summary" to "x"),
        )
        Agent(phone, model, Gate(), noSettle).run("add protein to snacks")
        assertEquals(4, phone.actions.count { it is Op.Scroll }, "the third reversal (up) is held; down goes on")
        assertTrue("Keep scrolling down" in model.lastUserText, model.lastUserText)
    }

    @Test
    fun whatWasSeenWhileScrollingAListIsKeptForComparing() = runTest {
        // On the phone: Matsuri 4.7 was on screen; scrolling down and up to "check the top" never ended.
        val top = listOf("Rating (High to Low)" to (100.0 to 260.0), "WeFit - Protein Bowls 4.7 (186) AD" to (200.0 to 700.0), "Matsuri - The Chancery 4.7 (106)" to (200.0 to 920.0))
        val next = listOf("Rating (High to Low)" to (100.0 to 260.0), "Yuki 4.6 (2.3K+)" to (200.0 to 370.0), "Noodle Bar 4.6 (2.0K+)" to (200.0 to 620.0))
        val phone = FakePhone(listOf(top, next, next, next))
        val model = ScriptedModel(
            call("scroll", "direction" to "down", "reason" to "Seeing more restaurants"),
            call("done", "evidence" to "x", "summary" to "x"), call("done", "evidence" to "x", "summary" to "x"),
        )
        Agent(phone, model, Gate(), noSettle).run("highest rated sushi")
        val seen = model.lastUserText.substringAfter("SEEN WHILE SCROLLING").substringBefore("\n\n")
        assertTrue("Matsuri - The Chancery 4.7 (106)" in seen && "Yuki 4.6 (2.3K+)" in seen, model.lastUserText)
    }

    @Test
    fun everyFurtherReversalIsHeldUntilTheAgentActs() = runTest {
        val phone = FakePhone(List(12) { i -> listOf("List" to (100.0 to 200.0), "row $i" to (100.0 to 300.0 + i)) })
        fun scroll(d: String) = call("scroll", "direction" to d, "reason" to "Checking")
        val model = ScriptedModel(
            scroll("down"), scroll("up"), scroll("down"), scroll("up"), scroll("up"), scroll("down"), scroll("up"),
            call("done", "evidence" to "x", "summary" to "x"), call("done", "evidence" to "x", "summary" to "x"),
        )
        Agent(phone, model, Gate(), noSettle).run("t")
        // down, up, down go; up, up held; down goes; up held.
        assertEquals(4, phone.actions.count { it is Op.Scroll }, phone.actions.toString())
    }

    @Test
    fun theHandsAskTheBrainToChooseAndThereIsNoPlanToolWithABrief() = runTest {
        val list = listOf("WeFit - Protein Bowls 4.7 (186) AD" to (200.0 to 300.0), "Matsuri - The Chancery 4.7 (106)" to (200.0 to 500.0))
        val phone = FakePhone(List(5) { list })
        val model = ScriptedModel(
            call("brief", "intent" to "Add sushi from the highest-rated restaurant", "app" to "Swiggy", "start" to "continue",
                "done_when" to "cart shows sushi", "steps" to listOf("Search sushi", "Pick the highest rated"), "report" to "what was added"),
            call("decide", "question" to "Which restaurant is highest rated, ignoring ads?"),
            call("decision", "answer" to "Matsuri - The Chancery: 4.7, the top non-ad sushi place. Tap \"Matsuri - The Chancery\""),
            call("done", "evidence" to "x", "summary" to "x"), call("done", "evidence" to "x", "summary" to "x"),
        )
        Agent(phone, model, Gate(), noSettle.copy(model = "cheap", brainModel = "brain")).run("sushi from the highest rated")
        assertEquals(listOf("brain", "cheap", "brain"), model.models.take(3), "brief, then the hands ask, then the brain decides")
        assertTrue("your planning mind decided: Matsuri" in model.lastUserText, model.lastUserText)
        assertTrue(AgentTools.available(setOf("input.tap"), brain = true, briefed = true).none { it.name == "plan" })
        assertTrue(AgentTools.available(setOf("input.tap")).none { it.name == "decide" }, "no brain, no decide")
    }

    @Test
    fun goingBackAndForthHandsTheChoiceToTheBrain() = runTest {
        val phone = FakePhone(List(12) { i -> listOf("List" to (100.0 to 200.0), "row $i" to (100.0 to 300.0 + i), "info $i" to (100.0 to 340.0)) })
        fun scroll(d: String) = call("scroll", "direction" to d, "reason" to "Checking the top")
        val model = ScriptedModel(
            call("brief", "intent" to "x", "start" to "continue", "done_when" to "x", "steps" to listOf("a"), "report" to "x"),
            scroll("down"), scroll("up"), scroll("down"), scroll("up"),
            call("decision", "answer" to "Pick row 2: tap \"row 2\""),
            call("done", "evidence" to "x", "summary" to "x"), call("done", "evidence" to "x", "summary" to "x"),
        )
        Agent(phone, model, Gate(), noSettle.copy(model = "cheap", brainModel = "brain")).run("t")
        assertTrue("your planning mind decided: Pick row 2" in model.lastUserText, model.lastUserText)
    }

    @Test
    fun scrollingOneWayAgainAndAgainSuggestsSortOrFilter() = runTest {
        val phone = FakePhone(List(12) { i -> listOf("Results" to (100.0 to 200.0), "row $i" to (100.0 to 300.0)) })
        fun down() = call("scroll", "direction" to "down", "reason" to "Looking for the highest rated")
        val model = ScriptedModel(down(), down(), down(), call("done", "evidence" to "x", "summary" to "x"), call("done", "evidence" to "x", "summary" to "x"))
        Agent(phone, model, Gate(), noSettle).run("highest rated sushi")
        assertTrue("Sort, Filter or Search" in model.lastUserText, model.lastUserText)
    }

    @Test
    fun theStrongModelIsAskedAtMostThreeTimesAndOnlyWithBudget() = runTest {
        val phone = FakePhone(List(5) { listOf("Same" to (100.0 to 300.0)) })
        val plan = call("plan", "steps" to "1. x")
        val churn = Array(12) { plan }
        val model = ScriptedModel(*churn, call("done", "evidence" to "x", "summary" to "x", "success" to false))
        Agent(phone, model, Gate(), AgentConfig(model = "cheap", strongModel = "strong", settleMs = 0, maxSteps = 14)).run("t")
        // Plans are steps without progress: after six the run stops, having asked the strong model at most a few times.
        assertTrue(model.models.count { it == "strong" } in 1..3, model.models.toString())
        assertTrue(model.models.size <= 8, "stopped after six steps without progress: ${model.models}")

        val tight = ScriptedModel(*churn, call("done", "evidence" to "x", "summary" to "x", "success" to false))
        Agent(phone, tight, Gate(), AgentConfig(model = "cheap", strongModel = "strong", settleMs = 0, maxSteps = 14, maxCostUsd = 0.03)).run("t")
        assertEquals(0, tight.models.count { it == "strong" }, "a step-up would take too much of what is left")
    }

    @Test
    fun aChoiceIsOfferedAsOptionsWithTheRecommendation() = runTest {
        val phone = FakePhone(List(5) { listOf("Menu" to (100.0 to 300.0)) })
        val gate = Gate(answer = "California Roll · ₹325")
        val model = ScriptedModel(
            call(
                "ask_user", "question" to "Which sushi would you like?",
                "options" to listOf("Ebi Tempura · ₹345 · prawn", "California Roll · ₹325", "Veg Maki · ₹295"),
                "recommended" to "Ebi Tempura · ₹345 · prawn",
            ),
            call("done", "evidence" to "x", "summary" to "x"), call("done", "evidence" to "x", "summary" to "x"),
        )
        Agent(phone, model, gate, noSettle).run("add a sushi to my cart")
        assertEquals(listOf("Ebi Tempura · ₹345 · prawn", "California Roll · ₹325", "Veg Maki · ₹295") to "Ebi Tempura · ₹345 · prawn", gate.offered.single())
        assertTrue("California Roll" in model.lastUserText, "the pick reaches the model")
    }

    @Test
    fun theSameLookupTwiceIsNotRepeated() = runTest {
        val phone = FakePhone(List(5) { listOf("Menu" to (100.0 to 300.0)) })
        val memory = Remembers(mapOf("sushi on zomato" to listOf("Swish: track")))
        val ask = call("recall", "query" to "sushi on zomato")
        val model = ScriptedModel(ask, ask, call("done", "evidence" to "x", "summary" to "x"), call("done", "evidence" to "x", "summary" to "x"))
        Agent(phone, model, Gate(), noSettle, memory = memory).run("t")
        assertEquals(1, memory.asked.size)
        assertTrue("already looked this up" in model.lastUserText, model.lastUserText)
    }

    @Test
    fun theBrainBriefsFirstAndItsStepsBecomeThePlan() = runTest {
        val phone = FakePhone(List(5) { listOf("Chats" to (100.0 to 300.0)) })
        val plans = mutableListOf<List<PlanItem>>()
        val model = ScriptedModel(
            call(
                "brief", "intent" to "Log Beast Life protein in today's Snacks", "app" to "MyFitnessPal", "start" to "fresh",
                "done_when" to "Today's Snacks lists it", "steps" to listOf("Open today's diary", "Log it under Snacks"), "report" to "What was logged",
            ),
            call("open_app", "name" to "MyFitnessPal", "reason" to "Opening MyFitnessPal"),
            call("done", "evidence" to "x", "summary" to "x"), call("done", "evidence" to "x", "summary" to "x"),
        )
        Agent(phone, model, Gate(), noSettle.copy(model = "cheap", brainModel = "brain"), onPlan = { plans += it })
            .run("Beast Life protein in my snacks")
        assertEquals("brain", model.models.first())
        assertEquals(listOf("Open today's diary", "Log it under Snacks"), plans.first().map { it.text })
        assertTrue("THE BRIEF" in model.lastUserText && "Today's Snacks lists it" in model.lastUserText, model.lastUserText)
        assertEquals(Op.LaunchApp("MyFitnessPal", fresh = true), phone.actions.first(), "a new task starts the app fresh")
    }

    @Test
    fun withoutABriefTheTaskStillRuns() = runTest {
        val phone = FakePhone(List(5) { listOf("Home" to (100.0 to 300.0)) })
        val model = ScriptedModel(
            call("not_a_brief"),
            call("done", "evidence" to "x", "summary" to "ok"), call("done", "evidence" to "x", "summary" to "ok"),
        )
        val result = Agent(phone, model, Gate(), noSettle.copy(brainModel = "brain")).run("t")
        assertIs<AgentOutcome.Done>(result.outcome)
    }

    @Test
    fun aBackSwipeThatLeavesTheSamePageSaysToUseThePagesOwnButton() = runTest {
        // On the phone: Swiggy ignores the back swipe; only its rotating offer banner changed.
        fun page(offer: String) = listOf("Hunaaan" to (100.0 to 220.0), offer to (150.0 to 300.0), "Search for dishes" to (200.0 to 440.0),
            "Ratings 4.0+" to (220.0 to 500.0), "Bestseller" to (320.0 to 500.0), "Related to your search (42)" to (150.0 to 580.0),
            "Vegetable Bang Bang Sushi 4Pc" to (100.0 to 850.0), "ADD" to (300.0 to 910.0)) +
            // A real page carries ~30 pieces of text; one rotating banner is a small part of it.
            List(14) { "dish $it" to (100.0 + it to 600.0 + it * 10) }
        val phone = FakePhone(listOf(page("30% OFF UPTO 300"), page("10% OFF UPTO 50")) + List(5) { page("30% OFF UPTO 300") })
        val model = ScriptedModel(
            call("back", "reason" to "Back to the results"),
            call("done", "evidence" to "x", "summary" to "x"), call("done", "evidence" to "x", "summary" to "x"),
        )
        Agent(phone, model, Gate(), noSettle).run("t")
        assertTrue("the back swipe did nothing here" in model.lastUserText, model.lastUserText)
    }

    @Test
    fun aPointOutsideTheScreenshotIsRefusedAndAPairIsRead() {
        val g = AgentTools.Geometry(460, 1000, 402.0, 874.0)
        // On the phone: x 659 on a 460-pixel screenshot became a tap at 576 pt, off a 402 pt screen.
        val e = runCatching { AgentTools.toOp(call("tap", "x" to 659, "y" to 934), g) }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException && "outside the screenshot" in e.message!!, e?.message)
        // And "x": [228, 269] (a point) for a tap.
        val tap = AgentTools.toOp(call("tap", "x" to listOf(228, 269)), g) as Op.Tap
        assertEquals(228 * 402.0 / 460, tap.x)
        assertEquals(269 * 874.0 / 1000, tap.y)
    }

    @Test
    fun aModelThatNeverAnswersIsAskedAgainThenTheRunGoesOn() = runTest {
        val phone = FakePhone(List(5) { listOf("Home" to (100.0 to 300.0)) })
        var calls = 0
        val slowThenFine = LlmClient { _, _, _ ->
            calls++
            if (calls == 1) kotlinx.coroutines.awaitCancellation()
            ChatResult(ToolCall("done", buildJsonObject { put("evidence", "x"); put("summary", "ok") }), "", Usage(0, 0, 0.0))
        }
        val result = Agent(phone, slowThenFine, Gate(), noSettle).run("t")
        assertIs<AgentOutcome.Done>(result.outcome)
        assertEquals(2, calls, "the silent first try was given up after the time limit")
    }

    @Test
    fun scrollingIsNotCheckingAnAdd() = runTest {
        // On the phone: a scroll down a long menu counted as leaving the page; a second dish was added unchecked.
        fun menu(i: Int) = List(10) { k -> "dish ${i * 10 + k}" to (100.0 to 200.0 + k * 60) }
        val phone = FakePhone(listOf(menu(0), menu(0), menu(1), menu(1)) + List(5) { menu(1) })
        val add = call("tap", "x" to 411, "y" to 340, "reason" to "Adding it to the cart")
        val another = call("tap", "x" to 411, "y" to 600, "reason" to "Adding another dish")
        val model = ScriptedModel(
            add, call("scroll", "direction" to "down", "reason" to "Checking the cart"), another,
            call("done", "evidence" to "x", "summary" to "x"), call("done", "evidence" to "x", "summary" to "x"),
        )
        Agent(phone, model, Gate(), noSettle).run("add a sushi")
        assertEquals(1, phone.actions.count { it is Op.Tap }, "the second add waits: a scroll is not a check")
    }

    @Test
    fun aCartCountAppearingIsProof() {
        assertEquals("1 item", Changes.proof(listOf("Yuki", "1 item")))
        assertEquals("View Cart", Changes.proof(listOf("View Cart")))
        assertEquals(null, Changes.proof(listOf("Items", "Push Pop Sushi")))
    }

    @Test
    fun afterARiskyTapIsAllowedTheAppIsReopenedBeforeTheTap() = runTest {
        // On the phone: asking brought Mode forward and "Book now" was tapped on Mode's own screen.
        val phone = FakePhone(List(6) { listOf("Book now" to (300.0 to 800.0), "Auto 163" to (100.0 to 700.0)) })
        val model = ScriptedModel(
            call("open_app", "name" to "Rapido", "reason" to "Opening Rapido"),
            call("tap", "x" to 373, "y" to 915, "reason" to "Booking the Auto ride"),
            call("done", "evidence" to "x", "summary" to "x"), call("done", "evidence" to "x", "summary" to "x"),
        )
        Agent(phone, model, Gate(allow = true), noSettle).run("book a ride")
        val afterAsk = phone.actions.drop(1)
        assertEquals(Op.LaunchApp("bundle.Rapido"), afterAsk.first(), "back to the app first (by its bundle id)")
        assertIs<Op.Tap>(afterAsk[1])
    }

    @Test
    fun aSecondConfirmationDoesNotRepeatWhatWasTypedBefore() = runTest {
        // On the phone: "Confirm pickup" was asked with "It typed: Bellandur", already shown for "Book now".
        val search = listOf("Drop location" to (200.0 to 200.0), "Recent places" to (120.0 to 300.0))
        val rides = listOf("Book now" to (300.0 to 800.0), "Auto 163" to (100.0 to 500.0), "Bike 99" to (100.0 to 560.0), "Cab 250" to (100.0 to 620.0))
        val pickup = listOf("Confirm booking" to (300.0 to 600.0), "Pickup point" to (120.0 to 300.0), "Drag map to move" to (200.0 to 350.0), "Select a pickup point" to (200.0 to 400.0))
        val phone = FakePhone(listOf(search, rides, pickup, pickup, pickup))
        val gate = Gate(allow = true)
        val model = ScriptedModel(
            call("type_text", "text" to "Bellandur", "reason" to "Entering the drop"),
            call("tap", "x" to 373, "y" to 915, "reason" to "Booking the ride"),
            call("tap", "x" to 373, "y" to 686, "reason" to "Confirming the booking"),
            call("done", "evidence" to "x", "summary" to "x"), call("done", "evidence" to "x", "summary" to "x"),
        )
        Agent(phone, model, gate, noSettle).run("book a ride")
        assertEquals(2, gate.asked.size, gate.asked.toString())
        assertTrue("Bellandur" in gate.asked[0])
        assertTrue("Bellandur" !in gate.asked[1], gate.asked[1])
    }

    @Test
    fun withTheKeyboardUpAListedEntryIsPreferredToTyping() = runTest {
        val phone = FakePhone(List(5) { searchWithKeyboard })
        val model = ScriptedModel(call("done", "evidence" to "x", "summary" to "x"))
        Agent(phone, model, Gate(), noSettle).run("search sushi")
        assertTrue("already listed below (a recent or suggested entry): then tap that" in model.lastUserText, model.lastUserText)
    }

    // A focused search field with the keyboard up, as on the phone (run 14-11-27): the keys are single letters, low.
    private val keyboard = "QWERTYUIOPASDFGHJKL".mapIndexed { i, c -> c.toString() to (20.0 + (i % 10) * 38 to 640.0 + (i / 10) * 55) }
    private val searchWithKeyboard = listOf("Try 'Pizza'" to (200.0 to 134.0), "Popular searches" to (120.0 to 260.0)) + keyboard

    @Test
    fun theKeyboardIsNamedAndItsKeysLeftOut() = runTest {
        val phone = FakePhone(List(5) { searchWithKeyboard })
        val model = ScriptedModel(call("done", "evidence" to "x", "summary" to "x"))
        Agent(phone, model, Gate(), noSettle).run("search sushi")
        assertTrue("KEYBOARD IS UP" in model.lastUserText, model.lastUserText)
        assertTrue("\"Q\" (" !in model.lastUserText, "keys are not listed as text")
        assertTrue("Try 'Pizza'" in model.lastUserText)
    }

    @Test
    fun tappingTheFocusedFieldAgainIsRefused() = runTest {
        val phone = FakePhone(List(8) { searchWithKeyboard })
        val focus = call("tap", "x" to 249, "y" to 176, "reason" to "Focus search field")
        val model = ScriptedModel(focus, focus, call("done", "evidence" to "x", "summary" to "x"), call("done", "evidence" to "x", "summary" to "x"))
        Agent(phone, model, Gate(), noSettle).run("search sushi")
        assertEquals(1, phone.actions.count { it is Op.Tap }, "the second tap on the focused field is refused")
        assertTrue("keyboard is already up" in model.lastUserText, model.lastUserText)
    }

    @Test
    fun typeTextFocusesTheFieldWhenTheKeyboardIsDownAndCanSubmit() = runTest {
        val phone = FakePhone(List(6) { listOf("Search for 'Biryani'" to (200.0 to 268.0)) })
        val model = ScriptedModel(
            call("type_text", "text" to "sushi", "x" to 249, "y" to 307, "submit" to true, "reason" to "Searching for sushi"),
            call("done", "evidence" to "x", "summary" to "x"), call("done", "evidence" to "x", "summary" to "x"),
        )
        Agent(phone, model, Gate(), noSettle).run("search sushi")
        assertIs<Op.Tap>(phone.actions[0], "focused first")
        assertEquals(Op.TypeText("sushi"), phone.actions[1])
        assertEquals(Op.PressKey("return"), phone.actions[2])
    }

    @Test
    fun withABrainThreeStepsWithoutProgressGetADecisionNotAStrongStep() = runTest {
        val phone = FakePhone(List(8) { listOf("Results" to (100.0 to 300.0)) })
        fun wait() = call("wait", "seconds" to 1, "reason" to "loading")
        val model = ScriptedModel(
            call("brief", "intent" to "x", "start" to "continue", "done_when" to "x", "steps" to listOf("a"), "report" to "x"),
            wait(), wait(), wait(),
            call("decision", "answer" to "Open the first result: tap \"Results\""),
            call("done", "evidence" to "x", "summary" to "x"), call("done", "evidence" to "x", "summary" to "x"),
        )
        Agent(phone, model, Gate(), noSettle.copy(model = "cheap", strongModel = "strong", brainModel = "brain")).run("t")
        assertTrue("your planning mind decided: Open the first result" in model.lastUserText, model.lastUserText)
        assertTrue("strong" !in model.models, "a decision, not a strong step: ${model.models}")
    }

    @Test
    fun anAddThatVisiblyDidNothingMayBeTriedOnceMore() = runTest {
        // On the phone: a missed tap held every retry back, nine times. The page stayed exactly the same.
        val menu = List(12) { "dish $it" to (100.0 to 200.0 + it * 50) }
        val phone = FakePhone(List(10) { menu })
        val add = call("tap", "x" to 411, "y" to 340, "reason" to "Adding it to the cart")
        val model = ScriptedModel(add, add, add, call("done", "evidence" to "x", "summary" to "x"), call("done", "evidence" to "x", "summary" to "x"))
        Agent(phone, model, Gate(), noSettle).run("add a dish")
        // First try; it changed nothing, so a second; a third is held for a check.
        assertEquals(2, phone.actions.count { it is Op.Tap })
        assertTrue("tried twice without any sign" in model.lastUserText, model.lastUserText)
    }

    @Test
    fun aPlusTappedToCheckIsAnsweredNotTapped() = runTest {
        val phone = FakePhone(List(10) { listOf("十" to (330.0 to 272.0), "Oats" to (120.0 to 272.0)) })
        val model = ScriptedModel(
            call("tap", "x" to 411, "y" to 340, "reason" to "Check the item is logged"),
            call("done", "evidence" to "x", "summary" to "x"), call("done", "evidence" to "x", "summary" to "x"),
        )
        Agent(phone, model, Gate(), noSettle).run("add oats")
        assertEquals(0, phone.actions.count { it is Op.Tap })
        assertTrue("this button adds" in model.lastUserText, model.lastUserText)
    }

    @Test
    fun aTapOnAnIconIsDescribedByItsRow() = runTest {
        val phone = FakePhone(List(10) { listOf("Dark chocolate oats - high protein" to (120.0 to 272.0), "392 cal, 100 gram" to (120.0 to 291.0)) })
        val model = ScriptedModel(
            call("tap", "x" to 411, "y" to 340, "reason" to "Opening it"),
            call("done", "evidence" to "x", "summary" to "x"), call("done", "evidence" to "x", "summary" to "x"),
        )
        Agent(phone, model, Gate(), noSettle).run("t")
        assertTrue("tap beside \"Dark chocolate oats - high protein · 392 cal, 100 gram\"" in model.lastUserText, model.lastUserText)
    }

    @Test
    fun ordinaryTapsAreNeverHeldBack() = runTest {
        val phone = FakePhone(List(10) { listOf("Same" to (200.0 to 300.0)) })
        val model = ScriptedModel(
            call("tap", "x" to 100, "y" to 600, "reason" to "Opening Settings"),
            call("tap", "x" to 100, "y" to 600, "reason" to "Opening Wi-Fi"),
            call("done", "evidence" to "on screen", "summary" to "x"), call("done", "evidence" to "on screen", "summary" to "x"),
        )
        Agent(phone, model, Gate(), noSettle).run("open wifi")
        assertEquals(2, phone.actions.count { it is Op.Tap })
    }

    @Test
    fun whatAppearsRightAfterAnAddIsShownToTheModelAsProof() = runTest {
        val phone = FakePhone(listOf(
            listOf("Chocolate oats" to (200.0 to 136.0)),
            listOf("Chocolate oats" to (200.0 to 136.0), "Added to Dinner" to (200.0 to 700.0)),
        ))
        val model = ScriptedModel(
            call("tap", "x" to 411, "y" to 273, "reason" to "Adding it to Dinner"),
            call("done", "evidence" to "Added to Dinner", "summary" to "x"),
        )
        Agent(phone, model, Gate(), noSettle).run("add oats")
        assertTrue("right after it the screen showed: \"Added to Dinner\"" in model.lastUserText, model.lastUserText)
    }

    @Test
    fun aTickingClockInTheStatusBarIsNotAChange() = runTest {
        // The Dynamic Island's timer changes every second; the page below does not.
        val phone = FakePhone(List(10) { i -> listOf("0:${10 + i}" to (330.0 to 20.0), "Wi-Fi" to (200.0 to 300.0)) })
        val model = ScriptedModel(call("tap", "x" to 100, "y" to 600, "reason" to "Opening Wi-Fi"), call("done", "evidence" to "on screen", "summary" to "x"))
        Agent(phone, model, Gate(), noSettle).run("open wifi")
        assertTrue("screen did NOT change" in model.lastUserText, model.lastUserText)
    }

    @Test
    fun afterAnAddADifferentAddOnTheSameScreenAlsoWaits() = runTest {
        val phone = FakePhone(List(10) { listOf("Results" to (200.0 to 100.0)) })
        fun plus(y: Int) = call("tap", "x" to 411, "y" to y, "reason" to "Adding it")
        val model = ScriptedModel(plus(435), plus(355), call("done", "evidence" to "on screen", "summary" to "x"), call("done", "evidence" to "on screen", "summary" to "x"))
        Agent(phone, model, Gate(), noSettle).run("add oats to dinner")
        assertEquals(1, phone.actions.count { it is Op.Tap }, "the second row's + waits until the first was checked")
    }

    @Test
    fun progressShownToTheUserNeverNamesTheModel() = runTest {
        val phone = FakePhone(List(10) { listOf("Same" to (10.0 to 10.0)) })
        val statuses = mutableListOf<String>()
        val model = ScriptedModel(call("done", "evidence" to "on screen", "summary" to "x"))
        Agent(phone, model, Gate(), noSettle.copy(model = "anthropic/claude-haiku-5.5"), onStatus = { statuses += it }).run("t")
        assertTrue(statuses.none { "/" in it || "Step" in it }, statuses.toString())
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
        // Each scroll brings new lines (a scroll that brings nothing new is the end of the list).
        val phone = FakePhone(List(30) { i -> listOf("s$i" to (100.0 to 300.0), "t$i" to (100.0 to 340.0)) })
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
        // Plans make no progress: after three, the strong model takes the next decision.
        assertEquals(listOf("cheap", "cheap", "cheap", "strong"), model.models)
        assertTrue("not saved: you just planned" in model.lastUserText, model.lastUserText)
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
        override fun recall(query: String, apps: List<String>) = recall(query)
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

    @Test
    fun aPausedTaskWaitsBeforeItsNextStepAndShowsEachScreen() = runTest {
        val phone = FakePhone(List(5) { i -> listOf("s$i" to (1.0 to 1.0)) })
        val gate = PauseGate()
        val screens = mutableListOf<ByteArray>()
        val statuses = mutableListOf<String>()
        val model = ScriptedModel(call("home", "reason" to "x"), call("done", "evidence" to "x", "summary" to "ok"))
        gate.pause()
        var result: AgentResult? = null
        val job = launch {
            result = Agent(phone, model, Gate(), noSettle, onStatus = { statuses += it }, pause = gate, onScreen = { screens += it }).run("t")
        }
        testScheduler.runCurrent()
        assertEquals(listOf("Paused"), statuses, "nothing happens while paused")
        assertTrue(model.models.isEmpty())
        gate.resume()
        job.join()
        assertIs<AgentOutcome.Done>(result!!.outcome)
        assertEquals(2, screens.size)
    }
}
