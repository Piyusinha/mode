package com.piyush.mode.feature.settings

import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.testing.InMemoryKeyValueStore
import com.piyush.mode.testing.InMemorySecretStore
import com.piyush.mode.feature.settings.domain.VoiceKind
import com.piyush.mode.feature.settings.voice.DeviceVoice
import com.piyush.mode.feature.settings.voice.Heard
import com.piyush.mode.feature.settings.voice.Voices
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
    fun aRefusalBeforeSoundTriesTheNextVoiceOnce() = runTest {
        val settings = SettingsRepository(InMemorySecretStore(), InMemoryKeyValueStore())
        settings.setVoiceKey(VoiceKind.Fish, "fish-key-12345678")
        settings.setVoiceKey(VoiceKind.ElevenLabs, "eleven-key-12345678")
        DeviceVoice.speechReady = { false }
        var plays = 0
        var posts = 0
        DeviceVoice.play = { _, done -> plays++; done(true) }
        val http = HttpClient(MockEngine { request ->
            posts++
            if (request.url.host.contains("fish")) respond("no", status = HttpStatusCode.Unauthorized)
            else respond(byteArrayOf(1, 2, 3))
        })
        Voices(settings, http).speak("Do you want to order this?")
        assertEquals(2, posts)
        assertEquals(1, plays)
        DeviceVoice.play = null
    }

    @Test
    fun silenceDoesNotUpload() = runTest {
        val settings = SettingsRepository(InMemorySecretStore(), InMemoryKeyValueStore())
        settings.setVoiceKey(VoiceKind.Deepgram, "dg-key-12345678")
        DeviceVoice.hearingState = { "unsupported" }
        DeviceVoice.record = { _, _, done -> done(null) }
        var posts = 0
        val http = HttpClient(MockEngine { posts++; respond("") })
        assertIs<Heard.Silence>(Voices(settings, http).hear())
        assertEquals(0, posts)
        DeviceVoice.record = null
        DeviceVoice.hearingState = { "unsupported" }
    }
}
