package com.piyush.mode.agent

import com.piyush.mode.ops.Op
import com.piyush.mode.testing.FakePhone
import com.piyush.mode.testing.ScriptedModel
import com.piyush.mode.testing.call
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExpectCheckTest {
    @Test
    fun whatWasExpectedShowingIsFine() {
        val r = ExpectCheck.check("One Classic Tres Leches is added to the cart", listOf("Classic Tres Leches", "1 item added", "Continue >"), true, false, "tap \"Add item\"")
        assertFalse(assertNotNull(r).missed)
        assertNull(ExpectCheck.check(null, listOf("x"), true, false, null), "nothing expected, nothing checked")
        assertNull(ExpectCheck.check("it opens", listOf("x"), true, false, null), "no content words, no check")
    }

    @Test
    fun anUnchangedScreenNamesTheUsualCauses() {
        val r = assertNotNull(ExpectCheck.check("Paid order confirmation appears", listOf("Place Order •", "₹440"), false, false, "tap \"Place Order •\""))
        assertTrue(r.missed)
        assertTrue("NOT what you expected (tap \"Place Order •\" should have: Paid order confirmation appears)" in r.hint, r.hint)
        assertTrue("the screen did not change" in r.hint && "do not repeat the same coordinates" in r.hint, r.hint)
    }

    @Test
    fun aDifferentScreenIsReadBack() {
        val r = assertNotNull(ExpectCheck.check("Mail inbox with the search field", listOf("Hey, can you do one thing for", "Q Spoken", "Today 01:18"), true, false, "open Mail"))
        assertTrue(r.missed)
        assertTrue("instead the screen shows \"Hey, can you do one thing for\", \"Q Spoken\"" in r.hint, r.hint)
    }

    @Test
    fun aFieldThatShouldBeFocusedNeedsTheKeyboard() {
        val r = assertNotNull(ExpectCheck.check("The search field is focused with the keyboard up", listOf("Search", "Cancel"), true, false, "tap \"Search\""))
        assertTrue(r.missed && "the keyboard did not come up" in r.hint, r.hint)
        assertFalse(assertNotNull(ExpectCheck.check("The search field is focused", listOf("Search"), true, true, null)).missed)
    }

    private class Gate : UserGate {
        override suspend fun confirm(question: String) = true
        override suspend fun ask(question: String): String? = "yes"
    }

    @Test
    fun theAgentSeesTheMissInItsHistory() = runTest {
        val page = listOf("Settings" to (100.0 to 300.0), "Display & Brightness" to (120.0 to 400.0))
        val phone = FakePhone(listOf(page, page, page))
        val model = ScriptedModel(
            call("tap", "x" to 150, "y" to 460, "reason" to "Opening Display", "expect" to "Appearance options Light and Dark"),
            call("done", "success" to false, "summary" to "stop", "evidence" to "x"),
        )
        Agent(phone, model, Gate(), AgentConfig(settleMs = 0, maxSteps = 6)).run("turn off dark mode")
        assertEquals(1, phone.actions.count { it is Op.Tap })
        assertTrue("NOT what you expected (tap \"Display & Brightness\" should have: Appearance options Light and Dark)" in model.lastUserText, model.lastUserText)
    }
}
