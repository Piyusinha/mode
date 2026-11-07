package com.piyush.mode.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class FailureWordsTest {
    @Test
    fun aRejectedKeyAsksToCheckIt() {
        val line = FailureWords.of(LlmFailed("anthropic/claude-haiku-5.5: HTTP 401: Missing Authentication header", status = 401))
        assertEquals(FailureWords.KEY, line)
        assertFalse("HTTP" in line!! || "claude" in line)
    }

    @Test
    fun creditBusyAndDownEachSayWhatToDo() {
        assertEquals(FailureWords.CREDIT, FailureWords.of(LlmFailed("m: HTTP 402: no credit", status = 402)))
        assertEquals(FailureWords.BUSY, FailureWords.of(LlmFailed("m: HTTP 429", status = 429)))
        assertEquals(FailureWords.UNREACHABLE, FailureWords.of(LlmFailed("m: HTTP 503", status = 503)))
        assertEquals(FailureWords.OTHER, FailureWords.of(LlmFailed("m: HTTP 400: bad", status400 = true)))
    }

    @Test
    fun aMissingKeyKeepsItsOwnPlainLine() {
        assertEquals("No OpenRouter API key: add it in Settings.", FailureWords.of(LlmFailed("No OpenRouter API key: add it in Settings.")))
    }

    @Test
    fun otherErrorsAreLeftAlone() {
        assertNull(FailureWords.of(IllegalStateException("Turn on Mode in Settings")))
    }
}
