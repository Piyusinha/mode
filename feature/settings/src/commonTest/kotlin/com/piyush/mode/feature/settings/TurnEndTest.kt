package com.piyush.mode.feature.settings

import com.piyush.mode.feature.settings.voice.TurnEnd
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TurnEndTest {
    @Test
    fun aSentenceThatStopsMidWayGetsALongerPause() {
        for (said in listOf("Turn on the", "Message Asha and", "Open WhatsApp, then", "Order my usual from", "um", "Book a cab to", "Asha ko",
            // Apple adds a full stop at each pause; the last word still says more is coming.
            "Can you find all the flight status? Not all the like.", "Open WhatsApp and.", "Book a cab to…")) {
            assertTrue(TurnEnd.looksUnfinished(said), said)
            assertEquals(TurnEnd.UNFINISHED_PAUSE_MS, TurnEnd.pauseMs(said), said)
        }
    }

    @Test
    fun aFinishedSentenceEndsAfterTheShortPause() {
        for (said in listOf("Turn on dark mode", "Message Asha I'm on my way.", "What's on my calendar?", "Play some music")) {
            assertFalse(TurnEnd.looksUnfinished(said), said)
            assertEquals(TurnEnd.PAUSE_MS, TurnEnd.pauseMs(said), said)
        }
    }

    @Test
    fun beforeAnyWordsAreRecognisedModeWaitsLonger() {
        assertTrue(TurnEnd.looksUnfinished(""))
        assertEquals(TurnEnd.UNFINISHED_SETTLE_MS, TurnEnd.settleMs("  "))
    }

    @Test
    fun theWaitsAreLongerThanAThinkingPause() {
        assertTrue(TurnEnd.PAUSE_MS >= 1_200 && TurnEnd.SETTLE_MS > TurnEnd.PAUSE_MS)
        assertTrue(TurnEnd.UNFINISHED_PAUSE_MS > TurnEnd.PAUSE_MS && TurnEnd.UNFINISHED_SETTLE_MS > TurnEnd.SETTLE_MS)
    }
}
