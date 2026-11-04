package com.piyush.mode.ops

/**
 * What Mode's own app can do on this phone without driving a screen: open a link or an app's URL scheme,
 * read the calendar, look up a contact. Direct routes are shorter and surer than taps (PhoneHarness: 75% vs
 * 62% for screens alone; ten steps instead of nineteen), so the agent is offered them first. Each is
 * optional: a platform says what it has.
 */
interface DeviceData {
    val canOpenUrl: Boolean get() = false
    val hasCalendar: Boolean get() = false
    val hasContacts: Boolean get() = false

    /** Opens [url] (https, an app's scheme, shortcuts://…); true when the system took it. */
    suspend fun openUrl(url: String): Boolean = false

    /** Events from now for [days] days, one line each ("Fri 7 Nov 09:30–10:30 · Standup · Office"); empty when none or no access. */
    suspend fun calendar(days: Int): List<String> = emptyList()

    /** Contacts whose name matches [query], one line each ("Asha Rao · +91 98765 43210 · asha@…"). */
    suspend fun contacts(query: String): List<String> = emptyList()

    object None : DeviceData
}
