package com.piyush.mode.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChangesTest {
    @Test
    fun anOrderIsNotProvenByHistoryOrAPromoBadge() {
        // Zomato, 3 Nov: both of these were taken as proof that Place Order went through.
        assertNull(Changes.proof(listOf("You ordered 5 months ago", "German Chocolate Cake Slice"), Changes.Kind.Commit))
        assertNull(Changes.proof(listOf("be10x", "Claim this after your order is placed", "ADDED x", "₹440"), Changes.Kind.Commit))
        assertNull(Changes.proof(listOf("Scheduling your order", "Paying ₹521.69", "CANCEL"), Changes.Kind.Commit), "a countdown sheet is not confirmation")
        assertEquals("Order placed · #18823", Changes.proof(listOf("Magnolia Bakery", "Order placed · #18823"), Changes.Kind.Commit))
        assertEquals("Your order is on its way", Changes.proof(listOf("Your order is on its way"), Changes.Kind.Commit))
    }

    @Test
    fun anAddIsProvenByACartCountNotByHistory() {
        assertEquals("1 item added", Changes.proof(listOf("You ordered 5 months ago", "1 item added", "Continue >"), Changes.Kind.Add))
        assertNull(Changes.proof(listOf("Highly reordered", "You ordered 9 months ago"), Changes.Kind.Add))
        assertNull(Changes.proof(listOf("Order placed"), Changes.Kind.Send), "an order is not a sent message")
    }

    @Test
    fun continueAndViewCartOnlyMoveOn() {
        assertTrue(Changes.isNavigation("Continue >"))
        assertTrue(Changes.isNavigation("View cart"))
        assertTrue(Changes.isNavigation("Checkout"))
        assertFalse(Changes.isNavigation("Place Order •"))
        assertFalse(Changes.isNavigation("Add item - ₹440"))
        assertFalse(Changes.isChange("Reviewing your order and delivery details", "Continue >"))
        assertTrue(Changes.isChange("Placing the scheduled order", "Place Order •"))
    }

    @Test
    fun theKindComesFromTheButtonThenFromTheReason() {
        assertEquals(Changes.Kind.Commit, Changes.kindOf("Placing the scheduled order", "Place Order •"))
        assertEquals(Changes.Kind.Commit, Changes.kindOf("Adding to cart first", "Pay ₹521"))
        assertEquals(Changes.Kind.Add, Changes.kindOf("Adding Classic Tres Leches", "Add item - ₹440"))
        assertEquals(Changes.Kind.Add, Changes.kindOf("Adding the oats", "+"))
        assertEquals(Changes.Kind.Send, Changes.kindOf("Sending the message", null))
        assertEquals(Changes.Kind.Other, Changes.kindOf("Opening the menu", "Menu"))
    }
}
