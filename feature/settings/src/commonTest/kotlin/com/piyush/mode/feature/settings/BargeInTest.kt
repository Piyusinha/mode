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
        assertFalse(BargeIn.shouldInterrupt("actually use the IndiGo app instead", line, 300), "not before 500 ms of speech")
    }

    @Test
    fun modesOwnVoiceHeardBackDoesNotInterrupt() {
        // The microphone hears Mode: every word is in the line being said.
        assertFalse(BargeIn.shouldInterrupt("I'll check your IndiGo flight on Flightradar", line, 900))
        assertEquals(emptyList(), BargeIn.newWords("check your flight", line))
        assertEquals(listOf("rahul", "whatsapp"), BargeIn.newWords("and tell Rahul on WhatsApp", line))
        assertFalse(BargeIn.shouldInterrupt("tell Rahul", line, 900), "one new word is not enough")
        assertFalse(BargeIn.shouldInterrupt("and tell Rahul on WhatsApp", line, 900), "two new words are not enough")
    }

    @Test
    fun modesVoiceMisheardIsStillModes() {
        // Echo cancellation leaves a little of Mode; Apple mishears it: near-misses and pieces of Mode's words.
        assertFalse(BargeIn.shouldInterrupt("okay I'll checking you indigo flights on flight radar and tell you the gates", line, 3_000))
        assertEquals(emptyList(), BargeIn.newWords("flight radar indigo flights checking", line))
        val harper = "Hi, I'm Harper. Tell me what to do."
        assertFalse(BargeIn.shouldInterrupt("hi I'm harbor tell me what to do", harper, 2_000))
    }

    @Test
    fun aFewMisheardWordsScatteredInTheEchoDoNotInterrupt() {
        // Three odd words, but spread through Mode's own line: not someone talking over it.
        assertFalse(BargeIn.shouldInterrupt("okay banana I'll check your IndiGo pickle flight on Flightradar and tell zebra you the gate", line, 3_000))
    }

    @Test
    fun threeNewWordsInARowInterrupt() {
        assertTrue(BargeIn.shouldInterrupt("I'll check your IndiGo flight no wait book Uber instead", line, 1_500))
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
