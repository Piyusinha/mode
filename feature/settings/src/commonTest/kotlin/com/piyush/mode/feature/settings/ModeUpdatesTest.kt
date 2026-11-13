package com.piyush.mode.feature.settings

import com.piyush.mode.feature.settings.data.ModeUpdate
import com.piyush.mode.feature.settings.data.ModeUpdates
import com.piyush.mode.testing.InMemoryKeyValueStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModeUpdatesTest {
    private fun rel(tag: String, draft: Boolean = false, ipa: Boolean = true) =
        """{"tag_name":"$tag","draft":$draft,"prerelease":false,"html_url":"https://x/$tag","assets":[${if (ipa) """{"name":"Mode.ipa"}""" else """{"name":"Mode-Setup-mac.dmg"}"""}]}"""

    private val list = "[" + listOf(
        rel("Mode-Desktop-App-0.1.9", ipa = false),
        rel("Mode-Mobile-Build-0.2.0", draft = true),
        rel("v0.1.2"),
        rel("Mode-Mobile-Build-0.1.10"),
        rel("Mode-Mobile-Build-0.1.4"),
    ).joinToString(",") + "]"

    @Test fun picksTheNewestPublishedModeByVersion() {
        assertEquals(ModeUpdate("0.1.10", "https://x/Mode-Mobile-Build-0.1.10"), ModeUpdates.newestOf(list))
    }

    @Test fun aReleaseIsAnUpdateOnlyWhereItCarriesThatPlatformsFile() {
        fun withFiles(tag: String, vararg files: String) =
            """{"tag_name":"$tag","draft":false,"prerelease":false,"html_url":"https://x/$tag","assets":[${files.joinToString(",") { """{"name":"$it"}""" }}]}"""
        // 0.1.10 came out for the iPhone only; Android's newest is 0.1.9.
        val both = "[" + withFiles("Mode-Mobile-Build-0.1.10", "Mode.ipa") + "," + withFiles("Mode-Mobile-Build-0.1.9", "Mode.ipa", "mode-android.apk") + "]"
        assertEquals("0.1.10", ModeUpdates.newestOf(both)?.version)
        assertEquals("0.1.9", ModeUpdates.newestOf(both, file = "mode-android.apk")?.version)
        assertNull(ModeUpdates.newestOf(list, file = "mode-android.apk"), "no Android build yet: nothing to offer")
    }

    @Test fun comparesVersions() {
        assertTrue(ModeUpdates.isNewer("0.1.10", "0.1.9"))
        assertTrue(ModeUpdates.isNewer("0.2", "0.1.9"))
        assertFalse(ModeUpdates.isNewer("0.1.3", "0.1.3"))
        assertFalse(ModeUpdates.isNewer("0.1.3.0", "0.1.3"))
        assertFalse(ModeUpdates.isNewer("next", "0.1.3"))
    }

    @Test fun offersTheNewerModeAndRemembersIt() = runTest {
        var calls = 0
        val http = HttpClient(MockEngine { calls++; respond(list) })
        val prefs = InMemoryKeyValueStore()
        var now = 1_000L
        val updates = ModeUpdates(http, prefs, current = "0.1.3", now = { now })
        assertNull(updates.available.value)
        updates.check()
        assertEquals("0.1.10", updates.available.value?.version)
        // Checked once a day: a second check the same day doesn't ask again.
        updates.check()
        assertEquals(1, calls)
        // The next launch knows it without asking.
        assertEquals("0.1.10", ModeUpdates(http, prefs, current = "0.1.3", now = { now }).available.value?.version)
        // Already on the newest: nothing to offer.
        now += 2L * 24 * 60 * 60 * 1000
        val newest = ModeUpdates(http, prefs, current = "0.1.10", now = { now })
        newest.check()
        assertNull(newest.available.value)
    }

    @Test fun aCheckThatFindsNothingForThisPlatformIsStillTodaysCheck() = runTest {
        var calls = 0
        val http = HttpClient(MockEngine { calls++; respond(list) })
        val updates = ModeUpdates(http, InMemoryKeyValueStore(), current = "0.1.3", now = { 1_000L }, file = "mode-android.apk")
        updates.check()
        updates.check()
        assertNull(updates.available.value)
        assertEquals(1, calls, "asked once, not every time Settings opens")
    }

    @Test fun offlineKeepsWhatItKnew() = runTest {
        val prefs = InMemoryKeyValueStore()
        ModeUpdates(HttpClient(MockEngine { respond(list) }), prefs, current = "0.1.3", now = { 0L }).check()
        val offline = ModeUpdates(HttpClient(MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }), prefs, current = "0.1.3", now = { 0L })
        offline.check(force = true)
        assertEquals("0.1.10", offline.available.value?.version)
    }
}
