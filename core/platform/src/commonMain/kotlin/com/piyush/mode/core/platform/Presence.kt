package com.piyush.mode.core.platform

/**
 * Where Mode shows itself to the user while it works. On Android an overlay over the user's app can carry the whole
 * conversation (asking, answering, the result): then Mode must not bring its own app to the front.
 */
public interface ModePresence {
    /** An overlay over the user's app is showing Mode right now. */
    public val overlay: Boolean

    public companion object {
        /** No overlay (the iPhone, the Play build): Mode's app comes forward as before. */
        public val None: ModePresence = object : ModePresence {
            override val overlay: Boolean = false
        }
    }
}
