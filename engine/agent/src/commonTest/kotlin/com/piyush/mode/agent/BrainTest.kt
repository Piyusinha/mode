package com.piyush.mode.agent

import com.piyush.mode.llm.LlmClient
import com.piyush.mode.llm.LlmFailed
import com.piyush.mode.testing.ScriptedModel
import com.piyush.mode.testing.call
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrainTest {
    private val asha = call(
        "brief",
        "intent" to "Continue the WhatsApp chat with Asha in Alex's voice",
        "app" to "WhatsApp",
        "start" to "continue",
        "done_when" to "A reply to her latest message is sent",
        "gather" to listOf("Her last few messages and his replies: what is unanswered, his language mix and length"),
        "decide" to listOf("What to say, in his style"),
        "steps" to listOf("Open Asha's chat", "Read recent messages", "Write and send the reply"),
        "report" to "What was sent",
    )

    @Test
    fun aBriefIsReadFromTheBrainsToolCall() = runTest {
        val model = ScriptedModel(asha)
        val r = Brain(model, "strong").brief("talk with Asha", TaskContext(userName = "Alex", today = "Sunday 1 November 2026, 19:05"))
        val b = assertNotNull(r.brief)
        assertEquals("WhatsApp", b.app)
        assertEquals(false, b.fresh, "continue keeps the chat where it is")
        assertEquals(3, b.steps.size)
        assertTrue("Gather first: Her last few messages" in b.text())
        assertEquals(listOf("strong"), model.models)
        assertTrue("NOW: Sunday 1 November 2026" in model.lastUserText, "the brain knows what today is")
    }

    @Test
    fun theBrainSeesWhatWasLearnedAboutTheApps() = runTest {
        val model = ScriptedModel(asha)
        Brain(model, "strong").brief("ride to Bellandur", TaskContext(appNotes = listOf("Rapido: recent destinations are on the home screen")))
        assertTrue("TIPS FOR THESE APPS" in model.lastUserText && "recent destinations are on the home screen" in model.lastUserText, model.lastUserText)
    }

    @Test
    fun aMissingOrFailedBriefIsNoBriefNotAFailedTask() = runTest {
        assertNull(Brain(ScriptedModel(call("brief", "intent" to "x")), "strong").brief("t", TaskContext()).brief, "no steps")
        val failing = LlmClient { _, _, _ -> throw LlmFailed("down") }
        val r = Brain(failing, "strong").brief("t", TaskContext())
        assertNull(r.brief)
        assertEquals("down", r.error)
    }

    @Test
    fun theBriefKnowsANamedDateWinsAndThatLateAtNightTomorrowIsToday() = runTest {
        // 3 Nov 19:44 UTC was 01:14 on 4 Nov in Bengaluru: "tomorrow, the fourth" was read as the fifth.
        val model = ScriptedModel(asha)
        Brain(model, "strong").brief("Tomorrow I have a flight on the fourth of November", TaskContext(today = "Wednesday 4 November 2026, 01:14"))
        assertTrue("A date the user names wins" in model.lastSystemText, model.lastSystemText.take(400))
        assertTrue("Between midnight and 05:00" in model.lastSystemText)
        assertTrue("NOW: Wednesday 4 November 2026, 01:14" in model.lastUserText)
    }

    @Test
    fun theBriefIsToldTheDirectRoutesAndPrefersThem() = runTest {
        val model = ScriptedModel(asha)
        Brain(model, "strong").brief("is my flight tomorrow on time", TaskContext(directRoutes = listOf("open_url", "read_calendar")))
        assertTrue("DIRECT ROUTES THE HANDS HAVE BESIDES THE SCREEN: open_url, read_calendar" in model.lastUserText, model.lastUserText)
        assertTrue("Direct routes first" in model.lastSystemText)
    }
}
