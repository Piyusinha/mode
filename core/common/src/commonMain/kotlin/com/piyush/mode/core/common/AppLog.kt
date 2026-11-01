package com.piyush.mode.core.common

/**
 * The app-wide event timeline: connecting, the VPN, disk-image mounts,
 * reconnects, keep-awake, questions, errors. The app installs one sink at
 * start (on iOS: logs/events.log plus the system log, so a Mac can watch it).
 */
public object AppLog {
    private var sink: ((String) -> Unit)? = null

    public fun install(sink: (String) -> Unit) {
        this.sink = sink
    }

    public fun log(tag: String, message: String) {
        sink?.invoke("${nowIso()} [$tag] $message")
    }

    public fun error(tag: String, message: String, e: Throwable? = null): Unit =
        log(tag, "ERROR $message" + (e?.let { ": ${it.message ?: it}" } ?: ""))
}
