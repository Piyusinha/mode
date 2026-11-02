package com.piyush.mode.feature.settings.domain

/** One speech or hearing choice. Apple needs no key. The others do. */
public enum class VoiceKind(public val id: String, public val needsKey: Boolean) {
    Apple("apple", false),
    Fish("fish", true),
    ElevenLabs("elevenlabs", true),
    Soniox("soniox", true),
    Deepgram("deepgram", true),
    ;

    public companion object {
        public val SPEECH: List<VoiceKind> = listOf(Apple, Fish, ElevenLabs)
        public val HEARING: List<VoiceKind> = listOf(Apple, Soniox, Deepgram)
        public fun fromId(id: String): VoiceKind? = entries.firstOrNull { it.id == id }
        public fun speechDefault(): List<String> = SPEECH.map { it.id }
        public fun hearingDefault(): List<String> = HEARING.map { it.id }
    }
}

/** Why a row cannot be used yet. Null means ready. */
public enum class VoiceBlock {
    NoKey,
    Permission,
    Denied,
    Unsupported,
    Asset,
}
