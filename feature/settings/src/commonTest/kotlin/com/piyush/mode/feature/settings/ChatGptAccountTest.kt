package com.piyush.mode.feature.settings

import com.piyush.mode.feature.settings.data.ChatGptAccount
import com.piyush.mode.llm.ChatGptAuth
import com.piyush.mode.testing.InMemorySecretStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import com.piyush.mode.llm.LlmFailed
import io.ktor.http.Url
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
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

    private fun oldAccount() = InMemorySecretStore().also {
        it.set("chatgpt_client_id", "old-workspace-client")
        it.set("chatgpt_refresh_token", "old-refresh")
        it.set("chatgpt_access_token", "old-access")
        it.set("chatgpt_models", "old-model")
        it.set("chatgpt_profile", "old-profile")
    }

    private fun callback(start: String, client: String? = "new-client"): String =
        "http://127.0.0.1:1455/auth/callback?code=code&state=${Url(start).parameters["state"]}" +
            (client?.let { "&client_id=$it" } ?: "")

    private val tokenResponse = """{"access_token":"new-access","refresh_token":"new-refresh","expires_in":3600,"scope":"chatgpt.tokens.use.direct"}"""

    @Test fun newAccountRegistersWithoutReusingThePreviousWorkspace() = runTest {
        val secrets = oldAccount()
        val http = HttpClient(MockEngine { request ->
            assertTrue((request.body as io.ktor.http.content.TextContent).text.contains("client_id=new-client"))
            respond(tokenResponse)
        })
        val account = ChatGptAccount(ChatGptAuth(http), secrets)
        val start = account.beginSignIn()
        assertEquals(ChatGptAuth.REGISTER, Url(start).parameters["client_id"])
        assertEquals("old-refresh", secrets.get("chatgpt_refresh_token"), "preserved until success")
        account.finishSignIn(callback(start))
        assertEquals("new-client", secrets.get("chatgpt_client_id"))
        assertEquals("new-refresh", secrets.get("chatgpt_refresh_token"))
        assertNull(account.account())
        assertTrue(account.knownModels.value.isEmpty())
        account.signOut()
        assertNull(secrets.get("chatgpt_client_id"))
        assertFalse(account.connected.value)
        http.close()
    }

    @Test fun missingRegistrationIdCannotFallBackToTheOldAccount() = runTest {
        val secrets = oldAccount()
        val http = HttpClient(MockEngine { error("must not exchange using old registration") })
        val account = ChatGptAccount(ChatGptAuth(http), secrets)
        assertFailsWith<LlmFailed> { account.finishSignIn(callback(account.beginSignIn(), null)) }
        assertEquals("old-refresh", secrets.get("chatgpt_refresh_token"))
        assertEquals("old-workspace-client", secrets.get("chatgpt_client_id"))
        http.close()
    }

    @Test fun signOutWinsOverAnInFlightLogin() = runTest {
        val secrets = oldAccount()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val http = HttpClient(MockEngine {
            entered.complete(Unit)
            release.await()
            respond(tokenResponse)
        })
        val account = ChatGptAccount(ChatGptAuth(http), secrets)
        val start = account.beginSignIn()
        val login = async { runCatching { account.finishSignIn(callback(start)) } }
        entered.await()
        account.signOut()
        release.complete(Unit)
        assertTrue(login.await().exceptionOrNull() is LlmFailed)
        assertNull(secrets.get("chatgpt_refresh_token"))
        assertFalse(account.connected.value)
        http.close()
    }

    @Test fun oldRefreshCannotOverwriteAReplacementAccount() = runTest {
        val secrets = oldAccount()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val http = HttpClient(MockEngine { request ->
            val body = (request.body as io.ktor.http.content.TextContent).text
            if ("grant_type=refresh_token" in body) {
                entered.complete(Unit)
                release.await()
                respond(tokenResponse.replace("new-refresh", "stale-refresh"))
            } else respond(tokenResponse)
        })
        val account = ChatGptAccount(ChatGptAuth(http), secrets)
        val refresh = async { runCatching { account.accessToken() } }
        entered.await()
        account.finishSignIn(callback(account.beginSignIn()))
        release.complete(Unit)
        assertTrue(refresh.await().exceptionOrNull() is LlmFailed)
        assertEquals("new-refresh", secrets.get("chatgpt_refresh_token"))
        assertEquals("new-client", secrets.get("chatgpt_client_id"))
        http.close()
    }

    @Test fun cancelledLoginCannotReplaceTheActiveAccount() = runTest {
        val secrets = oldAccount()
        val http = HttpClient(MockEngine { error("cancelled") })
        val account = ChatGptAccount(ChatGptAuth(http), secrets)
        val start = account.beginSignIn()
        account.cancelSignIn()
        assertFailsWith<LlmFailed> { account.finishSignIn(callback(start)) }
        assertEquals("old-refresh", secrets.get("chatgpt_refresh_token"))
        http.close()
    }

    @Test fun failedReplacementPreservesAllExistingCredentialsAndModels() = runTest {
        for (response in listOf(
            tokenResponse.replace("chatgpt.tokens.use.direct", "openid"),
            tokenResponse.replace("new-refresh", ""),
        )) {
            val secrets = oldAccount()
            val http = HttpClient(MockEngine { respond(response) })
            val account = ChatGptAccount(ChatGptAuth(http), secrets)
            assertFailsWith<LlmFailed> { account.finishSignIn(callback(account.beginSignIn())) }
            assertEquals("old-refresh", secrets.get("chatgpt_refresh_token"))
            assertEquals("old-access", secrets.get("chatgpt_access_token"))
            assertEquals("old-workspace-client", secrets.get("chatgpt_client_id"))
            assertEquals(listOf("old-model"), account.knownModels.value)
            assertTrue(account.connected.value)
            http.close()
        }
    }

    @Test fun signOutDuringRefreshCannotRestoreThePreviousAccount() = runTest {
        val secrets = oldAccount()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val http = HttpClient(MockEngine { request ->
            val body = (request.body as io.ktor.http.content.TextContent).text
            assertTrue("client_id=old-workspace-client" in body, "refresh keeps the active registration")
            entered.complete(Unit)
            release.await()
            respond(tokenResponse)
        })
        val account = ChatGptAccount(ChatGptAuth(http), secrets)
        val refresh = async { runCatching { account.accessToken() } }
        entered.await()
        account.signOut()
        release.complete(Unit)
        assertTrue(refresh.await().exceptionOrNull() is LlmFailed)
        assertNull(secrets.get("chatgpt_client_id"))
        assertNull(secrets.get("chatgpt_refresh_token"))
        assertTrue(account.knownModels.value.isEmpty())
        http.close()
    }
}
