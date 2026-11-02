package com.piyush.mode.feature.settings

import com.piyush.mode.feature.settings.data.ChatGptAccount
import com.piyush.mode.llm.ChatGptAuth
import com.piyush.mode.testing.InMemorySecretStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatGptAccountTest {
    private fun hostIn(url: String) = Regex("""ext_agent_host_id=([^&]+)""").find(url)!!.groupValues[1]

    @Test
    fun theHostIdIsAUrnUuidAndStaysTheSame() {
        val secrets = InMemorySecretStore()
        val account = ChatGptAccount(ChatGptAuth(HttpClient(MockEngine { respond("") })), secrets)
        val first = hostIn(account.beginSignIn())
        assertTrue(Regex("""urn%3Auuid%3A[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}""").matches(first), first)
        assertEquals(first, hostIn(account.beginSignIn()), "kept for this iPhone")
    }

    @Test
    fun anOldStyleHostIdIsReplaced() {
        val secrets = InMemorySecretStore()
        secrets.set("chatgpt_host_id", "3f2a9c0d1e4b4a6f8c7d2e1f0a9b8c7d")
        val account = ChatGptAccount(ChatGptAuth(HttpClient(MockEngine { respond("") })), secrets)
        assertTrue(hostIn(account.beginSignIn()).startsWith("urn%3Auuid%3A"))
    }
}
