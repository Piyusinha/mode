package com.piyush.mode.app.screens

import com.piyush.mode.app.screens.settings.ListenPresenter
import com.piyush.mode.app.screens.settings.VoicePickerPresenter
import com.piyush.mode.feature.settings.domain.VoiceKind
import com.piyush.mode.feature.settings.voice.DeviceVoice
import com.piyush.mode.feature.settings.voice.VoiceOption
import com.piyush.mode.feature.settings.voice.Voices
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VoiceScreensTest {
    @Test
    fun choosingAVoiceKeepsItAndPutsItsProviderFirst() = runTest {
        val f = Fixture(this)
        DeviceVoice.voices = {
            listOf(VoiceOption("ava", "Ava", "Female · English (US) · Premium", "English (US)"), VoiceOption("rishi", "Rishi", "Male · English (India)", "English (India)"))
        }
        val p = VoicePickerPresenter(backgroundScope, f.settings, Voices(f.settings, HttpClient(MockEngine { error("offline") })), VoiceKind.Apple)
        val listed = p.state.awaitState { !it.loading }
        assertEquals("ava", listed.selected, "the first voice until one is picked")
        assertEquals(listOf("English (US)", "English (India)"), listed.groups)
        p.filter("English (India)")
        assertEquals(listOf("rishi"), p.state.awaitState { it.group != null }.voices.map { it.id })
        p.select("rishi")
        p.state.awaitState { it.selected == "rishi" }
        p.use()
        assertEquals("Rishi", f.settings.settings.value.voices["apple"]?.name)
        assertEquals("apple", f.settings.settings.value.speechOrder.first())
        DeviceVoice.voices = { emptyList() }
    }

    @Test
    fun modeAnswersLikeAnAssistantWithoutReadingTheTaskBack() {
        val line = ListenPresenter.replyLine("can you turn on dark mode please")
        assertTrue(line in listOf("Sure, let me do that for you.", "On it.", "Sure, I'll take care of it.", "Got it, doing it now."), line)
        assertTrue("dark mode" !in line.lowercase(), "never the task read back")
        assertEquals(line, ListenPresenter.replyLine("can you turn on dark mode please"), "the same task, the same line")
        assertEquals("Let me check.", ListenPresenter.replyLine("what's on my calendar today?"))
    }

    @Test
    fun modeGreetsByNameAndTimeOfDay() {
        assertEquals("Hey Piyush, good evening. What do you want me to do?", ListenPresenter.greetingLine("Piyush Sinha", 19))
        assertEquals("Hey Piyush, good morning. What do you want me to do?", ListenPresenter.greetingLine("Piyush", 8))
        assertEquals("Hey, good afternoon. What do you want me to do?", ListenPresenter.greetingLine(null, 14))
        assertEquals("Hey Piyush. What do you want me to do?", ListenPresenter.greetingLine("Piyush", 1), "late at night: no greeting for the hour")
    }
}
