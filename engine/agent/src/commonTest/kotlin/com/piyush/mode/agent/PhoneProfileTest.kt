package com.piyush.mode.agent

import com.piyush.mode.llm.ToolCall
import com.piyush.mode.ops.Op
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PhoneProfileTest {
    private val iphoneOps = setOf(
        "screen.text", "screen.capture", "screen.bounds", "input.tap", "input.press", "input.drag", "input.scroll",
        "input.text", "input.keys", "nav.home", "apps.launch", "session.state",
    )
    private val androidOps = iphoneOps + setOf("nav.back", "nav.recents", "notifications.open", "quicksettings.open", "apps.current", "url.open", "tree")
    private val g = AgentTools.Geometry(500, 1000, 402.0, 874.0)

    @Test
    fun theIphoneKeepsItsToolsAndPrompt() {
        val names = AgentTools.available(iphoneOps).map { it.name }
        assertEquals(
            listOf("tap", "long_press", "swipe", "scroll", "type_text", "press_key", "back", "control_center", "home", "open_app", "plan", "wait", "ask_user", "wait_for_user", "done"),
            names,
        )
        assertIs<Op.Drag>(AgentTools.toOp(ToolCall("back", JsonObject(emptyMap())), g))
        val prompt = Agent.SYSTEM_PROMPT
        assertTrue("You operate an iPhone for the user" in prompt)
        assertTrue("The iOS back swipe does nothing in many apps." in prompt)
        assertTrue("Know iOS and pick the fastest route" in prompt)
        assertTrue("- Spotlight (swipe down in the middle of the Home Screen)" in prompt)
        assertTrue("synced to the iPhone" in prompt)
        assertTrue("Face ID" in Agent.accessRules(AgentConfig()))
        assertTrue("operates the user's iPhone" in Brain.SYSTEM)
    }

    @Test
    fun androidGetsItsOwnToolsAndWords() {
        val android = PhoneProfile.Android
        val names = AgentTools.available(androidOps, profile = android).map { it.name }
        assertTrue("back" in names && "quick_settings" in names && "notifications" in names, names.toString())
        assertFalse("control_center" in names || "run_shortcut" in names, names.toString())
        assertEquals(Op.Back, AgentTools.toOp(ToolCall("back", JsonObject(emptyMap())), g, android))
        assertEquals(Op.OpenQuickSettings, AgentTools.toOp(ToolCall("quick_settings", JsonObject(emptyMap())), g, android))
        assertEquals(Op.OpenNotifications, AgentTools.toOp(ToolCall("notifications", JsonObject(emptyMap())), g, android))

        val prompt = Agent.systemPrompt(android) + Agent.accessRules(AgentConfig(), android)
        assertTrue("You operate an Android phone for the user" in prompt)
        assertTrue("Know Android and pick the fastest route" in prompt)
        assertTrue("Quick Settings (quick_settings tool)" in prompt)
        assertFalse("iPhone" in prompt || "iOS" in prompt || "Control Center" in prompt || "Face ID" in prompt, prompt)
        assertTrue("operates the user's Android phone" in Brain.system(android))
        assertFalse("iPhone" in Brain.decide(android))
    }

    @Test
    fun androidToolsNeedAndroidOps() {
        // Without the new ops (an engine that cannot do them), the Android profile offers no back at all rather than a swipe.
        val names = AgentTools.available(iphoneOps, profile = PhoneProfile.Android).map { it.name }
        assertFalse("back" in names || "quick_settings" in names || "notifications" in names, names.toString())
    }
}
