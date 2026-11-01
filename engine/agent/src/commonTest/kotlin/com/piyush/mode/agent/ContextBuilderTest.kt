package com.piyush.mode.agent

import com.piyush.mode.llm.ChatMessage.Part
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ContextBuilderTest {
    private val screen = ContextBuilder.Screen(byteArrayOf(1), 500, 1000, listOf(Triple("General", 99, 783)))

    private fun input(
        step: Int = 1,
        history: List<String> = emptyList(),
        notes: List<String> = emptyList(),
        context: TaskContext = TaskContext.Empty,
        plan: String? = null,
        answers: List<Pair<String, String>> = emptyList(),
        hideScreen: Boolean = false,
        texts: List<Triple<String, Int, Int>> = screen.texts,
    ) = ContextBuilder.Input(
        systemPrompt = "RULES", task = "turn off dark mode", context = context, step = step, maxSteps = 30,
        answers = answers, plan = plan, notes = notes, history = history, recentSteps = 12, lastApp = null,
        screen = ContextBuilder.Screen(screen.jpeg, 500, 1000, texts), hideScreen = hideScreen,
    )

    @Test
    fun stablePartsComeFirstAndEndWithCachePoints() {
        val (system, user) = ContextBuilder.build(input())
        assertEquals("system", system.role)
        assertTrue((system.parts.single() as Part.Text).cacheAfter)
        val parts = user.parts
        assertEquals(3, parts.size)
        assertTrue((parts[0] as Part.Text).cacheAfter, "stable part")
        assertFalse((parts[1] as Part.Text).cacheAfter, "per-step part")
        assertIs<Part.Image>(parts[2])
        assertTrue((parts[0] as Part.Text).text.contains("TASK: turn off dark mode"))
        assertTrue((parts[1] as Part.Text).text.startsWith("STEP 1 of at most 30"))
        assertTrue((parts[1] as Part.Text).text.contains("\"General\" at (99, 783)"))
    }

    @Test
    fun theStablePartIsTheSameFromStepToStep() {
        val context = TaskContext(userName = "Piyush", earlierTurns = listOf(TaskContext.EarlierTurn("message Sonali", "Sent.")))
        val one = ContextBuilder.stable(input(step = 2, history = listOf("1. open Settings — ok"), context = context, plan = "1. a"))
        val two = ContextBuilder.stable(
            input(step = 7, history = List(6) { "${it + 1}. tap \"x\" — ok" }, notes = listOf("a"), context = context, plan = "1. [done] a\n2. b"),
        )
        assertEquals(one, two)
        assertTrue(one.indexOf("Their name: Piyush") < one.indexOf("EARLIER IN THIS CONVERSATION"))
        assertTrue(one.indexOf("EARLIER IN THIS CONVERSATION") < one.indexOf("TASK:"))
    }

    @Test
    fun everySectionKeepsToItsBudget() {
        val context = TaskContext(
            aboutYou = List(40) { "fact $it" },
            appNotes = List(20) { "app note $it" },
            earlierTurns = List(5) { TaskContext.EarlierTurn("task $it " + "x".repeat(400), "done $it") },
        )
        val stable = ContextBuilder.stable(input(context = context, answers = List(8) { "q$it" to "a$it" }))
        assertEquals(ContextBuilder.MAX_ABOUT_YOU, Regex("- fact \\d+").findAll(stable).count())
        assertEquals(ContextBuilder.MAX_APP_NOTES, Regex("- app note \\d+").findAll(stable).count())
        assertFalse("task 1 " in stable, "only the last 3 turns")
        assertTrue("task 4 " in stable)
        assertFalse("x".repeat(300) in stable, "each turn clipped")
        assertFalse("q2" in stable, "only the last 5 answers")
        assertTrue("q7" in stable)

        val step = ContextBuilder.perStep(
            input(notes = List(35) { "note $it " + "n".repeat(300) }, texts = List(100) { Triple("t$it", 1, it) }, plan = "p".repeat(3000)),
        )
        assertFalse("p".repeat(1500) in step, "plan clipped")
        assertTrue("(5 earlier notes not shown)" in step)
        assertFalse("note 4 " in step)
        assertTrue("note 34 " in step)
        assertFalse("n".repeat(200) in step, "each note clipped")
        assertTrue("\"t79\"" in step)
        assertFalse("\"t80\"" in step)
    }

    @Test
    fun olderStepsBecomeOneSummaryLine() {
        val history = listOf(
            "1. open Settings — ok → screen changed",
            "2. tap \"Search\" — ok → screen changed",
            "3. type \"Dark\" — ok → screen changed",
            "4. scroll down — ok → screen did NOT change",
        ) + List(12) { "${it + 5}. tap \"row $it\" — ok → screen changed" }
        val step = ContextBuilder.perStep(input(step = 17, history = history))
        assertTrue("Steps 1–4 (earlier): opened Settings; 1 tap, 1 typed, 1 scroll; 1 left the screen unchanged." in step, step)
        assertFalse("2. tap \"Search\"" in step)
        assertTrue("16. tap \"row 11\"" in step)
        assertTrue("5. tap \"row 0\"" in step)
    }

    @Test
    fun modesOwnScreenIsDescribedNotShown() {
        val user = ContextBuilder.build(input(hideScreen = true))[1]
        assertTrue(user.parts.none { it is Part.Image })
        assertTrue("Mode, the app that runs you, is in front" in user.text)
        assertFalse("\"General\"" in user.text)
    }

    @Test
    fun thePlanAndTheExpectationComeLastJustBeforeTheScreen() {
        val step = ContextBuilder.perStep(
            ContextBuilder.Input(
                systemPrompt = "RULES", task = "t", context = TaskContext.Empty, step = 3, maxSteps = 30,
                answers = emptyList(), plan = "1. open Settings", notes = listOf("a fact"),
                history = listOf("1. open Settings — ok"), recentSteps = 12, lastApp = null,
                expectation = "Settings opens", screen = screen, hideScreen = false,
            ),
        )
        assertTrue(step.indexOf("WHAT HAPPENED SO FAR") < step.indexOf("YOU EXPECTED"))
        assertTrue(step.indexOf("YOU EXPECTED your last action to: Settings opens") < step.indexOf("YOUR PLAN"))
        assertTrue(step.indexOf("YOUR PLAN") < step.indexOf("THE SCREEN NOW"))
    }

    @Test
    fun memorySectionsComeInOrderBeforeTheTask() {
        val context = TaskContext(
            userName = "Piyush",
            aboutYou = listOf("Sonali: WhatsApp"),
            skills = listOf("SKILL: Turn off Dark Mode\n1. open Settings"),
            appNotes = listOf("Settings: search is at the top"),
            recentTasks = List(8) { "task $it" },
        )
        val stable = ContextBuilder.stable(input(context = context))
        val order = listOf("ABOUT THE USER", "WHAT WORKED BEFORE", "TIPS FOR THESE APPS", "RECENT TASKS", "TASK:").map { stable.indexOf(it) }
        assertEquals(order.sorted(), order, stable)
        assertTrue(order.all { it >= 0 })
        assertTrue("- task 4" in stable)
        assertFalse("- task 5" in stable, "only the last 5 tasks")
    }
}
