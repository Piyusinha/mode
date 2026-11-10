package com.piyush.mode.core.platform

/** Facts about this app install. */
public interface AppInfo {
    /** This app's bundle id, to bring Mode back to the front after driving another app. */
    public val bundleId: String

    /** This Mode's version ("0.1.3"), to tell whether a newer one is out. */
    public val version: String get() = ""

    /** The file a Mode release carries for this platform ("Mode.ipa", "Mode.apk"): a release without it isn't an update here. */
    public val releaseFile: String get() = "Mode.ipa"

    /** CPU time this process has used so far (user and system, every thread), in milliseconds; null where unknown. */
    public fun cpuTimeMs(): Long? = null
}

/** The app moving between foreground, background and ending. */
public interface AppLifecycle {
    /** Calls [action] whenever Mode goes to the background (the user left it). */
    public fun onBackground(action: () -> Unit)

    /** Calls [action] whenever Mode comes back to the front (opened, or a notification tapped). */
    public fun onForeground(action: () -> Unit) {}

    /** As [onForeground], but the returned function stops the calls (for a presenter that closes). */
    public fun watchForeground(action: () -> Unit): () -> Unit {
        onForeground(action)
        return {}
    }

    /** Calls [action] when the OS is about to end Mode; it must finish quickly and not suspend. */
    public fun onTerminate(action: () -> Unit)
}

/** Keeps Mode alive while it works in the background (it is, whenever it drives another app). */
public interface BackgroundWork {
    public suspend fun <T> run(work: suspend () -> T): T
}

/** What kind of answer a question wants: Allow / Don't allow, one of a few options, or words. */
/** How a question can be answered: yes/no (with "don't ask again" when [YesNoAlways]), options, typed, or done. */
public enum class QuestionKind { YesNo, YesNoAlways, Choice, Text, YourTurn }

/** Other places a finished task's answer shows, so it isn't announced twice. */
public object ResultSurfaces {
    /** A Live Activity is showing the task, and will show its answer. */
    public var onLockScreen: Boolean = false
}

/** Local notifications: a task finished, or Mode needs the user. */
public interface Notifier {
    public fun requestPermission()

    /** Shown only while Mode is not on screen; in front, the app itself shows it. */
    public fun post(title: String, body: String)

    /**
     * The agent's question, answerable from the notification itself (Yes/No, option buttons, or a typed
     * reply) so Mode need not come to the front. [id] comes back with the answer.
     */
    public fun ask(id: String, text: String, kind: QuestionKind, options: List<String> = emptyList()) {
        post("Mode needs you", text)
    }

    /** Questions can be answered from a notification (allowed by the user); otherwise Mode opens to ask. */
    public fun canAskInPlace(): Boolean = false

    /**
     * A notification that opens [conversationId]'s chat when tapped (a finished task's answer); without one,
     * tapping just opens Mode.
     */
    public fun postFor(title: String, body: String, conversationId: String?) {
        post(title, body)
    }

    /** A finished task's answer: skipped while the Lock Screen already shows it (the Live Activity). Tapping opens its chat. */
    public fun postResult(title: String, body: String, conversationId: String? = null) {
        if (!ResultSurfaces.onLockScreen) postFor(title, body, conversationId)
    }

    /** A notification at [atEpochMs], replacing any earlier one with the same [id]. Tapping it opens Mode. */
    public fun schedule(id: String, title: String, body: String, atEpochMs: Long) {}

    /** Takes back a question's notification once it no longer waits (answered elsewhere, or moved on). */
    public fun withdraw(id: String) {}

    /** Takes back a scheduled notification. */
    public fun cancel(id: String) {}
}
