package com.piyush.mode.app.screens

import com.piyush.mode.app.screens.settings.HealthFix
import com.piyush.mode.app.screens.settings.HealthItem
import com.piyush.mode.app.screens.settings.HealthPresenter
import com.piyush.mode.app.screens.settings.HealthState
import com.piyush.mode.app.screens.settings.HealthUiState
import com.piyush.mode.core.platform.Permission
import com.piyush.mode.feature.settings.data.ChatGptAccount
import com.piyush.mode.llm.ChatGptAuth
import com.piyush.mode.llm.ChatResult
import com.piyush.mode.llm.LlmClient
import com.piyush.mode.llm.ToolCall
import com.piyush.mode.llm.Usage
import com.piyush.mode.testing.InMemorySecretStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HealthTest {
    private fun health(scope: kotlinx.coroutines.CoroutineScope, f: Fixture, model: LlmClient) = HealthPresenter(
        scope, f.settings, f.session, f.checks, f.runner, null, f.permissions, model,
        ChatGptAccount(ChatGptAuth(HttpClient(MockEngine { respond("") })), InMemorySecretStore()), null, logTail = { "line 1\nline 2" },
    )

    private fun row(s: HealthUiState, item: HealthItem) = s.rows.first { it.item == item }

    @Test
    fun aModelThatAnswersWithoutAnActionIsCaughtAndAPairingProblemCanBeRepaired() = runTest {
        val f = Fixture(backgroundScope)
        f.settings.apiKey = "sk-or-v1-abcdefghijklmnop1234"
        val talksOnly = LlmClient { _, _, _ -> ChatResult(null, "Sure, I can help.", Usage(10, 5, 0.0)) }
        val h = health(backgroundScope, f, talksOnly)
        h.check()
        val s = h.state.awaitState { !it.checking && row(it, HealthItem.Actions).state != HealthState.Checking }

        assertEquals(HealthState.Warn, row(s, HealthItem.Actions).state)
        assertTrue("without choosing an action" in row(s, HealthItem.Actions).detail)
        // FakeChecks: the pairing failed ("Pair again from your Mac.").
        assertEquals(HealthState.Warn, row(s, HealthItem.Paired).state)
        // Pairing needs the Mac: no Repair button for it; the parts after it wait.
        assertEquals(HealthFix.None, row(s, HealthItem.Paired).fix)
        assertEquals(HealthState.Waiting, row(s, HealthItem.Reach).state)
        assertEquals(HealthFix.Repair, row(s, HealthItem.Notifications).fix)
        assertTrue(s.problems >= 3)
        assertTrue("line 2" in h.report() && "[!!] Actions" in h.report())
    }

    @Test
    fun aWorkingModelPassesAndAllowingNotificationsIsARepair() = runTest {
        val f = Fixture(backgroundScope)
        f.settings.apiKey = "sk-or-v1-abcdefghijklmnop1234"
        val acts = LlmClient { _, _, _ -> ChatResult(ToolCall("ok", JsonObject(emptyMap())), "", Usage(10, 5, 0.0)) }
        val h = health(backgroundScope, f, acts)
        h.check()
        h.state.awaitState { !it.checking && row(it, HealthItem.Actions).state != HealthState.Checking }
        assertEquals(HealthState.Ok, row(h.state.value, HealthItem.Actions).state)

        h.repair()
        val after = h.state.awaitState { !it.repairing && row(it, HealthItem.Notifications).state == HealthState.Repaired }
        assertTrue(Permission.Notifications in f.permissions.granted)
        assertEquals(HealthState.Repaired, row(after, HealthItem.Notifications).state)
    }
}
