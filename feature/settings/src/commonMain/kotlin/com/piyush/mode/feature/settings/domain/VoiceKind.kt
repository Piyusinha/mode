package com.piyush.mode.feature.settings.domain

/** One speech or hearing choice. Apple needs no key. The others do. */
public enum class VoiceKind(public val id: String, public val needsKey: Boolean) {
    /** The phone's own voice and hearing (Apple on iOS, Android's on Android). The id stays "apple": settings and backups keep working. */
    Device("apple", false),
    Speechify("speechify", true),
    Fish("fish", true),
    ElevenLabs("elevenlabs", true),
    Soniox("soniox", true),
    Deepgram("deepgram", true),
    ;

    public companion object {
        public val SPEECH: List<VoiceKind> = listOf(Device, Speechify, Fish, ElevenLabs)
        public val HEARING: List<VoiceKind> = listOf(Device, Soniox, Deepgram)
        public fun fromId(id: String): VoiceKind? = entries.firstOrNull { it.id == id }
        public fun speechDefault(): List<String> = SPEECH.map { it.id }
        public fun hearingDefault(): List<String> = HEARING.map { it.id }

        /** Mode's recommendation for listening: the most accurate. */
        public val MOST_ACCURATE: VoiceKind = Deepgram

        /** The voice Mode suggests on a provider, by its name there (null: none in particular). */
        public fun ourPick(kind: VoiceKind): String? = when (kind) {
            ElevenLabs -> "Rachel"
            Speechify -> "Harper"
            else -> null
        }
    }
}

/** A voice picked from a provider's list: its id there (Apple's identifier, ElevenLabs' voice_id, Speechify's voice id, Fish Audio's model id) and its name. */
@kotlinx.serialization.Serializable
public data class VoiceChoice(val id: String, val name: String)

/** How fast Mode talks; [factor] scales each provider's normal speed. */
public enum class SpeechSpeed(public val id: String, public val factor: Double) {
    Slower("slower", 0.85), Normal("normal", 1.0), Faster("faster", 1.15);

    public companion object {
        public fun fromId(id: String?): SpeechSpeed = entries.firstOrNull { it.id == id } ?: Normal
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
