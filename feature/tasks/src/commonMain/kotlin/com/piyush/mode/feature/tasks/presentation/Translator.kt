package com.piyush.mode.feature.tasks.presentation

import com.piyush.mode.feature.settings.domain.SpeechLanguage
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
 * Mode works in English: a request in another language is turned into English before the task starts,
 * by the quick model (phone control's, lowest effort). English requests skip it, so they don't wait.
 */
public class Translator(
    private val llm: LlmClient,
    private val model: () -> Pair<String, ModelProvider>?,
) {
    /** [english] to work on; [language] the request was in, when it wasn't English (for the reply). */
    public data class Result(val english: String, val language: String?)

    public suspend fun toEnglish(text: String, spoken: SpeechLanguage, budgetMs: Long = BUDGET_MS): Result {
        val said = text.trim()
        val chosen = spoken.takeUnless { it == SpeechLanguage.Auto || it.isEnglish }
        if (said.isEmpty() || (chosen == null && !looksForeign(said))) return Result(said, null)
        val fallback = Result(said, chosen?.languageName)
        val (id, provider) = model() ?: return fallback
        return try {
            withTimeoutOrNull(budgetMs) {
                val user = (chosen?.let { "THEY USUALLY SPEAK: ${it.languageName}\n" } ?: "") + "THE REQUEST: $said"
                val reply = llm.forProvider(provider).chat(id, listOf(ChatMessage("system", SYSTEM), ChatMessage("user", user)), TOOLS)
                val args = reply.toolCall?.takeIf { it.name == TOOL }?.arguments ?: return@withTimeoutOrNull null
                val english = (args["english"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: return@withTimeoutOrNull null
                val language = (args["language"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                Result(english, language?.takeUnless { it.equals("English", ignoreCase = true) })
            } ?: fallback
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fallback
        }
    }

    public companion object {
        public const val BUDGET_MS: Long = 6_000
        private const val TOOL = "english"

        /** Mostly letters outside the Latin alphabet (Devanagari, Gujarati, Tamil…): not English. */
        public fun looksForeign(text: String): Boolean {
            val letters = text.filter { it.isLetter() }
            if (letters.isEmpty()) return false
            return letters.count { it.code > LATIN_END } * 2 > letters.length
        }

        private const val LATIN_END = 0x024F

        private val SYSTEM = """
            Mode is a personal assistant that does tasks on the user's iPhone and works in English. Turn the user's
            request into natural English, keeping its meaning exactly: names of people, apps, shops, places, dishes
            and amounts stay as they are (in Latin letters). If it is already English, return it unchanged.
            Also name the language it was in (in English: "Hindi", "Gujarati", "English").
            Call english.
        """.trimIndent()

        private val TOOLS = buildJsonArray {
            add(buildJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", TOOL)
                    put("description", "The request in English and the language it was in.")
                    putJsonObject("parameters") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("english") { put("type", "string"); put("description", "The request in English.") }
                            putJsonObject("language") { put("type", "string"); put("description", "The language it was in, in English.") }
                        }
                        putJsonArray("required") { add(JsonPrimitive("english")); add(JsonPrimitive("language")) }
                    }
                }
            })
        }
    }
}
