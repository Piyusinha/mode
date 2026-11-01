package com.piyush.mode.feature.tasks

import com.piyush.mode.agent.AgentConfig
import com.piyush.mode.agent.AgentOutcome
import com.piyush.mode.core.common.ActivityTracker
import com.piyush.mode.core.platform.AppInfo
import com.piyush.mode.core.platform.BackgroundWork
import com.piyush.mode.core.platform.Notifier
import com.piyush.mode.feature.connection.domain.PhoneConnection
import com.piyush.mode.feature.connection.domain.PhoneLink
import com.piyush.mode.feature.connection.presentation.PhoneSession
import com.piyush.mode.feature.conversations.data.ConversationRepository
import com.piyush.mode.feature.conversations.domain.TurnRole
import com.piyush.mode.feature.runs.data.RunRepository
import com.piyush.mode.feature.memory.data.MemoryRepository
import com.piyush.mode.feature.memory.data.MemoryRetrieval
import com.piyush.mode.feature.memory.data.Reflector
import com.piyush.mode.feature.memory.domain.MemoryDraft
import com.piyush.mode.feature.memory.domain.MemoryKind
import com.piyush.mode.feature.tasks.data.TaskMemory
import com.piyush.mode.feature.tasks.data.TaskRepository
import com.piyush.mode.llm.ChatMessage
import com.piyush.mode.llm.ChatResult
import com.piyush.mode.llm.ToolCall
import com.piyush.mode.llm.Usage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.piyush.mode.feature.tasks.domain.TaskStatus
import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.feature.tasks.presentation.TaskQueue
import com.piyush.mode.feature.tasks.presentation.TaskRunner
import com.piyush.mode.feature.tasks.domain.TaskSource
import com.piyush.mode.llm.LlmClient
import com.piyush.mode.ops.PhoneUnavailable
import com.piyush.mode.testing.FakePhone
import com.piyush.mode.testing.InMemoryFileStore
import com.piyush.mode.testing.InMemoryKeyValueStore
import com.piyush.mode.testing.InMemorySecretStore
import com.piyush.mode.testing.ScriptedModel
import com.piyush.mode.testing.call
import com.piyush.mode.testing.testDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

private class Link(private val fail: String? = null) : PhoneLink {
    var connects = 0
    var releases = 0
    override suspend fun connect(): PhoneConnection {
        connects++
        fail?.let { throw PhoneUnavailable(it) }
        return PhoneConnection(FakePhone(List(10) { listOf("Settings" to (100.0 to 100.0)) }), "test", "")
    }
    override suspend fun release() { releases++ }
}

private class Notes : Notifier {
    val posted = mutableListOf<String>()
    override fun requestPermission() {}
    override fun post(title: String, body: String) { posted += "$title: $body" }
}

private object Inline : BackgroundWork {
    override suspend fun <T> run(work: suspend () -> T): T = work()
}

private class Fixture(scope: CoroutineScope, model: LlmClient, key: String? = "sk-or-test", fail: String? = null, withMemory: Boolean = false) {
    val link = Link(fail)
    val files = InMemoryFileStore()
    val notes = Notes()
    val activity = ActivityTracker()
    val db = testDatabase()
    val runs = RunRepository(files, db)
    val conversations = ConversationRepository(db)
    val tasks = TaskRepository(db)
    val settings = SettingsRepository(InMemorySecretStore(), InMemoryKeyValueStore()).also { it.apiKey = key }
    val memory = MemoryRepository(db)
    private val taskMemory = TaskMemory(memory, MemoryRetrieval(memory), Reflector(model, memory), tasks, startOfToday = { 0L })
    val runner = TaskRunner(
        session = PhoneSession(link, Duration.ZERO),
        settings = settings,
        llm = model,
        files = files,
        runs = runs,
        conversations = conversations,
        tasks = tasks,
        notifier = notes,
        background = Inline,
        appInfo = object : AppInfo { override val bundleId = "com.piyush.mode" },
        activity = activity,
        scope = scope,
        memory = if (withMemory) taskMemory else null,
    )
}

private val quick = AgentConfig(settleMs = 0, maxSteps = 10)

