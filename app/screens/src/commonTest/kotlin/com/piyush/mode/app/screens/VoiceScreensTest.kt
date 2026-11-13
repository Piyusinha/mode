package com.piyush.mode.app.screens

import com.piyush.mode.app.screens.settings.ListenPresenter
import com.piyush.mode.app.screens.settings.TalkTaskCard
import com.piyush.mode.app.screens.settings.VoicePickerPresenter
import com.piyush.mode.feature.settings.domain.VoiceKind
import com.piyush.mode.feature.settings.voice.DeviceVoice
import com.piyush.mode.feature.settings.voice.VoiceOption
import com.piyush.mode.feature.settings.voice.Voices
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VoiceScreensTest {
    @Test
    fun choosingAVoiceKeepsItAndPutsItsProviderFirst() = runTest {
        val f = Fixture(this)
        DeviceVoice.voices = {
            listOf(VoiceOption("ava", "Ava", "Female · English (US) · Premium", "English (US)"), VoiceOption("rishi", "Rishi", "Male · English (India)", "English (India)"))
        }
        val p = VoicePickerPresenter(backgroundScope, f.settings, Voices(f.settings, HttpClient(MockEngine { error("offline") })), VoiceKind.Device)
        val listed = p.state.awaitState { !it.loading }
        assertEquals("ava", listed.selected, "the first voice until one is picked")
        assertEquals(listOf("English (US)", "English (India)"), listed.groups)
        p.filter("English (India)")
        assertEquals(listOf("rishi"), p.state.awaitState { it.group != null }.voices.map { it.id })
        p.select("rishi")
        p.state.awaitState { it.selected == "rishi" }
        p.use()
        assertEquals("Rishi", f.settings.settings.value.voices["apple"]?.name)
        assertEquals("apple", f.settings.settings.value.speechOrder.first())
        DeviceVoice.voices = { emptyList() }
    }

    @Test
    fun previewKeepsAVoiceFromTheFullCatalogWithoutLosingTheRecommendation() {
        val all = listOf(VoiceOption("a", "Adam"), VoiceOption("b", "Aria"), VoiceOption("r", "Rachel"), VoiceOption("n", "Nova"))
        val previews = Voices.previewVoices(VoiceKind.ElevenLabs, all, "n")
        assertEquals(listOf("r", "a", "n"), previews.map { it.id })
        assertEquals(Voices.OUR_PICK, previews.first().tag)
        assertEquals(listOf("a", "b", "r", "n"), all.map { it.id }, "previewing never truncates or changes the full catalog")
        assertEquals(listOf("n"), Voices.previewVoices(VoiceKind.ElevenLabs, all, "n", limit = 1).map { it.id })
    }

    @Test
    fun modeAnswersLikeAnAssistantWithoutReadingTheTaskBack() {
        val line = ListenPresenter.replyLine("can you turn on dark mode please")
        assertTrue(line in listOf("Sure, let me do that for you.", "On it.", "Sure, I'll take care of it.", "Got it, doing it now."), line)
        assertTrue("dark mode" !in line.lowercase(), "never the task read back")
        assertEquals(line, ListenPresenter.replyLine("can you turn on dark mode please"), "the same task, the same line")
        assertEquals("Let me check.", ListenPresenter.replyLine("what's on my calendar today?"))
    }

    @Test
    fun modeGreetsByNameAndTimeOfDay() {
        assertEquals("Hey Alex, good evening. What do you want me to do?", ListenPresenter.greetingLine("Alex Sinha", 19))
        assertEquals("Hey Alex, good morning. What do you want me to do?", ListenPresenter.greetingLine("Alex", 8))
        assertEquals("Hey, good afternoon. What do you want me to do?", ListenPresenter.greetingLine(null, 14))
        assertEquals("Hey Alex. What do you want me to do?", ListenPresenter.greetingLine("Alex", 1), "late at night: no greeting for the hour")
    }

    @Test
    fun aKeyIsSavedOnlyOnceItsProviderAcceptsItAndRemovingItHandsBackToApple() = runTest {
        val f = Fixture(this)
        val http = HttpClient(MockEngine { request ->
            if (request.headers["Authorization"] == "Token good-deepgram-key") respond("{}")
            else respond("no", io.ktor.http.HttpStatusCode.Unauthorized)
        })
        val p = com.piyush.mode.app.screens.settings.VoicePresenter(backgroundScope, f.settings, Voices(f.settings, http), hearing = true)

        p.checkAndSaveKey("deepgram", "bad-key-1234")
        p.state.awaitState { it.keyCheck?.state == "failed" }
        assertEquals("KeyRejected", p.state.value.keyCheck?.trouble)
        assertEquals(null, f.settings.voiceKey(VoiceKind.Deepgram))

        p.checkAndSaveKey("deepgram", "good-deepgram-key")
        val saved = p.state.awaitState { it.keyCheck?.state == "saved" }
        assertEquals("go•••••ey", saved.rows.first { it.id == "deepgram" }.masked)
        assertTrue(saved.rows.first { it.id == "deepgram" }.first)

        p.remove("deepgram")
        val after = p.state.awaitState { s -> s.rows.firstOrNull { it.id == "apple" }?.first == true }
        assertEquals(null, after.rows.first { it.id == "deepgram" }.masked)
    }

    @Test
    fun aTaskSpokenFromAChatsMicGoesIntoThatChat() = runTest {
        val f = Fixture(backgroundScope, model = NeverAnswers)
        val chat = f.conversations.create("Dark Mode")
        val talk = ListenPresenter(backgroundScope, Voices(f.settings, HttpClient(MockEngine { error("offline") })), f.queue, f.settings, f.runner,
            conversationId = chat.id)

        talk.submitTyped("Turn it back on")
        talk.state.awaitState { it.sent }

        val task = f.tasks.recent(10).first().first { t -> t.text == "Turn it back on" }
        assertEquals(chat.id, task.conversationId)
    }

    /** Records spans and events, to check what a talking session leaves in the trace. */
    private class Recorded : com.piyush.mode.core.telemetry.Telemetry {
        val spans = mutableListOf<Pair<com.piyush.mode.core.telemetry.Operation, MutableMap<com.piyush.mode.core.telemetry.Field, String>>>()
        val events = mutableListOf<com.piyush.mode.core.telemetry.Event>()
        var ended = 0
        override fun start(operation: com.piyush.mode.core.telemetry.Operation, parent: com.piyush.mode.core.telemetry.TraceContext?,
                           fields: com.piyush.mode.core.telemetry.Fields): com.piyush.mode.core.telemetry.TraceSpan {
            val f = fields.toMutableMap(); spans += operation to f
            return object : com.piyush.mode.core.telemetry.TraceSpan {
                override val context: com.piyush.mode.core.telemetry.TraceContext? = null
                override fun fields(fields: com.piyush.mode.core.telemetry.Fields) { f.putAll(fields) }
                override fun event(event: com.piyush.mode.core.telemetry.Event, fields: com.piyush.mode.core.telemetry.Fields) { events += event }
                override fun end() { ended++ }
            }
        }
    }

    @Test
    fun aTalkingSessionIsOneTraceFromHowItOpenedToHowItEnded() = runTest {
        val f = Fixture(backgroundScope, model = NeverAnswers)
        val traces = Recorded()
        val talk = ListenPresenter(backgroundScope, Voices(f.settings, HttpClient(MockEngine { error("offline") })), f.queue, f.settings, f.runner,
            telemetry = traces, entry = "chat_mic")

        talk.submitTyped("Turn on dark mode")
        talk.state.awaitState { it.sent }
        talk.dismiss()

        val (_, fields) = traces.spans.single { it.first == com.piyush.mode.core.telemetry.Operation.VoiceSession }
        assertEquals("chat_mic", fields[com.piyush.mode.core.telemetry.Field.Entry])
        assertEquals("TaskTyped", fields[com.piyush.mode.core.telemetry.Field.Outcome])
        assertTrue(com.piyush.mode.core.telemetry.Event.TaskSent in traces.events)
        assertTrue(traces.ended >= 1)
    }

    @Test
    fun withoutTheMicrophoneTalkingModeAsksForItInsteadOfRecordingSilence() = runTest {
        val f = Fixture(backgroundScope)
        f.settings.setVoiceKey(VoiceKind.Soniox, "soniox-key")
        f.settings.update { it.copy(hearingOrder = listOf("soniox", "apple")) }
        val before = DeviceVoice.hearingState
        DeviceVoice.hearingState = { "permission" }
        try {
            val traces = Recorded()
            val talk = ListenPresenter(backgroundScope, Voices(f.settings, HttpClient(MockEngine { error("offline") })), f.queue, f.settings, f.runner,
                telemetry = traces, entry = "siri")
            talk.begin()
            val s = talk.state.awaitState { it.phase == com.piyush.mode.app.screens.settings.TalkPhase.NeedsMic }
            assertEquals(false, s.micDenied)
            assertTrue(com.piyush.mode.core.telemetry.Event.MicBlocked in traces.events)
        } finally {
            DeviceVoice.hearingState = before
        }
    }

    @Test
    fun theTaskStartsAtOnceAndModeSaysTheAIsOwnLineWhenItArrives() = runTest {
        val f = Fixture(backgroundScope, model = NeverAnswers)
        val slowButPersonal = com.piyush.mode.llm.LlmClient { _, _, _ ->
            kotlinx.coroutines.delay(2_000)
            com.piyush.mode.llm.ChatResult(
                com.piyush.mode.llm.ToolCall("say", kotlinx.serialization.json.buildJsonObject {
                    put("line", kotlinx.serialization.json.JsonPrimitive("On it, Alex. Dark Mode, coming right up."))
                }), "", com.piyush.mode.llm.Usage(10, 5, 0.0))
        }
        val ack = com.piyush.mode.feature.memory.data.Acknowledger(slowButPersonal, null, model = { "fast@low" to com.piyush.mode.llm.ModelProvider.ChatGpt })
        val talk = ListenPresenter(backgroundScope, Voices(f.settings, HttpClient(MockEngine { error("offline") })), f.queue, f.settings, f.runner,
            acknowledger = ack)

        talk.submitTyped("Turn on dark mode")
        val sent = talk.state.awaitState { it.sent }
        assertEquals("", sent.reply)   // the task went before the line was written
        // The session carries on after the task, so the reply is checked where it stays: in the conversation.
        talk.state.awaitState { s -> s.turns.any { !it.mine && it.text == "On it, Alex. Dark Mode, coming right up." } }
    }

    @Test
    fun startedByVoiceModeGreetsInTheAIsWordsWithWhatsComingUp() = runTest {
        val f = Fixture(backgroundScope)
        var asked = ""
        val greeting = "Dinner with Priya's at 7. Want me to text her you're on your way?"
        val model = com.piyush.mode.llm.LlmClient { _, messages, _ ->
            asked = messages.last().text
            kotlinx.coroutines.delay(1_500) // the AI takes its time; Mode's own hello doesn't wait for it
            com.piyush.mode.llm.ChatResult(
                com.piyush.mode.llm.ToolCall("say", kotlinx.serialization.json.buildJsonObject {
                    put("line", kotlinx.serialization.json.JsonPrimitive(greeting))
                }), "", com.piyush.mode.llm.Usage(10, 5, 0.0))
        }
        val greeter = com.piyush.mode.feature.memory.data.Greeter(model, null, model = { "fast@low" to com.piyush.mode.llm.ModelProvider.ChatGpt })
        val day = com.piyush.mode.feature.memory.data.DayContext("Thursday 6 November, 18:20", events = listOf("Thu 6 Nov 19:00–21:00 · Dinner with Priya"))
        val before = DeviceVoice.hearingState
        DeviceVoice.hearingState = { "ready" }
        try {
            val talk = ListenPresenter(backgroundScope, Voices(f.settings, HttpClient(MockEngine { error("offline") })), f.queue, f.settings, f.runner,
                entry = "siri", greeter = greeter, day = { day })
            talk.begin()
            val hello = talk.state.awaitState { it.greeting.isNotEmpty() }
            assertTrue(hello.greeting.startsWith("Hey") && hello.greeting.endsWith("."), "Mode's own hello comes first: ${hello.greeting}")
            assertFalse("Dinner" in hello.greeting)
            val full = talk.state.awaitState { it.greeting.endsWith(greeting) }
            assertEquals("${hello.greeting} $greeting", full.greeting)
            assertEquals(1, full.turns.count { !it.mine }, "the AI's part joins the hello on screen, not a second line")
            assertTrue("Dinner with Priya" in asked)
        } finally {
            DeviceVoice.hearingState = before
        }
    }

    @Test
    fun aTaskGivenWhileTheAIStillWritesTheGreetingStartsAtOnce() = runTest {
        val f = Fixture(backgroundScope, model = NeverAnswers)
        val slow = com.piyush.mode.llm.LlmClient { _, _, _ -> kotlinx.coroutines.awaitCancellation() }
        val greeter = com.piyush.mode.feature.memory.data.Greeter(slow, null, model = { "fast@low" to com.piyush.mode.llm.ModelProvider.ChatGpt })
        val talk = ListenPresenter(backgroundScope, Voices(f.settings, HttpClient(MockEngine { error("offline") })), f.queue, f.settings, f.runner,
            entry = "siri", greeter = greeter, day = { com.piyush.mode.feature.memory.data.DayContext("Monday 10 November, 21:00") })
        talk.begin()
        val hello = talk.state.awaitState { it.greeting.isNotEmpty() }.greeting
        talk.submitTyped("Turn on dark mode")
        val sent = talk.state.awaitState { it.sent }
        assertEquals("Turn on dark mode", sent.task)
        assertEquals(hello, sent.greeting, "the greeting stays as Mode's hello; the AI's late line is dropped")
        assertTrue(sent.turns.none { ListenPresenter.QUESTION_LINE in it.text }, "no question after the task started")
    }

    @Test
    fun withoutTheAIModeSaysItsOwnGreeting() = runTest {
        val f = Fixture(backgroundScope)
        f.settings.update { it.copy(userName = "Alex") }
        val before = DeviceVoice.hearingState
        DeviceVoice.hearingState = { "ready" }
        try {
            val talk = ListenPresenter(backgroundScope, Voices(f.settings, HttpClient(MockEngine { error("offline") })), f.queue, f.settings, f.runner,
                entry = "siri", day = { com.piyush.mode.feature.memory.data.DayContext("Thursday 6 November, 18:20") })
            talk.begin()
            val s = talk.state.awaitState { it.greeting.isNotEmpty() }
            assertTrue(s.greeting.startsWith("Hey Alex"), s.greeting)
        } finally {
            DeviceVoice.hearingState = before
        }
    }

    private fun nextLines(vararg lines: String, offer: String? = null): com.piyush.mode.feature.memory.data.Greeter {
        val queue = ArrayDeque(lines.toList())
        var offered = false
        val model = com.piyush.mode.llm.LlmClient { _, _, _ ->
            com.piyush.mode.llm.ChatResult(
                com.piyush.mode.llm.ToolCall("say", kotlinx.serialization.json.buildJsonObject {
                    put("line", kotlinx.serialization.json.JsonPrimitive(queue.removeFirstOrNull() ?: "Anything else?"))
                    if (offer != null && !offered) {
                        offered = true
                        put("offer_task", kotlinx.serialization.json.JsonPrimitive(offer))
                        put("yes_label", kotlinx.serialization.json.JsonPrimitive("Yes, look there"))
                        put("no_label", kotlinx.serialization.json.JsonPrimitive("Skip it"))
                    }
                }), "", com.piyush.mode.llm.Usage(10, 5, 0.0))
        }
        return com.piyush.mode.feature.memory.data.Greeter(model, null, model = { "fast@low" to com.piyush.mode.llm.ModelProvider.ChatGpt })
    }

    @Test
    fun startedByVoiceTheConversationCarriesOnAfterATaskInTheSameChat() = runTest {
        val f = Fixture(backgroundScope)
        val talk = ListenPresenter(backgroundScope, Voices(f.settings, HttpClient(MockEngine { error("offline") })), f.queue, f.settings, f.runner,
            entry = "siri", greeter = nextLines("Anything else, Alex?"), day = { com.piyush.mode.feature.memory.data.DayContext("Thursday 6 November, 18:20") })

        talk.submitTyped("Turn on dark mode")
        val next = talk.state.awaitState { it.greeting == "Anything else, Alex?" }
        assertEquals(listOf("Turn on dark mode"), next.doneSoFar)
        assertFalse(next.finished, "the conversation goes on after the task")
        // One conversation on one screen: the request, how it went, and what Mode asks next, in order.
        val lines = next.turns
        assertEquals("Turn on dark mode", lines.first { it.mine }.text)
        assertEquals("Anything else, Alex?", lines.last().text)
        val card = lines.single { it.kind == com.piyush.mode.app.screens.settings.TalkTurn.Kind.Task }.card!!
        assertTrue(card.state != com.piyush.mode.app.screens.settings.TalkTaskCard.State.Working, "the task's card ends with its result")
        assertTrue(card.headline.isNotBlank())
        assertTrue(lines.indexOfFirst { it.mine } < lines.indexOfLast { !it.mine })

        talk.submitTyped("Now turn off Wi-Fi")
        talk.state.awaitState { it.doneSoFar.size == 2 }
        val chats = f.tasks.recent(10).first().map { it.conversationId }.distinct()
        assertEquals(1, chats.size, "both tasks in one chat")

        talk.submitTyped("That's all, thanks")
        assertTrue(talk.state.awaitState { it.finished }.finished)
    }

    @Test
    fun androidsAssistantButtonStartsTheSameConversation() = runTest {
        val f = Fixture(backgroundScope)
        val talk = ListenPresenter(backgroundScope, Voices(f.settings, HttpClient(MockEngine { error("offline") })), f.queue, f.settings, f.runner,
            entry = "assistant", greeter = nextLines("Anything else?"))
        talk.submitTyped("Turn on dark mode")
        val next = talk.state.awaitState { it.greeting == "Anything else?" }
        assertFalse(next.finished, "holding power, like Hey Siri, keeps the conversation going")
    }

    @Test
    fun theOverlayTalksTheWholeWayEvenWithoutTheAi() = runTest {
        val f = Fixture(backgroundScope, com.piyush.mode.testing.ScriptedModel(
            com.piyush.mode.testing.call("done", "evidence" to "x", "summary" to "Dark mode is on."),
        ))
        // No greeter (no AI line): Mode still asks what's next, and keeps listening.
        val talk = ListenPresenter(backgroundScope, Voices(f.settings, HttpClient(MockEngine { error("offline") })), f.queue, f.settings, f.runner,
            entry = ListenPresenter.OVERLAY)
        talk.submitTyped("Turn on dark mode")
        val next = talk.state.awaitState { it.greeting == ListenPresenter.ANYTHING_ELSE }
        assertFalse(next.finished, "the overlay is a conversation")
        val task = f.tasks.recent(10).first().first()
        assertEquals(com.piyush.mode.feature.tasks.domain.TaskSource.Voice, task.source, "typed in the overlay, its questions are asked out loud")
    }

    @Test
    fun aTaskThatFailsIsExplainedAndTryAgainRunsItAgain() = runTest {
        val f = Fixture(backgroundScope, com.piyush.mode.llm.LlmClient { _, _, _ ->
            throw com.piyush.mode.llm.LlmFailed("model: HTTP 401: nope", status = 401)
        })
        val talk = ListenPresenter(backgroundScope, Voices(f.settings, HttpClient(MockEngine { error("offline") })), f.queue, f.settings, f.runner,
            entry = ListenPresenter.OVERLAY)
        talk.submitTyped("Order my usual coffee")
        val offer = talk.state.awaitState { it.greeting.startsWith(ListenPresenter.DIDNT_WORK) }
        assertTrue(offer.greeting.endsWith(ListenPresenter.TRY_AGAIN_QUESTION), offer.greeting)
        assertFalse("HTTP" in offer.greeting || "401" in offer.greeting, "plain words, never codes")
        assertEquals(ListenPresenter.TRY_AGAIN, offer.turns.last().offer?.yes)

        talk.pick(ListenPresenter.TRY_AGAIN)
        val tries = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            kotlinx.coroutines.withTimeout(5_000) { f.tasks.recent(10).first { list -> list.size >= 2 } }
        }
        assertEquals(listOf("Order my usual coffee", "Order my usual coffee"), tries.map { it.text })
    }

    @Test
    fun thatsAllEndsTheConversationEvenBeforeATask() = runTest {
        val f = Fixture(backgroundScope)
        val talk = ListenPresenter(backgroundScope, Voices(f.settings, HttpClient(MockEngine { error("offline") })), f.queue, f.settings, f.runner,
            entry = ListenPresenter.OVERLAY)
        talk.submitTyped("That's all, thanks")
        assertTrue(talk.state.awaitState { it.finished }.finished)
        assertTrue(f.tasks.recent(10).first().isEmpty(), "nothing was started")
    }

    @Test
    fun aSessionFromAChatCarriesOnAfterItsTask() = runTest {
        val f = Fixture(backgroundScope)
        val talk = ListenPresenter(backgroundScope, Voices(f.settings, HttpClient(MockEngine { error("offline") })), f.queue, f.settings, f.runner,
            entry = "chat_mic", greeter = nextLines("Anything else?"), pausedEndMs = Long.MAX_VALUE)
        talk.submitTyped("Turn on dark mode")
        val next = talk.state.awaitState { it.doneSoFar.isNotEmpty() }
        assertFalse(next.finished, "the session stays open until it is ended")
    }

    @Test
    fun aWrapUpIsTheWholeReplyNotAWordInsideATask() {
        listOf("That's all", "thats all", "That’s all.", "thanks", "No thank you.", "bye Mo", "nahi bas", "that's it, thanks", "Im done").forEach {
            assertTrue(ListenPresenter.isWrapUp(it), it)
        }
        listOf("Text Priya thanks for dinner", "No, order the large one", "Stop the music on Spotify please now").forEach {
            assertFalse(ListenPresenter.isWrapUp(it), it)
        }
    }

    @Test
    fun yesToModesOfferRunsTheTaskItOffered() = runTest {
        val f = Fixture(backgroundScope)
        val talk = ListenPresenter(backgroundScope, Voices(f.settings, HttpClient(MockEngine { error("offline") })), f.queue, f.settings, f.runner,
            entry = "siri", greeter = nextLines("Want me to look in your work calendar too, or skip it?", offer = "Look for today's DoorDash meeting in my work calendar"))
        talk.submitTyped("Check my DoorDash meeting today")
        val next = talk.state.awaitState { s -> s.turns.any { it.offer != null } }
        assertEquals("Yes, look there", next.turns.last().offer?.yes)

        talk.pick("Yes, look there")
        talk.state.awaitState { it.doneSoFar.size == 2 }
        val asked = f.tasks.recent(10).first().map { it.text }
        assertTrue("Look for today's DoorDash meeting in my work calendar" in asked, "yes runs the offered task: $asked")
        val mine = talk.state.value.turns.filter { it.mine }.map { it.text }
        assertEquals(listOf("Check my DoorDash meeting today", "Yes, look there"), mine, "the bubble shows what they said")
    }

    @Test
    fun noToModesOfferRunsNothingAndTheConversationGoesOn() = runTest {
        val f = Fixture(backgroundScope)
        val talk = ListenPresenter(backgroundScope, Voices(f.settings, HttpClient(MockEngine { error("offline") })), f.queue, f.settings, f.runner,
            entry = "siri", greeter = nextLines("Want me to look in your work calendar too?", "Okay. Anything else?", offer = "Look in my work calendar"))
        talk.submitTyped("Check my DoorDash meeting today")
        talk.state.awaitState { s -> s.turns.any { it.offer != null } }
        talk.pick("Skip it")
        val after = talk.state.awaitState { s -> s.turns.lastOrNull()?.text == "Okay. Anything else?" }
        assertFalse(after.finished)
        assertEquals(1, f.tasks.recent(10).first().size, "no task for a no")
    }

    @Test
    fun yesAndNoAreTheWholeReply() {
        listOf("Yes", "yeah sure", "Yes please", "go ahead Mo").forEach { assertTrue(ListenPresenter.isYes(it), it) }
        listOf("Skip it", "no thanks", "leave it for now").forEach { assertTrue(ListenPresenter.isDecline(it), it) }
        assertFalse(ListenPresenter.isYes("Yes, and also text Priya"))
    }

    @Test
    fun aSessionOffersToCarryOnTheLastStoppedTaskInItsChat() = runTest {
        val f = Fixture(backgroundScope, model = NeverAnswers)
        val before = DeviceVoice.hearingState
        DeviceVoice.hearingState = { "ready" }
        try {
            val chat = f.conversations.create("LinkedIn onboarding and paneer")
            val stopped = com.piyush.mode.app.screens.settings.StoppedTask("Order high-protein paneer on Blinkit", chat.id, "LinkedIn onboarding and paneer")
            var askedFor: String? = "unset"
            val talk = ListenPresenter(backgroundScope, Voices(f.settings, HttpClient(MockEngine { error("offline") })), f.queue, f.settings, f.runner,
                conversationId = chat.id, entry = "chat_mic", lastStopped = { id -> askedFor = id; stopped })
            talk.begin()
            val offered = talk.state.awaitState { it.greeting.isNotEmpty() }
            assertEquals(chat.id, askedFor, "opened from the chat's mic: that chat is looked at")
            assertEquals("Last time, \u201cLinkedIn onboarding and paneer\u201d stopped before it was done. Want me to carry on?", offered.greeting)
            assertEquals("Yes, carry on", offered.turns.last().offer?.yes)
            talk.pick("Yes, carry on")
            val sent = talk.state.awaitState { it.sent }
            assertEquals("Order high-protein paneer on Blinkit", sent.task)
            assertEquals(chat.id, f.tasks.latest(1).first().conversationId, "it runs in the same chat: the session carries on")
        } finally {
            DeviceVoice.hearingState = before
        }
    }

    @Test
    fun yesToTheGreetingsOfferRunsTheOfferedTask() = runTest {
        val f = Fixture(backgroundScope, model = NeverAnswers)
        val model = com.piyush.mode.llm.LlmClient { _, _, _ ->
            com.piyush.mode.llm.ChatResult(
                com.piyush.mode.llm.ToolCall("say", kotlinx.serialization.json.buildJsonObject {
                    put("line", kotlinx.serialization.json.JsonPrimitive("Your Softovac reminder is at 11:30 tonight. Want me to set an alarm?"))
                    put("offer_task", kotlinx.serialization.json.JsonPrimitive("Set an alarm for 11:30 tonight for Softovac"))
                    put("yes_label", kotlinx.serialization.json.JsonPrimitive("Yes, set it"))
                    put("no_label", kotlinx.serialization.json.JsonPrimitive("Not now"))
                }), "", com.piyush.mode.llm.Usage(10, 5, 0.0))
        }
        val greeter = com.piyush.mode.feature.memory.data.Greeter(model, null, model = { "fast@low" to com.piyush.mode.llm.ModelProvider.ChatGpt })
        val day = com.piyush.mode.feature.memory.data.DayContext("Monday 9 November, 22:10", repeats = listOf("Softovac at 23:30"))
        val before = DeviceVoice.hearingState
        DeviceVoice.hearingState = { "ready" }
        try {
            val talk = ListenPresenter(backgroundScope, Voices(f.settings, HttpClient(MockEngine { error("offline") })), f.queue, f.settings, f.runner,
                entry = "siri", greeter = greeter, day = { day })
            talk.begin()
            val offered = talk.state.awaitState { it.turns.any { t -> t.offer != null } }
            assertEquals("Yes, set it", offered.turns.last().offer?.yes)
            assertEquals(1, offered.turns.count { "Your Softovac" in it.text }, "the greeting shows once")
            assertTrue(ListenPresenter.isYes("Sure, please do this."))
            talk.pick("Sure, please do this.")
            val sent = talk.state.awaitState { it.sent }
            assertEquals("Set an alarm for 11:30 tonight for Softovac", sent.task, "the offered task runs, not the words \"sure, please do this\"")
        } finally {
            DeviceVoice.hearingState = before
        }
    }

    @Test
    fun aNewSessionNeverBringsUpAnEarlierStoppedTask() = runTest {
        val f = Fixture(backgroundScope)
        f.settings.update { it.copy(userName = "Alex") }
        val before = DeviceVoice.hearingState
        DeviceVoice.hearingState = { "ready" }
        try {
            val chat = f.conversations.create("LinkedIn onboarding and paneer")
            val stopped = com.piyush.mode.app.screens.settings.StoppedTask("Order high-protein paneer on Blinkit", chat.id, "LinkedIn onboarding and paneer")
            var asked = false
            val talk = ListenPresenter(backgroundScope, Voices(f.settings, HttpClient(MockEngine { error("offline") })), f.queue, f.settings, f.runner,
                entry = "siri", lastStopped = { asked = true; stopped })
            talk.begin()
            val greeted = talk.state.awaitState { it.greeting.isNotEmpty() }
            assertFalse(asked, "a new chat does not look back")
            assertTrue(greeted.greeting.startsWith("Hey Alex"), greeted.greeting)
            assertTrue(greeted.turns.none { it.offer != null })
        } finally {
            DeviceVoice.hearingState = before
        }
    }

    @Test
    fun withNothingStoppedTheSessionGreetsAsUsual() = runTest {
        val f = Fixture(backgroundScope)
        f.settings.update { it.copy(userName = "Alex") }
        val before = DeviceVoice.hearingState
        DeviceVoice.hearingState = { "ready" }
        try {
            val talk = ListenPresenter(backgroundScope, Voices(f.settings, HttpClient(MockEngine { error("offline") })), f.queue, f.settings, f.runner,
                entry = "siri", lastStopped = { null })
            talk.begin()
            assertTrue(talk.state.awaitState { it.greeting.isNotEmpty() }.greeting.startsWith("Hey Alex"))
        } finally {
            DeviceVoice.hearingState = before
        }
    }

    @Test
    fun aSessionFromAChatShowsItAndCarriesOnFromIt() = runTest {
        val f = Fixture(backgroundScope)
        val before = DeviceVoice.hearingState
        DeviceVoice.hearingState = { "ready" }
        try {
            val soFar = com.piyush.mode.app.screens.settings.ChatSoFar(
                title = "LinkedIn onboarding and paneer",
                lines = listOf(true to "Check my LinkedIn onboarding email", false to "It says to fill in your new-hire details."),
                lastTask = "Check my LinkedIn onboarding email", lastDone = true, lastSaid = "It says to fill in your new-hire details.",
            )
            val talk = ListenPresenter(backgroundScope, Voices(f.settings, HttpClient(MockEngine { error("offline") })), f.queue, f.settings, f.runner,
                conversationId = "chat-9", entry = "chat_mic", chatSoFar = { soFar })
            talk.begin()
            val s = talk.state.awaitState { it.greeting.isNotEmpty() }
            assertEquals("Back to \u201cLinkedIn onboarding and paneer\u201d. What next?", s.greeting)
            assertEquals(listOf("Check my LinkedIn onboarding email", "It says to fill in your new-hire details."), s.turns.take(2).map { it.text })
            assertTrue(s.turns.first().mine)
        } finally {
            DeviceVoice.hearingState = before
        }
    }

    @Test
    fun theMicPausesAndResumesWithoutEndingTheSession() = runTest {
        val f = Fixture(backgroundScope)
        val before = DeviceVoice.hearingState
        DeviceVoice.hearingState = { "ready" }
        try {
            val talk = ListenPresenter(backgroundScope, Voices(f.settings, HttpClient(MockEngine { error("offline") })), f.queue, f.settings, f.runner, entry = "siri", pausedEndMs = Long.MAX_VALUE)
            talk.begin()
            talk.state.awaitState { it.greeting.isNotEmpty() }
            talk.pauseListening()
            assertFalse(talk.state.awaitState { it.micPaused }.finished, "pausing keeps the session open")
            talk.resumeListening()
            assertFalse(talk.state.awaitState { !it.micPaused }.finished)
        } finally {
            DeviceVoice.hearingState = before
        }
    }

    @Test
    fun aTaskThatLeavesThemInItsAppEndsALoneQuestionButNotTheOverlay() = runTest {
        val stay = { com.piyush.mode.testing.ScriptedModel(com.piyush.mode.testing.call("done", "evidence" to "x", "summary" to "Here are the results.", "stay_here" to true)) }
        val f = Fixture(backgroundScope, stay())
        val question = ListenPresenter(backgroundScope, Voices(f.settings, HttpClient(MockEngine { error("offline") })), f.queue, f.settings, f.runner,
            entry = "siri", forQuestion = true, pausedEndMs = Long.MAX_VALUE)
        question.submitTyped("Search for Claude Fable 5.2")
        assertTrue(question.state.awaitState { it.finished }.finished, "a lone question is over: Mode's own screen goes, so they see the results")

        val g = Fixture(backgroundScope, stay())
        val overlay = ListenPresenter(backgroundScope, Voices(g.settings, HttpClient(MockEngine { error("offline") })), g.queue, g.settings, g.runner,
            entry = ListenPresenter.OVERLAY, pausedEndMs = Long.MAX_VALUE)
        overlay.submitTyped("Search for Claude Fable 5.2")
        val after = overlay.state.awaitState { it.leftInApp == 1 && it.greeting.isNotEmpty() }
        assertFalse(after.finished, "the overlay steps aside; the conversation goes on")
    }

    @Test
    fun listeningAgainKeepsTheConversation() = runTest {
        val f = Fixture(backgroundScope)
        val talk = ListenPresenter(backgroundScope, Voices(f.settings, HttpClient(MockEngine { error("offline") })), f.queue, f.settings, f.runner,
            greeter = nextLines("Anything else?"), pausedEndMs = Long.MAX_VALUE)
        talk.submitTyped("Turn on dark mode")
        talk.state.awaitState { it.doneSoFar.isNotEmpty() }
        // They chose to type, then want the mic back: the turns so far stay (begin would start over).
        talk.typeInstead()
        val typing = talk.state.awaitState { it.phase == com.piyush.mode.app.screens.settings.TalkPhase.Typing }
        talk.listenAgain()
        val again = talk.state.awaitState { it.phase != com.piyush.mode.app.screens.settings.TalkPhase.Typing }
        assertEquals(typing.turns, again.turns, "the conversation is still there")
        assertEquals(typing.doneSoFar, again.doneSoFar)
        assertFalse(again.finished)
    }

    @Test
    fun endFinishesTheSessionAtOnce() = runTest {
        val f = Fixture(backgroundScope, model = NeverAnswers)
        val talk = ListenPresenter(backgroundScope, Voices(f.settings, HttpClient(MockEngine { error("offline") })), f.queue, f.settings, f.runner)
        talk.submitTyped("Turn on dark mode")
        talk.state.awaitState { it.sent }
        talk.endNow()
        talk.state.awaitState { it.finished }
        f.runner.state.awaitState { !it.running }
    }

    @Test
    fun aForgottenPausedSessionEndsByItself() = runTest {
        val f = Fixture(backgroundScope)
        val talk = ListenPresenter(backgroundScope, Voices(f.settings, HttpClient(MockEngine { error("offline") })), f.queue, f.settings, f.runner)
        talk.pauseListening()
        talk.state.awaitState { it.micPaused }
        talk.state.awaitState { it.finished }
    }

    @Test
    fun stoppingATaskKeepsTheSessionOpen() = runTest {
        val f = Fixture(backgroundScope, model = NeverAnswers)
        val talk = ListenPresenter(backgroundScope, Voices(f.settings, HttpClient(MockEngine { error("offline") })), f.queue, f.settings, f.runner,
            pausedEndMs = Long.MAX_VALUE)
        talk.submitTyped("Turn on dark mode")
        talk.state.awaitState { it.sent }
        talk.stop()
        // Not working any more: the card says stopped and Mode listens again (the task winds down behind).
        val after = talk.state.awaitState { s -> !s.sent && s.turns.mapNotNull { it.card }.none { it.state == TalkTaskCard.State.Working } }
        assertFalse(after.finished, "stopping the task keeps the session open")
        f.runner.state.awaitState { !it.running }
        talk.state.awaitState { !it.stopping }
    }

    @Test
    fun aTaskThatLeavesThemInTheAppDoesNotEndTheSession() = runTest {
        // Mode left the cart open in Zomato ("stay"); the session goes on from there (seen on the phone, 10 Nov: it ended).
        val f = Fixture(backgroundScope, model = com.piyush.mode.testing.ScriptedModel(
            com.piyush.mode.testing.call("done", "evidence" to "cart open", "summary" to "Your coffee is in the cart.", "success" to true, "stay_here" to true),
        ))
        val talk = ListenPresenter(backgroundScope, Voices(f.settings, HttpClient(MockEngine { error("offline") })), f.queue, f.settings, f.runner,
            entry = "siri", greeter = nextLines("Anything else?"), pausedEndMs = Long.MAX_VALUE)
        talk.submitTyped("Order my usual coffee")
        val next = talk.state.awaitState { it.doneSoFar.isNotEmpty() }
        assertFalse(next.finished, "the session carries on while they look at the cart")
        assertEquals("Anything else?", talk.state.awaitState { it.greeting == "Anything else?" }.greeting)
    }

    @Test
    fun stopWithNothingRunningPausesTheMic() = runTest {
        val f = Fixture(backgroundScope)
        val talk = ListenPresenter(backgroundScope, Voices(f.settings, HttpClient(MockEngine { error("offline") })), f.queue, f.settings, f.runner,
            pausedEndMs = Long.MAX_VALUE)
        talk.stop()
        assertFalse(talk.state.awaitState { it.micPaused }.finished)
    }
}
