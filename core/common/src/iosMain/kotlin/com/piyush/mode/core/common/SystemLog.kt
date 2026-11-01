package com.piyush.mode.core.common

import platform.Foundation.NSLog

/**
 * Writes [line] to the iOS system log, where a Mac can follow it live.
 * One preformatted string: Kotlin strings cannot go through NSLog's varargs
 * (%@ crashed at launch), and a literal % must not be read as a format.
 */
public fun systemLog(line: String) {
    NSLog(("Mode " + line).replace("%", "%%"))
}
