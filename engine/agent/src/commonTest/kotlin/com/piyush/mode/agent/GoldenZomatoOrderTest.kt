package com.piyush.mode.agent

import com.piyush.mode.ops.Op
import com.piyush.mode.testing.FakePhone
import com.piyush.mode.testing.ScriptedModel
import com.piyush.mode.testing.call
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Golden trace from the phone, 3 Nov 2026 20:06 (Zomato, "order Tres Leches and pay"): the screen text as OCR
 * read it, step by step. The run failed because the harness took "You ordered 5 months ago" and a promo
 * badge "ADDED ×" as proof, refused "Continue >" as a repeat order, and then forbade a retry of Place Order.
 */
class GoldenZomatoOrderTest {
    private class Gate : UserGate {
        override suspend fun confirm(question: String) = true
        override suspend fun ask(question: String): String? = "yes"
    }

    // The run's OCR positions are image pixels (460×1000); the phone reports points (402×874), so both the
    // texts and the taps go through the same conversion.
    private fun pt(px: Double, py: Double) = (px * 402 / 460) to (py * 874 / 1000)
    private fun List<Pair<String, Pair<Double, Double>>>.points() = map { (t, at) -> t to pt(at.first, at.second) }
    // The model taps in the fake's screenshot pixels (500×1000): the run's x scaled, its y as is.
    private fun tap(px: Double, py: Double) = (px * 500 / 460) to py

    private val menu = listOf(
        "Magnolia Baker" to (119.0 to 320.0), "4.5" to (413.0 to 326.0), "Classic Tres Leches" to (114.0 to 747.0),
        "Highly reordered" to (124.0 to 776.0), "1" to (72.0 to 931.0), "Add item - ·440" to (296.0 to 932.0),
    ).points()
    private val afterAdd = listOf(
        "Magnolia Bakery" to (118.0 to 264.0), "Your Orders and Collections" to (137.0 to 564.0),
        "Classic Tres Leches" to (157.0 to 646.0), "- 1 +" to (404.0 to 651.0), "·440" to (101.0 to 669.0),
        "You ordered 5 months ago" to (164.0 to 691.0), "German Chocolate Cake Slice" to (197.0 to 737.0), "ADD" to (403.0 to 743.0),
        "You ordered 9 months ago" to (164.0 to 781.0), "1 item added" to (120.0 to 929.0), "Continue >" to (380.0 to 928.0),
    ).points()
    private val checkout = listOf(
        "Magnolia Bakery" to (116.0 to 85.0), "Delivery scheduled for today, 11:00-11:30 AM" to (159.0 to 157.0),
        "Special offer for you" to (96.0 to 265.0), "ADDED ×" to (389.0 to 311.0), "belox" to (56.0 to 324.0),
        "Cloim this ofter your order is placed" to (189.0 to 327.0), "Classic Tres Leches" to (121.0 to 410.0),
        "·440" to (413.0 to 444.0), "+ Add more items" to (98.0 to 495.0), "Use ·521.69 from Zomato Money" to (153.0 to 842.0),
        "Place Order •" to (227.0 to 929.0),
    ).points()
    // The same checkout as OCR read it a moment later: three lines differ by a glyph.
    private val checkoutAgain = checkout.map { (t, at) ->
        when (t) { "ADDED ×" -> "ADDED x"; "belox" -> "be10x"; "·440" -> "₹440"; else -> t } to at
    }
    private val scheduling = listOf(
        "Magnolia Bakery" to (121.0 to 151.0), "Classic Tres Leches" to (126.0 to 460.0), "Scheduling your order" to (124.0 to 709.0),
        "Delivering today" to (132.0 to 757.0), "Paying ·521.69" to (126.0 to 823.0), "CANCEL" to (404.0 to 944.0),
    ).points()
    private val placed = listOf("Magnolia Bakery" to (121.0 to 151.0), "Order placed · #18823" to (200.0 to 400.0), "Arriving 11:00-11:30 AM" to (200.0 to 440.0)).points()

