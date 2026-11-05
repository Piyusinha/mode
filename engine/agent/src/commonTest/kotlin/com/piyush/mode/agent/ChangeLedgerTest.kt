package com.piyush.mode.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChangeLedgerTest {
    private val checkout = listOf(
        "Magnolia Bakery", "Delivery scheduled for today, 11:00-11:30 AM", "Special offer for you", "ADDED ×", "belox",
        "Cloim this ofter your order is placed", "Classic Tres Leches", "·440", "+ Add more items", "Use ·521.69 from Zomato Money", "Place Order •",
    )
    private val checkoutAgain = checkout.map { when (it) { "ADDED ×" -> "ADDED x"; "belox" -> "be10x"; "·440" -> "₹440"; else -> it } }

    @Test
    fun anOrderWhoseButtonComesBackOnTheSamePageDidNotGoThrough() {
        val ledger = ChangeLedger()
        val entry = ledger.add(1, "Use ·521.69 from Zomato Money", "Placing the scheduled order", ChangeLedger.page(checkout), null, Changes.Kind.Commit, "Place Order •")
        val sim = ChangeLedger.similarity(ChangeLedger.page(checkout), ChangeLedger.page(checkoutAgain))
        assertTrue(sim >= ChangeLedger.SAME_PAGE, "same page: $sim")
        ledger.looked(ChangeLedger.page(checkoutAgain), checkoutAgain, navigated = true, rightAfter = true)
        assertTrue(entry.noEffect, "no effect: sim=$sim, checked=${entry.checked}, proof=${entry.proof}")
        assertTrue("did NOT go through" in ledger.lines().single(), ledger.lines().single())
        assertEquals(entry, ledger.pending(), "it may be retried once")
    }
}
