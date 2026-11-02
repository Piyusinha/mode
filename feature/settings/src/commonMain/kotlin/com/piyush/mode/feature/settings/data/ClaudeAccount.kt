package com.piyush.mode.feature.settings.data

import kotlinx.coroutines.flow.StateFlow

import com.piyush.mode.llm.ClaudeOAuth
import com.piyush.mode.llm.LlmFailed
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/** Serializes rotated refresh-token writes. Sign-out wins over an in-flight login or refresh. */
public class ClaudeAccount(private val credentials: ClaudeCredentialStore, private val auth: ClaudeOAuth) {
    public val connected: StateFlow<Boolean> = credentials.connected
    private val lock = Mutex()
    private var pending: ClaudeOAuth.Start? = null
    private var generation = 0

    public fun start(): String {
        return auth.start().also { pending = it }.url
    }

    public fun cancel() {
        pending = null
    }

    public suspend fun finish(code: String) {
        val start = pending ?: throw LlmFailed("Start Claude sign-in first.")
        val attempt = generation
        val result = auth.exchange(code, start)
        if (attempt != generation || pending !== start) throw LlmFailed("Claude sign-in was cancelled. Start again.")
        pending = null
        credentials.save(result)
        generation++
    }

    public fun signOut() {
        cancel()
        generation++
        credentials.save(null)
    }

    public suspend fun accessToken(forceRefresh: Boolean = false): String = lock.withLock {
        val saved = credentials.load() ?: throw LlmFailed("Connect Claude in Settings first.")
        if (!forceRefresh && saved.expiresAt - Clock.System.now().toEpochMilliseconds() > 60_000) return@withLock saved.accessToken
        val attempt = generation
        val refreshed = auth.refresh(saved)
        if (attempt != generation || credentials.load()?.refreshToken != saved.refreshToken) {
            throw LlmFailed("Claude account changed. Try again.")
        }
        credentials.save(refreshed)
        refreshed.accessToken
    }
}