    private val add = tap(296.0, 932.0).let { (x, y) -> call("tap", "x" to x, "y" to y, "reason" to "Adding Classic Tres Leches", "expect" to "One Classic Tres Leches is added to the cart") }
    private val cont = tap(380.0, 928.0).let { (x, y) -> call("tap", "x" to x, "y" to y, "reason" to "Reviewing your order and delivery details") }
    private val place = tap(227.0, 929.0).let { (x, y) -> call("tap", "x" to x, "y" to y, "reason" to "Placing the scheduled order", "expect" to "Paid order confirmation appears") }
    private val config = AgentConfig(settleMs = 0, maxSteps = 12, fullAccess = true)

    @Test
    fun theAddIsProvenByTheCartCountAndContinueGoesThrough() = runTest {
        val phone = FakePhone(listOf(menu, afterAdd, checkout, checkout))
        val steps = mutableListOf<StepRecord>()
        val model = ScriptedModel(add, cont, call("done", "success" to false, "summary" to "stop here", "evidence" to "x"))
        Agent(phone, model, Gate(), config, onStep = { steps += it }).run("order Tres Leches from Magnolia Bakery on Zomato and pay")
        assertEquals(2, phone.actions.count { it is Op.Tap }, "add, then Continue: ${steps.map { it.action + " — " + it.outcome }}")
        assertTrue("confirms it" in steps[0].outcome && steps[0].outcome.startsWith("ok; right after it the screen showed: \"1 item added\""), steps[0].outcome)
        assertTrue("confirmed: \"1 item added\"" in model.lastUserText && "5 months ago\"" !in model.lastUserText.substringAfter("DONE SO FAR").substringBefore("\n\n"), model.lastUserText)
        assertFalse("already done" in steps[1].outcome, steps[1].outcome)
    }

    @Test
    fun placeOrderWithTheSameCheckoutBackIsNoEffectAndMayBeRetriedOnce() = runTest {
        // Place Order → the checkout re-read with OCR jitter → the model retries → the "Scheduling your order" sheet.
        val phone = FakePhone(listOf(checkout, checkoutAgain, scheduling, scheduling))
        val steps = mutableListOf<StepRecord>()
        val model = ScriptedModel(place, place, call("wait", "seconds" to 2, "reason" to "Waiting for the confirmation"), call("done", "success" to false, "summary" to "stop", "evidence" to "x"))
        Agent(phone, model, Gate(), config, onStep = { steps += it }).run("order Tres Leches on Zomato and pay")
        assertTrue("confirms it" !in steps[0].outcome && "check it worked" in steps[0].outcome, steps[0].outcome)
        assertTrue("did NOT go through" in model.lastUserText || "did NOT go through" in steps[1].outcome, "the second look names the no-effect; ledger: " + model.lastUserText.substringAfter("DONE SO FAR", "(none)").substringBefore("\n\n") + "; steps: ${steps.map { it.action + " — " + it.outcome.take(80) }}")
        assertEquals(2, phone.actions.count { it is Op.Tap }, "the retry of Place Order is allowed once")
        assertTrue("confirms it" !in steps[1].outcome, "a countdown sheet is not confirmation: ${steps[1].outcome}")
    }

    @Test
    fun aRealConfirmationIsProof() = runTest {
        val phone = FakePhone(listOf(checkout, placed, placed))
        val steps = mutableListOf<StepRecord>()
        val model = ScriptedModel(place, call("done", "success" to true, "summary" to "Ordered", "evidence" to "Order placed · #18823"))
        Agent(phone, model, Gate(), config, onStep = { steps += it }).run("order Tres Leches on Zomato and pay")
        assertTrue("confirms it" in steps[0].outcome && "Order placed" in steps[0].outcome, steps[0].outcome)
    }
}
