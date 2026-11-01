package com.piyush.mode.core.common

/**
 * Spots text that must never be kept: card numbers, one-time codes, PINs and
 * passcodes, passwords, government IDs, bank numbers and API keys. Memory
 * drops anything it flags, and the agent's notes hide it, whatever a model
 * says. Phone numbers, addresses, postal codes and dates are fine.
 */
public object SecretFilter {
    public const val HIDDEN: String = "[hidden: looked like a secret]"

    /** Why [text] looks like a secret, or null when it doesn't. */
    public fun reasonOf(text: String): String? {
        if (cardNumber(text)) return "card number"
        for ((why, pattern) in PATTERNS) if (pattern.containsMatchIn(text)) return why
        if (codeNearKeyword(text)) return "one-time code, PIN or passcode"
        return null
    }

    public fun isSecret(text: String): Boolean = reasonOf(text) != null

    /** [text] itself, or [HIDDEN] when it looks like a secret. */
    public fun hide(text: String): String = if (isSecret(text)) HIDDEN else text

    private val PATTERNS: List<Pair<String, Regex>> = listOf(
        "password" to Regex("""\b(password|passcode|passwd|pwd|pass ?phrase)\s*(is|:|=|-)\s*\S+""", RegexOption.IGNORE_CASE),
        "CVV" to Regex("""\b(cvv2?|cvc2?|csc)\b\W{0,6}\d{3,4}\b""", RegexOption.IGNORE_CASE),
        "PAN" to Regex("""\b[A-Z]{5}\d{4}[A-Z]\b"""),
        "social security number" to Regex("""\b\d{3}-\d{2}-\d{4}\b"""),
        "bank account (IBAN)" to Regex("""\b[A-Z]{2}\d{2}(?: ?[A-Z0-9]{4}){3,7}(?: ?[A-Z0-9]{1,3})?\b"""),
        // Exactly 12 digits in groups of four, not part of a longer number.
        "Aadhaar number" to Regex("""(?<!\d[ -]?)(?<!\d)\d{4}[ -]\d{4}[ -]\d{4}(?![ -]?\d)"""),
        "API key" to Regex("""\b(sk|pk|rk)[-_](live_|test_|or-v1-|ant-)?[A-Za-z0-9_-]{16,}"""),
        "API key" to Regex("""\b(AKIA[0-9A-Z]{16}|ghp_[A-Za-z0-9]{30,}|xox[abprs]-[A-Za-z0-9-]{10,}|AIza[0-9A-Za-z_-]{30,})"""),
        "one-time code" to Regex(
            """(?<!\d)\d{4,8}\W{0,3}(is|as)\s+(your|the)\s+(\w+\s+)?(otp|code|pin|passcode|password)\b""",
            RegexOption.IGNORE_CASE,
        ),
        "access token" to Regex("""\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}"""),
        "access token" to Regex("""\b(?=[A-Za-z0-9_-]*\d)(?=[A-Za-z0-9_-]*[A-Za-z])[A-Za-z0-9_-]{32,}\b"""),
    )

    /** 13 to 19 digits, spaces or dashes allowed between them, passing the Luhn check. */
    private fun cardNumber(text: String): Boolean =
        Regex("""(?<!\d)(?:\d[ -]?){12,18}\d(?!\d)""").findAll(text).any { match ->
            val digits = match.value.filter(Char::isDigit)
            digits.length in 13..19 && luhn(digits)
        }

    private fun luhn(digits: String): Boolean {
        var sum = 0
        digits.reversed().forEachIndexed { i, c ->
            var d = c - '0'
            if (i % 2 == 1) { d *= 2; if (d > 9) d -= 9 }
            sum += d
        }
        return sum % 10 == 0
    }

    private val CODE_WORDS = Regex(
        """\b(otp|one[- ]time (?:pass(?:word|code)?|code)|verification code|security code|auth(?:entication)? code|login code|code|pin|passcode|password)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Words before "code" or "pin" that make the number harmless: a postal, area or country code. */
    private val HARMLESS = Regex("""\b(zip|postal|post|area|country|dial|promo|coupon|discount|referral|pin)\s*$""", RegexOption.IGNORE_CASE)

    /** A 3–8 digit number within a few characters of a code word, either side. */
    private fun codeNearKeyword(text: String): Boolean {
        for (word in CODE_WORDS.findAll(text)) {
            val w = word.value.lowercase()
            val before = text.substring(0, word.range.first)
            if ((w == "code" || w == "pin") && HARMLESS.containsMatchIn(before)) continue
            // "PIN code 560034" and "pincode" are Indian postal codes.
            val after = text.substring(word.range.last + 1)
            if (w == "pin" && Regex("""^\s*code\b""", RegexOption.IGNORE_CASE).containsMatchIn(after)) continue
            if (Regex("""^\W{0,10}(?:is\W{1,5})?\d{3,8}(?!\d)""", RegexOption.IGNORE_CASE).containsMatchIn(after)) return true
        }
        return false
    }
}
