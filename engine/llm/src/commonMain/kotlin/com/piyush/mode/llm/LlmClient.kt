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

/**
 * One message to the model, as ordered parts. A text part marked [Part.Text.cacheAfter]
 * ends a prefix that stays the same from step to step, so providers that cache prompts
 * can reuse everything up to it.
 */
class ChatMessage(val role: String, val parts: List<Part>) {
    /** A plain message: [text], then [image] (a JPEG) when given. */
    constructor(role: String, text: String, image: ByteArray? = null) :
        this(role, buildList { add(Part.Text(text)); image?.let { add(Part.Image(it)) } })

    /** All the text parts, joined. */
    val text: String get() = parts.filterIsInstance<Part.Text>().joinToString("\n\n") { it.text }

    /** The first image, if any. */
    val image: ByteArray? get() = parts.filterIsInstance<Part.Image>().firstOrNull()?.jpeg

    sealed interface Part {
        class Text(val text: String, val cacheAfter: Boolean = false) : Part
        class Image(val jpeg: ByteArray) : Part
    }
}

/** The single tool call the model chose, with its arguments. */
class ToolCall(val name: String, val arguments: JsonObject)

class ChatResult(
    /** Null when the model answered in text instead of calling a tool. */
    val toolCall: ToolCall?,
    val text: String,
    val usage: Usage,
)

class Usage(
    val promptTokens: Int,
    val completionTokens: Int,
    val costUsd: Double,
    /** Prompt tokens read from the provider's cache (billed at a fraction). */
    val cachedTokens: Int = 0,
    /** Prompt tokens written to the cache on this call. */
    val cacheWriteTokens: Int = 0,
)

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
    /** Models that refused having their reasoning turned off with a 400 this session. */
    private val mustReason = mutableSetOf<String>()

    override suspend fun chat(model: String, messages: List<ChatMessage>, tools: JsonArray): ChatResult {
        val key = apiKey()?.takeIf { it.isNotBlank() } ?: throw LlmFailed("No OpenRouter API key: add it in Settings.")
        val forced = forcedToolChoiceAllowed(model) && model !in noForcedToolChoice
        return try {
            postQuick(key, model, messages, tools, if (forced) "required" else "auto")
        } catch (e: LlmFailed) {
            // Newer models reject forced tool use with a 400; ask again with "auto"
            // (the prompt already says to always call exactly one tool).
            if (!forced || !e.status400) throw e
            noForcedToolChoice += model
            postQuick(key, model, messages, tools, "auto")
        }
    }

    /**
     * Seen on the phone: a hybrid model (Qwen3.8-Omni-Flash) thought before every tap and took
     * 8–26 s a step. One action at a time needs no long reasoning: it is turned off where the
     * model allows, and left on for a model that refuses (remembered for the session).
     */
    private suspend fun postQuick(key: String, model: String, messages: List<ChatMessage>, tools: JsonArray, toolChoice: String): ChatResult {
        val quiet = reasoningOffAllowed(model) && model !in mustReason
        return try {
            post(key, model, messages, tools, toolChoice, reasoningOff = quiet)
        } catch (e: LlmFailed) {
            // Only a refusal about reasoning itself; any other 400 is someone else's to handle.
            if (!quiet || !e.status400 || e.message?.contains("reason", ignoreCase = true) != true) throw e
            mustReason += model
            post(key, model, messages, tools, toolChoice, reasoningOff = false)
        }
    }

    private suspend fun post(
        key: String,
        model: String,
        messages: List<ChatMessage>,
        tools: JsonArray,
        toolChoice: String,
        reasoningOff: Boolean = false,
    ): ChatResult {
        val body = buildJsonObject {
            put("model", model)
            val markers = cacheMarkersAllowed(model)
            put("messages", buildJsonArray { messages.forEach { add(it.toJson(markers)) } })
            put("tools", tools)
            put("tool_choice", toolChoice)
            put("parallel_tool_calls", false)
            put("max_tokens", 1024)
            putJsonObject("usage") { put("include", true) }
            if (reasoningOff) putJsonObject("reasoning") { put("enabled", false) }
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
        /** Claude models do not reason unless asked; everyone else is asked not to. */
        fun reasoningOffAllowed(model: String): Boolean = !model.startsWith("anthropic/")

        fun forcedToolChoiceAllowed(model: String): Boolean =
            listOf("claude-sonnet-5.5", "claude-opus-5.5", "claude-fable-5.1", "claude-mythos-5.1").none { it in model }

        /**
         * Models whose providers cache only up to explicit `cache_control` markers
         * (Anthropic, Gemini). Others cache a repeated prefix by themselves, so they get
         * the same stable-first layout without markers.
         */
        fun cacheMarkersAllowed(model: String): Boolean =
            model.startsWith("anthropic/") || model.startsWith("google/gemini")

        @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
        internal fun ChatMessage.toJson(markers: Boolean): JsonElement = buildJsonObject {
            put("role", role)
            val onlyText = parts.all { it is ChatMessage.Part.Text }
            val anyMarker = markers && parts.any { it is ChatMessage.Part.Text && it.cacheAfter }
            if (onlyText && !anyMarker) {
                put("content", text)
                return@buildJsonObject
            }
            put(
                "content",
                buildJsonArray {
                    for (part in parts) when (part) {
                        is ChatMessage.Part.Text -> add(
                            buildJsonObject {
                                put("type", "text")
                                put("text", part.text)
                                if (markers && part.cacheAfter) putJsonObject("cache_control") { put("type", "ephemeral") }
                            },
                        )
                        is ChatMessage.Part.Image -> add(
                            buildJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") {
                                    put("url", "data:image/jpeg;base64," + kotlin.io.encoding.Base64.encode(part.jpeg))
                                }
                            },
                        )
                    }
                },
            )
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
            val details = usage?.get("prompt_tokens_details") as? JsonObject
            return ChatResult(
                toolCall = toolCall,
                text = message["content"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                usage = Usage(
                    promptTokens = usage?.get("prompt_tokens")?.jsonPrimitive?.intOrNull ?: 0,
                    completionTokens = usage?.get("completion_tokens")?.jsonPrimitive?.intOrNull ?: 0,
                    costUsd = usage?.get("cost")?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    cachedTokens = details?.get("cached_tokens")?.jsonPrimitive?.intOrNull ?: 0,
                    cacheWriteTokens = details?.get("cache_write_tokens")?.jsonPrimitive?.intOrNull ?: 0,
                ),
            )
        }
    }
}
