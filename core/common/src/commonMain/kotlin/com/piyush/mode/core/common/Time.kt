package com.piyush.mode.core.common

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Now, as an ISO-8601 instant. */
@OptIn(ExperimentalTime::class)
public fun nowIso(): String = Clock.System.now().toString()

/** A sortable, file-name-safe timestamp, like 2026-10-31T23-40-12. */
@OptIn(ExperimentalTime::class)
public fun nowId(): String = Clock.System.now().toString().substring(0, 19).replace(':', '-')

/** Midnight at the start of today on this device's clock and time zone, in epoch milliseconds. */
public expect fun startOfTodayMillis(): Long
