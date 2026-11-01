package com.piyush.mode.llm

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** One message to the model. [image] is a JPEG shown alongside [text]. */
class ChatMessage(val role: String, val text: String, val image: ByteArray? = null)

/** The single tool call the model chose, with its arguments. */
class ToolCall(val name: String, val arguments: JsonObject)

class ChatResult(
    /** Null when the model answered in text instead of calling a tool. */
    val toolCall: ToolCall?,
    val text: String,
    val usage: Usage,
)

class Usage(val promptTokens: Int, val completionTokens: Int, val costUsd: Double)

/** A chat model that can call tools. */
fun interface LlmClient {
    suspend fun chat(model: String, messages: List<ChatMessage>, tools: JsonArray): ChatResult
}

/** The model call failed: network, key, credit, or a model that cannot do this. */
class LlmFailed(message: String, val status400: Boolean = false) : RuntimeException(message)

/**
 * OpenRouter's OpenAI-compatible chat completions, which also fits any
 * compatible endpoint ([baseUrl]). Asks for exactly one tool call and for the
 * cost of each call.
 */
class OpenRouterClient(
    private val http: HttpClient,
    private val apiKey: () -> String?,
    private val baseUrl: String = "https://openrouter.ai/api/v1",
) : LlmClient {

    /** Models that answered a forced tool choice with a 400 this session. */
    private val noForcedToolChoice = mutableSetOf<String>()

    override suspend fun chat(model: String, messages: List<ChatMessage>, tools: JsonArray): ChatResult {
        val key = apiKey()?.takeIf { it.isNotBlank() } ?: throw LlmFailed("No OpenRouter API key: add it in Settings.")
        val forced = forcedToolChoiceAllowed(model) && model !in noForcedToolChoice
        return try {
            post(key, model, messages, tools, if (forced) "required" else "auto")
        } catch (e: LlmFailed) {
            // Newer models reject forced tool use with a 400; ask again with "auto"
            // (the prompt already says to always call exactly one tool).
            if (!forced || !e.status400) throw e
            noForcedToolChoice += model
            post(key, model, messages, tools, "auto")
        }
    }

    private suspend fun post(key: String, model: String, messages: List<ChatMessage>, tools: JsonArray, toolChoice: String): ChatResult {
        val body = buildJsonObject {
            put("model", model)
            put("messages", buildJsonArray { messages.forEach { add(it.toJson()) } })
            put("tools", tools)
            put("tool_choice", toolChoice)
            put("parallel_tool_calls", false)
            put("max_tokens", 1024)
            putJsonObject("usage") { put("include", true) }
        }
        val response = http.post("$baseUrl/chat/completions") {
            header("Authorization", "Bearer $key")
            header("X-Title", "Mode")
            setBody(TextContent(body.toString(), ContentType.Application.Json))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw LlmFailed("$model: HTTP ${response.status.value}: ${errorMessage(text)}", status400 = response.status.value == 400)
        }
        return parse(text, model)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Claude Sonnet 5.5, Opus 5.5 and Fable/Mythos 5.1 reject forced tool use
         * (tool_choice any/required → HTTP 400); everything else accepts it.
         */
        fun forcedToolChoiceAllowed(model: String): Boolean =
            listOf("claude-sonnet-5.5", "claude-opus-5.5", "claude-fable-5.1", "claude-mythos-5.1").none { it in model }

        @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
        private fun ChatMessage.toJson(): JsonElement = buildJsonObject {
            put("role", role)
            if (image == null) {
                put("content", text)
            } else {
                put(
                    "content",
                    buildJsonArray {
                        add(buildJsonObject { put("type", "text"); put("text", text) })
                        add(
                            buildJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") {
                                    put("url", "data:image/jpeg;base64," + kotlin.io.encoding.Base64.encode(image))
                                }
                            },
                        )
                    },
                )
            }
        }

        /** OpenRouter's message plus the provider's own reason (metadata.raw), which says what was wrong. */
        private fun errorMessage(body: String): String = runCatching {
            val error = json.parseToJsonElement(body).jsonObject["error"]?.jsonObject ?: return@runCatching null
            val message = error["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val metadata = error["metadata"]?.jsonObject
            val raw = metadata?.get("raw")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: it.toString() }
            val provider = metadata?.get("provider_name")?.jsonPrimitive?.contentOrNull
            listOfNotNull(message, provider?.let { "provider $it" }, raw?.take(500)).joinToString(" | ")
        }.getOrNull() ?: body.take(500)

        /** Parses a chat-completions response (exposed for tests). */
        fun parse(body: String, model: String): ChatResult {
            val root = json.parseToJsonElement(body).jsonObject
            root["error"]?.let { throw LlmFailed("$model: ${it.jsonObject["message"]?.jsonPrimitive?.contentOrNull ?: it}") }
            val message = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
                ?: throw LlmFailed("$model: the response had no message")
            val call = message["tool_calls"]?.jsonArray?.firstOrNull()?.jsonObject?.get("function")?.jsonObject
            val toolCall = call?.let {
                val name = it["name"]?.jsonPrimitive?.contentOrNull ?: return@let null
                val raw = it["arguments"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "{}" }
                val args = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrElse { JsonObject(emptyMap()) }
                ToolCall(name, args)
            }
            val usage = root["usage"]?.jsonObject
            return ChatResult(
                toolCall = toolCall,
                text = message["content"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                usage = Usage(
                    promptTokens = usage?.get("prompt_tokens")?.jsonPrimitive?.intOrNull ?: 0,
                    completionTokens = usage?.get("completion_tokens")?.jsonPrimitive?.intOrNull ?: 0,
                    costUsd = usage?.get("cost")?.jsonPrimitive?.doubleOrNull ?: 0.0,
                ),
            )
        }
    }
}
