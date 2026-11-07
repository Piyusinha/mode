package com.piyush.mode.app.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinkTest {
    @Test
    fun readsEveryLinkModeMakes() {
        assertEquals(DeepLink.Conversation("c1"), DeepLink.parse("mode://conversation/c1"))
        assertEquals(DeepLink.Open, DeepLink.parse("mode://conversation"))
        assertEquals(DeepLink.Ask, DeepLink.parse("mode://ask"))
        assertEquals(DeepLink.Open, DeepLink.parse("mode://open"))
        assertEquals(DeepLink.Listen, DeepLink.parse("mode://listen"))
        assertEquals(DeepLink.Talk(null), DeepLink.parse("mode://talk"))
        assertEquals(DeepLink.Talk("c2"), DeepLink.parse("mode://talk/c2"))
        assertEquals(DeepLink.Retry("t1", "c3"), DeepLink.parse("mode://retry/t1/c3"))
        assertEquals(DeepLink.Retry("t1", null), DeepLink.parse("mode://retry/t1?from=notification"))
    }

    @Test
    fun ignoresOtherLinks() {
        assertNull(DeepLink.parse("https://heymode.app"))
        assertNull(DeepLink.parse("mode://retry"))
        assertNull(DeepLink.parse("mode://somewhere"))
    }

    @Test
    fun theTraceNeverCarriesAnId() {
        assertEquals("conversation", DeepLink.Conversation("secret").traceName)
        assertEquals("chat_mic", DeepLink.Talk("secret").traceName)
    }
}
