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
import com.piyush.mode.feature.runs.data.RunRepository
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
    val settings = SettingsRepository(InMemorySecretStore(), InMemoryKeyValueStore()).also { it.apiKey = key }
    val runner = TaskRunner(
        session = PhoneSession(link, Duration.ZERO),
        settings = settings,
        llm = model,
        files = files,
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
        val f = Fixture(this, ScriptedModel(call("done", "summary" to "Light mode on")))
        val result = f.runner.runTask("Turn off dark mode", quick)

        assertIs<AgentOutcome.Done>(result!!.outcome)
        val state = f.runner.state.value
        assertFalse(state.running)
        assertTrue(state.status.startsWith("Done: Light mode on"), state.status)
        assertEquals(1, f.link.releases, "disconnected after the task")
        assertEquals("done", RunRepository(f.files).run(state.lastRunId!!)!!.outcome)
        assertTrue(f.notes.posted.single().contains("Light mode on"))
        assertFalse(f.activity.isBusy)
    }

    @Test
    fun withoutAKeyItDoesNotConnect() = runTest {
        val f = Fixture(this, ScriptedModel(), key = null)
        assertNull(f.runner.runTask("t", quick))
        assertTrue("API key" in f.runner.state.value.status)
        assertEquals(0, f.link.connects)
        assertEquals("not_started", RunRepository(f.files).runs().single().outcome)
    }

    @Test
    fun aConnectionFailureIsShownNotThrown() = runTest {
        val f = Fixture(this, ScriptedModel(), fail = "No pairing file yet")
        assertNull(f.runner.runTask("t", quick))
        assertEquals("Failed: No pairing file yet", f.runner.state.value.status)
    }

    @Test
    fun aQuestionWaitsForTheUsersAnswer() = runTest {
        val f = Fixture(this, ScriptedModel(call("ask_user", "question" to "Which Wi-Fi?"), call("done", "summary" to "joined")))
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
}
