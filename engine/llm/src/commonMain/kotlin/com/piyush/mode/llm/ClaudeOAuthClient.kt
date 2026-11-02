package com.piyush.mode.llm

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64

/** Experimental direct Messages transport; protocol reference: finnvoor/OAuthLanguageModels (CC0).
 * Currency cost is not reported by this endpoint. Zero is an unknown-cost sentinel, never a free-usage claim.
 * No fallback to an API key or OpenRouter: an OAuth refusal must stay visible.
 */
class ClaudeOAuthClient(
    private val http: HttpClient,
    private val token: suspend (forceRefresh: Boolean) -> String,
    private val baseUrl: String = "https://api.anthropic.com",
) : LlmClient {
    override suspend fun chat(model: String, messages: List<ChatMessage>, tools: JsonArray): ChatResult {
        val body = request(model, messages, tools)
        for (attempt in 0..1) {
            val access = token(attempt == 1)
            val response = http.post("$baseUrl/v1/messages") {
                header("Authorization", "Bearer $access")
                header("anthropic-version", "2023-06-01")
                header("anthropic-beta", "claude-code-20250219,oauth-2025-04-20")
                header("User-Agent", "claude-cli/2.1.75")
                header("x-app", "cli")
                setBody(TextContent(body.toString(), ContentType.Application.Json))
            }
            if (response.status.value == 401 && attempt == 0) continue
            if (!response.status.isSuccess()) throw LlmFailed(when (response.status.value) {
                401 -> "Claude needs you to sign in again in Settings."
                403 -> "Claude refused this OAuth request. Your plan or this client may not be supported."
                429 -> "Claude is rate limited or your plan allowance is exhausted. Wait and try again."
                else -> "Claude request failed (HTTP ${response.status.value})."
            })
            return parse(response.bodyAsText())
        }
        error("unreachable")
    }

    companion object {
        const val MAIN_MODEL = "claude-haiku-5-5"
        const val STRONG_MODEL = "claude-sonnet-5-5"

        /** Efforts Mode offers; Claude also takes xhigh and max, which a phone step does not need. */
        val EFFORTS = setOf("low", "medium", "high")

        /**
         * Room for the answer and Claude's thinking (on by default; the thinking counts against it). 1,024 was
         * too little at high effort: the thinking could use it all and leave no action.
         */
        const val MAX_TOKENS = 8192

        fun request(model: String, messages: List<ChatMessage>, tools: JsonArray): JsonObject = buildJsonObject {
            // "claude-haiku-5-5@low": the model, and the effort the user picked for it (none: Claude's own default).
            val base = model.substringBefore('@')
            require(base.startsWith("claude-") && '/' !in base) { "Choose a native Claude model for Claude OAuth." }
            put("model", base)
            put("max_tokens", MAX_TOKENS)
            model.substringAfter('@', "").takeIf { it in EFFORTS }?.let { putJsonObject("output_config") { put("effort", it) } }
            put("system", buildJsonArray {
                add(buildJsonObject { put("type", "text"); put("text", "You are Claude Code, Anthropic's official CLI for Claude.") })
                messages.filter { it.role == "system" }.forEach { m -> m.parts.filterIsInstance<ChatMessage.Part.Text>().forEach { add(textPart(it)) } }
            })
            put("messages", buildJsonArray {
                messages.filter { it.role != "system" }.forEach { m -> add(buildJsonObject {
                    put("role", m.role)
                    put("content", buildJsonArray {
                        m.parts.forEach { p -> when (p) {
                            is ChatMessage.Part.Text -> add(textPart(p))
                            is ChatMessage.Part.Image -> add(buildJsonObject {
                                put("type", "image")
                                putJsonObject("source") {
                                    put("type", "base64"); put("media_type", "image/jpeg"); put("data", Base64.encode(p.jpeg))
                                }
                            })
                        } }
                    })
                }) }
            })
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    tools.forEach { t ->
                        val f = t.jsonObject.getValue("function").jsonObject
                        add(buildJsonObject {
                            put("name", f.getValue("name")); f["description"]?.let { put("description", it) }
                            put("input_schema", f.getValue("parameters"))
                        })
                    }
                })
                // Auto is accepted by reasoning-capable models; Mode already requires a single tool in its prompt.
                putJsonObject("tool_choice") { put("type", "auto"); put("disable_parallel_tool_use", true) }
            }
        }

        private fun textPart(p: ChatMessage.Part.Text): JsonObject = buildJsonObject {
            put("type", "text"); put("text", p.text)
            if (p.cacheAfter) putJsonObject("cache_control") { put("type", "ephemeral") }
        }

        fun parse(body: String): ChatResult {
            val root = runCatching { Json.parseToJsonElement(body).jsonObject }
                .getOrElse { throw LlmFailed("Claude returned an unreadable answer.") }
            if ("error" in root) throw LlmFailed("Claude returned an error. Try signing in again or choose OpenRouter.")
            val content = root["content"] as? JsonArray ?: throw LlmFailed("Claude returned no answer.")
            val blocks = content.map { it.jsonObject }
            val calls = blocks.filter { it["type"]?.jsonPrimitive?.contentOrNull == "tool_use" }
            if (calls.size > 1) throw LlmFailed("Claude returned multiple actions; Mode requires one action at a time.")
            val call = calls.singleOrNull()?.let {
                val name = it["name"]?.jsonPrimitive?.contentOrNull ?: throw LlmFailed("Claude returned an unnamed action.")
                val input = it["input"] as? JsonObject ?: throw LlmFailed("Claude returned invalid action arguments.")
                ToolCall(name, input)
            }
            val usage = root["usage"] as? JsonObject
            fun count(key: String) = usage?.get(key)?.jsonPrimitive?.intOrNull ?: 0
            val cached = count("cache_read_input_tokens")
            val writes = count("cache_creation_input_tokens")
            return ChatResult(call, blocks.filter { it["type"]?.jsonPrimitive?.contentOrNull == "text" }
                .mapNotNull { it["text"]?.jsonPrimitive?.contentOrNull }.joinToString("\n"),
                Usage(count("input_tokens") + cached + writes, count("output_tokens"), 0.0, cached, writes, costReported = false))
        }
    }
}
