package com.piyush.mode.app.screens

import com.piyush.mode.app.screens.common.PartOfDay
import com.piyush.mode.app.screens.common.RelativeDay
import com.piyush.mode.app.screens.common.TaskBadge
import com.piyush.mode.app.screens.home.HomePresenter
import com.piyush.mode.app.screens.home.Suggestion
import com.piyush.mode.feature.memory.domain.MemoryDraft
import com.piyush.mode.feature.memory.domain.MemoryKind
import com.piyush.mode.feature.tasks.domain.TaskSource
import com.piyush.mode.feature.tasks.domain.TaskStatus
import com.piyush.mode.testing.ScriptedModel
import com.piyush.mode.testing.call
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomePresenterTest {
    private fun TestScope.home(f: Fixture) = HomePresenter(backgroundScope, f.settings, f.runner, f.queue, f.tasks, f.conversations, f.memory, f.clock)

    @Test
    fun greetsByNameAndPartOfDay() = runTest {
        val f = Fixture(this, now = nov(1, 19, 30))
        f.settings.update { it.copy(userName = "Piyush") }
        val s = home(f).state.awaitState { it.name != null }
        assertEquals("Piyush", s.name)
        assertEquals(PartOfDay.Evening, s.partOfDay)
    }

    @Test
    fun suggestsWhatIsAskedOftenThenLearnedRoutes() = runTest {
        val f = Fixture(this)
        repeat(3) { i ->
            val t = f.tasks.create("Read today's calendar", TaskSource.App, null)
            f.tasks.finish(t.id, TaskStatus.Done, "ok", 5, 0.001)
        }
        f.memory.save(MemoryDraft(MemoryKind.Skill, "turn off dark mode", "Turn off Dark Mode", "1. Settings", "e", app = "Settings"))
        val s = home(f).state.awaitState { it.suggestions.size == 2 }
        assertEquals(
            listOf(Suggestion("Read today's calendar", Suggestion.Reason.Often(3)), Suggestion("Turn off Dark Mode", Suggestion.Reason.Learned("Settings"))),
            s.suggestions,
        )
    }

    @Test
    fun recentConversationsCarryTheirDayAndHowTheyEnded() = runTest {
        val f = Fixture(this)
        val c = f.conversations.create("Turn off dark mode")
        val t = f.tasks.create("Turn off dark mode", TaskSource.App, c.id)
        f.tasks.finish(t.id, TaskStatus.GaveUp, "no", 3, 0.001)
        f.now = nov(2, 9)
        val recent = home(f).state.awaitState { it.recent.isNotEmpty() }.recent.single()
        assertEquals(RelativeDay.Yesterday, recent.day)
        assertEquals(TaskBadge.GaveUp, recent.badge)
    }

    @Test
    fun askingSendsTheTaskAndGivesItsConversation() = runTest {
        val f = Fixture(this, ScriptedModel(call("done", "evidence" to "x", "summary" to "Done.")))
        val p = home(f)
        val conversation = p.ask("Turn off dark mode")
        testScheduler.advanceUntilIdle()
        assertTrue(conversation != null)
        assertEquals(TaskStatus.Done, f.tasks.latest(1).single().status)
    }

    @Test
    fun withoutAKeyItSaysSo() = runTest {
        val f = Fixture(this)
        f.settings.apiKey = ""
        assertTrue(home(f).state.awaitState { it.needsKey }.needsKey)
    }
}
