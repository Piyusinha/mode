package com.piyush.mode.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecretFilterTest {
    private val secrets = mapOf(
        "Card 4111 1111 1111 1111 expires 12/29" to "card number",
        "4111-1111-1111-1111" to "card number",
        "Your OTP is 482913" to "one-time code, PIN or passcode",
        "482913 is your Swiggy OTP" to "one-time code",
        "verification code: 7731" to "one-time code, PIN or passcode",
        "the code is 4821" to "one-time code, PIN or passcode",
        "ATM PIN 4402" to "one-time code, PIN or passcode",
        "phone passcode 120498" to "one-time code, PIN or passcode",
        "wifi password: tiger@123" to "password",
        "CVV 123" to "CVV",
        "Aadhaar 2345 6789 0123" to "Aadhaar number",
        "PAN ABCDE1234F" to "PAN",
        "SSN 123-45-6789" to "social security number",
        "IBAN GB82 WEST 1234 5698 7654 32" to "bank account (IBAN)",
        "key sk-or-v1-abcdefghijklmnopqrstuvwxyz012345" to "API key",
        "AKIAIOSFODNN7EXAMPLE" to "API key",
        "token eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.sig" to "access token",
        "session 9f8e7d6c5b4a39281706f5e4d3c2b1a0ffeeddcc" to "access token",
    )

    private val fine = listOf(
        "Sonali: message on WhatsApp",
        "Sonali's number is +91 98765 43210",
        "Home: 12 MG Road, Bengaluru, PIN code 560034",
        "pincode 560034",
        "zip code 94107",
        "area code 415",
        "14:00 Biozyme with lunch",
        "Settings: search 'Dark Mode', then Appearance → Light",
        "Prefers short, casual messages",
        "Code review at 3pm",
        "Use promo code SAVE20",
        "Order total ₹1,299 on 1 Nov 2026",
        "Flight AI 504 departs 06:45",
        "Tap the PIN icon to pin the chat",
    )

    @Test
    fun secretsAreCaughtWithAReason() {
        for ((text, why) in secrets) assertEquals(why, SecretFilter.reasonOf(text), text)
    }

    @Test
    fun everydayFactsAreKept() {
        for (text in fine) assertNull(SecretFilter.reasonOf(text), text)
    }

    @Test
    fun aCardNumberMustPassTheLuhnCheck() {
        assertNull(SecretFilter.reasonOf("Ticket 1234 5678 9012 3456"))
    }

    @Test
    fun hideReplacesOnlySecrets() {
        assertEquals(SecretFilter.HIDDEN, SecretFilter.hide("OTP 482913"))
        assertEquals("14:00 Biozyme", SecretFilter.hide("14:00 Biozyme"))
        assertTrue(SecretFilter.isSecret("password = hunter2"))
    }
}
