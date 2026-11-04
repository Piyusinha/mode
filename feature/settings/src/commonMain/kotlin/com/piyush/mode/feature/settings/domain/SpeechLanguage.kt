package com.piyush.mode.feature.settings.domain

/**
 * The language the user speaks to Mode (Settings → Listening → Language). Mode always works in English:
 * a request in another language is translated before the task starts, and Mode answers in the language
 * spoken unless the user picked English for replies.
 */
public enum class SpeechLanguage(public val id: String, public val english: String, public val native: String) {
    /** Apple listens in this iPhone's language; Soniox and Deepgram detect it. */
    Auto(AUTO_ID, "Detect it each time", ""),
    EnglishIndia("en-IN", "English (India)", ""),
    EnglishUS("en-US", "English (US)", ""),
    EnglishUK("en-GB", "English (UK)", ""),
    Hindi("hi-IN", "Hindi", "हिन्दी"),
    Gujarati("gu-IN", "Gujarati", "ગુજરાતી"),
    Marathi("mr-IN", "Marathi", "मराठी"),
    Bengali("bn-IN", "Bengali", "বাংলা"),
    Tamil("ta-IN", "Tamil", "தமிழ்"),
    Telugu("te-IN", "Telugu", "తెలుగు"),
    Kannada("kn-IN", "Kannada", "ಕನ್ನಡ"),
    Malayalam("ml-IN", "Malayalam", "മലയാളം"),
    Spanish("es-ES", "Spanish", "Español"),
    French("fr-FR", "French", "Français"),
    German("de-DE", "German", "Deutsch"),
    ;

    public val isEnglish: Boolean get() = id.startsWith("en-")

    /** The language's name in English, for the model ("Hindi"); null for Auto. */
    public val languageName: String? get() = if (this == Auto) null else english.substringBefore(" (")

    /** The two-letter code cloud listeners take ("hi"); null for Auto. */
    public val code: String? get() = if (this == Auto) null else id.substringBefore('-')

    public companion object {
        public fun fromId(id: String?): SpeechLanguage = entries.firstOrNull { it.id == id } ?: Auto
    }
}

internal const val AUTO_ID: String = "auto"
