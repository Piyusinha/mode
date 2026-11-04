package com.piyush.mode.app.graph

import com.piyush.mode.core.common.AppLog
import com.piyush.mode.ops.DeviceData
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Contacts.CNContact
import platform.Contacts.CNContactEmailAddressesKey
import platform.Contacts.CNContactFetchRequest
import platform.Contacts.CNContactFamilyNameKey
import platform.Contacts.CNContactGivenNameKey
import platform.Contacts.CNContactPhoneNumbersKey
import platform.Contacts.CNContactStore
import platform.Contacts.CNEntityType
import platform.Contacts.CNLabeledValue
import platform.Contacts.CNPhoneNumber
import platform.EventKit.EKEvent
import platform.EventKit.EKEventStore
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSURL
import platform.Foundation.dateByAddingTimeInterval
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Direct routes on this iPhone: a link or an app's URL opened by Mode itself, the calendar through EventKit,
 * contacts through Contacts. iOS asks the user for calendar and contacts access the first time; without it the
 * agent is told nothing was found and uses the apps' screens instead.
 */
@OptIn(ExperimentalForeignApi::class)
class IosDeviceData : DeviceData {
    override val canOpenUrl: Boolean = true
    override val hasCalendar: Boolean = true
    override val hasContacts: Boolean = true

    private val events = EKEventStore()
    private val contactStore = CNContactStore()

    override suspend fun openUrl(url: String): Boolean = withContext(Dispatchers.Main) {
        val nsUrl = NSURL.URLWithString(url) ?: return@withContext false
        suspendCoroutine { cont ->
            UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = { ok -> cont.resume(ok) })
        }.also { if (!it) AppLog.log("device", "could not open ${url.take(60)}") }
    }

    override suspend fun calendar(days: Int): List<String> {
        val granted = withContext(Dispatchers.Main) {
            suspendCoroutine<Boolean> { cont -> events.requestFullAccessToEventsWithCompletion { ok, _ -> cont.resume(ok) } }
        }
        if (!granted) return emptyList()
        val start = NSDate()
        val end = start.dateByAddingTimeInterval(days * 86_400.0)
        val predicate = events.predicateForEventsWithStartDate(start, end, null)
        val day = NSDateFormatter().apply { dateFormat = "EEE d MMM" }
        val time = NSDateFormatter().apply { dateFormat = "HH:mm" }
        return events.eventsMatchingPredicate(predicate).mapNotNull { it as? EKEvent }
            .sortedBy { it.startDate?.timeIntervalSince1970 ?: 0.0 }
            .map { e ->
                val s = e.startDate
                val f = e.endDate
                val when_ = if (s == null) "" else if (e.allDay) day.stringFromDate(s) else
                    day.stringFromDate(s) + " " + time.stringFromDate(s) + (f?.let { "–" + time.stringFromDate(it) }.orEmpty())
                listOfNotNull(when_, e.title, e.location?.takeIf { it.isNotBlank() }).joinToString(" · ")
            }
    }

    override suspend fun contacts(query: String): List<String> {
        val granted = withContext(Dispatchers.Main) {
            suspendCoroutine<Boolean> { cont -> contactStore.requestAccessForEntityType(CNEntityType.CNEntityTypeContacts) { ok, _ -> cont.resume(ok) } }
        }
        if (!granted) return emptyList()
        val keys = listOf(CNContactGivenNameKey, CNContactFamilyNameKey, CNContactPhoneNumbersKey, CNContactEmailAddressesKey)
        val wanted = query.trim().lowercase()
        val found = mutableListOf<CNContact>()
        contactStore.enumerateContactsWithFetchRequest(CNContactFetchRequest(keysToFetch = keys), error = null) { contact, _ ->
            val c = contact ?: return@enumerateContactsWithFetchRequest
            val name = (c.givenName + " " + c.familyName).lowercase()
            if (wanted.isEmpty() || name.contains(wanted) || wanted.split(' ').all { it.isNotEmpty() && name.contains(it) }) found += c
        }
        return found.take(MAX_CONTACTS).map { c ->
            val name = listOf(c.givenName, c.familyName).filter { it.isNotBlank() }.joinToString(" ")
            val phones = c.phoneNumbers.mapNotNull { ((it as? CNLabeledValue)?.value as? CNPhoneNumber)?.stringValue }
            val emails = c.emailAddresses.mapNotNull { (it as? CNLabeledValue)?.value?.toString() }
            (listOf(name) + phones + emails).filter { it.isNotBlank() }.joinToString(" · ")
        }
    }

    private companion object {
        const val MAX_CONTACTS = 10
    }
}
