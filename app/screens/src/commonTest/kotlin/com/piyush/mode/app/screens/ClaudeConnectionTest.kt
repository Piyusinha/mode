package com.piyush.mode.app.screens

import com.piyush.mode.app.screens.settings.ClaudeConnectionPresenter
import com.piyush.mode.feature.settings.data.ClaudeAccount
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import com.piyush.mode.llm.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class ClaudeConnectionTest {
    @Test fun connectionCheckRequiresTheImageToBeReadAndAValidToolResponse() = runTest {
        val f = Fixture(this)
        f.settings.claudeCredentials.save(ClaudeOAuth.Credentials("test-access", "test-refresh", Long.MAX_VALUE))
        var called = false
        val client = LlmClient { model, messages, tools ->
            called = true
            assertEquals(ClaudeOAuthClient.MAIN_MODEL, model)
            assertContentEquals(byteArrayOf(1, 2, 3), messages.last().image)
            assertEquals("connection_check", tools.single().jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
            ChatResult(ToolCall("connection_check", buildJsonObject { put("observed", "42") }), "", Usage(10, 5, 0.0))
        }
        val http = HttpClient(MockEngine { error("not called") })
        val p = ClaudeConnectionPresenter(backgroundScope, ClaudeAccount(f.settings.claudeCredentials, ClaudeOAuth(http)), client)
        p.check(byteArrayOf(1, 2, 3))
        val result = p.state.awaitState { it.check != null }
        assertTrue(called)
        assertTrue(result.check!!.startsWith("Connected:"))
        assertFalse(result.checking)
        p.close()
        http.close()
    }

    @Test fun aTextAnswerDoesNotPassTheToolCheck() = runTest {
        val f = Fixture(this)
        f.settings.claudeCredentials.save(ClaudeOAuth.Credentials("test-access", "test-refresh", Long.MAX_VALUE))
        val client = LlmClient { _, _, _ -> ChatResult(null, "42", Usage(10, 5, 0.0)) }
        val http = HttpClient(MockEngine { error("not called") })
        val p = ClaudeConnectionPresenter(backgroundScope, ClaudeAccount(f.settings.claudeCredentials, ClaudeOAuth(http)), client)
        p.check(byteArrayOf(1, 2, 3))
        assertTrue(p.state.awaitState { it.check != null }.check!!.contains("did not pass"))
        p.close()
        http.close()
    }
}
