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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        assertEquals(list, Voices.withOurPick(VoiceKind.Apple, list))
        assertEquals(VoiceKind.Deepgram, VoiceKind.MOST_ACCURATE)
    }

    @Test
    fun speechifySpeaksTheChosenVoiceInTheUsersLanguageAndSpeed() = runTest {
        val settings = SettingsRepository(InMemorySecretStore(), InMemoryKeyValueStore())
        settings.setVoiceKey(VoiceKind.Speechify, "sk-speechify-12345678")
        settings.update {
            it.copy(
                speechOrder = listOf("speechify", "apple"), speechSpeed = com.piyush.mode.feature.settings.domain.SpeechSpeed.Faster,
                language = "hi-IN",
            )
        }
        var url = ""
        var body = ""
        var auth = ""
        var accept = ""
        val stream = StreamPhone().also { it.install() }
        val http = HttpClient(MockEngine { request ->
            url = request.url.toString(); auth = request.headers["Authorization"].orEmpty(); accept = request.headers["Accept"].orEmpty()
            body = (request.body as io.ktor.http.content.TextContent).text
            respond(byteArrayOf(1, 2, 3, 4, 5, 6))
        })
        val voices = Voices(settings, http)
        voices.speak("On it & done.")
        stream.uninstall()
        assertEquals("https://api.speechify.ai/v1/audio/stream", url)
        assertEquals("Bearer sk-speechify-12345678", auth)
        assertEquals("audio/pcm", accept)
        assertEquals(24_000, stream.rate)
        val json = kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject
        assertEquals("mihika", json["voice_id"]?.jsonPrimitive?.content, "no voice picked: Speechify's top Hindi voice")
        assertEquals("simba-3.5", json["model"]?.jsonPrimitive?.content)
        assertEquals("hi-IN", json["language"]?.jsonPrimitive?.content)
        assertEquals("<speak><prosody rate=\"115%\">On it &amp; done.</prosody></speak>", json["input"]?.jsonPrimitive?.content)
        assertEquals("pcm_24000", json["output_format"]?.jsonPrimitive?.content)
        assertEquals(listOf<Byte>(1, 2, 3, 4, 5, 6), stream.played.toList())
        assertEquals(1, stream.ended)
        assertEquals(null, voices.problem.value)
    }

    @Test
    fun speechifyAtNormalSpeedSendsPlainTextAndARefusalSaysWhy() = runTest {
        val settings = SettingsRepository(InMemorySecretStore(), InMemoryKeyValueStore())
        settings.setVoiceKey(VoiceKind.Speechify, "sk-speechify-12345678")
        settings.update {
            it.copy(speechOrder = listOf("speechify", "apple"),
                voices = mapOf("speechify" to com.piyush.mode.feature.settings.domain.VoiceChoice("harper_32", "Harper")))
        }
        var body = ""
        val stream = StreamPhone().also { it.install() }
        val http = HttpClient(MockEngine { request ->
            body = (request.body as io.ktor.http.content.TextContent).text
            respond("""{"error":{"code":"payment_required"}}""", status = HttpStatusCode.PaymentRequired)
        })
        val voices = Voices(settings, http)
        voices.speak("Done.")
        stream.uninstall()
        assertEquals(0, stream.begun, "nothing plays when Speechify refuses")
        val json = kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject
        assertEquals("Done.", json["input"]?.jsonPrimitive?.content)
        assertEquals("harper_32", json["voice_id"]?.jsonPrimitive?.content)
        assertEquals(null, json["language"])
        assertEquals(VoiceProblem(VoiceKind.Speechify, hearing = false, VoiceTrouble.PlanLimit), voices.problem.value)
    }

    @Test
    fun speechifyVoicesListStaffPicksFirstWithHarperAsModesPick() = runTest {
        val settings = SettingsRepository(InMemorySecretStore(), InMemoryKeyValueStore())
        settings.setVoiceKey(VoiceKind.Speechify, "sk-speechify-12345678")
        var asked = ""
        val http = HttpClient(MockEngine { request ->
            asked = request.url.toString()
            respond("""{"has_more":false,"next_cursor":null,"voices":[
                {"id":"oliver","display_name":"Oliver","gender":"male","locale":"en-US","type":"shared","models":[]},
                {"id":"mihika","display_name":"Mihika","gender":"female","locale":"hi-IN","type":"shared","models":[],"staff_pick_rank":1,"preview_audio":"https://x/m.mp3"},
                {"id":"harper_32","display_name":"Harper","gender":"female","locale":"en-US","type":"shared","models":[],"staff_pick_rank":1},
                {"id":"wyatt_32","display_name":"Wyatt","gender":"male","locale":"en-US","type":"shared","models":[],"staff_pick_rank":2}
            ]}""")
        })
        val voices = Voices(settings, http)
        kotlin.test.assertTrue(asked.isEmpty())
        val list = voices.options(VoiceKind.Speechify).options
        kotlin.test.assertTrue("type=shared" in asked, asked)
        assertEquals(listOf("mihika", "harper_32", "wyatt_32", "oliver"), list.map { it.id })
        assertEquals(VoiceOption("mihika", "Mihika", "Female · Hindi", "Hindi", "https://x/m.mp3", Voices.POPULAR), list.first())
        val ranked = Voices.withOurPick(VoiceKind.Speechify, list)
        assertEquals("harper_32", ranked.first().id)
        assertEquals(Voices.OUR_PICK, ranked.first().tag)
    }

    @Test
    fun aSpeechifyKeyIsCheckedAgainstItsVoices() = runTest {
        val settings = SettingsRepository(InMemorySecretStore(), InMemoryKeyValueStore())
        var asked = ""
        val http = HttpClient(MockEngine { request ->
            asked = request.url.toString()
            if (request.headers["Authorization"] == "Bearer good-key-1234") respond("{}") else respond("no", status = HttpStatusCode.Unauthorized)
        })
        val voices = Voices(settings, http)
        assertEquals(KeyCheck.Valid, voices.checkKey(VoiceKind.Speechify, "good-key-1234"))
        assertEquals("https://api.speechify.ai/v1/voices?limit=1", asked)
        assertEquals(KeyCheck.Rejected(VoiceTrouble.KeyRejected), voices.checkKey(VoiceKind.Speechify, "bad-key-1234"))
    }

    @Test
    fun speechifyIsOfferedButAppleStaysTheDefaultVoice() {
        kotlin.test.assertTrue(VoiceKind.Speechify in VoiceKind.SPEECH)
        assertEquals(VoiceKind.Apple.id, VoiceKind.speechDefault().first())
        // A saved order from before Speechify existed gains it without changing who speaks.
        val old = com.piyush.mode.feature.settings.domain.ModeSettings(speechOrder = listOf("elevenlabs", "apple", "fish")).validated()
        assertEquals(listOf("elevenlabs", "apple", "fish", "speechify"), old.speechOrder)
    }

    @Test
    fun speechifyStreamsOnlyWholeSamplesWhateverTheChunks() = runTest {
        // A 16-bit sample split across two network reads is joined before it plays.
        val settings = SettingsRepository(InMemorySecretStore(), InMemoryKeyValueStore())
        settings.setVoiceKey(VoiceKind.Speechify, "sk-speechify-12345678")
        settings.update { it.copy(speechOrder = listOf("speechify", "apple")) }
        val stream = StreamPhone().also { it.install() }
        val http = HttpClient(MockEngine { respond(io.ktor.utils.io.ByteReadChannel(byteArrayOf(1, 2, 3, 4, 5, 6, 7))) })
        Voices(settings, http).speak("Hello there.")
        stream.uninstall()
        assertEquals(listOf<Byte>(1, 2, 3, 4, 5, 6), stream.played.toList(), "a lone last byte is not half a sample played")
        kotlin.test.assertTrue(stream.chunks.all { it % 2 == 0 }, stream.chunks.toString())
    }

    /** The phone's streaming player, faked: records what it was given and reports sound once the stream ends. */
    private class StreamPhone {
        var rate = 0
        var begun = 0
        var ended = 0
        var played = byteArrayOf()
        val chunks = mutableListOf<Int>()
        private var done: ((Boolean) -> Unit)? = null
        fun install() {
            DeviceVoice.streamBegin = { r, d -> rate = r; begun++; done = d; true }
            DeviceVoice.streamChunk = { b -> played += b; chunks += b.size }
            DeviceVoice.streamEnd = { ended++; done?.invoke(played.isNotEmpty()) }
        }
        fun uninstall() { DeviceVoice.streamBegin = null; DeviceVoice.streamChunk = {}; DeviceVoice.streamEnd = {} }
    }
}
