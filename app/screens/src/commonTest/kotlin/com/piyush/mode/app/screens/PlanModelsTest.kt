package com.piyush.mode.app.screens

import com.piyush.mode.app.screens.settings.ModelRole
import com.piyush.mode.app.screens.settings.PlanModelsPresenter
import com.piyush.mode.app.screens.settings.PlanTag
import com.piyush.mode.feature.settings.data.BrainProbe
import com.piyush.mode.feature.settings.data.ChatGptAccount
import com.piyush.mode.feature.settings.data.ModelBench
import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.feature.settings.domain.ProviderModels
import com.piyush.mode.llm.ChatGptAuth
import com.piyush.mode.llm.ChatResult
import com.piyush.mode.llm.LlmClient
import com.piyush.mode.llm.ModelProvider
import com.piyush.mode.llm.ToolCall
import com.piyush.mode.llm.Usage
import com.piyush.mode.testing.InMemoryKeyValueStore
import com.piyush.mode.testing.InMemorySecretStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlanModelsTest {
    private val secrets = InMemorySecretStore()
    private val prefs = InMemoryKeyValueStore()
    private val settings = SettingsRepository(secrets, prefs)
    private val chatGpt = ChatGptAccount(ChatGptAuth(HttpClient(MockEngine { respond("") })), secrets, onChange = { settings.accountsChanged() })
    /**
     * Luna answers in 0.1 s but misses 1 of 4; Terra takes 0.4 s, all right; Sol takes 2 s, all right.
     * Each probe's image is as many bytes as the number it shows.
     */
    private val model = LlmClient { m, messages, _ ->
        val shown = messages.last().image!!.size
        val answer = if (m.startsWith("gpt-6.1-luna") && shown == 4) "0" else shown.toString()
        delay(when { m.startsWith("gpt-6.1-luna") -> 100L; m.startsWith("gpt-6.1-terra") -> 400L; else -> 2000L })
        ChatResult(ToolCall("read_number", JsonObject(mapOf("number" to JsonPrimitive(answer)))), "", Usage(10, 1, 0.0))
    }

    private fun probes() = (1..4).map { ModelBench.Probe(ByteArray(it), it.toString()) }

    @Test
    fun choosingAModelKeepsTheRolesEffortAndEffortChangesOnlyIt() = runTest {
        val bench = ModelBench(prefs)
        secrets.set(ChatGptAccount.CONNECTED_KEY, "r"); settings.accountsChanged()
        settings.selectProvider(ModelProvider.ChatGpt)
        val p = PlanModelsPresenter(backgroundScope, settings, chatGpt, bench, model)
        p.state.awaitState { it.models.isNotEmpty() }
        p.choose("gpt-6.1-sol")
        assertEquals("gpt-6.1-sol@low", settings.settings.value.model, "phone control starts at low effort")
        p.effort("medium")
        assertEquals("gpt-6.1-sol@medium", settings.settings.value.model)
        p.role(ModelRole.Brain)
        p.choose(ProviderModels.CHATGPT_MAIN)
        assertEquals("${ProviderModels.CHATGPT_MAIN}@high", settings.settings.value.strongModel, "the brain starts at high")
        assertEquals("gpt-6.1-sol@medium", settings.settings.value.model, "phone control untouched")
    }

    @Test
    fun measuringTagsTheFastestTheMostAccurateAndTheBestForTheRole() = runTest {
        val bench = ModelBench(prefs, timeSource = testScheduler.timeSource)
        secrets.set(ChatGptAccount.CONNECTED_KEY, "r"); settings.accountsChanged()
        settings.selectProvider(ModelProvider.ChatGpt)
        // The plan lists three models.
        secrets.set("chatgpt_models", "gpt-6.1-terra,gpt-6.1-sol,gpt-6.1-luna")
        val known = ChatGptAccount(ChatGptAuth(HttpClient(MockEngine { respond("") })), secrets)
        val p = PlanModelsPresenter(backgroundScope, settings, known, bench, model)
        p.state.awaitState { it.models.size == 3 }
        p.measure(probes())
        // Simulated time: 5 pairs × 4 probes, up to 2 s each.
        repeat(120) { delay(500) }
        val s = p.state.awaitState { st -> st.measuring == null && st.models.all { it.millis != null } }
        val tags = s.models.associate { it.id to it.tag }
        assertEquals(PlanTag.Fastest, tags["gpt-6.1-luna"], "fastest, 3 of 4 right")
        assertEquals(PlanTag.BestForPhoneControl, tags["gpt-6.1-terra"], "all right and quick enough")
        assertEquals(null, tags["gpt-6.1-sol"], "as accurate as Terra but five times slower")
        assertEquals(400L, s.models.single { it.id == "gpt-6.1-terra" }.millis)
        assertEquals(3, s.models.single { it.id == "gpt-6.1-luna" }.correct)
        p.role(ModelRole.Brain)
        // The brain's usual effort is high: only Terra (the selected one) was measured at high, so the
        // brain's default is still the one marked.
        assertEquals(PlanTag.Recommended, p.state.awaitState { it.role == ModelRole.Brain }.models.single { it.id == ProviderModels.CHATGPT_STRONG }.tag)
        val selected = s.models.single { it.selected }
        assertEquals(3, selected.efforts.count { it.millis != null }, "the selected model measured at every effort")
        assertTrue(selected.efforts.single { it.selected }.effort == "low")
    }

    @Test
    fun theBrainHasItsOwnTestAndScores() = runTest {
        val bench = ModelBench(prefs, timeSource = testScheduler.timeSource)
        secrets.set(ChatGptAccount.CONNECTED_KEY, "r"); settings.accountsChanged()
        settings.selectProvider(ModelProvider.ChatGpt)
        secrets.set("chatgpt_models", "gpt-6.1-terra,gpt-6.1-sol")
        val known = ChatGptAccount(ChatGptAuth(HttpClient(MockEngine { respond("") })), secrets)
        // Sol decides well (B on every choice: the first three are right); Terra answers A (none right). Neither writes a brief.
        val brainModel = LlmClient { m, _, _ ->
            delay(if (m.startsWith("gpt-6.1-sol")) 1000L else 300L)
            val letter = if (m.startsWith("gpt-6.1-sol")) "B" else "A"
            ChatResult(ToolCall("decision", JsonObject(mapOf("answer" to JsonPrimitive("$letter) yes")))), "", Usage(10, 1, 0.0))
        }
        val p = PlanModelsPresenter(backgroundScope, settings, known, bench, brainModel)
        p.state.awaitState { it.models.size == 2 }
        p.role(ModelRole.Brain)
        p.measure(emptyList())
        repeat(120) { delay(500) }
        val s = p.state.awaitState { st -> st.measuring == null && st.role == ModelRole.Brain && st.models.all { it.millis != null } }
        val sol = s.models.single { it.id == "gpt-6.1-sol" }
        assertEquals(3, sol.correct)
        assertEquals(BrainProbe.ALL.size, sol.total)
        assertEquals(0, s.models.single { it.id == "gpt-6.1-terra" }.correct)
        assertEquals(PlanTag.BestForBrain, sol.tag, "more right, though slower")
        p.role(ModelRole.PhoneControl)
        assertTrue(p.state.awaitState { it.role == ModelRole.PhoneControl }.models.all { it.millis == null }, "phone control not measured")
    }
}
