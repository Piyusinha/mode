package com.piyush.mode.feature.settings.data

import com.piyush.mode.core.storage.SecretStore
import com.piyush.mode.llm.ClaudeOAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/** One encrypted entry for a rotated credential bundle. No preferences, files, or credential logs. */
public class ClaudeCredentialStore(
    private val secrets: SecretStore,
    private val changed: () -> Unit = {},
) {
    private val mutableConnected = MutableStateFlow(load() != null)
    public val connected: StateFlow<Boolean> = mutableConnected.asStateFlow()

    public fun load(): ClaudeOAuth.Credentials? = secrets.get(KEY)?.let {
        runCatching { Json.decodeFromString<ClaudeOAuth.Credentials>(it) }.getOrNull()
    }

    public fun save(credentials: ClaudeOAuth.Credentials?) {
        val encoded = credentials?.let { Json.encodeToString(it) }
        secrets.set(KEY, encoded)
        check(secrets.get(KEY) == encoded) { "Could not update Claude credentials in secure storage. Unlock this iPhone and try again." }
        mutableConnected.value = credentials != null
        changed()
    }

    private companion object {
        const val KEY = "claude_oauth_credentials"
    }
}
