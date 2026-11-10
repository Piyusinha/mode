package com.piyush.mode.feature.settings

import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.feature.settings.voice.DeviceVoice
import com.piyush.mode.feature.settings.voice.Heard
import com.piyush.mode.feature.settings.voice.Voices
import com.piyush.mode.testing.InMemoryKeyValueStore
import com.piyush.mode.testing.InMemorySecretStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlin.time.ExperimentalTime::class)
class SpeakOrHearTest {
    private val settings = SettingsRepository(InMemorySecretStore(), InMemoryKeyValueStore())
    private val http = HttpClient(MockEngine { respond("{}") })

    private class Phone {
        var speakDone: ((Boolean) -> Unit)? = null
        var partial: ((String) -> Unit)? = null
        var listenDone: ((String?, String?, String) -> Unit)? = null
        var hushed = 0
        var cancelled = 0
        var linesSpoken = mutableListOf<String>()
        fun install() {
            DeviceVoice.speechReady = { true }
            DeviceVoice.hearingState = { "ready" }
            DeviceVoice.speak = { text, _, _, done -> linesSpoken += text; speakDone = done }
            DeviceVoice.listen = { _, _, _, p, d -> partial = p; listenDone = d }
            DeviceVoice.hush = { hushed++; speakDone?.invoke(true); speakDone = null }
            DeviceVoice.cancel = { cancelled++; listenDone?.invoke(null, null, "cancelled"); listenDone = null }
        }
        fun uninstall() { DeviceVoice.speak = null; DeviceVoice.listen = null; DeviceVoice.hush = {}; DeviceVoice.cancel = {}; DeviceVoice.hearingState = { "unsupported" } }
    }

    @Test
    fun talkingOverModeStopsTheLineAndTheirWordsComeBack() = runTest {
        val phone = Phone().also { it.install() }
        try {
            val voices = Voices(settings, http, timeSource = testScheduler.timeSource)
            launch {
                while (phone.partial == null) delay(10)
                phone.partial!!("use")
                delay(600) // past BargeIn.MIN_SPEECH_MS
                phone.partial!!("use the IndiGo app instead")
                while (phone.hushed == 0) delay(10)
                delay(50)
                phone.listenDone!!(null, "use the IndiGo app instead please", "quiet")
            }
            val heard = voices.speakOrHear("Okay, I'll check your IndiGo flight on Flightradar and tell you the gate.")
            assertEquals("use the IndiGo app instead please", assertIs<Heard.Words>(heard).text)
            assertEquals(1, phone.hushed, "Mode stopped mid-line once")
            assertEquals(0, phone.cancelled, "the microphone stayed open to the end of their turn")
        } finally { phone.uninstall() }
    }

    @Test
    fun aLineSaidToItsEndClosesTheMicrophoneAndGivesNothing() = runTest {
        val phone = Phone().also { it.install() }
        try {
            val voices = Voices(settings, http, timeSource = testScheduler.timeSource)
            launch {
                while (phone.partial == null) delay(10)
                phone.partial!!("okay")            // a nod
                delay(200)
                phone.speakDone!!(true)            // Mode finished its line
            }
            assertNull(voices.speakOrHear("Done, your flight is on time."))
            assertEquals(0, phone.hushed)
            assertEquals(1, phone.cancelled, "the watch listen is cancelled once the line ends")
        } finally { phone.uninstall() }
    }

    @Test
    fun withBargeInOffItJustSpeaks() = runTest {
        val phone = Phone().also { it.install() }
        try {
            settings.update { it.copy(bargeIn = false) }
            val voices = Voices(settings, http, timeSource = testScheduler.timeSource)
            launch { while (phone.speakDone == null) delay(10); phone.speakDone!!(true) }
            assertNull(voices.speakOrHear("Hello"))
            assertTrue(phone.partial == null, "no microphone was opened")
        } finally { phone.uninstall(); settings.update { it.copy(bargeIn = true) } }
    }
}
