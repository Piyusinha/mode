package com.piyush.mode.feature.memory

import com.piyush.mode.feature.memory.data.MemoryRepository
import com.piyush.mode.feature.memory.data.Teacher
import com.piyush.mode.feature.memory.domain.MemoryKind
import com.piyush.mode.llm.ChatResult
import com.piyush.mode.llm.LlmClient
import com.piyush.mode.llm.ModelProvider
import com.piyush.mode.llm.ToolCall
import com.piyush.mode.llm.Usage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TeacherTest {
    @Test
    fun aLessonInChatIsToldFromATask() {
        assertTrue(Teacher.isLesson("Next time use Control Center for this, it's faster"))
        assertTrue(Teacher.isLesson("From now on order from Napoli Corner"))
        assertTrue(Teacher.isLesson("You should have searched for Dark Mode"))
        assertFalse(Teacher.isLesson("Turn on dark mode"))
        assertFalse(Teacher.isLesson("What's on my calendar today?"))
    }

    @Test
    fun whatTheUserTeachesBecomesTheirTipForThatApp() = runTest {
        val memory = MemoryRepository(com.piyush.mode.testing.testDatabase())
        val llm = LlmClient { _, messages, _ ->
            assertTrue("Turn off Dark Mode" in messages.last().text)
            ChatResult(
                ToolCall("save_lesson", JsonObject(mapOf(
                    "app" to JsonPrimitive("Settings"), "title" to JsonPrimitive("Dark Mode"),
                    "tip" to JsonPrimitive("Open Control Center and tap its Dark Mode switch."),
                    "reply" to JsonPrimitive("Got it. Next time I'll switch Dark Mode from Control Center."),
                ))), "", Usage(10, 5, 0.0),
            )
        }
        val lesson = Teacher(llm, memory, model = { "m" to ModelProvider.OpenRouter }).teach("Next time use Control Center for this", "Turn off Dark Mode")!!
        assertEquals("Got it. Next time I'll switch Dark Mode from Control Center.", lesson.reply)
        val tip = memory.ofKind(MemoryKind.Tip).single()
        assertEquals("Settings", tip.app)
        assertEquals("Open Control Center and tap its Dark Mode switch.", tip.text)
        assertTrue(Teacher.taught(tip), "Memory shows it as theirs")
    }

    @Test
    fun withoutTheModelTheirWordsAreKept() = runTest {
        val memory = MemoryRepository(com.piyush.mode.testing.testDatabase())
        val offline = LlmClient { _, _, _ -> error("offline") }
        val lesson = Teacher(offline, memory, model = { "m" to ModelProvider.OpenRouter }).teach("Always search Settings for Dark Mode", "Turn off Dark Mode", "step 8: scroll up")!!
        assertEquals("Always search Settings for Dark Mode", lesson.item.text)
        assertTrue("step 8" in lesson.item.evidence)
    }
}
