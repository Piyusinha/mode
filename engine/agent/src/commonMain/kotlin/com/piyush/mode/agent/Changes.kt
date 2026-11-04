package com.piyush.mode.agent

/**
 * Taps that change something for the user: add, log, save, send, book, buy, delete.
 * Known from the agent's own words for the step ("Adding it to Dinner") or the
 * button's label (Add, Send, a plus sign); nothing about any particular app.
 * Doing one twice adds twice, so each is checked before another is made.
 */
internal object Changes {
    /**
     * What kind of change a tap makes, so proof is read for that kind: an order is not proven by "added", and
     * nothing is proven by history ("You ordered 5 months ago"). Seen on the phone (Zomato): both were taken as proof.
     */
    enum class Kind { Add, Commit, Send, Delete, Other }

    /** Buttons that only move on: tapping them changes nothing, whatever the reason says. */
    private val NAVIGATION = Regex(
        """^\W*(continue|next|proceed|view cart|go to cart|checkout|check out|review|see|show|open|back|skip|ok|okay|got it|done|close|cancel|dismiss|not now|later)\b[\W\w]{0,16}$""",
        RegexOption.IGNORE_CASE,
    )
    private val COMMIT = Regex("""\b(order|ordering|place|placing|pay|paying|book|booking|buy|buying|purchase|purchasing|confirm|confirming|subscribe|transfer)\b""", RegexOption.IGNORE_CASE)
    private val SEND = Regex("""\b(send|sending|post|posting|reply|replying|message|messaging|submit|submitting)\b""", RegexOption.IGNORE_CASE)
    private val ADD = Regex("""\b(add|adding|log|logging|save|saving|track|tracking|record|recording|create|creating|join|joining|accept|accepting)\b""", RegexOption.IGNORE_CASE)
    private val DELETE = Regex("""\b(delete|deleting|remove|removing)\b""", RegexOption.IGNORE_CASE)

    private val VERBS = Regex(
        """\b(add|adding|log|logging|save|saving|send|sending|post|posting|book|booking|buy|buying|""" +
            """order|ordering|pay|paying|submit|submitting|create|creating|delete|deleting|remove|removing|""" +
            """track|tracking|record|recording|join|joining|accept|accepting)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Words that mean the agent wants to look, not to change anything. */
    private val CHECKING = Regex("""\b(check|checking|verify|verifying|confirm whether|see if|see whether|look|looking)\b""", RegexOption.IGNORE_CASE)

    /** What apps say when a change went through, for a moment or for good. */
    private val CONFIRMED = Regex(
        """\b(logged|added|saved|sent|booked|ordered|posted|created|scheduled|confirmed|paid|deleted|removed|""" +
            """success|successful|successfully)\b|[✓✔]|\b\d+\s+items?\b|\bview cart\b""",
        RegexOption.IGNORE_CASE,
    )

    private val PROOF_ADD = Regex("""\b(logged|added|saved|tracked|recorded|created|joined|accepted|success|successful|successfully)\b|[✓✔]|\b\d+\s+items?\b|\bview cart\b""", RegexOption.IGNORE_CASE)
    private val PROOF_COMMIT = Regex(
        """\b(order (placed|confirmed|received|accepted|id|no\.?|number|#)|placed|confirmed|booked|booking (id|confirmed)|paid|""" +
            """payment (successful|done|received|complete)|on (its|the) way|preparing|being prepared|arriving|pnr|ticket|receipt|""" +
            """thank you for (your )?(order|booking|payment)|order #?\s?\d{3,}|successfully?)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val PROOF_SEND = Regex("""\b(sent|delivered|posted|submitted|replied|message sent)\b|✓✓""", RegexOption.IGNORE_CASE)
    private val PROOF_DELETE = Regex("""\b(deleted|removed|cleared)\b""", RegexOption.IGNORE_CASE)

    /** History and promotions read like proof but are not ("You ordered 5 months ago", "Highly reordered"). */
    private val HISTORY = Regex(
        """\b(\d+\s+(minutes?|hours?|days?|weeks?|months?|years?)\s+ago|ago|last (ordered|booked|time)|previously|you (ordered|booked|bought|sent)|""" +
            """reordered|reorder|past orders?|order history|your orders|again|claim|offer|coupon|promo|unlock|""" +
            """(after|once|when) (your|the) (order|booking|payment))\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Plus signs as screen text recognition reads them ("十" is how it read MyFitnessPal's +). */
    private val PLUS = setOf("+", "＋", "十", "⊕", "➕")

    /** A button that adds or sends: a plus sign, or a short label such as "Add" or "Send". */
    fun isChangeButton(label: String?): Boolean {
        val l = label?.trim().orEmpty()
        return l in PLUS || (l.length <= 12 && VERBS.containsMatchIn(l))
    }

    fun isChange(reason: String, label: String?): Boolean =
        !isNavigation(label) && (isChangeButton(label) || (VERBS.containsMatchIn(reason) && !isChecking(reason)))

    /** "Continue", "View cart", "Checkout": the tap only moves on (seen as a repeat order and refused). */
    fun isNavigation(label: String?): Boolean = label?.trim()?.let { it.length <= 24 && NAVIGATION.matches(it) } == true

    /** The kind of change: the button's label decides when it says; else the agent's words for the step. */
    fun kindOf(reason: String, label: String?): Kind {
        val l = label?.trim().orEmpty()
        if (l.length <= 24) {
            when {
                COMMIT.containsMatchIn(l) -> return Kind.Commit
                SEND.containsMatchIn(l) -> return Kind.Send
                DELETE.containsMatchIn(l) -> return Kind.Delete
                ADD.containsMatchIn(l) || l in PLUS -> return Kind.Add
            }
        }
        return when {
            COMMIT.containsMatchIn(reason) -> Kind.Commit
            SEND.containsMatchIn(reason) -> Kind.Send
            DELETE.containsMatchIn(reason) -> Kind.Delete
            ADD.containsMatchIn(reason) -> Kind.Add
            else -> Kind.Other
        }
    }

    /** The agent says it is checking: a tap on an add button is then a mistake, not a check. */
    fun isChecking(reason: String): Boolean = CHECKING.containsMatchIn(reason)

    /** The first text that says a change went through ("Food logged!"), if any. */
    fun proof(texts: List<String>): String? = texts.firstOrNull { it.length <= 60 && CONFIRMED.containsMatchIn(it) }

    /** Proof of a change of this [kind]: an order needs order words, never history or a promo badge. */
    fun proof(texts: List<String>, kind: Kind): String? {
        val pattern = when (kind) {
            Kind.Add -> PROOF_ADD
            Kind.Commit -> PROOF_COMMIT
            Kind.Send -> PROOF_SEND
            Kind.Delete -> PROOF_DELETE
            Kind.Other -> CONFIRMED
        }
        return texts.firstOrNull { it.length <= 60 && !HISTORY.containsMatchIn(it) && pattern.containsMatchIn(it) }
    }
}
