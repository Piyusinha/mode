package com.piyush.mode.app.screens

import com.piyush.mode.app.screens.onboarding.OnboardingPresenter
import com.piyush.mode.app.screens.onboarding.OnboardingStep
import com.piyush.mode.app.screens.onboarding.PermissionKind
import com.piyush.mode.app.screens.onboarding.TestItemKind
import com.piyush.mode.app.screens.onboarding.TestItemState
import com.piyush.mode.core.platform.AppLifecycle
import com.piyush.mode.core.platform.PermissionStatus
import com.piyush.mode.feature.connection.domain.CheckState
import com.piyush.mode.llm.OpenRouterAuth
import com.piyush.mode.testing.ScriptedModel
import com.piyush.mode.testing.call
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OnboardingTest {
    private val auth = OpenRouterAuth(HttpClient(MockEngine {
        respond("""{"key":"sk-or-v1-from-oauth-0123456789"}""", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
    }))

    private fun TestScope.onboarding(f: Fixture) =
        OnboardingPresenter(backgroundScope, f.settings, f.catalog, f.session, f.runner, f.permissions, f.checks, auth, lifecycle)

    private val lifecycle = FakeLifecycle()

    /** Lets a test bring Mode "back to the front". */
    class FakeLifecycle : AppLifecycle {
        private val foreground = mutableListOf<() -> Unit>()
        override fun onBackground(action: () -> Unit) {}
        override fun onForeground(action: () -> Unit) { foreground += action }
        override fun onTerminate(action: () -> Unit) {}
        fun comeToFront() = foreground.forEach { it() }
    }

    @Test
    fun welcomeKnowsWhetherThisIPhoneIsPaired() = runTest {
        val f = Fixture(this)
        assertTrue(onboarding(f).state.value.paired)
        f.checks.paired = false
        assertFalse(onboarding(f).state.value.paired)
    }

    @Test
    fun theNameComesFromTheContactCardAndStaysEditable() = runTest {
        val f = Fixture(this)
        val p = onboarding(f)
        p.next()
        assertEquals(1, p.state.value.step.number)
        p.setNameFromCard("Piyush", "Sinha")
        assertEquals("Piyush Sinha", p.state.value.cardName)
        assertEquals("Piyush", p.state.value.callName)
        p.setCallName("Piyu")
        p.next()
        assertEquals("Piyu", f.settings.settings.value.userName)
        assertEquals(OnboardingStep.Key, p.state.value.step)
    }

    @Test
    fun connectOpenRouterSavesTheKeyWithoutCopying() = runTest {
        val f = Fixture(this)
        f.settings.apiKey = ""
        val p = onboarding(f)
        p.next(); p.setNameFromCard("Piyush", ""); p.next()
        assertFalse(p.state.value.canContinue)
        val url = p.startSignIn(OnboardingPresenter.SIGN_IN_CALLBACK)
        assertTrue(url.startsWith("https://openrouter.ai/auth?"))
        assertTrue(p.state.value.signingIn)
        p.finishSignIn("mode://openrouter?code=abc")
        val s = p.state.awaitState { it.connected }
        assertTrue(s.canContinue)
        assertEquals("sk-or-v1-from-oauth-0123456789", f.settings.apiKey)

        p.startSignIn(OnboardingPresenter.SIGN_IN_CALLBACK)
        p.finishSignIn(null, error = "cancelled")
        assertEquals("cancelled", p.state.value.signInError)
    }

    @Test
    fun theModelStepPicksAMainModelAndTheWhenStuckOne() = runTest {
        val f = Fixture(this)
        // The fixture runs without a strong model; setup starts from the real default.
        f.settings.update { it.copy(strongModel = com.piyush.mode.agent.AgentConfig.DEFAULT_STRONG_MODEL) }
        val p = onboarding(f)
        repeat(3) { p.next(); if (p.state.value.step == OnboardingStep.Name) p.setNameFromCard("P", "") }
        assertEquals(OnboardingStep.Model, p.state.value.step)
        val s = p.state.awaitState { it.models.isNotEmpty() && it.stuckModel != null }
        assertTrue(s.stuckModel!!.isStuck, "the strongest is on by default")
        val cheap = s.models.last()
        p.chooseModel(cheap.id)
        p.toggleStuck()
        p.next()
        assertEquals(cheap.id, f.settings.settings.value.model)
        assertEquals(null, f.settings.settings.value.strongModel)
    }

    @Test
    fun permissionsShowTheirStateAndAsk() = runTest {
        val f = Fixture(this)
        val p = onboarding(f)
        val rows = p.state.awaitState { it.permissions.isNotEmpty() }.permissions
        assertEquals(listOf(PermissionKind.Vpn, PermissionKind.Notifications, PermissionKind.Siri, PermissionKind.Microphone), rows.map { it.kind })
        assertTrue(rows.single { it.kind == PermissionKind.Siri }.optional)
        assertTrue(rows.single { it.kind == PermissionKind.Microphone }.later)
        p.allow(PermissionKind.Notifications)
        val after = p.state.awaitState { st -> st.permissions.single { it.kind == PermissionKind.Notifications }.status == PermissionStatus.Allowed }
        assertEquals(PermissionStatus.NotAsked, after.permissions.single { it.kind == PermissionKind.Vpn }.status)
    }

    @Test
    fun theTestTicksOffAndAFailureRunsTheChecks() = runTest {
        val f = Fixture(this, ScriptedModel(
            call("open_app", "name" to "Settings", "reason" to "open Settings"),
            call("tap", "x" to 100, "y" to 200, "reason" to "Wi-Fi"),
            // A real step takes seconds; this pause is when Mode comes back in front.
            call("wait", "seconds" to 1, "reason" to "coming back"),
            // The fake screen never changes, so the first done is sent back once for a check.
            call("done", "evidence" to "Wi-Fi page", "summary" to "Opened Wi-Fi and came back."),
            call("done", "evidence" to "Wi-Fi page", "summary" to "Opened Wi-Fi and came back."),
        ))
        val p = onboarding(f)
        p.runTest()
        // Back in front after Wi-Fi: "Come back here" ticks at once, while the run is still going.
        var ticks = 0
        while (p.state.value.testItems.single { i -> i.kind == TestItemKind.TapWifi }.state != TestItemState.Done && ticks++ < 10_000) {
            testScheduler.advanceTimeBy(10)
            testScheduler.runCurrent()
        }
        assertTrue(p.state.value.testRunning, "still running when Mode is back in front")
        lifecycle.comeToFront()
        val back = p.state.value.testItems.single { it.kind == TestItemKind.ComeBack }
        assertEquals(TestItemState.Done, back.state)
        assertNotNull(back.ms)
        testScheduler.advanceUntilIdle()
        val result = p.state.awaitState { it.step == OnboardingStep.Result }
        assertEquals(TestItemState.Done, result.testItems.single { it.kind == TestItemKind.OpenSettings }.state)
        assertEquals("Opened Wi-Fi and came back.", result.testSummary)
        assertNotNull(result.connectedMs)

        p.confirmTest(worked = false)
        testScheduler.advanceUntilIdle()
        val diagnose = p.state.awaitState { it.checks.isNotEmpty() && !it.checking }
        assertEquals(OnboardingStep.Diagnose, diagnose.step)
        assertIs<CheckState.Failed>(diagnose.checks.last().state)
        assertTrue("Paired: FAILED: unpaired Fix: Pair again from your Mac." in p.report())
        p.finish()
        assertTrue(f.settings.settings.value.onboardingDone)
    }

    @Test fun aNewKeyInvalidatesAnOnboardingLoginStillInFlight() = runTest {
        val f = Fixture(this)
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine {
            entered.complete(Unit)
            release.await()
            respond("""{"key":"stale-key"}""")
        })
        val p = OnboardingPresenter(backgroundScope, f.settings, f.catalog, f.session, f.runner,
            f.permissions, f.checks, OpenRouterAuth(client), lifecycle)
        p.startSignIn(OnboardingPresenter.SIGN_IN_CALLBACK)
        p.finishSignIn("mode://openrouter?code=old-code")
        testScheduler.runCurrent()
        entered.await()
        f.settings.apiKey = "replacement-key"
        release.complete(Unit)
        testScheduler.runCurrent()
        assertEquals("replacement-key", f.settings.apiKey)
        p.close()
        client.close()
    }
}
