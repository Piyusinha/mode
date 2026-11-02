package com.piyush.mode.feature.settings

import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.llm.ClaudeOAuth
import com.piyush.mode.llm.ClaudeOAuthClient
import com.piyush.mode.llm.ModelProvider
import com.piyush.mode.testing.InMemoryKeyValueStore
import com.piyush.mode.testing.InMemorySecretStore
import kotlin.test.*
import com.piyush.mode.feature.settings.data.ClaudeAccount
import com.piyush.mode.llm.LlmFailed
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi

class ClaudeAccountTest {
    @Test fun closingSignInDoesNotCancelAnExistingTasksRefresh() = runTest {
        val settings = SettingsRepository(InMemorySecretStore(), InMemoryKeyValueStore())
        settings.claudeCredentials.save(ClaudeOAuth.Credentials("expired", "old-refresh", 0))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val http = HttpClient(MockEngine {
            entered.complete(Unit)
            release.await()
            respond("""{"access_token":"new-access","refresh_token":"rotated-refresh","expires_in":3600}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val account = ClaudeAccount(settings.claudeCredentials, ClaudeOAuth(http))
        val taskRefresh = async { account.accessToken() }
        entered.await()
        account.start()
        account.cancel()
        release.complete(Unit)
        assertEquals("new-access", taskRefresh.await())
        assertEquals("rotated-refresh", settings.claudeCredentials.load()!!.refreshToken)
        http.close()
    }

    @Test fun parallelCallsRefreshOnlyOnceAndPersistRotatedCredentials() = runTest {
        val settings = SettingsRepository(InMemorySecretStore(), InMemoryKeyValueStore())
        settings.claudeCredentials.save(ClaudeOAuth.Credentials("expired", "old-refresh", 0))
        var calls = 0
        val http = HttpClient(MockEngine {
            calls++
            respond("""{"access_token":"new-access","refresh_token":"rotated-refresh","expires_in":3600}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val account = ClaudeAccount(settings.claudeCredentials, ClaudeOAuth(http))
        assertEquals(listOf("new-access", "new-access"), listOf(async { account.accessToken() }, async { account.accessToken() }).awaitAll())
        assertEquals(1, calls)
        assertEquals("rotated-refresh", settings.claudeCredentials.load()!!.refreshToken)
        http.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun signOutDuringRefreshDoesNotRestoreCredentials() = runTest {
        val settings = SettingsRepository(InMemorySecretStore(), InMemoryKeyValueStore())
        settings.claudeCredentials.save(ClaudeOAuth.Credentials("expired", "old-refresh", 0))
        settings.selectProvider(ModelProvider.ClaudeOAuth)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val http = HttpClient(MockEngine {
            entered.complete(Unit)
            release.await()
            respond("""{"access_token":"new-access","refresh_token":"rotated-refresh","expires_in":3600}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val account = ClaudeAccount(settings.claudeCredentials, ClaudeOAuth(http))
        val request = async { runCatching { account.accessToken() } }
        entered.await()
        account.signOut()
        release.complete(Unit)
        assertTrue(request.await().exceptionOrNull() is LlmFailed)
        assertNull(settings.claudeCredentials.load())
        assertEquals(ModelProvider.ClaudeOAuth, settings.settings.value.provider, "sign-out must not switch to paid OpenRouter")
        assertFalse(settings.hasModelAccess)
        http.close()
    }

    @Test fun credentialsStayInSecretsAndSelectionsSurviveProviderSwitchesAndRestart() {
        val secrets = InMemorySecretStore()
        val prefs = InMemoryKeyValueStore()
        val settings = SettingsRepository(secrets, prefs)
        settings.apiKey = "router-key"
        settings.update { it.copy(model = "qwen/custom", strongModel = null) }
        settings.claudeCredentials.save(ClaudeOAuth.Credentials("access-secret", "refresh-secret", 1234))
        settings.selectProvider(ModelProvider.ClaudeOAuth)
        assertTrue(settings.hasModelAccess)
        assertEquals(ClaudeOAuthClient.MAIN_MODEL, settings.agentConfig().model)
        assertFalse(prefs.values.values.any { "access-secret" in it || "refresh-secret" in it })
        val restarted = SettingsRepository(secrets, prefs)
        assertEquals(ModelProvider.ClaudeOAuth, restarted.settings.value.provider)
        assertEquals("refresh-secret", restarted.claudeCredentials.load()!!.refreshToken)
        restarted.selectProvider(ModelProvider.OpenRouter)
        assertEquals("qwen/custom", restarted.agentConfig().model)
        assertNull(restarted.agentConfig().strongModel)
        assertEquals("router-key", restarted.apiKey)
        restarted.selectProvider(ModelProvider.ClaudeOAuth)
        restarted.claudeCredentials.save(null)
        assertFalse(restarted.hasModelAccess, "must not use the saved OpenRouter key when Claude is selected")
    }

    @Test fun replacementSignInClearsThePreviousAccountsProfileAndUsage() = runTest {
        val settings = SettingsRepository(InMemorySecretStore(), InMemoryKeyValueStore())
        val store = settings.claudeCredentials
        store.save(ClaudeOAuth.Credentials("old-access", "old-refresh", 0))
        store.profile = com.piyush.mode.llm.AccountInfo(email = "old@example.com")
        store.usage = emptyList()
        store.usageAt = 123L
        val http = HttpClient(MockEngine {
            respond("""{"access_token":"new-access","refresh_token":"new-refresh","expires_in":3600}""")
        })
        val account = ClaudeAccount(store, ClaudeOAuth(http))
        val state = Url(account.start()).parameters["state"]!!
        assertEquals("old@example.com", store.profile?.email)
        account.finish("code#$state")
        assertEquals("new-refresh", store.load()?.refreshToken)
        assertNull(store.profile)
        assertNull(store.usage)
        assertNull(store.usageAt)
        http.close()
    }
}
