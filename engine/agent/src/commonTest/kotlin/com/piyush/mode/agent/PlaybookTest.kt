package com.piyush.mode.agent

import com.piyush.mode.testing.FakePhone
import com.piyush.mode.testing.ScriptedModel
import com.piyush.mode.testing.call
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybookTest {
    private val skill = """
        1. Open Zomato
        2. Search "Magnolia Bakery"
        3. Tap "Add item" beside the cake
        Backup: the search box is at the top; the "Orders" tab at the bottom lists past orders
        If it goes wrong:
        - if "CLOSED RIGHT NOW": tap Schedule, pick the next slot, then ask which
        - when "Out of stock": pick the closest cake and ask
        Failed: 2026-11-03 gave up at the closed shop (no schedule tried)
    """.trimIndent()

    @Test
    fun rulesAreReadFromTheSkillAndShownOnlyWhenTheirTriggerIsOnScreen() {
        val rules = Playbook.rules(skill)
        assertEquals(listOf("CLOSED RIGHT NOW", "Out of stock"), rules.map { it.trigger })
        assertEquals("tap Schedule, pick the next slot, then ask which", rules[0].response)
        assertEquals(emptyList(), Playbook.applicable(rules, listOf("Magnolia Bakery", "Classic Tres Leches", "Add item")))
        val shown = Playbook.applicable(rules, listOf("Tres Leches Creamery", "CLOSED RIGHT NOW · Schedule", "10–15 min"))
        assertEquals(1, shown.size)
        assertTrue("PLAYBOOK (seen before, \"CLOSED RIGHT NOW\" is on screen): tap Schedule" in shown.single(), shown.single())
        assertEquals("1. Open Zomato\n2. Search \"Magnolia Bakery\"\n3. Tap \"Add item\" beside the cake", Playbook.routeOnly(skill))
    }

    private class Gate : UserGate {
        override suspend fun confirm(question: String) = true
        override suspend fun ask(question: String): String? = "yes"
    }

    @Test
    fun theAgentSeesTheRuleTheMomentTheShopIsClosed() = runTest {
        val closed = listOf("Tres Leches Creamery" to (100.0 to 300.0), "CLOSED RIGHT NOW" to (100.0 to 340.0), "Schedule" to (300.0 to 340.0))
        val phone = FakePhone(List(4) { closed })
        val model = ScriptedModel(call("done", "success" to false, "summary" to "x", "evidence" to "CLOSED RIGHT NOW", "blocked_by" to "closed"), call("done", "success" to false, "summary" to "x", "evidence" to "x"))
        Agent(phone, model, Gate(), AgentConfig(settleMs = 0, maxSteps = 4)).run("order tres leches", context = TaskContext(skills = listOf(skill)))
        assertTrue("PLAYBOOK (seen before, \"CLOSED RIGHT NOW\" is on screen)" in model.lastUserText, model.lastUserText)
    }
}
