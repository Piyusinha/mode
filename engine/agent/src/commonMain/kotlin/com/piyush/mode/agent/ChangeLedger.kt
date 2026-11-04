package com.piyush.mode.agent

/**
 * Mode's own record of what the agent changed for the user in this task (added, logged,
 * sent, saved…), with the proof for each. The model sees it every step, never summarised
 * away; Mode itself refuses to repeat a confirmed change and holds the next change until
 * an unconfirmed one was checked. Nothing in it is about a particular app.
 *
 * Seen on the phone: the agent added a food, saw "Food logged!", then after a question
 * the row showed + again; it trusted the screen over its own past and added it five times.
 */
internal class ChangeLedger {
    class Entry(
        val step: Int,
        /** What it changed: the row or button it tapped ("Dark chocolate oats · 392 cal, 100 gram"). */
        val target: String,
        /** The agent's words for it ("Adding the 100 g oats to Dinner"). */
        val what: String,
        /** The page it was made on, to know when the agent has left it to check. */
        val page: Set<String>,
        var proof: String? = null,
        /** The agent left that page (to the list it adds to), so it has had the chance to check. */
        var checked: Boolean = false,
        /** The strong model has been asked about it once. */
        var escalated: Boolean = false,
        /** The page right after it was the same page, nothing new on it: the tap most likely missed. */
        var noEffect: Boolean = false,
        val kind: Changes.Kind = Changes.Kind.Other,
        /** The button tapped ("Place Order •"): for an order, seeing it again on the same page means it did not go through. */
        val button: String? = null,
        /** It had no effect and the same change was tried again: a later entry stands for it now. */
        var retried: Boolean = false,
    ) {
        val confirmed: Boolean get() = proof != null
    }

    val entries = mutableListOf<Entry>()

    fun add(
        step: Int, target: String, what: String, page: Set<String>, proof: String?,
        kind: Changes.Kind = Changes.Kind.Other, button: String? = null,
    ): Entry {
        // A retry of a change that had no effect supersedes it: only the retry is checked from here on.
        entries.lastOrNull { it.noEffect && !it.confirmed && !it.retried && same(it.target, target) }?.retried = true
        return Entry(step, target, what, page, proof, kind = kind, button = button).also { entries += it }
    }

    /**
     * The latest change still waiting for a check, if any. One that had no effect still waits, as far as
     * other changes go: a silent add looks the same as a missed tap, so only the same item may be retried.
     */
    fun pending(): Entry? = entries.lastOrNull { !it.confirmed && !it.retried && (!it.checked || it.noEffect) }

    /** A confirmed change of this same target. */
    fun confirmedFor(target: String): Entry? = entries.lastOrNull { it.confirmed && same(it.target, target) }

    /**
     * Each look: proof on screen confirms the pending change; going to a different page counts as
     * having checked. [navigated]: the last action moved between pages (a tap, back, opening an app),
     * not a scroll: seen on the phone, a scroll down a long menu "left the page" and a second,
     * different item was added before the first was checked.
     */
    fun looked(page: Set<String>, texts: List<String>, navigated: Boolean, rightAfter: Boolean = false) {
        val entry = pending() ?: return
        // Only what was not already on the page it was made on ("Recently added" headers don't count).
        Changes.proof(texts.filter { norm(it) !in entry.page }, entry.kind)?.let { entry.proof = it; return }
        // Seen on the phone (Zomato): "Place Order" tapped, a "Scheduling your order" sheet, then the same checkout
        // with Place Order back. An order, payment or booking whose button is on the same page again did not go through.
        if (entry.kind == Changes.Kind.Commit && entry.button != null && !entry.noEffect &&
            similarity(entry.page, page) >= SAME_PAGE && texts.any { same(it, entry.button) }
        ) {
            entry.noEffect = true
            entry.checked = true
            return
        }
        // Seen on the phone: a tap that missed (off by a coordinate convention) held every retry back, nine
        // times. Right after the change, the very same page with nothing new means it most likely did nothing.
        if (rightAfter && !entry.noEffect && similarity(entry.page, page) >= NO_EFFECT) {
            entry.noEffect = true
            entry.checked = true
            return
        }
        if (navigated && similarity(entry.page, page) < SAME_PAGE) {
            entry.checked = true
            // An order that visibly did not go through stays that way; a silent add may still show up in the list.
            if (entry.kind != Changes.Kind.Commit) entry.noEffect = false
        }
    }

    /** Tries at this target that showed no proof; a third is held, whatever the page shows. */
    fun unprovenTries(target: String): Int = entries.count { !it.confirmed && same(it.target, target) }

    /** One line each, for the model. */
    fun lines(): List<String> = entries.map { e ->
        val status = when {
            e.confirmed -> "confirmed: \"${e.proof}\""
            e.retried -> "it did NOT go through; you tried it again at a later step (see that one)"
            e.noEffect && e.kind == Changes.Kind.Commit ->
                "it did NOT go through (the same page came back with \"${e.button}\" still there): try it once more, or check the app's orders"
            e.noEffect -> "it had no effect (the page stayed the same): you may try it once more"
            e.checked -> "not confirmed; you left that page, so check the list you are on"
            else -> "NOT confirmed yet: check it before any other change"
        }
        "Step ${e.step}: ${e.what.ifBlank { "changed" }} → on \"${e.target}\" ($status)"
    }

    companion object {
        /** Pages sharing at least this much of their text count as the same page. */
        const val SAME_PAGE = 0.5
        /** Right after a change, a page at least this similar (and nothing new on it) means the tap did nothing. */
        const val NO_EFFECT = 0.95

        fun page(texts: List<String>): Set<String> = texts.map(::norm).filter { it.length > 1 }.toSet()

        fun similarity(a: Set<String>, b: Set<String>): Double =
            if (a.isEmpty() && b.isEmpty()) 1.0 else a.intersect(b).size.toDouble() / a.union(b).size

        fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() || it == ' ' }.trim()

        /** Same thing tapped: equal once case and punctuation are set aside, or one names the other. */
        fun same(a: String, b: String): Boolean {
            val x = norm(a)
            val y = norm(b)
            return x.isNotEmpty() && y.isNotEmpty() && (x == y || x.startsWith(y) || y.startsWith(x))
        }
    }
}