class TaskRunnerTest {
    @Test
    fun runsATaskRecordsItNotifiesAndLetsGoOfThePhone() = runTest {
        val f = Fixture(this, ScriptedModel(call("done", "evidence" to "on screen", "summary" to "Light mode on")))
        val result = f.runner.runTask("Turn off dark mode", quick)

        assertIs<AgentOutcome.Done>(result!!.outcome)
        val state = f.runner.state.value
        assertFalse(state.running)
        assertTrue(state.status.startsWith("Done: Light mode on"), state.status)
        assertEquals(1, f.link.releases, "disconnected after the task")
        assertEquals("done", f.runs.run(state.lastRunId!!)!!.outcome)
        assertTrue(f.notes.posted.single().contains("Light mode on"))
        assertFalse(f.activity.isBusy)
    }

    @Test
    fun withoutAKeyItDoesNotConnect() = runTest {
        val f = Fixture(this, ScriptedModel(), key = null)
        assertNull(f.runner.runTask("t", quick))
        assertTrue("API key" in f.runner.state.value.status)
        assertEquals(0, f.link.connects)
        assertEquals("not_started", f.runs.runs().single().outcome)
    }

    @Test
    fun aConnectionFailureIsShownNotThrown() = runTest {
        val f = Fixture(this, ScriptedModel(), fail = "No pairing file yet")
        assertNull(f.runner.runTask("t", quick))
        assertEquals("Failed: No pairing file yet", f.runner.state.value.status)
    }

    @Test
    fun aQuestionWaitsForTheUsersAnswer() = runTest {
        val f = Fixture(this, ScriptedModel(call("ask_user", "question" to "Which Wi-Fi?"), call("done", "evidence" to "on screen", "summary" to "joined")))
        f.runner.start("Join Wi-Fi", quick)
        testScheduler.runCurrent()
        val question = f.runner.state.value.question!!
        assertEquals("Which Wi-Fi?", question.text)
        assertTrue(f.activity.isBusy)

        f.runner.answer("Home")
        testScheduler.advanceUntilIdle()
        assertIs<AgentOutcome.Done>(f.runner.state.value.lastResult!!.outcome)
        assertNull(f.runner.state.value.question)
    }

    @Test
    fun stopEndsTheTaskAndStillDisconnects() = runTest {
        val f = Fixture(this, ScriptedModel(call("ask_user", "question" to "Which one?")))
        f.runner.start("t", quick)
        testScheduler.runCurrent()
        f.runner.stop()
        testScheduler.advanceUntilIdle()
        assertFalse(f.runner.state.value.running)
        assertEquals("Stopped.", f.runner.state.value.status)
        assertEquals(1, f.link.releases)
    }

    @Test
    fun aTaskIsSavedWithItsConversation() = runTest {
        val f = Fixture(this, ScriptedModel(call("done", "evidence" to "on screen", "summary" to "Light mode is on.")))
        f.runner.runTask("Turn off dark mode", quick)
        val state = f.runner.state.value

        val task = f.tasks.get(state.taskId!!)!!
        assertEquals(TaskStatus.Done, task.status)
        assertEquals("Light mode is on.", task.summary)
        assertEquals(state.lastRunId, task.runId)
        assertEquals(state.taskId, f.runs.run(task.runId!!)!!.taskId, "the run points back at its task")

        val turns = f.conversations.turns(state.conversationId!!).first()
        assertEquals(listOf(TurnRole.User, TurnRole.Mode), turns.map { it.role })
        assertEquals(listOf("Turn off dark mode", "Light mode is on."), turns.map { it.text })
    }

    @Test
    fun aFollowUpContinuesTheSameConversation() = runTest {
        val f = Fixture(this, ScriptedModel(call("done", "evidence" to "on screen", "summary" to "Sent."), call("done", "evidence" to "on screen", "summary" to "Sent to Rahul too.")))
        f.runner.runTask("Text Sonali I'm leaving", quick)
        val conversation = f.runner.state.value.conversationId!!
        f.runner.runTask("Also tell Rahul", quick, conversationId = conversation)
        assertEquals(4, f.conversations.turns(conversation).first().size)
    }

