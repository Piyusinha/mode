package com.piyush.mode.feature.settings.data

import com.piyush.mode.core.storage.SecretStore
import com.piyush.mode.feature.settings.domain.VoiceKind

/**
 * One provider's sign-in or key as it sits in the Keychain: the entries that make it up, and the one whose
 * presence means it is connected. A backup carries these whole, and a restore puts one back only when this
 * iPhone has no sign-in for that provider.
 */
public data class SignIn(
    val id: String,
    /** What the user sees ("ChatGPT", "OpenRouter", "ElevenLabs"). */
    val name: String,
    val connectedKey: String,
    val keys: List<String>,
) {
    public fun connected(secrets: SecretStore): Boolean = !secrets.get(connectedKey).isNullOrBlank()
}

/** Every AI and voice sign-in Mode keeps. Anything else in the Keychain (this iPhone's own ids) stays here. */
public object SignIns {
    public val all: List<SignIn> = buildList {
        add(SignIn("chatgpt", "ChatGPT", ChatGptAccount.CONNECTED_KEY, ChatGptAccount.ACCOUNT_KEYS))
        add(SignIn("claude", "Claude", ClaudeCredentialStore.CREDENTIALS_KEY, ClaudeCredentialStore.ACCOUNT_KEYS))
        add(SignIn("openrouter", "OpenRouter", SettingsRepository.API_KEY_NAME, listOf(SettingsRepository.API_KEY_NAME)))
        VoiceKind.entries.filter { it.needsKey }.forEach { kind ->
            val key = SettingsRepository.voiceSecretName(kind)
            add(SignIn(kind.id, voiceName(kind), key, listOf(key)))
        }
    }

    /** The providers connected on this iPhone, in [all]'s order. */
    public fun connected(secrets: SecretStore): List<SignIn> = all.filter { it.connected(secrets) }

    private fun voiceName(kind: VoiceKind): String = when (kind) {
        VoiceKind.Apple -> "Apple"
        VoiceKind.Speechify -> "Speechify"
        VoiceKind.Fish -> "Fish Audio"
        VoiceKind.ElevenLabs -> "ElevenLabs"
        VoiceKind.Soniox -> "Soniox"
        VoiceKind.Deepgram -> "Deepgram"
    }
}
