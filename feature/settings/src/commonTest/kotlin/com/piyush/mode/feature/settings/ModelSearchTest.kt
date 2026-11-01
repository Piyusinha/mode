package com.piyush.mode.feature.settings

import com.piyush.mode.feature.settings.data.ModelSearch
import com.piyush.mode.feature.settings.domain.ModelOption
import com.piyush.mode.feature.settings.domain.ModelTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelSearchTest {
    private val models = listOf(
        ModelOption("anthropic/claude-sonnet-5.5", "Claude Sonnet 5.5", 0.077, ModelTag.Strongest),
        ModelOption("anthropic/claude-haiku-5.5", "Claude Haiku 5.5", 0.0039, ModelTag.Recommended),
        ModelOption("anthropic/claude-sonnet-5", "Claude Sonnet 5", 0.07),
        ModelOption("qwen/qwen3.7-flash", "Qwen 3.7 Flash", 0.0011, ModelTag.Cheapest),
        ModelOption("openai/gpt-6.1-terra", "GPT-6.1 Terra", 0.02),
        ModelOption("google/gemini-3.8-flash", "Gemini 3.8 Flash", 0.01),
    )

    private fun ids(q: String, maker: String? = null) = ModelSearch.search(models, q, maker).map { it.id }

    @Test
    fun theMakerCountsThoughOnlyTheIdHasIt() {
        assertEquals(listOf("anthropic/claude-haiku-5.5"), ids("anthropic haiku"))
    }

    @Test
    fun numbersStuckToWordsAreFound() {
        assertEquals(listOf("anthropic/claude-haiku-5.5"), ids("haiku5.5"))
        assertEquals("qwen/qwen3.7-flash", ids("qwen3.7").single())
    }

    @Test
    fun wordsInAnyOrderAndTheClosestNameFirst() {
        assertEquals("anthropic/claude-sonnet-5", ids("sonnet 5").first(), "an exact version beats a longer one")
        assertTrue("anthropic/claude-sonnet-5.5" in ids("sonnet 5"))
        assertEquals(listOf("qwen/qwen3.7-flash", "google/gemini-3.8-flash"), ids("flash"), "picks first, then cheaper")
        assertEquals(listOf("google/gemini-3.8-flash"), ids("flash gemini"))
    }

    @Test
    fun aMakerFilterAndAnEmptyQuery() {
        assertEquals(3, ids("", maker = "anthropic").size)
        assertEquals(listOf("anthropic/claude-haiku-5.5"), ids("hai", maker = "anthropic"))
        assertEquals(6, ids("  ").size)
    }
}
