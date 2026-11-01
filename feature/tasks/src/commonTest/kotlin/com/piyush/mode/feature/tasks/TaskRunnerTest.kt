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
import com.piyush.mode.feature.tasks.data.TaskRepository
import com.piyush.mode.feature.tasks.domain.TaskStatus
import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.feature.tasks.presentation.TaskRunner
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

private class Fixture(scope: CoroutineScope, model: LlmClient, key: String? = "sk-or-test", fail: String? = null) {
    val link = Link(fail)
    val files = InMemoryFileStore()
    val notes = Notes()
    val activity = ActivityTracker()
    val db = testDatabase()
    val runs = RunRepository(files, db)
    val conversations = ConversationRepository(db)
    val tasks = TaskRepository(db)
    val settings = SettingsRepository(InMemorySecretStore(), InMemoryKeyValueStore()).also { it.apiKey = key }
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
}
