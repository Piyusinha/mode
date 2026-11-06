package com.piyush.mode.feature.memory

import com.piyush.mode.feature.memory.data.MemoryRepository
import com.piyush.mode.feature.memory.data.MemoryRetrieval
import com.piyush.mode.feature.memory.data.PreferenceCandidates
import com.piyush.mode.feature.memory.domain.MemoryDraft
import com.piyush.mode.feature.memory.domain.MemoryKind
import com.piyush.mode.testing.testDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersonalMemoryTest {
    private val memory = MemoryRepository(testDatabase())

    @Test
    fun aUsualIsShownForAVagueRequestAndForOneThatNamesIt() {
        memory.save(MemoryDraft(MemoryKind.Usual, "order oats", "Usual: chocolate oats from Swiggy", "Swiggy · Dark chocolate oats 100 g · Home · paid from wallet · mornings", "step 9", listOf("oats", "breakfast", "swiggy"), app = "Swiggy"))
        memory.save(MemoryDraft(MemoryKind.Usual, "cab home", "Usual: cab home on Rapido", "Rapido · Cab Premium · GMR B → Home", "step 4", listOf("cab", "ride", "home"), app = "Rapido"))
        memory.save(MemoryDraft(MemoryKind.Fact, "alex", "Alex", "Lives in Indiranagar, Bengaluru", "said"))
        val retrieval = MemoryRetrieval(memory)
        val vague = retrieval.forTask("order my usual")
        assertEquals(listOf("Usual: chocolate oats from Swiggy: Swiggy · Dark chocolate oats 100 g · Home · paid from wallet · mornings", "Usual: cab home on Rapido: Rapido · Cab Premium · GMR B → Home", "Lives in Indiranagar, Bengaluru"), vague.aboutYou)
        val named = retrieval.forTask("get me oats for breakfast")
        assertEquals("Usual: chocolate oats from Swiggy: Swiggy · Dark chocolate oats 100 g · Home · paid from wallet · mornings", named.aboutYou.first())
        assertTrue(named.aboutYou.none { "Rapido" in it }, "the cab is not about oats: ${named.aboutYou}")
        assertTrue(retrieval.forTask("turn off dark mode").aboutYou.none { it.startsWith("Usual") })
    }

    @Test
    fun theProfileStaysWithinItsBudget() {
        repeat(40) { i -> memory.save(MemoryDraft(MemoryKind.Fact, "topic $i", "Topic $i", "Fact number $i about the user, " + "x".repeat(60), "said")) }
        val about = MemoryRetrieval(memory).forTask("anything").aboutYou
        assertTrue(about.sumOf { it.length } <= MemoryRetrieval.MAX_PROFILE_CHARS, "${about.sumOf { it.length }} chars")
        assertTrue(about.size <= MemoryRetrieval.MAX_FACTS)
    }

    @Test
    fun theSameAnswerTwiceBecomesAFactAndADifferentOneResetsIt() {
        val candidates = PreferenceCandidates(memory)
        assertEquals(emptyList(), candidates.learn(listOf("Which size would you like?" to "Large"), "t1"))
        assertEquals(1, memory.ofKind(MemoryKind.Candidate).size)
        assertEquals(emptyList(), candidates.learn(listOf("Which size would you like?" to "Medium"), "t2"), "a different answer: no fact yet")
        assertEquals("Medium", memory.ofKind(MemoryKind.Candidate).single().text)
        val promoted = candidates.learn(listOf("What size do you want?" to "Medium"), "t3")
        assertEquals(1, promoted.size)
        assertTrue("they chose \"Medium\" (twice): use it without asking" in promoted.single().text, promoted.single().text)
        assertEquals(0, memory.ofKind(MemoryKind.Candidate).size, "the candidate is gone")
        assertTrue(MemoryRetrieval(memory).forTask("order a pizza, which size").aboutYou.any { "Medium" in it })
        assertEquals(emptyList(), candidates.learn(listOf("Pay now?" to "yes"), "t4"), "yes/no is not a preference")
    }
}
