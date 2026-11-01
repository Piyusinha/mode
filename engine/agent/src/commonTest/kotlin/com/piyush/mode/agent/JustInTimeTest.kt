package com.piyush.mode.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JustInTimeTest {
    private val tips = listOf(
        "MyFitnessPal: Serving Size is a menu: pick piece or gram there, then set Number of Servings.",
        "MyFitnessPal: in the diary, use the day arrows at the top to switch days.",
    )

    @Test
    fun onlyTipsAboutThisScreenAreGiven() {
        val item = listOf("Chicken Sushi Roll", "Serving Size", "1 roll", "Number of Servings", "Meal", "Dinner")
        assertEquals(listOf(tips[0]), JustInTime.tips(tips, item, "MyFitnessPal"))
        assertEquals(emptyList(), JustInTime.tips(tips, listOf("Search foods, brands", "Best Match"), "MyFitnessPal"))
    }

    @Test
    fun aRoutinesQuotedLabelsAreFoundNotItsApostrophes() {
        val steps = JustInTime.steps("1. Open MyFitnessPal.\n2. Don't scroll; tap 'Log' beside the meal.\n3. Tap “Search foods, brands, flavours...”")
        assertEquals(listOf(emptyList(), listOf("Log"), listOf("Search foods, brands, flavours...")), steps.map { it.labels })
    }

    @Test
    fun theHintIsTheNextStepWhoseLabelIsOnScreen() {
        val steps = JustInTime.steps("1. Tap 'Log' beside the meal.\n2. Tap 'Search foods, brands, flavours...'")
        val search = ScreenLayout.of(listOf(ScreenLayout.Box("Search foods, brands, flavours…", 200, 160), ScreenLayout.Box("Log", 400, 900)), 20)
        assertEquals(2, JustInTime.hint(steps, from = 2, layout = search)?.step?.number)
        val hint = JustInTime.hint(steps, from = 1, layout = search)!!
        assertEquals("Log", hint.label)
        assertTrue("[2]" in hint.text())
        assertNull(JustInTime.hint(steps, from = 3, layout = search), "nothing left to replay")
    }
}