    @Test
    fun aStoppedTaskIsSavedAsStopped() = runTest {
        val f = Fixture(this, ScriptedModel(call("ask_user", "question" to "Which one?")))
        f.runner.start("t", quick)
        testScheduler.runCurrent()
        assertEquals(TaskStatus.WaitingForUser, f.tasks.get(f.runner.state.value.taskId!!)!!.status)
        f.runner.stop()
        testScheduler.advanceUntilIdle()
        val task = f.tasks.get(f.runner.state.value.taskId!!)!!
        assertEquals(TaskStatus.Stopped, task.status)
        assertEquals("Stopped.", f.conversations.turns(task.conversationId!!).first().last().text)
    }

    @Test
    fun withoutAKeyTheTaskIsSavedAsNotStarted() = runTest {
        val f = Fixture(this, ScriptedModel(), key = null)
        f.runner.runTask("t", quick)
        assertEquals(TaskStatus.NotStarted, f.tasks.get(f.runner.state.value.taskId!!)!!.status)
    }

    @Test
    fun comparisonRunsStartNoConversation() = runTest {
        val f = Fixture(this, ScriptedModel(call("done", "evidence" to "on screen", "summary" to "x")))
        f.runner.runTask("t", quick, tag = "compare-1")
        assertNull(f.runner.state.value.conversationId)
        assertTrue(f.conversations.recent().first().isEmpty())
    }

    private fun Fixture.alreadySpentToday(usd: Double) {
        val earlier = tasks.create("earlier task", com.piyush.mode.feature.tasks.domain.TaskSource.App, null)
        tasks.finish(earlier.id, TaskStatus.Done, "ok", 5, usd)
    }

    @Test
    fun onceTodaysCapIsSpentATaskDoesNotStart() = runTest {
        val model = ScriptedModel()
        val f = Fixture(this, model)
        f.settings.update { it.copy(dailyCapUsd = 0.25) }
        f.alreadySpentToday(0.30)

        assertNull(f.runner.runTask("Turn off dark mode", quick))
        assertTrue(model.models.isEmpty(), "the model was never asked")
        assertEquals(0, f.link.connects)
        val saved = f.tasks.recent().first().single { it.text == "Turn off dark mode" }
        assertEquals(TaskStatus.NotStarted, saved.status)
        assertTrue("spending limit" in saved.summary!!, saved.summary)
    }

    @Test
    fun whatIsLeftOfTodaysCapBecomesThisTasksLimit() = runTest {
        val tap = call("tap", "x" to 1, "y" to 1, "reason" to "try")
        val f = Fixture(this, ScriptedModel(tap, tap, tap, tap))
        f.settings.update { it.copy(dailyCapUsd = 0.2025) }
        f.alreadySpentToday(0.20)

        val result = f.runner.runTask("Turn off dark mode", AgentConfig(settleMs = 0, maxSteps = 10, maxCostUsd = 0.05))
        val stopped = assertIs<AgentOutcome.Stopped>(result!!.outcome)
        assertTrue(stopped.why.startsWith("Reached the $0.0025 limit"), stopped.why)
        assertEquals(3, result.steps)
    }

    @Test
    fun withNoDailyCapTheTasksOwnLimitStands() = runTest {
        val f = Fixture(this, ScriptedModel(call("done", "evidence" to "on screen", "summary" to "ok")))
        f.alreadySpentToday(99.0)
        assertIs<AgentOutcome.Done>(f.runner.runTask("Turn off dark mode", quick)!!.outcome)
    }

    @Test
    fun aFollowUpShowsTheModelHowTheEarlierTaskWentAndTheUsersName() = runTest {
        val model = ScriptedModel(
            call("done", "evidence" to "on screen", "summary" to "Sent Sonali: I'm leaving."),
            call("done", "evidence" to "on screen", "summary" to "Told Rahul."),
        )
        val f = Fixture(this, model)
        f.settings.update { it.copy(userName = "Piyush") }
        f.runner.runTask("Text Sonali I'm leaving", quick)
        assertFalse("EARLIER IN THIS CONVERSATION" in model.lastUserText, "a new conversation has no history")

        f.runner.runTask("Also tell Rahul", quick, conversationId = f.runner.state.value.conversationId)
        val prompt = model.lastUserText
        assertTrue("They asked: Text Sonali I'm leaving" in prompt, prompt)
        assertTrue("Result: Sent Sonali: I'm leaving." in prompt, prompt)
        assertFalse("They asked: Also tell Rahul" in prompt, "the current task is not an earlier turn")
        assertTrue("Their name: Piyush" in prompt)
    }

