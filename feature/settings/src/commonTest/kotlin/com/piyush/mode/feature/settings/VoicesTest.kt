package com.piyush.mode.feature.settings

import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.testing.InMemoryKeyValueStore
import com.piyush.mode.testing.InMemorySecretStore
import com.piyush.mode.feature.settings.domain.VoiceKind
import com.piyush.mode.feature.settings.voice.DeviceVoice
import com.piyush.mode.feature.settings.voice.Heard
import com.piyush.mode.feature.settings.voice.Voices
import com.piyush.mode.feature.settings.voice.VoiceOption
import com.piyush.mode.feature.settings.voice.VoiceProblem
import com.piyush.mode.feature.settings.voice.VoiceTrouble
import com.piyush.mode.feature.settings.voice.KeyCheck
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class VoicesTest {
    @Test
    fun theChosenVoiceFailingSaysWhyAndNoOtherVoiceSpeaks() = runTest {
        val settings = SettingsRepository(InMemorySecretStore(), InMemoryKeyValueStore())
        settings.setVoiceKey(VoiceKind.Fish, "fish-key-12345678")
        settings.setVoiceKey(VoiceKind.ElevenLabs, "eleven-key-12345678")
        settings.update { it.copy(speechOrder = listOf("elevenlabs", "fish", "apple")) }
        DeviceVoice.speechReady = { true }
        var plays = 0
        var posts = 0
        DeviceVoice.play = { _, done -> plays++; done(true) }
        // ElevenLabs refuses: the plan doesn't include this voice.
        val http = HttpClient(MockEngine { posts++; respond("no", status = HttpStatusCode.PaymentRequired) })
        val voices = Voices(settings, http)
        voices.speak("Do you want to order this?")
        assertEquals(1, posts)
        assertEquals(0, plays)
        assertEquals(VoiceProblem(VoiceKind.ElevenLabs, hearing = false, VoiceTrouble.PlanLimit), voices.problem.value)
        DeviceVoice.play = null
    }

    @Test
    fun aKeyIsCheckedWithTheProviderBeforeItIsSaved() = runTest {
        val settings = SettingsRepository(InMemorySecretStore(), InMemoryKeyValueStore())
        val http = HttpClient(MockEngine { request ->
            when (request.headers["Authorization"] ?: request.headers["xi-api-key"]) {
                "Token good-key-1234", "good-key-1234" -> respond("{}")
                else -> respond("no", status = HttpStatusCode.Unauthorized)
            }
        })
        val voices = Voices(settings, http)
        assertEquals(KeyCheck.Valid, voices.checkKey(VoiceKind.Deepgram, "good-key-1234"))
        assertEquals(KeyCheck.Valid, voices.checkKey(VoiceKind.ElevenLabs, " good-key-1234 "))
        assertEquals(KeyCheck.Rejected(VoiceTrouble.KeyRejected), voices.checkKey(VoiceKind.Deepgram, "bad-key-1234"))
        assertEquals(KeyCheck.Rejected(VoiceTrouble.NoKey), voices.checkKey(VoiceKind.Soniox, "  "))
    }

    @Test
    fun silenceDoesNotUpload() = runTest {
        val settings = SettingsRepository(InMemorySecretStore(), InMemoryKeyValueStore())
        settings.setVoiceKey(VoiceKind.Deepgram, "dg-key-12345678")
        settings.update { it.copy(hearingOrder = listOf("deepgram", "apple", "soniox")) }
        DeviceVoice.hearingState = { "unsupported" }
        DeviceVoice.record = { _, _, done -> done(null) }
        var posts = 0
        val http = HttpClient(MockEngine { posts++; respond("") })
        assertIs<Heard.Silence>(Voices(settings, http).hear())
        assertEquals(0, posts)
        DeviceVoice.record = null
        DeviceVoice.hearingState = { "unsupported" }
    }

    @Test
    fun elevenLabsAndFishVoicesAreListedWithSamples() = runTest {
        val settings = SettingsRepository(InMemorySecretStore(), InMemoryKeyValueStore())
        settings.setVoiceKey(VoiceKind.ElevenLabs, "eleven-key-12345678")
        settings.setVoiceKey(VoiceKind.Fish, "fish-key-12345678")
        val http = HttpClient(MockEngine { request ->
            when {
                request.url.host.contains("elevenlabs") -> {
                    assertEquals("eleven-key-12345678", request.headers["xi-api-key"])
                    respond("""{"voices":[{"voice_id":"v1","name":"Sarah - soft","labels":{"gender":"female","age":"young","accent":"american","use_case":"conversational"},"preview_url":"https://x/s.mp3"}],"has_more":false}""")
                }
                else -> {
                    assertEquals("Bearer fish-key-12345678", request.headers["Authorization"])
                    respond("""{"total":1,"items":[{"_id":"m1","title":"Calm Guide","languages":["en","hi"],"task_count":1234567,"samples":[{"audio":"https://f/a.mp3"}]}]}""")
                }
            }
        })
        val voices = Voices(settings, http)
        val sarah = voices.options(VoiceKind.ElevenLabs).options.single()
        assertEquals("Sarah", sarah.name)
        assertEquals("Female · Young · American", sarah.traits)
        assertEquals("Conversational", sarah.group)
        assertEquals("https://x/s.mp3", sarah.preview)
        val guide = voices.options(VoiceKind.Fish).options.single()
        assertEquals("English · Hindi · 1.2M uses", guide.traits)
        assertEquals(Voices.POPULAR, guide.tag)
        assertEquals("English", guide.group)
    }

    @Test
    fun withoutAKeyTheListSaysSo() = runTest {
        val settings = SettingsRepository(InMemorySecretStore(), InMemoryKeyValueStore())
        val list = Voices(settings, HttpClient(MockEngine { error("not called") })).options(VoiceKind.Fish)
        assertEquals(com.piyush.mode.feature.settings.voice.VoiceListError.NoKey, list.error)
    }

    @Test
    fun theChosenVoiceAndSpeedAreSent() = runTest {
        val settings = SettingsRepository(InMemorySecretStore(), InMemoryKeyValueStore())
        settings.setVoiceKey(VoiceKind.ElevenLabs, "eleven-key-12345678")
        settings.update {
            it.copy(
                speechOrder = listOf("elevenlabs", "apple", "fish"), speechSpeed = com.piyush.mode.feature.settings.domain.SpeechSpeed.Faster,
                voices = mapOf("elevenlabs" to com.piyush.mode.feature.settings.domain.VoiceChoice("v1", "Sarah")),
            )
        }
        var url = ""
        var body = ""
        DeviceVoice.play = { _, done -> done(true) }
        val http = HttpClient(MockEngine { request ->
            url = request.url.toString(); body = (request.body as io.ktor.http.content.TextContent).text
            respond(byteArrayOf(1, 2, 3))
        })
        Voices(settings, http).speak("On it.")
        DeviceVoice.play = null
        assertEquals("https://api.elevenlabs.io/v1/text-to-speech/v1", url)
        kotlin.test.assertTrue("\"speed\":1.15" in body, body)
        // Kept across launches.
        assertEquals("Sarah", SettingsRepository(InMemorySecretStore(), InMemoryKeyValueStore()).let { settings.settings.value.voices["elevenlabs"]?.name })
    }

    @Test
    fun modesPickComesFirstAndTaggedOnlyWhereItHasOne() {
        val list = listOf(VoiceOption("a", "Adam"), VoiceOption("r", "Rachel"), VoiceOption("g", "George"))
        val eleven = Voices.withOurPick(VoiceKind.ElevenLabs, list)
        assertEquals(listOf("r", "a", "g"), eleven.map { it.id })
        assertEquals(Voices.OUR_PICK, eleven.first().tag)
        assertEquals(list, Voices.withOurPick(VoiceKind.Device, list))
        assertEquals(VoiceKind.ElevenLabs, VoiceKind.MOST_HUMAN)
        assertEquals(VoiceKind.Deepgram, VoiceKind.MOST_ACCURATE)
    }
}
