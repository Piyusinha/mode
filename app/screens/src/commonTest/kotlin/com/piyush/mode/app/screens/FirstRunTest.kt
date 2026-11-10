package com.piyush.mode.app.screens

import com.piyush.mode.app.screens.firstrun.ConnectState
import com.piyush.mode.app.screens.firstrun.FirstRunPresenter
import com.piyush.mode.app.screens.firstrun.FirstRunStep
import com.piyush.mode.app.screens.settings.ConnectChatGptPresenter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.ktor.client.engine.mock.respond

class FirstRunTest {
    private fun Fixture.firstRun(
        scope: kotlinx.coroutines.CoroutineScope,
        prepare: suspend () -> Unit = {},
    ): FirstRunPresenter {
        val account = com.piyush.mode.feature.settings.data.ChatGptAccount(
            com.piyush.mode.llm.ChatGptAuth(io.ktor.client.HttpClient(io.ktor.client.engine.mock.MockEngine { respond("") })), com.piyush.mode.testing.InMemorySecretStore(),
        )
        return FirstRunPresenter(scope, settings, ConnectChatGptPresenter(scope, account, com.piyush.mode.testing.ScriptedModel()), queue, prepare)
    }

    @Test
    fun welcomeThenConnectThenTheFirstJobOnceAnAIIsConnected() = runTest {
        val f = Fixture(this)
        f.settings.apiKey = null
        val p = f.firstRun(backgroundScope)
        assertEquals(FirstRunStep.Welcome, p.state.awaitState { true }.step)
        p.getStarted()
        val connect = p.state.awaitState { it.step == FirstRunStep.Connect }
        assertEquals(ConnectState.Choose, connect.connect)
        p.next()
        assertEquals(FirstRunStep.Connect, p.state.value.step, "no AI yet: Continue does nothing")
        f.settings.apiKey = "sk-or-test"
        f.settings.update { it }
        val connected = p.state.awaitState { it.connected }
        // Connected with OpenRouter: the card shows OpenRouter, not ChatGPT.
        assertEquals(ConnectState.Connected("openrouter"), connected.connect)
        p.next()
        assertEquals(FirstRunStep.FirstTask, p.state.awaitState { it.step == FirstRunStep.FirstTask }.step)
        assertTrue(p.state.value.firstJobs.size == 3)
    }

    @Test
    fun finishingOpensModeNextTime() = runTest {
        val f = Fixture(this)
        val p = f.firstRun(backgroundScope)
        p.finish()
        assertTrue(f.settings.settings.value.onboardingDone)
    }

    @Test
    fun aFirstJobAsksForPermissionsThenQueuesTheTaskInItsChatAndEndsSetup() = runTest {
        val f = Fixture(this)
        var asked = 0
        val p = f.firstRun(backgroundScope) { asked++ }
        p.state.awaitState { it.connected }
        p.startJob("  Turn off Wi-Fi ")
        val started = p.state.awaitState { it.started != null }
        assertEquals(1, asked, "the VPN and notifications are asked once, as the first job starts")
        assertTrue(started.started!!.isNotEmpty(), "the job runs in a chat that Mode opens")
        assertTrue(f.settings.settings.value.onboardingDone)
        val task = f.tasks.latest(5).single()
        assertEquals("Turn off Wi-Fi", task.text)
        assertEquals(started.started, task.conversationId)
        p.startJob("Turn on Do Not Disturb")
        assertEquals(1, f.tasks.latest(5).size, "only one first job")
    }

    @Test
    fun aRefusedPermissionDoesNotStopTheFirstJob() = runTest {
        val f = Fixture(this)
        val p = f.firstRun(backgroundScope) { error("VPN not allowed") }
        p.state.awaitState { it.connected }
        p.startJob("Set an alarm for 7 am")
        p.state.awaitState { it.started != null }
        assertEquals("Set an alarm for 7 am", f.tasks.latest(5).single().text)
        assertTrue(f.settings.settings.value.onboardingDone)
    }

    @Test
    fun noFirstJobWithoutAnAIOrWords() = runTest {
        val f = Fixture(this)
        val p = f.firstRun(backgroundScope)
        p.startJob("   ")
        f.settings.apiKey = null
        f.settings.update { it }
        p.state.awaitState { !it.connected }
        p.startJob("Turn off Wi-Fi")
        testScheduler.advanceUntilIdle()
        assertTrue(f.tasks.latest(5).isEmpty())
        assertEquals(null, p.state.value.started)
        assertTrue(!f.settings.settings.value.onboardingDone)
    }
}
