package com.piyush.mode.feature.conversations

import com.piyush.mode.feature.conversations.data.ConversationRepository
import com.piyush.mode.feature.conversations.domain.TurnRole
import com.piyush.mode.testing.testDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversationRepositoryTest {
    private var clock = 1_000L
    private val repo = ConversationRepository(testDatabase(), now = { clock++ })

    @Test
    fun turnsComeBackInOrderAndTheLatestConversationListsFirst() = runTest {
        val first = repo.create("Turn off dark mode")
        val second = repo.create("Text Sonali I'm leaving in 10")
        repo.addTurn(first.id, TurnRole.User, "Turn off dark mode", "k1")
        repo.addTurn(first.id, TurnRole.Mode, "Light mode is on.", "k1")

        assertEquals(listOf(TurnRole.User, TurnRole.Mode), repo.turns(first.id).first().map { it.role })
        assertEquals(listOf(first.id, second.id), repo.recent().first().map { it.id }, "a new turn brings it to the top")
    }

    @Test
    fun longTasksGetAShortTitle() {
        val c = repo.create("Open WhatsApp, send message to Sonali sending this message automatically, then read my calendar")
        assertTrue(c.title.length <= 60)
        assertTrue(c.title.endsWith("…"))
    }

    @Test
    fun deletingAConversationTakesItsTurns() = runTest {
        val c = repo.create("t")
        repo.addTurn(c.id, TurnRole.User, "t")
        repo.delete(c.id)
        assertNull(repo.get(c.id))
        assertTrue(repo.turns(c.id).first().isEmpty())
    }
}
