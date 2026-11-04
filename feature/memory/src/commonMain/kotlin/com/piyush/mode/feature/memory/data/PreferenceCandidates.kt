package com.piyush.mode.feature.memory.data

import com.piyush.mode.feature.memory.domain.MemoryDraft
import com.piyush.mode.feature.memory.domain.MemoryItem
import com.piyush.mode.feature.memory.domain.MemoryKind
import com.piyush.mode.feature.memory.domain.SaveResult

/**
 * Ask once, remember forever: a choice the user made when Mode asked (a size, an address, a payment, a slot)
 * is kept as a candidate; the second time they choose the same for the same kind of question it becomes a
 * fact, and Mode stops asking. A different answer resets the candidate. Research: agents sit 62 points below
 * one that knows the preference, and the cause is never asking or never keeping the answer (Ask-to-Remember).
 */
public class PreferenceCandidates(private val memory: MemoryRepository) {
    /** Learns from a task's questions and answers; returns the facts that became known this time. */
    public fun learn(answers: List<Pair<String, String>>, taskId: String?): List<MemoryItem> {
        val promoted = mutableListOf<MemoryItem>()
        for ((question, answer) in answers) {
            val subject = subjectOf(question) ?: continue
            val chosen = answer.trim().takeIf { it.isNotEmpty() && it.length <= MAX_ANSWER && !GENERIC.matches(it) } ?: continue
            val existing = memory.ofKind(MemoryKind.Candidate).firstOrNull { it.subject == subject }
            when {
                existing == null -> memory.save(MemoryDraft(MemoryKind.Candidate, subject, "Chose $chosen", chosen, evidence = "asked: $question", keywords = Words.of(question).toList()), taskId)
                Words.normal(existing.text) == Words.normal(chosen) -> {
                    val fact = MemoryDraft(
                        MemoryKind.Fact, "preference: $subject", "Prefers $chosen",
                        "When asked ${question.trim().trimEnd('?')}, they chose \"$chosen\" (twice): use it without asking.",
                        evidence = "answered the same twice: $question", keywords = Words.of(question).toList(),
                    )
                    when (val r = memory.save(fact, taskId)) {
                        is SaveResult.Added -> promoted += r.item
                        is SaveResult.Updated -> promoted += r.item
                        else -> {}
                    }
                    memory.forget(existing.id)
                }
                else -> memory.update(existing.id, chosen, "asked again: $question", taskId, title = "Chose $chosen")
            }
        }
        return promoted
    }

    private fun subjectOf(question: String): String? {
        val words = Words.of(question).filter { it !in ASK_WORDS }.take(4)
        return words.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    private companion object {
        const val MAX_ANSWER = 80
        val GENERIC = Regex("""(?i)^(yes|no|ok|okay|sure|any|you choose|whatever|cancel|stop)$""")
        val ASK_WORDS = setOf("which", "would", "like", "want", "should", "prefer", "choose", "pick", "please", "could", "there", "these", "those", "this", "that", "what", "about")
    }
}