    /** Answers the agent from a script and the reflection with one saved fact; remembers what each was shown. */
    private class AgentAndCritic(vararg agentReplies: ToolCall) : LlmClient {
        private val agent = ScriptedModel(*agentReplies)
        val agentPrompts = mutableListOf<String>()
        var reflections = 0
        override suspend fun chat(model: String, messages: List<ChatMessage>, tools: JsonArray): ChatResult {
            if ("save_memory" in tools.toString()) {
                reflections++
                val args = buildJsonObject {
                    put("failure_type", "none")
                    put("changes", buildJsonArray {
                        add(buildJsonObject {
                            put("op", "add"); put("kind", "fact"); put("subject", "sonali"); put("title", "Sonali")
                            put("text", "Sonali: message her on WhatsApp"); put("evidence", "step 1")
                        })
                    })
                }
                return ChatResult(ToolCall("save_memory", args), "", Usage(800, 100, 0.0003))
            }
            agentPrompts += messages.last().text
            return agent.chat(model, messages, tools)
        }
    }

    @Test
    fun aTaskGetsWhatIsRememberedAndTeachesSomethingNew() = runTest {
        val model = AgentAndCritic(call("done", "evidence" to "sent", "summary" to "Sent."))
        val f = Fixture(this, model, withMemory = true)
        f.memory.save(MemoryDraft(MemoryKind.Fact, "home", "Home", "Home is in Indiranagar", "e"))

        f.runner.runTask("Message Sonali saying I'm late", quick)
        testScheduler.advanceUntilIdle()

        assertTrue("ABOUT THE USER:\n- Home is in Indiranagar" in model.agentPrompts.first(), model.agentPrompts.first())
        assertEquals(1, model.reflections)
        assertEquals(listOf("Sonali: message her on WhatsApp"), f.runner.state.value.remembered.map { it.text })
        val saved = f.tasks.recent().first().single()
        assertEquals(0.001 + 0.0003, saved.costUsd!!, 1e-9, "the reflection's cost counts toward the task")
        assertEquals(1, f.memory.all().first { it.subject == "home" }.uses, "what it was shown is recorded")
    }

    @Test
    fun undoTakesBackWhatATaskTaught() = runTest {
        val f = Fixture(this, AgentAndCritic(call("done", "evidence" to "sent", "summary" to "Sent.")), withMemory = true)
        f.runner.runTask("Message Sonali", quick)
        testScheduler.advanceUntilIdle()
        val item = f.runner.state.value.remembered.single()
        f.runner.undoRemembered(item.id)
        assertTrue(f.runner.state.value.remembered.isEmpty())
        assertTrue(f.memory.all().isEmpty())
    }

    @Test
    fun aPrivateTaskNeitherUsesNorAddsToMemory() = runTest {
        val model = AgentAndCritic(call("done", "evidence" to "sent", "summary" to "Sent."))
        val f = Fixture(this, model, withMemory = true)
        f.memory.save(MemoryDraft(MemoryKind.Fact, "home", "Home", "Home is in Indiranagar", "e"))
        f.runner.runTask("Message Sonali", quick, private = true)
        testScheduler.advanceUntilIdle()
        assertFalse("Indiranagar" in model.agentPrompts.single())
        assertEquals(0, model.reflections)
        assertEquals(1, f.memory.all().size)
    }

    @Test
    fun withLearningOffNothingNewIsSavedButMemoryIsStillUsed() = runTest {
        val model = AgentAndCritic(call("done", "evidence" to "sent", "summary" to "Sent."))
        val f = Fixture(this, model, withMemory = true)
        f.settings.update { it.copy(learnFromTasks = false) }
        f.memory.save(MemoryDraft(MemoryKind.Fact, "home", "Home", "Home is in Indiranagar", "e"))
        f.runner.runTask("Message Sonali", quick)
        testScheduler.advanceUntilIdle()
        assertTrue("Indiranagar" in model.agentPrompts.single())
        assertEquals(0, model.reflections)
    }

