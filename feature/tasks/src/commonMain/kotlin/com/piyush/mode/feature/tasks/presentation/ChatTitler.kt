package com.piyush.mode.feature.tasks.presentation

import com.piyush.mode.llm.ChatMessage
import com.piyush.mode.llm.LlmClient
import com.piyush.mode.llm.ModelProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * A chat's name, from everything asked in it: "Sam, DoorDash meeting and coffee" rather than the first request
 * word for word ("Can you turn off dark mode"). Written by the quick model (lowest effort) after each task.
 */
public class ChatTitler(
    private val llm: LlmClient,
    private val model: () -> Pair<String, ModelProvider>?,
) {
    /**
     * A short title for a chat whose requests were [asked] (oldest first); null if the model can't. [done] is what
     * Mode did or answered (oldest first): a request that names nothing ("Sure, please do this") is named from it.
     */
    public suspend fun title(asked: List<String>, done: List<String> = emptyList(), budgetMs: Long = BUDGET_MS): String? {
        val requests = asked.map { it.trim() }.filter { it.isNotEmpty() }.takeLast(MAX_REQUESTS)
        if (requests.isEmpty()) return null
        val (id, provider) = model() ?: return null
        return try {
            withTimeoutOrNull(budgetMs) {
                val results = done.map { it.trim() }.filter { it.isNotEmpty() }.takeLast(MAX_REQUESTS)
                val user = "WHAT THEY ASKED, OLDEST FIRST:\n" + requests.joinToString("\n") { "- ${it.take(REQUEST_CHARS)}" } +
                    if (results.isEmpty()) "" else "\nWHAT MODE DID OR ANSWERED, OLDEST FIRST:\n" + results.joinToString("\n") { "- ${it.take(REQUEST_CHARS)}" }
                val reply = llm.forProvider(provider).chat(id, listOf(ChatMessage("system", SYSTEM), ChatMessage("user", user)), TOOLS)
                val args = reply.toolCall?.takeIf { it.name == TOOL }?.arguments
                clean((args?.get("title") as? JsonPrimitive)?.contentOrNull ?: reply.text)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    public companion object {
        private const val BUDGET_MS = 15_000L
        private const val MAX_REQUESTS = 12
        private const val REQUEST_CHARS = 200
        private const val MAX_WORDS = 7
        private const val MAX_CHARS = 48
        private const val TOOL = "name_chat"

        /** One short line, no quotes or trailing full stop; null when empty or too long. */
        internal fun clean(text: String?): String? {
            val line = text?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }
                ?.trim('"', '“', '”', '\'', '*', '`', ' ')?.trimEnd('.')
                ?.replace(Regex("\\s+"), " ") ?: return null
            if (line.isEmpty() || line.split(' ').size > MAX_WORDS || line.length > MAX_CHARS) return null
            return line.replaceFirstChar { it.uppercaseChar() }
        }

        private val SYSTEM = """
            Name a chat between the user and Mode, their iPhone assistant, from what they asked in it. 2 to 6 words,
            like a phone's chat list: what it's about, covering the main things when there are several ("Sam,
            DoorDash meeting and coffee", "Dark Mode and Wi-Fi", "Tomorrow's flight to Delhi"). No verbs like "Can you",
            no quotes, no full stop, no emoji. Same language as the requests. When a request doesn't say what it is
            about ("Sure, please do this", "Yes"), name the chat from what Mode did or answered. Never a placeholder
            such as "Unspecified request", "Unknown" or "New chat". Call name_chat.
        """.trimIndent()

        private val TOOLS = buildJsonArray {
            add(buildJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", TOOL)
                    put("description", "The chat's name.")
                    putJsonObject("parameters") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("title") { put("type", "string"); put("description", "2 to 6 words.") }
                        }
                        putJsonArray("required") { add(JsonPrimitive("title")) }
                    }
                }
            })
        }
    }
}
