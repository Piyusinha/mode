package com.piyush.mode.feature.settings

import com.piyush.mode.feature.settings.data.ModelCatalog
import com.piyush.mode.feature.settings.domain.CatalogSource
import com.piyush.mode.feature.settings.domain.ModelTag
import com.piyush.mode.testing.InMemoryFileStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun model(id: String, name: String, prompt: String, completion: String, image: Boolean = true, tools: Boolean = true) = """
    {"id":"$id","name":"$name","context_length":100000,
     "architecture":{"input_modalities":[${if (image) "\"text\",\"image\"" else "\"text\""}]},
     "pricing":{"prompt":"$prompt","completion":"$completion"},
     "supported_parameters":[${if (tools) "\"tools\",\"tool_choice\"" else "\"max_tokens\""}]}
"""

private val CATALOGUE = """{"data":[
    ${model("anthropic/claude-haiku-5.5", "Anthropic: Claude Haiku 5.5", "0.0000001", "0.0000005")},
    ${model("qwen/qwen3.7-flash", "Qwen: Qwen 3.7 Flash", "0.00000003", "0.00000013")},
    ${model("text/only", "Text Only", "0.00000001", "0.00000001", image = false)},
    ${model("no/tools", "No Tools", "0.00000001", "0.00000001", tools = false)}
]}"""

class ModelCatalogTest {
    private var clock = 1_000_000L

    @Test
    fun keepsModelsThatSeeAndUseToolsWithACostPerTask() = runTest {
        val catalog = ModelCatalog(fetch = { CATALOGUE }, files = InMemoryFileStore(), now = { clock })
        catalog.refresh()
        val state = catalog.state.value
        assertEquals(CatalogSource.Live, state.source)
        assertEquals(listOf("qwen/qwen3.7-flash", "anthropic/claude-haiku-5.5"), state.all.map { it.id }, "usable, cheapest first")
        val haiku = state.all.single { it.id == "anthropic/claude-haiku-5.5" }
        assertEquals("Claude Haiku 5.5", haiku.name)
        assertEquals(10 * (3500 * 0.0000001 + 70 * 0.0000005), haiku.costPerTaskUsd, 1e-12)
        assertEquals(ModelTag.Recommended, state.recommended.first().tag)
        assertEquals(haiku.costPerTaskUsd, state.recommended.first().costPerTaskUsd, "picks carry live prices")
    }

    @Test
    fun aCachedListIsUsedUntilADayHasPassed() = runTest {
        val files = InMemoryFileStore()
        var fetches = 0
        ModelCatalog(fetch = { fetches++; CATALOGUE }, files = files, now = { clock }).refresh()

        val reopened = ModelCatalog(fetch = { fetches++; CATALOGUE }, files = files, now = { clock })
        assertEquals(CatalogSource.Cached, reopened.state.value.source)
        reopened.refresh()
        assertEquals(1, fetches, "still fresh")
        clock += 25L * 60 * 60 * 1000
        reopened.refresh()
        assertEquals(2, fetches, "a day old: fetched again")
    }

    @Test
    fun withNoNetworkThePickerIsNeverEmpty() = runTest {
        val catalog = ModelCatalog(fetch = { error("offline") }, files = InMemoryFileStore(), now = { clock })
        catalog.refresh()
        val state = catalog.state.value
        assertEquals(CatalogSource.BuiltIn, state.source)
        assertTrue(state.recommended.isNotEmpty())
        assertTrue(catalog.search("haiku").isNotEmpty())
    }
}