    @Test
    fun recentTasksAreShownToTheNextOne() = runTest {
        val model = AgentAndCritic(
            call("done", "evidence" to "x", "summary" to "Dark mode is off."),
            call("done", "evidence" to "x", "summary" to "Dark mode is on."),
        )
        val f = Fixture(this, model, withMemory = true)
        f.runner.runTask("turn off dark mode", quick)
        testScheduler.advanceUntilIdle()
        f.runner.runTask("turn it back on", quick)
        assertTrue("today: turn off dark mode → Dark mode is off." in model.agentPrompts.last(), model.agentPrompts.last())
    }

    private fun Fixture.queue(scope: CoroutineScope) = TaskQueue(runner, tasks, conversations, scope)

    private suspend fun Fixture.saved(text: String) = tasks.recent().first().single { it.text == text }

    @Test
    fun queuedTasksRunOneAfterAnotherInOrder() = runTest {
        val model = ScriptedModel(
            call("done", "evidence" to "x", "summary" to "A done"),
            call("done", "evidence" to "x", "summary" to "B done"),
        )
        val f = Fixture(this, model)
        val q = f.queue(this)
        q.submit("A", TaskSource.Siri)
        q.submit("B", TaskSource.Watch)
        testScheduler.advanceUntilIdle()
        assertEquals("A done", f.saved("A").summary)
        assertEquals("B done", f.saved("B").summary)
        assertEquals(TaskSource.Watch, f.saved("B").source)
        assertTrue(q.queue.value.isEmpty())
    }

    @Test
    fun aSecondTaskWaitsWhileTheFirstRunsAndCanBeCancelled() = runTest {
        val model = ScriptedModel(
            call("ask_user", "question" to "Which Sonali?"),
            call("done", "evidence" to "x", "summary" to "A done"),
            call("done", "evidence" to "x", "summary" to "C done"),
        )
        val f = Fixture(this, model)
        val q = f.queue(this)
        q.submit("A", TaskSource.App)
        testScheduler.advanceUntilIdle()
        val b = q.submit("B", TaskSource.App)!!
        q.submit("C", TaskSource.App)
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("B", "C"), q.queue.value.map { it.text }, "A is running, B and C wait")

        q.cancel(b.id)
        assertEquals(listOf("C"), q.queue.value.map { it.text })
        f.runner.answer("the first one")
        testScheduler.advanceUntilIdle()
        assertEquals(TaskStatus.Stopped, f.saved("B").status)
        assertEquals("C done", f.saved("C").summary, "B never ran")
    }

    @Test
    fun stopEndsTheRunningTaskButTheQueueGoesOn() = runTest {
        val model = ScriptedModel(
            call("ask_user", "question" to "Which one?"),
            call("done", "evidence" to "x", "summary" to "B done"),
        )
        val f = Fixture(this, model)
        val q = f.queue(this)
        val a = q.submit("A", TaskSource.App)!!
        testScheduler.advanceUntilIdle()
        q.submit("B", TaskSource.App)
        q.cancel(a.id)
        testScheduler.advanceUntilIdle()
        assertEquals(TaskStatus.Stopped, f.saved("A").status)
        assertEquals("B done", f.saved("B").summary)
    }

    @Test
    fun afterARelaunchInterruptedTasksFailStaleOnesAreDroppedAndFreshOnesRun() = runTest {
        val f = Fixture(this, ScriptedModel(call("done", "evidence" to "x", "summary" to "fresh done")))
        TaskRepository(f.db, now = { 0L }).create("stale", TaskSource.Siri, null)
        val running = f.tasks.create("was running", TaskSource.App, null)
        f.tasks.markRunning(running.id, "run-x")
        f.tasks.create("fresh", TaskSource.Watch, null)

        f.queue(this).recover()
        testScheduler.advanceUntilIdle()
        assertEquals(TaskStatus.Failed, f.saved("was running").status)
        assertEquals(TaskStatus.NotStarted, f.saved("stale").status)
        assertEquals("fresh done", f.saved("fresh").summary)
    }

    @Test
    fun aPrivateTaskStaysPrivateWhileItWaits() = runTest {
        val model = AgentAndCritic(call("done", "evidence" to "x", "summary" to "ok"))
        val f = Fixture(this, model, withMemory = true)
        val task = f.queue(this).submit("Message Sonali", TaskSource.App, private = true)!!
        assertTrue(f.tasks.get(task.id)!!.private)
        testScheduler.advanceUntilIdle()
        assertEquals(0, model.reflections)
    }
}
