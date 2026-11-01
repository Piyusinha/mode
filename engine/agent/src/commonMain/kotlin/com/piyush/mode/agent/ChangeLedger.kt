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
    ) {
        val confirmed: Boolean get() = proof != null
    }

    val entries = mutableListOf<Entry>()

    fun add(step: Int, target: String, what: String, page: Set<String>, proof: String?): Entry =
        Entry(step, target, what, page, proof).also { entries += it }

    /** The latest change still waiting for a check, if any. */
    fun pending(): Entry? = entries.lastOrNull { !it.confirmed && !it.checked }

    /** A confirmed change of this same target. */
    fun confirmedFor(target: String): Entry? = entries.lastOrNull { it.confirmed && same(it.target, target) }

    /** Each look: proof on screen confirms the pending change; a different page counts as having checked. */
    fun looked(page: Set<String>, texts: List<String>) {
        val entry = pending() ?: return
        // Only what was not already on the page it was made on ("Recently added" headers don't count).
        Changes.proof(texts.filter { norm(it) !in entry.page })?.let { entry.proof = it; return }
        if (similarity(entry.page, page) < SAME_PAGE) entry.checked = true
    }

    /** One line each, for the model. */
    fun lines(): List<String> = entries.map { e ->
        val status = when {
            e.confirmed -> "confirmed: \"${e.proof}\""
            e.checked -> "not confirmed; you left that page, so check the list you are on"
            else -> "NOT confirmed yet: check it before any other change"
        }
        "Step ${e.step}: ${e.what.ifBlank { "changed" }} → on \"${e.target}\" ($status)"
    }

    companion object {
        /** Pages sharing at least this much of their text count as the same page. */
        const val SAME_PAGE = 0.5

        fun page(texts: List<String>): Set<String> = texts.map(::norm).filter { it.length > 1 }.toSet()

        fun similarity(a: Set<String>, b: Set<String>): Double =
            if (a.isEmpty() && b.isEmpty()) 1.0 else a.intersect(b).size.toDouble() / a.union(b).size

        private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() || it == ' ' }.trim()

        /** Same thing tapped: equal once case and punctuation are set aside, or one names the other. */
        fun same(a: String, b: String): Boolean {
            val x = norm(a)
            val y = norm(b)
            return x.isNotEmpty() && y.isNotEmpty() && (x == y || x.startsWith(y) || y.startsWith(x))
        }
    }
}
