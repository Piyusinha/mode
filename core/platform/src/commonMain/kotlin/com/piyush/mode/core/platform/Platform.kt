package com.piyush.mode.core.platform

/** Facts about this app install. */
public interface AppInfo {
    /** This app's bundle id, to bring Mode back to the front after driving another app. */
    public val bundleId: String
}

/** The app moving between foreground, background and ending. */
public interface AppLifecycle {
    /** Calls [action] whenever Mode goes to the background (the user left it). */
    public fun onBackground(action: () -> Unit)

    /** Calls [action] when the OS is about to end Mode; it must finish quickly and not suspend. */
    public fun onTerminate(action: () -> Unit)
}

/** Keeps Mode alive while it works in the background (it is, whenever it drives another app). */
public interface BackgroundWork {
    public suspend fun <T> run(work: suspend () -> T): T
}

/** Local notifications: a task finished, or Mode needs the user. */
public interface Notifier {
    public fun requestPermission()

    /** Shown only while Mode is not on screen; in front, the app itself shows it. */
    public fun post(title: String, body: String)
}
