package com.piyush.mode.feature.settings.data

import com.piyush.mode.core.storage.SecretStore
import com.piyush.mode.llm.AccountInfo
import com.piyush.mode.llm.ClaudeOAuth
import com.piyush.mode.llm.UsageWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * One encrypted entry for a rotated credential bundle, and the account's profile and last usage kept beside
 * it (so the account page shows them at once). No preferences, files, or credential logs.
 */
public class ClaudeCredentialStore(
    private val secrets: SecretStore,
    private val changed: () -> Unit = {},
) {
    private val mutableConnected = MutableStateFlow(load() != null)
    public val connected: StateFlow<Boolean> = mutableConnected.asStateFlow()

    /** Account identity changes invalidate profile/usage requests started for the previous account. */
    public var accountRevision: Long = 0
        private set

    public fun load(): ClaudeOAuth.Credentials? = secrets.get(KEY)?.let {
        runCatching { Json.decodeFromString<ClaudeOAuth.Credentials>(it) }.getOrNull()
    }

    /** Who is signed in, as Anthropic last said (asked once per sign-in, or when the user pulls to refresh). */
    public var profile: AccountInfo?
        get() = secrets.get(PROFILE)?.let { runCatching { Json.decodeFromString<AccountInfo>(it) }.getOrNull() }
        set(value) { secrets.set(PROFILE, value?.let { Json.encodeToString(it) }) }

    /** The plan's limits as last asked, shown while they are asked again. */
    public var usage: List<UsageWindow>?
        get() = secrets.get(USAGE)?.let { runCatching { Json.decodeFromString<List<UsageWindow>>(it) }.getOrNull() }
        set(value) { secrets.set(USAGE, value?.let { Json.encodeToString(it) }) }

    /** When [usage] was last answered by Claude (epoch ms). */
    public var usageAt: Long?
        get() = secrets.get(USAGE_AT)?.toLongOrNull()
        set(value) { secrets.set(USAGE_AT, value?.toString()) }

    public fun save(credentials: ClaudeOAuth.Credentials?, newSignIn: Boolean = false) {
        // Signed out, or a new sign-in (not a refresh of the one kept): what was kept about the account goes.
        if (newSignIn || credentials == null || load() == null) {
            accountRevision++
            profile = null
            usage = null
            usageAt = null
        }
        val encoded = credentials?.let { Json.encodeToString(it) }
        secrets.set(KEY, encoded)
        check(secrets.get(KEY) == encoded) { "Could not update Claude credentials in secure storage. Unlock this iPhone and try again." }
        mutableConnected.value = credentials != null
        changed()
    }

    private companion object {
        const val KEY = "claude_oauth_credentials"
        const val PROFILE = "claude_profile"
        const val USAGE = "claude_usage"
        const val USAGE_AT = "claude_usage_at"
    }
}
