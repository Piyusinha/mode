package com.piyush.mode.core.storage

/**
 * iOS keeps an app's Keychain when the app is deleted, but not its settings. Without this, Mode installed again
 * finds the last install's sign-ins (ChatGPT, Claude, OpenRouter, voice keys) and shows them as connected.
 */
public object FreshInstall {
    /** Set once Mode has started on this install. */
    public const val MARKER: String = "install.started"

    /**
     * Run once at start, before anything reads [secrets]. On a fresh install (no settings at all) it removes the
     * sign-ins an earlier install left in the Keychain; on an update the settings are there, so nothing is removed.
     * True when it cleared them.
     */
    public fun clearLeftovers(prefs: KeyValueStore, secrets: SecretStore): Boolean {
        if (prefs.getString(MARKER) != null) return false
        val fresh = prefs.names().isEmpty()
        if (fresh) secrets.clear()
        prefs.putString(MARKER, "1")
        return fresh
    }
}
