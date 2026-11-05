package com.piyush.mode.feature.settings

import com.piyush.mode.feature.settings.voice.BargeIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BargeInTest {
    private val line = "Okay, I'll check your IndiGo flight on Flightradar and tell you the gate."

    @Test
    fun theUsersOwnWordsInterruptAfterAMomentOfSpeech() {
        assertTrue(BargeIn.shouldInterrupt("actually use the IndiGo app instead", line, 600))
        assertFalse(BargeIn.shouldInterrupt("actually use the IndiGo app instead", line, 100), "not before 250 ms of speech")
    }

    @Test
    fun modesOwnVoiceHeardBackDoesNotInterrupt() {
        // The microphone hears Mode: every word is in the line being said.
        assertFalse(BargeIn.shouldInterrupt("I'll check your IndiGo flight on Flightradar", line, 900))
        assertEquals(emptyList(), BargeIn.newWords("check your flight", line))
        assertEquals(listOf("rahul", "whatsapp"), BargeIn.newWords("and tell Rahul on WhatsApp", line))
        assertFalse(BargeIn.shouldInterrupt("tell Rahul", line, 900), "one new word is not enough")
    }

    @Test
    fun aNodIsNotAnInterruption() {
        for (nod in listOf("ok", "okay", "yes", "hmm", "mm-hmm", "haan", "haan haan", "theek hai", "ok ok", "")) {
            assertTrue(BargeIn.isBackchannel(nod), nod)
            assertFalse(BargeIn.shouldInterrupt(nod, line, 2_000), nod)
        }
        assertFalse(BargeIn.isBackchannel("ok but use Flightradar"))
        assertTrue(BargeIn.shouldInterrupt("ok but use the Swiggy app", line, 500))
    }
}
