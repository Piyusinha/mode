package com.piyush.mode.app.screens

import com.piyush.mode.app.screens.conversation.ConversationPresenter
import com.piyush.mode.app.screens.conversation.ReplyPart
import com.piyush.mode.app.screens.conversation.TimeRow
import com.piyush.mode.app.screens.conversation.replyParts
import com.piyush.mode.app.screens.home.HomePresenter
import com.piyush.mode.app.screens.memory.MemoryPresenter
import com.piyush.mode.app.screens.run.LiveRunPresenter
import com.piyush.mode.app.screens.tasks.TaskFilter
import com.piyush.mode.app.screens.tasks.TasksPresenter
import com.piyush.mode.feature.memory.domain.MemoryDraft
import com.piyush.mode.feature.memory.domain.MemoryKind
import com.piyush.mode.feature.tasks.domain.TaskSource
import com.piyush.mode.feature.tasks.domain.TaskStatus
import com.piyush.mode.testing.ScriptedModel
import com.piyush.mode.testing.call
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoardFeaturesTest {
    @Test
    fun timedLinesInAReplyBecomeATimetable() {
        val parts = replyParts("Done. I sent \"Leaving in 10\" to Sonali.\nHere's your day:\n14:00 Biozyme with lunch\n21:00 – Shelcal, fish oil\n- 23.00 Softovac before bed\nThat's all.")
        assertEquals(
            listOf(
                ReplyPart.Paragraph("Done. I sent \"Leaving in 10\" to Sonali.\nHere's your day:"),
                ReplyPart.Agenda(listOf(TimeRow("14:00", null, "Biozyme with lunch"), TimeRow("21:00", null, "Shelcal, fish oil"), TimeRow("23:00", null, "Softovac before bed"))),
                ReplyPart.Paragraph("That's all."),
            ),
            parts,
        )
        assertEquals(listOf(ReplyPart.Paragraph("Dark mode is off.")), replyParts("Dark mode is off."))
    }

    @Test
    fun liveRunShowsTheScreenAndCanPause() = runTest {
        val f = Fixture(this, ScriptedModel(call("ask_user", "question" to "Which one?"), call("done", "evidence" to "x", "summary" to "ok")))
        val p = LiveRunPresenter(backgroundScope, f.runner, f.tasks)
        f.queue.submit("Message Sonali", TaskSource.App)
        testScheduler.advanceUntilIdle()
        val s = p.state.awaitState { it.running?.question != null }
        assertNotNull(s.screen)
        assertNotNull(s.running!!.startedAtMs)
        p.pause()
        assertTrue(p.state.awaitState { it.running?.paused == true }.running!!.paused)
        p.resume()
        p.answer("first")
        testScheduler.advanceUntilIdle()
        val done = p.state.awaitState { it.finished != null }
        assertNull(done.screen, "no live screen once finished")
    }

    @Test
    fun recentShowsWhenAndHowLong() = runTest {
        val f = Fixture(this)
        val c = f.conversations.create("Turn off dark mode")
        val t = f.tasks.create("Turn off dark mode", TaskSource.App, c.id)
        f.tasks.markRunning(t.id, "r")
        f.now += 37_000
        f.tasks.finish(t.id, TaskStatus.Done, "ok", 3, 0.001)
        val p = HomePresenter(backgroundScope, f.settings, f.runner, f.queue, f.tasks, f.conversations, f.memory, f.clock)
        val r = p.state.awaitState { it.recent.isNotEmpty() }.recent.single()
        assertEquals(37L, r.durationSeconds)
    }

    @Test
    fun aConversationCanBeSharedRepeatedAndDeleted() = runTest {
        val f = Fixture(this, ScriptedModel(call("done", "evidence" to "x", "summary" to "Dark mode is off.")))
        val task = f.queue.submit("Turn off dark mode", TaskSource.Siri)!!
        testScheduler.advanceUntilIdle()
        val p = ConversationPresenter(backgroundScope, task.conversationId!!, f.conversations, f.runner, f.queue, f.tasks, f.runs, f.clock)
        val s = p.state.awaitState { it.turns.size == 2 }
        assertEquals(TaskSource.Siri, s.turns.first().source)
        assertEquals("› Turn off dark mode\n\nDark mode is off.", p.transcript())
        assertEquals("Turn off dark mode", p.taskToRepeat())
        p.delete()
        assertNull(f.conversations.get(task.conversationId!!))
    }

    @Test
    fun tasksHaveAFilter() = runTest {
        val f = Fixture(this)
        val p = TasksPresenter(backgroundScope, f.runner, f.queue, f.scheduler, f.tasks, f.clock)
        assertEquals(TaskFilter.Now, p.state.value.filter)
        p.setFilter(TaskFilter.Done)
        assertEquals(TaskFilter.Done, p.state.awaitState { it.filter == TaskFilter.Done }.filter)
    }

    @Test
    fun memoryCanBeEditedAndSeveralDeleted() = runTest {
        val f = Fixture(this)
        f.memory.save(MemoryDraft(MemoryKind.Fact, "sonali", "Sonali", "Sonali: WhatsApp", "step 3"))
        f.memory.save(MemoryDraft(MemoryKind.Fact, "home", "Home", "Home: Indiranagar", "you said"))
        val p = MemoryPresenter(backgroundScope, f.memory, f.settings, f.clock)
        val s = p.state.awaitState { it.aboutYou.size == 2 }
        val sonali = s.aboutYou.first { it.title == "Sonali" }
        assertEquals("step 3", sonali.evidence)
        p.edit(sonali.id, "Sonali: message her on Telegram")
        val edited = p.state.awaitState { st -> st.aboutYou.any { it.text.contains("Telegram") } }.aboutYou.first { it.title == "Sonali" }
        assertEquals(MemoryPresenter.EDITED_BY_YOU, edited.evidence)
        p.deleteMany(s.aboutYou.map { it.id })
        assertFalse(p.state.awaitState { it.isEmpty }.isEmpty.not())
    }
}
