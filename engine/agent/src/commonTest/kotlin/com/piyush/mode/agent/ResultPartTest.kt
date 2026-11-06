package com.piyush.mode.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResultPartTest {
    @Test
    fun aTickedPartIsDoneWithItsDetail() {
        assertEquals(ResultPart("Replied to Sonali", "WhatsApp · testing my agent", true), ResultPart.parse("✓ Replied to Sonali — WhatsApp · testing my agent"))
    }

    @Test
    fun aCrossedPartIsNotDone() {
        assertEquals(ResultPart("Coffee not ordered", "the café is closed until 8", false), ResultPart.parse("✗ Coffee not ordered — the café is closed until 8"))
    }

    @Test
    fun noMarkCountsAsDoneAndNoDetailIsEmpty() {
        assertEquals(ResultPart("Dark Mode is off", "", true), ResultPart.parse("Dark Mode is off"))
        assertNull(ResultPart.parse("  ✓  "))
    }
}
