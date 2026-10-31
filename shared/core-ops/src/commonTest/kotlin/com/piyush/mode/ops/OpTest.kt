package com.piyush.mode.ops

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpTest {

    @Test
    fun callCarriesWireNameAndKeywords() {
        val (op, kw) = Op.Tap(x = 190.0, y = 310.5).toCall()
        assertEquals("input.tap", op)
        assertEquals(190.0, kw.getValue("x").jsonPrimitive.double)
        assertEquals(310.5, kw.getValue("y").jsonPrimitive.double)
        assertFalse("op" in kw, "the discriminator is the op name, not a keyword")
    }

    @Test
    fun objectOpsHaveNoKeywords() {
        assertEquals("nav.home" to JsonObject(emptyMap()), Op.Home.toCall())
    }

    @Test
    fun keywordsUsePhoneHarnessNames() {
        assertEquals(JsonPrimitive("India Gate"), Op.TypeText("India Gate").toCall().second["s"])
        assertEquals(JsonPrimitive(0.3), Op.ScreenText().toCall().second["min_confidence"])
    }

    @Test
    fun defaultsAreSent() {
        val kw = Op.Scroll(x = 200.0, y = 400.0, dy = -300.0).toCall().second
        assertEquals(0.0, kw.getValue("dx").jsonPrimitive.double)
        assertEquals(JsonPrimitive(6), kw["steps"])
    }

    @Test
    fun everyOpHasADistinctWireName() {
        val all = listOf(
            Op.Tree, Op.ScreenText(), Op.ScreenCapture, Op.ScreenBounds,
            Op.Tap(0.0, 0.0), Op.LongPress(0.0, 0.0), Op.Drag(0.0, 0.0, 1.0, 1.0),
            Op.Scroll(0.0, 0.0, dy = -1.0), Op.TypeText("hi"), Op.PressKey("return"),
            Op.Home, Op.LaunchApp("Uber"), Op.CurrentApp, Op.OpenUrl("https://example.com"),
            Op.SessionState,
        )
        val names = all.map { it.wireName }
        assertEquals(names.size, names.toSet().size, "duplicate wire name in $names")
    }

    @Test
    fun fakeRefusesWhatItDoesNotSupport() = runTest {
        val phone = FakeBackend(supported = setOf("input.tap"))
        assertTrue(phone.supports(Op.Tap(1.0, 2.0)))
        assertFalse(phone.supports(Op.Home))
        assertFailsWith<Unsupported> { phone.send(Op.Home) }
        phone.send(Op.Tap(1.0, 2.0))
        assertEquals(listOf<Op>(Op.Tap(1.0, 2.0)), phone.sent)
    }
}
