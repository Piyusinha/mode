package com.piyush.mode.app.screens.home

import com.piyush.mode.feature.settings.domain.ModeSettings
import com.piyush.mode.feature.settings.domain.RiskConfirmation
import com.piyush.mode.feature.settings.domain.VoiceKind

/**
 * The "Did you know?" tips on Home: what the short first run left out, all of them every day for the first
 * days (a carousel that moves on by itself and swipes). Each is done (and left out) once its setting has
 * been changed.
 */
public enum class SetupTip {
    /** Mo's voice: talk back out loud. */
    Voice,

    /** Who listens (dictation), and the language. */
    Listening,

    /** How much Mo asks before it acts. */
    Access,

    /** Siri, and Unlock by voice. */
    StartWithoutOpening,

    /** The user's name. */
    Name;

    /**
     * The user changed this away from the default, so the tip is no longer shown (only this one):
     * a voice picked or a voice other than Apple's speaking; a listener other than Apple's in use;
     * Unlock by voice set up; and so on.
     */
    internal fun done(s: ModeSettings): Boolean = when (this) {
        Voice -> s.voices.isNotEmpty() || s.speechOrder.firstOrNull() != VoiceKind.Apple.id
        Listening -> s.hearingOrder.firstOrNull() != VoiceKind.Apple.id
        Access -> s.fullAccess || s.askBeforeRisky != RiskConfirmation.OnlyVeryRisky
        StartWithoutOpening -> s.voiceUnlock
        Name -> !s.userName.isNullOrBlank()
    }
}

/** The banner: the tips not done yet, the one showing, and whether it can be closed for good yet. */
public data class TipsUiState(
    val tips: List<SetupTip>,
    val showing: SetupTip,
    val closable: Boolean,
)

public object SetupTips {
    /** The tips show for this many days after Home first shows them. */
    public const val DAYS: Int = 5

    /** From this day on the banner has a close button. */
    public const val CLOSABLE_FROM_DAY: Int = 3

    internal const val DAY_MS: Long = 24 * 60 * 60 * 1000L

    /**
     * The banner for [s] at [nowMs], or null: before setup ends, once closed, after [DAYS] days, or when every
     * tip is done. It shows every tip not done yet, starting with the first; [picked]: the one swiped or
     * moved to.
     */
    public fun banner(s: ModeSettings, nowMs: Long, picked: SetupTip? = null): TipsUiState? {
        if (!s.onboardingDone || s.tipsHidden) return null
        val start = s.tipsStartedAtMs ?: return null
        val day = ((nowMs - start).coerceAtLeast(0) / DAY_MS).toInt() + 1
        if (day > DAYS) return null
        val open = SetupTip.entries.filter { !it.done(s) }
        if (open.isEmpty()) return null
        val showing = picked?.takeIf { it in open } ?: open.first()
        return TipsUiState(open, showing, closable = day >= CLOSABLE_FROM_DAY)
    }
}
